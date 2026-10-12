package com.mobixournal.ui

import com.mobixournal.format.SaveFormat
import com.mobixournal.io.saveNameFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format policy's user-facing half: a **new** document is born in the user's chosen format, the
 * recommended one is `.mobix`, and each choice's label says which file it writes.
 *
 * The policy itself lives in `docs/mobix-format.md`; what is pinned here is the part a code change can
 * break silently — a default that drifts back to `.xopp`, a choice mapped to the wrong [SaveFormat]
 * (so the picker offers `notes.xopp` and the writer emits `.mobix`), or a label that stops naming the
 * extension the file lands with.
 */
class NewDocumentFormatTest {

    @Test
    fun `the recommended default is the app's own document`() {
        assertEquals(NewDocumentFormat.MOBIX, AppSettings().newDocumentFormat)
        assertEquals(SaveFormat.MOBIX, NewDocumentFormat.MOBIX.format)
    }

    @Test
    fun `the choice is between the two documents, not the legacy ZIP package`() {
        assertEquals(
            "one choice per document kind: the app's own, and the desktop's",
            listOf(SaveFormat.MOBIX, SaveFormat.ORIGINAL),
            NewDocumentFormat.values().map { it.format },
        )
    }

    @Test
    fun `each choice writes the extension its label names`() {
        // The label is what the user reads and what the file manager shows, so it has to be the suffix
        // that actually lands on disk — the picker's name comes from the format, not from the label.
        for (choice in NewDocumentFormat.values()) {
            val suffix = saveNameFor("notes", choice.format).substringAfterLast('.')
            assertTrue(
                "${choice.name}'s label \"${choice.label}\" must name the .$suffix file it writes",
                choice.label.contains(".$suffix"),
            )
        }
    }

    @Test
    fun `the interchange choice is the one desktop Xournal++ opens without an export`() {
        // `.mobix` needs the companion plugin or a Save As to reach a laptop; `.xopp` does not, which is
        // what makes it the choice to offer beside the recommended one rather than a third option.
        assertEquals(1, NewDocumentFormat.values().count { it.format == SaveFormat.ORIGINAL })
        assertTrue(NewDocumentFormat.XOPP.label.contains(".xopp"))
    }
}
