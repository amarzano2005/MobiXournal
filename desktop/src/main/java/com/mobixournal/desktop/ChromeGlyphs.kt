package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ChromeGlyphCatalogue

/**
 * The window's **chrome glyphs**, drawn from the shared catalogue — the tablet's own pictures.
 *
 * The art is not here: `ChromeGlyphCatalogue` (`mobix-core`) holds the path data Material publishes for
 * the commands the phone's header and menu use, and this file turns a glyph into a filled `Path` on the
 * 24-unit grid ([svgPath]). The window used to draw a *near* set by hand — its floppy was a page with a
 * notch, its PDF mark a page of lines — which is the one thing a product shown on two screens cannot
 * afford: put the phone beside the PC and the icons must be the same icons, not two artists' versions.
 *
 * Only the geometry is shared, never the ink: the colour arrives from the window's theme, exactly as it
 * does for a tool slot.
 */
internal fun DrawScope.drawChromeGlyph(glyph: ChromeGlyph, color: Color) {
    val grid = DesktopTheme.TOOL_GLYPH_GRID
    val factor = size.minDimension / grid
    // Scaled rather than rebuilt per size: the catalogue's paths are cached, the transform is a matrix.
    scale(scaleX = factor, scaleY = factor, pivot = Offset.Zero) {
        drawPath(chromePath(glyph), color)
    }
}

/** The parsed paths, one per glyph: the catalogue is data, and parsing it per frame would be waste. */
private val chromePaths: Map<ChromeGlyph, Path> by lazy {
    ChromeGlyph.entries.associateWith { svgPath(ChromeGlyphCatalogue.path(it)) }
}

/** [glyph]'s filled path in grid units. */
internal fun chromePath(glyph: ChromeGlyph): Path = chromePaths.getValue(glyph)
