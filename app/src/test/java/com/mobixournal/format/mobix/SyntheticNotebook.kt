package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.File
import java.io.FileOutputStream

/**
 * A synthetic paginated `.mobix` for the tests that care about *how many pages are opened*, not about
 * the ink: one stroke per page, a size declared once in `canvas.json`, and a manifest listing every
 * page. Written through the real container, so a reader sees an ordinary archive.
 */
internal object SyntheticNotebook {

    /** The page size the fixture's `canvas.json` declares, in pt. */
    const val WIDTH_PT = 595.0
    const val HEIGHT_PT = 842.0

    /**
     * A two-point stroke at [x], so a page's content is identifiable and non-empty. [id] is set by
     * the fixture: a stroke written without one has a **fresh id minted on every encode**, which would
     * make a page read back from the archive compare unequal to the one that was written.
     */
    fun stroke(x: Double, id: String? = null): Stroke = Stroke(
        points = listOf(StrokePoint(x, x, 1.0), StrokePoint(x + 10.0, x + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
        id = id,
    )

    /** [count] pages, page `i` holding one stroke and the id the manifest will name it by. */
    fun pages(count: Int): List<Page> = List(count) { index ->
        Page(
            width = WIDTH_PT,
            height = HEIGHT_PT,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(listOf(stroke(index.toDouble(), id = strokeIdOf(index))))),
            id = idOf(index),
        )
    }

    /** The page id of page [index] (0-based) — what the manifest's `pages[]` carries. */
    fun idOf(index: Int): String = "pg_%03d".format(index + 1)

    /** A stable id for page [index]'s stroke, so a page survives a write and a read unchanged. */
    fun strokeIdOf(index: Int): String = "stk_p%03d".format(index + 1)

    /**
     * The archive entry of page [index] (0-based) — the format's own name for it, not a fixture-local
     * one: the manifest is what a reader goes by, but a fixture written under a name the app's writer
     * would never produce hides a mismatch until an encode test hits it.
     */
    fun entryOf(index: Int): String = MobixFormat.pageEntry(index + 1)

    /** Write [pages] to [file] as a real `.mobix`, with the page order the manifest fixes. */
    fun write(file: File, pages: List<Page>) {
        val now = 1700000000000L
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        pages.forEachIndexed { index, page ->
            val entry = entryOf(index)
            val json = JsonWriter.write(MobixPageCodec.encode(page, MobixAssetSink())).toByteArray(Charsets.UTF_8)
            entries += MobixContainer.Entry(entry, json)
            parts += MobixPart(
                id = page.id ?: idOf(index),
                entry = entry,
                sha256 = MobixAssetSink.sha256(json),
                modified = now,
            )
        }
        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_synthetic",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            title = "Synthetic notebook",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.0.0",
        ).copy(pages = parts)
        val canvas = MobixCanvasFile(CanvasMode.PAGINATED, WIDTH_PT, HEIGHT_PT)
        val envelope = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas.encode().toByteArray(Charsets.UTF_8)),
        )
        FileOutputStream(file).use { MobixContainer.save(envelope + entries, it) }
    }
}
