package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalRemotePalette
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.PowerCrimson
import com.example.ui.theme.PowerCrimsonDark
import com.example.ui.theme.StatusIrAmber
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate

/**
 * Triggers crisp physical click haptic feedback.
 */
fun triggerRemoteHaptic(
    context: Context,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    heavy: Boolean = false
) {
    try {
        haptic.performHapticFeedback(
            if (heavy) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        )
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(
                        if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(if (heavy) 30L else 16L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    btController: BluetoothRemoteController,
    irHelper: IrBlasterHelper,
    voiceHelper: VoiceInputHelper,
    hasBluetoothPermissions: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestDiscoverable: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val connectionState by btController.connectionState.collectAsStateWithLifecycle()
    val pairedDevices by btController.pairedDevices.collectAsStateWithLifecycle()
    val isSdpRegistered by btController.isSdpRegistered.collectAsStateWithLifecycle()
    val lastActionLog by btController.lastActionLog.collectAsStateWithLifecycle()
    val isDirectPairingActive by btController.isDirectPairingActive.collectAsStateWithLifecycle()

    val hasIrHardware = remember { irHelper.hasIrEmitter() }
    var controlMode by remember { mutableStateOf(ControlModeStore.load(context, hasIrHardware)) }
    val forceIrMode = controlMode == ControlMode.IR
    var navMode by rememberSaveable { mutableStateOf(NavPadMode.DPAD) }
    var showDeviceBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showKeyboardDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceSearchDialog by rememberSaveable { mutableStateOf(false) }

    val isBtConnected = connectionState is HidConnectionState.Connected

    // Sends one button press over the selected path only (Bluetooth OR IR, never both).
    fun dispatchRemoteKey(
        label: String,
        reportId: Int,
        hidKeyCode: Int,
        irCommand: IrBlasterHelper.IrCommand? = null
    ) {
        triggerRemoteHaptic(context, haptic, heavy = false)
        if (forceIrMode) {
            val irSent = irCommand != null && irHelper.transmitCommand(irCommand)
            btController.updateActionLog(
                if (irSent) "$label • sent by IR" else "$label • no IR code for this button"
            )
            return
        }
        val btSent = btController.sendKey(
            reportId = reportId,
            keyCode = hidKeyCode,
            actionLabel = label
        )
        if (!btSent) {
            if (btController.reconnectKnownTv()) {
                btController.updateActionLog("$label pressed • Connecting to TV…")
            } else {
                btController.updateActionLog("$label • Tap 'Direct Connect' to link TV")
            }
        }
    }

    // Power: one signal on the selected path, so the TV is not switched twice.
    fun dispatchPowerAndWake() {
        triggerRemoteHaptic(context, haptic, heavy = true)
        if (forceIrMode) {
            val irSent = irHelper.transmitPowerWake()
            btController.updateActionLog(if (irSent) "Power • sent by IR" else "Power • IR failed")
            return
        }
        val btInitiated = btController.wakeAndPowerToggle()
        if (!btInitiated) btController.updateActionLog("Power • Tap 'Direct Connect' to link TV")
    }

    MyApplicationTheme(
        connectionState = connectionState,
        forceIrMode = forceIrMode
    ) {
        val palette = LocalRemotePalette.current

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = palette.backgroundBase,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(palette.ambientRadialGlow, palette.backgroundBase),
                            center = Offset(500f, 320f),
                            radius = 1100f
                        )
                    )
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("remote_root_container"),
                contentAlignment = Alignment.Center
            ) {
                // Fixed Minimal Remote Chassis: zero scrolling, weight-based vertical distribution
                Surface(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(32.dp),
                            ambientColor = palette.chassisGlowShadow,
                            spotColor = palette.chassisGlowShadow
                        )
                        .testTag("remote_chassis_card"),
                    shape = RoundedCornerShape(32.dp),
                    color = palette.chassisSurface,
                    border = BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(palette.chassisRimTop, palette.chassisRimBottom)
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. TOP HEADER: Mi TV Status, 1-Tap Direct Connect, Crimson Power Button
                        MinimalTopBar(
                            connectionState = connectionState,
                            isDirectPairingActive = isDirectPairingActive,
                            onDirectConnect = {
                                triggerRemoteHaptic(context, haptic, heavy = true)
                                if (!hasBluetoothPermissions) {
                                    onRequestPermissions()
                                }
                                btController.startDirectTvConnect()
                            },
                            onOpenDeviceList = {
                                triggerRemoteHaptic(context, haptic)
                                if (!hasBluetoothPermissions) {
                                    onRequestPermissions()
                                }
                                btController.refreshPairedDevices()
                                showDeviceBottomSheet = true
                            },
                            onPowerClick = { dispatchPowerAndWake() }
                        )

                        if (hasIrHardware) {
                            Spacer(modifier = Modifier.weight(0.35f))
                            ControlModeSelector(
                                currentMode = controlMode,
                                onSelectMode = { mode ->
                                    triggerRemoteHaptic(context, haptic)
                                    controlMode = mode
                                    ControlModeStore.save(context, mode)
                                    btController.updateActionLog(
                                        if (mode == ControlMode.IR) "Mode: IR Blaster" else "Mode: Bluetooth"
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(0.55f))

                        // 2. NAVIGATION MODE TOGGLE: D-PAD ARROWS vs. VIRTUAL JOYSTICK
                        NavPadModeSelector(
                            currentMode = navMode,
                            onSelectMode = { mode ->
                                triggerRemoteHaptic(context, haptic)
                                navMode = mode
                            },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.weight(0.75f))

                        // 3. CENTRAL NAVIGATION PAD (Proportional weighted zone for D-Pad or Joystick)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(4.6f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val padSize = minOf(maxWidth, maxHeight, 236.dp)

                            if (navMode == NavPadMode.DPAD) {
                                AnimatedTactileDpad(
                                    sizeDp = padSize,
                                    onUp = {
                                        dispatchRemoteKey(
                                            "Up",
                                            HidDescriptor.REPORT_ID_KEYBOARD,
                                            HidDescriptor.KEY_DPAD_UP,
                                            IrBlasterHelper.IrCommand.DPAD_UP
                                        )
                                    },
                                    onDown = {
                                        dispatchRemoteKey(
                                            "Down",
                                            HidDescriptor.REPORT_ID_KEYBOARD,
                                            HidDescriptor.KEY_DPAD_DOWN,
                                            IrBlasterHelper.IrCommand.DPAD_DOWN
                                        )
                                    },
                                    onLeft = {
                                        dispatchRemoteKey(
                                            "Left",
                                            HidDescriptor.REPORT_ID_KEYBOARD,
                                            HidDescriptor.KEY_DPAD_LEFT,
                                            IrBlasterHelper.IrCommand.DPAD_LEFT
                                        )
                                    },
                                    onRight = {
                                        dispatchRemoteKey(
                                            "Right",
                                            HidDescriptor.REPORT_ID_KEYBOARD,
                                            HidDescriptor.KEY_DPAD_RIGHT,
                                            IrBlasterHelper.IrCommand.DPAD_RIGHT
                                        )
                                    },
                                    onOk = {
                                        dispatchRemoteKey(
                                            "OK",
                                            HidDescriptor.REPORT_ID_KEYBOARD,
                                            HidDescriptor.KEY_ENTER_OK,
                                            IrBlasterHelper.IrCommand.OK
                                        )
                                    }
                                )
                            } else {
                                VirtualJoystickPad(
                                    sizeDp = padSize,
                                    onDirectionStep = { label, repId, code, irCmd ->
                                        dispatchRemoteKey(label, repId, code, irCmd)
                                    },
                                    onTapOk = {
                                        dispatchRemoteKey(
                                            "OK",
                                            HidDescriptor.REPORT_ID_KEYBOARD,
                                            HidDescriptor.KEY_ENTER_OK,
                                            IrBlasterHelper.IrCommand.OK
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(0.75f))

                        // 4. MI TV CORE 3-BUTTON ROW: BACK, HOME, PATCHWALL (Hold Home + PatchWall to Pair)
                        MiCoreNavigationRow(
                            onBack = {
                                dispatchRemoteKey(
                                    "Back",
                                    HidDescriptor.REPORT_ID_CONSUMER,
                                    HidDescriptor.CONSUMER_BACK,
                                    IrBlasterHelper.IrCommand.BACK
                                )
                            },
                            onHome = {
                                dispatchRemoteKey(
                                    "Home",
                                    HidDescriptor.REPORT_ID_CONSUMER,
                                    HidDescriptor.CONSUMER_HOME,
                                    IrBlasterHelper.IrCommand.HOME
                                )
                            },
                            onPatchWall = {
                                dispatchRemoteKey(
                                    "PatchWall",
                                    HidDescriptor.REPORT_ID_CONSUMER,
                                    HidDescriptor.CONSUMER_MENU,
                                    IrBlasterHelper.IrCommand.MENU
                                )
                            },
                            onDualButtonDirectPair = {
                                triggerRemoteHaptic(context, haptic, heavy = true)
                                if (!hasBluetoothPermissions) {
                                    onRequestPermissions()
                                }
                                btController.startDirectTvConnect()
                            }
                        )

                        Spacer(modifier = Modifier.weight(0.7f))

                        // 5. BOTTOM CONTROLS: Volume Pill Rocker, Voice Mic, Mute, Keyboard
                        BottomControlsRow(
                            onVolumeUp = {
                                dispatchRemoteKey(
                                    "Vol +",
                                    HidDescriptor.REPORT_ID_CONSUMER,
                                    HidDescriptor.CONSUMER_VOLUME_UP,
                                    IrBlasterHelper.IrCommand.VOL_UP
                                )
                            },
                            onVolumeDown = {
                                dispatchRemoteKey(
                                    "Vol -",
                                    HidDescriptor.REPORT_ID_CONSUMER,
                                    HidDescriptor.CONSUMER_VOLUME_DOWN,
                                    IrBlasterHelper.IrCommand.VOL_DOWN
                                )
                            },
                            onVoiceSearch = {
                                triggerRemoteHaptic(context, haptic)
                                if (!hasAudioPermission) {
                                    onRequestAudioPermission()
                                    btController.updateActionLog("Microphone access requested")
                                } else {
                                    showVoiceSearchDialog = true
                                }
                            },
                            onMute = {
                                dispatchRemoteKey(
                                    "Mute",
                                    HidDescriptor.REPORT_ID_CONSUMER,
                                    HidDescriptor.CONSUMER_MUTE,
                                    IrBlasterHelper.IrCommand.MUTE
                                )
                            },
                            onKeyboard = {
                                triggerRemoteHaptic(context, haptic)
                                showKeyboardDialog = true
                            }
                        )

                        Spacer(modifier = Modifier.weight(0.45f))

                        // 6. MINIMAL SINGLE-LINE TELEMETRY FOOTER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.backgroundBase.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = lastActionLog,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBtConnected) "HID READY" else "OFFLINE",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isBtConnected) palette.primaryAccent else TextMutedSlate,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Direct Pairing Beacon Animation Overlay
        if (isDirectPairingActive) {
            DirectPairingOverlay(
                onDismiss = { btController.dismissDirectPairing() },
                onClassicPairing = {
                    // Fallback: make the phone visible to classic Bluetooth scans for 2 minutes.
                    btController.updateActionLog("Classic pairing • allow 'visible' on the phone")
                    onRequestDiscoverable()
                }
            )
        }

        // Quick Keyboard Dialog
        if (showKeyboardDialog) {
            QuickKeyboardDialog(
                isBtConnected = isBtConnected,
                onDismiss = { showKeyboardDialog = false },
                onSendText = { text, sendEnter ->
                    triggerRemoteHaptic(context, haptic)
                    btController.sendTextString(text, sendEnterAfter = sendEnter)
                    if (sendEnter) showKeyboardDialog = false
                },
                onSendBackspace = {
                    dispatchRemoteKey("Backspace", HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEY_BACKSPACE)
                },
                onSendEnter = {
                    dispatchRemoteKey("Enter", HidDescriptor.REPORT_ID_KEYBOARD, HidDescriptor.KEY_ENTER_OK)
                },
                onOpenVoiceSearch = {
                    showKeyboardDialog = false
                    if (!hasAudioPermission) {
                        onRequestAudioPermission()
                    } else {
                        showVoiceSearchDialog = true
                    }
                }
            )
        }

        // Voice-to-Text Speech Search Dialog
        if (showVoiceSearchDialog) {
            VoiceSearchDialog(
                voiceHelper = voiceHelper,
                isBtConnected = isBtConnected,
                onDismiss = { showVoiceSearchDialog = false },
                onSendQuery = { query, sendEnter ->
                    triggerRemoteHaptic(context, haptic, heavy = true)
                    btController.sendTextString(query, sendEnterAfter = sendEnter)
                    val label = if (sendEnter) "\"$query\" + ENTER" else "\"$query\""
                    btController.updateActionLog("Voice query typed: $label")
                }
            )
        }

        // Paired Devices Bottom Sheet
        if (showDeviceBottomSheet) {
            DeviceSelectionBottomSheet(
                pairedDevices = pairedDevices,
                connectionState = connectionState,
                isSdpRegistered = isSdpRegistered,
                hasIrHardware = hasIrHardware,
                onDismiss = { showDeviceBottomSheet = false },
                onRefresh = {
                    triggerRemoteHaptic(context, haptic)
                    btController.refreshPairedDevices()
                },
                onSelectDevice = { pairedDevice ->
                    triggerRemoteHaptic(context, haptic, heavy = true)
                    btController.connectToDevice(pairedDevice.rawDevice)
                    showDeviceBottomSheet = false
                },
                onDisconnect = {
                    triggerRemoteHaptic(context, haptic)
                    btController.disconnectCurrentDevice()
                },
                onOpenBluetoothSettings = {
                    triggerRemoteHaptic(context, haptic)
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    } catch (_: Exception) {}
                }
            )
        }
    }
}

/**
 * Minimal top bar with connection badge, Direct Connect 1-tap button, and red Power button.
 */
@Composable
private fun MinimalTopBar(
    connectionState: HidConnectionState,
    isDirectPairingActive: Boolean,
    onDirectConnect: () -> Unit,
    onOpenDeviceList: () -> Unit,
    onPowerClick: () -> Unit
) {
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary
    val isConnected = connectionState is HidConnectionState.Connected

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Branding + Connection Dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenDeviceList)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) palette.primaryAccent else StatusIrAmber)
            )
            Column {
                Text(
                    text = "MI TV REMOTE",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryWhite
                )
                Text(
                    text = when (connectionState) {
                        is HidConnectionState.Connected -> connectionState.deviceName
                        is HidConnectionState.Connecting -> "Connecting…"
                        else -> if (isDirectPairingActive) "Pairing…" else "Direct Pair Ready"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isConnected) palette.primaryAccent else TextSecondarySlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Direct Connect Button + Red Power
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDirectConnect,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("direct_connect_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) palette.buttonSurface else accentColor,
                    contentColor = if (isConnected) palette.primaryAccent else ObsidianBackground
                ),
                border = BorderStroke(1.dp, if (isConnected) palette.buttonBorder else accentColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothSearching,
                    contentDescription = stringResource(R.string.direct_connect_btn),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "Connected" else stringResource(R.string.direct_connect_btn),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Power Button (Crimson Circular)
            val powerInteraction = remember { MutableInteractionSource() }
            val isPowerPressed by powerInteraction.collectIsPressedAsState()
            val powerScale by animateFloatAsState(
                targetValue = if (isPowerPressed) 0.88f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "powerScale"
            )

            Box(
                modifier = Modifier
                    .scale(powerScale)
                    .size(42.dp)
                    .shadow(10.dp, CircleShape, ambientColor = PowerCrimson, spotColor = PowerCrimson)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PowerCrimson, PowerCrimsonDark)
                        )
                    )
                    .border(1.5.dp, Color(0xFFFF7A70), CircleShape)
                    .clickable(
                        interactionSource = powerInteraction,
                        indication = null,
                        onClick = onPowerClick
                    )
                    .testTag("power_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.power_button_desc),
                    tint = TextPrimaryWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Animated D-Pad Ring with high-contrast, deeply noticeable arrow click animations.
 */
@Composable
private fun AnimatedTactileDpad(
    sizeDp: Dp,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit
) {
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(sizeDp)
            .shadow(
                elevation = 18.dp,
                shape = CircleShape,
                ambientColor = palette.chassisGlowShadow,
                spotColor = palette.chassisGlowShadow
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(palette.dpadOuterStart, palette.dpadOuterEnd)
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(palette.chassisRimTop, palette.chassisRimBottom)
                ),
                shape = CircleShape
            )
            .testTag("animated_dpad_ring"),
        contentAlignment = Alignment.Center
    ) {
        val ringStrokeColor = palette.dpadRingStroke
        val wellColor = palette.backgroundBase

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
                color = ringStrokeColor,
                radius = radius * 0.92f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = wellColor,
                radius = radius * 0.44f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        AnimatedArrowButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDesc = stringResource(R.string.dpad_up_desc),
            testTag = "dpad_up_button",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
            onClick = onUp
        )

        AnimatedArrowButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDesc = stringResource(R.string.dpad_down_desc),
            testTag = "dpad_down_button",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            onClick = onDown
        )

        AnimatedArrowButton(
            icon = Icons.Default.KeyboardArrowLeft,
            contentDesc = stringResource(R.string.dpad_left_desc),
            testTag = "dpad_left_button",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp),
            onClick = onLeft
        )

        AnimatedArrowButton(
            icon = Icons.Default.KeyboardArrowRight,
            contentDesc = stringResource(R.string.dpad_right_desc),
            testTag = "dpad_right_button",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            onClick = onRight
        )

        val okInteraction = remember { MutableInteractionSource() }
        val isOkPressed by okInteraction.collectIsPressedAsState()
        val okScale by animateFloatAsState(
            targetValue = if (isOkPressed) 0.86f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "okScale"
        )

        Box(
            modifier = Modifier
                .scale(okScale)
                .size(sizeDp * 0.40f)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (isOkPressed) {
                        SolidColor(accentColor)
                    } else {
                        Brush.radialGradient(
                            colors = listOf(palette.dpadOuterEnd, palette.dpadOuterStart)
                        )
                    }
                )
                .border(
                    width = 2.dp,
                    color = if (isOkPressed) TextPrimaryWhite else accentColor,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = okInteraction,
                    indication = null,
                    onClick = onOk
                )
                .testTag("dpad_ok_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.dpad_ok_label),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    letterSpacing = 1.4.sp
                ),
                color = if (isOkPressed) ObsidianBackground else TextPrimaryWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AnimatedArrowButton(
    icon: ImageVector,
    contentDesc: String,
    testTag: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "arrowScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(58.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) accentColor else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (isPressed) ObsidianBackground else TextPrimaryWhite,
            modifier = Modifier
                .scale(if (isPressed) 1.15f else 1f)
                .size(32.dp)
        )
    }
}

/**
 * Classic Mi TV Core 3-Button Navigation Row: Back, Home, PatchWall.
 */
@Composable
private fun MiCoreNavigationRow(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPatchWall: () -> Unit,
    onDualButtonDirectPair: () -> Unit
) {
    var isHomePressed by remember { mutableStateOf(false) }
    var isPatchWallPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isHomePressed, isPatchWallPressed) {
        if (isHomePressed && isPatchWallPressed) {
            onDualButtonDirectPair()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactNavButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = "BACK",
            testTag = "nav_back_button",
            modifier = Modifier.weight(1f),
            onClick = onBack
        )
        Spacer(modifier = Modifier.width(10.dp))
        CompactNavButton(
            icon = Icons.Default.Home,
            label = "HOME",
            testTag = "nav_home_button",
            isHighlighted = true,
            modifier = Modifier.weight(1f),
            onPressedChanged = { isHomePressed = it },
            onClick = onHome
        )
        Spacer(modifier = Modifier.width(10.dp))
        CompactNavButton(
            icon = Icons.Default.Dashboard,
            label = "PATCHWALL",
            testTag = "nav_patchwall_button",
            modifier = Modifier.weight(1f),
            onPressedChanged = { isPatchWallPressed = it },
            onClick = onPatchWall
        )
    }
}

@Composable
private fun CompactNavButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onPressedChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressedChanged(isPressed)
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        label = "btnScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = when {
            isPressed -> accentColor
            isHighlighted -> palette.dpadOuterStart
            else -> palette.buttonSurface
        },
        border = BorderStroke(
            1.dp,
            if (isPressed || isHighlighted) accentColor else palette.buttonBorder
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isPressed) ObsidianBackground else if (isHighlighted) accentColor else TextPrimaryWhite,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = if (isPressed) ObsidianBackground else TextPrimaryWhite
            )
        }
    }
}

/**
 * Bottom Controls Row: Horizontal Volume Rocker, Voice Mic, Mute, Keyboard.
 */
@Composable
private fun BottomControlsRow(
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onVoiceSearch: () -> Unit,
    onMute: () -> Unit,
    onKeyboard: () -> Unit
) {
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Horizontal Volume Rocker
        Surface(
            modifier = Modifier
                .height(48.dp)
                .width(132.dp),
            shape = RoundedCornerShape(24.dp),
            color = palette.buttonSurface,
            border = BorderStroke(1.dp, palette.buttonBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val minusInteraction = remember { MutableInteractionSource() }
                val isMinusPressed by minusInteraction.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isMinusPressed) accentColor else Color.Transparent)
                        .clickable(
                            interactionSource = minusInteraction,
                            indication = null,
                            onClick = onVolumeDown
                        )
                        .testTag("vol_down_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Vol -",
                        tint = if (isMinusPressed) ObsidianBackground else TextPrimaryWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "VOL",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondarySlate,
                    fontWeight = FontWeight.Bold
                )

                val plusInteraction = remember { MutableInteractionSource() }
                val isPlusPressed by plusInteraction.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isPlusPressed) accentColor else Color.Transparent)
                        .clickable(
                            interactionSource = plusInteraction,
                            indication = null,
                            onClick = onVolumeUp
                        )
                        .testTag("vol_up_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Vol +",
                        tint = if (isPlusPressed) ObsidianBackground else TextPrimaryWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Voice Search Mic Button
        val micInteraction = remember { MutableInteractionSource() }
        val isMicPressed by micInteraction.collectIsPressedAsState()
        val micScale by animateFloatAsState(
            targetValue = if (isMicPressed) 0.88f else 1f,
            label = "micScale"
        )
        Box(
            modifier = Modifier
                .scale(micScale)
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isMicPressed) accentColor else accentColor.copy(alpha = 0.20f)
                )
                .border(1.5.dp, accentColor, CircleShape)
                .clickable(
                    interactionSource = micInteraction,
                    indication = null,
                    onClick = onVoiceSearch
                )
                .testTag("voice_search_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.voice_button_desc),
                tint = if (isMicPressed) ObsidianBackground else accentColor,
                modifier = Modifier.size(22.dp)
            )
        }

        // Mute Button
        val muteInteraction = remember { MutableInteractionSource() }
        val isMutePressed by muteInteraction.collectIsPressedAsState()
        val muteScale by animateFloatAsState(
            targetValue = if (isMutePressed) 0.88f else 1f,
            label = "muteScale"
        )
        Box(
            modifier = Modifier
                .scale(muteScale)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isMutePressed) accentColor else palette.buttonSurface)
                .border(1.dp, if (isMutePressed) accentColor else palette.buttonBorder, CircleShape)
                .clickable(
                    interactionSource = muteInteraction,
                    indication = null,
                    onClick = onMute
                )
                .testTag("nav_mute_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = "Mute",
                tint = if (isMutePressed) ObsidianBackground else TextPrimaryWhite,
                modifier = Modifier.size(20.dp)
            )
        }

        // Keyboard Button
        val kbInteraction = remember { MutableInteractionSource() }
        val isKbPressed by kbInteraction.collectIsPressedAsState()
        val kbScale by animateFloatAsState(
            targetValue = if (isKbPressed) 0.88f else 1f,
            label = "kbScale"
        )
        Box(
            modifier = Modifier
                .scale(kbScale)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isKbPressed) accentColor else palette.buttonSurface)
                .border(1.dp, if (isKbPressed) accentColor else palette.buttonBorder, CircleShape)
                .clickable(
                    interactionSource = kbInteraction,
                    indication = null,
                    onClick = onKeyboard
                )
                .testTag("keyboard_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = stringResource(R.string.keyboard_button_desc),
                tint = if (isKbPressed) ObsidianBackground else TextPrimaryWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
