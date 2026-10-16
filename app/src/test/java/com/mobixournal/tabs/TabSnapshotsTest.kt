package com.mobixournal.tabs

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.asAssetSource
import com.mobixournal.render.BackgroundResolver
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A `.mobix` tab has to come back from a restart with the payloads its pages name.
 *
 * The bug this pins: the session cache wrote gzip XML for every tab whatever its format, so a page's
 * bundled picture (and a stroke's recording) had nowhere to go, `hydrate` rebuilt the document without
 * those references, and the next save wrote the user's own file without the bytes. A `.mobix` tab
 * therefore snapshots in **its own container** (`docs/data-path.md` → *The session is cached, not
 * saved*), and the tab id decides which file — under **either** suffix, so an install that upgraded
 * still finds the session it already had.
 *
 * What is not tested here is the write itself: that is `DocumentIo.encode`, which needs a device. The
 * seam below stands in for it with the *same* format-layer calls — `MobixDocument.of` carrying the
 * payloads a source archive holds, which is what `DocumentIo.carriedAssets` does — so what these tests
 * settle is [TabStore]'s half of the contract: which file, which shape, and what survives a read.
 */
class TabSnapshotsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val appVersion = "1.3.0"
    private val picture = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
    private val recording = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 9, 9, 9)
    private val pictureEntry =
        MobixFormat.assetEntry(MobixAssetSink.sha256(picture), MobixFormat.AssetKind.IMAGE, "png")
    private val audioEntry =
        MobixFormat.assetEntry(MobixAssetSink.sha256(recording), MobixFormat.AssetKind.AUDIO, "ogg")

    /**
     * A recorded stroke, as the editor holds one: the sidecar it replayed from (`fn`/`ts`) **and** the
     * `assets/` entry its bytes are bundled under — the two names the codec writes together.
     */
    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = 0xFF102030.toInt(),
        capStyle = null,
        points = listOf(StrokePoint(10.0, 10.0, 1.4)),
        uniformWidth = true,
        id = "el_1",
    ).withAudio(AudioRef("lecture.wav", 0)).copy(bundledAudio = audioEntry)

    /** A document whose page names both payloads — the state a `.mobix` holds unsaved. */
    private fun liveDoc() = Document(
        title = "board",
        documentId = "doc_snapshot",
        pages = listOf(
            Page(
                width = 595.0,
                height = 842.0,
                background = Background.Pixmap(
                    domain = "absolute",
                    filename = "content://device-fixture-picture",
                    bundledAsset = pictureEntry,
                ),
                layers = listOf(Layer(listOf(stroke()))),
            ),
        ),
    )

    /** The archive the session opened: what a payload is carried out of ([DocumentIo] → `carriedAssets`). */
    private fun sourceArchive(): File = File(tmp.newFolder(), "source.mobix").apply {
        writeBytes(
            MobixDocument.of(
                document = liveDoc(),
                appVersion = appVersion,
                nowMillis = 1L,
                payloads = mapOf(pictureEntry to picture, audioEntry to recording),
            ).toBytes(1L),
        )
    }

    /**
     * The app's seam without the device: a `.mobix` tab is written as the native container with the
     * payloads the source archive holds, and read back through the format layer's own reader.
     */
    private inner class Seam(
        private val source: File?,
        /** What a read hands back for a `.mobix`, as `DocumentIo.read` hands back `BundledPageAssets`. */
        private val resolver: BackgroundResolver? = null,
    ) : TabSnapshots {

        override fun suffixFor(tab: OpenTab): String =
            if (tab.format == SaveFormat.MOBIX) TabSnapshots.MOBIX_SUFFIX else TabSnapshots.XOPP_SUFFIX

        override fun write(file: File, tab: OpenTab, sources: SnapshotSources) {
            if (tab.format != SaveFormat.MOBIX) {
                XoppSnapshots.write(file, tab, sources)
                return
            }
            file.writeBytes(
                MobixDocument.of(
                    document = tab.document,
                    appVersion = appVersion,
                    nowMillis = 1L,
                    canvasMode = tab.canvasMode,
                    payloads = carried(),
                ).toBytes(1L),
            )
        }

        override fun read(file: File, tab: OpenTab): SnapshotRead? = runCatching {
            if (file.name.endsWith(TabSnapshots.MOBIX_SUFFIX)) {
                SnapshotRead(
                    MobixEntrySource.fromFile(file).use { MobixDocument.open(it).toDocument() },
                    resolver,
                )
            } else {
                XoppSnapshots.read(file, tab)
            }
        }.getOrNull()

        /** The referenced entries, read out of the archive the session opened — `carriedAssets`' job. */
        private fun carried(): Map<String, ByteArray> {
            val from = source?.takeIf(File::isFile) ?: return emptyMap()
            return MobixEntrySource.fromFile(from).use { entries ->
                val assets = entries.asAssetSource()
                listOf(pictureEntry, audioEntry)
                    .mapNotNull { name -> assets.bytes(name)?.let { bytes -> name to bytes } }
                    .toMap()
            }
        }
    }

    private fun mobixTab(id: String = "t1", doc: Document = liveDoc()) =
        OpenTab(id, "board.mobix", doc, format = SaveFormat.MOBIX)

    @Test fun `a native snapshot is its own container and keeps both payloads`() {
        val dir = tmp.newFolder()
        val store = TabStore(dir, Seam(sourceArchive()))

        store.save(TabSession(listOf(mobixTab()), 0))

        assertTrue("a .mobix tab snapshots natively", File(dir, "t1.mobix").isFile)
        assertFalse("and not as gzip XML", File(dir, "t1.xopp").isFile)

        val back = store.load()!!
        assertEquals(listOf("t1"), back.tabs.map { it.id })
        val restored = store.hydrate(back.tabs[0]).document

        // The references the model keeps are the ones the file was read with.
        assertEquals(pictureEntry, (restored.pages[0].background as Background.Pixmap).bundledAsset)
        assertEquals(audioEntry, (restored.pages[0].layers[0].elements[0] as Stroke).bundledAudio)

        // And the bytes they name travel with the snapshot: this is what a save after the restart
        // carries into the user's own file instead of writing an archive with no assets/ entry.
        MobixEntrySource.fromFile(File(dir, "t1.mobix")).use { from ->
            val assets = from.asAssetSource()
            assertArrayEquals("the picture survives the restart", picture, assets.bytes(pictureEntry))
            assertArrayEquals("so does the recording", recording, assets.bytes(audioEntry))
        }
    }

    @Test fun `a session written before the native shape still restores and is upgraded on the next write`() {
        val dir = tmp.newFolder()
        // The shape before the seam existed: gzip XML for a .mobix tab, payloads and all lost.
        TabStore(dir, XoppSnapshots).save(TabSession(listOf(mobixTab()), 0))
        assertTrue(File(dir, "t1.xopp").isFile)

        val upgraded = TabStore(dir, Seam(null))
        val back = upgraded.load()!!

        assertEquals("the tab is not dropped for carrying the old shape", listOf("t1"), back.tabs.map { it.id })
        val hydrated = upgraded.hydrate(back.tabs[0])
        assertTrue("and it is not blanked either", hydrated.hydrated)

        upgraded.save(TabSession(listOf(hydrated), 0))

        assertTrue("the next write gives it the native shape", File(dir, "t1.mobix").isFile)
        assertFalse("and leaves one snapshot behind, not two", File(dir, "t1.xopp").isFile)
    }

    @Test fun `closing a tab deletes its native snapshot`() {
        val dir = tmp.newFolder()
        val store = TabStore(dir, Seam(sourceArchive()))
        store.save(TabSession(listOf(mobixTab("t1"), mobixTab("t2")), 0))

        store.save(TabSession(listOf(mobixTab("t1")), 0))

        assertFalse(File(dir, "t2.mobix").isFile)
        assertEquals(listOf("t1"), store.load()!!.tabs.map { it.id })
    }

    @Test fun `an xopp tab keeps the gzip shape and does not hold up a native one`() {
        val dir = tmp.newFolder()
        val store = TabStore(dir, Seam(sourceArchive()))
        val xopp = OpenTab("t1", "notes.xopp", liveDoc(), format = SaveFormat.ORIGINAL)
        store.save(TabSession(listOf(xopp, mobixTab("t2")), 0))

        assertTrue(File(dir, "t1.xopp").isFile)
        assertFalse(File(dir, "t1.mobix").isFile)
        assertTrue(File(dir, "t2.mobix").isFile)
        assertEquals(listOf("t1", "t2"), store.load()!!.tabs.map { it.id })
    }

    /** Handing a not-yet-parsed tab to the other pane copies its file, suffix included. */
    @Test fun `adopting a native tab carries its payloads to the other store`() {
        val left = tmp.newFolder()
        val right = tmp.newFolder()
        TabStore(left, Seam(sourceArchive())).save(TabSession(listOf(mobixTab()), 0))

        assertTrue(TabStore(right, Seam(null)).adopt(TabStore(left, Seam(null)), "t1", "t9"))

        val copy = TabStore(right, Seam(null))
            .hydrate(OpenTab("t9", "board.mobix", liveDoc(), format = SaveFormat.MOBIX, hydrated = false))
        assertEquals(pictureEntry, (copy.document.pages[0].background as Background.Pixmap).bundledAsset)
        MobixEntrySource.fromFile(File(right, "t9.mobix")).use { from ->
            assertArrayEquals(picture, from.asAssetSource().bytes(pictureEntry))
        }
    }

    /**
     * The document is not the whole restore: a `.mobix` adopts a page's picture when that page is
     * *read*, through a resolver the open path built. A hydrate that dropped it draws every page bare —
     * which is what the emulator pass caught after the container half was already right.
     */
    @Test fun `the resolver a background is read through comes back with the tab`() {
        val dir = tmp.newFolder()
        val resolver = object : BackgroundResolver {
            override fun file(background: Background): File? = null
            override val images: Map<String, File> = emptyMap()
        }
        TabStore(dir, Seam(sourceArchive(), resolver)).save(TabSession(listOf(mobixTab()), 0))

        val restored = TabStore(dir, Seam(null, resolver)).hydrate(
            OpenTab("t1", "board.mobix", liveDoc(), format = SaveFormat.MOBIX, hydrated = false),
        )

        assertTrue("the tab keeps the resolver the read produced", restored.backgrounds === resolver)
    }

    /** The read side keys off the **file**, not the tab's current format — a Save As can switch. */
    @Test fun `a native snapshot is read natively even when the tab records another format`() {
        val dir = tmp.newFolder()
        TabStore(dir, Seam(sourceArchive())).save(TabSession(listOf(mobixTab()), 0))

        val switched = TabStore(dir, Seam(null)).hydrate(
            OpenTab("t1", "board.mobix", liveDoc(), format = SaveFormat.ORIGINAL, hydrated = false),
        )

        assertTrue(switched.hydrated)
        assertEquals(pictureEntry, (switched.document.pages[0].background as Background.Pixmap).bundledAsset)
    }
}
