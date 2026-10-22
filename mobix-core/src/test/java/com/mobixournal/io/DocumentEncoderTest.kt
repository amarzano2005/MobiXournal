package com.mobixournal.io

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * The **encoder without Android**: the same two-page document written in each of the three containers
 * through [DocumentEncoder], each one read back with the core codec that owns it.
 *
 * This is what the desktop window needs and what the app's own suite could never prove — `DocumentIo`
 * reaches its writers only through a SAF staging file — so the three arms are checked here on a plain
 * `File`, which is exactly the destination the PC writes.
 */
class DocumentEncoderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun stroke(x: Double) = Stroke(
        points = listOf(StrokePoint(x, x, 1.0), StrokePoint(x + 10.0, x + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun document(): Document = Document(
        creator = "MobiXournal test",
        title = "Encoder notebook",
        pages = (0 until 2).map { index ->
            Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(stroke(index * 5.0)))),
            )
        },
    )

    private fun target(name: String): File = temp.newFile(name)

    @Test
    fun writesAGzipXoppAndReadsItBack() {
        val file = target("encoded.xopp")
        val uncompiled = FileOutputStream(file).use { out ->
            DocumentEncoder.write(
                document = document(),
                format = SaveFormat.ORIGINAL,
                target = DocumentEncoder.FileSaveTarget(file),
                out = out,
                appVersion = "1.2.3",
            )
        }
        assertTrue("an ink-only document has nothing to report", uncompiled.isEmpty())

        val back = file.inputStream().buffered().use { Xopp.open(it) }
        assertEquals(2, back.pages.size)
        assertEquals(595.0, back.pages[0].width, 1e-6)
        val pages = back.pages.map { it.layers.single().elements.single() as Stroke }
        assertEquals(0.0, pages[0].points[0].x, 1e-6)
        assertEquals(5.0, pages[1].points[0].x, 1e-6)
    }

    @Test
    fun writesAZippedXoppAndReadsItBack() {
        val file = target("encoded-zipped.xopp")
        FileOutputStream(file).use { out ->
            DocumentEncoder.write(
                document = document(),
                format = SaveFormat.ZIPPED,
                target = DocumentEncoder.FileSaveTarget(file),
                out = out,
                appVersion = "1.2.3",
            )
        }

        val loaded = file.inputStream().buffered().use { XoppZip.open(it, { temp.newFile("bg.pdf") }) }
        assertEquals(2, loaded.doc.pages.size)
        assertEquals(2, loaded.doc.pages.sumOf { it.layers.single().elements.size })
    }

    @Test
    fun writesAMobixAndReadsItBackPageByPage() {
        val file = target("encoded.mobix")
        FileOutputStream(file).use { out ->
            DocumentEncoder.write(
                document = document(),
                format = SaveFormat.MOBIX,
                target = DocumentEncoder.FileSaveTarget(file),
                out = out,
                appVersion = "1.2.3",
            )
        }

        // The document is lazy: its pages come out of the archive as they are asked for, so the reader
        // is still open when the first page is read.
        MobixPagedReader(MobixEntrySource.fromFile(file)).use { reader ->
            val document = MobixDocument.openPaged(reader).toDocument()
            assertEquals("the manifest is the page count", 2, document.pages.size)
            assertEquals("Encoder notebook", document.title)
            assertNotNull("a native archive carries an identity", document.documentId)
            val first = document.pages[0].layers.single().elements.single() as Stroke
            assertEquals(0.0, first.points[0].x, 1e-6)
        }
    }

    @Test
    fun aRelativeReferenceIsWrittenForABackgroundBesideTheDocument() {
        // The one reference arithmetic the desktop needs: a PDF in the document's own folder becomes
        // relative, so the pair survives being copied or zipped.
        val folder = temp.newFolder("docs")
        val pdf = File(folder, "background.pdf").apply { writeBytes(ByteArray(4)) }
        val file = File(folder, "notebook.xopp")
        val withPdf = document().let { doc ->
            doc.copy(
                pages = doc.pages.map { page ->
                    page.copy(background = Background.Pdf(pdf.absoluteFile.invariantSeparatorsPath, pageNo = 1, domain = "absolute"))
                },
            )
        }

        FileOutputStream(file).use { out ->
            DocumentEncoder.write(
                document = withPdf,
                format = SaveFormat.ORIGINAL,
                target = DocumentEncoder.FileSaveTarget(file),
                out = out,
                appVersion = "1.2.3",
            )
        }

        val back = file.inputStream().buffered().use { Xopp.open(it) }
        val background = back.pages.first().background as Background.Pdf
        assertEquals("background.pdf", background.filename)
    }
}
