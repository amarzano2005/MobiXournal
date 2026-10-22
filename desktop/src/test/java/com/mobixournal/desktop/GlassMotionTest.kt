package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glass' **movement**, measured rather than claimed.
 *
 * A panel that merely appears and a panel that grows in from 82 % while its alpha rises look the same in
 * a still frame (the settled one) and differ entirely over the first 300 ms. So this drives the real
 * animation through an [ImageComposeScene]'s frame clock — one frame every [FRAME_MS] — and reads the
 * pixels it paints at successive instants: at the start the panel is invisible, near the start it is
 * faint and still inside its 82 % box, and by the documented end it is the glass at full size. The
 * numbers are [DesignTokens.GlassMotion]'s, so a token that moves without the motion moving fails here.
 */
class GlassMotionTest {

    private val viewportWidth = 260
    private val viewportHeight = 200
    private val panelWidth = 200
    private val panelHeight = 120

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun thePanelGrowsInAndFadesInOnTheSharedSpring() {
        val scene = scene { Box(Modifier.size(panelWidth.dp, panelHeight.dp).animatedGlass()) }
        try {
            val centreX = viewportWidth / 2
            val centreY = viewportHeight / 2
            // Inside the panel's *final* box, well outside its 82 % opening box (which starts 10.8 px in):
            // only a panel that has grown can paint here.
            val grownY = centreY - panelHeight / 2 + 3

            val opening = frame(scene, 0)
            val early = advance(scene, to = 40)
            val settled = advance(scene, to = 700)
            val later = advance(scene, to = 900)

            assertEquals(
                "at t=0 the panel is not yet visible",
                0xFFFFFF,
                opening.getRGB(centreX, centreY) and 0xFFFFFF,
            )
            assertEquals(
                "and it has not grown into its final box yet",
                0xFFFFFF,
                opening.getRGB(centreX, grownY) and 0xFFFFFF,
            )

            val settledFill = composite(DesignTokens.GLASS_DARK_RGB, DesignTokens.FLOATING_PANEL_ALPHA, 0xFFFFFF)
            assertTrue(
                "40 ms in, the panel is still fading: centre ${Integer.toHexString(early.getRGB(centreX, centreY))} " +
                    "must be lighter than the settled ${Integer.toHexString(settledFill)}",
                luminance(early.getRGB(centreX, centreY)) > luminance(settledFill),
            )
            assertEquals(
                "and at 40 ms it is still inside its 82 % box, so the far edge is the sheet",
                0xFFFFFF,
                early.getRGB(centreX, grownY) and 0xFFFFFF,
            )

            val settledCentre = settled.getRGB(centreX, centreY) and 0xFFFFFF
            assertTrue(
                "by 700 ms the panel is the glass over the sheet: " +
                    "${Integer.toHexString(settledCentre)} vs ${Integer.toHexString(settledFill)}",
                channelDistance(settledCentre, settledFill) <= 2,
            )
            assertTrue(
                "and it has grown into the box it did not reach at 40 ms: " +
                    Integer.toHexString(settled.getRGB(centreX, grownY)),
                (settled.getRGB(centreX, grownY) and 0xFFFFFF) != 0xFFFFFF,
            )
            assertEquals(
                "the arrival is over and stays put",
                settled.getRGB(centreX, centreY),
                later.getRGB(centreX, centreY),
            )
        } finally {
            scene.close()
        }
    }

    /**
     * The exit is held, then played: gating a surface with [GlassPresence] keeps it composed while it
     * draws back, and lets it go only after the token's hold — the half `if (open) { … }` cannot have.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun theExitIsHeldAndThenReleased() {
        val visible = mutableStateOf(true)
        val scene = scene {
            GlassPresence(visible.value) { Box(Modifier.size(panelWidth.dp, panelHeight.dp).animatedGlass()) }
        }
        try {
            val centreX = viewportWidth / 2
            val centreY = viewportHeight / 2

            frame(scene, 0)
            val full = advance(scene, to = 700).getRGB(centreX, centreY)

            visible.value = false
            // Samples a third of the way into the 230 ms hold rather than 60 ms in: the first frame of
            // the fade can land on the same tone as the settled panel, which made this assertion read
            // a timing artefact instead of the property it is about (the exit is played, not snapped).
            val closing = advance(scene, to = 700L + DesignTokens.GlassMotion.FADE_OUT_MS * 2 / 3)

            assertTrue(
                "a third into the exit the panel is still composed, drawing back: " +
                    Integer.toHexString(closing.getRGB(centreX, centreY)),
                luminance(closing.getRGB(centreX, centreY)) > luminance(full),
            )

            // The hold is a `delay`, so it elapses on the wall clock, not on the scene's frame clock.
            Thread.sleep(DesignTokens.GlassMotion.EXIT_HOLD_MS + 80L)
            val gone = advance(scene, to = 900)
            assertEquals(
                "after the hold the surface is released",
                0xFFFFFF,
                gone.getRGB(centreX, centreY) and 0xFFFFFF,
            )
        } finally {
            scene.close()
        }
    }

    /** The spring has to be the **outer** modifier: an alpha layer inside the fill fades empty space. */
    @Composable
    private fun Modifier.animatedGlass(): Modifier = glassSpring().glassSurface()

    @OptIn(ExperimentalComposeUiApi::class)
    private fun scene(panel: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(viewportWidth, viewportHeight, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.align(Alignment.Center)) { panel() }
            }
        }

    private var now = 0L

    /** One frame at [millis] since the scene started, as pixels. */
    private fun frame(scene: ImageComposeScene, millis: Long): BufferedImage {
        now = millis
        val image = scene.render(millis * 1_000_000L)
        val bytes = requireNotNull(image.encodeToData()?.bytes) { "Skia produced no PNG" }
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

    /** Renders every frame up to [to], and answers the last one. */
    private fun advance(scene: ImageComposeScene, to: Long): BufferedImage {
        var last = frame(scene, now)
        while (now < to) {
            last = frame(scene, minOf(now + FRAME_MS, to))
        }
        return last
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun channelDistance(a: Int, b: Int): Int {
        fun channel(rgb: Int, shift: Int) = (rgb shr shift) and 0xFF
        return maxOf(
            kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)),
        )
    }

    private fun composite(rgb: Int, alpha: Float, over: Int): Int {
        fun channel(shift: Int): Int {
            val a = ((rgb shr shift) and 0xFF) * alpha
            val b = ((over shr shift) and 0xFF) * (1f - alpha)
            return (a + b).toInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
