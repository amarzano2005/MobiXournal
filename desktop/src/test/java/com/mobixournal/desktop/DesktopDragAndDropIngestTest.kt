package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopDropTarget
import com.mobixournal.desktop.io.DroppedFileKind
import com.mobixournal.desktop.shell.DesktopShellState

import com.mobixournal.format.model.ImageElement
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Headless unit tests for desktop drag-and-drop file classification and routing.
 */
class DesktopDragAndDropIngestTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun samplePng(): ByteArray {
        val img = BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun samplePdf(): File {
        val file = File(temp.root, "sample.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(file)
        }
        return file
    }

    @Test
    fun classifyCorrectlyIdentifiesSupportedFileFamilies() {
        assertEquals(DroppedFileKind.IMAGE, DesktopDropTarget.classify(File("test.png")))
        assertEquals(DroppedFileKind.IMAGE, DesktopDropTarget.classify(File("photo.JPG")))
        assertEquals(DroppedFileKind.IMAGE, DesktopDropTarget.classify(File("art.webp")))
        assertEquals(DroppedFileKind.IMAGE, DesktopDropTarget.classify(File("anim.GIF")))

        assertEquals(DroppedFileKind.PDF, DesktopDropTarget.classify(File("document.pdf")))
        assertEquals(DroppedFileKind.PDF, DesktopDropTarget.classify(File("report.PDF")))

        assertEquals(DroppedFileKind.DOCUMENT, DesktopDropTarget.classify(File("notes.mobix")))
        assertEquals(DroppedFileKind.DOCUMENT, DesktopDropTarget.classify(File("notes.xopp")))

        assertEquals(DroppedFileKind.UNSUPPORTED, DesktopDropTarget.classify(File("archive.zip")))
        assertEquals(DroppedFileKind.UNSUPPORTED, DesktopDropTarget.classify(File("text.txt")))
        assertEquals(DroppedFileKind.UNSUPPORTED, DesktopDropTarget.classify(File("data.bin")))
    }

    @Test
    fun droppedImagePlacesImageElementOnActivePage() {
        val state = DesktopShellState()
        state.newDocument()
        val tab = state.tabs.active!!
        assertEquals(0, tab.document.page(0).layers[0].elements.size)

        val imageFile = File(temp.root, "sample.png").apply { writeBytes(samplePng()) }
        DesktopDropTarget.routeDroppedFiles(state, listOf(imageFile))

        val elements = tab.document.page(0).layers[0].elements
        assertEquals(1, elements.size)
        assertTrue("Dropped image must become an ImageElement", elements[0] is ImageElement)
    }

    @Test
    fun droppedPdfTriggersImportPdfDialogRequest() {
        val state = DesktopShellState()
        state.newDocument()
        val pdfFile = samplePdf()

        DesktopDropTarget.routeDroppedFiles(state, listOf(pdfFile))
        assertNotNull("Dropping PDF must trigger importRequest", state.importRequest)
        assertEquals(pdfFile, state.importRequest!!.file)
    }

    @Test
    fun droppedUnsupportedFileReportsOpenFailure() {
        val state = DesktopShellState()
        state.newDocument()
        val unsupportedFile = File(temp.root, "data.unknown").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        DesktopDropTarget.routeDroppedFiles(state, listOf(unsupportedFile))
        assertNotNull("Dropping unsupported file must report failure", state.failure)
        assertEquals("Unsupported file type", state.failure!!.title)
        assertTrue("Failure message mentions file name", state.failure!!.detail.contains("data.unknown"))
    }
}
