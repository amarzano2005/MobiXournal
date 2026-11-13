package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MSI's file associations are the one place the Windows window's document suffixes are written down
 * that the compiler cannot check. The failure is silent and concrete: setup still succeeds, and a
 * double-click on the file a release just introduced opens nothing.
 *
 * The suffixes are the ones the window's Open row lists — `DOCUMENT_OPEN_TYPES` owns that list — and a
 * portable `.pmobix` is a `.mobix` container, so it carries the same declared type. Unit tests run with
 * the module directory as their working directory (see [SourceStructureTest]), hence the path candidates.
 */
class InstallerFileAssociationsTest {

    @Test
    fun everySuffixTheWindowOpensHasAnAssociation() {
        val installer = installerText()
        val missing = SUFFIXES.filterNot { suffix ->
            installer.contains("Key=\"Software\\Classes\\.$suffix\"")
        }

        assertTrue(
            "these documents could not be opened by double-clicking them — add an association in" +
                " desktop/installer/main.wxs:\n  " + missing.joinToString("\n  ") { ".$it" },
            missing.isEmpty(),
        )
    }

    @Test
    fun everyAssociationRunsTheQuotedLauncherWithTheDocument() {
        val installer = installerText()

        assertEquals(
            "one open command per suffix, each quoting both the executable and the document path",
            SUFFIXES.size,
            countOf(installer, "Value=\"&quot;[INSTALLDIR]MobiXournal.exe&quot; &quot;%1&quot;\""),
        )
    }

    @Test
    fun thePortableCopySharesTheNotebooksDeclaredType() {
        val installer = installerText()

        assertEquals(
            "a .pmobix is a .mobix container, so Windows must be told the same content type" +
                " (the .xopp association declares its own)",
            2,
            countOf(installer, "Value=\"application/vnd.mobixournal+zip\""),
        )
    }

    private fun countOf(text: String, needle: String): Int = text.split(needle).size - 1

    /** The installer as the build sees it, from the module directory the tests run in. */
    private fun installerText(): String {
        val candidates = listOf("../desktop/installer/main.wxs", "desktop/installer/main.wxs")
        val file = candidates.map(::File).firstOrNull { it.isFile }
            ?: error("no desktop/installer/main.wxs from ${File(".").absolutePath}")
        return file.readText()
    }

    private companion object {
        /** Every document suffix the window lists in Open, plus the two containers it writes. */
        val SUFFIXES = listOf("mobix", "pmobix", "xopp")
    }
}
