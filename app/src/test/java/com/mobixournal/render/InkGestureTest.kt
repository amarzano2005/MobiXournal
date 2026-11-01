package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ink gesture as an object: what it holds while a stroke is live, what `drop` forgets, and when its
 * router is built.
 *
 * The router is real here (it is pure and needs no `View`), so `drop` is checked by behaviour rather than
 * by a flag: after it, a move for a pointer the router had seen produces nothing, because the contact map
 * is empty — which is exactly what "the next gesture must not inherit these contacts" means.
 */
class InkGestureTest {

    private var built = 0
    private val router = InkPointerRouter(startOnDown = true)
    private val ink = InkGesture(newRouter = { built++; router })

    private fun pointer(phase: InkPointerPhase, x: Float = 10f, timeMs: Long = 0L) = InkPointer(
        id = 1,
        x = x,
        y = 10f,
        pressure = 1f,
        timeMs = timeMs,
        tool = PointerKind.STYLUS,
        phase = phase,
    )

    private fun point(x: Double) = StrokePoint(x, 0.0, 1.5)

    @Test
    fun `a fresh gesture carries no ink`() {
        assertFalse(ink.inProgress)
        assertNull(ink.current)
        assertNull(ink.currentStrokes)
    }

    @Test
    fun `either kind of in-progress ink counts`() {
        ink.current = arrayListOf(point(0.0))
        assertTrue(ink.inProgress)

        ink.current = null
        ink.currentStrokes = listOf(listOf(point(0.0), point(10.0)))
        assertTrue("a shape's multi-stroke preview is ink too", ink.inProgress)

        ink.current = arrayListOf(point(0.0))
        ink.currentStrokes = null
        assertTrue(ink.inProgress)
    }

    @Test
    fun `dropping forgets the ink but keeps the page`() {
        ink.currentPage = 7
        ink.current = arrayListOf(point(0.0))
        ink.currentStrokes = listOf(listOf(point(0.0)))

        ink.drop()

        assertNull(ink.current)
        assertNull(ink.currentStrokes)
        assertEquals("the page a gesture worked on outlives its ink", 7, ink.currentPage)
    }

    @Test
    fun `dropping empties the router's contacts`() {
        ink.pointerRouter.onPointer(pointer(InkPointerPhase.DOWN))
        assertTrue(
            "the router is following the stroke",
            ink.pointerRouter.onPointer(pointer(InkPointerPhase.MOVE, x = 20f, timeMs = 16)).isNotEmpty(),
        )

        ink.drop()

        assertTrue(
            "a move for a contact the router no longer knows is not a sample",
            ink.pointerRouter.onPointer(pointer(InkPointerPhase.MOVE, x = 30f, timeMs = 32)).isEmpty(),
        )
    }

    @Test
    fun `the router is built on first use, not at construction`() {
        assertEquals("nothing may be built before the canvas has its settings", 0, built)

        assertSame(router, ink.pointerRouter)
        assertEquals(1, built)

        ink.pointerRouter
        assertEquals("and it is kept, not rebuilt per access", 1, built)
    }

    @Test
    fun `a replaced router is the one used from then on`() {
        val replacement = InkPointerRouter(startOnDown = true)

        ink.pointerRouter = replacement

        assertSame(replacement, ink.pointerRouter)
        assertEquals("the factory is not consulted for a router that was handed in", 0, built)
    }
}
