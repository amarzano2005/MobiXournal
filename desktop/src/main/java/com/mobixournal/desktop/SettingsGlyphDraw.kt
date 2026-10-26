package com.mobixournal.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.design.SettingsGlyphCatalogue

/**
 * The window's **adapter** for the shared settings glyphs — the PC's half of the one catalogue.
 *
 * The art is not here: `SettingsGlyphCatalogue` (plain Kotlin, `mobix-core`) names every part of every
 * section's mark on the 24-unit grid, and the tablet's adapter builds the same parts into an
 * `ImageVector`. That is what makes the settings index show the same picture on both shells instead of
 * the phone's Material icon beside the PC's blank space (`docs/interface-contract.md` §4).
 *
 * [drawGlyphParts] is the window's one stroke adapter, shared with the tool and chrome art, so a section's
 * mark weighs exactly what a tool slot does at the same size.
 */
internal fun DrawScope.drawSettingsGlyph(glyph: SettingsGlyph, color: Color) =
    drawGlyphParts(SettingsGlyphCatalogue.parts(glyph), color)
