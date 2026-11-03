package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.HudUi
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.RadialHud

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The ring **over a page**, in a headless scene: it paints what it says it paints, it is anchored where the
 * nib is, and a click on a swatch commits it through the editor's own path and inks nothing.
 *
 * The state and its arithmetic are [DesktopRingTest]'s; this is the half only a scene can prove — that the
 * ring is drawn **above** the sheet in the canvas's own pixels (its glass, its rim, a swatch's colour), and
 * that a press while it is up reaches the ring instead of starting a stroke underneath it.
 *
 * The scene is the real `PageCanvas` with the window's own wiring, so the nib publish the canvas makes and
 * the [`HudUi`] door the workspace hands it are exercised as the window uses them.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopRingRenderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 520
    private val height = 720

    private fun notebook(): File = temp.newFile("ring.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /** A window state with a pinned page — so pointer = page point, and the sheet covers the canvas. */
    private fun state(): DesktopShellState {
        val state = DesktopShellState(DesktopSettings(File(temp.root, "ring.properties")))
        state.open(notebook())
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        // The ring must *change* the ink: the pen starts on something that is not the palette's first swatch.
        state.pickColour(0xFF00FF00.toInt())
        return state
    }

    private fun scene(state: DesktopShellState): ImageComposeScene {
        val tab = state.tabs.active!!
        return ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = { tab.viewport = it },
                onFit = { tab.fit = it },
                ink = state.pageInk,
                // The door the workspace hands a canvas, as the window hands it.
                hud = HudUi(
                    open = { state.ring.open },
                    onPress = { x, y, density -> state.ring.press(x, y, density) },
                    slots = state.ring.slots,
                    onPointerAt = { at, density -> state.ring.notePointer(at, density) },
                    pressHeld = { state.ring.pressHeld },
                    onRelease = { state.ring.release() },
                ),
            )
        }
    }

    /** The rendered frame, saved beside the other desktop renders and read back as pixels. */
    private fun pixels(scene: ImageComposeScene, name: String): IntArray {
        Snapshot.sendApplyNotifications()
        val png = scene.render().encodeToData()!!.bytes
        File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        val image = ImageIO.read(ByteArrayInputStream(png))
        return IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }
    }

    private fun IntArray.at(x: Float, y: Float): Int = this[y.toInt() * width + x.toInt()]

    private fun Int.rgb(): Int = this and 0xFFFFFF

    private fun click(scene: ImageComposeScene, x: Float, y: Float, time: Long) {
        for ((type, pressed) in listOf(
            PointerEventType.Press to true,
            PointerEventType.Release to false,
        )) {
            scene.sendPointerEvent(
                eventType = type,
                pointers = listOf(
                    ComposeScenePointer(
                        id = PointerId(1),
                        position = Offset(x, y),
                        pressed = pressed,
                        type = PointerType.Mouse,
                    ),
                ),
                buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                timeMillis = time,
            )
            Snapshot.sendApplyNotifications()
            scene.render()
        }
    }

    private fun strokes(document: DesktopDocument) =
        document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    /**
     * Nothing on the page until it is summoned, and then the ring: the glass over the sheet, a rim round it,
     * the palette's first swatch where the geometry puts it, and the nib dot at the anchor.
     */
    @Test
    fun `the ring is painted over the page at the nib and nowhere before that`() {
        val state = state()
        val scene = scene(state)
        try {
            // The canvas publishes where the nib is: with no hover yet it is the centre of the canvas.
            val closed = pixels(scene, "ring-closed")
            val anchor = state.ring.anchor
            assertNotNull("the canvas reports the nib's place even before a pointer arrives", anchor)
            assertEquals("the canvas's centre, in its own pixels", width / 2f, anchor!!.x, 1f)
            assertEquals(height / 2f, anchor.y, 1f)
            // Where the ring's first slot would be: the paper, and nothing else, while the ring is shut.
            val count = state.ring.slots.size
            val (sx, sy) = RadialHud.slotCenter(anchor.x, anchor.y, 0, count, RadialHud.RING_RADIUS_DP)
            assertEquals(
                "nothing floats over the page while the ring is shut",
                0xFFFFFF,
                closed.at(sx, sy).rgb(),
            )

            assertTrue(state.apply(ShellCommand.RADIAL_HUD))
            val open = pixels(scene, "ring-at-the-nib")
            val ring = state.ring.open!!
            assertEquals("anchored at the nib", anchor.x, ring.cx, 1e-4f)
            assertEquals(
                "the first slot is the palette's first swatch, on its own colour",
                PenPalette.COLORS.first().rgb(),
                open.at(sx, sy).rgb(),
            )
            // The nib marker: a 3 dp dot of chrome ink on the ring's glass, which is what anchors it at the
            // pen rather than parking it somewhere on the screen.
            assertEquals("the nib dot is drawn at the anchor", 0xFFFFFF, open.at(ring.cx, ring.cy).rgb())
        } finally {
            scene.close()
            state.tabs.active?.document?.close()
        }
    }

    /**
     * A click on a swatch is the ring's, not the page's: the ink takes that colour, the ring closes, and the
     * sheet keeps every stroke it had.
     */
    @Test
    fun `a click on a swatch commits it and writes nothing`() {
        val state = state()
        val scene = scene(state)
        try {
            pixels(scene, "ring-before-the-click")
            assertTrue(state.apply(ShellCommand.RADIAL_HUD))
            val before = strokes(state.tabs.active!!.document).size
            val ring = state.ring.open!!
            val (sx, sy) = RadialHud.slotCenter(
                ring.cx, ring.cy, 0, state.ring.slots.size, RadialHud.RING_RADIUS_DP,
            )

            click(scene, sx, sy, time = 1_000L)
            pixels(scene, "ring-after-the-click")

            assertEquals("the ink took the swatch", PenPalette.COLORS.first(), state.ink.colour)
            assertNull("and the ring went away", state.ring.open)
            assertEquals("the press never reached the sheet", before, strokes(state.tabs.active!!.document).size)
        } finally {
            scene.close()
            state.tabs.active?.document?.close()
        }
    }

    /**
     * The **contact** the ring answered is the ring's until it lifts.
     *
     * The pen's own packets commit on the frame thread and close the surface, and Windows' mouse copy of that
     * same press arrives a frame later with nothing on screen. Read as a page press it inked the sheet under the
     * ring that had just gone — the complaint this test pins. The copy is swallowed instead, and the contact it
     * belonged to is over, so the next press is the page's again.
     */
    @Test
    fun `the mouse copy of a press the ring answered writes nothing`() {
        val state = state()
        val scene = scene(state)
        try {
            pixels(scene, "ring-race-before")
            assertTrue(state.apply(ShellCommand.RADIAL_HUD))
            val before = strokes(state.tabs.active!!.document).size
            val ring = state.ring.open!!
            val (sx, sy) = RadialHud.slotCenter(
                ring.cx, ring.cy, 0, state.ring.slots.size, RadialHud.RING_RADIUS_DP,
            )

            // The pen's own path answers the face first: the ring commits and is gone. Then Windows' copy of
            // the same press arrives, one frame later, on the same spot.
            assertTrue("the face took the press", state.ring.press(sx, sy, 1f))
            assertNull("the ring went away", state.ring.open)
            assertTrue("but the contact is still the ring's", state.ring.pressHeld)
            click(scene, sx, sy, time = 1_100L)

            assertEquals(
                "the copy inked nothing on the sheet beneath",
                before,
                strokes(state.tabs.active!!.document).size,
            )
            assertNull("and the next press has no ring to answer", state.ring.open)
            assertFalse("swallowing the copy ended the contact", state.ring.pressHeld)
        } finally {
            scene.close()
            state.tabs.active?.document?.close()
        }
    }

    /** A click well outside the ring is a deliberate "not now": it closes it and changes nothing. */
    @Test
    fun `a click outside the ring puts it away without changing the ink`() {
        val state = state()
        val scene = scene(state)
        try {
            pixels(scene, "ring-outside")
            val ink = state.ink.colour
            assertTrue(state.apply(ShellCommand.RADIAL_HUD))
            val ring = state.ring.open!!

            click(scene, ring.cx + 220f, ring.cy, time = 2_000L)
            Snapshot.sendApplyNotifications()
            assertNull("the ring is gone", state.ring.open)
            assertEquals("and the pen is still where it was", ink, state.ink.colour)
        } finally {
            scene.close()
            state.tabs.active?.document?.close()
        }
    }
}
