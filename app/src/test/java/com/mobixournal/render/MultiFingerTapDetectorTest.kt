package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MultiFingerTapDetector]: two-finger tap (Undo) and three-finger tap (Redo),
 * ensuring centroid shifts and release lift-off jitter never disqualify legitimate taps while
 * real pans and pinches are reliably excluded.
 */
class MultiFingerTapDetectorTest {

    private val detector = MultiFingerTapDetector(shortcutsEnabled = true, timeoutMs = 350L)
    private val slop = 20f

    @Test
    fun `two finger still tap fires UNDO`() {
        // Finger 0 lands at (100, 100)
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        // Finger 1 lands at (200, 100) 20ms later
        detector.onPointerDown(time = 1020L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)

        assertTrue(detector.candidate)
        assertEquals(2, detector.maxPointers)
        assertFalse(detector.moved)

        // Slight natural tremor within slop
        val moved = detector.onMove(
            time = 1050L,
            pointerCount = 2,
            getId = { it },
            getX = { if (it == 0) 102f else 198f },
            getY = { if (it == 0) 101f else 102f },
        )
        assertFalse(moved)
        assertFalse(detector.moved)

        // Finger 0 lifts
        detector.onPointerUp(pointerId = 0)
        assertTrue(detector.lifting)

        // Finger 1 lifts 15ms later
        val action = detector.onUp(time = 1100L)
        assertEquals(MultiFingerTapAction.UNDO, action)
    }

    @Test
    fun `three finger still tap fires REDO`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1015L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)
        detector.onPointerDown(time = 1030L, x = 300f, y = 100f, pointerId = 2, pointerCount = 3)

        assertTrue(detector.candidate)
        assertEquals(3, detector.maxPointers)

        detector.onPointerUp(pointerId = 0)
        detector.onPointerUp(pointerId = 1)
        val action = detector.onUp(time = 1120L)
        assertEquals(MultiFingerTapAction.REDO, action)
    }

    @Test
    fun `single finger tap returns NONE`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        assertFalse("Single finger never arms multi-tap candidate", detector.candidate)
        val action = detector.onUp(time = 1050L)
        assertEquals(MultiFingerTapAction.NONE, action)
    }

    @Test
    fun `four finger tap disqualifies candidate and returns NONE`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1010L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)
        detector.onPointerDown(time = 1020L, x = 300f, y = 100f, pointerId = 2, pointerCount = 3)
        detector.onPointerDown(time = 1030L, x = 400f, y = 100f, pointerId = 3, pointerCount = 4)

        assertFalse(detector.candidate)
        val action = detector.onUp(time = 1100L)
        assertEquals(MultiFingerTapAction.NONE, action)
    }

    @Test
    fun `movement past slop disqualifies tap and returns NONE`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1020L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)

        // Finger 0 moves 50px (pan)
        val moved = detector.onMove(
            time = 1050L,
            pointerCount = 2,
            getId = { it },
            getX = { if (it == 0) 150f else 250f },
            getY = { 100f },
        )
        assertTrue(moved)
        assertTrue(detector.moved)
        assertFalse(detector.candidate)

        detector.onPointerUp(pointerId = 0)
        val action = detector.onUp(time = 1100L)
        assertEquals(MultiFingerTapAction.NONE, action)
    }

    @Test
    fun `gesture exceeding timeout returns NONE`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1020L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)

        // Fingers held for 400ms (> 350ms)
        detector.onMove(
            time = 1400L,
            pointerCount = 2,
            getId = { it },
            getX = { if (it == 0) 100f else 200f },
            getY = { 100f },
        )
        assertFalse(detector.candidate)

        detector.onPointerUp(pointerId = 0)
        val action = detector.onUp(time = 1450L)
        assertEquals(MultiFingerTapAction.NONE, action)
    }

    @Test
    fun `lift jitter on the trailing finger does not disqualify tap`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1020L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)

        // First finger lifts cleanly
        detector.onPointerUp(pointerId = 0)
        assertTrue(detector.lifting)

        // Trailing finger rolls on glass by 40px right before lifting
        detector.onMove(
            time = 1080L,
            pointerCount = 1,
            getId = { 1 },
            getX = { 240f },
            getY = { 100f },
        )
        assertFalse("Move checks are frozen during lift-off", detector.moved)

        val action = detector.onUp(time = 1100L)
        assertEquals(MultiFingerTapAction.UNDO, action)
    }

    @Test
    fun `shortcuts disabled never triggers UNDO or REDO`() {
        detector.shortcutsEnabled = false
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1020L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)

        assertFalse(detector.candidate)
        val action = detector.onUp(time = 1100L)
        assertEquals(MultiFingerTapAction.NONE, action)
    }

    @Test
    fun `reset clears all detector state`() {
        detector.onDown(time = 1000L, x = 100f, y = 100f, pointerId = 0, touchSlop = slop)
        detector.onPointerDown(time = 1020L, x = 200f, y = 100f, pointerId = 1, pointerCount = 2)
        detector.reset()

        assertFalse(detector.candidate)
        assertFalse(detector.moved)
        assertEquals(0, detector.maxPointers)
    }
}
