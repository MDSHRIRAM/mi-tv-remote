package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalRemotePalette
import com.example.ui.theme.PowerCrimson
import com.example.ui.theme.StatusIrAmber
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate

/**
 * Voice Search Dialog allowing users to speak search queries on their phone microphone
 * and have the transcribed text automatically typed to the Xiaomi Mi TV via Bluetooth HID.
 */
@Composable
fun VoiceSearchDialog(
    voiceHelper: VoiceInputHelper,
    isBtConnected: Boolean,
    onDismiss: () -> Unit,
    onSendQuery: (query: String, sendEnter: Boolean) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary

    val voiceState by voiceHelper.voiceState.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var autoSendEnabled by rememberSaveable { mutableStateOf(true) }

    // Start listening on launch if in Idle state
    LaunchedEffect(Unit) {
        if (voiceState is VoiceInputState.Idle) {
            voiceHelper.startListening(
                onSuccess = { query ->
                    searchQuery = query
                    if (autoSendEnabled && query.isNotBlank()) {
                        onSendQuery(query, true)
                        onDismiss()
                    }
                },
                onError = { /* handled in voiceState */ }
            )
        }
    }

    // Keep searchQuery in sync with recognized results
    LaunchedEffect(voiceState) {
        when (val state = voiceState) {
            is VoiceInputState.Listening -> {
                if (state.partialText.isNotBlank()) {
                    searchQuery = state.partialText
                }
            }
            is VoiceInputState.Processing -> {
                if (state.text.isNotBlank()) {
                    searchQuery = state.text
                }
            }
            is VoiceInputState.Success -> {
                searchQuery = state.query
            }
            else -> {}
        }
    }

    AlertDialog(
        onDismissRequest = {
            voiceHelper.cancel()
            onDismiss()
        },
        containerColor = palette.chassisSurface,
        titleContentColor = TextPrimaryWhite,
        textContentColor = TextPrimaryWhite,
        modifier = Modifier.testTag("voice_search_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.voice_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = {
                        voiceHelper.cancel()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("voice_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dialog_close),
                        tint = TextSecondarySlate
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connection indicator message
                Text(
                    text = if (isBtConnected) {
                        "Voice queries are typed directly into the active TV search field via Bluetooth HID."
                    } else {
                        "Bluetooth HID disconnected • Reconnect to type voice input to Mi TV."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isBtConnected) TextSecondarySlate else StatusIrAmber,
                    textAlign = TextAlign.Center
                )

                // Central Animated Pulsing Microphone Visualizer Orb
                VoiceMicOrbSection(
                    voiceState = voiceState,
                    accentColor = accentColor,
                    secondaryColor = secondaryAccent,
                    onMicClick = {
                        when (voiceState) {
                            is VoiceInputState.Listening -> {
                                voiceHelper.stopListening()
                            }
                            else -> {
                                voiceHelper.startListening(
                                    onSuccess = { query ->
                                        searchQuery = query
                                        if (autoSendEnabled && query.isNotBlank()) {
                                            onSendQuery(query, true)
                                            onDismiss()
                                        }
                                    },
                                    onError = {}
                                )
                            }
                        }
                    }
                )

                // Status text caption
                val statusMessage = when (val state = voiceState) {
                    is VoiceInputState.Listening -> {
                        if (state.partialText.isNotBlank()) "\"${state.partialText}\"" else stringResource(R.string.voice_listening)
                    }
                    is VoiceInputState.Processing -> stringResource(R.string.voice_processing)
                    is VoiceInputState.Success -> "Recognized: \"${state.query}\""
                    is VoiceInputState.Error -> state.message
                    is VoiceInputState.Idle -> stringResource(R.string.voice_tap_to_speak)
                }

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (voiceState) {
                        is VoiceInputState.Error -> PowerCrimson
                        is VoiceInputState.Listening -> accentColor
                        else -> TextPrimaryWhite
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Editable / Recognized query input box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.voice_recognized_query_hint),
                            color = TextMutedSlate
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_query_input"),
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
                            if (searchQuery.isNotBlank()) {
                                onSendQuery(searchQuery, true)
                                onDismiss()
                            }
                        }
                    )
                )

                // Auto-send Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.buttonSurface.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.voice_send_automatically),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryWhite,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoSendEnabled,
                        onCheckedChange = { autoSendEnabled = it },
                        modifier = Modifier.testTag("voice_auto_send_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.backgroundBase,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = TextMutedSlate,
                            uncheckedTrackColor = palette.buttonBorder
                        )
                    )
                }

                // Error Retry Action
                if (voiceState is VoiceInputState.Error) {
                    OutlinedButton(
                        onClick = {
                            voiceHelper.startListening(
                                onSuccess = { query ->
                                    searchQuery = query
                                    if (autoSendEnabled && query.isNotBlank()) {
                                        onSendQuery(query, true)
                                        onDismiss()
                                    }
                                },
                                onError = {}
                            )
                        },
                        border = BorderStroke(1.dp, accentColor),
                        modifier = Modifier.testTag("voice_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry Voice Input",
                            color = accentColor,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        onSendQuery(searchQuery, true)
                        voiceHelper.cancel()
                        onDismiss()
                    }
                },
                enabled = searchQuery.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = palette.backgroundBase
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("voice_send_enter_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.voice_send_enter),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        onSendQuery(searchQuery, false)
                        voiceHelper.cancel()
                        onDismiss()
                    } else {
                        voiceHelper.cancel()
                        onDismiss()
                    }
                },
                border = BorderStroke(1.dp, palette.buttonBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("voice_type_only_button")
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) stringResource(R.string.voice_send_text_only) else stringResource(R.string.dialog_close),
                    color = TextPrimaryWhite,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}

/**
 * Pulsing Audio Visualizer and Microphone Orb.
 */
@Composable
private fun VoiceMicOrbSection(
    voiceState: VoiceInputState,
    accentColor: Color,
    secondaryColor: Color,
    onMicClick: () -> Unit
) {
    val isListening = voiceState is VoiceInputState.Listening
    val rmsLevel = (voiceState as? VoiceInputState.Listening)?.rmsDb ?: 0f

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    Box(
        modifier = Modifier
            .size(130.dp)
            .clickable(onClick = onMicClick)
            .testTag("voice_mic_orb"),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic expanding audio ripple rings when microphone is listening
        if (isListening) {
            Canvas(modifier = Modifier.fillMaxWidth()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = 40.dp.toPx()
                val dynamicRadius = baseRadius + (rmsLevel * 4.dp.toPx()) * pulseScale

                // Outer aura ring
                drawCircle(
                    color = accentColor.copy(alpha = 0.20f),
                    radius = dynamicRadius.coerceAtMost(size.width / 2f),
                    center = center
                )
                // Middle sound wave ring
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.35f),
                    radius = (dynamicRadius * 0.82f).coerceAtMost(size.width / 2f),
                    center = center
                )
            }
        }

        // Center Microphone Button
        Box(
            modifier = Modifier
                .scale(if (isListening) pulseScale.coerceAtMost(1.12f) else 1f)
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isListening) {
                            listOf(accentColor, secondaryColor)
                        } else {
                            listOf(Color(0xFF2B354C), Color(0xFF19202E))
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    color = if (isListening) TextPrimaryWhite else accentColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (voiceState is VoiceInputState.Error) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(R.string.voice_button_desc),
                tint = if (isListening) Color(0xFF0B0D12) else TextPrimaryWhite,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
