package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.mobixournal.render.CircuitInk
import com.mobixournal.render.ShapeKind

/**
 * The **component picker** of the Circuit slot: the tablet's own palette of symbols, drawn from the very
 * catalogue the placement and the ink use ([CircuitElementFactory.CIRCUIT_KINDS]) — so the list cannot
 * offer a part this build cannot draw.
 *
 * It is the strip's picker material, not a new surface (`docs/interface-contract.md`): the floating glass
 * with its rim, one row per symbol, the picked row carrying the accent. Picking a symbol **arms** the
 * slot — the next press on the page places that component — and closes the list; `Esc` closes it and
 * leaves the arm as it was.
 *
 * The row's symbol is the real drawing: each part is compiled by [CircuitInk] (the same compiler the page
 * uses) and scaled into the row, so a resistor that reads as a resistor here is the resistor the page
 * will show.
 */
@Composable
internal fun CircuitKindPicker(
    current: ShapeKind,
    onPick: (ShapeKind) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset,
) {
    Popup(offset = offset, onDismissRequest = onDismiss) {
        Column(
            Modifier
                .glassSurface()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (kind in CircuitElementFactory.CIRCUIT_KINDS) {
                    CircuitKindRow(
                        kind = kind,
                        picked = kind == current,
                        onPick = { onPick(kind) },
                    )
                }
            }
        }
    }
}

/** One symbol row: its drawing, its name, and — when armed — the accent. */
@Composable
private fun CircuitKindRow(kind: ShapeKind, picked: Boolean, onPick: () -> Unit) {
    val inkColour = if (picked) DesktopTheme.accent else DesktopTheme.chromeInk
    Row(
        Modifier
            .hoverFill(DesktopTheme.railSlotShape, active = picked)
            .then(
                if (picked) Modifier.background(DesktopTheme.activeSlot, DesktopTheme.railSlotShape)
                else Modifier,
            )
            .pointerInput(kind) { detectTapGestures { onPick() } }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(24.dp)) { drawCircuitSymbol(kind, inkColour) }
        Spacer(Modifier.width(8.dp))
        Text(circuitKindLabel(kind), color = DesktopTheme.ink, style = DesktopTheme.label)
    }
}

/**
 * One symbol in a 24 dp frame, compiled by the page's own [CircuitInk] and scaled to fit.
 *
 * A part is authored horizontally at a fixed length and then mapped into the frame, so a gate's triangle
 * and a capacitor's plates keep their proportions at the row's size instead of stretching to it.
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
    // A dot at the origin keeps a one-pin symbol (the ground) from looking off-centre in its row.
    drawCircle(colour, radius = 0.5f, center = Offset(size.width / 2f, size.height / 2f))
}
