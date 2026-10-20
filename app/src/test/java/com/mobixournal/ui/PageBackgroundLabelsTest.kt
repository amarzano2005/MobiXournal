package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure half of the page-background dialog: the label its Pages-menu row carries, and the fact that
 * every paper style the file can hold has a name to show. Tested on the JVM because it is a lookup, not
 * a screen.
 */
class PageBackgroundLabelsTest {

    @Test
    fun `every paper style the file can hold has a label`() {
        for ((value, label) in BACKGROUND_STYLES) {
            assertEquals("the row names the live style", label, paperStyleLabel(value))
        }
    }

    @Test
    fun `the xopp style names are the ones written to the file`() {
        // These are desktop Xournal++'s own spellings, the exact `<background style=…>` values: renaming
        // one would silently round-trip a page as plain paper on the desktop.
        assertEquals(
            listOf("plain", "lined", "ruled", "graph", "dotted", "isograph"),
            BACKGROUND_STYLES.map { it.first },
        )
    }

    @Test
    fun `a page with no solid sheet and an unknown style name have no label`() {
        // Null is what a PDF/pixmap page reports: there is no paper to name, so the row shows no hint.
        assertNull(paperStyleLabel(null))
        assertNull(paperStyleLabel(""))
        assertNull(paperStyleLabel("some-style-a-newer-file-has"))
    }
}
