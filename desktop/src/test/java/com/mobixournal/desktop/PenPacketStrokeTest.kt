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
            path.packet(packet(20f, 20f, pressure = 0.02f, atNanos = 1_000_000_000L))
            path.packet(packet(80f, 60f, pressure = 0.2f, atNanos = 1_008_000_000L))
            path.packet(packet(160f, 120f, pressure = 0.6f, atNanos = 1_016_000_000L))
            // The pen resting at the end of the stroke: a real one sends a few hundred of these, and the
            // position filter is what the last few millimetres look like.
            repeat(8) { index ->
                path.packet(packet(260f, 200f, pressure = 1f, atNanos = 1_024_000_000L + index * 2_000_000L))
            }
            assertTrue("the pen owns the stroke while packets arrive", owned.contains(true))

            path.packet(packet(260f, 200f, pressure = 0f, tip = false, atNanos = 1_040_000_000L))

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
            path.packet(packet(80f, 80f, pressure = 0.05f, atNanos = 1_000_000_000L))
            path.packet(packet(81f, 81f, pressure = 0.06f, atNanos = 1_008_000_000L))
            path.packet(packet(81f, 81f, pressure = 0f, tip = false, atNanos = 1_016_000_000L))
            assertEquals("a barely-moving light press leaves a stroke", before + 1, strokes(tab).size)
            assertEquals("with its two samples", 2, strokes(tab).last().points.size)

            val held = path(tab, ink = DesktopInk(width = 4f))
            var nanos = 2_000_000_000L
            repeat(60) {
                held.packet(packet(120f, 120f + it, pressure = 1f, atNanos = nanos))
                nanos += 2_500_000L
            }
            held.packet(packet(140f, 180f, pressure = 0f, tip = false, atNanos = nanos))
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

    @Test
    fun `a packet off the canvas starts nothing, and a silence finishes the stroke where it stopped`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook("off-canvas.mobix")))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        try {
            val before = strokes(tab).size
            val path = path(tab, size = Size(100f, 100f))
            path.packet(packet(400f, 400f, pressure = 0.5f))
            path.packet(packet(420f, 420f, pressure = 0.6f))
            assertEquals("a packet on the desk is not a stroke", before, strokes(tab).size)
            assertFalse(path.drawing)

            // A real stroke, then the packets stop: the silence ends it, and the ink the hand drew is
            // kept — a stroke that vanished under a pause is the loss this path exists to prevent.
            val second = path(tab, size = Size(600f, 900f))
            second.packet(packet(50f, 50f, pressure = 0.5f, atNanos = 2_000_000_000L))
            second.packet(packet(90f, 90f, pressure = 0.6f, atNanos = 2_010_000_000L))
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
            path.packet(packet(60f, 60f, pressure = 0.3f, atNanos = 1_000_000_000L))
            path.packet(packet(120f, 90f, pressure = 0.5f, atNanos = 1_010_000_000L))

            // Half a second of stillness: the ticker sees no packets, and the stroke is still being drawn.
            path.finishIfStale(1_010_000_000L + 500_000_000L)
            assertTrue("a pause is not a lift", path.drawing)
            assertEquals("and nothing was committed by it", before, strokes(tab).size)

            // A tip-down more than a window later is a new stroke, and the first one is finished as it was.
            val later = 1_010_000_000L + PenPacketStroke.STALE_NANOS
            path.packet(packet(200f, 160f, pressure = 0.4f, atNanos = later))
            assertEquals("the paused stroke was kept", before + 1, strokes(tab).size)
            assertEquals("with the two samples it had", 2, strokes(tab).last().points.size)
            assertTrue("and the new tip-down is a stroke of its own", path.drawing)

            path.packet(packet(260f, 200f, pressure = 0.8f, atNanos = later + 10_000_000L))
            path.packet(packet(260f, 200f, pressure = 0f, tip = false, atNanos = later + 20_000_000L))
            assertEquals("which the lift commits separately", before + 2, strokes(tab).size)
            assertEquals(2, strokes(tab).last().points.size)
        } finally {
            tab.document.close()
        }
    }
}
