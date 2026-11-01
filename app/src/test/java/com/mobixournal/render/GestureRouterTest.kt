package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gesture priority, as a value: which gesture a move belongs to when several modes are on at once.
 *
 * The order in [GestureRouter]'s file note is the specification, and `CanvasGesture`'s declaration order
 * is that same order — so the cascade test below reads the two against each other instead of restating
 * the table a third time.
 */
class GestureRouterTest {

    /** A situation with exactly the modes named — the constructor is the mapping under test's input. */
    private fun situation(vararg on: CanvasGesture): GestureSituation = GestureSituation(
        panning = CanvasGesture.PAN in on,
        erasing = CanvasGesture.ERASE in on,
        placing = CanvasGesture.PLACE in on,
        backgroundSelecting = CanvasGesture.BACKGROUND_SELECT in on,
        slidingParam = CanvasGesture.SLIDE_PARAM in on,
        draggingDivider = CanvasGesture.DIVIDER in on,
        draggingVertex = CanvasGesture.VERTEX in on,
        resizing = CanvasGesture.RESIZE in on,
        rotating = CanvasGesture.ROTATE in on,
        movingSelection = CanvasGesture.MOVE in on,
        verticalSpace = CanvasGesture.VERTICAL_SPACE in on,
        banding = CanvasGesture.BAND in on,
        textSelecting = CanvasGesture.TEXT_SELECT in on,
        routingInk = CanvasGesture.INK in on,
    )

    private val ordered: List<CanvasGesture> = CanvasGesture.values().filter { it != CanvasGesture.NONE }

    @Test
    fun `nothing in progress means the move belongs to no gesture`() {
        assertEquals(CanvasGesture.NONE, GestureRouter.route(situation()))
    }

    @Test
    fun `each mode alone routes to itself`() {
        for (gesture in ordered) {
            assertEquals("$gesture is listening on its own", gesture, GestureRouter.route(situation(gesture)))
        }
    }

    @Test
    fun `each gesture beats the one after it in the declared order`() {
        for (index in 0 until ordered.lastIndex) {
            val higher = ordered[index]
            val lower = ordered[index + 1]
            assertEquals(
                "$higher must win over $lower",
                higher,
                GestureRouter.route(situation(higher, lower)),
            )
        }
    }

    @Test
    fun `a pan wins over everything at once`() {
        assertEquals(
            CanvasGesture.PAN,
            GestureRouter.route(situation(*ordered.toTypedArray())),
        )
    }

    @Test
    fun `the eraser wins over every edit, and ink over nothing`() {
        assertEquals(
            "the eraser is a mode of its own",
            CanvasGesture.ERASE,
            GestureRouter.route(situation(CanvasGesture.ERASE, CanvasGesture.INK, CanvasGesture.BAND)),
        )
        assertEquals(
            "text selection is decided before the stroke it overlaps",
            CanvasGesture.TEXT_SELECT,
            GestureRouter.route(situation(CanvasGesture.TEXT_SELECT, CanvasGesture.INK)),
        )
    }
}
