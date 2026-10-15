package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.mobixournal.tabs.OpenTab
import com.mobixournal.tabs.TabIndex
import com.mobixournal.tabs.TabSession
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for rule 4 of docs/mobix-schema-spec.md:
 * A .mobix document requiring a newer app version than this build (minAppVersion > appVersion)
 * must be flagged as [LoadedFile.Doc.needsNewerApp] so the activity can offer a read-only open.
 *
 * Verifies that:
 * - A synthetic .mobix with minAppVersion higher than appVersion returns needsNewerApp = true.
 * - A synthetic .mobix with same minAppVersion returns needsNewerApp = false.
 * - A synthetic .mobix with lower minAppVersion returns needsNewerApp = false.
 * - A synthetic .mobix with no minAppVersion field returns needsNewerApp = false.
 * - A standard gzip .xopp returns needsNewerApp = false.
 * - A ZIP-package .xopp returns needsNewerApp = false.
 * - TabIndex preserves and restores the isReadOnly state across sessions.
 */
class DocumentIoNewerAppTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val testAppVersion = "1.3.0"
    private val nowMillis = 1_775_952_000_000L

    private fun createIo(appVersion: String = testAppVersion): DocumentIo {
        val resolver = object : ContentResolver(null) {}
        val cache = temp.newFolder("cache")
        val files = temp.newFolder("files")
        return DocumentIo(
            resolver = resolver,
            cacheDir = cache,
            filesDir = files,
            appVersion = appVersion,
        )
    }

    private fun sampleDocument(): Document = Document(
        title = "Test",
        pages = listOf(
            Page(
                width = 612.0,
                height = 792.0,
                background = com.mobixournal.format.model.Background.Solid(
                    color = 0xFFFFFFFF.toInt(),
                    style = "plain",
                ),
                layers = emptyList(),
            ),
        ),
    )

    private fun writeMobix(file: File, minAppVersion: String?, appVersion: String = testAppVersion) {
        val base = MobixDocument.of(sampleDocument(), appVersion = appVersion, nowMillis = nowMillis)
        val modifiedManifest = base.manifest.copy(minAppVersion = minAppVersion ?: "0.0.0")
        val doc = MobixDocument(
            manifest = modifiedManifest,
            canvas = base.canvas,
            pages = base.pages,
            bridge = base.bridge,
            payloads = base.payloads,
            preserved = base.preserved,
        )
        val entries = doc.encode(nowMillis, backgroundBytes = { null }).map { entry ->
            if (minAppVersion == null && entry.name == com.mobixournal.format.mobix.MobixFormat.MANIFEST_ENTRY) {
                // Remove minAppVersion from manifest.json to test actual missing field in archive
                val text = entry.bytes.toString(Charsets.UTF_8)
                val withoutMinApp = text.replace(Regex(""""minAppVersion":\s*"[^"]*",?"""), "")
                com.mobixournal.format.mobix.MobixContainer.Entry(entry.name, withoutMinApp.toByteArray(Charsets.UTF_8))
            } else {
                entry
            }
        }
        file.writeBytes(com.mobixournal.format.mobix.MobixContainer.toBytes(entries))
    }

    @Test
    fun mobixWithHigherMinAppVersionSetsNeedsNewerAppTrue() {
        val io = createIo()
        val file = temp.newFile("newer.mobix")
        writeMobix(file, minAppVersion = "2.0.0")

        val loaded = io.read(file)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertEquals(SaveFormat.MOBIX, doc.format)
        assertTrue("needsNewerApp must be true for minAppVersion 2.0.0 vs 1.3.0", doc.needsNewerApp)
    }

    @Test
    fun mobixWithSameMinAppVersionLeavesNeedsNewerAppFalse() {
        val io = createIo()
        val file = temp.newFile("same.mobix")
        writeMobix(file, minAppVersion = testAppVersion)

        val loaded = io.read(file)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertFalse("needsNewerApp must be false for same minAppVersion", doc.needsNewerApp)
    }

    @Test
    fun mobixWithLowerMinAppVersionLeavesNeedsNewerAppFalse() {
        val io = createIo()
        val file = temp.newFile("older.mobix")
        writeMobix(file, minAppVersion = "1.0.0")

        val loaded = io.read(file)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertFalse("needsNewerApp must be false for lower minAppVersion", doc.needsNewerApp)
    }

    @Test
    fun mobixWithNoMinAppVersionLeavesNeedsNewerAppFalse() {
        val io = createIo()
        val file = temp.newFile("no_min.mobix")
        writeMobix(file, minAppVersion = null)

        val loaded = io.read(file)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertFalse("needsNewerApp must be false when minAppVersion is absent", doc.needsNewerApp)
    }

    @Test
    fun gzipXoppLeavesNeedsNewerAppFalse() {
        val io = createIo()
        val file = temp.newFile("document.xopp")
        file.outputStream().use { Xopp.save(sampleDocument(), it) }

        val loaded = io.read(file)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertEquals(SaveFormat.ORIGINAL, doc.format)
        assertFalse("needsNewerApp must be false for gzip .xopp", doc.needsNewerApp)
    }

    @Test
    fun zippedPackageXoppLeavesNeedsNewerAppFalse() {
        val io = createIo()
        val file = temp.newFile("package.xopp")
        file.outputStream().use { XoppZip.save(sampleDocument(), null, it) }

        val loaded = io.read(file)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertEquals(SaveFormat.ZIPPED, doc.format)
        assertFalse("needsNewerApp must be false for zipped .xopp", doc.needsNewerApp)
    }

    @Test
    fun tabIndexPreservesAndRestoresReadOnlyState() {
        val tab = OpenTab(
            id = "test-tab",
            title = "Read Only Document",
            document = sampleDocument(),
            format = SaveFormat.MOBIX,
            isReadOnly = true,
        )
        val session = TabSession(tabs = listOf(tab), activeIndex = 0)
        val encoded = TabIndex.encode(session)
        val decoded = TabIndex.decode(encoded) { sampleDocument() }

        assertEquals(1, decoded.tabs.size)
        assertTrue("isReadOnly must survive encode/decode", decoded.tabs.first().isReadOnly)
    }

    @Test
    fun tabIndexDecodesLegacyEntryWithoutReadOnlyFieldAsFalse() {
        val legacyIndex = "active\t0\ntab-1\tLegacyDoc\t\tORIGINAL\t\t0\tkey-1\n"
        val decoded = TabIndex.decode(legacyIndex) { sampleDocument() }

        assertEquals(1, decoded.tabs.size)
        assertFalse("isReadOnly must default to false for legacy entries", decoded.tabs.first().isReadOnly)
    }

    @Test
    fun mobixDetectsConflictSiblingAndMergesRemoteEdits() {
        val io = createIo()
        val dir = temp.newFolder("notes_sync")
        val mainFile = File(dir, "notes.mobix")

        // Main file has 1 page with stroke stk_1
        val stroke1 = com.mobixournal.format.model.Stroke(
            points = listOf(com.mobixournal.format.model.StrokePoint(0.0, 0.0, 1.0)),
            color = 0,
            tool = com.mobixournal.format.model.Tool.PEN,
            capStyle = "round",
            uniformWidth = false,
            id = "stk_1",
        )
        val doc1 = Document(
            title = "Notes",
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(com.mobixournal.format.model.Layer(listOf(stroke1))),
                    id = "pg_1",
                ),
            ),
        )
        val mobix1 = MobixDocument.of(doc1, appVersion = testAppVersion, nowMillis = nowMillis)
        mainFile.writeBytes(mobix1.toBytes(nowMillis))

        // Syncthing conflict file with page 2 added on laptop
        val conflictFile = File(dir, "notes.sync-conflict-20261012-120000-LAPTOP.mobix")
        val stroke2 = com.mobixournal.format.model.Stroke(
            points = listOf(com.mobixournal.format.model.StrokePoint(10.0, 10.0, 1.0)),
            color = 0,
            tool = com.mobixournal.format.model.Tool.PEN,
            capStyle = "round",
            uniformWidth = false,
            id = "stk_remote",
        )
        val page2Remote = Page(
            width = 595.0,
            height = 842.0,
            background = com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(com.mobixournal.format.model.Layer(listOf(stroke2))),
            id = "pg_2",
        )
        val doc2 = Document(
            title = "Notes",
            pages = listOf(doc1.pages[0], page2Remote),
        )
        val mobix2 = MobixDocument.of(doc2, appVersion = testAppVersion, nowMillis = nowMillis + 1000)
        conflictFile.writeBytes(mobix2.toBytes(nowMillis + 1000))

        // Opening mainFile should discover conflict sibling, merge it, and archive conflict file
        val loaded = io.read(mainFile)
        assertTrue("expected Doc result", loaded is LoadedFile.Doc)
        val doc = loaded as LoadedFile.Doc
        assertEquals(2, doc.document.pages.size)
        assertEquals("pg_2", doc.document.pages[1].id)
        assertNotNull("Conflict notification must be non-null", doc.conflictNotification)
        assertTrue("Notification indicates remote page adopted", doc.conflictNotification!!.contains("remote page"))

        // Conflict file is safely archived to .bak
        assertFalse("Original conflict file should be moved/renamed", conflictFile.exists())
        val archivedFile = File(dir, "notes.sync-conflict-20261012-120000-LAPTOP.mobix.conflict-merged.bak")
        assertTrue("Archived conflict file must exist", archivedFile.exists())
    }
}
