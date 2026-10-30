package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import com.mobixournal.design.PenPalette
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import java.util.Locale

/**
 * **Figures**: the width the shape tools draw at, and the variants a triangle and a trapezoid take.
 *
 * A figure has no pressure — it is drawn at its full width — so a rectangle would otherwise always be as
 * thick as the pen's *slot*, and a light pen stroke beside it reads thinner. Its width is therefore its own
 * value: the tablet's three S/M/L slots as presets, and a slider for anything between them.
 *
 * The other two answers are the variants: a triangle's four kinds (with a scalene one's three angles) and a
 * trapezoid's three (with two base angles), the same ones the tablet's figures page offers.
 */
@Composable
internal fun FiguresSettings(policy: DesktopSettingsState) {
    val widths = PenPalette.DEFAULT_WIDTHS
    val letters = listOf("S", "M", "L")
    SettingsGroup("Figure width") {
        SettingsSelect(
            label = "Preset",
            note = "The pen's three slots, for every figure tool — figures ignore pressure.",
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
            note = "Anything between the slots, in points.",
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
    SettingsNote("The pen's own widths stay in the strip's colour capsule.")
    // A triangle and a trapezoid are the two figures whose shape is not fully said by the drag, so
    // they carry the tablet's variants here — the same four and the same three, with the angles a
    // scalene one is built from.
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
            FigureAngle("Left base angle", policy.figures.shape.trapezoidAngleA) {
                policy.figures.chooseShape(policy.figures.shape.copy(trapezoidAngleA = it))
            }
            FigureAngle("Right base angle", policy.figures.shape.trapezoidAngleB) {
                policy.figures.chooseShape(policy.figures.shape.copy(trapezoidAngleB = it))
            }
        }
    }
}

/** One of a scalene figure's angles, in degrees — the range [FigureSettings] keeps a figure within. */
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
