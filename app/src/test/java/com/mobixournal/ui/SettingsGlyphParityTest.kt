package com.mobixournal.ui

import com.mobixournal.design.SettingsGlyph
import com.mobixournal.design.SettingsGlyphCatalogue
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet's half of the **one settings-glyph catalogue** (`docs/interface-contract.md` §4).
 *
 * Every section and area row used to take a Material `Icons.Outlined` vector, which the window may not
 * import and could therefore not draw: the same section was a picture on the phone and a blank space on
 * the PC. The marks are geometry in `mobix-core` now, and this test holds the phone to it — the row is
 * drawn from [SettingsGlyphCatalogue] and not from an icon set of its own.
 *
 * The vector's `name` is the proof, because it is the name the adapter built it with: an icon that slipped
 * back to Material would fail here by name rather than by eye.
 */
class SettingsGlyphParityTest {

    @Test
    fun `every settings section draws its shared glyph`() {
        for (section in SettingsSection.entries) {
            val glyph = SettingsGlyph.entries.firstOrNull { it.name == section.name }
            assertTrue("${section.title} has a glyph in the shared catalogue", glyph != null)
            assertTrue(
                "${section.title} is drawn from the catalogue",
                section.icon.name == "SettingsGlyph.${glyph!!.name.lowercase()}",
            )
            assertTrue("${section.title}'s mark is a picture", SettingsGlyphCatalogue.parts(glyph).size >= 2)
        }
    }

    @Test
    fun `every settings area draws its shared glyph`() {
        for (area in SettingsArea.entries) {
            assertTrue(
                "${area.title} is drawn from the shared catalogue, not from Material icons",
                area.icon.name.startsWith("SettingsGlyph."),
            )
        }
    }

    /**
     * The catalogue is the product's, so every mark it has is used by a settings section.
     *
     * [WINDOW_ONLY] is the exception the two shells need: the window's Settings has an **Audio** page — the
     * microphone and the output a take and a clip use — that the tablet answers from its own capture
     * pipeline instead of a page, so its mark is shared but not listed here (`docs/desktop-settings.md`).
     */
    @Test
    fun `the catalogue carries no mark the settings cannot reach`() {
        val used = SettingsSection.entries.map { it.name }.toSet()
        for (glyph in SettingsGlyph.entries) {
            if (glyph.name in WINDOW_ONLY) continue
            assertTrue("${glyph.name} belongs to a settings section", glyph.name in used)
        }
    }

    private companion object {
        /** The marks only the window's section list reaches. */
        val WINDOW_ONLY = setOf("AUDIO")
    }
}
