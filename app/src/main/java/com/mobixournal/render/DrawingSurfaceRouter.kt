/**
 * [DrawingSurfaceView]'s half of `give-both-shells-one-pointer-router`: the **adapter** that turns a
 * `MotionEvent` into the shared [InkPointer] vocabulary and carries the router's answers onto the
 * surface.
 *
 * The decisions are not made here. [InkPointerRouter] (`mobix-core/render`) owns them: when a press
 * becomes a stroke instead of a tap, that a tap on this shell leaves a dot (`startOnDown`), that a
 * second finger ends the stroke it interrupts and starts a pan, that a pen outranks a hand that landed
 * first and that a hand under a writing pen is a resting palm. The companion window feeds the same
 * router from Compose's pointer (`desktop/PageCanvasInput.kt`), which is what makes the two shells one
 * input layer instead of two opinions (`docs/stylus.md`).
 *
 * **Routed** (this file plus [DrawingSurfaceStrokes]' `…From(pointer)` half): the stroke of every
 * drawing tool including the shape tools, its samples — historical ones too, so the batch's pressure
 * ramp reaches the router — the tap that leaves a dot, the eraser drag, and the second-finger pan.
 *
 * **Not routed**, each keeping the entry point it already had, because none of them is a stroke:
 * barrel keys and vendor keys (`DrawingSurfaceBarrel.kt`), the barrel pressed *mid*-stroke
 * (`switchStrokeToErase`), page drag and its arm timer, guide drag, spline taps, table/plot
 * double-taps, audio play mode, vertical space, background select and the marquee.
 */
package com.mobixournal.render

import android.view.MotionEvent

/**
 * A router for the gesture that is starting, carrying the settings in force *now*.
 *
 * Built per gesture rather than once per surface: the pressure filter and the finger-draw/barrel
 * settings are `var`s the editor pushes from Settings, and a router that captured them at
 * construction would keep deciding with the values of the first stroke of the session.
 *
 * The tool is a lambda instead because the toolbar can change between contacts while the router
 * lives across the gesture ([DrawingSurfaceView.activeTool]).
 */
internal fun DrawingSurfaceView.newPointerRouter(): InkPointerRouter = InkPointerRouter(
    settings = inputSettings,
    activeTool = { activeTool() },
    pressureEnabled = pressureEnabled,
    pressureMultiplier = pressureMultiplier,
    minimumPressure = minimumPressure,
    // A tap on the tablet leaves a dot at the tip, so the press is ink at once. (The desktop wants
    // the opposite — there a click selects — and asks for it with `startOnDown = false`.)
    startOnDown = true,
)

/**
 * Feed one sample of [event]'s pointer at [pointerIndex] to the router and do what it says.
 *
 * [history] names a historical sample of a MOVE batch (`0 until historySize`); `-1` is the event's own
 * newest sample. [force] is passed to the smoother: the newest sample of a batch always lands as a
 * vertex, so the drawn line reaches the pen (`StrokeSmoother.accept`).
 */
internal fun DrawingSurfaceView.routePointer(
    event: MotionEvent,
    pointerIndex: Int,
    phase: InkPointerPhase,
    history: Int = -1,
    force: Boolean = true,
) {
    val pointer = event.inkPointer(pointerIndex, phase, barrelButton.held, history)
    for (inkEvent in pointerRouter.onPointer(pointer)) applyInkEvent(inkEvent, pointer, event, force)
}

/**
 * Feed a whole MOVE batch: its historical samples first, then its newest. The batch is exactly what a
 * fast pen produces — the platform coalesces a 100–240 Hz digitiser into one `ACTION_MOVE` — so
 * emitting one [InkPointer] per historical point is what keeps the pressure ramp in the file rather
 * than flattening a stroke to the few samples the batch happens to end on.
 *
 * Only a **stroke** takes the historical samples. An eraser hit is not a sample of anything: it is a
 * page edit, a relayout and a repaint, so one bite per event is plenty and ten per frame would be a
 * visible stutter under a fast rub.
 */
internal fun DrawingSurfaceView.feedPointerSamples(event: MotionEvent) {
    val index = event.findPointerIndex(gesturePointerId)
    if (index < 0) return
    if (current != null || currentStrokes != null) {
        for (h in 0 until event.historySize) {
            routePointer(event, index, InkPointerPhase.MOVE, history = h, force = false)
        }
    }
    routePointer(event, index, InkPointerPhase.MOVE, force = true)
}

/**
 * One router answer, as an effect on the surface. [pointer] is the sample that produced it (the
 * eraser's first hit fixes the gesture on its pointer id), and [motion] is the event a pan
 * re-baselines from.
 */
private fun DrawingSurfaceView.applyInkEvent(
    event: InkEvent,
    pointer: InkPointer,
    motion: MotionEvent,
    force: Boolean,
) {
    when (event) {
        is InkEvent.StrokeStart -> startStrokeFrom(pointer)
        is InkEvent.StrokeSample -> extendStrokeFrom(pointer, force)
        is InkEvent.Erase -> eraseAtPointer(pointer)
        // A second contact: the stroke in progress is dropped (not committed) and the page pans, which
        // is what a two-finger gesture means on every touch app. The view's own `doScroll` carries the
        // deltas — the router only says navigation has begun.
        is InkEvent.PanBegin -> beginScroll(motion)
        // The lift is the view's own `endGesture`: one commit path, one undo step, one place where
        // momentum is launched. With `startOnDown` a tap never reaches here as `Tap` — it is already a
        // stroke — and the router's own `GestureEnd` is teardown the view has done by the same act.
        is InkEvent.StrokeEnd, is InkEvent.Tap, InkEvent.GestureEnd -> Unit
        is InkEvent.Pan, is InkEvent.Zoom -> Unit
    }
}
