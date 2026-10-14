package com.mobixournal.format

import android.content.ContentResolver
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.io.DocumentIo
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The measurement behind the size case in docs/mobix-format.md and docs/xopp-format.md: one
 * multi-page document written through the **real** writers — `io/DocumentIo.encode` — as
 * `SaveFormat.MOBIX`, `SaveFormat.ORIGINAL` and `SaveFormat.ZIPPED`, reporting the bytes on disk
 * for each and the wall time to write and to read back.
 *
 * Run it with:
 *
 * ```
 * ./gradlew testDebugUnitTest --tests com.mobixournal.format.MobixXoppSizeBenchmarkTest
 * ```
 *
 * and read the report it writes to `build/reports/mobix-vs-xopp.txt` (it is also printed to the
 * test's standard output, visible with `--info`).
 *
 * The picture and the PDF are fixed **incompressible blobs** (a seeded PRNG, so the bytes are the
 * same on every run): the point is the container's overhead around them, not any particular PDF
 * encoder's ratio. The document is [pages] pages of ink, plus one embedded picture and a PDF
 * background on every page in the `withAssets` case.
 *
 * The two self-contained writers (MOBIX and ZIPPED) both carry the binary; ORIGINAL **links** the
 * PDF by reference instead, so its number is reported but is not the same comparison — the report
 * says which is which rather than pretending they are equal.
 */
class MobixXoppSizeBenchmarkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pages = 8
    private val strokesPerPage = 20
    private val pointsPerStroke = 30
    private val iterations = 5

    /** A fixed ~120 KB incompressible blob, as the picture the document embeds. */
    private val pictureBytes: ByteArray = Random(42).nextBytes(120_000)

    /** A fixed ~250 KB incompressible blob behind a real PDF header, as the background PDF. */
    private val pdfBytes: ByteArray = pdfBlob(250_000)

    private val appVersion = "1.3.0"

    private fun pdfBlob(size: Int): ByteArray {
        val header = "%PDF-1.7\n".toByteArray(Charsets.US_ASCII)
        return header + Random(7).nextBytes(size - header.size)
    }

    private fun newIo(): DocumentIo = DocumentIo(
        resolver = object : ContentResolver(null) {},
        cacheDir = temp.newFolder("cache"),
        filesDir = temp.newFolder("files"),
        appVersion = appVersion,
    )

    private fun sampleDocument(withAssets: Boolean): Document {
        val pageList = (0 until pages).map { p ->
            val elements = buildList {
                repeat(strokesPerPage) { s ->
                    add(
                        Stroke(
                            tool = Tool.PEN,
                            color = 0xFF1A237E.toInt(),
                            capStyle = "round",
                            points = (0 until pointsPerStroke).map { i ->
                                StrokePoint(10.0 + i, 20.0 + p + i * 0.5, 1.4)
                            },
                            uniformWidth = false,
                            id = "stk_${p}_$s",
                        ),
                    )
                }
                if (withAssets && p == 0) {
                    add(ImageElement(50.0, 50.0, 400.0, 350.0, pictureBytes, id = "img_bench"))
                }
            }
            val background = if (withAssets) {
                Background.Pdf(
                    filename = if (p == 0) "scan.pdf" else null,
                    pageNo = p,
                    domain = if (p == 0) "absolute" else null,
                )
            } else {
                Background.Solid(0xFFFFFFFF.toInt(), "plain")
            }
            Page(width = 595.0, height = 842.0, background = background, layers = listOf(Layer(elements)), id = "pg_$p")
        }
        return Document(title = "benchmark", pages = pageList, documentId = "doc_benchmark")
    }

    private class Measured(val bytes: Long, val writeMs: Long, val readMs: Long)

    private inline fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    /** One picture file on disk, created once so repeated measures reuse it. */
    private val pictureFile: File by lazy { temp.newFile("scan.png").apply { writeBytes(pictureBytes) } }

    private fun pagesIn(file: File, format: SaveFormat): Int = when (format) {
        SaveFormat.ORIGINAL -> Xopp.open(file.inputStream()).pages.size
        SaveFormat.ZIPPED -> XoppZip.open(
            file.inputStream(),
            pdfFile = { temp.newFile() },
            imageFile = { temp.newFile() },
        ).doc.pages.size
        SaveFormat.MOBIX -> MobixDocument.open(MobixContainer.open(file.inputStream())).toDocument().pages.size
    }

    private fun measure(io: DocumentIo, doc: Document, pdf: File?, assets: Boolean, format: SaveFormat): Measured {
        val images = if (assets) mapOf("scan.png" to pictureFile) else emptyMap()
        val writeTimes = ArrayList<Long>(iterations)
        var file: File? = null
        repeat(iterations) {
            writeTimes += timed { file = io.encode(doc, pdf, format, target = null, images = images).file }
        }
        val out = file!!
        val readTimes = ArrayList<Long>(iterations)
        repeat(iterations) {
            readTimes += timed { pagesIn(out, format) }
        }
        return Measured(out.length(), median(writeTimes), median(readTimes))
    }

    private fun row(label: String, format: SaveFormat, m: Measured): String {
        val selfContained = format != SaveFormat.ORIGINAL
        return label.padEnd(15) +
            format.name.padEnd(10) +
            m.bytes.toString().padStart(13) +
            m.writeMs.toString().padStart(10) +
            m.readMs.toString().padStart(10) +
            "    " + (if (selfContained) "yes" else "no (PDF linked)")
    }

    @Test
    fun reportMobixAgainstXoppOnOneDocument() {
        val io = newIo()
        val inkOnly = sampleDocument(withAssets = false)
        val withAssets = sampleDocument(withAssets = true)
        val pdfFile = temp.newFile("scan.pdf").apply { writeBytes(pdfBytes) }

        val formats = listOf(SaveFormat.MOBIX, SaveFormat.ORIGINAL, SaveFormat.ZIPPED)
        val inkRows = formats.map { it to measure(io, inkOnly, null, assets = false, format = it) }
        val assetRows = formats.map { it to measure(io, withAssets, pdfFile, assets = true, format = it) }

        val report = buildString {
            appendLine("MobiXournal .mobix vs .xopp — size and time on one document")
            appendLine("generated by com.mobixournal.format.MobixXoppSizeBenchmarkTest @ 2026-10-14")
            appendLine(
                "document: $pages pages, ${strokesPerPage * pages} strokes, " +
                    "picture ${pictureBytes.size} B, PDF ${pdfBytes.size} B (both incompressible, seeded)",
            )
            appendLine("times: median of $iterations runs, ms, wall clock on the harness host")
            appendLine()
            appendLine("case           format    bytes on disk   write ms  read ms    self-contained")
            for ((format, m) in inkRows) appendLine(row("ink-only", format, m))
            for ((format, m) in assetRows) appendLine(row("picture+PDF", format, m))
        }

        println(report)
        val out = File("build/reports/mobix-vs-xopp.txt")
        out.parentFile?.mkdirs()
        out.writeText(report)

        assertTrue("the report must name real numbers", report.contains("bytes on disk"))
        assertEquals(
            "MOBIX read-back loses pages",
            pages,
            pagesIn(
                io.encode(withAssets, pdfFile, SaveFormat.MOBIX, target = null, images = mapOf("scan.png" to pictureFile)).file,
                SaveFormat.MOBIX,
            ),
        )
    }
}
