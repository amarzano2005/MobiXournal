package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusBarGeometryTest {

    @Test
    fun `resolveFocusBarSlots defaults to the six factory tools`() {
        assertEquals(
            listOf(
                FocusBarSlotKind.PEN,
                FocusBarSlotKind.HIGHLIGHTER,
                FocusBarSlotKind.COLORS,
                FocusBarSlotKind.ERASER,
                FocusBarSlotKind.HAND,
                FocusBarSlotKind.SELECT,
            ),
            resolveFocusBarSlots(emptyList()),
        )
    }

    @Test
    fun `resolveFocusBarSlots filters unknown and duplicate ids and caps at max`() {
        val resolved = resolveFocusBarSlots(
            listOf("pen", "unknown", "pen", "undo", "redo", "hand", "select", "eraser", "image"),
        )
        assertEquals(FOCUS_BAR_MAX_SLOTS, resolved.size)
        assertEquals(
            listOf(
                FocusBarSlotKind.PEN,
                FocusBarSlotKind.UNDO,
                FocusBarSlotKind.REDO,
                FocusBarSlotKind.HAND,
                FocusBarSlotKind.SELECT,
                FocusBarSlotKind.ERASER,
            ),
            resolved,
        )
    }

    @Test
    fun `slot editing helpers respect min, max, swap, and reorder bounds`() {
        val defaults = FOCUS_BAR_DEFAULT_SLOTS
        // Swapping an already-present tool keeps size intact and avoids duplicates.
        val swapped = updateFocusBarSlotList(defaults, 0, FocusBarSlotKind.HAND)
        assertEquals(defaults.size, swapped.size)
        assertEquals(FocusBarSlotKind.HAND, swapped[0])
        assertEquals(FocusBarSlotKind.PEN, swapped[4])

        // Removing stops at FOCUS_BAR_MIN_SLOTS.
        var shrunk = defaults
        repeat(5) { shrunk = updateFocusBarSlotList(shrunk, 0, null) }
        assertEquals(FOCUS_BAR_MIN_SLOTS, shrunk.size)

        // Adding appends unused tools up to FOCUS_BAR_MAX_SLOTS.
        val added = appendFocusBarSlot(shrunk)
        assertEquals(FOCUS_BAR_MIN_SLOTS + 1, added.size)
        assertEquals(added.size, added.distinct().size)

        val moved = moveFocusBarSlot(defaults, 1, -1)
        assertEquals(FocusBarSlotKind.HIGHLIGHTER, moved[0])
        assertEquals(FocusBarSlotKind.PEN, moved[1])
    }

    @Test
    fun `anchor encode and decode round-trips and handles malformed inputs`() {
        val anchor = FocusBarAnchor(FocusBarDock.RIGHT_EDGE, 0.875f, 0.42f)
        val decoded = decodeFocusBarAnchor(encodeFocusBarAnchor(anchor))
        assertEquals(FocusBarDock.RIGHT_EDGE, decoded.dock)
        assertEquals(0.875f, decoded.xFraction, 0.001f)
        assertEquals(0.42f, decoded.yFraction, 0.001f)

        assertEquals(FocusBarAnchor(), decodeFocusBarAnchor(null))
        assertEquals(FocusBarAnchor(), decodeFocusBarAnchor("bad"))
        assertEquals(FocusBarDock.FREE, decodeFocusBarAnchor("BOGUS:0.5:0.5").dock)
    }

    @Test
    fun `nextFocusBarDock enforces hysteresis near left and right edges`() {
        val w = 1000f
        val enter = 72f
        val exit = 110f

        assertEquals(FocusBarDock.LEFT_EDGE, nextFocusBarDock(FocusBarDock.FREE, 60f, w, enter, exit))
        assertEquals(FocusBarDock.RIGHT_EDGE, nextFocusBarDock(FocusBarDock.FREE, 940f, w, enter, exit))
        assertEquals(FocusBarDock.FREE, nextFocusBarDock(FocusBarDock.FREE, 500f, w, enter, exit))

        // Inside hysteresis band (72..110), vertical dock stays latched.
        assertEquals(FocusBarDock.LEFT_EDGE, nextFocusBarDock(FocusBarDock.LEFT_EDGE, 95f, w, enter, exit))
        assertEquals(FocusBarDock.FREE, nextFocusBarDock(FocusBarDock.LEFT_EDGE, 120f, w, enter, exit))

        assertEquals(FocusBarDock.RIGHT_EDGE, nextFocusBarDock(FocusBarDock.RIGHT_EDGE, 905f, w, enter, exit))
        assertEquals(FocusBarDock.FREE, nextFocusBarDock(FocusBarDock.RIGHT_EDGE, 880f, w, enter, exit))
    }

    @Test
    fun `morph geometry smoothly swaps width and height and keeps offsets inside bounds`() {
        val (hw, hh) = focusBarSizePx(6, 40f, 4f, 14f, 6f, 0f)
        val (mw, mh) = focusBarSizePx(6, 40f, 4f, 14f, 6f, 0.5f)
        val (vw, vh) = focusBarSizePx(6, 40f, 4f, 14f, 6f, 1f)

        assertEquals(hw, vh, 0.01f)
        assertEquals(hh, vw, 0.01f)
        assertTrue(mw in vw..hw)
        assertTrue(mh in hh..vh)

        val (hx0, hy0) = focusBarChildOffsetPx(0, 40f, 4f, 14f, 6f, 0f)
        val (hx1, hy1) = focusBarChildOffsetPx(0, 40f, 4f, 14f, 6f, 1f)
        assertEquals(6f, hx0, 0.01f)
        assertEquals(6f, hy0, 0.01f)
        assertEquals(6f, hx1, 0.01f)
        assertEquals(6f, hy1, 0.01f)

        val (x0, y0) = focusBarChildOffsetPx(1, 40f, 4f, 14f, 6f, 0f)
        val (x1, y1) = focusBarChildOffsetPx(1, 40f, 4f, 14f, 6f, 1f)
        assertEquals(y0, x1, 0.01f)
        assertEquals(x0, y1, 0.01f)
    }

    @Test
    fun `snapFocusBarPosition snaps to side margins and clamps within safe vertical zone`() {
        val (lx, ly) = snapFocusBarPosition(FocusBarDock.LEFT_EDGE, 40f, 10f, 52f, 290f, 1200f, 800f, 14f, 56f, 16f)
        assertEquals(14f, lx, 0.01f)
        assertEquals(56f, ly, 0.01f)

        val (rx, ry) = snapFocusBarPosition(FocusBarDock.RIGHT_EDGE, 900f, 700f, 52f, 290f, 1200f, 800f, 14f, 56f, 16f)
        assertEquals(1200f - 52f - 14f, rx, 0.01f)
        assertEquals(800f - 290f - 16f, ry, 0.01f)
    }

    @Test
    fun `effectiveEdgeThresholds caps bands on compact phones and enables finger-driven edge morphing`() {
        val (phoneEnter, phoneExit) = effectiveEdgeThresholds(360f, 128f, 178f)
        assertTrue("phone enter < exit", phoneEnter < phoneExit)
        assertTrue("phone exit leaves free middle", phoneExit < 360f / 2f)

        val (tabEnter, tabExit) = effectiveEdgeThresholds(1000f, 128f, 178f)
        assertEquals(128f, tabEnter, 0.01f)
        assertEquals(178f, tabExit, 0.01f)

        // Finger drags a 290-px wide 6-slot horizontal bar toward left and right edges.
        var dock = FocusBarDock.FREE
        dock = nextFocusBarDock(dock, 110f, 1000f, tabEnter, tabExit)
        assertEquals(FocusBarDock.LEFT_EDGE, dock)
        // Still inside hysteresis band (110..178) stays vertical.
        dock = nextFocusBarDock(dock, 160f, 1000f, tabEnter, tabExit)
        assertEquals(FocusBarDock.LEFT_EDGE, dock)
        // Pulled past exit threshold returns horizontal.
        dock = nextFocusBarDock(dock, 210f, 1000f, tabEnter, tabExit)
        assertEquals(FocusBarDock.FREE, dock)

        dock = nextFocusBarDock(dock, 890f, 1000f, tabEnter, tabExit)
        assertEquals(FocusBarDock.RIGHT_EDGE, dock)
    }
}
