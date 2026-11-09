package com.mobixournal.tabs

import android.content.ContentResolver
import com.mobixournal.format.SaveFormat
import com.mobixournal.io.DocumentIo
import com.mobixournal.io.MobixOpenSessions
import com.mobixournal.render.blankDocument
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A `.mobix` that was just opened **is** its archive, so its snapshot is that archive's bytes: copying
 * them is proportional to the file, while re-encoding rebuilds every page's JSON and streams the bundled
 * recording back out — the background I/O that made opening a document with audio take seconds.
 *
 * The licence to copy is **identity**: the tab's document must still be the very instance the file was
 * opened as, so the first edit (which commits a new document) turns the snapshot back into an encode.
 */
class VerbatimSnapshotTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun io(): DocumentIo = DocumentIo(
        resolver = object : ContentResolver(null) {},
        cacheDir = temp.newFolder("cache"),
        filesDir = temp.newFolder("files"),
        appVersion = "1.3.0",
        textPdf = null,
    )

    private val archiveBytes = "PK\u0003\u0004 a whole container, audio and all".toByteArray()

    private fun snapshots(archive: File?) =
        MobixTabSnapshots(io(), uneditedArchive = { archive })

    private fun tab(document: com.mobixournal.format.model.Document) =
        OpenTab("tab-1", "Lecture", document, "content://doc/1", opening = false)

    @Test fun anUneditedOpenIsSnapshottedAsItsOwnArchive() {
        val archive = temp.newFile("staged.mobix").also { it.writeBytes(archiveBytes) }
        val target = File(temp.root, "snapshot.mobix")
        val document = blankDocument()

        snapshots(archive).write(target, tab(document).copy(format = SaveFormat.MOBIX), SnapshotSources())

        assertArrayEquals("the snapshot is the archive, byte for byte", archiveBytes, target.readBytes())
        assertFalse("the temporary a copy is published through must not survive", File(target.path + ".part").exists())
    }

    @Test fun anEditedDocumentFallsBackToAnEncodeAndAStaleArchiveIsNeverCopied() {
        val session = MobixOpenSessions()
        val document = blankDocument()
        session.opened(document)
        assertTrue("the instance the file was opened as", session.openedUnedited(document))
        assertFalse("an edit commits a new document, so the record stops matching",
            session.openedUnedited(document.copy(title = "edited")))
    }
}
