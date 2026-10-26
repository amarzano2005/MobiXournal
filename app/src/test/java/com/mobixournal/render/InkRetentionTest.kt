package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two per-frame decisions the ink cache makes, pinned as pure arithmetic.
 *
 * The raster itself is Android graphics and cannot be exercised here, but both questions the frame
 * asks of the cache are decisions rather than drawing: **which pages keep their bitmap** across a
 * scroll ([retainedAround]), and **whether the bitmap already held may stand in** for the bucket a
 * pinch has just moved to ([inkStandInUsable]). Those are what decide if scrolling back re-rasterises
 * and if a pinch shows a blit or a bare redraw, so they are tested on their own.
 */
class InkRetentionTest {

    private fun pages(count: Int): List<Page> = List(count) {
        Page(
            width = 595.0,
            height = 842.0,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(emptyList())),
        )
    }

    /** The laid-out document, so the "visible" window is a real slice of a real stack. */
    private fun boxes(count: Int) =
        PageStacker.stack(pages(count), viewWidthPx = 1000, gapPx = 8f, zoom = 1f).boxes

    @Test
    fun `a scroll keeps the pages on screen and one either side`() {
        val boxes = boxes(10)
        assertEquals(
            "the two pages on screen, plus the one a flick would return to on either side",
            setOf(3, 4, 5, 6),
            retainedAround(boxes.subList(4, 6), pageCount = 10),
        )
    }

    @Test
    fun `the margin stops at the ends of the document`() {
        val boxes = boxes(10)
        assertEquals("the first page has no page before it", setOf(0, 1), retainedAround(boxes.subList(0, 1), 10))
        assertEquals("nor the last one after it", setOf(8, 9), retainedAround(boxes.subList(9, 10), 10))
    }

    @Test
    fun `a single-page document keeps its one raster`() {
        val boxes = boxes(1)
        assertEquals(setOf(0), retainedAround(boxes, pageCount = 1))
    }

    @Test
    fun `no visible page keeps nothing, and a margin of zero keeps exactly what is drawn`() {
        val boxes = boxes(10)
        assertTrue(retainedAround(emptyList(), pageCount = 10).isEmpty())
        assertEquals(setOf(4, 5), retainedAround(boxes.subList(4, 6), pageCount = 10, margin = 0))
    }

    @Test
    fun `the raster a pinch already has stands in for the bucket it moved to`() {
        val cached = 512
        assertTrue("the bucket it was made for", inkStandInUsable(cached, cached))
        assertTrue("one bucket step up is the stretch a bucket already implies", inkStandInUsable(cached, 609))
        assertTrue("a sharper raster serving a smaller bucket is free", inkStandInUsable(2048, 300))
    }

    @Test
    fun `a raster too coarse to stretch is not a stand-in`() {
        assertFalse(
            "half again as wide would visibly blur the ink; the frame draws elements instead",
            inkStandInUsable(256, 600),
        )
        assertFalse(inkStandInUsable(256, 4096))
    }

    @Test
    fun `an unbounded raster never stands in`() {
        assertFalse("a zero or negative width is a broken entry, not a small one", inkStandInUsable(0, 256))
    }
}
