package com.mobixournal.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.LivingInk
import com.mobixournal.render.Uncompilable
import java.awt.Color as AwtColor
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The preview draws what the document *is*, not just its strokes.
 *
 * Three claims are checked here, and each one is a class of thing that used to be silently blank:
 * a **living entity** (a table, a parametric shape) is painted through `LivingInk` — the same compiler
 * the Android canvas and every export use — a **pixmap background** shows the picture the page names,
 * and a **PDF background** is rasterised and placed under the ink. The pixel assertions are what make
 * this evidence rather than a call that returned without throwing.
 */
class LivingAndBackgroundTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun page(background: Background, elements: List<com.mobixournal.format.model.Element>) =
        Page(
            width = PDF_WIDTH_PT,
            height = PDF_HEIGHT_PT,
            background = background,
            layers = listOf(Layer(elements)),
        )

    private fun solid() = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain")

    /** A one-pixel-grid of ink on the page: a stroke from (10,10) to (90,90). */
    private fun ink(): Stroke = Stroke(
        points = listOf(StrokePoint(10.0, 10.0, 2.0), StrokePoint(90.0, 90.0, 2.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    @Test
    fun aLivingTableIsPaintedThroughTheSharedCompiler() {
        val table = TableElement(
            left = 20.0,
            top = 20.0,
            colWidths = listOf(60.0, 60.0),
            rowHeights = listOf(30.0, 30.0),
            cells = listOf(
                listOf(TableCell(text = "a"), TableCell(text = "b")),
                listOf(TableCell(text = "c"), TableCell(text = "d")),
            ),
            headerRow = true,
        )

        // The compiler the canvas and every export use produces ordinary ink — strokes and text — not a
        // picture, which is why the desktop can draw it without knowing what a table is.
        val compiled = LivingInk.of(table)
        assertTrue("a table compiles to ink", compiled.elements.isNotEmpty())
        assertTrue("and reports nothing it could not draw", compiled.uncompiled.isEmpty())
        assertTrue("the grid is drawn with strokes", compiled.elements.any { it is Stroke })

        val picture = render(page(solid(), listOf(table)))
        assertTrue("the table's grid was painted", darkPixels(picture) > 0)
    }

    @Test
    fun aParametricShapeIsPaintedThroughTheSharedCompiler() {
        val triangle = ParametricShapeElement(
            shape = "triangle",
            vertices = listOf(ShapeVertex(20.0, 100.0), ShapeVertex(120.0, 100.0), ShapeVertex(70.0, 30.0)),
            strokeColor = 0xFF000000.toInt(),
            strokeWidth = 1.5,
            showSideLengths = true,
        )

        val compiled = LivingInk.of(triangle)
        assertTrue("a shape compiles to ink", compiled.elements.isNotEmpty())

        val picture = render(page(solid(), listOf(triangle)))
        assertTrue("the shape's outline was painted", darkPixels(picture) > 0)
    }

    @Test
    fun anUncompilableEntityIsReportedRatherThanDropped() {
        // A shape's kind is an open name — an unknown one still draws its vertices — but a circuit
        // symbol is a closed vocabulary: a component this build has no drawing for has no ink at all,
        // and that has to be *said*, because a silently missing component looks like a deleted one.
        val unknown = CircuitElement(
            symbol = "flux-capacitor",
            positionX = 40.0,
            positionY = 40.0,
        )
        val ink = LivingInk.of(unknown)
        assertTrue("nothing to draw", ink.elements.isEmpty())
        assertEquals(listOf("circuit"), ink.uncompiled.map(Uncompilable::type))
    }

    @Test
    fun aPixmapBackgroundIsDrawnUnderTheInk() {
        val picture = solidPng(AwtColor(0x20, 0x60, 0xE0))
        val backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root)
        val page = page(Background.Pixmap(domain = "absolute", filename = picture.absolutePath), listOf(ink()))

        val pixels = render(page, backdrop)
        val image = ImageIO.read(pixels.file)
        // The sheet is the picture now: a pixel away from the stroke must be the picture's own blue.
        val sample = image.getRGB(4, 4) and 0xFFFFFF
        assertEquals("the pixmap's blue is the sheet", 0x2060E0, sample)
        assertTrue("and the ink is still on top", darkPixels(pixels) > 0)
    }

    @Test
    fun aBundledPictureWinsOverAReferenceThatNoLongerExists() {
        val bytes = pngBytes(AwtColor(0xE0, 0x30, 0x30))
        val backdrop = PageBackdrop(
            bundled = { name -> if (name == "assets/img_red.png") bytes else null },
            documentDir = temp.root,
        )
        val page = page(
            Background.Pixmap(
                domain = "absolute",
                filename = "/nowhere/moved-away.png",
                bundledAsset = "assets/img_red.png",
            ),
            emptyList(),
        )

        val image = ImageIO.read(render(page, backdrop).file)
        assertEquals("the bundled bytes are what was drawn", 0xE03030, image.getRGB(4, 4) and 0xFFFFFF)
    }

    @Test
    fun aPdfBackgroundIsRasterisedAndPlacedUnderTheInk() {
        val pdf = onePagePdf()
        val backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root)
        val page = page(Background.Pdf(filename = pdf.absolutePath, pageNo = 0, domain = "absolute"), listOf(ink()))

        val picture = render(page, backdrop)
        val image = ImageIO.read(picture.file)
        // The PDF's own page is a light-grey rectangle; the top-left corner is outside the ink diagonal.
        val sample = image.getRGB(4, 4) and 0xFFFFFF
        assertTrue("the PDF page's grey landed on the sheet (was ${sample.toString(16)})", sample in 0x808080..0xC8C8C8)
        assertTrue("and the ink is still on top", darkPixels(picture) > 0)
    }

    @Test
    fun aMissingBackgroundLeavesTheSheetInsteadOfFailing() {
        val backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root)
        val page = page(Background.Pdf(filename = "/nowhere/gone.pdf", pageNo = 0, domain = "absolute"), listOf(ink()))

        val image = ImageIO.read(render(page, backdrop).file)
        assertEquals("a page with no resolvable picture stays white paper", 0xFFFFFF, image.getRGB(4, 4) and 0xFFFFFF)
    }

    // --- rendering ------------------------------------------------------------------------------

    private class Rendered(val file: File, val width: Int, val height: Int)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(page: Page, backdrop: PageBackdrop? = null): Rendered {
        val width = page.width.toInt()
        val height = page.height.toInt()
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            PageView(page = page, scale = 1f, backdrop = backdrop)
        }
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        assertNotNull("Skia produced a PNG", png)
        val out = temp.newFile("render-${System.nanoTime()}.png")
        out.writeBytes(png!!)
        return Rendered(out, width, height)
    }

    /** Sampled dark pixels — the ink, not the paper. */
    private fun darkPixels(rendered: Rendered): Int {
        val image = ImageIO.read(rendered.file)
        var ink = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val rgb = image.getRGB(x, y) and 0xFFFFFF
                if (maxOf(rgb shr 16, (rgb shr 8) and 0xFF, rgb and 0xFF) < 0x60) ink++
                x += 2
            }
            y += 2
        }
        return ink
    }

    // --- fixtures -------------------------------------------------------------------------------

    private fun solidPng(color: AwtColor): File =
        temp.newFile("background.png").also { it.writeBytes(pngBytes(color)) }

    private fun pngBytes(color: AwtColor): ByteArray {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.dispose()
        val out = java.io.ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    /** One page of PDF, filled with light grey so the rasterised sheet is distinguishable from paper. */
    private fun onePagePdf(): File {
        val file = temp.newFile("background.pdf")
        PDDocument().use { pdf ->
            val pdfPage = PDPage(PDRectangle(PDF_WIDTH_PT.toFloat(), PDF_HEIGHT_PT.toFloat()))
            pdf.addPage(pdfPage)
            PDPageContentStream(pdf, pdfPage).use { content ->
                content.setNonStrokingColor(PDColor(floatArrayOf(0.6f, 0.6f, 0.6f), PDDeviceRGB.INSTANCE))
                content.addRect(0f, 0f, PDF_WIDTH_PT.toFloat(), PDF_HEIGHT_PT.toFloat())
                content.fill()
            }
            pdf.save(file)
        }
        return file
    }

    private companion object {
        /** A6-ish, small enough that a full-page render is cheap in a unit test. */
        const val PDF_WIDTH_PT = 298.0
        const val PDF_HEIGHT_PT = 420.0
    }
}
