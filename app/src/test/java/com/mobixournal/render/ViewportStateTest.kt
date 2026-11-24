package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportStateTest {

    /** A viewport of 100×100 over content of 200×400 at zoom 1. */
    private fun viewport() = ViewportState().apply { setBounds(100f, 100f, 200f, 400f) }

    @Test fun `max scroll is the content beyond the viewport`() {
        val v = viewport()
        assertEquals(100f, v.maxScrollX(), 0f)
        assertEquals(300f, v.maxScrollY(), 0f)
        assertTrue(v.canScroll())
    }

    @Test fun `content smaller than the viewport cannot scroll`() {
        val v = ViewportState().apply { setBounds(100f, 100f, 50f, 50f) }
        assertEquals(0f, v.maxScrollX(), 0f)
        assertEquals(0f, v.maxScrollY(), 0f)
        assertFalse(v.canScroll())
    }

    @Test fun `scrollBy clamps and reports whether it moved`() {
        val v = viewport()
        assertTrue(v.scrollBy(10f, 20f))
        assertEquals(10f, v.scrollX, 0f)
        assertEquals(20f, v.scrollY, 0f)
        assertTrue(v.scrollBy(0f, 1000f))
        assertEquals(300f, v.scrollY, 0f)
        assertFalse(v.scrollBy(0f, 50f)) // already pinned at the bottom
        assertTrue(v.scrollBy(1000f, 0f))
        assertEquals(100f, v.scrollX, 0f)
        assertFalse(v.scrollBy(50f, 0f)) // already pinned at the right edge
    }

    @Test fun `scrollToY clamps to the range`() {
        val v = viewport()
        v.scrollToY(-5f)
        assertEquals(0f, v.scrollY, 0f)
        v.scrollToY(9999f)
        assertEquals(300f, v.scrollY, 0f)
    }

    @Test fun `setBounds pulls a stale offset back into range`() {
        val v = viewport()
        v.scrollToY(300f)
        v.setBounds(100f, 100f, 200f, 150f) // pages shrank
        assertEquals(50f, v.scrollY, 0f)
    }

    /** Relayout the way the stacked layout does: extents scale with the zoom. */
    private fun ViewportState.relayoutTo(w: Float, h: Float): () -> Unit =
        { setBoundsKeepingOffsets(w * zoom, h * zoom) }

    private fun ViewportState.setBoundsKeepingOffsets(cw: Float, ch: Float) =
        setBounds(100f, 100f, cw, ch)

    @Test fun `zoomTo clamps to the zoom range`() {
        val v = viewport()
        assertTrue(v.zoomTo(100f, v.relayoutTo(200f, 400f)))
        assertEquals(ViewportState.MAX_ZOOM, v.zoom, 0f)
        assertFalse(v.zoomTo(100f, v.relayoutTo(200f, 400f)))
        assertTrue(v.zoomTo(0f, v.relayoutTo(200f, 400f)))
        assertEquals(ViewportState.MIN_ZOOM, v.zoom, 0f)
    }

    @Test fun `zoomAbout keeps the focus point fixed`() {
        val v = viewport()
        v.scrollToY(100f)
        // The content point under viewport y=50 is 150 of 400 — 0.375 of the way down.
        v.zoomAbout(0f, 50f, 2f, v.relayoutTo(200f, 400f))
        assertEquals(2f, v.zoom, 0f)
        assertEquals(0.375f * 800f - 50f, v.scrollY, 0.001f)
    }

    @Test fun `zoomTo keeps the viewport centre fixed`() {
        val v = viewport()
        v.scrollToY(100f)
        // Centre (y=50) sits at content 150/400; after 2× it should stay centred on that fraction.
        v.zoomTo(2f, v.relayoutTo(200f, 400f))
        assertEquals(0.375f * 800f - 50f, v.scrollY, 0.001f)
    }

    @Test fun `reset returns to the top-left at 100 percent`() {
        val v = viewport()
        v.scrollBy(50f, 50f)
        v.zoomTo(2f, v.relayoutTo(200f, 400f))
        v.reset()
        assertEquals(0f, v.scrollX, 0f)
        assertEquals(0f, v.scrollY, 0f)
        assertEquals(1f, v.zoom, 0f)
    }

    @Test fun `scrolling is clamped to the page stack in both axes`() {
        val v = viewport()
        v.scrollBy(1000f, 5000f)
        assertEquals(100f, v.scrollX, 0f)
        assertEquals(300f, v.scrollY, 0f)
        v.scrollBy(-10000f, -10000f)
        assertEquals(0f, v.scrollX, 0f)
        assertEquals(0f, v.scrollY, 0f)
    }

    @Test fun `kept scale holds the page's size across a width change`() {
        // The page is drawn at `width × zoom`: half the width needs twice the zoom for the same px.
        val split = keptScaleZoom(1f, 2432f, 1194f)
        assertEquals(2432f, 1194f * split, 0.5f)
        assertEquals(2432f, 2432f * keptScaleZoom(split, 1194f, 2432f), 0.5f)
    }

    @Test fun `kept scale is exactly reversible`() {
        // The toggle in both directions: landing back on the old width restores the old zoom.
        val zoomed = keptScaleZoom(0.8f, 2432f, 1194f)
        assertEquals(0.8f, keptScaleZoom(zoomed, 1194f, 2432f), 1e-5f)
    }

    @Test fun `kept scale clamps like any other zoom`() {
        assertEquals(ViewportState.MAX_ZOOM, keptScaleZoom(9f, 2400f, 600f), 0f)
        assertEquals(ViewportState.MIN_ZOOM, keptScaleZoom(0.3f, 600f, 2400f), 0f)
    }

    @Test fun `kept scale leaves the zoom alone on a degenerate width`() {
        // A canvas that has not been laid out yet has no width to compensate against.
        assertEquals(1.4f, keptScaleZoom(1.4f, 0f, 1200f), 0f)
        assertEquals(1.4f, keptScaleZoom(1.4f, 1200f, 0f), 0f)
    }

    @Test fun `an unqueued resize keeps the zoom`() {
        val v = viewport()
        assertEquals(1f, v.adoptResizeZoom(40f), 0f)
    }

    @Test fun `a queued keep compensates once and then clears`() {
        val v = viewport()
        v.keepScaleOnResize = true
        // The canvas grew from 100 to 200: the page's fit doubles, so the zoom halves to hold its px.
        assertEquals(0.5f, v.adoptResizeZoom(200f), 1e-5f)
        // Only the toggle's own resize is compensated; the next one re-fits like a drag.
        assertEquals(v.zoom, v.adoptResizeZoom(100f), 0f)
    }

    @Test fun `a relayout at the same width leaves the queue armed`() {
        val v = viewport()
        v.keepScaleOnResize = true
        // The toggle's own repaint can still run against the old extent: that is not the resize.
        assertEquals(1f, v.adoptResizeZoom(100f), 0f)
        assertEquals(0.5f, v.adoptResizeZoom(200f), 1e-5f)
    }

    private fun testPage(w: Double, h: Double) = com.mobixournal.format.model.Page(
        width = w,
        height = h,
        background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
        layers = emptyList(),
    )

    @Test fun `PageStacker fits and centres pages within chrome insets`() {
        val page = testPage(500.0, 800.0)
        val stacked = PageStacker.stack(
            pages = listOf(page),
            viewWidthPx = 1200,
            gapPx = 20f,
            zoom = 1f,
            insetLeftPx = 200f,
            insetTopPx = 100f,
            insetRightPx = 0f,
            insetBottomPx = 0f,
        )
        assertEquals(1, stacked.boxes.size)
        val box = stacked.boxes.first()
        assertEquals(200f, box.leftPx, 0.01f)
        assertEquals(100f, box.topPx, 0.01f)
        assertEquals(1000f, box.widthPx, 0.01f)
        assertEquals(1600f, box.heightPx, 0.01f)
        assertEquals(1200f, stacked.contentWidthPx, 0.01f)
        assertEquals(100f + 1600f + 20f, stacked.totalHeightPx, 0.01f)
    }

    @Test fun `updateChromeInsets keeps scrollY at 0 at top and preserves focal fraction when scrolled`() {
        val pages = listOf(
            testPage(500.0, 1000.0),
            testPage(500.0, 1000.0),
        )
        val v = ViewportState()
        fun doRelayout() {
            val stacked = PageStacker.stack(
                pages = pages,
                viewWidthPx = 1000,
                gapPx = 0f,
                zoom = v.zoom,
                insetLeftPx = v.insetLeftPx,
                insetTopPx = v.insetTopPx,
                insetRightPx = v.insetRightPx,
                insetBottomPx = v.insetBottomPx,
            )
            v.setBounds(1000f, 1000f, stacked.contentWidthPx, stacked.totalHeightPx)
        }
        v.updateChromeInsets(100f, 80f, 0f, 0f, ::doRelayout)
        assertEquals(0f, v.scrollY, 0.01f)
        assertEquals(100f, v.insetLeftPx, 0.01f)
        assertEquals(900f, v.usableWidth(), 0.01f)

        // Scroll partway down, then remove insets: focal document fraction at usable centre stays stable.
        v.scrollToY(500f)
        val beforeCenterFrac = (v.scrollY + v.usableCenterY() - v.insetTopPx) / (2 * 1800f)
        v.updateChromeInsets(0f, 0f, 0f, 0f, ::doRelayout)
        val afterCenterFrac = (v.scrollY + v.usableCenterY() - v.insetTopPx) / (2 * 2000f)
        assertEquals(beforeCenterFrac, afterCenterFrac, 0.01f)
    }

    @Test fun `zoomAbout pins focal pixel under non-zero chrome insets`() {
        val page = testPage(500.0, 1000.0)
        val v = ViewportState()
        fun doRelayout() {
            val stacked = PageStacker.stack(
                pages = listOf(page),
                viewWidthPx = 1000,
                gapPx = 0f,
                zoom = v.zoom,
                insetLeftPx = v.insetLeftPx,
                insetTopPx = v.insetTopPx,
                insetRightPx = v.insetRightPx,
                insetBottomPx = v.insetBottomPx,
            )
            v.setBounds(1000f, 800f, stacked.contentWidthPx, stacked.totalHeightPx)
        }
        v.updateChromeInsets(200f, 100f, 0f, 0f, ::doRelayout)
        v.scrollToY(200f)
        val focusVx = 600f
        val focusVy = 450f
        val ptXBefore = (v.scrollX + focusVx - v.insetLeftPx) / v.zoom
        val ptYBefore = (v.scrollY + focusVy - v.insetTopPx) / v.zoom
        assertTrue(v.zoomAbout(focusVx, focusVy, 2f, ::doRelayout))
        val ptXAfter = (v.scrollX + focusVx - v.insetLeftPx) / v.zoom
        val ptYAfter = (v.scrollY + focusVy - v.insetTopPx) / v.zoom
        assertEquals(ptXBefore, ptXAfter, 0.5f)
        assertEquals(ptYBefore, ptYAfter, 0.5f)
    }
}
