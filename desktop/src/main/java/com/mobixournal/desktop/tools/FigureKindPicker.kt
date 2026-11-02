package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.drawGlyphParts
import com.mobixournal.desktop.settings.drawToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue
import com.mobixournal.desktop.sheet.FigureShape

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

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
                if (glyph != null) {
                    drawToolGlyph(glyph, inkColour)
                } else {
                    // A side count wears the polygon of its own count, from the shared catalogue, so
                    // the tablet's Polygons menu and this one draw one heptagon.
                    drawGlyphParts(ToolGlyphCatalogue.regularPolygon(option.shape.polygonSides), inkColour)
                }
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
