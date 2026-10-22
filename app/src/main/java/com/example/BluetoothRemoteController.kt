package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the current Bluetooth HID connection state to the Xiaomi Mi TV.
 */
sealed class HidConnectionState {
    data object Disconnected : HidConnectionState()
    data class Connecting(val deviceName: String, val address: String) : HidConnectionState()
    data class Connected(val deviceName: String, val address: String) : HidConnectionState()
}

/**
 * UI model for a paired Bluetooth device.
 */
data class PairedTvDevice(
    val name: String,
    val address: String,
    val isLikelyTv: Boolean,
    val rawDevice: BluetoothDevice
)

/**
 * Manages Android's native `BluetoothHidDevice` profile (API 28+) to turn the phone into a
 * standard, 100% offline Bluetooth HID Remote & Keyboard for Xiaomi Mi TV.
 */
@SuppressLint("MissingPermission", "NewApi")
class BluetoothRemoteController(private val context: Context) {

    companion object {
        private const val TAG = "BtRemoteController"
        private const val PREFS_NAME = "mi_tv_remote_prefs"
        private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"

        const val SDP_NAME = "Mi TV Smart Remote"
        const val SDP_DESCRIPTION = "Xiaomi Mi TV Bluetooth HID Remote & Keyboard"
        const val SDP_PROVIDER = "Android"
        private const val KEY_HOLD_DELAY_MS = 30L
        private const val PAIRING_WINDOW_MS = 120_000L
        private const val RECONNECT_WINDOW_MS = 30_000L
        private const val WAKE_WINDOW_MS = 45_000L
        private const val WAKE_BURST_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var lastTargetDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow<HidConnectionState>(HidConnectionState.Disconnected)
    val connectionState: StateFlow<HidConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedTvDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedTvDevice>> = _pairedDevices.asStateFlow()

    private val _isSdpRegistered = MutableStateFlow(false)
    val isSdpRegistered: StateFlow<Boolean> = _isSdpRegistered.asStateFlow()

    private val _lastActionLog = MutableStateFlow("Ready • 100% Offline Bluetooth HID & IR")
    val lastActionLog: StateFlow<String> = _lastActionLog.asStateFlow()

    private val _isDirectPairingActive = MutableStateFlow(false)
    val isDirectPairingActive: StateFlow<Boolean> = _isDirectPairingActive.asStateFlow()

    private val _isBleAdvertising = MutableStateFlow(false)
    val isBleAdvertising: StateFlow<Boolean> = _isBleAdvertising.asStateFlow()

    // ------------------------------------------------------------ BLE remote (main path)

    private val bleListener = object : BleHidPeripheral.Listener {
        override fun onHostConnected(device: BluetoothDevice) {
            val name = resolveDeviceName(device)
            _connectionState.value = HidConnectionState.Connected("$name • BLE", device.address)
            _lastActionLog.value = "Connected to $name (Bluetooth LE)"
            _isDirectPairingActive.value = false
        }

        override fun onHostDisconnected(device: BluetoothDevice) {
            if (connectedDevice == null) {
                _connectionState.value = HidConnectionState.Disconnected
            }
            _lastActionLog.value = "TV disconnected • it reconnects when you press a button"
        }

        override fun onAdvertisingChanged(active: Boolean) {
            _isBleAdvertising.value = active
        }

        override fun onAdvertiseFailed(unsupported: Boolean) {
            _isDirectPairingActive.value = false
            if (unsupported) {
                _lastActionLog.value = "This phone cannot act as a BLE remote • use Classic pairing"
            }
        }

        override fun onLog(message: String) {
            _lastActionLog.value = message
        }
    }

    private val ble = BleHidPeripheral(context, bleListener).also { it.restoreOriginalName() }

    /** True when a TV is linked over Bluetooth LE. */
    val isBleLinked: Boolean get() = ble.isHostConnected

    /** True once a TV has bonded with the BLE remote at least once. */
    val hasBleTv: Boolean get() = ble.savedHostAddress != null

    /**
     * Pairing mode: the phone advertises as a Bluetooth remote for 2 minutes. On the TV open
     * Settings → Remotes & Accessories → Add accessory and pick the phone. A TV that paired
     * before reconnects by itself.
     */
    fun startDirectTvConnect() {
        if (!hasBluetoothPermission()) return
        initialize()
        if (ble.isHostConnected) {
            _lastActionLog.value = "Already connected"
            return
        }
        ble.startAdvertising(PAIRING_WINDOW_MS)
        _isDirectPairingActive.value = true
        _lastActionLog.value = "Pairing on • TV: Remotes & Accessories → Add accessory"
        scope.launch {
            delay(PAIRING_WINDOW_MS)
            _isDirectPairingActive.value = false
        }
        // A TV paired earlier over classic Bluetooth can still be linked that way.
        val classicAddr = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null)
        val classicTarget = _pairedDevices.value.firstOrNull { it.address == classicAddr }?.rawDevice
        if (classicTarget != null && connectedDevice == null) connectToDevice(classicTarget)
    }

    fun dismissDirectPairing() {
        _isDirectPairingActive.value = false
        if (!ble.isHostConnected) ble.stopAdvertising()
    }

    /** A button was pressed while no TV is linked: call the known TV back. */
    fun reconnectKnownTv(): Boolean {
        if (!hasBluetoothPermission()) return false
        if (hasBleTv) {
            initialize()
            ble.startAdvertising(RECONNECT_WINDOW_MS)
            _lastActionLog.value = "Reconnecting to TV…"
            return true
        }
        val target = getPreferredTargetDevice() ?: return false
        return connectToDevice(target)
    }

    /** Forget the BLE TV (used when the user wants to pair a different TV). */
    fun forgetBleTv() {
        ble.forgetHost()
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    private val hidCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                super.onAppStatusChanged(pluggedDevice, registered)
                _isSdpRegistered.value = registered
                Log.d(TAG, "HID SDP Registered: $registered, pluggedDevice: ${pluggedDevice?.address}")

                if (registered) {
                    refreshPairedDevices()
                    val savedAddr = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null)
                    val autoTarget = pluggedDevice
                        ?: _pairedDevices.value.firstOrNull { it.address == savedAddr }?.rawDevice
                        ?: _pairedDevices.value.firstOrNull { it.isLikelyTv }?.rawDevice

                    if (autoTarget != null) {
                        lastTargetDevice = autoTarget
                        connectToDevice(autoTarget)
                    }
                } else {
                    _connectionState.value = HidConnectionState.Disconnected
                    connectedDevice = null
                }
            }

            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                super.onConnectionStateChanged(device, state)
                val safeName = resolveDeviceName(device)
                val safeAddr = device?.address ?: ""

                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevice = device
                        if (device != null) {
                            lastTargetDevice = device
                            prefs.edit().putString(KEY_LAST_DEVICE_ADDRESS, device.address).apply()
                        }
                        _connectionState.value = HidConnectionState.Connected(safeName, safeAddr)
                        _lastActionLog.value = "Connected to $safeName via Bluetooth HID"
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        if (ble.isHostConnected) return
                        _connectionState.value = HidConnectionState.Connecting(safeName, safeAddr)
                        _lastActionLog.value = "Connecting to $safeName…"
                    }
                    BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING -> {
                        if (connectedDevice?.address == device?.address) {
                            connectedDevice = null
                        }
                        if (!ble.isHostConnected) {
                            _connectionState.value = HidConnectionState.Disconnected
                            _lastActionLog.value = "Disconnected from $safeName"
                        }
                    }
                }
            }
        }
    } else {
        null
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val hid = proxy as? BluetoothHidDevice ?: return
                hidDevice = hid
                registerHidSdp(hid)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedDevice = null
                _isSdpRegistered.value = false
                _connectionState.value = HidConnectionState.Disconnected
            }
        }
    }

    /**
     * Initializes the Bluetooth HID Device profile proxy and registers the SDP record.
     */
    fun initialize() {
        if (!hasBluetoothPermission()) {
            _lastActionLog.value = "Waiting for Bluetooth permission…"
            return
        }
        refreshPairedDevices()
        val adapter = bluetoothAdapter ?: run {
            _lastActionLog.value = "Bluetooth hardware unavailable on this device"
            return
        }
        if (!adapter.isEnabled) {
            _lastActionLog.value = "Bluetooth is turned off"
            return
        }
        // BLE remote (works from Android 5). If a TV paired before, let it reconnect.
        if (hasBleTv && !ble.isHostConnected && !_isBleAdvertising.value) {
            ble.startAdvertising(RECONNECT_WINDOW_MS)
        } else {
            ble.start()
        }
        // Classic Bluetooth remote needs Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (hidDevice != null && _isSdpRegistered.value) {
            return
        }
        try {
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire HID_DEVICE profile proxy", e)
            _lastActionLog.value = "HID initialization error: ${e.localizedMessage ?: "Unknown"}"
        }
    }

    private fun registerHidSdp(hid: BluetoothHidDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasBluetoothPermission()) return
        try {
            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                SDP_NAME,
                SDP_DESCRIPTION,
                SDP_PROVIDER,
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidDescriptor.COMBINED_HID_DESCRIPTOR
            )
            val qosOut = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800,
                9,
                0,
                11250,
                BluetoothHidDeviceAppQosSettings.MAX
            )
            val callback = hidCallback ?: return
            hid.registerApp(sdpSettings, null, qosOut, callbackExecutor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HID SDP settings", e)
        }
    }

    /**
     * Refreshes the list of paired Bluetooth devices, sorting likely Xiaomi Mi TVs to the top.
     */
    fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) return
        val adapter = bluetoothAdapter ?: return
        try {
            val bonded = adapter.bondedDevices.orEmpty()
            val mapped = bonded.map { device ->
                val name = resolveDeviceName(device)
                val majorClass = device.bluetoothClass?.majorDeviceClass
                val isAvDevice = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                val isTvName = name.contains("Mi", ignoreCase = true) ||
                    name.contains("Xiaomi", ignoreCase = true) ||
                    name.contains("TV", ignoreCase = true) ||
                    name.contains("PatchWall", ignoreCase = true) ||
                    name.contains("Box", ignoreCase = true)
                PairedTvDevice(
                    name = name,
                    address = device.address,
                    isLikelyTv = isAvDevice || isTvName,
                    rawDevice = device
                )
            }.sortedWith(
                compareByDescending<PairedTvDevice> { it.isLikelyTv }
                    .thenBy { it.name.lowercase() }
            )
            _pairedDevices.value = mapped

            if (lastTargetDevice == null) {
                val savedAddr = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null)
                lastTargetDevice = mapped.firstOrNull { it.address == savedAddr }?.rawDevice
                    ?: mapped.firstOrNull { it.isLikelyTv }?.rawDevice
                    ?: mapped.firstOrNull()?.rawDevice
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query bonded Bluetooth devices", e)
        }
    }

    /**
     * Connects to the specified paired Mi TV device using the Bluetooth HID profile.
     */
    fun connectToDevice(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasBluetoothPermission()) return false
        lastTargetDevice = device
        prefs.edit().putString(KEY_LAST_DEVICE_ADDRESS, device.address).apply()

        val hid = hidDevice
        if (hid == null) {
            initialize()
            return false
        }
        return try {
            val name = resolveDeviceName(device)
            _connectionState.value = HidConnectionState.Connecting(name, device.address)
            val initiated = hid.connect(device)
            if (!initiated) {
                _connectionState.value = HidConnectionState.Disconnected
                _lastActionLog.value = "Could not initiate HID link to $name"
            }
            initiated
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            _connectionState.value = HidConnectionState.Disconnected
            false
        }
    }

    /**
     * Disconnects from the currently active Bluetooth HID host.
     */
    fun disconnectCurrentDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !hasBluetoothPermission()) return
        val device = connectedDevice ?: lastTargetDevice ?: return
        try {
            hidDevice?.disconnect(device)
            connectedDevice = null
            _connectionState.value = HidConnectionState.Disconnected
            _lastActionLog.value = "Disconnected from ${resolveDeviceName(device)}"
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting device", e)
        }
    }

    /**
     * Sends a key-down HID report followed by a key-up release report after a 30ms delay.
     */
    fun sendKey(
        reportId: Int,
        keyCode: Int,
        modifier: Int = HidDescriptor.MODIFIER_NONE,
        actionLabel: String? = null
    ): Boolean {
        if (!hasBluetoothPermission()) return false
        if (ble.sendKey(reportId, keyCode, modifier)) {
            _lastActionLog.value = actionLabel ?: "Sent key 0x${keyCode.toString(16).uppercase()}"
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val hid = hidDevice ?: return false
        val target = connectedDevice ?: return false

        return try {
            val pressBytes = HidDescriptor.buildPressReport(reportId, keyCode, modifier)
            val releaseBytes = HidDescriptor.buildReleaseReport(reportId)
            val sentDown = hid.sendReport(target, reportId, pressBytes)
            scope.launch {
                delay(KEY_HOLD_DELAY_MS)
                try {
                    hid.sendReport(target, reportId, releaseBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send release report", e)
                }
            }
            val hexCode = "0x" + keyCode.toString(16).uppercase()
            _lastActionLog.value = actionLabel ?: "Sent HID Report #$reportId ($hexCode)"
            sentDown
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send HID report", e)
            false
        }
    }

    /**
     * Dual-stage Bluetooth HID Wake & Power Toggle:
     * 1. If disconnected, attempts `hidDevice.connect(targetDevice)` to wake the Mi TV Bluetooth host stack.
     * 2. Sends System Control Wake (`0x83` on Report ID 3 & Consumer `0x0083` on Report ID 2)
     *    followed by Consumer Control Power (`0x0030` on Report ID 2).
     */
    fun wakeAndPowerToggle(explicitDevice: BluetoothDevice? = null): Boolean {
        if (!hasBluetoothPermission()) return false

        // BLE linked = TV is on: Power turns it off.
        if (ble.isHostConnected) {
            ble.sendKey(HidDescriptor.REPORT_ID_CONSUMER, HidDescriptor.CONSUMER_POWER)
            _lastActionLog.value = "Power • sent by Bluetooth LE"
            return true
        }
        // BLE TV known but not linked = TV is probably in standby. Advertise like the official
        // remote does when a key is pressed; if the TV wakes and reconnects, send wake-up keys.
        if (hasBleTv) {
            ble.onNextHostConnect = {
                ble.sendKey(HidDescriptor.REPORT_ID_SYSTEM, HidDescriptor.SYSTEM_WAKE_UP)
                ble.sendKey(HidDescriptor.REPORT_ID_CONSUMER, HidDescriptor.CONSUMER_SYSTEM_WAKE)
                _lastActionLog.value = "TV reconnected • wake-up sent"
            }
            // 1) the official remote's "power" advertisement, 2) knock on the TV's address,
            // 3) stay reconnectable so the TV can link up once it is awake.
            ble.startWakeBurst(WAKE_BURST_MS)
            ble.savedHostAddress?.let { ble.knockOnTv(it) }
            ble.startAdvertising(WAKE_WINDOW_MS)
            _connectionState.value = HidConnectionState.Connecting("TV", ble.savedHostAddress ?: "")
            _lastActionLog.value = "Power • calling the TV over Bluetooth LE…"
            scope.launch {
                delay(WAKE_WINDOW_MS)
                if (!ble.isHostConnected && connectedDevice == null) {
                    _connectionState.value = HidConnectionState.Disconnected
                    _lastActionLog.value = "TV did not answer • see README (standby wake)"
                }
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return hasBleTv
        val hid = hidDevice ?: run {
            initialize()
            return hasBleTv
        }
        // With a BLE TV known, only use classic for a TV that was really linked over classic.
        val classicAddr = prefs.getString(KEY_LAST_DEVICE_ADDRESS, null)
        val target = explicitDevice ?: connectedDevice ?: if (hasBleTv) {
            _pairedDevices.value.firstOrNull { it.address == classicAddr }?.rawDevice
        } else {
            lastTargetDevice
        }
        if (target == null) return hasBleTv

        // Already linked = TV is on, so Power means "turn off". Not linked = TV is
        // probably in standby, so only send wake codes (a Power toggle right after
        // waking would switch it straight back off).
        val wasConnected = connectedDevice != null

        scope.launch {
            if (connectedDevice == null && target != null) {
                try {
                    _connectionState.value = HidConnectionState.Connecting(
                        resolveDeviceName(target),
                        target.address
                    )
                    hid.connect(target)
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-connect on wake failed", e)
                }
                // Wait up to 3 s for the TV to accept the link.
                var waited = 0L
                while (connectedDevice == null && waited < 3000L) {
                    delay(100L)
                    waited += 100L
                }
            }

            val activeDevice = connectedDevice ?: target
            if (activeDevice != null && wasConnected) {
                try {
                    hid.sendReport(
                        activeDevice,
                        HidDescriptor.REPORT_ID_CONSUMER,
                        HidDescriptor.buildConsumerPressReport(HidDescriptor.CONSUMER_POWER)
                    )
                    delay(KEY_HOLD_DELAY_MS)
                    hid.sendReport(
                        activeDevice,
                        HidDescriptor.REPORT_ID_CONSUMER,
                        HidDescriptor.buildConsumerReleaseReport()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending power report", e)
                }
            } else if (activeDevice != null) {
                try {
                    // 1. Send System Control Wake Up (0x83 on Report ID 3)
                    hid.sendReport(
                        activeDevice,
                        HidDescriptor.REPORT_ID_SYSTEM,
                        HidDescriptor.buildSystemPressReport(HidDescriptor.SYSTEM_WAKE_UP)
                    )
                    delay(KEY_HOLD_DELAY_MS)
                    hid.sendReport(
                        activeDevice,
                        HidDescriptor.REPORT_ID_SYSTEM,
                        HidDescriptor.buildSystemReleaseReport()
                    )
                    delay(25L)

                    // 2. Send Consumer Control System Wake (0x0083 on Report ID 2)
                    hid.sendReport(
                        activeDevice,
                        HidDescriptor.REPORT_ID_CONSUMER,
                        HidDescriptor.buildConsumerPressReport(HidDescriptor.CONSUMER_SYSTEM_WAKE)
                    )
                    delay(KEY_HOLD_DELAY_MS)
                    hid.sendReport(
                        activeDevice,
                        HidDescriptor.REPORT_ID_CONSUMER,
                        HidDescriptor.buildConsumerReleaseReport()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending wake/power reports", e)
                }
            }
        }
        return connectedDevice != null || target != null
    }

    /**
     * Types a full text string on the Mi TV by sending sequential USB HID Keyboard reports.
     */
    fun sendTextString(text: String, sendEnterAfter: Boolean = false) {
        if (!hasBluetoothPermission()) return
        if (ble.isHostConnected) {
            for (ch in text) {
                val (modifier, keyCode) = HidDescriptor.charToHidKey(ch) ?: continue
                ble.sendKey(HidDescriptor.REPORT_ID_KEYBOARD, keyCode, modifier)
            }
            if (sendEnterAfter) ble.sendKey(HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEY_ENTER_OK)
            _lastActionLog.value = "Typed \"${text.take(22)}\" via Bluetooth LE"
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val hid = hidDevice ?: return
        val target = connectedDevice ?: return

        scope.launch {
            for (ch in text) {
                val mapped = HidDescriptor.charToHidKey(ch) ?: continue
                val (modifier, keyCode) = mapped
                try {
                    hid.sendReport(
                        target,
                        HidDescriptor.REPORT_ID_KEYBOARD,
                        HidDescriptor.buildKeyboardPressReport(keyCode, modifier)
                    )
                    delay(24L)
                    hid.sendReport(
                        target,
                        HidDescriptor.REPORT_ID_KEYBOARD,
                        HidDescriptor.buildKeyboardReleaseReport()
                    )
                    delay(18L)
                } catch (e: Exception) {
                    Log.w(TAG, "Error typing character: $ch", e)
                }
            }
            if (sendEnterAfter) {
                delay(30L)
                sendKey(
                    reportId = HidDescriptor.REPORT_ID_KEYBOARD,
                    keyCode = HidDescriptor.KEY_ENTER_OK,
                    actionLabel = "Typed \"${text.take(18)}\" + ENTER"
                )
            } else {
                _lastActionLog.value = "Typed \"${text.take(22)}\" via BT HID"
            }
        }
    }

    fun updateActionLog(message: String) {
        _lastActionLog.value = message
    }

    fun getPreferredTargetDevice(): BluetoothDevice? = connectedDevice ?: lastTargetDevice

    private fun resolveDeviceName(device: BluetoothDevice?): String {
        if (device == null) return "Mi TV"
        return try {
            val name = device.name
            if (name.isNullOrBlank()) device.address else name
        } catch (e: Exception) {
            device.address
        }
    }

    /**
     * Unregisters the HID app and closes the Bluetooth profile proxy on cleanup.
     */
    fun release() {
        ble.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                hidDevice?.let { hid ->
                    if (hasBluetoothPermission()) {
                        hid.unregisterApp()
                    }
                    bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing BluetoothHidDevice proxy", e)
            }
        }
        hidDevice = null
        connectedDevice = null
    }
}
