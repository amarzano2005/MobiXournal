package com.mobixournal.desktop

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edge scroll's **inertia**: it winds up instead of snapping on, it unwinds instead of stopping dead,
 * and its travel depends on the time that passed rather than on how many ticks happened to arrive.
 *
 * The three claims are separate failures in practice. A scroll that jumps to full speed reads as a switch;
 * one that stops instantly is a switch in the other direction; and one that moves a fixed step per tick
 * runs at whatever rate the machine's frames happen to be, so the same gesture crosses a page on one host
 * and crawls on another. `EdgePanTest` covers where the band is and which way it points — this covers how
 * the view gets there.
 */
class EdgeScrollTest {

    private val viewW = 1000f
    private val viewH = 700f

    /** One tick against the right border — the target speed is the full [EDGE_PAN_SPEED_PX] there. */
    private fun atBorder(scroll: EdgeScroll, dtMs: Float) =
        scroll.step(dtMs, viewW - 1f, viewH / 2f, viewW, viewH)

    @Test
    fun `the scroll winds up rather than snapping to full speed`() {
        val scroll = EdgeScroll()
        scroll.reset()
        val first = -atBorder(scroll, EDGE_PAN_TICK_MS.toFloat()).first

        // Half a second of ticks against the border: long enough for the ease to have arrived.
        repeat(60) { atBorder(scroll, EDGE_PAN_TICK_MS.toFloat()) }
        val settled = -atBorder(scroll, EDGE_PAN_TICK_MS.toFloat()).first

        assertTrue("the first tick moves a little ($first px)", first > 0f)
        assertTrue("but a good deal less than the settled one ($first vs $settled)", first < settled / 4f)
        assertEquals(
            "and settling is the border's own speed, not something faster",
            (EDGE_PAN_SPEED_PX * EDGE_PAN_TICK_MS / EDGE_PAN_UNIT_MS).toDouble(),
            settled.toDouble(),
            1.0,
        )
    }

    @Test
    fun `the scroll unwinds to a stop when the pointer leaves the band`() {
        val scroll = EdgeScroll()
        scroll.reset()
        atBorder(scroll, 400f) // at speed

        val justLeft = -scroll.step(EDGE_PAN_TICK_MS.toFloat(), viewW / 2f, viewH / 2f, viewW, viewH).first
        assertTrue("it coasts for a moment ($justLeft)", justLeft > 0f)

        var coasted = 0f
        repeat(120) {
            val step = -scroll.step(EDGE_PAN_TICK_MS.toFloat(), viewW / 2f, viewH / 2f, viewW, viewH).first
            coasted += step
            assertTrue("and never reverses", step >= 0f)
        }
        assertTrue("the coast is short, not a free flight ($coasted px)", coasted < EDGE_PAN_BAND_PX)
        assertEquals(
            "and it comes to rest",
            0f,
            scroll.step(EDGE_PAN_TICK_MS.toFloat(), viewW / 2f, viewH / 2f, viewW, viewH).first,
            0f,
        )
    }

    @Test
    fun `the same time crosses the same distance whatever the frame rate`() {
        // 60 Hz frames against 120 Hz ones over the same 240 ms: the ease is applied per tick, so the two
        // agree to well within a pixel rather than by a factor of two.
        fun travel(dtMs: Float, ticks: Int): Float {
            val scroll = EdgeScroll()
            scroll.reset()
            var total = 0f
            repeat(ticks) { total += -atBorder(scroll, dtMs).first }
            return total
        }
        val sixty = travel(16f, 15)
        val oneTwenty = travel(8f, 30)
        assertTrue("both travelled ($sixty vs $oneTwenty)", sixty > 100f && oneTwenty > 100f)
        assertTrue("the faster tick is not a different gesture", abs(sixty - oneTwenty) / sixty < 0.05f)
    }

    @Test
    fun `a stalled tick cannot lurch the sheet`() {
        val scroll = EdgeScroll()
        scroll.reset()
        atBorder(scroll, 400f) // at speed
        // Half a second without a tick (a stalled window) is clamped to one slow tick's worth.
        val stalled = -scroll.step(500f, viewW - 1f, viewH / 2f, viewW, viewH).first
        assertTrue(
            "the catch-up is bounded ($stalled px)",
            stalled <= EDGE_PAN_SPEED_PX * EDGE_PAN_MAX_TICK_MS / EDGE_PAN_UNIT_MS + 0.01f,
        )
    }

    @Test
    fun `a new drag starts from rest, not from the last gesture's speed`() {
        val warm = EdgeScroll().apply {
            reset()
            atBorder(this, 400f)
        }
        val cold = EdgeScroll().apply { reset() }
        val warmStep = -atBorder(warm, EDGE_PAN_TICK_MS.toFloat()).first
        val coldStep = -atBorder(cold, EDGE_PAN_TICK_MS.toFloat()).first

        assertTrue("a scroll already at speed moves in one tick ($warmStep px)", warmStep > 8f)
        assertTrue("a fresh one has to wind up again ($coldStep px)", coldStep < warmStep / 4f)
    }
}
