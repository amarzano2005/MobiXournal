package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The **menu of a figure slot**: the triangle's kinds, the trapezoid's kinds, or the polygon's side
 * count — the tablet's Figure submenu, on the window's glass.
 *
 * It is the strip's picker material, not a new surface (`docs/interface-contract.md`): the floating glass
 * with its rim, and the live answer's cell carrying the accent. The three figures keep **a slot each**, so
 * this menu belongs to one figure rather than to a family: the triangle's four kinds are not mixed with
 * the trapezoid's three, and the polygon's own question is a count.
 *
 * A triangle's and a trapezoid's cell draws the catalogue's picture of the kind it writes, because at
 * this size the silhouette is all a user reads; a polygon's cell draws the polygon of its own count, so
 * "seven" is a heptagon rather than the numeral seven alone. Picking a cell **takes the slot's tool as
 * well** — a menu opened from the chevron of a slot that was not picked lands on the figure it answers,
 * the same one gesture the polygon's count picker has on the tablet.
 */
@Composable
internal fun FigureKindPicker(
    tool: DesktopTool,
    shape: FigureShape,
    onPick: (FigureShape) -> Unit,
    onEdit: (DesktopTool) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset,
) {
    val options = figureMenu(tool, shape) ?: return

    Popup(offset = offset, onDismissRequest = onDismiss, properties = FloatingSurface) {
        Column(
            Modifier
                .glassSurface()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                figureMenuTitle(tool),
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            for (row in options.chunked(figureMenuRowSize(tool))) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (option in row) {
                        val isScalene = option.label == "Scalene"
                        FigureOptionCell(
                            option = option,
                            editable = isScalene,
                            onPick = {
                                onPick(it.shape)
                                if (isScalene) onEdit(tool)
                            },
                            onEdit = {
                                onPick(option.shape)
                                onEdit(tool)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One answer's cell: the figure it writes over the name, the live one in the accent. */
@Composable
private fun FigureOptionCell(
    option: FigureOption,
    editable: Boolean = false,
    onPick: (FigureOption) -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val inkColour = if (option.selected) DesktopTheme.accent else DesktopTheme.chromeInk
    Box(
        Modifier
            .width(88.dp)
            .hoverFill(DesktopTheme.railSlotShape, active = option.selected)
            .then(
                if (option.selected) Modifier.background(DesktopTheme.activeSlot, DesktopTheme.railSlotShape)
                else Modifier,
            )
            .pointerInput(option) { detectTapGestures { onPick(option) } }
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(24.dp)) {
                val glyph = option.glyph
                if (glyph != null) drawToolGlyph(glyph, inkColour) else drawRegularPolygon(option.shape.polygonSides, inkColour)
            }
            Text(option.label, color = DesktopTheme.ink, style = DesktopTheme.label)
        }
        if (editable && onEdit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .pointerInput(Unit) { detectTapGestures { onEdit() } }
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(12.dp)) {
                    drawChromeGlyph(com.mobixournal.design.ChromeGlyph.EDIT, inkColour)
                }
            }
        }
    }
}

/**
 * A regular [sides]-gon in the cell's 24 dp frame, first vertex at the top — the figure the count will
 * draw, at the catalogue's monoline weight, so the cell and the slot agree about what "seven" looks like.
 */
private fun DrawScope.drawRegularPolygon(sides: Int, colour: Color) {
    val count = sides.coerceAtLeast(3)
    val radius = size.minDimension * 0.38f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path()
    for (i in 0 until count) {
        val angle = -PI / 2.0 + 2.0 * PI * i / count
        val x = cx + (radius * cos(angle)).toFloat()
        val y = cy + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(
        path = path,
        color = colour,
        style = Stroke(
            width = DesktopTheme.TOOL_GLYPH_STROKE_UNITS * size.minDimension / DesktopTheme.TOOL_GLYPH_GRID,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
