package com.mobixournal.io

import com.mobixournal.BuildConfig
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixManifest
import com.mobixournal.format.mobix.MobixPageCodec
import com.mobixournal.format.mobix.MobixPart
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.mobix` files the **device** passes are driven with, written by the same code the JVM tests
 * build their fixtures from — `build/device-fixtures/`, pushed with `adb push` (the recipe is in
 * `docs/tools.md`, *Fixtures for an emulator pass*).
 *
 * It exists because the tasks these files serve are *runtime* claims that no JVM test can settle: a
 * two-hundred-page notebook proves the counter comes from the manifest without a page being read only
 * when it is opened on a screen, and a board's chunk has to be written across a boundary by a pen
 * before "a stroke lands in the chunk under the pen" is anything but a unit test. Generating them here
 * rather than by hand keeps the device fixture and the unit fixture from drifting apart, which is the
 * only way the two passes end up talking about the same document.
 *
 * Not a test of anything in the app: the assertion is that the files were written.
 */
class DeviceFixturesTest {

    /** Where every fixture lands; the same directory for every pass. */
    private val dir = File("build/device-fixtures")

    /**
     * The version the fixtures declare as `minAppVersion` — **the installed build's own**, read from
     * `BuildConfig` rather than written out again, because a document claiming a newer version is
     * offered read-only and none of these passes is about that path. Reading it means a version bump
     * cannot leave the fixtures behind the build the device pass installs; the assert below compares
     * the manifest against this same value, so the two cannot disagree either.
     */
    private val appVersion = BuildConfig.VERSION_NAME

    /**
     * A paginated notebook of [pageCount] pages, each with its own stroke so a page is recognisable in
     * a screenshot, page 3 ruled and page 2 carrying a **bundled picture** — the background the paged
     * open adopts when that page is read, and never before (`BackgroundResolver`). Its
     * `minAppVersion` is this build's, like the board's, so neither fixture depends on what the
     * writer's default happens to be.
     */
    private fun paginated(file: File, pageCount: Int = 200): File {
        val sink = MobixAssetSink()
        val pictureAsset = sink.add(pictureBytes(), MobixFormat.AssetKind.IMAGE)
        val pages = SyntheticNotebook.pages(pageCount).toMutableList()
        pages[1] = pages[1].copy(
            background = Background.Pixmap(
                domain = "absolute",
                filename = "content://device-fixture-picture.png",
                bundledAsset = pictureAsset,
            ),
        )
        pages[2] = pages[2].copy(background = Background.Solid(0xFFFFFFFF.toInt(), "graph"))

        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        pages.forEachIndexed { index, page ->
            val name = SyntheticNotebook.entryOf(index)
            val json = JsonWriter.write(MobixPageCodec.encode(page, sink) { null }).toByteArray(Charsets.UTF_8)
            entries += MobixContainer.Entry(name, json)
            parts += MobixPart(
                id = page.id ?: SyntheticNotebook.idOf(index),
                entry = name,
                sha256 = MobixAssetSink.sha256(json),
                modified = 1L,
            )
        }
        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_device_paged",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1L,
            title = "Device 200",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        ).copy(pages = parts, minAppVersion = appVersion)
        FileOutputStream(file).use { out ->
            MobixContainer.save(
                listOf(
                    MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
                    MobixContainer.Entry(
                        MobixFormat.CANVAS_ENTRY,
                        MobixCanvasFile(CanvasMode.PAGINATED, SyntheticNotebook.WIDTH_PT, SyntheticNotebook.HEIGHT_PT)
                            .encode().toByteArray(Charsets.UTF_8),
                    ),
                ) + entries + sink.entries(),
                out,
            )
        }
        return file
    }

    /**
     * A 2D board with three occupied cells — `(0, 0)`, `(1, 0)` beside it so a stroke can be drawn
     * **across** their shared edge, and `(-1, 0)` so the archive carries a signed chunk name — one
     * stroke per cell, plus the one the passes write.
     */
    private fun board(file: File): File {
        val chunks = listOf(
            chunk(0, 0, "stk_0_0"),
            chunk(1, 0, "stk_1_0"),
            chunk(-1, 0, "stk_-1_0"),
        )
        val document = MobixDocument.forInfinite(
            documentId = "doc_device_board",
            canvasMode = CanvasMode.INFINITE_2D,
            widthPt = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
            heightPt = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
            chunks = chunks,
            title = "Device board",
            // The app's own version, not the writer's default: a fixture that declares a newer one
            // than the build under test opens read-only, which is not the pass this file is for.
            appVersion = appVersion,
        )
        FileOutputStream(file).use { out -> MobixContainer.save(document.encode(nowMillis = 1L).entries, out) }
        return file
    }

    /** One occupied cell: a stroke near its inner edge, so the tile it belongs to is visible. */
    private fun chunk(cx: Int, cy: Int, strokeId: String) = MobixChunk(
        cx = cx,
        cy = cy,
        layers = listOf(Layer(listOf(SyntheticNotebook.stroke(40.0, id = strokeId)))),
        id = "ch_${cx}_$cy",
    )

    /**
     * A picture a person can see, and a PNG the app can decode (`MobixAssetSink.suffixFor` says so).
     *
     * Encoded by hand rather than through AWT: an Android unit test compiles against `android.jar`,
     * which has no `java.awt`, and a fixture generator that only runs on a desktop JVM is a fixture
     * nobody runs. Four horizontal bands in RGBA, so a screenshot says whether the page drew it.
     */
    private fun pictureBytes(): ByteArray {
        val width = 240
        val height = 320
        val bands = intArrayOf(0xE8342A, 0xF5C211, 0x2E8B3C, 0x1B4FA8)
        val raw = ByteArray(height * (1 + width * 4))
        var at = 0
        for (y in 0 until height) {
            raw[at++] = 0 // a filter byte per scanline: none
            val color = bands[y / (height / bands.size)]
            for (x in 0 until width) {
                raw[at++] = ((color shr 16) and 0xFF).toByte()
                raw[at++] = ((color shr 8) and 0xFF).toByte()
                raw[at++] = (color and 0xFF).toByte()
                raw[at++] = 0xFF.toByte()
            }
        }
        val header = ByteArrayOutputStream().apply {
            write(intBytes(width))
            write(intBytes(height))
            write(byteArrayOf(8, 6, 0, 0, 0)) // 8-bit RGBA, deflate, adaptive filtering, no interlace
        }.toByteArray()
        val deflated = ByteArray(raw.size + raw.size / 100 + 64)
        val deflater = Deflater(Deflater.BEST_SPEED)
        val length = try {
            deflater.setInput(raw)
            deflater.finish()
            deflater.deflate(deflated)
        } finally {
            deflater.end()
        }
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            writeChunk("IHDR", header)
            writeChunk("IDAT", deflated.copyOf(length))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    /** One PNG chunk: length, type, data, CRC over type and data. */
    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        write(intBytes(data.size))
        write(tag)
        write(data)
        write(intBytes(CRC32().apply { update(tag); update(data) }.value.toInt()))
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    @Test
    fun writesTheDeviceFixtures() {
        dir.mkdirs()
        val pages = paginated(File(dir, "paged-200.mobix"))
        val board = board(File(dir, "board-2d.mobix"))

        println("device fixtures: ${pages.absolutePath} (${pages.length()} B), ${board.absolutePath} (${board.length()} B)")
        assertTrue("the 200-page notebook must be written", pages.length() > 0)
        assertTrue("the board must be written", board.length() > 0)

        // The notebook's pages are the fixture's, not this build's, and one of them names a picture.
        val pageNames = pages.inputStream().use { MobixContainer.open(it.buffered()).names.toList() }
        assertEquals("the notebook carries 200 pages", 200, pageNames.count { MobixFormat.isPageEntry(it) })
        assertTrue(
            "the picture must be a PNG the app decodes",
            pageNames.any { MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.IMAGE) && it.endsWith(".png") },
        )

        // Both must open **writable** in the build under test: a document whose `minAppVersion` is
        // newer than the app is offered read-only, and a read-only board cannot be drawn on.
        for ((label, file) in listOf("paged-200.mobix" to pages, "board-2d.mobix" to board)) {
            val manifest = file.inputStream().use { MobixContainer.open(it.buffered()) }
                .let { archive ->
                    MobixManifest.decode(archive.require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8))
                }
            assertTrue(
                "$label declares ${manifest.minAppVersion}, which is newer than $appVersion",
                !manifest.requiresAppNewerThan(appVersion),
            )
        }

        // The board carries its cells under signed names, one entry each.
        val names = board.inputStream().use { MobixContainer.open(it.buffered()).names.toList() }
        assertEquals(
            "the board carries its three cells under signed names",
            listOf(
                MobixFormat.chunkEntry(0, 0),
                MobixFormat.chunkEntry(1, 0),
                MobixFormat.chunkEntry(-1, 0),
            ).sorted(),
            names.filter { MobixFormat.isChunkEntry(it) }.sorted(),
        )
    }
}
