package com.mobixournal.desktop.tools
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.settings.SettingsGroup
import com.mobixournal.desktop.settings.SettingsRow
import com.mobixournal.desktop.settings.SettingsSelect
import com.mobixournal.desktop.settings.SettingsSlider
import com.mobixournal.desktop.settings.SettingsSwitch

import androidx.compose.runtime.Composable
import com.mobixournal.design.PenPalette
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings ▸ **Figures**: the tools that write geometric primitives.
 */
@Composable
internal fun FiguresSettings(policy: DesktopSettingsState) {
    FigureRecognitionSettings(policy)
    FigureWidthSettings(policy)
    TriangleSettings(policy, onCustomize = { policy.figures.openDialog(DesktopTool.TRIANGLE) })
    TrapezoidSettings(policy, onCustomize = { policy.figures.openDialog(DesktopTool.TRAPEZOID) })
    PolygonSettings(policy)
}

@Composable
private fun FigureRecognitionSettings(policy: DesktopSettingsState) {
    SettingsGroup("Shape recognition") {
        SettingsRow(
            label = "Recognize shapes",
            note = "Snap a freehand stroke to the shape it resembles when the stylus is lifted.",
            onClick = { policy.figures.chooseRecognizeShapes(!policy.figures.recognizeShapes) },
        ) {
            SettingsSwitch(policy.figures.recognizeShapes)
        }
        // The two triggers are exclusive: recognising a held stroke *and* the lifted one would read the
        // clean figure back as ink (a held square comes back a circle), so each switch turns the other off.
        SettingsRow(
            label = "Recognize on hold",
            note = "Snap while the tip rests on the stroke, before the lift. Turning this on turns the lift off.",
            onClick = { policy.figures.chooseRecognizeOnHold(!policy.figures.recognizeOnHold) },
        ) {
            SettingsSwitch(policy.figures.recognizeOnHold)
        }
    }
}

@Composable
private fun FigureWidthSettings(policy: DesktopSettingsState) {
    val widths = PenPalette.DEFAULT_WIDTHS
    val letters = listOf("S", "M", "L")
    SettingsGroup("Figure width") {
        SettingsSelect(
            label = "Preset",
            note = "Standard stroke widths for figure tools (pen widths stay in toolbar color capsule).",
            options = widths.indices.toList(),
            picked = PenPalette.widthSlot(policy.figures.width),
            optionLabel = { index ->
                val name = letters.getOrNull(index) ?: "${index + 1}"
                "$name — ${String.format(Locale.ROOT, "%.1f", widths[index])} pt"
            },
            onPick = { index -> policy.figures.chooseWidth(widths[index]) },
        )
        SettingsRow(
            label = "Width",
            note = "Custom stroke width in points (figure tools ignore pen pressure).",
        ) {
            SettingsSlider(
                value = policy.figures.width,
                range = widths.first()..widths.last(),
                step = 0.25f,
                format = { String.format(Locale.ROOT, "%.2f pt", it) },
                onValue = policy.figures::chooseWidth,
            )
        }
    }
}

@Composable
private fun TriangleSettings(policy: DesktopSettingsState, onCustomize: () -> Unit) {
    SettingsGroup("Triangle") {
        SettingsSelect(
            label = "Type",
            note = "The variant a triangle drag draws.",
            options = TriangleKind.entries.toList(),
            picked = policy.figures.shape.triangleKind,
            optionLabel = { it.label },
            onPick = { policy.figures.chooseShape(policy.figures.shape.copy(triangleKind = it)) },
        )
        if (policy.figures.shape.triangleKind == TriangleKind.SCALENE) {
            SettingsRow(
                label = "Angles dialog",
                note = "Interactive visual editor with auto-balance and presets (sum = 180°).",
            ) {
                PanelButton(label = "Customize…", primary = false, enabled = true, onClick = onCustomize)
            }
            FigureAngle("Angle A", policy.figures.shape.scaleneAngleA) {
                policy.figures.chooseShape(policy.figures.shape.copy(scaleneAngleA = it))
            }
            FigureAngle("Angle B", policy.figures.shape.scaleneAngleB) {
                policy.figures.chooseShape(policy.figures.shape.copy(scaleneAngleB = it))
            }
            FigureAngle("Angle C", policy.figures.shape.scaleneAngleC) {
                policy.figures.chooseShape(policy.figures.shape.copy(scaleneAngleC = it))
            }
        }
    }
}

@Composable
private fun TrapezoidSettings(policy: DesktopSettingsState, onCustomize: () -> Unit) {
    SettingsGroup("Trapezoid") {
        SettingsSelect(
            label = "Type",
            note = "The variant a trapezoid drag draws.",
            options = TrapezoidKind.entries.toList(),
            picked = policy.figures.shape.trapezoidKind,
            optionLabel = { it.label },
            onPick = { policy.figures.chooseShape(policy.figures.shape.copy(trapezoidKind = it)) },
        )
        if (policy.figures.shape.trapezoidKind == TrapezoidKind.SCALENE) {
            SettingsRow(
                label = "Angles dialog",
                note = "Interactive visual editor with live preview and angle presets.",
            ) {
                PanelButton(label = "Customize…", primary = false, enabled = true, onClick = onCustomize)
            }
            FigureAngle("Left base angle", policy.figures.shape.trapezoidAngleA) {
                policy.figures.chooseShape(policy.figures.shape.copy(trapezoidAngleA = it))
            }
            FigureAngle("Right base angle", policy.figures.shape.trapezoidAngleB) {
                policy.figures.chooseShape(policy.figures.shape.copy(trapezoidAngleB = it))
            }
        }
    }
}

@Composable
private fun PolygonSettings(policy: DesktopSettingsState) {
    SettingsGroup("Polygon") {
        SettingsRow(
            label = "Sides",
            note = "How many sides the polygon tool draws, from 3 to 12.",
        ) {
            SettingsSlider(
                value = policy.figures.shape.polygonSides.toFloat(),
                range = ShapeBuilder.MIN_POLYGON_SIDES.toFloat()..ShapeBuilder.MAX_POLYGON_SIDES.toFloat(),
                step = 1f,
                format = { String.format(Locale.ROOT, "%.0f sides", it) },
                onValue = { sides ->
                    policy.figures.chooseShape(policy.figures.shape.withPolygonSides(sides.roundToInt()))
                },
            )
        }
    }
}

@Composable
private fun FigureAngle(label: String, degrees: Double, onValue: (Double) -> Unit) {
    SettingsRow(label = label, note = "Degrees, between 5 and 175.") {
        SettingsSlider(
            value = degrees.toFloat(),
            range = FigureSettings.MIN_ANGLE.toFloat()..FigureSettings.MAX_ANGLE.toFloat(),
            step = 1f,
            format = { String.format(Locale.ROOT, "%.0f°", it) },
            onValue = { onValue(it.toDouble()) },
        )
    }
}
