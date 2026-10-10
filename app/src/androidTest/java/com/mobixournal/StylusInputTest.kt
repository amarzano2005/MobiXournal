package com.mobixournal

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.BarrelAction
import com.mobixournal.render.BarrelClickDetector
import com.mobixournal.render.BarrelDoubleAction
import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.InputSettings
import com.mobixournal.render.PressureCurve
import com.mobixournal.render.Snapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of the stylus input layer (see `docs/architecture.md` → "Stylus &
 * selection roadmap"): the eraser tip, the finger-draw palm gate, the barrel button, palm rejection
 * while a stylus writes, the pressure stream, and the guide hook. These need real Android
 * `MotionEvent`s carrying a *tool type*, *button state* or per-pointer *pressure* (none of which
 * `adb shell input` can inject, and JVM unit tests can't build), so they live here and run via
 * `connectedDebugAndroidTest`. The pure logic is unit-tested in `InputClassifierTest`,
 * `PressureCurveTest` and `DrawingGuideTest`; these prove the view is wired to it.
 *
 * Run them on a host with an attached emulator/device:
 * `scripts/host-build.sh connectedDebugAndroidTest` (see `docs/tools.md` → "Host fallback").
 */
@RunWith(AndroidJUnit4::class)
class StylusInputTest {

    /** The eraser tip erases a stroke even though the selected tool is the pen. */
    @Test
    fun eraserTipErasesWithPenSelected() = onView { view ->
        view.tool = Tool.PEN
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals("pen laid down a stroke", 1, strokesOf(view).size)

        // Same path, but with the flipped-over eraser tip: it should delete the stroke.
        drawLine(view, MotionEvent.TOOL_TYPE_ERASER)
        assertEquals("eraser tip removed the stroke", 0, strokesOf(view).size)
    }

    /** The barrel button erases while held, regardless of the pen being selected (default mapping). */
    @Test
    fun barrelButtonErasesWhileHeld() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(barrelAction = BarrelAction.ERASE)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1, strokesOf(view).size)

        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS, buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals("barrel-held stylus erased the stroke", 0, strokesOf(view).size)
    }

    /**
     * A pen whose button arrives only as a **key** event still erases while held. This is the Honor
     * Choice Pencil's shape: the tablet sees it as a Bluetooth keyboard and it sends an unnamed key
     * code (755) for the side button, while every motion event of the stroke reports no buttons at
     * all — so neither a documented barrel key code nor the motion stream can recognise it.
     */
    @Test
    fun barrelButtonSentAsAKeyErasesWhileHeld() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(barrelAction = BarrelAction.ERASE)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1, strokesOf(view).size)

        // The button goes down as a key the tablet has no name for, and stays down across the stroke.
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, VENDOR_BARREL_KEY_CODE))
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals("a key-only barrel erased the stroke", 0, strokesOf(view).size)

        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, VENDOR_BARREL_KEY_CODE))
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals("and the pen draws again once the button is up", 1, strokesOf(view).size)
    }

    /**
     * A pen whose own firmware recognises the double-click and reports it as **one** click edge runs
     * the bound action off that single edge. This is the Honor Choice Pencil's double-click, and
     * waiting for a second edge would mean the button never does anything at all.
     */
    @Test
    fun vendorKeyClickRunsTheDoubleClickAction() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(barrelDoubleAction = BarrelDoubleAction.UNDO)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1, strokesOf(view).size)

        // One click of the button, as the pen's firmware reports a whole double-click.
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, VENDOR_BARREL_KEY_CODE))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, VENDOR_BARREL_KEY_CODE))
        assertEquals("the single click ran the bound undo", 0, strokesOf(view).size)

        // A second click *inside* the same gesture is the same gesture: it must not undo twice.
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(2, strokesOf(view).size)
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, VENDOR_BARREL_KEY_CODE))
        assertEquals("the second edge of one gesture is swallowed", 2, strokesOf(view).size)
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, VENDOR_BARREL_KEY_CODE))
    }

    /** A vendor-key button that is genuinely held still erases, as any other held barrel does. */
    @Test
    fun heldVendorKeyClickStillErases() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(barrelAction = BarrelAction.ERASE, barrelDoubleAction = BarrelDoubleAction.NONE)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1, strokesOf(view).size)

        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, VENDOR_BARREL_KEY_CODE))
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals("the held button erased as usual", 0, strokesOf(view).size)
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, VENDOR_BARREL_KEY_CODE))
    }

    /** Two barrel clicks in quick succession, tip off the glass, run the bound action (undo). */
    @Test
    fun barrelDoubleClickWhileHoveringUndoes() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(barrelDoubleAction = BarrelDoubleAction.UNDO)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1, strokesOf(view).size)

        hoverClick(view); hoverClick(view)
        assertEquals("the double-click undid the stroke", 0, strokesOf(view).size)
    }

    /** Two clicks *slower* than the double-click window are two singles, and do nothing. */
    @Test
    fun slowBarrelClicksDoNotUndo() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(barrelDoubleAction = BarrelDoubleAction.UNDO)
        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)

        hoverClick(view)
        SystemClock.sleep(BarrelClickDetector.DEFAULT_WINDOW_MS + 100)
        hoverClick(view)
        assertEquals("the stroke survived two slow clicks", 1, strokesOf(view).size)
    }

    /** With finger-draw off, a finger only pans — it must not lay ink; a stylus still draws. */
    @Test
    fun fingerDrawOffStopsFingerButNotStylus() = onView { view ->
        view.tool = Tool.PEN
        view.inputSettings = InputSettings(fingerDraws = false)

        drawLine(view, MotionEvent.TOOL_TYPE_FINGER)
        assertEquals("finger did not draw", 0, strokesOf(view).size)

        drawLine(view, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals("stylus still draws", 1, strokesOf(view).size)
    }

    /** A palm (finger) resting mid-stroke while the stylus writes is ignored, not drawn/panned. */
    @Test
    fun palmRestingDuringStylusStrokeIsIgnored() = onView { view ->
        view.tool = Tool.PEN
        val downTime = SystemClock.uptimeMillis()

        // Stylus tip goes down and moves right.
        send(view, downTime, downTime, MotionEvent.ACTION_DOWN, floatArrayOf(200f), floatArrayOf(300f), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS))
        send(view, downTime, now(), MotionEvent.ACTION_MOVE, floatArrayOf(260f), floatArrayOf(300f), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS))

        // A palm lands as a second pointer and moves — it must not perturb the stroke.
        send(view, downTime, now(), MotionEvent.ACTION_POINTER_DOWN,
            floatArrayOf(260f, 850f), floatArrayOf(300f, 900f), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER), actionIndex = 1)
        send(view, downTime, now(), MotionEvent.ACTION_MOVE,
            floatArrayOf(320f, 860f), floatArrayOf(300f, 910f), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER))
        // Palm lifts, then the stylus lifts.
        send(view, downTime, now(), MotionEvent.ACTION_POINTER_UP,
            floatArrayOf(320f, 860f), floatArrayOf(300f, 910f), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER), actionIndex = 1)
        send(view, downTime, now(), MotionEvent.ACTION_UP, floatArrayOf(320f), floatArrayOf(300f), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS))

        val strokes = strokesOf(view)
        assertEquals("exactly one stroke, from the stylus only", 1, strokes.size)
        // The palm was far below (view y≈900); its page-y would be large. Every sampled point must
        // come from the stylus's own path (y≈300 view px → small page y), proving the palm was ignored.
        val maxY = strokes[0].points.maxOf { it.y }
        assertTrue("no palm samples leaked into the stroke (maxY=$maxY)", maxY < 200.0)
    }

    /**
     * The same page-space gesture keeps the same detail whether it's drawn at 100% or zoomed out.
     *
     * Decimation and simplification were both budgeted purely in *view pixels*, so zooming out
     * multiplied how much page detail they threw away — at overview magnification a stored vertex
     * stood for several times as much real pen movement, which is the "corners and jumps" this
     * test guards against.
     */
    @Test
    fun sameGestureKeepsItsDetailAtEveryZoom() = onView { view ->
        view.tool = Tool.PEN

        val wide = drawRipple(view, zoom = 1f)
        // Six zoom-out steps ≈ 26% — the magnification of a multi-page overview.
        var zoomed = 1f
        repeat(6) { view.zoomOut(); zoomed /= ZOOM_STEP }
        val narrow = drawRipple(view, zoom = zoomed)

        assertTrue("100% gesture produced points", wide.size >= 8)
        assertTrue("zoomed-out gesture produced points", narrow.size >= 8)

        // Both strokes traced the same ripple on the page, so they should store a comparable
        // number of vertices and cover a comparable page-space distance. With the budgets pinned to
        // view pixels the zoomed-out stroke kept barely a third of the vertices; with them bounded
        // in page points it keeps over half (the rest is the simplifier's own page-point cap).
        assertTrue(
            "zoomed-out kept ${narrow.size} vertices vs ${wide.size} at 100%",
            narrow.size >= wide.size / 2,
        )
        val wideLen = lengthPt(wide)
        val narrowLen = lengthPt(narrow)
        assertTrue(
            "zoomed-out path length ${narrowLen}pt vs 100% ${wideLen}pt",
            narrowLen >= wideLen * 0.95,
        )
    }

    /**
     * Pressure reaches the document. A stroke whose samples press harder and harder must store, per
     * vertex, the width [PressureCurve] makes of that sample's own reading — the wiring that `adb`
     * cannot exercise, because `input tap`/`input swipe` send no pressure at all (every sample would
     * arrive at 1.0 and every stroke would be uniformly thick).
     *
     * The path curves, so the simplifier can't reduce it to its two ends and every stored vertex is
     * one of the samples. A stroke's vertices are its **motion** samples (down, then each move): the
     * lift-off closes the gesture and leaves the geometry where the pen's last movement put it.
     *
     * The pressure itself is low-pass filtered by `StrokeSmoother` — a digitiser's reading is noisy,
     * and the stored width is the filtered one — so the assertions are about the *taper* the press
     * produces (every vertex wider than the one before it, starting from the first sample's own
     * reading) rather than about each width matching the number this test happened to send.
     */
    @Test
    fun pressureIsReadFromEverySampleAndWidthsTheVertices() = onView { view ->
        view.tool = Tool.PEN
        view.baseWidthPt = 2f
        val pressures = floatArrayOf(0.05f, 0.2f, 0.35f, 0.5f, 0.65f, 0.8f, 0.95f)
        drawGradedStroke(view, pressures)

        val widths = strokesOf(view).single().points.map { it.width }
        val expected = pressures.map {
            PressureCurve.widthPt(view.baseWidthPt, it, view.pressureEnabled, view.pressureMultiplier, view.minimumPressure)
        }
        assertTrue("the stroke stored several vertices ($widths)", widths.size >= 3)
        assertEquals(
            "the first vertex is the first sample's own pressure",
            expected.first(),
            widths.first(),
            1e-9,
        )
        assertTrue(
            "harder presses stored wider vertices ($widths)",
            widths.zipWithNext().all { (a, b) -> b > a },
        )
        assertTrue(
            "and the taper is substantial, not rounding ($widths)",
            widths.last() > widths.first() * 5,
        )
    }

    /**
     * With **Pressure sensitivity** off the very same pressured stroke stores one uniform width, so
     * a pen stroke and a figure keep matching thickness (the desktop's own behaviour when the
     * setting is unchecked).
     */
    @Test
    fun pressureSensitivityOffStoresOneUniformWidth() = onView { view ->
        view.tool = Tool.PEN
        view.baseWidthPt = 2f
        view.pressureEnabled = false
        drawGradedStroke(view, floatArrayOf(0.05f, 0.5f, 1f))

        val widths = strokesOf(view).single().points.map { it.width }
        assertEquals("pressure off means one width, not a taper ($widths)", 1, widths.distinct().size)
        assertEquals(2.0, widths.first(), 1e-9)
    }

    /**
     * The pen **rules along a placed guide**: with a setsquare on the page, a stylus stroke drawn
     * 8pt off its hypotenuse — wobbling, so the raw samples never line up — is stored with every
     * vertex pulled onto that edge, and the same stroke out of the guide's reach is stored exactly
     * as drawn. "Within reach" and "out of reach" are both `DrawingGuide.GRAB_PT`'s decision, made on
     * the point after it has been mapped to page pt; what is verified here is that drawn vertices
     * really pass through it, which is what makes a guide behave like a straightedge.
     */
    @Test
    fun stylusVerticesHookOntoTheSetsquareAndReleaseBeyondItsReach() = onView { view ->
        view.tool = Tool.PEN
        val guide = DrawingGuide.Setsquare(x = 120.0, y = 400.0)
        val samples = offEdgeStroke(guide, offsetPt = 8.0)

        view.restoreGuide(guide, page = 0)
        drawStroke(view, samples)
        val hooked = strokesOf(view).last().points
        // The capture makes the stroke a straight line on the edge, so the simplifier is entitled to
        // keep only its ends — what matters is where those stored vertices are, not how many.
        assertTrue("the guide-captured stroke was committed (${hooked.size} vertices)", hooked.size >= 2)
        val worstHook = hooked.maxOf { distanceToNearestEdge(it, guide) }
        assertTrue("every vertex was pulled onto the edge (worst was ${worstHook}pt)", worstHook < 0.5)

        // Cleared, the same gesture is stored as drawn: it follows the 8pt offset instead.
        view.restoreGuide(null, page = 0)
        drawStroke(view, samples)
        val free = strokesOf(view).last().points
        val worstFree = free.maxOf { distanceToNearestEdge(it, guide) }
        assertTrue("out of reach the stroke keeps its offset (worst was ${worstFree}pt)", worstFree > 4.0)
        assertTrue("and the guide is gone", view.guide == null)
        assertTrue("both strokes are ordinary ink in the document", strokesOf(view).size == 2)
    }

    /**
     * The **ruling guide** hooks *every* vertex — it has no edge to leave and no reach limit — so a
     * stroke drawn between the ruled lines of a graph sheet lands on them, and clearing the guide
     * hands the same gesture back untouched. This is the guide a live session verified by eye; the
     * assertion of where each vertex landed is what an `adb`-driven check cannot make.
     */
    @Test
    fun rulingGuidePullsEveryVertexOntoThePagesRuling() = onView { view ->
        view.tool = Tool.PEN
        val lattice = Snapping.lattice(blankDocument().pages[0].background)
        assertTrue("a graph sheet rules lines to land on ($lattice)", lattice.active)
        // Deliberately halfway between ruled lines on both axes, and stepping line by line through
        // the *middle* of the page, so a stored vertex sitting on a line can only have been pulled
        // there. The rows zig-zag (two lines down, five down, two, five…) to keep the path off a
        // single straight diagonal, which the simplifier would be entitled to flatten.
        val samples = (0 until 6).map { k ->
            val x = lattice.phaseX + lattice.stepX * (20 + k) + lattice.stepX / 2
            val y = lattice.phaseY + lattice.stepY * (20 + if (k % 2 == 0) 2 else 5) + lattice.stepY / 2
            x to y
        }
        assertTrue(
            "the samples were chosen off the ruling (lattice=$lattice)",
            samples.all { (x, y) ->
                lattice.stepX > 0.0 && lattice.stepY > 0.0 &&
                    kotlin.math.abs(x - lattice.snapX(x)) > 1.0 &&
                    kotlin.math.abs(y - lattice.snapY(y)) > 1.0
            },
        )

        view.restoreGuide(DrawingGuide.Ruling(0.0, 0.0, lattice), page = 0)
        drawStroke(view, samples)
        assertEquals("the ruled stroke was committed", 1, strokesOf(view).size)
        val ruled = strokesOf(view).last().points
        assertTrue("the ruled stroke stored vertices (${ruled.size})", ruled.size >= 3)
        assertTrue(
            "every vertex sits on a ruled line ($ruled)",
            ruled.all { p ->
                kotlin.math.abs(p.x - lattice.snapX(p.x)) < 0.01 &&
                    kotlin.math.abs(p.y - lattice.snapY(p.y)) < 0.01
            },
        )

        view.restoreGuide(null, page = 0)
        drawStroke(view, samples)
        val free = strokesOf(view).last().points
        assertTrue(
            "cleared, the vertex between the lines is kept off them",
            free.any { kotlin.math.abs(it.y - lattice.snapY(it.y)) > 1.0 },
        )
    }

    // --- harness -------------------------------------------------------------------------------

    /**
     * Trace a fine ripple defined in **page points**, so the gesture is the same real movement at
     * every [zoom], and feed it as dense batches (only a batch's newest sample is force-kept, so
     * this exercises the decimation path the way a real digitiser does).
     */
    private fun drawRipple(view: DrawingSurfaceView, zoom: Float): List<StrokePoint> {
        val before = strokesOf(view).size
        val map = pageMap(zoom)
        // 0.4pt per sample along 200pt, rippling ±2.5pt every 10pt — detail a fixed 1.6 view-px
        // decimation radius keeps at 100% (0.9pt) but erases when zoomed out (3.4pt).
        fun pageX(i: Int) = 100.0 + i * 0.4
        fun pageY(i: Int) = 300.0 + kotlin.math.sin(pageX(i) * 2 * Math.PI / 10.0) * 2.5
        fun px(i: Int) = map.x(pageX(i))
        fun py(i: Int) = map.y(pageY(i))

        val downTime = SystemClock.uptimeMillis()
        send(view, downTime, downTime, MotionEvent.ACTION_DOWN, floatArrayOf(px(0)), floatArrayOf(py(0)), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS))
        var i = 1
        while (i < 500) {
            sendBatch(view, downTime, MotionEvent.ACTION_MOVE, (i until i + 10).map { px(it) to py(it) })
            i += 10
        }
        send(view, downTime, now(), MotionEvent.ACTION_UP, floatArrayOf(px(i)), floatArrayOf(py(i)), intArrayOf(MotionEvent.TOOL_TYPE_STYLUS))

        val strokes = strokesOf(view)
        assertEquals("the ripple committed one stroke", before + 1, strokes.size)
        return strokes.last().points
    }

    /** One [MotionEvent] carrying [samples] as historical samples plus a current one. */
    private fun sendBatch(view: View, downTime: Long, action: Int, samples: List<Pair<Float, Float>>) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun coordsAt(s: Pair<Float, Float>) = arrayOf(
            MotionEvent.PointerCoords().apply { x = s.first; y = s.second; pressure = 1f; size = 1f },
        )
        val event = MotionEvent.obtain(
            downTime, now(), action, 1, props, coordsAt(samples.first()),
            0, 0, 1f, 1f, 0, 0, 0x00004002 /* SOURCE_STYLUS */, 0,
        )
        try {
            for (s in samples.drop(1)) event.addBatch(now(), coordsAt(s), 0)
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Total page-space length of the stored polyline — how much of the real path survived. */
    private fun lengthPt(points: List<StrokePoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> kotlin.math.hypot(b.x - a.x, b.y - a.y) }

    /**
     * A stylus stroke along the **same short path**, pressing [pressures] harder sample by sample —
     * one event per sample, so each one survives decimation and its own pressure is what the stored
     * vertex is made of.
     */
    private fun drawGradedStroke(view: DrawingSurfaceView, pressures: FloatArray) {
        val map = pageMap()
        val downTime = SystemClock.uptimeMillis()
        pressures.indices.forEach { i ->
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                pressures.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            // A gentle arc, so the sampled path is not a straight line the simplifier may collapse.
            val pageX = 200.0 + i * 18.0
            val pageY = 400.0 + kotlin.math.sin(i * 1.1) * 6.0
            send(
                view, downTime, if (i == 0) downTime else now(), action,
                floatArrayOf(map.x(pageX)), floatArrayOf(map.y(pageY)),
                intArrayOf(MotionEvent.TOOL_TYPE_STYLUS), pressures = floatArrayOf(pressures[i]),
            )
        }
    }

    /** One stylus stroke through [samples] (page pt), down -> moves -> up. */
    private fun drawStroke(view: DrawingSurfaceView, samples: List<Pair<Double, Double>>) {
        val map = pageMap()
        val downTime = SystemClock.uptimeMillis()
        samples.forEachIndexed { i, (pageX, pageY) ->
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                samples.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            send(
                view, downTime, if (i == 0) downTime else now(), action,
                floatArrayOf(map.x(pageX)), floatArrayOf(map.y(pageY)),
                intArrayOf(MotionEvent.TOOL_TYPE_STYLUS),
            )
        }
    }

    /**
     * A stroke running along the guide's nearest drawing edge, held [offsetPt] off it and wobbling —
     * too far away to be captured by accident, near enough to be captured at all, and never quite on
     * the edge, so a stored vertex sitting exactly on it can only have been pulled there.
     */
    private fun offEdgeStroke(guide: DrawingGuide.Setsquare, offsetPt: Double): List<Pair<Double, Double>> {
        val corners = guide.corners()
        val (a, b) = corners[0] to corners[1]
        val midX = (a.first + b.first) / 2
        val midY = (a.second + b.second) / 2
        val ux = (b.first - a.first) / kotlin.math.hypot(b.first - a.first, b.second - a.second)
        val uy = (b.second - a.second) / kotlin.math.hypot(b.first - a.first, b.second - a.second)
        // Perpendicular pointing away from the right-angle corner, i.e. off the outside of the leg.
        val sign = if ((midX - corners[2].first) * -uy + (midY - corners[2].second) * ux < 0) -1.0 else 1.0
        val nx = -uy * sign * offsetPt
        val ny = ux * sign * offsetPt
        return listOf(-30.0, -10.0, 10.0, 30.0).map { step ->
            (midX + ux * step + nx + step / 20.0) to (midY + uy * step + ny + step / 20.0)
        }
    }

    /** Distance (pt) from [point] to the closest of the setsquare's three drawing edges. */
    private fun distanceToNearestEdge(point: StrokePoint, guide: DrawingGuide.Setsquare): Double {
        val corners = guide.corners()
        // Each edge as the pair of corners it runs between, so `(a, b)` in the lambda is its ends.
        val edges = listOf(corners[0] to corners[1], corners[0] to corners[2], corners[1] to corners[2])
        return edges.minOf { (a, b) ->
            val (qx, qy) = DrawingGuide.closestOnSegment(point.x, point.y, a.first, a.second, b.first, b.second)
            kotlin.math.hypot(point.x - qx, point.y - qy)
        }
    }

    /**
     * Page pt -> view px for the single-column page at [zoom], mirroring `PageStacker.stack`: the
     * page is fitted to the view width, centred horizontally, and the first page starts one gap
     * down. Tests need it to aim a gesture at a known page-space spot.
     */
    private class PageMap(val scale: Float, val left: Float, val top: Float) {
        fun x(pageX: Double): Float = left + (pageX * scale).toFloat()
        fun y(pageY: Double): Float = top + (pageY * scale).toFloat()
    }

    private fun pageMap(zoom: Float = 1f): PageMap {
        val scale = (VIEW_W / A4_WIDTH_PT).toFloat() * zoom
        val left = (maxOf(VIEW_W.toFloat(), A4_WIDTH_PT.toFloat() * scale) - A4_WIDTH_PT.toFloat() * scale) / 2f
        return PageMap(scale, left, GAP_PX)
    }

    private fun onView(body: (DrawingSurfaceView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = DrawingSurfaceView(instrumentation.targetContext)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_H, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, VIEW_W, VIEW_H)
            view.load(blankDocument())
            body(view)
        }
        instrumentation.waitForIdleSync()
    }

    /** Down -> a few moves -> up, all with the given [toolType] (and optional held [buttonState]). */
    private fun drawLine(view: DrawingSurfaceView, toolType: Int, buttonState: Int = 0) {
        val downTime = SystemClock.uptimeMillis()
        var x = 200f
        val y = 300f
        send(view, downTime, downTime, MotionEvent.ACTION_DOWN, floatArrayOf(x), floatArrayOf(y), intArrayOf(toolType), buttonState)
        repeat(5) {
            x += 30f
            send(view, downTime, now(), MotionEvent.ACTION_MOVE, floatArrayOf(x), floatArrayOf(y), intArrayOf(toolType), buttonState)
        }
        send(view, downTime, now(), MotionEvent.ACTION_UP, floatArrayOf(x), floatArrayOf(y), intArrayOf(toolType), buttonState)
    }

    /** One press-and-release of the barrel button while the stylus hovers above the glass. */
    private fun hoverClick(view: DrawingSurfaceView) {
        val t = SystemClock.uptimeMillis()
        hover(view, t, MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY)
        hover(view, t, MotionEvent.ACTION_HOVER_MOVE, 0)
    }

    /** Dispatch a single-pointer stylus hover event carrying [buttonState]. */
    private fun hover(view: View, downTime: Long, action: Int, buttonState: Int) {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = 200f; y = 300f; pressure = 0f; size = 1f },
        )
        val event = MotionEvent.obtain(
            downTime, now(), action, 1, props, coords,
            0, buttonState, 1f, 1f, 0, 0, 0x00004002 /* SOURCE_STYLUS */, 0,
        )
        try {
            view.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Build and dispatch a multi-pointer [MotionEvent] with explicit tool types and button state. */
    private fun send(
        view: View,
        downTime: Long,
        eventTime: Long,
        action: Int,
        xs: FloatArray,
        ys: FloatArray,
        toolTypes: IntArray,
        buttonState: Int = 0,
        actionIndex: Int = 0,
        pressures: FloatArray? = null,
    ) {
        val n = xs.size
        val props = Array(n) { i ->
            MotionEvent.PointerProperties().apply { id = i; this.toolType = toolTypes[i] }
        }
        val coords = Array(n) { i ->
            MotionEvent.PointerCoords().apply {
                x = xs[i]
                y = ys[i]
                // A real digitiser reports pressure per sample; every caller that doesn't care gets
                // a full press, exactly as before this parameter existed.
                pressure = pressures?.get(i) ?: 1f
                size = 1f
            }
        }
        val maskedAction = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(
            downTime, eventTime, maskedAction, n, props, coords,
            0, buttonState, 1f, 1f, 0, 0, 0x00004002 /* InputDevice.SOURCE_STYLUS */, 0,
        )
        try {
            view.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun now() = SystemClock.uptimeMillis()

    private fun strokesOf(view: DrawingSurfaceView): List<Stroke> =
        view.toDocument().pages.flatMap { p -> p.layers.flatMap { it.elements } }.filterIsInstance<Stroke>()

    private fun blankDocument(): Document =
        Document(
            pages = listOf(
                Page(A4_WIDTH_PT, A4_HEIGHT_PT, Background.Solid(0xFFFFFFFF.toInt(), "graph"), listOf(Layer(emptyList()))),
            ),
        )

    private companion object {
        const val A4_WIDTH_PT = 595.276
        const val A4_HEIGHT_PT = 841.89
        /** Mirrors `DrawingSurfaceView.ZOOM_STEP`, which is private to the view. */
        const val ZOOM_STEP = 1.25f
        /** Mirrors `DrawingSurfaceView.GAP_PX`. */
        const val GAP_PX = 24f

        /** The key code an Honor Choice Pencil sends for its side button (see `isVendorKeycode`). */
        const val VENDOR_BARREL_KEY_CODE = 755
        const val VIEW_W = 1080
        const val VIEW_H = 1920
    }
}
