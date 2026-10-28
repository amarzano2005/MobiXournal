package com.mobixournal.desktop

import com.mobixournal.format.SaveFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Save As picker's two **decisions**, held apart from the Swing dialog that asks them.
 *
 * The dialog itself cannot be opened here — a `JFileChooser` needs a screen, and every render test on this
 * host already fails at Skiko's native library — so the parts that can be wrong are the parts that are
 * pure: which container a type answers, what suffix a name typed without one is given, and what the name
 * field does when the type box changes.
 *
 * The first is the one that matters most. Before the picker, the container changed by the **name**, which
 * meant `.xopp` kept a packaged document packaged; the type box says which of the two `.xopp` forms the
 * reader means, so a plain `.xopp` must always be the gzip document desktop Xournal++ reads everywhere.
 */
class SaveAsPickerTest {

    @Test
    fun `the type box decides the container`() {
        assertEquals(
            "a .mobix type writes the native container",
            SaveFormat.MOBIX,
            DesktopSaves.formatOf(DesktopNewDocumentFormat.MOBIX.container),
        )
        assertEquals(
            "a plain .xopp type is always the gzip document",
            SaveFormat.ORIGINAL,
            DesktopSaves.formatOf(DesktopNewDocumentFormat.XOPP.container),
        )
        assertEquals(
            "the zipped type is the package",
            SaveFormat.ZIPPED,
            DesktopSaves.formatOf(DesktopNewDocumentFormat.ZIPPED.container),
        )
    }

    @Test
    fun `a name typed without a suffix is given the type's`() {
        assertEquals("note.mobix", saveAsName("note", DesktopNewDocumentFormat.MOBIX))
        assertEquals("note.xopp", saveAsName("note", DesktopNewDocumentFormat.XOPP))
        assertEquals("note.xopp", saveAsName("note", DesktopNewDocumentFormat.ZIPPED))
        assertEquals(
            "a suffix of the picked type is not doubled",
            "note.mobix",
            saveAsName("note.mobix", DesktopNewDocumentFormat.MOBIX),
        )
        assertEquals(
            "and case is a spelling, not a second file",
            "note.MOBIX",
            saveAsName("note.MOBIX", DesktopNewDocumentFormat.MOBIX),
        )
    }

    @Test
    fun `the name field follows the type box`() {
        assertEquals(
            "another type takes the suffix over",
            "note.xopp",
            saveAsRenamed("note.mobix", DesktopNewDocumentFormat.XOPP),
        )
        assertEquals(
            "including the zipped form of the same suffix",
            "note.xopp",
            saveAsRenamed("note.mobix", DesktopNewDocumentFormat.ZIPPED),
        )
        assertEquals(
            "and back again",
            "note.mobix",
            saveAsRenamed("note.xopp", DesktopNewDocumentFormat.MOBIX),
        )
        assertEquals(
            "a second dot is part of the name",
            "deck.v2.mobix",
            saveAsRenamed("deck.v2.xopp", DesktopNewDocumentFormat.MOBIX),
        )
        assertEquals(
            "the picked type's own suffix is left alone",
            "note.mobix",
            saveAsRenamed("note.mobix", DesktopNewDocumentFormat.MOBIX),
        )
        assertEquals("a bare name has nothing to replace", "note", saveAsRenamed("note", DesktopNewDocumentFormat.MOBIX))
        assertEquals(
            "and an extension this window does not write is not cut off",
            "my.notes",
            saveAsRenamed("my.notes", DesktopNewDocumentFormat.MOBIX),
        )
    }
}
