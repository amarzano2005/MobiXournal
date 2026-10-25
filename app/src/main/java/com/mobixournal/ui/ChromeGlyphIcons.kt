package com.mobixournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ChromeGlyphCatalogue
import com.mobixournal.design.DesignTokens

/**
 * The tablet's **adapter** for the shared chrome glyphs (`ChromeGlyphCatalogue`, plain Kotlin in
 * `mobix-core`).
 *
 * The catalogue holds one `d` string per command; Compose's own path reader turns it into nodes, so the
 * phone keeps using `Icon` — with its tint, its content description and its size — while drawing the
 * **same picture** the window fills (`desktop/…/SvgPath.kt`). That is the point of the catalogue: the
 * phone's shapes are not retyped on the PC, they are read from one home.
 *
 * The vectors are built once per glyph: an `ImageVector` is immutable, and a header asks for the same
 * handful on every composition.
 */
private val chromeGlyphVectors = HashMap<ChromeGlyph, ImageVector>()

/** The [ImageVector] for this chrome glyph — built once from the catalogue, then reused. */
fun ChromeGlyph.imageVector(): ImageVector = chromeGlyphVectors.getOrPut(this) { buildVector() }

private fun ChromeGlyph.buildVector(): ImageVector = ImageVector.Builder(
    name = "ChromeGlyph.${name.lowercase()}",
    defaultWidth = DesignTokens.TOOL_GLYPH_GRID.dp,
    defaultHeight = DesignTokens.TOOL_GLYPH_GRID.dp,
    viewportWidth = DesignTokens.TOOL_GLYPH_GRID,
    viewportHeight = DesignTokens.TOOL_GLYPH_GRID,
)
    // Filled, not stroked: Material's outlined set is solid areas with their holes wound the other way.
    .addPath(
        pathData = addPathNodes(ChromeGlyphCatalogue.path(this)),
        fill = SolidColor(Color.Black),
    )
    .build()
