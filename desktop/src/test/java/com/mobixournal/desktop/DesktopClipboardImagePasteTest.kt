package com.mobixournal.desktop

import com.mobixournal.format.model.ImageElement
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for clipboard image transfer and paste as [ImageElement].
 */
class DesktopClipboardImagePasteTest {

    private class ImageTransferable(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any {
            if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
            return image
        }
    }

    private fun sampleImage(width: Int = 120, height: Int = 60): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.MAGENTA
        g.fillRect(0, 0, width, height)
        g.dispose()
        return img
    }

    private fun newTab(): Pair<DesktopShellState, DesktopTab> {
        val state = DesktopShellState()
        state.newDocument()
        val tab = requireNotNull(state.tabs.active) { "newDocument must open an active tab" }
        return state to tab
    }

    @Test
    fun imageToPngBytesProducesValidPng() {
        val img = sampleImage(50, 50)
        val bytes = DesktopImagePlacement.imageToPngBytes(img)
        assertNotNull("PNG bytes must be produced", bytes)
        assertTrue("PNG header magic", bytes!!.size > 8)
        // PNG header: 0x89 'P' 'N' 'G'
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    @Test
    fun pasteSystemImageInsertsImageElementWithUndo() {
        val (state, tab) = newTab()
        val customClipboard = Clipboard("test-clipboard")
        val image = sampleImage(160, 80) // 2:1 aspect ratio
        customClipboard.setContents(ImageTransferable(image), null)

        val success = DesktopImagePlacement.pasteSystemImage(tab, customClipboard)
        assertTrue("Paste system image must succeed", success)

        val elements = tab.document.page(0).layers[0].elements
        assertEquals(1, elements.size)
        val imageElement = elements[0] as ImageElement

        val w = imageElement.right - imageElement.left
        val h = imageElement.bottom - imageElement.top
        assertEquals(2.0, w / h, 0.01)

        // Selected
        assertNotNull(tab.selection)
        assertEquals(1, tab.selection!!.refs.size)

        // Undo step recorded
        assertTrue(tab.canUndo)
        assertTrue(tab.undo())
        assertEquals(0, tab.document.page(0).layers[0].elements.size)
    }

    @Test
    fun pasteClipboardDispatchesSystemImageWhenInternalClipboardEmpty() {
        val (state, tab) = newTab()
        val internalClipboard = DesktopClipboard()
        assertTrue(internalClipboard.isEmpty)

        val customClipboard = Clipboard("test-clipboard")
        customClipboard.setContents(ImageTransferable(sampleImage(80, 80)), null)

        val handled = pasteClipboard(tab, internalClipboard, systemClipboard = customClipboard)
        assertTrue("pasteClipboard should ingest system image", handled)

        val elements = tab.document.page(0).layers[0].elements
        assertEquals(1, elements.size)
        assertTrue(elements[0] is ImageElement)
    }

    @Test
    fun pasteClipboardReturnsFalseWhenNoClipboardContent() {
        val (state, tab) = newTab()
        val internalClipboard = DesktopClipboard()
        val emptyClipboard = Clipboard("empty-clipboard")

        val handled = pasteClipboard(tab, internalClipboard, systemClipboard = emptyClipboard)
        assertFalse("pasteClipboard must return false when nothing to paste", handled)
    }
}
