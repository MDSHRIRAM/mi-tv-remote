package com.example

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalRemotePalette
import com.example.ui.theme.StatusIrAmber
import com.example.ui.theme.TextSecondarySlate

/** How the remote talks to the TV. Only one path is used per button press. */
enum class ControlMode { BLUETOOTH, IR }

/** Remembers the chosen mode between app launches. */
object ControlModeStore {
    private const val PREFS = "mi_tv_remote_prefs"
    private const val KEY = "control_mode"

    fun load(context: Context, hasIr: Boolean): ControlMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val mode = ControlMode.entries.firstOrNull { it.name == saved } ?: ControlMode.BLUETOOTH
        // A phone without an IR emitter can only use Bluetooth.
        return if (mode == ControlMode.IR && !hasIr) ControlMode.BLUETOOTH else mode
    }

    fun save(context: Context, mode: ControlMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, mode.name).apply()
    }
}

/** Bluetooth / IR switch. Shown only on phones that have an IR emitter. */
@Composable
fun ControlModeSelector(
    currentMode: ControlMode,
    onSelectMode: (ControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRemotePalette.current
    Surface(
        modifier = modifier.fillMaxWidth().testTag("control_mode_selector"),
        shape = RoundedCornerShape(14.dp),
        color = palette.backgroundBase.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, palette.buttonBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeChip(
                label = "Bluetooth",
                icon = Icons.Default.Bluetooth,
                selected = currentMode == ControlMode.BLUETOOTH,
                selectedColor = MaterialTheme.colorScheme.primary,
                onClick = { onSelectMode(ControlMode.BLUETOOTH) },
                tag = "mode_bluetooth_button",
                modifier = Modifier.weight(1f)
            )
            ModeChip(
                label = "IR Blaster",
                icon = Icons.Default.SettingsRemote,
                selected = currentMode == ControlMode.IR,
                selectedColor = StatusIrAmber,
                onClick = { onSelectMode(ControlMode.IR) },
                tag = "mode_ir_button",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalRemotePalette.current
    val contentColor = if (selected) palette.backgroundBase else TextSecondarySlate
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
