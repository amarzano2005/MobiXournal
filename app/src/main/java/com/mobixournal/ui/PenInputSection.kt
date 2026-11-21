package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.QuickAction
import com.mobixournal.render.StrokePrecision
import com.mobixournal.ui.theme.liquidGlassContentSize
import kotlin.math.max

/**
 * Unified **Pen & Input** section (`Stylus` + `Finger gestures`) with adaptive UI:
 * segmented touch-mode toggle (`Pan only` vs `Draw`), collapsible pressure-curve preview + sliders
 * when `pressureEnabled` is active, barrel button actions, finger tap gestures, and stroke precision.
 */
@Composable
fun PenInputSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    TouchInputGroup(settings, onChange)
    BarrelButtonGroup(settings, onChange)
    PressureGroup(settings, onChange)
    SettingsGroup("Ink") {
        OptionGroup(
            title = "Stroke precision",
            subtitle = "Stroke point sampling density. High increases fidelity; Low reduces file size.",
            options = StrokePrecision.values().toList(),
            selected = settings.strokePrecision,
            label = { it.label },
            onSelect = { onChange(settings.copy(strokePrecision = it)) },
        )
    }
}

@Composable
private fun TouchInputGroup(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Touch & gestures") {
        ControlHeader(
            title = "Finger touch mode",
            subtitle = "Choose whether fingers navigate the canvas or draw strokes.",
            modifier = Modifier.settingsBlockInset(),
        )
        SettingsSegmentedControl(
            options = listOf(false, true),
            selected = settings.fingerDraws,
            label = { if (it) "Draw" else "Pan only" },
            onSelect = { onChange(settings.copy(fingerDraws = it)) },
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        SettingsDivider()
        SwitchRow(
            title = "Hover preview",
            subtitle = "Display pointer position preview when stylus hovers above screen.",
            checked = settings.showHover,
            onCheckedChange = { onChange(settings.copy(showHover = it)) },
        )
        SettingsDivider()
        OptionGroup(
            title = "Two-finger tap",
            subtitle = "Quick stationary tap with two fingers. Panning or pinching will not trigger this.",
            options = QuickAction.values().filter { it != QuickAction.HUD },
            selected = settings.twoFingerTapAction,
            label = { quickActionLabel(it) },
            onSelect = { onChange(settings.copy(twoFingerTapAction = it)) },
        )
        SettingsDivider()
        OptionGroup(
            title = "Three-finger tap",
            subtitle = "Quick stationary tap with three fingers. Panning or pinching will not trigger this.",
            options = QuickAction.values().filter { it != QuickAction.HUD },
            selected = settings.threeFingerTapAction,
            label = { quickActionLabel(it) },
            onSelect = { onChange(settings.copy(threeFingerTapAction = it)) },
        )
    }
}

@Composable
private fun BarrelButtonGroup(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Barrel button") {
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
            subtitle = "Action triggered by double-clicking the stylus barrel button while lifted.",
            options = QuickAction.values().toList(),
            selected = settings.barrelDoubleAction,
            label = { quickActionLabel(it) },
            onSelect = { onChange(settings.copy(barrelDoubleAction = it)) },
        )
    }
}

@Composable
private fun PressureGroup(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Pressure") {
        Column(Modifier.liquidGlassContentSize()) {
            SwitchRow(
                title = "Pressure sensitivity",
                subtitle = "Vary stroke width based on pen pressure; off draws uniform strokes.",
                checked = settings.pressureEnabled,
                onCheckedChange = { onChange(settings.copy(pressureEnabled = it)) },
            )
            if (!settings.pressureEnabled) {
                SettingsDivider()
                SettingsNote("Uniform fixed stroke width active — pressure curve controls hidden.")
            }
            AnimatedVisibility(visible = settings.pressureEnabled) {
                Column {
                    SettingsDivider()
                    PressureCurvePreview(
                        minPressure = settings.minimumPressure,
                        multiplier = settings.pressureMultiplier,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
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
                                ),
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
                                ),
                            )
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    MinimumPressureSlider(
                        value = settings.minimumPressure,
                        enabled = true,
                        onChange = { newMinP ->
                            val updatedPresets = settings.penPresets.map { preset ->
                                if (preset.id == settings.selectedPenPresetId) {
                                    preset.copy(minimumPressure = newMinP)
                                } else {
                                    preset
                                }
                            }
                            onChange(settings.copy(minimumPressure = newMinP, penPresets = updatedPresets))
                        },
                    )
                    PressureMultiplierSlider(
                        value = settings.pressureMultiplier,
                        enabled = true,
                        onChange = { newMult ->
                            val updatedPresets = settings.penPresets.map { preset ->
                                if (preset.id == settings.selectedPenPresetId) {
                                    preset.copy(pressureMultiplier = newMult)
                                } else {
                                    preset
                                }
                            }
                            onChange(settings.copy(pressureMultiplier = newMult, penPresets = updatedPresets))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PressureCurvePreview(
    minPressure: Float,
    multiplier: Float,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val bgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val nominalY = size.height * (1f - 1f / 1.5f)
        drawLine(
            color = guideColor,
            start = Offset(0f, nominalY),
            end = Offset(size.width, nominalY),
            strokeWidth = 1.5f,
            pathEffect = dash,
        )
        val path = Path()
        val steps = 32
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val widthFactor = max(minPressure, t * multiplier).coerceIn(0f, 1.5f)
            val x = t * size.width
            val y = size.height * (1f - widthFactor / 1.5f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}
