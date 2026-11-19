package com.mobixournal.desktop

import com.mobixournal.desktop.sheet.Edge
import com.mobixournal.desktop.sheet.EdgeBumpState
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.edgeHitOf
import com.mobixournal.desktop.sheet.edgeRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edge bump's rule: what a clamped write refused is the side the window flashes (*EdgeBump.kt*).
 *
 * Pure arithmetic on purpose — the flash itself is a frame, but which side to flash, and how hard, is a
 * decision that can be read here without a window.
 */
class EdgeBumpTest {

    private val pageW = 600f
    private val pageH = 800f
    private val viewW = 400f
    private val viewH = 300f

    @Test
    fun `a write that travels as far as it asked reports no edge`() {
        val at = Viewport(zoom = 1f, offsetX = -100f, offsetY = -200f)
        val after = at.pannedBy(0f, 10f, pageW, pageH, viewW, viewH)
        assertNull("mid-page travel is not a bump", edgeRefusal(at, after, 0f, 10f))
    }

    @Test
    fun `a pan into the top of the page reports the top`() {
        val at = Viewport(zoom = 1f, offsetY = 0f)
        val after = at.pannedBy(0f, 40f, pageW, pageH, viewW, viewH)
        assertEquals(setOf(Edge.TOP), edgeRefusal(at, after, 0f, 40f)?.edges)
    }

    @Test
    fun `a pan into the bottom of the page reports the bottom`() {
        val at = Viewport(zoom = 1f, offsetY = viewH - pageH)
        val after = at.pannedBy(0f, -40f, pageW, pageH, viewW, viewH)
        assertEquals(setOf(Edge.BOTTOM), edgeRefusal(at, after, 0f, -40f)?.edges)
    }

    @Test
    fun `a pan into the left and right sides reports the side it hit`() {
        val left = Viewport(zoom = 1f, offsetX = 0f)
        val leftAfter = left.pannedBy(30f, 0f, pageW, pageH, viewW, viewH)
        assertEquals(setOf(Edge.LEFT), edgeRefusal(left, leftAfter, 30f, 0f)?.edges)

        val right = Viewport(zoom = 1f, offsetX = viewW - pageW)
        val rightAfter = right.pannedBy(-30f, 0f, pageW, pageH, viewW, viewH)
        assertEquals(setOf(Edge.RIGHT), edgeRefusal(right, rightAfter, -30f, 0f)?.edges)
    }

    @Test
    fun `a shove into a corner reports both sides`() {
        val at = Viewport(zoom = 1f, offsetX = 0f, offsetY = 0f)
        val after = at.pannedBy(50f, 50f, pageW, pageH, viewW, viewH)
        assertEquals(setOf(Edge.LEFT, Edge.TOP), edgeRefusal(at, after, 50f, 50f)?.edges)
    }

    @Test
    fun `a pixel of rounding is not an edge`() {
        assertNull(edgeHitOf(0.2f, -0.3f))
    }

    /**
     * A wheel held against an edge reports on every notch; the flash is one gesture, not eight a second.
     * Two bumps inside the cooldown keep the first one's frame, one past it is a new flash.
     */
    @Test
    fun `a wheel held against an edge flashes once, not per notch`() {
        val state = EdgeBumpState()
        val hit = edgeHitOf(6f, 0f)!!
        state.report(hit, nowMs = 1_000L)
        val first = state.seq
        state.report(hit, nowMs = 1_100L)
        assertEquals("a notch inside the cooldown is the same gesture", first, state.seq)
        state.report(hit, nowMs = 1_300L)
        assertEquals("past the cooldown it is a new bump", first + 1, state.seq)
    }

    @Test
    fun `a shove reads harder than a graze, and stops at full strength`() {
        val graze = edgeHitOf(2f, 0f)?.strength ?: 0f
        val shove = edgeHitOf(40f, 0f)?.strength ?: 0f
        assertTrue("a graze still shows something", graze > 0f)
        assertTrue("a shove reads harder", shove > graze)
        assertEquals(1f, shove, 0f)
    }
}
