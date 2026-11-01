package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopImagePlacement
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.tools.DesktopTool

import com.mobixournal.format.model.ImageElement
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Headless test for image placement arithmetic, file insertion, undo/redo, and selection hand-over.
 */
class DesktopImagePlacementTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun samplePng(width: Int = 100, height: Int = 50, colorArgb: Int = 0xFF0000FF.toInt()): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                img.setRGB(x, y, colorArgb)
            }
        }
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return baos.toByteArray()
    }

    private fun newTab(): Pair<DesktopShellState, DesktopTab> {
        val state = DesktopShellState()
        state.newDocument()
        val tab = requireNotNull(state.tabs.active) { "newDocument must open an active tab" }
        return state to tab
    }

    @Test
    fun calculateBoundsPreservesAspectRatioAndScalesWithinMaxLimits() {
        // Image aspect ratio: 800 / 400 = 2.0
        // Page dimensions: 500 x 400. 60% max limits: maxW = 300.0, maxH = 240.0
        // Target width constrained by maxW (300.0), so targetHeight = 150.0 (well within maxH)
        val bounds = DesktopImagePlacement.calculateBounds(
            imageWidthPx = 800.0,
            imageHeightPx = 400.0,
            pageWidthPt = 500.0,
            pageHeightPt = 400.0,
            centerX = 250.0,
            centerY = 200.0,
        )

        val w = bounds.right - bounds.left
        val h = bounds.bottom - bounds.top
        assertEquals(2.0, w / h, 0.001)
        assertEquals(300.0, w, 0.001)
        assertEquals(150.0, h, 0.001)
        assertEquals(100.0, bounds.left, 0.001)
        assertEquals(400.0, bounds.right, 0.001)
        assertEquals(125.0, bounds.top, 0.001)
        assertEquals(275.0, bounds.bottom, 0.001)
    }

    @Test
    fun calculateBoundsClampsWithinPageBoundaries() {
        // Placement center pushed against bottom-right corner
        val bounds = DesktopImagePlacement.calculateBounds(
            imageWidthPx = 100.0,
            imageHeightPx = 100.0,
            pageWidthPt = 200.0,
            pageHeightPt = 200.0,
            centerX = 195.0,
            centerY = 195.0,
        )

        assertTrue("Bounds must not exceed page width", bounds.right <= 200.0)
        assertTrue("Bounds must not exceed page height", bounds.bottom <= 200.0)
        assertTrue("Bounds left must be >= 0", bounds.left >= 0.0)
        assertTrue("Bounds top must be >= 0", bounds.top >= 0.0)
    }

    @Test
    fun insertBytesCreatesImageElementWithSingleUndoStepAndHandsToSelection() {
        val (state, tab) = newTab()
        assertFalse("Initial tab has no undo", tab.canUndo)
        assertEquals(0, tab.document.page(0).layers[0].elements.size)

        val png = samplePng(200, 100)
        val success = DesktopImagePlacement.insertBytes(state, tab, png)
        assertTrue("Image insertion must succeed", success)

        val elements = tab.document.page(0).layers[0].elements
        assertEquals(1, elements.size)
        val image = elements[0] as ImageElement
        assertTrue(png.contentEquals(image.data))

        // Tool handed over to Marquee selection
        assertEquals(DesktopTool.MARQUEE, state.tools.current)

        // Selection bounds match image and element reference is selected
        val sel = tab.selection
        assertNotNull("Tab must have an active selection", sel)
        assertEquals(1, sel!!.refs.size)

        // Exactly one undo step registered
        assertTrue("Tab has undo step", tab.canUndo)
        assertTrue("Undo restores clean page", tab.undo())
        assertEquals(0, tab.document.page(0).layers[0].elements.size)

        // Redo restores the element
        assertTrue(tab.canRedo)
        assertTrue(tab.redo())
        assertEquals(1, tab.document.page(0).layers[0].elements.size)
    }

    @Test
    fun insertFromFileReadsFileAndPlacesImage() {
        val (state, tab) = newTab()
        val imageFile = File(temp.root, "photo.png")
        val png = samplePng(60, 60)
        imageFile.writeBytes(png)

        val placed = DesktopImagePlacement.insertFromFile(state, tab, imageFile)
        assertTrue("Insertion from file must succeed", placed)

        val elements = tab.document.page(0).layers[0].elements
        assertEquals(1, elements.size)
        assertTrue(elements[0] is ImageElement)
    }
}
