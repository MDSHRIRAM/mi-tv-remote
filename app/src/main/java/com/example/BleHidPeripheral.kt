package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bluetooth Low Energy remote (HID over GATT), the same kind of link the official Mi remote uses.
 *
 * The phone runs a GATT server with the standard HID, Battery and Device Information services and
 * advertises itself as an HID device. The TV connects to it and bonds once; after that the TV
 * reconnects by itself whenever the phone advertises again. Because a bonded BLE remote is what
 * the TV listens for in standby, advertising after Power is pressed is also our wake-up attempt.
 *
 * Only one TV ("host") is served at a time. Other BLE devices that connect to the phone
 * (watches, earbuds…) are ignored: a device becomes the host only when it subscribes to our
 * HID reports, or when it is the TV we bonded with before.
 */
@SuppressLint("MissingPermission")
class BleHidPeripheral(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onHostConnected(device: BluetoothDevice)
        fun onHostDisconnected(device: BluetoothDevice)
        fun onAdvertisingChanged(active: Boolean)
        /** Advertising could not start; [unsupported] = this phone cannot be a BLE remote. */
        fun onAdvertiseFailed(unsupported: Boolean)
        fun onLog(message: String)
    }

    companion object {
        private const val TAG = "BleHidPeripheral"
        private const val PREFS = "mi_tv_remote_prefs"
        private const val KEY_HOST = "ble_host_address"
        private const val KEY_ORIGINAL_NAME = "original_bt_name"

        /** Name the official Xiaomi Bluetooth remote advertises. */
        const val MI_REMOTE_NAME = "MI RC"

        // Manufacturer field seen in the official remote's advertisement ("03 FF 00 01" on a
        // power press, "03 FF 00 00" on other keys). Android writes the 16-bit id little endian,
        // so id 0x0100 with no payload gives exactly "FF 00 01".
        private const val MI_MFG_POWER = 0x0100
        private const val MI_MFG_IDLE = 0x0000

        private fun uuid16(v: Int): UUID =
            UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v))

        val SERVICE_HID: UUID = uuid16(0x1812)
        val SERVICE_BATTERY: UUID = uuid16(0x180F)
        val SERVICE_DEVICE_INFO: UUID = uuid16(0x180A)

        val CHAR_HID_INFO: UUID = uuid16(0x2A4A)
        val CHAR_REPORT_MAP: UUID = uuid16(0x2A4B)
        val CHAR_HID_CONTROL_POINT: UUID = uuid16(0x2A4C)
        val CHAR_REPORT: UUID = uuid16(0x2A4D)
        val CHAR_PROTOCOL_MODE: UUID = uuid16(0x2A4E)
        val CHAR_BATTERY_LEVEL: UUID = uuid16(0x2A19)
        val CHAR_PNP_ID: UUID = uuid16(0x2A50)
        val CHAR_MANUFACTURER: UUID = uuid16(0x2A29)

        val DESC_CCCD: UUID = uuid16(0x2902)
        val DESC_REPORT_REFERENCE: UUID = uuid16(0x2908)

        // bcdHID 1.11, country 0, flags: RemoteWake | NormallyConnectable
        private val HID_INFO = byteArrayOf(0x11, 0x01, 0x00, 0x03)

        // Vendor ID source = USB, VID 0x18D1, PID 0x2C42, version 0x0100 (little endian)
        private val PNP_ID = byteArrayOf(0x02, 0xD1.toByte(), 0x18, 0x42, 0x2C, 0x00, 0x01)

        private val REPORT_SIZES = mapOf(
            HidDescriptor.REPORT_ID_KEYBOARD to 8,
            HidDescriptor.REPORT_ID_CONSUMER to 2,
            HidDescriptor.REPORT_ID_SYSTEM to 1
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    // Bluetooth calls can block for seconds when the phone's Bluetooth service is busy,
    // so they run on this worker thread, never on the UI thread.
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var gattServer: BluetoothGattServer? = null
    private val pendingServices = ArrayDeque<BluetoothGattService>()
    @Volatile private var servicesReady = false

    private val reportChars = mutableMapOf<Int, BluetoothGattCharacteristic>()
    private val cccdValues = mutableMapOf<String, ByteArray>()

    @Volatile var hostDevice: BluetoothDevice? = null
        private set

    @Volatile private var advertising = false
    private var advertiseStopJob: Job? = null

    private val sendQueue = Channel<Pair<Int, ByteArray>>(Channel.UNLIMITED)
    @Volatile private var notifySent: CompletableDeferred<Unit>? = null

    /** Called once the TV links up again, e.g. to send a wake-up key. */
    var onNextHostConnect: (() -> Unit)? = null

    val isHostConnected: Boolean get() = hostDevice != null

    val savedHostAddress: String? get() = prefs.getString(KEY_HOST, null)

    init {
        scope.launch { runSendLoop() }
    }

    // ---------------------------------------------------------------- GATT server

    /** Opens the GATT server and adds the HID services (in the background). */
    fun start() {
        worker.execute { startBlocking() }
    }

    private fun startBlocking(): Boolean {
        if (gattServer != null) return true
        val mgr = manager ?: return false
        if (mgr.adapter?.isEnabled != true) return false
        return try {
            val server = mgr.openGattServer(context, gattCallback) ?: return false
            gattServer = server
            servicesReady = false
            pendingServices.clear()
            pendingServices.addLast(buildHidService())
            pendingServices.addLast(buildBatteryService())
            pendingServices.addLast(buildDeviceInfoService())
            // Services must be added one at a time; the next one goes in onServiceAdded.
            server.addService(pendingServices.removeFirst())
            true
        } catch (e: Exception) {
            Log.e(TAG, "openGattServer failed", e)
            scope.launch { listener.onLog("BLE start failed: ${e.localizedMessage}") }
            false
        }
    }

    private fun buildHidService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_HID_INFO,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_REPORT_MAP,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
            )
        )

        reportChars.clear()
        for ((reportId, _) in REPORT_SIZES) {
            val report = BluetoothGattCharacteristic(
                CHAR_REPORT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
            )
            val cccd = BluetoothGattDescriptor(
                DESC_CCCD,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
                    BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
            )
            val reference = BluetoothGattDescriptor(
                DESC_REPORT_REFERENCE,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
            )
            report.addDescriptor(cccd)
            report.addDescriptor(reference)
            service.addCharacteristic(report)
            reportChars[reportId] = report
        }
        return service
    }

    private fun buildBatteryService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val level = BluetoothGattCharacteristic(
            CHAR_BATTERY_LEVEL,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        level.addDescriptor(
            BluetoothGattDescriptor(
                DESC_CCCD,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
        )
        service.addCharacteristic(level)
        return service
    }

    private fun buildDeviceInfoService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_DEVICE_INFO, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_PNP_ID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHAR_MANUFACTURER,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        return service
    }

    /** Which report a characteristic belongs to (all reports share the same UUID). */
    private fun reportIdOf(characteristic: BluetoothGattCharacteristic?): Int? =
        reportChars.entries.firstOrNull { it.value === characteristic }?.key

    private fun readValue(characteristic: BluetoothGattCharacteristic): ByteArray? {
        reportIdOf(characteristic)?.let { id -> return ByteArray(REPORT_SIZES[id] ?: 1) }
        return when (characteristic.uuid) {
            CHAR_HID_INFO -> HID_INFO
            CHAR_REPORT_MAP -> HidDescriptor.COMBINED_HID_DESCRIPTOR
            CHAR_PROTOCOL_MODE -> byteArrayOf(0x01) // Report protocol
            CHAR_BATTERY_LEVEL -> byteArrayOf(100)
            CHAR_PNP_ID -> PNP_ID
            CHAR_MANUFACTURER -> "Mi TV Remote".toByteArray()
            else -> null
        }
    }

    private fun cccdKey(device: BluetoothDevice, descriptor: BluetoothGattDescriptor): String =
        "${device.address}/${descriptor.characteristic.uuid}/${reportIdOf(descriptor.characteristic) ?: 0}"

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            val next = pendingServices.removeFirstOrNull()
            if (next != null) {
                gattServer?.addService(next)
            } else {
                servicesReady = true
                Log.d(TAG, "All BLE HID services added")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            scope.launch {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // The TV we bonded with before: it already has our reports enabled.
                    if (device.address == savedHostAddress && hostDevice == null) {
                        becomeHost(device)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (hostDevice?.address == device.address) {
                        hostDevice = null
                        listener.onHostDisconnected(device)
                    }
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = readValue(characteristic)
            if (value == null) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                return
            }
            val slice = if (offset >= value.size) ByteArray(0) else value.copyOfRange(offset, value.size)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            // Protocol Mode / Control Point (suspend / exit suspend): nothing to change for us.
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            val value: ByteArray = when (descriptor.uuid) {
                DESC_REPORT_REFERENCE -> {
                    val id = reportIdOf(descriptor.characteristic) ?: 0
                    byteArrayOf(id.toByte(), 0x01) // 0x01 = Input report
                }
                DESC_CCCD -> cccdValues[cccdKey(device, descriptor)]
                    ?: if (device.address == savedHostAddress) byteArrayOf(0x01, 0x00)
                    else byteArrayOf(0x00, 0x00)
                else -> ByteArray(0)
            }
            val slice = if (offset >= value.size) ByteArray(0) else value.copyOfRange(offset, value.size)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor.uuid == DESC_CCCD && value != null) {
                cccdValues[cccdKey(device, descriptor)] = value
                val enabled = value.isNotEmpty() && (value[0].toInt() and 0x01) != 0
                val isHidReport = reportIdOf(descriptor.characteristic) != null
                if (enabled && isHidReport) {
                    scope.launch {
                        if (hostDevice?.address != device.address) becomeHost(device)
                    }
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            notifySent?.complete(Unit)
        }
    }

    private fun becomeHost(device: BluetoothDevice) {
        hostDevice = device
        prefs.edit().putString(KEY_HOST, device.address).apply()
        stopAdvertising()
        listener.onHostConnected(device)
        onNextHostConnect?.let { action ->
            onNextHostConnect = null
            scope.launch {
                delay(400L) // let the TV finish setting up the link
                action()
            }
        }
    }

    // ---------------------------------------------------------------- advertising

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            listener.onAdvertisingChanged(true)
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            listener.onAdvertisingChanged(false)
            val reason = when (errorCode) {
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "this phone cannot act as a BLE remote"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many BLE advertisers running"
                ADVERTISE_FAILED_ALREADY_STARTED -> "already advertising"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "advertising data too large"
                else -> "error $errorCode"
            }
            if (errorCode == ADVERTISE_FAILED_ALREADY_STARTED) {
                advertising = true
                listener.onAdvertisingChanged(true)
            } else {
                listener.onLog("BLE advertising failed: $reason")
                listener.onAdvertiseFailed(errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
            }
        }
    }

    /**
     * Advertises as a connectable HID device for [durationMs]. A TV in "Add accessory" will list
     * the phone; a TV that bonded with us before reconnects by itself.
     */
    @Volatile private var advertiseUntil = 0L

    fun startAdvertising(durationMs: Long) {
        advertiseUntil = System.currentTimeMillis() + durationMs
        advertiseStopJob?.cancel()
        advertiseStopJob = scope.launch {
            delay(durationMs)
            stopAdvertising()
        }
        worker.execute { startAdvertisingBlocking() }
    }

    private fun startAdvertisingBlocking() {
        if (advertising) return
        if (!startBlocking()) {
            scope.launch { listener.onAdvertiseFailed(false) }
            return
        }
        // The TV reads our HID services right after connecting, so wait until all are added.
        var waited = 0
        while (!servicesReady && waited < 3000) {
            Thread.sleep(50)
            waited += 50
        }
        val adapter = manager?.adapter
        val advertiser = adapter?.bluetoothLeAdvertiser
        if (advertiser == null || !adapter.isMultipleAdvertisementSupported) {
            scope.launch {
                listener.onLog("This phone cannot act as a BLE remote")
                listener.onAdvertiseFailed(true)
            }
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // Advertise under the official remote's name so the TV's own remote-pairing screen has
        // the best chance to recognise us.
        useMiRemoteName(advertiseUntil)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SERVICE_HID))
            .addManufacturerData(MI_MFG_IDLE, ByteArray(0))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "startAdvertising failed", e)
            scope.launch {
                listener.onLog("BLE advertising failed: ${e.localizedMessage}")
                listener.onAdvertiseFailed(false)
            }
        }
    }

    // ---------------------------------------------------------------- standby wake

    private val wakeCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "wake burst failed: $errorCode")
        }
    }

    /**
     * Sends the official remote's "power pressed" advertisement for [durationMs]: name "MI RC",
     * HID service, manufacturer field FF 00 01, TX power. Non-connectable, like the remote.
     *
     * Limit: Xiaomi TVs only accept this from the remote's own Bluetooth address, and a normal
     * Android app cannot choose its address. It can still work on TVs that also accept the
     * phone's address, so it is worth trying.
     */
    fun startWakeBurst(durationMs: Long) {
        worker.execute {
            val adapter = manager?.adapter ?: return@execute
            val advertiser = adapter.bluetoothLeAdvertiser ?: return@execute
            useMiRemoteName(System.currentTimeMillis() + durationMs)
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(SERVICE_HID))
                .addManufacturerData(MI_MFG_POWER, ByteArray(0))
                .build()
            try {
                advertiser.stopAdvertising(wakeCallback)
                advertiser.startAdvertising(settings, data, wakeCallback)
            } catch (e: Exception) {
                Log.w(TAG, "wake burst error", e)
                return@execute
            }
            scope.launch {
                delay(durationMs)
                worker.execute {
                    try {
                        advertiser.stopAdvertising(wakeCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "stop wake burst error", e)
                    }
                }
            }
        }
    }

    /**
     * Knocks on the TV's door: opens a classic Bluetooth link (SDP query) and an LE connection
     * to the TV's address. Some Xiaomi boxes wake up from deep sleep when a known device asks
     * to connect. Harmless when the TV ignores it.
     */
    fun knockOnTv(address: String) {
        worker.execute {
            val adapter = manager?.adapter ?: return@execute
            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: Exception) {
                return@execute
            }
            try {
                device.fetchUuidsWithSdp()
            } catch (e: Exception) {
                Log.w(TAG, "SDP knock failed", e)
            }
            try {
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, object : android.bluetooth.BluetoothGattCallback() {},
                        BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, object : android.bluetooth.BluetoothGattCallback() {})
                }
                scope.launch {
                    delay(8000L)
                    worker.execute {
                        try {
                            gatt?.disconnect()
                            gatt?.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "close knock failed", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "LE knock failed", e)
            }
        }
    }

    // ---------------------------------------------------------------- phone name

    @Volatile private var nameUntil = 0L
    private var restoreNameJob: Job? = null

    /**
     * Shows the phone as "MI RC" until [untilMs], then puts the user's own name back.
     * The original name is kept in preferences so it can be restored even after a crash.
     * Runs on the worker thread.
     */
    private fun useMiRemoteName(untilMs: Long) {
        val adapter = manager?.adapter ?: return
        try {
            val current = adapter.name
            if (current != MI_REMOTE_NAME && prefs.getString(KEY_ORIGINAL_NAME, null) == null) {
                prefs.edit().putString(KEY_ORIGINAL_NAME, current).apply()
            }
            if (current != MI_REMOTE_NAME) {
                adapter.setName(MI_REMOTE_NAME)
                var waited = 0
                while (adapter.name != MI_REMOTE_NAME && waited < 1500) {
                    Thread.sleep(50)
                    waited += 50
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "rename failed", e)
            return
        }
        if (untilMs > nameUntil) nameUntil = untilMs
        scope.launch {
            restoreNameJob?.cancel()
            restoreNameJob = scope.launch {
                while (System.currentTimeMillis() < nameUntil) delay(1000L)
                restoreOriginalName()
            }
        }
    }

    /** Puts the phone's own Bluetooth name back (safe to call any time). */
    fun restoreOriginalName() {
        worker.execute {
            val original = prefs.getString(KEY_ORIGINAL_NAME, null) ?: return@execute
            try {
                val adapter = manager?.adapter ?: return@execute
                if (adapter.name == MI_REMOTE_NAME) adapter.setName(original)
                prefs.edit().remove(KEY_ORIGINAL_NAME).apply()
            } catch (e: Exception) {
                Log.w(TAG, "restore name failed", e)
            }
        }
    }

    fun stopAdvertising() {
        advertiseStopJob?.cancel()
        advertiseStopJob = null
        advertiseUntil = 0L
        nameUntil = minOf(nameUntil, System.currentTimeMillis())
        if (!advertising) return
        advertising = false
        listener.onAdvertisingChanged(false)
        worker.execute {
            try {
                manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.w(TAG, "stopAdvertising failed", e)
            }
        }
    }

    // ---------------------------------------------------------------- sending

    /** Queues one key press + release on the BLE link. Returns false when no TV is linked. */
    fun sendKey(reportId: Int, keyCode: Int, modifier: Int = HidDescriptor.MODIFIER_NONE): Boolean {
        if (hostDevice == null) return false
        sendQueue.trySend(reportId to HidDescriptor.buildPressReport(reportId, keyCode, modifier))
        sendQueue.trySend(reportId to HidDescriptor.buildReleaseReport(reportId))
        return true
    }

    /** Queues a raw report (used for typing text, which needs custom press/release pairs). */
    fun sendRaw(reportId: Int, bytes: ByteArray): Boolean {
        if (hostDevice == null) return false
        sendQueue.trySend(reportId to bytes)
        return true
    }

    private suspend fun runSendLoop() {
        for ((reportId, bytes) in sendQueue) {
            val device = hostDevice ?: continue
            val server = gattServer ?: continue
            val characteristic = reportChars[reportId] ?: continue
            val done = CompletableDeferred<Unit>()
            notifySent = done
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, characteristic, false, bytes)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = bytes
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "notify failed", e)
            }
            // Android allows one notification in flight; wait for it (or give up after 150 ms).
            withTimeoutOrNull(150L) { done.await() }
            delay(12L)
        }
    }

    fun forgetHost() {
        prefs.edit().remove(KEY_HOST).apply()
    }

    fun release() {
        stopAdvertising()
        restoreOriginalName()
        val server = gattServer
        gattServer = null
        hostDevice = null
        worker.execute {
            try {
                server?.close()
            } catch (e: Exception) {
                Log.w(TAG, "close failed", e)
            }
        }
        worker.shutdown()
    }
}
