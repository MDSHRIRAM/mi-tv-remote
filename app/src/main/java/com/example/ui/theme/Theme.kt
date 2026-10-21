package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.HidConnectionState

/**
 * Dynamic hardware surface and glow tokens generated from the active connection state.
 */
@Immutable
data class DynamicRemotePalette(
    val backgroundBase: Color,
    val ambientRadialGlow: Color,
    val chassisSurface: Color,
    val chassisRimTop: Color,
    val chassisRimBottom: Color,
    val chassisGlowShadow: Color,
    val surfaceVariant: Color,
    val buttonSurface: Color,
    val buttonBorder: Color,
    val dpadOuterStart: Color,
    val dpadOuterEnd: Color,
    val dpadRingStroke: Color,
    val trackpadStart: Color,
    val trackpadEnd: Color,
    val trackpadGridDot: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val isBluetoothGlowActive: Boolean
)

val LocalRemotePalette = staticCompositionLocalOf {
    DynamicRemotePalette(
        backgroundBase = ObsidianBackground,
        ambientRadialGlow = CharcoalRadialGlow,
        chassisSurface = CharcoalSurface,
        chassisRimTop = Color(0xFF333C52),
        chassisRimBottom = Color(0xFF181D27),
        chassisGlowShadow = Color.Black,
        surfaceVariant = TitaniumSurfaceVariant,
        buttonSurface = ElevatedButtonSurface,
        buttonBorder = ElevatedButtonBorder,
        dpadOuterStart = Color(0xFF283144),
        dpadOuterEnd = Color(0xFF191F2C),
        dpadRingStroke = Color(0xFF343F57),
        trackpadStart = Color(0xFF1F2637),
        trackpadEnd = Color(0xFF131822),
        trackpadGridDot = Color(0xFF2D364B),
        primaryAccent = MiOrange,
        secondaryAccent = MiOrangeGlow,
        isBluetoothGlowActive = false
    )
}

@Composable
private fun animateThemeColor(target: Color, label: String): Color {
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = label
    )
    return animated
}

/**
 * Dynamically generates and animates the Material 3 Dark ColorScheme and hardware surface tokens
 * based on the active Bluetooth HID connection state (`Disconnected`, `Connecting`, `Connected`)
 * and IR override mode.
 */
@Composable
fun MyApplicationTheme(
    connectionState: HidConnectionState = HidConnectionState.Disconnected,
    forceIrMode: Boolean = false,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isBtConnected = connectionState is HidConnectionState.Connected && !forceIrMode
    val isBtConnecting = connectionState is HidConnectionState.Connecting && !forceIrMode

    val backgroundBase = animateThemeColor(
        target = if (isBtConnected) BtConnectedBackground else ObsidianBackground,
        label = "backgroundBase"
    )
    val ambientRadialGlow = animateThemeColor(
        target = when {
            isBtConnected -> BtConnectedRadialGlow
            isBtConnecting -> BtConnectingRadialGlow
            else -> CharcoalRadialGlow
        },
        label = "ambientRadialGlow"
    )
    val chassisSurface = animateThemeColor(
        target = when {
            isBtConnected -> BtConnectedChassisSurface
            isBtConnecting -> BtConnectingChassisSurface
            else -> CharcoalSurface
        },
        label = "chassisSurface"
    )
    val chassisRimTop = animateThemeColor(
        target = when {
            isBtConnected -> BtElectricBlue.copy(alpha = 0.65f)
            isBtConnecting -> MiOrange.copy(alpha = 0.55f)
            else -> Color(0xFF333C52)
        },
        label = "chassisRimTop"
    )
    val chassisRimBottom = animateThemeColor(
        target = when {
            isBtConnected -> Color(0xFF102440)
            isBtConnecting -> BtConnectingBorder
            else -> Color(0xFF181D27)
        },
        label = "chassisRimBottom"
    )
    val chassisGlowShadow = animateThemeColor(
        target = when {
            isBtConnected -> BtElectricBlue.copy(alpha = 0.5f)
            isBtConnecting -> MiOrange.copy(alpha = 0.35f)
            else -> Color.Black
        },
        label = "chassisGlowShadow"
    )
    val surfaceVariant = animateThemeColor(
        target = if (isBtConnected) BtConnectedSurfaceVariant else TitaniumSurfaceVariant,
        label = "surfaceVariant"
    )
    val buttonSurface = animateThemeColor(
        target = if (isBtConnected) BtConnectedButtonSurface else ElevatedButtonSurface,
        label = "buttonSurface"
    )
    val buttonBorder = animateThemeColor(
        target = if (isBtConnected) BtConnectedButtonBorder else ElevatedButtonBorder,
        label = "buttonBorder"
    )
    val dpadOuterStart = animateThemeColor(
        target = if (isBtConnected) Color(0xFF1F385C) else Color(0xFF283144),
        label = "dpadOuterStart"
    )
    val dpadOuterEnd = animateThemeColor(
        target = if (isBtConnected) Color(0xFF112038) else Color(0xFF191F2C),
        label = "dpadOuterEnd"
    )
    val dpadRingStroke = animateThemeColor(
        target = if (isBtConnected) Color(0xFF356299) else Color(0xFF343F57),
        label = "dpadRingStroke"
    )
    val trackpadStart = animateThemeColor(
        target = if (isBtConnected) Color(0xFF172C4A) else Color(0xFF1F2637),
        label = "trackpadStart"
    )
    val trackpadEnd = animateThemeColor(
        target = if (isBtConnected) Color(0xFF0D1A2E) else Color(0xFF131822),
        label = "trackpadEnd"
    )
    val trackpadGridDot = animateThemeColor(
        target = if (isBtConnected) Color(0xFF2B4D7E) else Color(0xFF2D364B),
        label = "trackpadGridDot"
    )
    val primaryAccent = animateThemeColor(
        target = when {
            isBtConnected -> BtElectricBlue
            forceIrMode -> StatusIrAmber
            else -> MiOrange
        },
        label = "primaryAccent"
    )
    val secondaryAccent = animateThemeColor(
        target = when {
            isBtConnected -> BtElectricCyan
            forceIrMode -> StatusIrAmber
            else -> MiOrangeGlow
        },
        label = "secondaryAccent"
    )

    val dynamicPalette = DynamicRemotePalette(
        backgroundBase = backgroundBase,
        ambientRadialGlow = ambientRadialGlow,
        chassisSurface = chassisSurface,
        chassisRimTop = chassisRimTop,
        chassisRimBottom = chassisRimBottom,
        chassisGlowShadow = chassisGlowShadow,
        surfaceVariant = surfaceVariant,
        buttonSurface = buttonSurface,
        buttonBorder = buttonBorder,
        dpadOuterStart = dpadOuterStart,
        dpadOuterEnd = dpadOuterEnd,
        dpadRingStroke = dpadRingStroke,
        trackpadStart = trackpadStart,
        trackpadEnd = trackpadEnd,
        trackpadGridDot = trackpadGridDot,
        primaryAccent = primaryAccent,
        secondaryAccent = secondaryAccent,
        isBluetoothGlowActive = isBtConnected
    )

    val colorScheme = darkColorScheme(
        primary = primaryAccent,
        onPrimary = ObsidianBackground,
        primaryContainer = surfaceVariant,
        onPrimaryContainer = TextPrimaryWhite,
        secondary = secondaryAccent,
        onSecondary = ObsidianBackground,
        secondaryContainer = buttonSurface,
        onSecondaryContainer = TextPrimaryWhite,
        tertiary = StatusIrAmber,
        background = backgroundBase,
        onBackground = TextPrimaryWhite,
        surface = chassisSurface,
        onSurface = TextPrimaryWhite,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = TextSecondarySlate,
        outline = buttonBorder,
        error = PowerCrimson,
        onError = TextPrimaryWhite
    )

    CompositionLocalProvider(LocalRemotePalette provides dynamicPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
