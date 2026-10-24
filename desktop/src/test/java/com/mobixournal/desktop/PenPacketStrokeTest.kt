package com.mobixournal.desktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * A stroke drawn from the **tablet's packets**, end to end and headless.
 *
 * This is the half `DesktopPointerSceneTest` cannot reach: that suite pushes Compose pointer events,
 * which is the path Windows' mouse emulation produces, and the reason a light touch was sometimes lost.
 * Here the pen's packets are the source — a tip-down, a ramp of samples and a lift — and the assertions
 * are the ones that matter for the live complaint:
 *
 *  - the stroke **exists** even though no pointer press was ever delivered;
 *  - its widths are the shared filter's answer to the packet pressures, so a light start draws light
 *    instead of nothing;
 *  - it is **one** edit on the page, committed at the lift, with one undo step;
 *  - a packet outside the canvas, or a stroke whose packets stop arriving, leaves no ink behind.
 *
 * The gesture is the real [PageInkGesture] with the real router underneath, so what is proved is the
 * path the window runs, not a stand-in for it.
 */
class PenPacketStrokeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pageW = SyntheticNotebook.WIDTH_PT.toFloat()
    private val pageH = SyntheticNotebook.HEIGHT_PT.toFloat()

    private fun notebook(name: String): File = temp.newFile(name).also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
    }

    private fun strokes(tab: DesktopTab) =
        tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    /** A pen path over [tab] whose canvas starts at [origin] and is [size] pixels. */
    private fun path(
        tab: DesktopTab,
        draft: MutableList<InkSample> = mutableStateListOf(),
        origin: Offset = Offset.Zero,
        size: Size = Size(pageW, pageH),
        owned: MutableList<Boolean> = mutableListOf(),
        /** The ring positions the canvas would paint — `onHover`, per published frame. */
        hovered: MutableList<Offset?> = mutableListOf(),
        tool: DesktopTool = DesktopTool.PEN,
        ink: DesktopInk = DesktopInk(width = 4f),
    ): PenPacketStroke {
        val gesture = PageInkGesture(
            tab = tab,
            tool = tool,
            ink = ink,
            view = { Viewport(zoom = 1f) },
            pageW = pageW,
            pageH = pageH,
            density = 1f,
            draft = draft as androidx.compose.runtime.snapshots.SnapshotStateList<InkSample>,
            onEdited = {},
            // What `PageCanvas` passes for the packet path: a pen on paper starts at the press.
            startOnDown = true,
        )
        return PenPacketStroke(
            gesture = gesture,
            origin = { origin },
            size = { size },
            onOwnership = { owned.add(it) },
            onHover = { hovered.add(it) },
        )
    }

    private fun packet(
        x: Float,
        y: Float,
        pressure: Float?,
        tip: Boolean = true,
        eraser: Boolean = false,
        atNanos: Long = 1_000_000_000L,
    ) = PenPacket(
        x = x,
        y = y,
        pressure = pressure,
        tiltRad = 0.4f,
        azimuthRad = 1.2f,
        rotationRad = 0f,
        tip = tip,
        eraser = eraser,
        atNanos = atNanos,
    )

    /**
     * One packet **and the frame that consumes it**.
     *
     * `packet()` only enqueues — that is the whole point of the hand-off (`PenPacketStroke`): the driver's
     * thread must never touch the gesture or Compose's snapshot state, or a frame can block on it. So a
     * headless test has to do what the canvas's ticker does, in the same order, after every packet.
     */
    private fun PenPacketStroke.send(packet: PenPacket) {
        packet(packet)
        flush()
    }

    /**
     * The driver's thread is **not** allowed to draw: a packet without the frame that consumes it changes
     * nothing.
     *
     * This is the invariant that keeps the window responsive. Doing the gesture's work on the message
     * thread while the frame thread held a lock on the same object froze the window the moment a stroke
     * began; a queue makes the driver's thread wait for nobody, and the stroke is drawn one frame later
     * on the thread that owns the snapshot state.
     */
    @Test
    fun `a packet alone draws nothing until the frame consumes it`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("enqueue.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val before = strokes(tab).size
            val draft = mutableStateListOf<InkSample>()
            val hovered = mutableListOf<Offset?>()
            val path = path(tab, draft = draft, hovered = hovered)

            path.packet(packet(60f, 60f, pressure = 0.5f, atNanos = 1_000_000_000L))
            path.packet(packet(90f, 90f, pressure = 0.7f, atNanos = 1_002_000_000L))
            assertEquals("nothing is drawn off the frame thread", 0, draft.size)
            assertFalse("the stroke has not begun", path.drawing)
            assertEquals("nothing to commit yet", before, strokes(tab).size)

            path.flush()
            assertEquals("one frame consumes both packets", 2, draft.size)
            assertTrue("and the stroke is open", path.drawing)

            path.packet(packet(120f, 120f, pressure = 0f, tip = false, atNanos = 1_004_000_000L))
            path.flush()
            assertEquals("the lift commits one stroke", before + 1, strokes(tab).size)
            // The tip-down opens it and the move extends it; the lift ends it without adding a sample,
            // exactly as the stroke drawn on the tablet is made.
            assertEquals("with the two samples", 2, strokes(tab).last().points.size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a stroke is drawn from the packets alone and tapers with their pressure`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("packets.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val owned = mutableListOf<Boolean>()
            val path = path(tab, owned = owned)
            val before = strokes(tab).size

            // A light touch, ramping: no pointer press exists in this test at all.
            path.send(packet(20f, 20f, pressure = 0.02f, atNanos = 1_000_000_000L))
            path.send(packet(80f, 60f, pressure = 0.2f, atNanos = 1_008_000_000L))
            path.send(packet(160f, 120f, pressure = 0.6f, atNanos = 1_016_000_000L))
            // The pen resting at the end of the stroke: a real one sends a few hundred of these, and the
            // position filter is what the last few millimetres look like.
            repeat(8) { index ->
                path.send(packet(260f, 200f, pressure = 1f, atNanos = 1_024_000_000L + index * 2_000_000L))
            }
            assertTrue("the pen owns the stroke while packets arrive", owned.contains(true))

            path.send(packet(260f, 200f, pressure = 0f, tip = false, atNanos = 1_040_000_000L))

            val drawn = strokes(tab).takeIf { it.size == before + 1 }?.last()
            assertTrue("the lift committed exactly one stroke", drawn != null)
            drawn!!
            assertEquals(Tool.PEN, drawn.tool)
            assertTrue("the stroke is page points", drawn.points.last().x > 200.0)
            // The first packet is the lightest: it must draw a line, however thin, rather than nothing.
            assertTrue(
                "a light touch draws light, not nothing (${drawn.points.first().width} pt)",
                drawn.points.first().width > 0.0,
            )
            assertTrue(
                "the widths follow the packet pressures",
                drawn.points.zipWithNext().all { (a, b) -> b.width >= a.width },
            )
            // The filter is one-pole, so a four-packet ramp has not arrived yet — that lag is the point
            // (`StrokeSmoother` says the same on the tablet). A sustained press does reach the tool.
            assertTrue("a firm press is much wider than the first touch", drawn.points.last().width > 1.4)
            assertTrue("one gesture is one undo step", tab.canUndo)
            assertEquals("the pen is handed back after the lift", false, owned.last())
            assertFalse("and the path is no longer drawing", path.drawing)
        } finally {
            tab.document.close()
        }
    }

    /**
     * The driver sends 400 packets a second and the window paints at its own rate: the samples are
     * buffered and published once per frame (`flush`), so one frame's ink costs one invalidation
     * instead of eight. What must not change is the line itself — every sample in the driver's order,
     * with its own width — and a lift still commits exactly once.
     */
    @Test
    fun `packets are published once per frame, in order and with their widths`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("batch.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val draft = mutableStateListOf<InkSample>()
            val path = path(tab, draft = draft)
            val before = strokes(tab).size
            var nanos = 1_000_000_000L

            // The packets are queued by the driver's thread and drawn by the frame that consumes them:
            // the press opens the stroke there, and the eight moves ride along with it.
            path.packet(packet(40f, 40f, pressure = 0.1f, atNanos = nanos))
            assertEquals("no sample off the frame thread", 0, draft.size)
            path.flush()
            assertEquals("the press is the first sample", 1, draft.size)
            repeat(8) { index ->
                nanos += 2_500_000L
                path.packet(packet(60f + index * 20f, 50f + index * 5f, pressure = 0.2f + index * 0.1f, atNanos = nanos))
            }
            assertEquals("the moves are held for the frame", 1, draft.size)

            path.flush()
            assertEquals("one frame publishes every buffered sample", 9, draft.size)
            assertTrue(
                "the samples keep the driver's order",
                draft.zipWithNext().all { (a, b) -> b.x >= a.x && b.y >= a.y },
            )
            assertTrue(
                "and each keeps its own filtered width",
                draft.zipWithNext().all { (a, b) -> b.factor >= a.factor },
            )
            assertEquals("flushing is not a second stroke", before, strokes(tab).size)

            nanos += 2_500_000L
            path.send(packet(240f, 100f, pressure = 1f, atNanos = nanos))
            path.send(packet(240f, 100f, pressure = 0f, tip = false, atNanos = nanos + 2_500_000L))
            // `send` is a packet plus the frame that consumes it, which is what the canvas's ticker does.
            assertEquals("the lift flushes the tail and commits one stroke", before + 1, strokes(tab).size)
            assertEquals("with every sample the hand drew", 10, strokes(tab).last().points.size)
            assertTrue("one stroke is one undo step", tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a barely-moving light press is ink, and a sustained press reaches the tool's width`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("light.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val before = strokes(tab).size
            val path = path(tab)
            // A light press that travels one pixel and lifts: under the mouse's tap slop, so the mouse
            // path would have called it a click and drawn nothing. A pen on paper is ink from the press.
            path.send(packet(80f, 80f, pressure = 0.05f, atNanos = 1_000_000_000L))
            path.send(packet(81f, 81f, pressure = 0.06f, atNanos = 1_008_000_000L))
            path.send(packet(81f, 81f, pressure = 0f, tip = false, atNanos = 1_016_000_000L))
            assertEquals("a barely-moving light press leaves a stroke", before + 1, strokes(tab).size)
            assertEquals("with its two samples", 2, strokes(tab).last().points.size)

            val held = path(tab, ink = DesktopInk(width = 4f))
            var nanos = 2_000_000_000L
            repeat(60) {
                held.send(packet(120f, 120f + it, pressure = 1f, atNanos = nanos))
                nanos += 2_500_000L
            }
            held.send(packet(140f, 180f, pressure = 0f, tip = false, atNanos = nanos))
            val full = strokes(tab).last()
            assertEquals(
                "a press held long enough draws at the tool's width",
                4.0,
                full.points.last().width,
                1e-2,
            )
        } finally {
            tab.document.close()
        }
    }

    /**
     * The **ring** is the window's own ink, so it has to be moved by the driver's readings.
     *
     * The complaint this answers is a pointer that trails the nib: the ring used to follow the mouse
     * Windows emulates the pen as, whose moves are coalesced copies of the contact, while the driver's
     * own stream has the pen 400 times a second. A hovering pen is exactly the case — no stroke, no
     * mouse press, just a position that must be where the nib is.
     */
    @Test
    fun `a hovering pen moves the ring without starting a stroke`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("hover.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val before = strokes(tab).size
            val hovered = mutableListOf<Offset?>()
            val path = path(tab, hovered = hovered)
            path.send(packet(40f, 50f, pressure = null, tip = false, atNanos = 1_000_000_000L))
            path.send(packet(44f, 56f, pressure = null, tip = false, atNanos = 1_002_000_000L))
            // One publish per frame: two readings, one ring update, at the newest position.
            path.publishHover()
            assertEquals("one ring update for the frame", listOf(Offset(44f, 56f)), hovered)
            assertFalse("a hovering pen draws nothing", path.drawing)
            assertEquals("and commits nothing", before, strokes(tab).size)

            // A still pen publishes nothing: an unchanged ring must not repaint the window, and it must
            // not vanish either — the driver reports changes, so a resting hand sends no packets at all.
            path.publishHover()
            assertEquals("a rested pen keeps its ring", 1, hovered.size)

            // The packet that ends the hover is the driver's own proximity-out: then the ring goes, and
            // the window hands the pointer back to the mouse.
            val gone = packet(44f, 56f, pressure = null, tip = false, atNanos = 1_020_000_000L)
            path.send(gone.copy(near = false))
            path.publishHover()
            assertEquals("the ring is handed back", null, hovered.last())
            assertEquals("exactly once", 2, hovered.size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a packet off the canvas starts nothing, and a silence finishes the stroke where it stopped`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("off-canvas.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val before = strokes(tab).size
            val path = path(tab, size = Size(100f, 100f))
            path.send(packet(400f, 400f, pressure = 0.5f))
            path.send(packet(420f, 420f, pressure = 0.6f))
            assertEquals("a packet on the desk is not a stroke", before, strokes(tab).size)
            assertFalse(path.drawing)

            // A real stroke, then the packets stop: the silence ends it, and the ink the hand drew is
            // kept — a stroke that vanished under a pause is the loss this path exists to prevent.
            val second = path(tab, size = Size(600f, 900f))
            second.send(packet(50f, 50f, pressure = 0.5f, atNanos = 2_000_000_000L))
            second.send(packet(90f, 90f, pressure = 0.6f, atNanos = 2_010_000_000L))
            assertTrue("the stroke is in progress", second.drawing)
            second.finishIfStale(2_010_000_000L + PenPacketStroke.STALE_NANOS)
            assertEquals("the silence committed it where it stopped", before + 1, strokes(tab).size)
            assertEquals("with the samples the hand drew", 2, strokes(tab).last().points.size)
            assertFalse(second.drawing)
        } finally {
            tab.document.close()
        }
    }

    /**
     * A pen resting on the glass sends **no** packets (the driver reports changes), so a short window is
     * what made light, careful strokes disappear under a pause. The window is long enough that a still pen
     * keeps its stroke, and a real silence ends it instead of stitching the next stroke onto it.
     */
    @Test
    fun `a pen held still keeps its stroke, and a silence starts the next one`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("paused.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val before = strokes(tab).size
            val path = path(tab, size = Size(600f, 900f))
            path.send(packet(60f, 60f, pressure = 0.3f, atNanos = 1_000_000_000L))
            path.send(packet(120f, 90f, pressure = 0.5f, atNanos = 1_010_000_000L))

            // Half a second of stillness: the ticker sees no packets, and the stroke is still being drawn.
            path.finishIfStale(1_010_000_000L + 500_000_000L)
            assertTrue("a pause is not a lift", path.drawing)
            assertEquals("and nothing was committed by it", before, strokes(tab).size)

            // A tip-down more than a window later is a new stroke, and the first one is finished as it was.
            val later = 1_010_000_000L + PenPacketStroke.STALE_NANOS
            path.send(packet(200f, 160f, pressure = 0.4f, atNanos = later))
            assertEquals("the paused stroke was kept", before + 1, strokes(tab).size)
            assertEquals("with the two samples it had", 2, strokes(tab).last().points.size)
            assertTrue("and the new tip-down is a stroke of its own", path.drawing)

            path.send(packet(260f, 200f, pressure = 0.8f, atNanos = later + 10_000_000L))
            path.send(packet(260f, 200f, pressure = 0f, tip = false, atNanos = later + 20_000_000L))
            assertEquals("which the lift commits separately", before + 2, strokes(tab).size)
            assertEquals(2, strokes(tab).last().points.size)
        } finally {
            tab.document.close()
        }
    }
}
