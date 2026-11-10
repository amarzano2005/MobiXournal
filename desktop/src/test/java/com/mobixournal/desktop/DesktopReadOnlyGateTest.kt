package com.mobixournal.desktop

import com.mobixournal.desktop.ink.withElement
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.io.DesktopSaves
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.MobixCapability
import com.mobixournal.format.model.TextElement
import com.mobixournal.io.DocumentEncoder
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's half of the capability notice: a document the gate opened read-only opens **as a tab**,
 * shows its pages, says on the band why, and takes no write from either of the two places a write can
 * start — an edit (`DesktopDocument.editPage`) or a save (`DesktopSaves.stage`).
 *
 * The verdict itself is `mobix-core`'s (`MobixCapabilityGateTest` owns the matrix); what these tests
 * pin is that the window reads it instead of comparing versions again, and that one answer turns off
 * the canvas and the two save commands together.
 */
class DesktopReadOnlyGateTest {

    @get:Rule
    val temp = TemporaryFolder()

    /**
     * A one-page `.mobix` whose manifest declares [required] and [optional], written by the window's own
     * save so the file is exactly what a writer with those capabilities would produce.
     */
    private fun notebookWith(
        required: List<MobixCapability> = emptyList(),
        optional: List<MobixCapability> = emptyList(),
    ): File {
        val path = File(temp.root, "declared-${required.size}-${optional.size}.mobix")
        val model = DesktopDocuments.blank(path, container = DesktopContainer.MOBIX).use { blank ->
            blank.model.copy(requiredCapabilities = required, optionalCapabilities = optional)
        }
        // The encoder, not a staged save: a *new* `.mobix` has no archive to repack over, and the repack
        // path is the one a save of an existing file takes.
        FileOutputStream(path).use { out ->
            DocumentEncoder.write(
                document = model,
                format = SaveFormat.MOBIX,
                target = DocumentEncoder.FileSaveTarget(path),
                out = out,
                appVersion = DesktopSaves.APP_VERSION,
            )
        }
        return path
    }

    @Test
    fun aRequiredCapabilityOpensReadOnlyAndRefusesEveryWrite() {
        val file = notebookWith(required = listOf(MobixCapability("com.example.future", "gradebook", 1)))
        DocumentLoader.load(file).use { document ->
            assertTrue("a required capability this build cannot write turns editing off", document.readOnly)
            assertNotNull("the notice exists", document.gateNote)
            assertEquals(
                "the page is still there to look at",
                document.pageCount,
                1,
            )
            // A real change, not `{ it }`: an unchanged page comes back null on an editable document too,
            // so an identity transform would pass here without the gate existing.
            assertNull("the canvas takes no edit", document.editPage(0) { page -> page.withElement(TextElement("Sans", 10.0, 1.0, 2.0, 0, "gate test")) })

            val refused = runCatching {
                DesktopSaves.stage(document, File(temp.root, "written-anyway.mobix"))
            }.exceptionOrNull()
            assertTrue(
                "a save is refused with the gate's own sentence, not an I/O failure: $refused",
                refused is DesktopSaves.SaveRefused,
            )
            assertEquals(
                "and the refusal is the notice's sentence",
                document.gateNote,
                refused?.message,
            )
            assertFalse(
                "nothing was written beside the document",
                File(temp.root, "written-anyway.mobix").exists(),
            )
        }
    }

    @Test
    fun anOptionalCapabilityIsKeptAsWrittenAndStaysEditable() {
        val file = notebookWith(optional = listOf(MobixCapability("com.example.future", "ink", 1)))
        DocumentLoader.load(file).use { document ->
            assertFalse(
                "an element this build preserves does not make the document read-only",
                document.readOnly,
            )
            assertNotNull("it says what it is keeping", document.gateNote)
            assertNotNull("and the page is editable", document.editPage(0) { page -> page.withElement(TextElement("Sans", 10.0, 1.0, 2.0, 0, "gate test")) })
        }
    }

    @Test
    fun aDocumentThisBuildWroteHasNothingToSay() {
        val file = notebookWith()
        DocumentLoader.load(file).use { document ->
            assertFalse(document.readOnly)
            assertNull("no note when the gate is FULL_EDIT", document.gateNote)
        }
    }
}
