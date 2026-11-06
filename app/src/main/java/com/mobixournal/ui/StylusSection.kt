package com.mobixournal.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.QuickAction
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
    SettingsGroup("Touch") {
        SwitchRow(
            title = "Finger draws",
            subtitle = "When disabled, finger touch only navigates the canvas (pan/zoom) and never draws.",
            checked = settings.fingerDraws,
            onCheckedChange = { onChange(settings.copy(fingerDraws = it)) },
        )
        SettingsDivider()
        SwitchRow(
            title = "Hover preview",
            subtitle = "Display pointer position preview when stylus hovers above screen.",
            checked = settings.showHover,
            onCheckedChange = { onChange(settings.copy(showHover = it)) },
        )
    }
    SettingsGroup("Barrel button") { BarrelButtonControls(settings, onChange) }
    SettingsGroup("Pressure") { PressureControls(settings, onChange) }
    SettingsGroup("Ink") {
        OptionGroup(
            title = "Stroke precision",
            "Stroke point sampling density. High increases fidelity; Low reduces file size.",
            options = StrokePrecision.values().toList(),
            selected = settings.strokePrecision,
            label = { it.label },
            onSelect = { onChange(settings.copy(strokePrecision = it)) },
        )
    }
}

/**
 * The name a [BarrelAction] wears wherever the user picks one.
 *
 * **Ring**, not the "HUD" of the code: the ring is what the reader sees and what Settings → Drawing → Ring
 * says, so the barrel button cannot name the same thing a second way.
 */
fun barrelActionLabel(action: BarrelAction): String = when (action) {
    BarrelAction.HUD -> "Ring"
    BarrelAction.HAND -> "Hand (pan)"
    else -> action.name.lowercase().replaceFirstChar(Char::uppercase)
}

/** The side-button's held action and its double-click action. */
@Composable
private fun BarrelButtonControls(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    OptionGroup(
        title = "While held",
        subtitle = "Tool or action activated while pressing the stylus barrel button.",
        options = BarrelAction.values().toList(),
        selected = settings.barrelAction,
        label = { barrelActionLabel(it) },
        onSelect = { onChange(settings.copy(barrelAction = it)) },
    )
    SettingsDivider()
    OptionGroup(
        title = "Double-click",
        "Action triggered by double-clicking the stylus barrel button while lifted.",
        options = QuickAction.values().toList(),
        selected = settings.barrelDoubleAction,
        label = { quickActionLabel(it) },
        onSelect = { onChange(settings.copy(barrelDoubleAction = it)) },
    )
}

/**
 * Pressure sensitivity and its filter (presets, minimum floor, multiplier). The filter's components
 * stay on screen even when pressure is off — disabled, with the reason stated — rather than
 * vanishing: a control that silently does nothing is what makes a working slider read as broken
 * (desktop Xournal++ greys the same frame out).
 */
@Composable
private fun PressureControls(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SwitchRow(
        title = "Pressure sensitivity",
        "Vary stroke width based on pen pressure; off draws uniform strokes.",
        checked = settings.pressureEnabled,
        onCheckedChange = { onChange(settings.copy(pressureEnabled = it)) },
    )
    SettingsNote(
        "Geometric figure tools ignore pressure and draw at their configured width (see Figures).",
    )
    SettingsDivider()
    ControlHeader(
        "Pressure filter",
        "Floor threshold and curve scaling factor for pressure-sensitive ink.",
        Modifier.settingsBlockInset(),
    )
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
    Spacer(Modifier.height(4.dp))
    MinimumPressureSlider(
        value = settings.minimumPressure,
        enabled = settings.pressureEnabled,
        onChange = { newMinP ->
            val updatedPresets = settings.penPresets.map { preset ->
                if (preset.id == settings.selectedPenPresetId) preset.copy(minimumPressure = newMinP) else preset
            }
            onChange(settings.copy(minimumPressure = newMinP, penPresets = updatedPresets))
        },
    )
    PressureMultiplierSlider(
        value = settings.pressureMultiplier,
        enabled = settings.pressureEnabled,
        onChange = { newMult ->
            val updatedPresets = settings.penPresets.map { preset ->
                if (preset.id == settings.selectedPenPresetId) preset.copy(pressureMultiplier = newMult) else preset
            }
            onChange(settings.copy(pressureMultiplier = newMult, penPresets = updatedPresets))
        },
    )
}
