package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.mobixournal.design.DesignTokens
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.render.GuideKind
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The instrument and its readout, **as pixels**.
 *
 * `DesktopGuidesTest` proves the geometry and `DesktopGuideSceneTest` the gesture; this file proves the
 * window *paints* them: the setsquare's outline is the shared token's amber over the sheet, the compass's
 * circle goes through the same path, the readout pill is the floating glass rather than the paper, and the
 * overlay writes nothing — a guide is chrome, and the page it lies on still holds no element.
 *
 * The renders are written to `build/desktop-render/` so the claim can be looked at as well as checked.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopGuideRenderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(): File = temp.newFile("guide-render.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    /** A tab with [kind] standing at (200, 200) on a blank page, shown at 1:1. */
    private fun tab(kind: GuideKind): DesktopTab {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.guide = placedGuideOn(kind, page = 0, centre = 200.0 to 200.0)
        return tab
    }

    /** The page as the window paints it, written to `build/desktop-render/[name].png`. */
    private fun render(name: String, tab: DesktopTab): BufferedImage {
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.PEN,
                ink = DesktopInk(width = 4f),
                onViewport = {},
                onFit = {},
            )
        }
        val png = try {
            val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(bytes)
            bytes
        } finally {
            scene.close()
        }
        return ImageIO.read(ByteArrayInputStream(png))
    }

    /** True when [rgb] is the guide's amber, allowing for the antialiased edge of a 2 px stroke. */
    private fun isGuide(rgb: Int): Boolean {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val amber = DesignTokens.GUIDE_RGB
        return abs(r - ((amber shr 16) and 0xFF)) <= 40 &&
            abs(g - ((amber shr 8) and 0xFF)) <= 40 &&
            abs(b - (amber and 0xFF)) <= 48
    }

    /** How many pixels in the band [ys] × [xs] are the guide's amber. */
    private fun amberIn(image: BufferedImage, xs: IntRange, ys: IntRange): Int =
        ys.sumOf { y -> xs.count { x -> isGuide(image.getRGB(x, y)) } }

    @Test
    fun `a placed setsquare is painted in the guide's amber, and the page holds no element`() {
        val tab = tab(GuideKind.SETSQUARE)
        try {
            val elementsBefore = tab.document.page(0).layers.sumOf { it.elements.size }
            val image = render("guide", tab)
            // The long leg runs along y = 245 from (110, 245) to the tip at (290, 245): a solid amber run.
            val leg = amberIn(image, xs = 140..260, ys = 244..246)
            assertTrue("the long leg's outline is the token's amber ($leg px)", leg >= 40)
            // The right-angle corner's short leg runs down x = 110, so the triangle is a closed outline.
            val shortLeg = amberIn(image, xs = 109..111, ys = 260..330)
            assertTrue("the short leg is drawn too ($shortLeg px)", shortLeg >= 20)
            assertEquals(
                "the overlay is chrome: the page is still exactly what the file held",
                elementsBefore,
                tab.document.page(0).layers.sumOf { it.elements.size },
            )
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a placed compass draws its circumference and its hub`() {
        val tab = tab(GuideKind.COMPASS)
        try {
            val image = render("guide-compass", tab)
            // The circle stands at (200, 200) opened to 100 pt: its leftmost point is (100, 200).
            val left = amberIn(image, xs = 97..103, ys = 190..210)
            val right = amberIn(image, xs = 297..303, ys = 190..210)
            assertTrue("the circumference is amber on the left ($left px)", left >= 6)
            assertTrue("and on the right ($right px)", right >= 6)
            val hub = amberIn(image, xs = 197..203, ys = 197..203)
            assertTrue("the hub a hand grabs is filled ($hub px)", hub >= 20)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `the readout pill is the floating glass, and the snapped one reads in the accent`() {
        // Near the origin, so the pill beside the tip lands inside the small render.
        val pose = placedGuideOn(GuideKind.SETSQUARE, page = 0, centre = 0.0 to 0.0)!!
        val plain = renderPill("guide-aim", GuideAim(pose, snapped = false))
        val snapped = renderPill("guide-aim-snapped", GuideAim(pose, snapped = true))

        // The pill lies over white paper: a floating surface is dark, so the sheet cannot be all of it.
        val dark = countPixels(plain) { rgb -> ((rgb shr 16) and 0xFF) < 120 }
        assertTrue("the pill is glass over the sheet, not the sheet ($dark px)", dark >= 200)
        val accent = DesignTokens.ACCENT_DARK_RGB
        val blue = countPixels(snapped) { rgb ->
            val r = (rgb shr 16) and 0xFF
            val b = rgb and 0xFF
            abs(b - (accent and 0xFF)) <= 60 && b > r + 40
        }
        assertTrue("a snapped aim reads in the accent ($blue px)", blue >= 8)
    }

    /** A pill over a white sheet, with no page under it — the readout's own material, isolated. */
    private fun renderPill(name: String, aim: GuideAim): BufferedImage {
        val width = 260
        val height = 140
        val scene = ImageComposeScene(width, height, Density(1f)) {
            Box(Modifier.fillMaxSize().background(DesktopTheme.previewPaper)) {
                GuideAimPill(aim = aim, viewport = Viewport(zoom = 1f), zoom = 1f, density = 1f)
            }
        }
        val png = try {
            val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(bytes)
            bytes
        } finally {
            scene.close()
        }
        return ImageIO.read(ByteArrayInputStream(png))
    }

    private fun countPixels(image: BufferedImage, test: (Int) -> Boolean): Int =
        (0 until image.height).sumOf { y -> (0 until image.width).count { x -> test(image.getRGB(x, y) and 0xFFFFFF) } }
}
