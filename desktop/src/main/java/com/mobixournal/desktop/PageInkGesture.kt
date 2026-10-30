package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.input.pointer.PointerInputChange
import com.mobixournal.audio.withAudio
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.InkEvent
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.InkPointerRouter
import com.mobixournal.render.PageEraser
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.PointerKind
import com.mobixournal.render.RecognisedFigure
import com.mobixournal.render.ShapeDwell
import kotlin.math.hypot
import com.mobixournal.render.eraserRadiusPt

/**
 * One drag, as the shared router sees it: the samples collected so far and the page they are being
 * drawn on.
 *
 * [drawing] is the router's own "a stroke really started" state *and* the sheet check — a press that
 * lands on the desk starts nothing, because the desk is not paper. The samples are page points already,
 * so the canvas can paint them every frame and the lift can hand them straight to [strokeOf].
 */
internal class PageInkGesture(
    private val tab: DesktopTab,
    private val tool: DesktopTool,
    private val ink: DesktopInk,
    /**
     * The viewport in force **now**, not the one at press time: a tablet packet can arrive after a
     * zoom, and the page point it maps to must be the one the reader is looking at.
     */
    private val view: () -> Viewport,
    private val pageW: Float,
    private val pageH: Float,
    private val density: Float,
    private val draft: SnapshotStateList<InkSample>,
    private val onEdited: (Int) -> Unit,
    /** The pen's own reading, when the window has a stream — see [inkPointer]. */
    private val pen: PenContactStream? = null,
    /**
     * Whether the press itself opens the stroke.
     *
     * False for the window's mouse: a click selects, so a drag has to cross the tap slop before it is
     * ink. **True for the tablet's packets**, because there a press is a pen on paper — a touch that
     * never travels is a dot, exactly as it is on the tablet, and a very light stroke is no longer
     * discarded for having moved less than a mouse's slop.
     */
    private val startOnDown: Boolean = false,
) {
    private val pageWidth = pageW.toDouble()
    private val pageHeight = pageH.toDouble()
    // The pressure filter is read per sample in `PressureFeel` (sensitivity, floor, multiplier and the
    // curve), so this router hands the reading straight through: a factor floored or multiplied here as
    // well would apply the same setting twice.
    private val router = InkPointerRouter(
        activeTool = { tool.activeTool() },
        startOnDown = startOnDown,
        pressureEnabled = true,
        pressureMultiplier = 1f,
        minimumPressure = 0f,
    )

    /** True while a stroke this gesture started is on the sheet and will be committed. */
    var drawing: Boolean = false
        private set

    /** True once this gesture has taken its one undo step: an eraser drag is one step, not one per bite. */
    private var recorded = false

    /**
     * The figure a **dwell** put on the sheet, and the hand's own samples it replaced.
     *
     * Null whenever the preview is the ink as drawn. The pair is what makes the snap a preview and not a
     * rewrite: a stroke that grows after it gets [drawnSamples] back, and the lift commits the figure
     * that was found rather than re-recognising its clean geometry (four corners plus a closing repeat is
     * a round set for the circle fit, which is how a held square used to come back a circle).
     */
    private var dwellFigure: RecognisedFigure? = null
    private var drawnSamples: List<InkSample> = emptyList()

    /**
     * When the tip last **moved** ([ShapeDwell.SLOP_PT] away from the anchor), on the monotonic clock
     * the dwell is measured with — and where that anchor is.
     *
     * Not the newest sample's age: a Wintab driver keeps sending packets while the pen rests on the
     * glass, at its own rate and with the same position, so stamping the clock on every sample made the
     * dwell impossible to reach — the tip was reported "resting" only because no packet arrived, which
     * on a real tablet is the one thing it does send. A repeat sample inside the slop leaves the clock
     * alone, exactly as it leaves the slate's timer armed (`docs/shape-perfection-engine.md`).
     */
    private var lastSampleNanos = 0L
    private var anchorX = 0.0
    private var anchorY = 0.0

    /** The bites gathered for this frame: one page edit per frame, not one per pointer move. */
    private val eraser = EraseBatch()

    fun down(change: PointerInputChange, buttons: Int) {
        recorded = false
        feed(change, InkPointerPhase.DOWN, buttons)
    }

    fun move(change: PointerInputChange, buttons: Int) = feed(change, InkPointerPhase.MOVE, buttons)

    fun up(change: PointerInputChange, buttons: Int) {
        dwellCheck()
        feed(change, InkPointerPhase.UP, buttons)
        // The last few bites of a drag are always applied: a lift never leaves ink half rubbed out.
        flushErase()
    }

    /**
     * A sample that is already in the router's vocabulary — the tablet's own packet, in view pixels.
     *
     * This is the entry point a stroke is drawn from when the pen's stream exists: the packet carries
     * its own position and pressure, so nothing here depends on the mouse message Windows may or may not
     * have sent for the same contact.
     */
    fun down(pointer: InkPointer) {
        recorded = false
        handle(router.onPointer(pointer.copy(phase = InkPointerPhase.DOWN)))
    }

    fun move(pointer: InkPointer) = handle(router.onPointer(pointer.copy(phase = InkPointerPhase.MOVE)))

    fun up(pointer: InkPointer) {
        // A hand that rested and then lifted may send no further sample, so the dwell is offered here
        // too — before the lift, so the figure is what the commit sees.
        dwellCheck()
        handle(router.onPointer(pointer.copy(phase = InkPointerPhase.UP)))
        flushErase()
    }

    /**
     * Offer a resting stroke to the recogniser and put the figure on the sheet as a **preview**.
     *
     * Rest is the one event with no sample to wait for, so this is called from the canvas's silent
     * timer (`PageCanvas`), from the packet path's flush, and once before a lift. A no-op while the
     * stroke is still growing, while a preview is already on the sheet, and for a tool that lays no ink.
     *
     * The preview is the **same draft list** the sheet paints, so what is previewed is what the lift
     * writes: the figure's own points, all at the width the stroke was drawn at.
     */
    fun dwellCheck(nowNanos: Long = System.nanoTime()) {
        if (!drawing || dwellFigure != null) return
        if (nowNanos - lastSampleNanos < ShapeDwell.MS_NANOS) return
        if (tool.inkTool() == null) return
        val samples = draft.toList()
        if (samples.size < 3) return
        val width = ink.width.toDouble()
        val points = samples.map { StrokePoint(it.x, it.y, width * it.factor) }
        val average = points.map { it.width }.average()
        val figure = ShapeDwell.figureFor(points, average) ?: return
        drawnSamples = samples
        dwellFigure = figure
        // `strokeOf` sizes a point from the tool's width times the sample's factor, so the factor is
        // the average the recogniser was given back over that width.
        val factor = if (width > 0.0) (average / width).toFloat() else 1f
        val pointer = samples.last().pointer
        draft.clear()
        draft.addAll(figure.points.map { point -> InkSample(point.x, point.y, factor, pointer) })
    }

    /**
     * A gesture that ended without the router saying so leaves no ink behind — **its own** ink.
     *
     * [draft] is this gesture's list and no other path writes to it (`PageCanvas` gives the tablet's
     * packets a draft of their own), so a Compose gesture that stands aside for the pen cannot take a
     * sample away from the stroke the pen is still drawing.
     */
    fun cancel() {
        drawing = false
        draft.clear()
        dwellFigure = null
        drawnSamples = emptyList()
        // A cancelled gesture writes nothing that had not already been swept.
        eraser.clear()
    }

    /**
     * End the stroke where it is, as the lift would — for a stroke whose lift never arrived.
     *
     * The packets' path ends a stroke on the driver's own lift; when that packet does not come, the ink
     * the hand already drew is still the user's, so it is committed at its last sample rather than thrown
     * away. Called from `PenPacketStroke` when the packet stream goes quiet.
     */
    fun finish() {
        if (drawing) {
            dwellCheck()
            commit()
        }
        flushErase()
        cancel()
    }

    private fun feed(change: PointerInputChange, phase: InkPointerPhase, buttons: Int) =
        handle(router.onPointer(inkPointer(change, phase, buttons)))

    /**
     * One Compose pointer, with the pen's own readings folded in where Windows Ink has them.
     *
     * The order matters and is the reason this is one adapter and not two: Windows sends `WM_POINTER`
     * **before** the mouse message it promotes the same contact to, so the newest pen contact belongs to
     * the pointer change being translated. A device that declares no pressure keeps `change.pressure`
     * (which is `1.0`, the tool's width) rather than a zero that would draw nothing, and the eraser end of
     * a pen reclassifies the pointer as [PointerKind.ERASER_TIP] — the router then rubs out, which it
     * would not do for the mouse this driver otherwise looks like.
     */
    private fun inkPointer(change: PointerInputChange, phase: InkPointerPhase, buttons: Int): InkPointer {
        val contact = pen?.contact()
        return InkPointer(
            id = change.id.value.toInt(),
            x = change.position.x,
            y = change.position.y,
            // The reading is filtered once, here, by the one rule both shells share (`PressureFeel`), so
            // the model downstream never applies a second floor or multiplier.
            pressure = PressureFeel.factor(contact?.pressure ?: change.pressure),
            timeMs = change.uptimeMillis,
            tool = if (contact?.eraser == true) PointerKind.ERASER_TIP else change.pointerKind(),
            phase = phase,
            tiltRad = contact?.tiltRad,
            azimuthRad = contact?.azimuthRad,
            buttons = buttons,
        )
    }

    private fun handle(events: List<InkEvent>) {
        for (event in events) {
            when (event) {
                is InkEvent.StrokeStart -> start(event)
                is InkEvent.StrokeSample -> if (drawing) {
                    val sample = event.sample
                    val (x, y) = pagePointOf(sample.x, sample.y, density, view())
                    addSample(x, y, event.widthFactor, sample)
                }
                is InkEvent.StrokeEnd -> {
                    if (drawing) commit()
                    cancel()
                }
                is InkEvent.Erase -> {
                    val (x, y) = pagePointOf(event.x, event.y, density, view())
                    // Several moves arrive per frame; they are queued and swept as one page edit (`EraseBatch`).
                    if (eraser.add(x, y, System.nanoTime())) flushErase()
                }
                is InkEvent.Tap, InkEvent.PanBegin, InkEvent.GestureEnd -> {
                    // The gesture is over: whatever this frame's eraser gathered is still the user's work,
                    // so it is swept before the gesture lets go of it.
                    flushErase()
                    cancel()
                }
                is InkEvent.Pan, is InkEvent.Zoom -> Unit
            }
        }
    }

    /** The press that crossed the tap slop: ink from here on, unless it began on the desk. */
    private fun start(event: InkEvent.StrokeStart) {
        val start = event.start
        val (x, y) = pagePointOf(start.x, start.y, density, view())
        drawing = x in 0.0..pageWidth && y in 0.0..pageHeight
        draft.clear()
        dwellFigure = null
        drawnSamples = emptyList()
        anchorX = 0.0
        anchorY = 0.0
        if (drawing) addSample(x, y, event.widthFactor, start)
    }

    /**
     * One sample into the draft, and the clock the dwell is measured from.
     *
     * A sample arriving while a figure is previewed means the stroke **grew**: the figure was a preview
     * of a stroke that no longer exists, so the hand's own ink comes back and the dwell starts over.
     */
    private fun addSample(x: Double, y: Double, factor: Float, pointer: InkPointer) {
        val moved = draft.isEmpty() || hypot(x - anchorX, y - anchorY) >= ShapeDwell.SLOP_PT
        if (dwellFigure != null) {
            // A driver repeating a resting pen must not undo the preview: the hand has not moved.
            if (!moved) return
            dwellFigure = null
            draft.clear()
            draft.addAll(drawnSamples)
            drawnSamples = emptyList()
        }
        if (moved) {
            anchorX = x
            anchorY = y
            lastSampleNanos = System.nanoTime()
        }
        draft.add(InkSample(x, y, factor, pointer))
    }

    /**
     * The lift: the drawn samples become one `Stroke` on the page's top layer, as one edit — and one
     * undo step, recorded with the page as it was before the stroke.
     */
    private fun commit() {
        val drawnTool = tool.inkTool() ?: return
        // The figure the dwell found, if it is one this shell keeps as living geometry: a closed
        // polygon, drawn with the pen, on a plain line — the same rule the tablet commits by.
        val figure = dwellFigure?.takeIf { drawnTool == Tool.PEN && ink.lineStyle == LineStyle.PLAIN }
        val samples = draft.toList()
        dwellFigure = null
        drawnSamples = emptyList()
        draft.clear()
        val index = tab.page
        val before = tab.document.snapshot()
        val shape = figure?.let { ParametricShapes.elementFor(it, ink.colour, sampleWidth(samples)) }
        val wrote = if (shape != null) {
            tab.document.editPage(index) { page -> page.withElement(shape) } != null
        } else {
            val stroke = strokeOf(drawnTool, ink.colour, ink.width, samples, ink.lineStyle)
                ?: return
            // A stroke drawn while a take runs carries its `fn`/`ts` from the **one funnel** every
            // committed stroke passes through — not per tool, so a highlighter and a pen cannot stamp
            // differently. A paused take stamps nothing, and neither does a take running in **another
            // tab** (`DesktopAudioSession.stamp`, which asks the shared `ownsRecording` rule).
            val stamped = DesktopAudioSession.stamp(tab)?.let { stroke.withAudio(it) } ?: stroke
            tab.document.editPage(index) { page -> page.withElement(stamped) } != null
        }
        if (!wrote) return
        tab.record(before)
        onEdited(index)
    }

    /** The width the drawn samples were laid at — the average the recogniser was handed. */
    private fun sampleWidth(samples: List<InkSample>): Double {
        if (samples.isEmpty()) return ink.width.toDouble()
        return ink.width.toDouble() * samples.map { it.factor.toDouble() }.average()
    }

    /** Apply everything the eraser queued, as **one** page edit and one repaint. */
    private fun flushErase() {
        if (eraser.isEmpty()) return
        eraseAt(eraser.drain())
    }

    /**
     * Rub out at the page points a frame's hand produced, at the tip the ink width sizes
     * (`eraserRadiusPt`).
     *
     * One copy of the page, one sweep per point, one picture re-record: the bites of a single frame used
     * to be separate edits, so a dense sheet paid for two or three pages per frame — the eraser fell
     * behind the hand.
     *
     * The drag's undo step is taken **before its first bite** — a snapshot of a page nobody has touched
     * is cheap, and taking it only once an edit really happened keeps a stroke past the ink from
     * leaving an empty step behind.
     */
    private fun eraseAt(points: List<Pair<Double, Double>>) {
        if (points.isEmpty()) return
        val index = tab.page
        val radius = eraserRadiusPt(ink.width)
        val mode = tool.eraserMode()
        val hidden = tab.hiddenOn(index)
        val before = if (recorded) null else tab.document.snapshot()
        val edited = tab.document.editPage(index) { page ->
            var swept = page
            for ((x, y) in points) {
                swept = PageEraser.erase(
                    page = swept,
                    px = x,
                    py = y,
                    radius = radius,
                    mode = mode,
                    hiddenLayers = hidden,
                ) ?: swept
            }
            swept
        }
        if (edited == null) return
        if (before != null) {
            tab.record(before)
            recorded = true
        }
        onEdited(index)
    }
}
