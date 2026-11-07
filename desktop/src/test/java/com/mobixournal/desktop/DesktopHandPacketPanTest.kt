package com.mobixournal.desktop
import com.mobixournal.desktop.sheet.PEN_GAIN

import com.mobixournal.desktop.ink.PenPacket
import com.mobixournal.desktop.ink.PenPacketSource
import com.mobixournal.desktop.ink.PenPacketPan
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.panByDrag
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.format.mobix.SyntheticNotebook

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The **hand slot with the pen**, down to how the sheet is driven: the tablet's own packets, the way
 * `docs/desktop-pen-feel.md` says the hand is panned on this workstation.
 *
 * The point of these tests is that the pen's pan is asked of the *packets* path (`PenPacketPan`), not of
 * Compose's emulated mouse: a host whose driver reports the contact 400 times a second must not lose a
 * pan because the mouse copy was coalesced, and the pan has to combine with the Compose drag instead of
 * cancelling it (`panByDrag`'s `owned` gate).
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopHandPacketPanTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 1000
    private val viewH = 700

    private fun notebook(): File = temp.newFile("hand-packets.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /**
     * A page twice the window's size, so a pan has a real range: a page that fits is centred by the clamp,
     * which would pin the offsets and answer nothing about whether the pan ran.
     */
    private fun tab() = DesktopTabs().open(DocumentLoader.load(notebook())).also { tab ->
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 2f, offsetX = 0f, offsetY = 0f)
    }

    @Test
    fun `the pen's own packets pan the sheet in the hand slot`() {
        val tab = tab()
        val packets = FakePackets()
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.HAND,
                onViewport = { tab.viewport = it },
                onFit = {},
                packets = packets,
            )
        }
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            packets.send(400f, 400f, pressure = null)
            scene.render()
            packets.send(400f, 350f, pressure = null)
            scene.render()

            // The hand pans **further than the pen travels** by the sheet's one extra (*Scroll distance*'s
            // `penScrollGain`): 50 dp of pen travel is 50 × PEN_GAIN dp of sheet.
            assertEquals(
                "the packets' own travel must reach the sheet",
                -50f * PEN_GAIN,
                tab.viewport.offsetY,
                0.5f,
            )
            assertEquals("and not across it", 0f, tab.viewport.offsetX, 0.5f)

            packets.send(400f, 300f, pressure = null)
            scene.render()
            assertEquals(
                "each batch carries on from where the last left the sheet",
                -100f * PEN_GAIN,
                tab.viewport.offsetY,
                0.5f,
            )
        } finally {
            scene.close()
        }
    }

    /**
     * The two paths may not double-count: while the packets are panning this contact, the emulated mouse's
     * deltas are dropped (`owned`), so one inch of pen is one inch of sheet — not one and a half of it
     * twice over.
     */
    @Test
    fun `the emulated mouse does not pan the contact the packets already panned`() {
        val tab = tab()
        val packets = FakePackets()
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.HAND,
                onViewport = { tab.viewport = it },
                onFit = {},
                packets = packets,
            )
        }
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            // The emulation reports the same contact: press here, packets for the travel.
            mouse(scene, PointerEventType.Press, 400f, 400f, pressed = true, at = 1_000L)
            packets.send(400f, 400f, pressure = null)
            scene.render()
            packets.send(400f, 350f, pressure = null)
            scene.render()
            // The mouse copy now moves too — it must not add its own travel on top of the packets'.
            mouse(scene, PointerEventType.Move, 400f, 350f, pressed = true, at = 1_016L)

            assertEquals(
                "one contact, one pan: the packets' travel only",
                -50f * PEN_GAIN,
                tab.viewport.offsetY,
                0.5f,
            )

            packets.send(400f, 350f, pressure = null, tip = false)
            scene.render()
            mouse(scene, PointerEventType.Release, 400f, 350f, pressed = false, at = 1_032L)
        } finally {
            scene.close()
        }
    }

    /** One emulated-mouse event at ([x], [y]) in window pixels. */
    private fun mouse(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressed: Boolean,
        at: Long,
    ) {
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
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    /** The driver's packet stream, with a canvas subscribed to it the way `TabletWintab` serves one. */
    private class FakePackets : PenPacketSource {

        private val listeners = mutableListOf<(PenPacket) -> Unit>()

        override fun listen(listener: (PenPacket) -> Unit): () -> Unit {
            listeners.add(listener)
            return { listeners.remove(listener) }
        }

        fun send(x: Float, y: Float, pressure: Float?, tip: Boolean = true) {
            val packet = PenPacket(
                x = x,
                y = y,
                pressure = pressure,
                tiltRad = 0.4f,
                azimuthRad = 1.2f,
                rotationRad = 0f,
                tip = tip,
                eraser = false,
                atNanos = System.nanoTime(),
            )
            listeners.toList().forEach { it(packet) }
        }
    }
}
