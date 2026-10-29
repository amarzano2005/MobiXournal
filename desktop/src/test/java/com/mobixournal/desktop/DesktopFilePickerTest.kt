package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The file dialogs, in the two parts that can be checked without a reader clicking anything.
 *
 * **The type rows** are the window's own facts and they are pure: the save box lists the three containers
 * in the order the enum names them, a row maps back to exactly the container it writes, and the open row
 * accepts both readable documents — the filter `java.awt.FileDialog` ignored on Windows and the native box
 * does not.
 *
 * **The binding** is probed for real ([DesktopFilePickerWindows.probe]): the shell's dialog is created,
 * given the type rows, read back and released without ever being shown. That is what a wrong vtable slot
 * cannot survive, and it is skipped on a host that has no shell dialog at all.
 */
class DesktopFilePickerTest {

    @Test
    fun `the save box lists the three containers in the enum's order`() {
        assertEquals(
            DesktopNewDocumentFormat.entries.map { it.label },
            DOCUMENT_SAVE_TYPES.map { it.label },
        )
        assertEquals(
            "each row names its own suffix",
            DesktopNewDocumentFormat.entries.map { it.extension },
            DOCUMENT_SAVE_TYPES.map { it.extensions.single() },
        )
        assertEquals("three rows, not four", 3, DOCUMENT_SAVE_TYPES.size)
    }

    @Test
    fun `a row answers exactly the container it writes`() {
        DesktopNewDocumentFormat.entries.forEachIndexed { index, format ->
            assertEquals(format, documentFormatOf(index))
            assertEquals("the picker opens on the document's own container", index, documentTypeIndexOf(format))
        }
        assertEquals("an answer outside the box is the default", DesktopNewDocumentFormat.DEFAULT, documentFormatOf(7))
        assertEquals(0, documentTypeIndexOf(DesktopNewDocumentFormat.MOBIX))
    }

    @Test
    fun `the open box accepts both readable documents`() {
        val row = DOCUMENT_OPEN_TYPES.single()
        assertTrue(row.matches("notes.mobix"))
        assertTrue("a suffix typed in another case is the same file", row.matches("notes.XOPP"))
        assertFalse(row.matches("notes.pdf"))
        assertEquals("the shell wants a bare suffix as its default extension", "mobix", row.defaultExtension)
        assertTrue(PDF_TYPES.single().matches("lecture.pdf"))
        assertTrue(BACKUP_TYPES.single().matches("MobiXournal-settings.properties"))
    }

    @Test
    fun `the type box is one row per container, filters included`() {
        assertEquals(listOf("*.mobix", "*.xopp", "*.xopp"), DOCUMENT_SAVE_TYPES.map { filterOf(it.extensions.single()) })
        assertEquals("two patterns, one row", listOf("*.mobix", "*.xopp"), DOCUMENT_OPEN_TYPES.single().extensions.map(::filterOf))
    }

    @Test
    fun `the shell's dialog takes the window's type box`() {
        assumeTrue("only Windows has the shell's picker", DesktopFilePickerWindows.available)
        assertTrue(
            "the shell's dialog could not be created, or refused its type box",
            DesktopFilePickerWindows.probe(DOCUMENT_SAVE_TYPES),
        )
    }
}
