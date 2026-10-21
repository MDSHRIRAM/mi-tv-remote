package com.example

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalRemotePalette
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Navigation layout mode for the central pad: Arrows (D-Pad) or Smooth Virtual Joystick.
 */
enum class NavPadMode {
    DPAD,
    JOYSTICK
}

/**
 * Compact 2-way mode selector pill to switch between D-Pad Arrows and Virtual Joystick.
 */
@Composable
fun NavPadModeSelector(
    currentMode: NavPadMode,
    onSelectMode: (NavPadMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("nav_mode_selector"),
        shape = RoundedCornerShape(14.dp),
        color = palette.backgroundBase.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, palette.buttonBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dpadSelected = currentMode == NavPadMode.DPAD
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (dpadSelected) accentColor else Color.Transparent)
                    .clickable { onSelectMode(NavPadMode.DPAD) }
                    .testTag("mode_dpad_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = if (dpadSelected) palette.backgroundBase else TextSecondarySlate,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_mode_dpad_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dpadSelected) palette.backgroundBase else TextSecondarySlate,
                        fontWeight = if (dpadSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            val joystickSelected = currentMode == NavPadMode.JOYSTICK
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (joystickSelected) accentColor else Color.Transparent)
                    .clickable { onSelectMode(NavPadMode.JOYSTICK) }
                    .testTag("mode_joystick_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ControlCamera,
                        contentDescription = null,
                        tint = if (joystickSelected) palette.backgroundBase else TextSecondarySlate,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_mode_joystick_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (joystickSelected) palette.backgroundBase else TextSecondarySlate,
                        fontWeight = if (joystickSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Authentic circular Touchpad & Virtual Joystick that fits inside the exact same footprint as the D-Pad.
 * Supports smooth fluid dragging with physical spring snap-back, directional thresholds triggering D-Pad steps,
 * and tap-in-center to trigger OK!
 */
@Composable
fun VirtualJoystickPad(
    sizeDp: Dp = 230.dp,
    onDirectionStep: (label: String, reportId: Int, keyCode: Int, irCmd: IrBlasterHelper.IrCommand) -> Unit,
    onTapOk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val palette = LocalRemotePalette.current
    val accentColor = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary

    val maxRadiusPx = remember(sizeDp, density) {
        with(density) { (sizeDp / 2 - 36.dp).toPx() }
    }
    val triggerThresholdPx = remember(density) {
        with(density) { 32.dp.toPx() }
    }

    var targetOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var activeDirection by remember { mutableStateOf<String?>(null) }

    // Smooth spring animation for joystick thumb knob
    val animatedOffset by animateOffsetAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "joystickSpring"
    )

    Box(
        modifier = modifier
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onTapOk()
                    }
                )
            }
            .pointerInput(maxRadiusPx, triggerThresholdPx) {
                var hasFiredForCurrentPush = false

                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        hasFiredForCurrentPush = false
                    },
                    onDragEnd = {
                        isDragging = false
                        targetOffset = Offset.Zero
                        activeDirection = null
                        hasFiredForCurrentPush = false
                    },
                    onDragCancel = {
                        isDragging = false
                        targetOffset = Offset.Zero
                        activeDirection = null
                        hasFiredForCurrentPush = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = targetOffset + dragAmount
                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)

                        // Clamp thumb knob inside maximum circle radius
                        targetOffset = if (distance > maxRadiusPx) {
                            val angle = kotlin.math.atan2(newOffset.y, newOffset.x)
                            Offset(
                                (cos(angle) * maxRadiusPx),
                                (sin(angle) * maxRadiusPx)
                            )
                        } else {
                            newOffset
                        }

                        // Determine directional quadrant
                        if (distance >= triggerThresholdPx) {
                            val dir = if (abs(targetOffset.x) >= abs(targetOffset.y)) {
                                if (targetOffset.x > 0) "RIGHT" else "LEFT"
                            } else {
                                if (targetOffset.y > 0) "DOWN" else "UP"
                            }

                            if (!hasFiredForCurrentPush || activeDirection != dir) {
                                activeDirection = dir
                                hasFiredForCurrentPush = true

                                when (dir) {
                                    "UP" -> onDirectionStep(
                                        "Up",
                                        HidDescriptor.REPORT_ID_KEYBOARD,
                                        HidDescriptor.KEY_DPAD_UP,
                                        IrBlasterHelper.IrCommand.DPAD_UP
                                    )
                                    "DOWN" -> onDirectionStep(
                                        "Down",
                                        HidDescriptor.REPORT_ID_KEYBOARD,
                                        HidDescriptor.KEY_DPAD_DOWN,
                                        IrBlasterHelper.IrCommand.DPAD_DOWN
                                    )
                                    "LEFT" -> onDirectionStep(
                                        "Left",
                                        HidDescriptor.REPORT_ID_KEYBOARD,
                                        HidDescriptor.KEY_DPAD_LEFT,
                                        IrBlasterHelper.IrCommand.DPAD_LEFT
                                    )
                                    "RIGHT" -> onDirectionStep(
                                        "Right",
                                        HidDescriptor.REPORT_ID_KEYBOARD,
                                        HidDescriptor.KEY_DPAD_RIGHT,
                                        IrBlasterHelper.IrCommand.DPAD_RIGHT
                                    )
                                }
                            }
                        } else {
                            activeDirection = null
                            hasFiredForCurrentPush = false
                        }
                    }
                )
            }
            .testTag("virtual_joystick_pad"),
        contentAlignment = Alignment.Center
    ) {
        val outerStrokeColor = palette.dpadRingStroke
        val innerCircleColor = palette.backgroundBase.copy(alpha = 0.5f)

        // Precision Background Concentric Crosshairs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Concentric boundary circles
            drawCircle(
                color = outerStrokeColor,
                radius = radius * 0.92f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = innerCircleColor,
                radius = radius * 0.52f,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Crosshair lines
            drawLine(
                color = outerStrokeColor.copy(alpha = 0.45f),
                start = Offset(center.x, 14.dp.toPx()),
                end = Offset(center.x, size.height - 14.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = outerStrokeColor.copy(alpha = 0.45f),
                start = Offset(14.dp.toPx(), center.y),
                end = Offset(size.width - 14.dp.toPx(), center.y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Directional Glow Indicators on outer edge
        DirectionalIndicatorIcon(
            icon = Icons.Default.KeyboardArrowUp,
            isActive = activeDirection == "UP",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
        DirectionalIndicatorIcon(
            icon = Icons.Default.KeyboardArrowDown,
            isActive = activeDirection == "DOWN",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
        DirectionalIndicatorIcon(
            icon = Icons.Default.KeyboardArrowLeft,
            isActive = activeDirection == "LEFT",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        )
        DirectionalIndicatorIcon(
            icon = Icons.Default.KeyboardArrowRight,
            isActive = activeDirection == "RIGHT",
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )

        // Floating Joystick Thumb Knob
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedOffset.x.roundToInt(),
                        animatedOffset.y.roundToInt()
                    )
                }
                .size(76.dp)
                .shadow(
                    elevation = if (isDragging) 14.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = if (isDragging) accentColor else Color.Black,
                    spotColor = if (isDragging) accentColor else Color.Black
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = if (isDragging) {
                            listOf(accentColor, palette.dpadOuterStart)
                        } else {
                            listOf(palette.dpadOuterStart, palette.dpadOuterEnd)
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    color = if (isDragging) TextPrimaryWhite else accentColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.dpad_ok_label),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    letterSpacing = 1.2.sp
                ),
                color = if (isDragging) palette.backgroundBase else TextPrimaryWhite,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DirectionalIndicatorIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.35f else 1f,
        label = "indicatorScale"
    )

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isActive) accentColor else TextMutedSlate.copy(alpha = 0.5f),
        modifier = modifier
            .scale(scale)
            .size(22.dp)
    )
}
