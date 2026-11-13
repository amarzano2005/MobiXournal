package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Layer
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.rectRegion
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The pins of a **selected figure**, as the window actually paints them.
 *
 * The rule is `FigurePins.selectionMarks`' (`FigurePinsTest`), and this file answers the other half — that the
 * sheet really draws it: with a wire on the page, a selected rectangle marks the wire's ends as the pins it
 * can be paired with; on a page that holds nothing to join, the sheet carries no mark at all.
 *
 * Pixels, not the model: the marks are chrome painted above the page, so a page that is right and a sheet
 * that stays blank are indistinguishable from the person drawing. The accent is the theme's own colour, so
 * the probe is the same token the overlay is painted with.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSelectionPinsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 595
    private val height = 700

    /** A rectangle minted the way the Rectangle slot mints it: a figure, so it carries pins. */
    private fun figure() = shapeStroke(
        ShapeBuilder.build(ShapeKind.RECTANGLE, 100.0, 100.0, 220.0, 180.0, 2.0),
        0xFF000000.toInt(),
        2f,
    )!!

    /** A line minted the way the Line slot mints it — the one element a figure can be paired with. */
    private fun wire() = shapeStroke(
        ShapeBuilder.build(ShapeKind.LINE, 320.0, 450.0, 440.0, 450.0, 2.0),
        0xFF000000.toInt(),
        2f,
    )!!

    /** A tab shown at 1:1 with the rectangle selected, and the wire present only when asked for. */
    private fun tab(withWire: Boolean): DesktopTab {
        val document = DesktopDocuments.blank(File(temp.root, if (withWire) "pins.xopp" else "bare.xopp"))
        val elements = listOfNotNull(figure(), wire().takeIf { withWire })
        document.editPage(0) { page -> page.copy(layers = listOf(Layer(elements))) }
        val tab = DesktopTabs().open(document)
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.selection = PageSelection(
            region = rectRegion(98.0, 98.0, 222.0, 182.0),
            refs = setOf(ElementRef(layerIndex = 0, elementIndex = 0)),
        )
        return tab
    }

    private fun frame(tab: DesktopTab): IntArray {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = DesktopTool.MARQUEE, onViewport = {}, onFit = {})
        }
        val png = try {
            Snapshot.sendApplyNotifications()
            scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
        } finally {
            scene.close()
        }
        val image = ImageIO.read(ByteArrayInputStream(png))
        return IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }
    }

    /** True when [rgb] is the accent the pin marks are painted with, allowing for antialiasing. */
    private fun isAccent(rgb: Int): Boolean {
        val accent = DesktopTheme.accent.toArgb()
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return abs(r - ((accent shr 16) and 0xFF)) <= 40 &&
            abs(g - ((accent shr 8) and 0xFF)) <= 40 &&
            abs(b - (accent and 0xFF)) <= 48
    }

    private fun accentNear(pixels: IntArray, x: Int, y: Int): Boolean {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val px = x + dx
                val py = y + dy
                if (px !in 0 until width || py !in 0 until height) continue
                if (isAccent(pixels[py * width + px])) return true
            }
        }
        return false
    }

    @Test
    fun `a selected figure marks the wire it can be paired with`() {
        val tab = tab(withWire = true)
        try {
            val pixels = frame(tab)
            assertTrue("the wire's left end is marked", accentNear(pixels, 320, 450))
            assertTrue("and its right end", accentNear(pixels, 440, 450))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a selected figure with nothing to join marks no pin`() {
        val tab = tab(withWire = false)
        try {
            val pixels = frame(tab)
            // The page's right half is empty paper: not one mark may be painted there.
            val stray = (400..690).sumOf { y -> (300..560).count { x -> isAccent(pixels[y * width + x]) } }
            assertFalse("a figure alone marks nothing ($stray px)", stray > 0)
        } finally {
            tab.document.close()
        }
    }
}
