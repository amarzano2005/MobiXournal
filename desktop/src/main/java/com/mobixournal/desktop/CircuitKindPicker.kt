package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.format.model.Stroke as InkStroke
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.CircuitFamily
import com.mobixournal.render.CircuitInk
import com.mobixournal.render.ShapeKind

/**
 * The **component palette** of a circuit slot: one of the tablet's three families (passive parts, active
 * parts, logic gates), laid out in the family's own rows — `CircuitFamily.rows`, which is the tablet's
 * picker layout, so the two shells offer the same parts in the same places.
 *
 * It is the strip's picker material, not a new surface (`docs/interface-contract.md`): the floating glass
 * with its rim, and the armed part's row carrying the accent. Picking a part **arms** the slot — the next
 * press on the page places it — and closes the list; `Esc` closes it and leaves the arm as it was.
 *
 * A row's symbol is the real drawing: each part is compiled by [CircuitInk] (the same compiler the page
 * uses) and scaled into its cell, so a resistor that reads as a resistor here is the resistor the page
 * will show.
 */
@Composable
internal fun CircuitKindPicker(
    family: CircuitFamily,
    current: ShapeKind,
    onPick: (ShapeKind) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset,
) {
    Popup(offset = offset, onDismissRequest = onDismiss, properties = FloatingSurface) {
        Column(
            Modifier
                .glassSurface()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                family.title,
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            for (row in family.rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (kind in row) {
                        CircuitKindCell(
                            kind = kind,
                            picked = kind == current,
                            onPick = { onPick(kind) },
                        )
                    }
                }
            }
        }
    }
}

/** One part's cell: its drawing over its name, the armed one in the accent. */
@Composable
private fun CircuitKindCell(kind: ShapeKind, picked: Boolean, onPick: () -> Unit) {
    val inkColour = if (picked) DesktopTheme.accent else DesktopTheme.chromeInk
    Column(
        Modifier
            .width(78.dp)
            .hoverFill(DesktopTheme.railSlotShape, active = picked)
            .then(
                if (picked) Modifier.background(DesktopTheme.activeSlot, DesktopTheme.railSlotShape)
                else Modifier,
            )
            .pointerInput(kind) { detectTapGestures { onPick() } }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(24.dp)) { drawCircuitSymbol(kind, inkColour) }
        Text(circuitKindLabel(kind), color = DesktopTheme.ink, style = DesktopTheme.label)
    }
}

/**
 * One symbol in a 24 dp frame, compiled by the page's own [CircuitInk] and scaled to fit.
 *
 * A part is authored horizontally at a fixed length and then mapped into the frame, so a gate's triangle
 * and a capacitor's plates keep their proportions at the cell's size instead of stretching to it.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircuitSymbol(kind: ShapeKind, colour: Color) {
    val length = 32.0
    val element = CircuitElementFactory.placedElementFor(
        symbol = kind.name,
        startX = -length / 2.0,
        startY = 0.0,
        endX = length / 2.0,
        endY = 0.0,
    ) ?: return
    val strokes = CircuitInk.ink(element).orEmpty().filterIsInstance<InkStroke>()
    if (strokes.isEmpty()) return
    val scale = (size.minDimension / (length * 1.15)).toFloat()
    translate(size.width / 2f, size.height / 2f) {
        for (stroke in strokes) {
            drawPath(
                path = PageInk.polyline(stroke.points, scale),
                color = colour,
                style = Stroke(
                    width = DesktopTheme.TOOL_GLYPH_STROKE_UNITS * scale * 1.4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
    // A dot at the origin keeps a one-pin symbol (the ground) from looking off-centre in its cell.
    drawCircle(colour, radius = 0.5f, center = Offset(size.width / 2f, size.height / 2f))
}
