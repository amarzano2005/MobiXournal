package com.mobixournal.ui

import androidx.compose.runtime.Composable
import com.mobixournal.render.MomentumCurve

/** Canvas navigation: momentum scrolling (strength and curve) and panning sensitivity. */
@Composable
fun NavigationSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Momentum") {
        MomentumSlider(
            value = settings.momentum,
            onChange = { onChange(settings.copy(momentum = it)) },
        )
        SettingsDivider()
        OptionGroup(
            title = "Momentum curve",
            subtitle = "How sharply a faster flick coasts farther: Linear is even, " +
                "Exponential rewards fast swipes the most.",
            options = MomentumCurve.values().toList(),
            selected = settings.momentumCurve,
            label = { it.label },
            onSelect = { onChange(settings.copy(momentumCurve = it)) },
        )
    }
    SettingsGroup("Panning") {
        PanSensitivitySlider(
            value = settings.panSensitivity,
            onChange = { onChange(settings.copy(panSensitivity = it)) },
        )
    }
}
