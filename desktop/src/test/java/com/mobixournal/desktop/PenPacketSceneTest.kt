package com.mobixournal.desktop

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
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The tablet's packets and the mouse Windows emulates the same contact as, **in the same window**.
 *
 * This is the case neither `PenPacketStrokeTest` (packets alone) nor `DesktopPointerSceneTest` (a
 * pointer alone) can reach, and it is the one the owner could feel: the emulated mouse is a *copy* of the
 * contact, so its press, moves and release arrive interleaved with the driver's packets and it sees the
 * same one stroke twice. What must be true is that the stroke's **two ends are the packets'** — it is
 * born from a tip-down packet and committed by the lift packet — and that the Compose pointer, standing
 * aside, can neither add a second stroke nor take a sample away from the first one it is drawing.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PenPacketSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 500
    private val viewH = 700

    private fun notebook(): File = temp.newFile("packets-scene.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
    }

    private fun strokes(tab: DesktopTab) =
        tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    /**
     * The emulated mouse runs its whole gesture *around* the pen's: a press before the first packet (the
     * order Windows delivers them in), a move that crosses the tap slop mid-stroke (the move that used to
     * wipe the packet stroke's samples), and a release after the pen's lift. One stroke, the packets'.
     */
    @Test
    fun `the pen's stroke starts and ends on its packets, not on the emulated mouse`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val packets = FakePackets()
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = {},
                onFit = {},
                ink = DesktopInk(width = 4f),
                packets = packets,
            )
        }
        try {
            // Layout first: the canvas learns its own corner and the listener is registered at composition.
            scene.render()
            Snapshot.sendApplyNotifications()
            val before = strokes(tab).size
            assertEquals("the fixture's page holds one stroke", 1, before)

            // The emulation reports the contact first, as it does on this machine.
            mouse(scene, PointerEventType.Press, 120f, 120f, pressed = true, time = 1_000L)

            packets.send(120f, 120f, pressure = 0.05f)
            packets.send(160f, 150f, pressure = 0.4f)
            // The emulated mouse now moves past the tap slop — it believes it is drawing a stroke.
            mouse(scene, PointerEventType.Move, 200f, 180f, pressed = true, time = 1_020L)
            packets.send(240f, 200f, pressure = 0.9f)
            mouse(scene, PointerEventType.Move, 260f, 210f, pressed = true, time = 1_030L)
            packets.send(260f, 210f, pressure = 1f)
            // The pen lifts: its own packet ends the stroke, before the emulation's release.
            packets.send(260f, 210f, pressure = 0f, tip = false)
            mouse(scene, PointerEventType.Release, 260f, 210f, pressed = false, time = 1_040L)

            val drawn = strokes(tab)
            assertEquals("one contact is one stroke, not two", before + 1, drawn.size)
            val stroke = drawn.last()
            assertEquals(Tool.PEN, stroke.tool)
            assertEquals(
                "the packets' four samples, not the mouse's moves: " + stroke.points.map { it.x },
                4,
                stroke.points.size,
            )
            assertEquals("the stroke starts where the first packet was", 120.0, stroke.points.first().x, 0.5)
            // The last sample is the pen's own reading low-passed (`PenPacketStroke`'s position filter,
            // which is what removes the whole-pixel staircase), so it trails the final packet slightly.
            assertTrue(
                "and ends at the last packet, past the mouse's 260 px: ${stroke.points.last().x}",
                stroke.points.last().x > 230.0,
            )
            assertTrue(
                "a light tip-down still draws a line (${stroke.points.first().width} pt)",
                stroke.points.first().width > 0.0,
            )
            assertEquals("one stroke is one undo step", true, tab.canUndo)
            assertFalse("the pen is released when the lift packet says so", packets.owned)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /** One event as the mouse emulation sends it: a plain mouse pointer, in window pixels. */
    private fun mouse(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressed: Boolean,
        time: Long,
    ) {
        val pointers = listOf(
            ComposeScenePointer(
                id = PointerId(1),
                position = Offset(x, y),
                pressed = pressed,
                type = PointerType.Mouse,
            ),
        )
        val buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons()
        scene.sendPointerEvent(
            eventType = type,
            pointers = pointers,
            buttons = buttons,
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    /** The driver's packet stream, with a canvas subscribed to it the way `TabletWintab` serves one. */
    private class FakePackets : PenPacketSource {

        private val listeners = mutableListOf<(PenPacket) -> Unit>()

        /** True while a packet path holds the pen — the same hand-over `PageCanvas` acts on. */
        var owned = false
            private set

        override fun listen(listener: (PenPacket) -> Unit): () -> Unit {
            listeners.add(listener)
            return { listeners.remove(listener) }
        }

        /**
         * One packet, stamped with the clock **now** the way the driver's are: the canvas's ticker
         * compares the newest packet with that clock, so a hand-made timestamp in the far past would be
         * read as a silence and would end the stroke being drawn.
         */
        fun send(x: Float, y: Float, pressure: Float?, tip: Boolean = true) {
            val atNanos = System.nanoTime()
            val packet = PenPacket(
                x = x,
                y = y,
                pressure = pressure,
                tiltRad = 0.4f,
                azimuthRad = 1.2f,
                rotationRad = 0f,
                tip = tip,
                eraser = false,
                atNanos = atNanos,
            )
            owned = tip
            for (listener in listeners.toList()) listener(packet)
        }
    }
}
