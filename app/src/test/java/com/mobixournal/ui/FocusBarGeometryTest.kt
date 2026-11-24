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
            listOf(
                "pen", "unknown", "pen", "undo", "redo", "hand",
                "select", "eraser", "image", "text", "pages", "layers", "line",
            ),
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
                FocusBarSlotKind.IMAGE,
                FocusBarSlotKind.TEXT,
                FocusBarSlotKind.PAGES,
                FocusBarSlotKind.LAYERS,
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
    fun `toggleFocusBarSlot appends absent tools up to max and removes present tools down to min`() {
        val defaults = FOCUS_BAR_DEFAULT_SLOTS
        // Absent tool is appended when below FOCUS_BAR_MAX_SLOTS.
        val withUndo = toggleFocusBarSlot(defaults, FocusBarSlotKind.UNDO)
        assertEquals(defaults.size + 1, withUndo.size)
        assertEquals(FocusBarSlotKind.UNDO, withUndo.last())

        // Cannot append beyond FOCUS_BAR_MAX_SLOTS.
        var full = defaults
        for (candidate in FocusBarSlotKind.entries) {
            if (full.size >= FOCUS_BAR_MAX_SLOTS) break
            if (candidate !in full) full = toggleFocusBarSlot(full, candidate)
        }
        assertEquals(FOCUS_BAR_MAX_SLOTS, full.size)
        val extra = FocusBarSlotKind.entries.first { it !in full }
        assertEquals(full, toggleFocusBarSlot(full, extra))

        // Present tool is removed down to FOCUS_BAR_MIN_SLOTS, never below.
        val removed = toggleFocusBarSlot(defaults, FocusBarSlotKind.SELECT)
        assertEquals(defaults.size - 1, removed.size)
        assertTrue(FocusBarSlotKind.SELECT !in removed)

        val minimum = defaults.take(FOCUS_BAR_MIN_SLOTS)
        assertEquals(minimum, toggleFocusBarSlot(minimum, minimum.first()))
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

    private fun runStep(
        state: FocusBarDragState,
        fingerX: Float,
        fingerY: Float,
        currentMorph: Float,
    ): FocusBarDragStep = stepFocusBarDrag(
        state = state,
        fingerX = fingerX,
        fingerY = fingerY,
        horizW = 290f,
        horizH = 52f,
        currentMorph = currentMorph,
        containerW = 1000f,
        containerH = 800f,
        marginPx = 14f,
        topSafePx = 56f,
        bottomSafePx = 14f,
        undockPullPx = 28f,
        sideFingerZonePx = 22f,
        sideOverpushPx = 36f,
    )

    @Test
    fun `stepFocusBarDrag keeps FREE without side magnet even on slight overpush`() {
        val state = FocusBarDragState(FocusBarDock.FREE, grabFracX = 0.5f, grabFracY = 0.5f)
        // Finger inside safe zone and slight bar overpush (rawLeft = 115 - 145 = -30 > -36) stays FREE.
        val leftNear = runStep(state, fingerX = 115f, fingerY = 400f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, leftNear.state.dock)
        assertEquals(14f, leftNear.x, 0.01f)

        val rightNear = runStep(leftNear.state, fingerX = 885f, fingerY = 400f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, rightNear.state.dock)
        assertEquals(1000f - 290f - 14f, rightNear.x, 0.01f)

        // Pushing past -sideOverpushPx (rawLeft = 100 - 145 = -45 <= -36) or finger into side zone docks.
        val dockedLeft = runStep(rightNear.state, fingerX = 100f, fingerY = 400f, currentMorph = 0f)
        assertEquals(FocusBarDock.LEFT_EDGE, dockedLeft.state.dock)

        val fingerRightEdge = runStep(state, fingerX = 980f, fingerY = 400f, currentMorph = 0f)
        assertEquals(FocusBarDock.RIGHT_EDGE, fingerRightEdge.state.dock)
    }

    @Test
    fun `stepFocusBarDrag keeps FREE without bottom stickiness so lifting moves 1 to 1`() {
        val state = FocusBarDragState(FocusBarDock.FREE, grabFracX = 0.5f, grabFracY = 0.5f)
        val atBottom = runStep(state, fingerX = 500f, fingerY = 780f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, atBottom.state.dock)
        assertEquals(800f - 52f - 14f, atBottom.y, 0.01f)

        val liftedSlightly = runStep(atBottom.state, fingerX = 500f, fingerY = 755f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, liftedSlightly.state.dock)
        assertEquals(755f - 26f, liftedSlightly.y, 0.01f)

        // Sliding sideways along the bottom from FREE can dock cleanly to the side edge.
        val slidRight = runStep(atBottom.state, fingerX = 982f, fingerY = 760f, currentMorph = 0f)
        assertEquals(FocusBarDock.RIGHT_EDGE, slidRight.state.dock)
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
    fun `stepFocusBarDrag docks and undocks sides cleanly and preserves grabFracY`() {
        // Grabbing near the left handle (grabFracX = 0.05) and pushing the bar past right overpush.
        val initial = FocusBarDragState(FocusBarDock.FREE, grabFracX = 0.05f, grabFracY = 0.25f)
        // rawRight = 765 + 290 * 0.95 = 1040.5 >= 1000 + 36
        val docked = runStep(initial, fingerX = 765f, fingerY = 400f, currentMorph = 0f)
        assertEquals(FocusBarDock.RIGHT_EDGE, docked.state.dock)
        assertEquals(1000f - 52f - 14f, docked.x, 0.01f)
        assertEquals(0.25f, docked.state.grabFracY, 0.001f)

        // Holding steady at the same fingerX (765f) stays RIGHT_EDGE (no oscillation).
        val held = runStep(docked.state, fingerX = 765f, fingerY = 410f, currentMorph = 1f)
        assertEquals(FocusBarDock.RIGHT_EDGE, held.state.dock)

        // Pulling inward by >= undockPullPx (28f) cleanly undocks and stays FREE on subsequent frames.
        val undocked = runStep(held.state, fingerX = 735f, fingerY = 410f, currentMorph = 1f)
        assertEquals(FocusBarDock.FREE, undocked.state.dock)
        assertEquals(0.25f, undocked.state.grabFracY, 0.001f)
        val stillFree = runStep(undocked.state, fingerX = 735f, fingerY = 410f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, stillFree.state.dock)
    }

    @Test
    fun `stepFocusBarDrag unfolds vertical side dock to bottom, suppresses corner ping-pong, and docks opposite side`() {
        // Vertical bar on LEFT_EDGE grabbed near the top (grabFracY = 0.1f) dragged straight down.
        val leftVert = FocusBarDragState(FocusBarDock.LEFT_EDGE, grabFracX = 0.5f, grabFracY = 0.1f, dockReferencePx = 20f)
        // rawBarBottom = 530 + 0.9 * 290 = 791 >= 800 - 14 (786)
        val landedBottom = runStep(leftVert, fingerX = 20f, fingerY = 530f, currentMorph = 1f)
        assertEquals(FocusBarDock.BOTTOM_EDGE, landedBottom.state.dock)
        assertEquals(FocusBarDock.LEFT_EDGE, landedBottom.state.blockedSideDock)
        assertEquals("unfolds horizontally aligned to left margin", 14f, landedBottom.x, 0.01f)
        assertEquals("unfolds flush along the bottom edge, never higher up", 800f - 52f - 14f, landedBottom.y, 0.01f)

        // Next frame at same corner finger position stays BOTTOM_EDGE (no corner ping-pong).
        val heldBottom = runStep(landedBottom.state, fingerX = 20f, fingerY = 530f, currentMorph = 0f)
        assertEquals(FocusBarDock.BOTTOM_EDGE, heldBottom.state.dock)

        // Sliding horizontally along BOTTOM_EDGE to the right edge transitions directly to RIGHT_EDGE.
        val crossedRight = runStep(heldBottom.state, fingerX = 985f, fingerY = 530f, currentMorph = 0f)
        assertEquals(FocusBarDock.RIGHT_EDGE, crossedRight.state.dock)

        // Pulling straight upward by >= undockPullPx from bottom-left corner detaches to FREE and stays FREE.
        val liftedInCorner = runStep(heldBottom.state, fingerX = 20f, fingerY = 495f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, liftedInCorner.state.dock)
        val stillFreeInCorner = runStep(liftedInCorner.state, fingerX = 20f, fingerY = 490f, currentMorph = 0f)
        assertEquals(FocusBarDock.FREE, stillFreeInCorner.state.dock)

        // Mirror check on RIGHT_EDGE vertical-to-bottom.
        val rightVert = FocusBarDragState(FocusBarDock.RIGHT_EDGE, grabFracX = 0.5f, grabFracY = 0.1f, dockReferencePx = 980f)
        val rightBottom = runStep(rightVert, fingerX = 980f, fingerY = 530f, currentMorph = 1f)
        assertEquals(FocusBarDock.BOTTOM_EDGE, rightBottom.state.dock)
        assertEquals(FocusBarDock.RIGHT_EDGE, rightBottom.state.blockedSideDock)
        assertEquals("unfolds flush to right margin", 1000f - 290f - 14f, rightBottom.x, 0.01f)
        assertEquals("unfolds flush along the bottom edge", 800f - 52f - 14f, rightBottom.y, 0.01f)
    }

    @Test
    fun `focusBarRenderOffsetPx pins edges flush at intermediate and settled morph sizes`() {
        for (morph in listOf(0f, 0.5f, 1f)) {
            val (bw, bh) = focusBarSizePx(6, 40f, 4f, 14f, 6f, morph)
            val (lx, _) = focusBarRenderOffsetPx(
                FocusBarDock.LEFT_EDGE, 80f, 400f, 0.5f, 0.5f, bw, bh, 1000f, 800f, 14f, 56f, 14f,
            )
            val (rx, _) = focusBarRenderOffsetPx(
                FocusBarDock.RIGHT_EDGE, 920f, 400f, 0.5f, 0.5f, bw, bh, 1000f, 800f, 14f, 56f, 14f,
            )
            val (_, by) = focusBarRenderOffsetPx(
                FocusBarDock.BOTTOM_EDGE, 500f, 700f, 0.5f, 0.5f, bw, bh, 1000f, 800f, 14f, 56f, 14f,
            )
            assertEquals("left pinned at morph=$morph", 14f, lx, 0.01f)
            assertEquals("right pinned at morph=$morph", 1000f - bw - 14f, rx, 0.01f)
            assertEquals("bottom pinned at morph=$morph", 800f - bh - 14f, by, 0.01f)
        }
    }
}
