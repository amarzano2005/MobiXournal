package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopImageCache
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.shell.renderPageBitmap

import androidx.compose.ui.graphics.toPixelMap
import com.mobixournal.format.Xopp
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for desktop [ImageElement] canvas rendering and format round-trip.
 *
 * Verifies that:
 * 1. [DesktopImageCache] decodes and caches bitmaps, handling invalid bytes gracefully.
 * 2. [renderPageBitmap] paints the image pixels onto the page canvas at the specified bounds.
 * 3. Documents containing [ImageElement] round-trip losslessly through both .xopp and .mobix.
 */
class DesktopImageRenderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun samplePng(width: Int = 10, height: Int = 10, colorArgb: Int = 0xFFFF0000.toInt()): ByteArray {
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

    @Test
    fun cacheDecodesAndReusesBitmapForIdenticalBytes() {
        val png = samplePng(8, 8)
        val elem1 = ImageElement(10.0, 10.0, 50.0, 50.0, png)
        val elem2 = ImageElement(20.0, 20.0, 60.0, 60.0, png) // same png reference, moved

        val bmp1 = DesktopImageCache.get(elem1)
        assertNotNull("Bitmap must be decoded", bmp1)
        val bmp2 = DesktopImageCache.get(elem2)
        assertSame("Bitmap must be reused from cache for same byte array reference", bmp1, bmp2)
    }

    @Test
    fun cacheReturnsNullForCorruptBytes() {
        val corrupt = ImageElement(0.0, 0.0, 10.0, 10.0, byteArrayOf(1, 2, 3, 4))
        val bmp = DesktopImageCache.get(corrupt)
        assertNull("Corrupt image bytes must return null without throwing", bmp)
    }

    @Test
    fun renderPageBitmapPaintsImageElementAtBounds() {
        val png = samplePng(10, 10, 0xFFFF0000.toInt())
        val element = ImageElement(20.0, 20.0, 40.0, 40.0, png)
        val page = Page(
            width = 100.0,
            height = 100.0,
            background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
            layers = listOf(Layer(elements = listOf(element))),
        )

        val bitmap = renderPageBitmap(page, scale = 1f)
        val pixels = bitmap.toPixelMap()

        // Point inside the image bounds (x=30, y=30) should be red
        val insideColor = pixels[30, 30]
        assertEquals(1f, insideColor.red, 0.05f)
        assertEquals(0f, insideColor.green, 0.05f)
        assertEquals(0f, insideColor.blue, 0.05f)

        // Point outside the image bounds (x=5, y=5) should be white paper
        val outsideColor = pixels[5, 5]
        assertEquals(1f, outsideColor.red, 0.05f)
        assertEquals(1f, outsideColor.green, 0.05f)
        assertEquals(1f, outsideColor.blue, 0.05f)
    }

    @Test
    fun imageElementRoundTripsThroughXopp() {
        val png = samplePng(4, 4)
        val element = ImageElement(15.0, 25.0, 75.0, 85.0, png)
        val doc = Document(
            pages = listOf(
                Page(
                    width = 200.0,
                    height = 200.0,
                    background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
                    layers = listOf(Layer(elements = listOf(element))),
                ),
            ),
        )

        val baos = ByteArrayOutputStream()
        Xopp.save(doc, baos)
        val reopened = Xopp.open(ByteArrayInputStream(baos.toByteArray()))

        val reopenedElements = reopened.pages[0].layers[0].elements
        assertEquals(1, reopenedElements.size)
        val reopenedImage = reopenedElements[0] as ImageElement
        assertEquals(element.left, reopenedImage.left, 0.001)
        assertEquals(element.top, reopenedImage.top, 0.001)
        assertEquals(element.right, reopenedImage.right, 0.001)
        assertEquals(element.bottom, reopenedImage.bottom, 0.001)
        assertTrue(png.contentEquals(reopenedImage.data))
    }

    @Test
    fun imageElementRoundTripsThroughMobix() {
        val png = samplePng(4, 4)
        val element = ImageElement(15.0, 25.0, 75.0, 85.0, png)
        val doc = Document(
            pages = listOf(
                Page(
                    width = 200.0,
                    height = 200.0,
                    background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
                    layers = listOf(Layer(elements = listOf(element))),
                ),
            ),
        )

        val mobixFile = File(temp.root, "test.mobix")
        val now = System.currentTimeMillis()
        val mobixDoc = MobixDocument.of(doc, appVersion = "1.3.0", nowMillis = now)
        mobixFile.outputStream().use { mobixDoc.save(it, nowMillis = now) }

        val loaded = DocumentLoader.load(mobixFile)
        try {
            val reopenedElements = loaded.page(0).layers[0].elements
            assertEquals(1, reopenedElements.size)
            val reopenedImage = reopenedElements[0] as ImageElement
            assertEquals(element.left, reopenedImage.left, 0.001)
            assertEquals(element.top, reopenedImage.top, 0.001)
            assertEquals(element.right, reopenedImage.right, 0.001)
            assertEquals(element.bottom, reopenedImage.bottom, 0.001)
            assertTrue(png.contentEquals(reopenedImage.data))
        } finally {
            loaded.close()
        }
    }
}
