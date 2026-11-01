/**
 * **Which gesture a move belongs to**, decided in one place and answered as a value.
 *
 * A canvas runs many gestures at once, and they are not exclusive: a guide stays posed while the pen
 * rules along it, a pen's barrel can switch a stroke to the eraser mid-gesture, and the marquee's edge
 * band scrolls the page under a finger that has not let go. So every `ACTION_MOVE` has to ask "what am I
 * moving *now*?" — and the answer is a **priority order** that was until now implicit in the middle of a
 * thirteen-branch `when` inside an event handler, where it could only be checked by driving a device.
 *
 * The order is the point of this file:
 *
 *  1. **[CanvasGesture.PAN]** — a two-finger pan owns the gesture outright; everything else on the page
 *     is frozen while it runs.
 *  2. **[CanvasGesture.ERASE]** — the eraser is a mode of its own, and it beats every edit below it.
 *  3. **[CanvasGesture.PLACE]** — placing a new element.
 *  4. **[CanvasGesture.BACKGROUND_SELECT]** — the paper-region marquee (a background image, not ink).
 *  5. **[CanvasGesture.SLIDE_PARAM]**, **[DIVIDER]**, **[VERTEX]** — a plot slider, a table divider and a
 *     polygon vertex are the three *point* handles of the selection, and each is more specific than the
 *     selection's own drag.
 *  6. **[RESIZE]**, **[ROTATE]**, **[MOVE]** — the selection's body.
 *  7. **[VERTICAL_SPACE]** — the grab line, which is its own tool.
 *  8. **[BAND]** — the marquee being dragged (which also auto-scrolls).
 *  9. **[TEXT_SELECT]** — the PDF text range.
 *  10. **[INK]** — the stroke in progress, only when the shared router owns this gesture.
 *  11. **[NONE]** — a move nothing is listening to (a stationary tap candidate, say).
 *
 * The router is pure: [GestureSituation] is a snapshot of the canvas's modes and [route] is a function of
 * it, so the whole ordering is unit-tested (`GestureRouterTest`) instead of being inferred from a gesture
 * that happens to work on a tablet. The canvas keeps the *dispatch* — each branch is a call with its own
 * effects — and the framework's own action switch stays an action switch, because that is not a gesture
 * decision.
 */
package com.mobixournal.render

/** A gesture a move can belong to, in priority order (see the file note). */
internal enum class CanvasGesture {
    PAN,
    ERASE,
    PLACE,
    BACKGROUND_SELECT,
    SLIDE_PARAM,
    DIVIDER,
    VERTEX,
    RESIZE,
    ROTATE,
    MOVE,
    VERTICAL_SPACE,
    BAND,
    TEXT_SELECT,
    INK,
    NONE,
}

/** The canvas's modes at one instant — everything [GestureRouter.route] is allowed to look at. */
internal class GestureSituation(
    val panning: Boolean = false,
    val erasing: Boolean = false,
    val placing: Boolean = false,
    val backgroundSelecting: Boolean = false,
    val slidingParam: Boolean = false,
    val draggingDivider: Boolean = false,
    val draggingVertex: Boolean = false,
    val resizing: Boolean = false,
    val rotating: Boolean = false,
    val movingSelection: Boolean = false,
    val verticalSpace: Boolean = false,
    val banding: Boolean = false,
    val textSelecting: Boolean = false,
    /** True when the shared pointer router owns this gesture *and* it has ink in progress. */
    val routingInk: Boolean = false,
)

internal object GestureRouter {

    /** The gesture this move belongs to — the first match in the file's priority order. */
    fun route(situation: GestureSituation): CanvasGesture = when {
        situation.panning -> CanvasGesture.PAN
        situation.erasing -> CanvasGesture.ERASE
        situation.placing -> CanvasGesture.PLACE
        situation.backgroundSelecting -> CanvasGesture.BACKGROUND_SELECT
        situation.slidingParam -> CanvasGesture.SLIDE_PARAM
        situation.draggingDivider -> CanvasGesture.DIVIDER
        situation.draggingVertex -> CanvasGesture.VERTEX
        situation.resizing -> CanvasGesture.RESIZE
        situation.rotating -> CanvasGesture.ROTATE
        situation.movingSelection -> CanvasGesture.MOVE
        situation.verticalSpace -> CanvasGesture.VERTICAL_SPACE
        situation.banding -> CanvasGesture.BAND
        situation.textSelecting -> CanvasGesture.TEXT_SELECT
        situation.routingInk -> CanvasGesture.INK
        else -> CanvasGesture.NONE
    }
}
