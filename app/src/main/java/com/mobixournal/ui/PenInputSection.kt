package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.QuickAction
import com.mobixournal.render.StrokePrecision
import com.mobixournal.ui.theme.liquidGlassContentSize
import kotlin.math.max

/**
 * Unified **Pen & Input** section (`Stylus` + `Finger gestures`) with adaptive UI:
 * collapsible pressure-curve preview + sliders when `pressureEnabled` is active, stroke
 * precision, pen side button actions, segmented touch-mode toggle (`Pan only` vs `Draw`),
 * and finger tap gestures.
 */
@Composable
fun PenInputSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onOpenFocusMode: () -> Unit = {},
) {
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
    PenSideButtonGroup(settings, onChange)
    TouchInputGroup(settings, onChange, onOpenFocusMode)
}

@Composable
private fun TouchInputGroup(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onOpenFocusMode: () -> Unit,
) {
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
        SettingsDivider()
        TouchDoubleTapRow(onClick = onOpenFocusMode)
    }
}

@Composable
private fun TouchDoubleTapRow(onClick: () -> Unit) {
    val reach = LocalSettingsCardInset.current
    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = reach, vertical = settingsCellInset(reach)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlHeader(
            title = "Touch double-tap",
            subtitle = "Double-tap the canvas with a finger (any tool): left third for previous page, centre for Focus mode, right third for next page.",
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Focus mode",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PenSideButtonGroup(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Pen side button") {
        OptionGroup(
            title = "While held",
            subtitle = "Tool or action activated while pressing the pen side button.",
            options = BarrelAction.values().toList(),
            selected = settings.barrelAction,
            label = { barrelActionLabel(it) },
            onSelect = { onChange(settings.copy(barrelAction = it)) },
        )
        SettingsDivider()
        OptionGroup(
            title = "Double-click",
            subtitle = "Action triggered by double-clicking the pen side button while lifted.",
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
                SettingsAccentBlock {
                    PressureCurvePreview(
                        minPressure = settings.minimumPressure,
                        multiplier = settings.pressureMultiplier,
                        modifier = Modifier.padding(bottom = 8.dp),
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
    val fillColor = lineColor.copy(alpha = 0.10f)
    val inkColor = MaterialTheme.colorScheme.onSurface
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
    val bgColor = MaterialTheme.colorScheme.surface
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(8.dp))
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
            val strokePath = Path()
            val fillPath = Path()
            val steps = 32
            var startY = size.height
            var endY = 0f
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val widthFactor = max(minPressure, t * multiplier).coerceIn(0f, 1.5f)
                val x = t * size.width
                val y = size.height * (1f - widthFactor / 1.5f)
                if (i == 0) {
                    startY = y
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (i == steps) endY = y
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()
            drawPath(fillPath, color = fillColor)
            drawPath(strokePath, color = lineColor, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
            drawCircle(color = lineColor, radius = 6f, center = Offset(0f, startY))
            drawCircle(color = lineColor, radius = 6f, center = Offset(size.width, endY))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .padding(horizontal = 4.dp),
        ) {
            val cy = size.height / 2f
            val minR = (minPressure * 4.5f).coerceIn(1.2f, 5.5f)
            val maxR = (multiplier * 4.2f).coerceIn(minR, 6.5f)
            val taper = Path().apply {
                moveTo(minR, cy - minR)
                lineTo(size.width - maxR, cy - maxR)
                lineTo(size.width - maxR, cy + maxR)
                lineTo(minR, cy + minR)
                close()
            }
            drawPath(taper, color = inkColor.copy(alpha = 0.85f))
            drawCircle(color = inkColor.copy(alpha = 0.85f), radius = minR, center = Offset(minR, cy))
            drawCircle(color = inkColor.copy(alpha = 0.85f), radius = maxR, center = Offset(size.width - maxR, cy))
        }
    }
}
