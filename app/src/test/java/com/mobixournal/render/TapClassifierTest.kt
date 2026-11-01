package com.mobixournal.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tap classifier: what makes a lift a tap, what makes two taps a double-tap, and what disqualifies
 * either.
 *
 * Event times and pixels are fed in directly, so every rule the platform timings encode — the window, the
 * slop, the flick, the "a third tap must not re-fire", the key an element tap is bound to — is checked
 * here without a `View`, a clock or a `MotionEvent`.
 */
class TapClassifierTest {

    private val taps = TapClassifier(
        doubleTapTimeoutMs = 300L,
        doubleTapSlopPx = 40f,
        longPressMs = 500L,
        touchSlopPx = 8f,
    )

    /** One whole press-and-lift, at [x], [y], starting at [timeMs]. */
    private fun tap(timeMs: Long, x: Float = 100f, y: Float = 100f, flicked: Boolean = false): Boolean {
        taps.beginHand(armed = true, timeMs = timeMs, x = x, y = y)
        return taps.endHand(flicked)
    }

    @Test
    fun `one tap is only the first half of a double-tap`() {
        assertFalse(tap(1_000))
        assertTrue(tap(1_150))
        assertTrue("the double-tap routes by where the second tap landed", taps.tapX == 100f)
    }

    @Test
    fun `a third tap does not immediately re-fire`() {
        tap(1_000)
        assertTrue(tap(1_150))

        assertFalse("the pair was consumed by the double-tap", tap(1_200))
        assertTrue("and the next tap starts a new pair", tap(1_300))
    }

    @Test
    fun `a tap too far from the previous one in time does not pair`() {
        assertFalse(tap(1_000))
        assertFalse("301 ms is past the window", tap(1_301))
    }

    @Test
    fun `a tap too far from the previous one in place does not pair`() {
        assertFalse(tap(1_000, x = 100f))
        assertFalse("41 px apart is past the slop", tap(1_100, x = 141f))
    }

    @Test
    fun `a press that travels past the slop is a pan`() {
        taps.beginHand(armed = true, timeMs = 1_000, x = 100f, y = 100f)
        taps.handMove(150f, 100f)

        assertFalse("a panned touch never completes a tap", taps.endHand(flicked = false))

        assertFalse("and it forgot the pair it was in", tap(1_100))
    }

    @Test
    fun `a short flick is a pan even inside the slop`() {
        assertFalse(tap(1_000))
        assertFalse("the glide it started is not a tap", tap(1_100, flicked = true))
        assertFalse("and the flick also forgets the pair", tap(1_150))
    }

    @Test
    fun `a cancelled press is not a tap`() {
        taps.beginHand(armed = true, timeMs = 1_000, x = 100f, y = 100f)
        taps.cancelHand()

        assertFalse(taps.endHand(flicked = false))
        assertFalse("and nothing was recorded to pair with", tap(1_100))
    }

    @Test
    fun `the hand tool is what arms a tap`() {
        taps.beginHand(armed = false, timeMs = 1_000, x = 100f, y = 100f)
        assertFalse("every other tool's tap means something of its own", taps.endHand(flicked = false))
        taps.beginHand(armed = false, timeMs = 1_100, x = 100f, y = 100f)
        assertFalse(taps.endHand(flicked = false))
    }

    @Test
    fun `a living element pairs only with a tap on itself`() {
        assertFalse(taps.livingTap(1_000, 100f, 100f, key = "page 0 / table"))
        assertTrue(taps.livingTap(1_150, 105f, 100f, key = "page 0 / table"))

        assertFalse("consumed", taps.livingTap(1_200, 100f, 100f, key = "page 0 / table"))
        assertFalse("a different element never pairs", taps.livingTap(1_250, 100f, 100f, key = "page 0 / plot"))
    }

    @Test
    fun `two canvases do not share a tap history`() {
        val other = TapClassifier(300L, 40f, 500L, 8f)

        assertFalse(taps.livingTap(1_000, 100f, 100f, key = "k"))
        assertFalse(
            "a tap in another pane is that pane's first, not a pairing",
            other.livingTap(1_010, 100f, 100f, key = "k"),
        )
        assertTrue("its own pair still works", other.livingTap(1_020, 100f, 100f, key = "k"))
    }
}
