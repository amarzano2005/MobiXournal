package com.mobixournal.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.BarrelDoubleAction
import com.mobixournal.render.StrokePrecision

/**
 * Stylus behaviours: palm rejection, hover preview, barrel-button action and pressure "feel".
 *
 * Tool and colour shortcuts deliberately live in their own [ShortcutsSection] — they are a mapping
 * table of keys, not an input behaviour, and keeping them apart keeps this page about how the pen
 * *feels*.
 */
@Composable
fun StylusSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SwitchRow(
        title = "Finger draws",
        subtitle = "Off: fingers only pan/zoom and never use any tool — stylus only.",
        checked = settings.fingerDraws,
        onCheckedChange = { onChange(settings.copy(fingerDraws = it)) },
    )
    SwitchRow(
        title = "Hover preview",
        subtitle = "Show a ring where a hovering stylus will land.",
        checked = settings.showHover,
        onCheckedChange = { onChange(settings.copy(showHover = it)) },
    )
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    OptionGroup(
        title = "Barrel button",
        subtitle = "Action while the stylus side-button is held, whatever the tool.",
        options = BarrelAction.values().toList(),
        selected = settings.barrelAction,
        label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        onSelect = { onChange(settings.copy(barrelAction = it)) },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    OptionGroup(
        title = "Barrel double-click",
        subtitle = "Action for a rapid double-click of the side-button, with the tip off the glass. " +
            "A pen whose own firmware reports that double-click as a single click (the Honor Choice " +
            "Pencil does) runs this on each click instead.",
        options = BarrelDoubleAction.values().toList(),
        selected = settings.barrelDoubleAction,
        label = { it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) },
        onSelect = { onChange(settings.copy(barrelDoubleAction = it)) },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    SwitchRow(
        title = "Pressure sensitivity",
        subtitle = "On: pressing harder thickens the line. Off: every stroke draws at its set size, " +
            "so a pen stroke and a shape match exactly.",
        checked = settings.pressureEnabled,
        onCheckedChange = { onChange(settings.copy(pressureEnabled = it)) },
    )
    Text(
        "With pressure on, a figure is drawn at its full set width while a pen stroke thins with " +
            "pressure, so a figure can look thicker than the pen. Set the figures' default size to " +
            "compensate in Settings → Figures.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // The filter's components stay on screen even when pressure is off — disabled, with the reason
    // stated — rather than vanishing: a control that silently does nothing is what makes a working
    // slider read as broken (desktop Xournal++ greys the same frame out).
    Spacer(Modifier.height(12.dp))
    Text(
        "Pressure filter",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "How your raw pressure is turned into line width: multiplied, then floored. Applies to the " +
            "pen; the highlighter and the figure tools always draw at their set width.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Spacer(Modifier.height(8.dp))
    PenPresetsRow(
        presets = settings.penPresets,
        selectedPresetId = settings.selectedPenPresetId,
        currentMinPressure = settings.minimumPressure,
        currentMultiplier = settings.pressureMultiplier,
        onSelectPreset = { preset ->
            onChange(
                settings.copy(
                    selectedPenPresetId = preset.id,
                    minimumPressure = preset.minimumPressure,
                    pressureMultiplier = preset.pressureMultiplier,
                )
            )
        },
        onUpdatePresets = { updated, newSelectedId ->
            val nextSelectedId = newSelectedId ?: settings.selectedPenPresetId
            val active = updated.firstOrNull { it.id == nextSelectedId }
            onChange(
                settings.copy(
                    penPresets = updated,
                    selectedPenPresetId = nextSelectedId,
                    minimumPressure = active?.minimumPressure ?: settings.minimumPressure,
                    pressureMultiplier = active?.pressureMultiplier ?: settings.pressureMultiplier,
                )
            )
        },
    )
    Spacer(Modifier.height(12.dp))
    MinimumPressureSlider(
        value = settings.minimumPressure,
        enabled = settings.pressureEnabled,
        onChange = { newMinP ->
            val updatedPresets = settings.penPresets.map { preset ->
                if (preset.id == settings.selectedPenPresetId) {
                    preset.copy(minimumPressure = newMinP)
                } else {
                    preset
                }
            }
            onChange(
                settings.copy(
                    minimumPressure = newMinP,
                    penPresets = updatedPresets,
                )
            )
        },
    )
    Spacer(Modifier.height(12.dp))
    PressureMultiplierSlider(
        value = settings.pressureMultiplier,
        enabled = settings.pressureEnabled,
        onChange = { newMult ->
            val updatedPresets = settings.penPresets.map { preset ->
                if (preset.id == settings.selectedPenPresetId) {
                    preset.copy(pressureMultiplier = newMult)
                } else {
                    preset
                }
            }
            onChange(
                settings.copy(
                    pressureMultiplier = newMult,
                    penPresets = updatedPresets,
                )
            )
        },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    OptionGroup(
        title = "Stroke precision",
        subtitle = "How much pen detail a stroke keeps. Higher draws rounder curves on a big, " +
                "high-density screen; lower keeps files smaller.",
        options = StrokePrecision.values().toList(),
        selected = settings.strokePrecision,
        label = { it.label },
        onSelect = { onChange(settings.copy(strokePrecision = it)) },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    SwitchRow(
        title = "Shape recognition",
        subtitle = "Snap a finished freehand stroke to the shape it resembles — line, triangle, " +
                "rectangle or circle — using desktop Xournal++'s own recognizer. Anything " +
                "unrecognised stays as drawn.",
        checked = settings.recognizeShapes,
        onCheckedChange = { onChange(settings.copy(recognizeShapes = it)) },
    )
}