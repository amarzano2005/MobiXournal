package com.mobixournal.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.mobixournal.design.SettingsGlyph
import com.mobixournal.design.SettingsGlyphCatalogue

/**
 * The tablet's **adapter** for the shared settings glyphs (`SettingsGlyphCatalogue`, plain Kotlin in
 * `mobix-core`).
 *
 * A settings section used to carry a Material `Icons.Outlined` vector, which the window could not draw —
 * it may not import `material.icons` — so the same section was a picture on the phone and a blank space on
 * the PC. The catalogue is now the one source and both shells read it (`docs/interface-contract.md` §4):
 * the phone builds the `ImageVector` here, the window fills the same parts into its `DrawScope`.
 *
 * The vectors are built once per section: a list of thirteen settings rows asks for them on every
 * composition, and an icon never changes.
 */
private val settingsGlyphVectors = HashMap<SettingsGlyph, ImageVector>()

/** The [ImageVector] for this section's mark — built once, then reused. */
fun SettingsGlyph.imageVector(): ImageVector =
    settingsGlyphVectors.getOrPut(this) {
        glyphVector("SettingsGlyph.${name.lowercase()}", SettingsGlyphCatalogue.parts(this))
    }
