package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Editor preferences, in the order a user meets them: what a new document starts as (tool, page
 * size), then the aids that tidy what they draw (snapping, shape recognition).
 */
@Composable
fun EditorSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("New documents") {
        OptionGroup(
            title = "Default tool",
            subtitle = "Which tool is active when a document opens.",
            options = DEFAULT_TOOL_CHOICES,
            selected = settings.defaultTool,
            label = { it.label },
            onSelect = { onChange(settings.copy(defaultTool = it)) },
        )
        SettingsDivider()
        DefaultPageSizeFields(settings, onChange)
    }
    SettingsGroup("Drawing aids") {
        SwitchRow(
            title = "Snap to grid",
            subtitle = "Shape endpoints land on the page background's ruling.",
            checked = settings.snapToGrid,
            onCheckedChange = { onChange(settings.copy(snapToGrid = it)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Snap rotation",
            subtitle = "Rotating a selection steps in 15° increments.",
            checked = settings.snapRotation,
            onCheckedChange = { onChange(settings.copy(snapRotation = it)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Shape recognition",
            subtitle = "Snap a finished freehand stroke to the shape it resembles — line, triangle, " +
                "rectangle or circle — using desktop Xournal++'s own recognizer. Anything " +
                "unrecognised stays as drawn.",
            checked = settings.recognizeShapes,
            onCheckedChange = { onChange(settings.copy(recognizeShapes = it)) },
        )
    }
}

/** The default page width and height for new documents, side by side, in points. */
@Composable
private fun DefaultPageSizeFields(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ControlHeader(
        "Default page size",
        "Size for new documents and pages. Changes apply to newly created pages only. " +
            "A4 = 595.3 × 841.9 pt, Letter = 612 × 792 pt, A3 = 841.9 × 1190.6 pt.",
        Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
    var widthText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.defaultPageWidthPt)) }
    var heightText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.defaultPageHeightPt)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = widthText,
            onValueChange = {
                widthText = it
                it.replace(',', '.').toDoubleOrNull()?.let { w ->
                    onChange(settings.copy(defaultPageWidthPt = w.coerceIn(72.0, 14400.0)))
                }
            },
            label = { Text("Width (pt)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = heightText,
            onValueChange = {
                heightText = it
                it.replace(',', '.').toDoubleOrNull()?.let { h ->
                    onChange(settings.copy(defaultPageHeightPt = h.coerceIn(72.0, 14400.0)))
                }
            },
            label = { Text("Height (pt)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}
