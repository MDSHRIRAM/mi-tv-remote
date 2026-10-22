package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BtElectricBlue
import com.example.ui.theme.LocalRemotePalette
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.PowerCrimson
import com.example.ui.theme.StatusIrAmber
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate

/**
 * "Find your TV": searches for nearby Bluetooth devices, lists TVs first and pairs on tap.
 */
@Composable
fun FindTvDialog(
    devices: List<PairedTvDevice>,
    isScanning: Boolean,
    status: String,
    connectedAddress: String?,
    onSearchAgain: () -> Unit,
    onPick: (PairedTvDevice) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.chassisSurface,
        titleContentColor = TextPrimaryWhite,
        textContentColor = TextPrimaryWhite,
        modifier = Modifier.testTag("direct_pairing_overlay"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    tint = accentColor
                )
                Text(
                    text = stringResource(R.string.find_tv_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.find_tv_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )
                if (isScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor
                    )
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices, key = { it.address }) { item ->
                        val isConnected = item.address == connectedAddress
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPick(item) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isConnected) accentColor.copy(alpha = 0.18f) else palette.buttonSurface,
                            border = BorderStroke(1.dp, if (item.isLikelyTv) accentColor else palette.buttonBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isLikelyTv) Icons.Default.Tv else Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = if (item.isLikelyTv) accentColor else TextSecondarySlate
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimaryWhite
                                    )
                                    Text(
                                        text = when {
                                            isConnected -> "Connected"
                                            item.isLikelyTv && item.isBonded -> "Likely your TV • paired"
                                            item.isLikelyTv -> "Likely your TV"
                                            item.isBonded -> "Paired"
                                            else -> "Nearby device"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondarySlate
                                    )
                                }
                                if (isConnected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Connected",
                                        tint = accentColor
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onOpenBluetoothSettings) {
                    Text(stringResource(R.string.find_tv_open_settings), color = TextSecondarySlate)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSearchAgain,
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.find_tv_search_again))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_close), color = TextSecondarySlate)
            }
        }
    )
}

/**
 * Paired Mi TV Selection Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionBottomSheet(
    pairedDevices: List<PairedTvDevice>,
    connectionState: HidConnectionState,
    isSdpRegistered: Boolean,
    hasIrHardware: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectDevice: (PairedTvDevice) -> Unit,
    onDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.chassisSurface,
        contentColor = TextPrimaryWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsRemote,
                        contentDescription = null,
                        tint = accentColor
                    )
                    Text(
                        text = stringResource(R.string.device_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimaryWhite
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_devices_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.device_sheet_refresh),
                        tint = accentColor
                    )
                }
            }

            // Direct Pairing Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant),
                border = BorderStroke(1.dp, palette.buttonBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Pairing",
                        style = MaterialTheme.typography.titleSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap 'Find TV' on the main screen. The app searches for your TV and pairs with it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }
            }

            // Device list
            if (pairedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No paired devices found. Tap 'Find TV' on the main screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pairedDevices, key = { it.address }) { item ->
                        val isCurrentConnected =
                            (connectionState as? HidConnectionState.Connected)?.address == item.address
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectDevice(item) }
                                .testTag("device_item_${item.address}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrentConnected) {
                                BtElectricBlue.copy(alpha = 0.18f)
                            } else {
                                palette.buttonSurface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentConnected) BtElectricBlue else palette.buttonBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (item.isLikelyTv) Icons.Default.Tv else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isCurrentConnected) BtElectricBlue else accentColor
                                    )
                                    Column {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimaryWhite
                                        )
                                        Text(
                                            text = item.address,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondarySlate
                                        )
                                    }
                                }
                                if (isCurrentConnected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Connected",
                                        tint = BtElectricBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.buttonBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("open_bt_settings_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Text(
                        text = "Phone BT Settings",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                }

                if (connectionState is HidConnectionState.Connected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("disconnect_tv_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PowerCrimson)
                    ) {
                        Text(
                            text = "Disconnect",
                            style = MaterialTheme.typography.labelLarge,
                            color = PowerCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Quick Keyboard Text Input Dialog with Voice Dictation Shortcut.
 */
@Composable
fun QuickKeyboardDialog(
    isBtConnected: Boolean,
    onDismiss: () -> Unit,
    onSendText: (String, Boolean) -> Unit,
    onSendBackspace: () -> Unit,
    onSendEnter: () -> Unit,
    onOpenVoiceSearch: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary
    var textValue by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.chassisSurface,
        titleContentColor = TextPrimaryWhite,
        textContentColor = TextPrimaryWhite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = accentColor
                )
                Text(
                    text = stringResource(R.string.keyboard_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isBtConnected) {
                        "Type below to send keystrokes to TV via Bluetooth HID."
                    } else {
                        "Pair your TV first (tap 'Find TV') so keystrokes reach the TV."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isBtConnected) TextSecondarySlate else StatusIrAmber
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.keyboard_dialog_hint),
                            color = TextMutedSlate
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = onOpenVoiceSearch,
                            modifier = Modifier.testTag("keyboard_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = stringResource(R.string.voice_button_desc),
                                tint = accentColor
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keyboard_text_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = palette.buttonBorder,
                        focusedTextColor = TextPrimaryWhite,
                        unfocusedTextColor = TextPrimaryWhite,
                        cursorColor = accentColor
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textValue.isNotEmpty()) {
                                onSendText(textValue, true)
                                textValue = ""
                            }
                        }
                    )
                )

                // Quick Remote Key Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSendBackspace,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("keyboard_backspace_button"),
                        border = BorderStroke(1.dp, palette.buttonBorder)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.keyboard_backspace),
                            tint = TextPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.keyboard_backspace),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimaryWhite
                        )
                    }

                    OutlinedButton(
                        onClick = onSendEnter,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("keyboard_enter_button"),
                        border = BorderStroke(1.dp, palette.buttonBorder)
                    ) {
                        Text(
                            text = "TV Enter",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimaryWhite
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textValue.isNotEmpty()) {
                        onSendText(textValue, true)
                        textValue = ""
                    } else {
                        onSendEnter()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = palette.backgroundBase
                ),
                modifier = Modifier.testTag("keyboard_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.keyboard_send_enter),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("keyboard_close_button")
            ) {
                Text(
                    text = stringResource(R.string.dialog_close),
                    color = TextSecondarySlate
                )
            }
        }
    )
}
