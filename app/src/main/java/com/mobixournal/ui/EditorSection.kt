package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Editor preferences: the tool a document opens in, and how edits snap. */
@Composable
fun EditorSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SwitchRow(
        title = "Snap to grid",
        subtitle = "Shape endpoints land on the page background's ruling.",
        checked = settings.snapToGrid,
        onCheckedChange = { onChange(settings.copy(snapToGrid = it)) },
    )
    SwitchRow(
        title = "Snap rotation",
        subtitle = "Rotating a selection steps in 15° increments.",
        checked = settings.snapRotation,
        onCheckedChange = { onChange(settings.copy(snapRotation = it)) },
    )
    OptionGroup(
        title = "Default tool",
        subtitle = "Which tool is active when a document opens.",
        options = DEFAULT_TOOL_CHOICES,
        selected = settings.defaultTool,
        label = { it.label },
        onSelect = { onChange(settings.copy(defaultTool = it)) },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))

    Text("Default page size", style = MaterialTheme.typography.titleSmall)
    Text(
        "Size for new documents and pages. Changes apply to newly created pages only.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))

    var widthText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.defaultPageWidthPt)) }
    OutlinedTextField(
        value = widthText,
        onValueChange = {
            widthText = it
            it.replace(',', '.').toDoubleOrNull()?.let { w ->
                onChange(settings.copy(defaultPageWidthPt = w.coerceIn(72.0, 14400.0)))
            }
        },
        label = { Text("Width (pt)") },
        supportingText = { Text("A4=595.3, Letter=612, A3=841.9") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )

    Spacer(Modifier.height(8.dp))

    var heightText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.defaultPageHeightPt)) }
    OutlinedTextField(
        value = heightText,
        onValueChange = {
            heightText = it
            it.replace(',', '.').toDoubleOrNull()?.let { h ->
                onChange(settings.copy(defaultPageHeightPt = h.coerceIn(72.0, 14400.0)))
            }
        },
        label = { Text("Height (pt)") },
        supportingText = { Text("A4=841.9, Letter=792, A3=1190.6") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
