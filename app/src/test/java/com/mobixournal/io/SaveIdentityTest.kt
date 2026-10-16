package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [DocumentIo.identifyForSave] — the identity the editor adopts on the model it is about to snapshot.
 *
 * The editor has to adopt it **before** the write (the ids have to be the model's own, so the file and
 * the document cannot disagree — `format/model/DocumentIdentity.kt`), and which pages it adopts over is
 * the save's own answer: the ones that write will build, never the pages it copies, because a page the
 * archive already holds is a page no id has to be minted into and asking it for one inflates it
 * (`format/mobix/MobixPageReuse.kt`, measured in `MobixIncrementalSaveBenchmarkTest`).
 *
 * What a JVM test can reach is that decision's **fallback**, which is the half that has to stay
 * conservative: a document with no archive behind it — a new document, a first save, a Save As to
 * another target — reuses nothing, so every page is identified. A narrower adoption there would leave
 * the model without the ids the file was written with, which is the drift identity exists to prevent.
 * The narrowing branch itself needs the open archive's URI to match the target, and a unit test cannot
 * build a `content://` URI (`Uri.parse` is a stub in the mockable android.jar): the emulator pass that
 * saw the paged open ran over a `file://` path, so the SAF shape — a `DocumentsProvider` URI with a
 * persisted read grant, which is how a document picked from Downloads arrives — is still an on-device
 * check (`open-and-save-a-mobix-through-a-content-uri`).
 */
class SaveIdentityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun createIo(): DocumentIo = DocumentIo(
        resolver = object : ContentResolver(null) {},
        cacheDir = temp.newFolder("cache"),
        filesDir = temp.newFolder("files"),
        appVersion = "1.3.0",
        textPdf = null,
    )

    /**
     * A document of the model's own — what a `.xopp` import or a new document is: pages the reader
     * built, carrying no ids anywhere (a `.mobix` read from disk has them minted by its decoder, which
     * is why this fixture is built here rather than read from a file).
     */
    private fun inMemory(pageCount: Int = 3): Document = Document(
        pages = List(pageCount) { index ->
            Page(
                width = SyntheticNotebook.WIDTH_PT,
                height = SyntheticNotebook.HEIGHT_PT,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(SyntheticNotebook.stroke(index.toDouble(), id = null)))),
                id = null,
            )
        },
        documentId = null,
    )

    private fun idsOf(page: Page): List<String?> =
        page.layers.flatMap { it.elements }.map { ElementId.idOf(it) }

    @Test
    fun aDocumentWithNoSourceIsIdentifiedOverEveryPage() {
        val io = createIo()
        val document = inMemory(pageCount = 200)
        assertTrue("the fixture's pages carry no ids", document.pages.all { page -> idsOf(page).all { it == null } })

        val identified = io.identifyForSave(document, target = null)

        assertNotNull("the document's own id is minted", identified.documentId)
        assertTrue(
            "and with nothing to copy, every page is identified — not just the ones that moved",
            identified.pages.all { page -> idsOf(page).all { it != null } },
        )
        assertTrue("every page is still there", identified.pages.size == 200)
    }

    @Test
    fun anIdentifiedDocumentIsHandedBackAsItIs() {
        val io = createIo()
        val first = io.identifyForSave(inMemory(pageCount = 1), target = null)

        assertSame(
            "a second save has nothing to adopt, so no pane is woken for a document that did not change",
            first,
            io.identifyForSave(first, target = null),
        )
    }
}
