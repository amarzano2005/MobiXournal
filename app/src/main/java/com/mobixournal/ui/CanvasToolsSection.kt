package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobixournal.render.MomentumCurve
import com.mobixournal.ui.theme.liquidGlassContentSize

/**
 * Unified **Canvas & Tools** section (`Editor` + `Figures` + `Navigation`) with adaptive
 * sub-controls: figure custom angles expand only for `Scalene`, and momentum glide curve
 * expands only while `momentum > 0`.
 */
@Composable
fun CanvasToolsSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    EditorSection(settings, onChange)
    FiguresSection(settings, onChange)
    SettingsGroup("Navigation & momentum") {
        Column(Modifier.liquidGlassContentSize()) {
            PanSensitivitySlider(
                value = settings.panSensitivity,
                onChange = { onChange(settings.copy(panSensitivity = it)) },
            )
            SettingsDivider()
            MomentumSlider(
                value = settings.momentum,
                onChange = { onChange(settings.copy(momentum = it)) },
            )
            AnimatedVisibility(visible = settings.momentum > 0f) {
                Column {
                    SettingsDivider()
                    OptionGroup(
                        title = "Momentum curve",
                        subtitle = "Deceleration profile: Linear is uniform; Exponential scales with flick velocity.",
                        options = MomentumCurve.values().toList(),
                        selected = settings.momentumCurve,
                        label = { it.label },
                        onSelect = { onChange(settings.copy(momentumCurve = it)) },
                    )
                }
            }
        }
    }
}
