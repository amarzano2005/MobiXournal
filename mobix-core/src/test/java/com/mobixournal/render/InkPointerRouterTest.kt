package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The router's decisions, driven from synthetic pointer streams: the two shapes a pen really makes (a
 * press that ramps up, a tap that goes nowhere), the second finger that must not draw, and the eraser
 * whose hit **splits** a stroke.
 *
 * Every one of these was previously only observable on a device, through a `MotionEvent` a JVM cannot
 * build (`docs/stylus.md` says so about the on-device `StylusInputTest`). The values used here are the
 * same ones the app's own input path uses — [PressureCurve] for the filter, [PageEraser] for the cut —
 * so a test that passes here is a statement about both shells.
 */
class InkPointerRouterTest {

    private fun pen(
        x: Float,
        y: Float,
        pressure: Float = 0.5f,
        phase: InkPointerPhase = InkPointerPhase.MOVE,
        timeMs: Long = 0L,
        id: Int = 1,
        tool: PointerKind = PointerKind.STYLUS,
        buttons: Int = 0,
    ) = InkPointer(
        id = id,
        x = x,
        y = y,
        pressure = pressure,
        timeMs = timeMs,
        tool = tool,
        phase = phase,
        buttons = buttons,
    )

    private fun router(
        tool: ActiveTool = ActiveTool.PEN,
        settings: InputSettings = InputSettings(),
        startOnDown: Boolean = false,
    ) = InkPointerRouter(activeTool = { tool }, settings = settings, startOnDown = startOnDown)

    @Test
    fun aPressureRampComesBackThroughTheDesktopFilter() {
        val router = InkPointerRouter(
            activeTool = { ActiveTool.PEN },
            pressureEnabled = true,
            pressureMultiplier = 1f,
            minimumPressure = PressureCurve.MINIMUM_PRESSURE_DEFAULT,
        )
        val events = ArrayList<InkEvent>()
        events += router.onPointer(pen(10f, 10f, pressure = 0.02f, phase = InkPointerPhase.DOWN))
        // Past the slop, so the press becomes a stroke; the first sample joins it.
        events += router.onPointer(pen(40f, 10f, pressure = 0.2f))
        events += router.onPointer(pen(80f, 10f, pressure = 0.9f))
        events += router.onPointer(pen(100f, 10f, pressure = 1.0f))
        events += router.onPointer(pen(120f, 10f, pressure = 1.0f, phase = InkPointerPhase.UP))

        val start = events.filterIsInstance<InkEvent.StrokeStart>().single()
        assertEquals("the stroke starts where the tip landed", 10f, start.start.x, 1e-4f)
        // Below the floor is the floor: desktop Xournal++'s minimumPressure, not a curve of our own.
        assertEquals(PressureCurve.MINIMUM_PRESSURE_DEFAULT, start.widthFactor, 1e-4f)

        val samples = events.filterIsInstance<InkEvent.StrokeSample>().map { it.widthFactor }
        assertEquals(listOf(0.2f, 0.9f, 1.0f), samples)
        assertEquals("a full press is full width", 1.0f, samples.last(), 1e-4f)
        assertTrue(events.last() is InkEvent.StrokeEnd)
    }

    @Test
    fun aTapDoesNotDraw() {
        val router = router()
        val events = ArrayList<InkEvent>()
        events += router.onPointer(pen(20f, 20f, phase = InkPointerPhase.DOWN, timeMs = 0))
        events += router.onPointer(pen(23f, 22f, timeMs = 40))
        events += router.onPointer(pen(24f, 22f, phase = InkPointerPhase.UP, timeMs = 80))

        assertTrue("a tap is a tap", events.single() is InkEvent.Tap)
        assertTrue(events.none { it is InkEvent.StrokeStart })
    }

    @Test
    fun aTapMayLeaveADotWhereTheShellWantsOne() {
        // The tablet inks on the press; the desktop must not. One flag, not one implementation each.
        val router = router(startOnDown = true)
        val events = router.onPointer(pen(20f, 20f, phase = InkPointerPhase.DOWN))
        assertTrue(events.single() is InkEvent.StrokeStart)
        assertTrue(router.onPointer(pen(20f, 20f, phase = InkPointerPhase.UP)).single() is InkEvent.StrokeEnd)
    }

    @Test
    fun aSecondFingerEndsTheStrokeAndStartsAPan() {
        // With *finger draws* on, a finger inks like the pen; the second finger is then navigation,
        // which is the case where the stroke must end rather than be finished with a stray tap.
        val router = router(tool = ActiveTool.PEN, settings = InputSettings(fingerDraws = true))
        val events = ArrayList<InkEvent>()
        events += router.onPointer(pen(10f, 10f, phase = InkPointerPhase.DOWN, id = 1, tool = PointerKind.FINGER))
        events += router.onPointer(pen(60f, 10f, id = 1, tool = PointerKind.FINGER))
        assertTrue(events.any { it is InkEvent.StrokeStart })

        // A finger lands while the pen is writing: navigation begins, and the stroke is ended, not
        // committed as a tap.
        events += router.onPointer(pen(200f, 100f, phase = InkPointerPhase.DOWN, id = 2, tool = PointerKind.FINGER))
        assertTrue(router.navigating)
        assertTrue(events.last() is InkEvent.PanBegin)
        assertTrue("the stroke is over", events.any { it is InkEvent.GestureEnd })

        val pan = router.onPointer(pen(230f, 130f, id = 2, tool = PointerKind.FINGER))
        val moved = pan.filterIsInstance<InkEvent.Pan>().single()
        // The focus is the midpoint of the two contacts, so dragging one finger pans by half its move.
        assertEquals(15f, moved.dx, 1e-4f)
        assertEquals(15f, moved.dy, 1e-4f)

        // A pinch apart zooms in.
        val zoom = router.onPointer(pen(300f, 130f, id = 2, tool = PointerKind.FINGER))
        assertTrue(zoom.filterIsInstance<InkEvent.Zoom>().single().factor > 1f)
    }

    @Test
    fun aRestingPalmIsIgnoredWhileThePenOwnsTheGesture() {
        val router = router()
        router.onPointer(pen(10f, 10f, phase = InkPointerPhase.DOWN, id = 1))
        router.onPointer(pen(40f, 10f, id = 1))
        val palm = router.onPointer(pen(300f, 400f, phase = InkPointerPhase.DOWN, id = 7, tool = PointerKind.FINGER))
        assertTrue("the hand does not start a pan under the pen", palm.isEmpty())
        assertFalse(router.navigating)
    }

    @Test
    fun theEraserTipErasesWhateverTheToolAndItsHitSplitsTheStroke() {
        // The tip outranks the toolbar: the classifier's own precedence, reached through the stream.
        val router = router(tool = ActiveTool.PEN)
        val events = router.onPointer(pen(50f, 50f, phase = InkPointerPhase.DOWN, tool = PointerKind.ERASER_TIP))
        val hit = events.single() as InkEvent.Erase
        assertEquals(50f, hit.x, 1e-4f)

        // Cutting a page at that point is PageEraser's job, and it splits rather than deletes: 5 / 5.
        val stroke = Stroke(
            points = (0..10).map { StrokePoint(it * 20.0, 50.0, 1.0) },
            color = 0xFF000000.toInt(),
            tool = Tool.PEN,
            capStyle = "round",
            uniformWidth = false,
        )
        val page = Page(
            width = 595.0,
            height = 842.0,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(listOf(stroke))),
        )
        val cut = PageEraser.erase(page, 100.0, 50.0, radius = 12.0, mode = EraserMode.STANDARD)
        val halves = cut!!.layers.single().elements.filterIsInstance<Stroke>()
        // Two pieces, and the cut brackets the hit: each half ends where the rubber began, so the
        // wiped span is the eraser's own diameter and nothing outside it was taken.
        assertEquals(2, halves.size)
        val left = halves.minByOrNull { it.points.first().x }!!
        val right = halves.maxByOrNull { it.points.first().x }!!
        assertTrue(
            "the cut brackets the hit: ${halves.map { h -> h.points.map { it.x } }}",
            left.points.last().x < 100.0 && right.points.first().x > 100.0,
        )
        assertTrue("nothing inside the disc survived", left.points.all { it.x < 100.0 } && right.points.all { it.x > 100.0 })
    }

    @Test
    fun aHeldBarrelErasesOnADrag() {
        val router = router(tool = ActiveTool.PEN)
        val events = ArrayList<InkEvent>()
        events += router.onPointer(
            pen(10f, 10f, phase = InkPointerPhase.DOWN, buttons = InkPointer.SECONDARY),
        )
        events += router.onPointer(pen(40f, 10f, buttons = InkPointer.SECONDARY))
        assertEquals(2, events.filterIsInstance<InkEvent.Erase>().size)
        assertTrue(events.none { it is InkEvent.StrokeStart })
    }

    @Test
    fun aFingerWithFingerDrawsOffOnlyNavigates() {
        val router = router(settings = InputSettings(fingerDraws = false))
        val events = router.onPointer(pen(10f, 10f, phase = InkPointerPhase.DOWN, tool = PointerKind.FINGER))
        assertTrue("a finger actuates no tool at all", events.isEmpty())
        val up = router.onPointer(pen(10f, 10f, phase = InkPointerPhase.UP, tool = PointerKind.FINGER))
        assertTrue(up.single() is InkEvent.GestureEnd)
        assertNull("nothing was drawn", events.firstOrNull { it is InkEvent.StrokeStart })
    }
}
