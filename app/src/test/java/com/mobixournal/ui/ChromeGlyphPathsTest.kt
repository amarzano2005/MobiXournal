package com.mobixournal.ui

import androidx.compose.ui.graphics.vector.addPathNodes
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ChromeGlyphCatalogue
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue's `d` strings, read by **the phone's own path reader**.
 *
 * `ChromeGlyphsTest` (`desktop`) proves the strings parse on the window's reader; this is the other half —
 * Compose's `addPathNodes` — because the catalogue is now one file serving two readers, and a glyph that
 * only one of them understands is a command with a picture on one screen and none on the other.
 */
class ChromeGlyphPathsTest {

    @Test
    fun `every chrome glyph is a path Compose can read`() {
        for (glyph in ChromeGlyph.entries) {
            val data = ChromeGlyphCatalogue.path(glyph)
            assertTrue("$glyph has path data", data.isNotBlank())
            val nodes = addPathNodes(data)
            assertTrue("$glyph parses into a shape", nodes.size > 2)
        }
    }
}
