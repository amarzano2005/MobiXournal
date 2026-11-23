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
    fun `stepFocusBarDrag keeps FREE without side magnet inside margins`() {
        val state = FocusBarDragState(FocusBarDock.FREE, grabFracX = 0.5f, grabFracY = 0.5f)
        // Finger near the left side (but bar still inside [14, 1000 - 290 - 14]) stays FREE with zero pull.
        val leftNear = stepFocusBarDrag(
            state = state, fingerX = 165f, fingerY = 400f,
            horizW = 290f, horizH = 52f, currentMorph = 0f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.FREE, leftNear.state.dock)
        assertEquals(20f, leftNear.x, 0.01f)

        val rightNear = stepFocusBarDrag(
            state = leftNear.state, fingerX = 835f, fingerY = 400f,
            horizW = 290f, horizH = 52f, currentMorph = 0f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.FREE, rightNear.state.dock)
        assertEquals(690f, rightNear.x, 0.01f)
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
    fun `encodeFocusBarSlotIds and decodeFocusBarSlotIds preserve all six defaults and extra slots`() {
        val all = FocusBarSlotKind.entries.map { it.id }
        val encoded = encodeFocusBarSlotIds(all + listOf("unknown", "pen"))
        assertEquals(all, decodeFocusBarSlotIds(encoded))
        assertEquals(emptyList<String>(), decodeFocusBarSlotIds(null))
    }

    @Test
    fun `stepFocusBarDrag docks and undocks sides without handle-push oscillation`() {
        // Grabbing near the left handle (grabFracX = 0.05) and pushing the bar past the right edge.
        val initial = FocusBarDragState(FocusBarDock.FREE, grabFracX = 0.05f, grabFracY = 0.5f)
        val docked = stepFocusBarDrag(
            state = initial, fingerX = 735f, fingerY = 400f,
            horizW = 290f, horizH = 52f, currentMorph = 0f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.RIGHT_EDGE, docked.state.dock)
        assertEquals(1000f - 52f - 14f, docked.x, 0.01f)

        // Holding steady at the same fingerX (735f) stays RIGHT_EDGE (no oscillation).
        val held = stepFocusBarDrag(
            state = docked.state, fingerX = 735f, fingerY = 410f,
            horizW = 290f, horizH = 52f, currentMorph = 1f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.RIGHT_EDGE, held.state.dock)

        // Pulling inward by >= undockPullPx (24f) cleanly undocks and stays FREE on subsequent frames.
        val undocked = stepFocusBarDrag(
            state = held.state, fingerX = 705f, fingerY = 410f,
            horizW = 290f, horizH = 52f, currentMorph = 1f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.FREE, undocked.state.dock)
        val stillFree = stepFocusBarDrag(
            state = undocked.state, fingerX = 705f, fingerY = 410f,
            horizW = 290f, horizH = 52f, currentMorph = 0f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.FREE, stillFree.state.dock)
    }

    @Test
    fun `stepFocusBarDrag unfolds vertical side dock cleanly along bottom edge and resists corner ping-pong`() {
        // Vertical bar on LEFT_EDGE grabbed near the top (grabFracY = 0.1f) dragged straight down.
        val leftVert = FocusBarDragState(FocusBarDock.LEFT_EDGE, grabFracX = 0.5f, grabFracY = 0.1f, dockReferencePx = 20f)
        // rawBarBottom = 530 + 0.9 * 290 = 791 >= 800 - 14 (786)
        val landedBottom = stepFocusBarDrag(
            state = leftVert, fingerX = 20f, fingerY = 530f,
            horizW = 290f, horizH = 52f, currentMorph = 1f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.BOTTOM_EDGE, landedBottom.state.dock)
        assertEquals("unfolds horizontally aligned to left margin", 14f, landedBottom.x, 0.01f)
        assertEquals("unfolds flush along the bottom edge, never higher up", 800f - 52f - 14f, landedBottom.y, 0.01f)

        // Next frame at same finger position stays BOTTOM_EDGE (no corner ping-pong and no premature exit).
        val heldBottom = stepFocusBarDrag(
            state = landedBottom.state, fingerX = 20f, fingerY = 530f,
            horizW = 290f, horizH = 52f, currentMorph = 0f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.BOTTOM_EDGE, heldBottom.state.dock)

        // Pulling upward by >= undockPullPx (24f) detaches cleanly to FREE.
        val lifted = stepFocusBarDrag(
            state = heldBottom.state, fingerX = 20f, fingerY = 500f,
            horizW = 290f, horizH = 52f, currentMorph = 0f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.FREE, lifted.state.dock)

        // Mirror check on RIGHT_EDGE vertical-to-bottom.
        val rightVert = FocusBarDragState(FocusBarDock.RIGHT_EDGE, grabFracX = 0.5f, grabFracY = 0.1f, dockReferencePx = 980f)
        val rightBottom = stepFocusBarDrag(
            state = rightVert, fingerX = 980f, fingerY = 530f,
            horizW = 290f, horizH = 52f, currentMorph = 1f,
            containerW = 1000f, containerH = 800f,
            marginPx = 14f, topSafePx = 56f, bottomSafePx = 14f, undockPullPx = 24f,
        )
        assertEquals(FocusBarDock.BOTTOM_EDGE, rightBottom.state.dock)
        assertEquals("unfolds flush to right margin", 1000f - 290f - 14f, rightBottom.x, 0.01f)
        assertEquals("unfolds flush along the bottom edge", 800f - 52f - 14f, rightBottom.y, 0.01f)
    }
}
