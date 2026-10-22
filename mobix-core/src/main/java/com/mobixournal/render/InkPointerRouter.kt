package com.mobixournal.render

import kotlin.math.hypot

/**
 * What the router decided a stream of [InkPointer] means. A shell turns each event into its own effect
 * — ink on the page, a tapped dot, a viewport move — so the **decision** is written once here and the
 * **doing** stays where the model and the layout are.
 */
sealed interface InkEvent {

    /** A drawing contact passed the tap slop: from here on it is a stroke, starting where it went down. */
    data class StrokeStart(val start: InkPointer, val widthFactor: Float) : InkEvent

    /** One more sample of the stroke in progress, already through the pressure filter. */
    data class StrokeSample(val sample: InkPointer, val widthFactor: Float) : InkEvent

    /** The stroke's contact lifted: commit it. */
    data class StrokeEnd(val end: InkPointer) : InkEvent

    /** A contact that went down and came back up without moving: a tap, which is not a stroke. */
    data class Tap(val at: InkPointer) : InkEvent

    /** An eraser contact, at the view point it landed on. The caller maps it to a page and cuts. */
    data class Erase(val x: Float, val y: Float) : InkEvent

    /** Two contacts are down: navigation starts, and any stroke in progress has been ended. */
    data object PanBegin : InkEvent

    /** The finger focus moved by this much, in view pixels. */
    data class Pan(val dx: Float, val dy: Float) : InkEvent

    /**
     * The two contacts' span changed: a pinch. [factor] is `newSpan / oldSpan`, so a value above 1 is
     * fingers moving **apart** — a zoom in — and the focus is where it happened.
     */
    data class Zoom(val factor: Float, val focusX: Float, val focusY: Float) : InkEvent

    /** Nothing is down any more: drop any preview the shell was drawing. */
    data object GestureEnd : InkEvent
}

/**
 * Turns a stream of [InkPointer] into the events an editor consumes — **one implementation of the
 * decisions both shells need**, so the desktop window does not grow a second opinion about what a
 * second finger means or how hard a press draws.
 *
 * What it decides, and where each rule comes from:
 *
 *  - **Which gesture a contact begins** is [InputClassifier]'s answer, unchanged: the pen tip and the
 *    eraser tip outrank the toolbar, a held barrel applies its configured action, a finger with
 *    *finger draws* off only navigates, and otherwise the active tool decides.
 *  - **How hard it draws** is [PressureCurve]'s filter — desktop Xournal++'s
 *    `max(minimumPressure, pressure × multiplier)`, with no curve and no floor of its own. The router
 *    emits the factor and the shell multiplies its own tool width, so the same press stores the same
 *    width in both shells (`docs/stylus.md`).
 *  - **A tap is not a stroke.** The down is held until the contact moves past the slop; a contact that
 *    never does is a [InkEvent.Tap] on release. A shell that wants the tablet's "a tap leaves a dot"
 *    behaviour asks for it with [startOnDown], which starts the stroke on the press instead.
 *  - **A second finger navigates.** It ends the stroke in progress — no tap, no commit surprise — and
 *    starts a pan; while two or more contacts are down the router reports [InkEvent.Pan] and
 *    [InkEvent.Zoom] and draws nothing. Palm rejection is the same rule seen from the other side: once
 *    a **pen** owns the gesture, a later finger is ignored entirely, and a pen arriving while a finger
 *    gesture is running takes it over.
 *  - **An eraser contact is a stream of hits.** The router reports where the eraser *is*; cutting a page
 *    there is [PageEraser]'s job, so partial-erase geometry has one implementation
 *    (`PageEraser.erase`, which splits a stroke at the hit point).
 *
 * Where each half of the delivery sits: the Android adapter (a `MotionEvent` to [InkPointer], in
 * `render/MotionEventPointer.kt`) fills the fields from the platform's tool type, pressure and axes;
 * the **desktop's Compose pointer becomes the second adapter** in
 * `desktop-editing-undo-and-save` → `desktop-pressure-inking-through-the-pointer-router`, and which of
 * those fields Compose Desktop actually fills on Windows is *measured* by `desktop-tablet-and-dial-input`
 * rather than assumed here — the router therefore treats every optional field as genuinely optional.
 *
 * The router is deliberately unaware of pages, layers and undo: it answers "what did the user mean",
 * and the caller answers "on what, and how is it recorded".
 */
class InkPointerRouter(
    /** Input-layer preferences (finger draws, barrel action) — the same value the classifier reads. */
    private val settings: InputSettings = InputSettings(),
    /** The on-screen tool, asked on every press because the toolbar can change mid-gesture. */
    private val activeTool: () -> ActiveTool = { ActiveTool.PEN },
    /** The pressure filter's three settings (`docs/stylus.md` → *Pressure → width*). */
    private val pressureEnabled: Boolean = true,
    private val pressureMultiplier: Float = 1f,
    private val minimumPressure: Float = PressureCurve.MINIMUM_PRESSURE_DEFAULT,
    /** How far a contact may travel and still count as a tap, in view pixels. */
    private val tapSlopPx: Float = DEFAULT_TAP_SLOP_PX,
    /**
     * True for a shell where a tap is ink (the tablet leaves a dot at the tip); false where a tap is a
     * gesture and must not draw (the desktop, where a click selects).
     */
    private val startOnDown: Boolean = false,
) {

    companion object {
        /**
         * The slop a tap may wander, in view pixels. The app's own placement/tap constant, and above
         * Android's touch slop on a tablet's density: a hand that means to tap wobbles by about this,
         * and a hand that means to draw leaves it within a sample or two.
         */
        const val DEFAULT_TAP_SLOP_PX = 12f

        /** A span below this is two fingers on top of each other, not a pinch. */
        private const val MIN_SPAN_PX = 8f

        /** Pinch jitter: a factor this close to 1 is not a zoom the user meant. */
        private const val ZOOM_DEADBAND = 0.01f
    }

    /** Contacts down right now, in the order they arrived — the second finger is "the one that is not the first". */
    private val down = LinkedHashMap<Int, InkPointer>()

    /** The contact the current gesture belongs to (a stroke, an erase or a tap candidate). */
    private var owner: Int? = null

    private var drawing = false

    /** True once the stroke really started; false while the press is still a tap candidate. */
    private var started = false

    private var erasing = false

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    /** The press itself, kept because a stroke starts *there* — the slop-crossing sample is later. */
    private var origin: InkPointer? = null

    /** The last two-finger focus and span, so a pan reports a delta and a pinch a factor. */
    private var focusX = 0f
    private var focusY = 0f
    private var span = 0f

    /** True while two or more contacts are navigating. */
    val navigating: Boolean get() = down.size >= 2

    /** True while a stroke this router started is being drawn. */
    val inStroke: Boolean get() = drawing

    /**
     * Feed one sample and get everything it means — usually one event, sometimes none (a move inside
     * the tap slop), sometimes several (a second finger ending a stroke and starting a pan).
     */
    fun onPointer(pointer: InkPointer): List<InkEvent> = when (pointer.phase) {
        InkPointerPhase.DOWN -> onDown(pointer)
        InkPointerPhase.MOVE -> onMove(pointer)
        InkPointerPhase.UP -> onUp(pointer)
        InkPointerPhase.CANCEL -> onCancel(pointer)
    }

    /** Forget everything, as a window losing the pointer must: no commit, no tap. */
    fun reset(): List<InkEvent> {
        val wasActive = down.isNotEmpty()
        clear()
        return if (wasActive) listOf(InkEvent.GestureEnd) else emptyList()
    }

    // --- the contact's life ----------------------------------------------------------------------

    private fun onDown(pointer: InkPointer): List<InkEvent> {
        // A pen outranks a hand that landed first: the palm's gesture is abandoned, not finished, so a
        // resting hand can never stop the pen from writing (`docs/stylus.md` → *Palm rejection*).
        if (pointer.isPen && down.isNotEmpty() && down.values.none { it.isPen }) {
            clear()
            down[pointer.id] = pointer
            return listOf(InkEvent.GestureEnd) + begin(pointer)
        }
        // While a pen owns the gesture, fingers are a resting hand: ignored entirely.
        if (down.values.any { it.isPen } && !pointer.isPen) return emptyList()
        down[pointer.id] = pointer
        if (down.size >= 2) return beginNavigation()
        return begin(pointer)
    }

    /** Begin the gesture the classifier names for [pointer]. */
    private fun begin(pointer: InkPointer): List<InkEvent> {
        owner = pointer.id
        origin = pointer
        downX = pointer.x
        downY = pointer.y
        downTime = pointer.timeMs
        started = false
        erasing = false
        drawing = false
        val intent = InputClassifier.classify(
            kind = pointer.tool,
            barrelPressed = pointer.hasSecondaryButton,
            activeTool = activeTool(),
            settings = settings,
        )
        return when (intent) {
            GestureIntent.ERASE -> {
                erasing = true
                listOf(InkEvent.Erase(pointer.x, pointer.y))
            }
            GestureIntent.DRAW -> {
                drawing = true
                if (startOnDown) {
                    started = true
                    listOf(InkEvent.StrokeStart(pointer, widthFactor(pointer)))
                } else {
                    emptyList()
                }
            }
            // PAN, SELECT, PLACE and the rest are the shell's own gestures: it asked the same
            // classifier for the very same answer, so the router stays out of their way.
            else -> emptyList()
        }
    }

    private fun onMove(pointer: InkPointer): List<InkEvent> {
        if (pointer.id !in down) return emptyList()
        down[pointer.id] = pointer
        if (down.size >= 2) return navigate()
        if (pointer.id != owner) return emptyList()
        if (erasing) return listOf(InkEvent.Erase(pointer.x, pointer.y))
        if (!drawing) return emptyList()
        if (!started) {
            val moved = hypot(pointer.x - downX, pointer.y - downY)
            if (moved <= tapSlopPx) return emptyList()
            started = true
            val start = origin ?: pointer
            return listOf(
                InkEvent.StrokeStart(start, widthFactor(start)),
                InkEvent.StrokeSample(pointer, widthFactor(pointer)),
            )
        }
        return listOf(InkEvent.StrokeSample(pointer, widthFactor(pointer)))
    }

    private fun onUp(pointer: InkPointer): List<InkEvent> {
        val known = pointer.id in down
        down.remove(pointer.id)
        if (!known) return emptyList()
        if (down.size >= 2) return navigate()
        if (down.isNotEmpty()) {
            // The navigation contact that is still down keeps navigating; the span is re-baselined so
            // the drop of a finger is not read as a pinch.
            rebaseline()
            return emptyList()
        }
        val events = when {
            pointer.id != owner -> emptyList()
            erasing -> listOf(InkEvent.GestureEnd)
            drawing && started -> listOf(InkEvent.StrokeEnd(pointer))
            drawing -> listOf(InkEvent.Tap(pointer))
            else -> listOf(InkEvent.GestureEnd)
        }
        clear()
        // A gesture that was navigating ends with the last contact, whether or not it drew anything.
        return if (events.isEmpty()) listOf(InkEvent.GestureEnd) else events
    }

    private fun onCancel(pointer: InkPointer): List<InkEvent> {
        val known = pointer.id in down
        down.remove(pointer.id)
        if (!known) return emptyList()
        if (down.isNotEmpty()) {
            rebaseline()
            return emptyList()
        }
        clear()
        return listOf(InkEvent.GestureEnd)
    }

    // --- navigation ------------------------------------------------------------------------------

    /** A second contact arrived: end whatever the first was doing, then start navigating. */
    private fun beginNavigation(): List<InkEvent> {
        val events = ArrayList<InkEvent>(2)
        if (drawing || erasing) events += InkEvent.GestureEnd
        owner = null
        drawing = false
        erasing = false
        started = false
        rebaseline()
        events += InkEvent.PanBegin
        return events
    }

    private fun navigate(): List<InkEvent> {
        val points = down.values.toList()
        if (points.size < 2) return emptyList()
        val nx = (points[0].x + points[1].x) / 2f
        val ny = (points[0].y + points[1].y) / 2f
        val ns = hypot(points[0].x - points[1].x, points[0].y - points[1].y)
        val events = ArrayList<InkEvent>(2)
        events += InkEvent.Pan(nx - focusX, ny - focusY)
        if (span > MIN_SPAN_PX && ns > MIN_SPAN_PX) {
            val factor = ns / span
            if (kotlin.math.abs(factor - 1f) > ZOOM_DEADBAND) events += InkEvent.Zoom(factor, nx, ny)
        }
        focusX = nx
        focusY = ny
        span = ns
        return events
    }

    /** Take the current finger focus and span as the zero of the next frame. */
    private fun rebaseline() {
        val points = down.values.toList()
        if (points.size < 2) {
            span = 0f
            focusX = points.firstOrNull()?.x ?: 0f
            focusY = points.firstOrNull()?.y ?: 0f
            return
        }
        focusX = (points[0].x + points[1].x) / 2f
        focusY = (points[0].y + points[1].y) / 2f
        span = hypot(points[0].x - points[1].x, points[0].y - points[1].y)
    }

    private fun clear() {
        down.clear()
        origin = null
        owner = null
        drawing = false
        erasing = false
        started = false
        span = 0f
    }

    /** The pressure filter: one press becomes one width, by desktop Xournal++'s own mapping. */
    private fun widthFactor(pointer: InkPointer): Float =
        PressureCurve.penFactor(pointer.pressure, pressureEnabled, pressureMultiplier, minimumPressure)

}
