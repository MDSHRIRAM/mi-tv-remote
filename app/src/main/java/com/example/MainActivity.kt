package com.example

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var btController: BluetoothRemoteController
    private lateinit var irHelper: IrBlasterHelper
    private lateinit var voiceHelper: VoiceInputHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        btController = BluetoothRemoteController(applicationContext)
        irHelper = IrBlasterHelper(applicationContext)
        voiceHelper = VoiceInputHelper(applicationContext)

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                var hasBtPermissions by remember {
                    mutableStateOf(checkBluetoothPermissions())
                }
                var hasAudioPermission by remember {
                    mutableStateOf(checkRecordAudioPermission())
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val granted = results.values.all { it } || checkBluetoothPermissions()
                    hasBtPermissions = granted
                    if (granted) {
                        btController.initialize()
                    }
                }

                val discoverableLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { }

                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasAudioPermission = isGranted || checkRecordAudioPermission()
                }

                // Initialize Bluetooth HID controller on startup if permissions are already granted
                LaunchedEffect(Unit) {
                    if (hasBtPermissions) {
                        btController.initialize()
                    }
                }

                // Refresh paired devices and permissions on resume
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val btGranted = checkBluetoothPermissions()
                            hasBtPermissions = btGranted
                            hasAudioPermission = checkRecordAudioPermission()
                            if (btGranted) {
                                btController.initialize()
                                btController.refreshPairedDevices()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                RemoteScreen(
                    btController = btController,
                    irHelper = irHelper,
                    voiceHelper = voiceHelper,
                    hasBluetoothPermissions = hasBtPermissions,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermissions = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_ADVERTISE
                                )
                            )
                        }
                    },
                    onRequestAudioPermission = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestDiscoverable = {
                        try {
                            discoverableLauncher.launch(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                            )
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::btController.isInitialized) {
            btController.release()
        }
        if (::voiceHelper.isInitialized) {
            voiceHelper.destroy()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
