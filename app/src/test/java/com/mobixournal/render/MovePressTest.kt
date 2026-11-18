package com.mobixournal.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The press of a live move: a **tap on paper that holds nothing** lets go of the selection.
 *
 * The reading is two numbers — what the press landed on, and how far the hand went — so the rule is pinned
 * here rather than through a `MotionEvent`: the outline encloses air, and a still press in that air is a tap on
 * nothing, while the same press that lands on the element's ink is a press that holds it.
 */
class MovePressTest {

    private val slop = DrawingSurfaceDefaults.TAP_SLOP_PX

    @Test
    fun aStillPressOnNothingLetsGo() {
        val press = MovePress()
        press.begin(100f, 100f, onInk = false)
        assertTrue("nothing was under it and it never travelled", press.tapOnNothing)
    }

    @Test
    fun aStillPressOnTheElementHolds() {
        val press = MovePress()
        press.begin(100f, 100f, onInk = true)
        assertFalse("the ink under it is what the press holds", press.tapOnNothing)
    }

    @Test
    fun aPressThatTravelledIsADrag() {
        val press = MovePress()
        press.begin(100f, 100f, onInk = false)
        press.track(100f + slop + 1f, 100f)
        assertFalse("a hand that moved is carrying the selection, not letting go", press.tapOnNothing)
    }

    @Test
    fun aHandsJitterIsStillATap() {
        val press = MovePress()
        press.begin(100f, 100f, onInk = false)
        press.track(100f + slop / 2f, 100f - slop / 2f)
        assertTrue("under the tap slop it is the same tap", press.tapOnNothing)
    }

    @Test
    fun aFreshPressForgetsTheLastOne() {
        val press = MovePress()
        press.begin(100f, 100f, onInk = false)
        press.track(400f, 400f)
        assertFalse("that press was a drag", press.tapOnNothing)
        press.begin(40f, 40f, onInk = false)
        assertTrue("the next one starts where it goes down", press.tapOnNothing)
    }
}
