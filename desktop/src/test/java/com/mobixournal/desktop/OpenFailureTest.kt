package com.mobixournal.desktop

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What the window says when an open fails, and what it does with the path it was handed.
 *
 * Both rules here were broken by real runs of the preview: a stale window reported a `LinkageError` as
 * if the document were damaged (the detail read `…/DocumentLoader$openMobix$1`), and a notebook opened
 * from the command line with a space in its path arrived as two arguments and became `File("…/Il")`.
 */
class OpenFailureTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** The accident: the code was recompiled under the live window, so a class is gone. */
    @Test
    fun `a missing class blames the window, not the document`() {
        val reported = describeOpenFailure(
            NoClassDefFoundError("com/mobixournal/desktop/DocumentLoader\$openMobix\$1"),
        )
        assertTrue(reported.title, reported.title.contains("rimasta indietro"))
        assertTrue(reported.detail, reported.detail.contains("non c'entra"))
        assertTrue(reported.detail, reported.detail.contains("rilancia"))
        // The raw synthetic name is what the old notice showed — it must not reach the user.
        assertFalse(reported.detail, reported.detail.contains("DocumentLoader"))
    }

    /** The loader's own refusals already explain themselves; they keep their words. */
    @Test
    fun `an unsupported file keeps the loader's explanation`() {
        val text = temp.newFile("note.txt").apply { writeText("ciao") }
        val failure = runCatching { DocumentLoader.load(text) }.exceptionOrNull()
        assertTrue("expected a refusal, got $failure", failure is UnsupportedOperationException)

        val reported = describeOpenFailure(failure!!)
        assertEquals("Formato non supportato", reported.title)
        assertTrue(reported.detail, reported.detail.contains("non un documento MobiXournal"))
    }

    /** Anything else carries its class name, which is what makes a bug report possible. */
    @Test
    fun `an unexpected failure names its class and message`() {
        val reported = describeOpenFailure(FileNotFoundException("C:\\notes\\a.mobix"))
        assertEquals("Impossibile aprire il documento", reported.title)
        assertTrue(reported.detail, reported.detail.startsWith("FileNotFoundException:"))
        assertTrue(reported.detail, reported.detail.contains("a.mobix"))
    }

    /** A failure with no message is still readable. */
    @Test
    fun `a silent failure still says something`() {
        val reported = describeOpenFailure(RuntimeException())
        assertEquals("Impossibile aprire il documento", reported.title)
        assertEquals("RuntimeException: nessun dettaglio disponibile", reported.detail)
    }

    /** A page too big for the heap is a memory problem, and says so instead of a class name. */
    @Test
    fun `running out of memory is reported as memory`() {
        val reported = describeOpenFailure(OutOfMemoryError("Java heap space"))
        assertEquals("Memoria insufficiente", reported.title)
    }

    @Test
    fun `a plain path on the command line is the document`() {
        val file = temp.newFile("udiff.xopp")
        assertEquals(file, initialDocument(arrayOf(file.path)))
    }

    /** `--args` splits on spaces; the pieces are the same path with the gaps put back. */
    @Test
    fun `a path with spaces split into arguments is put back together`() {
        val folder = temp.newFolder("Il mio Drive")
        val file = File(folder, "Appunti 01.mobix").apply { writeText("x") }
        val pieces = file.path.split(" ")
        assertTrue("the fixture must actually split", pieces.size > 1)

        assertEquals(file, initialDocument(pieces.toTypedArray()))
    }

    @Test
    fun `no argument starts empty`() {
        assertEquals(null, initialDocument(emptyArray()))
    }

    @Test
    fun `a missing path is handed back for the loader to report`() {
        val missing = File(temp.root, "assente.mobix")
        assertEquals(missing, initialDocument(arrayOf(missing.path)))
    }
}
