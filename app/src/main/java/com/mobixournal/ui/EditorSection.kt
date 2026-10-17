package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
        OptionGroup(
            title = "Default canvas",
            subtitle = "The canvas geometry newly created documents open on: fixed pages or an infinite board.",
            options = NewDocumentCanvas.entries,
            selected = settings.newDocumentCanvas,
            label = { it.label },
            onSelect = { onChange(settings.copy(newDocumentCanvas = it)) },
        )
        SettingsDivider()
        DefaultDimensionsControl(settings, onChange)
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

/** The default page width and height controls for new documents, with presets, unit toggle, and inputs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultDimensionsControl(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var unit by remember { mutableStateOf(SizeUnit.MM) }
    var widthText by remember(settings.defaultPageWidthPt, unit) {
        mutableStateOf(fmtDim(unit.fromPt(settings.defaultPageWidthPt)))
    }
    var heightText by remember(settings.defaultPageHeightPt, unit) {
        mutableStateOf(fmtDim(unit.fromPt(settings.defaultPageHeightPt)))
    }

    ControlHeader(
        "Default page dimensions",
        "Dimensions for new documents and newly created pages.",
        Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        for (preset in PAGE_PRESETS) {
            val selected = matchesPageSize(
                settings.defaultPageWidthPt,
                settings.defaultPageHeightPt,
                preset.widthPt,
                preset.heightPt,
            )
            FilterChip(
                selected = selected,
                onClick = {
                    onChange(settings.copy(
                        defaultPageWidthPt = preset.widthPt,
                        defaultPageHeightPt = preset.heightPt,
                    ))
                },
                label = { Text(preset.name) },
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Text("Units:", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        for (u in SizeUnit.entries) {
            TextButton(onClick = { unit = u }) {
                Text(
                    u.label,
                    fontWeight = if (u == unit) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = widthText,
            onValueChange = { entered ->
                widthText = entered
                entered.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                    val pt = unit.toPt(it).coerceIn(72.0, 14400.0)
                    onChange(settings.copy(defaultPageWidthPt = pt))
                }
            },
            label = { Text("Width") },
            suffix = { Text(unit.label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            val w = settings.defaultPageWidthPt
            val h = settings.defaultPageHeightPt
            onChange(settings.copy(defaultPageWidthPt = h, defaultPageHeightPt = w))
        }) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap width and height")
        }
        OutlinedTextField(
            value = heightText,
            onValueChange = { entered ->
                heightText = entered
                entered.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                    val pt = unit.toPt(it).coerceIn(72.0, 14400.0)
                    onChange(settings.copy(defaultPageHeightPt = pt))
                }
            },
            label = { Text("Height") },
            suffix = { Text(unit.label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}
