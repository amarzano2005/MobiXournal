package com.mobixournal.ui

import com.mobixournal.render.PdfBarBox
import com.mobixournal.render.PdfTextBarPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the **PDF text** bar lands, pinned as geometry rather than left to a device pass.
 *
 * It used to be a fixed strip on the pane's bottom edge — exactly where a passage at the foot of a page sits —
 * and then a capsule hanging from the union of the whole passage, which put it beneath the *last* line however
 * far the reader's finger was. The rule these cases pin is the shared [PdfTextBarPlacement]'s: **beside the
 * active line**, above it when the pane ends below, inside the pane's edges either way, and absent when no
 * selected line is visible at all.
 *
 * The rule itself is tested in `mobix-core` (`PdfTextActionBarTest`); what this class adds is the tablet's own
 * framing of it — a split-screen pane measuring its bars in view pixels, and the guarantee that the bar and the
 * washed band never occupy the same rows when there is any room at all.
 */
class TextSelectionBarAnchorTest {

    /** A split-screen pane: narrow, and shorter than the page it shows. */
    private val canvasWidth = 900f
    private val canvasHeight = 1200f
    private val barWidth = 260f
    private val barHeight = 96f
    private val gap = 10f
    private val margin = 8f

    private fun offset(
        spans: List<PdfBarBox>,
        active: PdfBarBox?,
        bar: Pair<Float, Float> = barWidth to barHeight,
        canvas: Pair<Float, Float> = canvasWidth to canvasHeight,
    ) = PdfTextBarPlacement.offset(
        spans = spans,
        active = active,
        barWidth = bar.first,
        barHeight = bar.second,
        canvasWidth = canvas.first,
        canvasHeight = canvas.second,
        gapPx = gap,
        marginPx = margin,
    )

    private fun line(top: Float, bottom: Float, left: Float = 100f, right: Float = 500f) =
        PdfBarBox(left, top, right, bottom)

    @Test
    fun `a passage mid-page gets the bar under the line the reader is on`() {
        val first = line(300f, 380f)
        val second = line(400f, 480f)
        // Dragging downwards: the endpoint is on the lower line and nothing of the range is under it.
        val placed = offset(listOf(first, second), active = second)!!
        assertEquals("the bar starts one gap under that line", 490f, placed.y, 0.01f)
        assertTrue("so the line is not under it", placed.y >= second.bottom)
    }

    @Test
    fun `selecting upwards puts the bar above the top line, clear of the wash`() {
        val first = line(300f, 380f)
        val second = line(400f, 480f)
        val placed = offset(listOf(first, second), active = first)!!
        assertTrue("above the line the endpoint is on: ${placed.y}", placed.y + barHeight <= first.top)
        assertTrue("and not beneath the passage: ${placed.y}", placed.y < first.top)
    }

    @Test
    fun `a passage at the foot of the pane gets the bar above its active line`() {
        val top = line(1000f, 1080f)
        val last = line(1110f, 1190f)
        val placed = offset(listOf(top, last), active = last)!!
        assertEquals(1110f - gap - barHeight, placed.y, 0.01f)
        assertTrue("and clears the words upwards", placed.y + barHeight <= 1110f)
    }

    @Test
    fun `copy stays reachable at the left edge of a narrow pane`() {
        val placed = offset(listOf(line(300f, 380f, left = 0f, right = 80f)), active = null)!!
        assertEquals(margin, placed.x, 0.01f)
    }

    @Test
    fun `copy stays reachable at the right edge of a narrow pane`() {
        val placed = offset(listOf(line(300f, 380f, left = 820f, right = 890f)), active = null)!!
        assertEquals(canvasWidth - barWidth - margin, placed.x, 0.01f)
    }

    @Test
    fun `a selection taller than the room left stays nearest its line but inside`() {
        // A block filling the pane: neither side has room, so the bar is pulled just inside the bottom edge —
        // still the nearest legal place to the words, and still tappable.
        val placed = offset(listOf(line(40f, 1180f)), active = null)!!
        assertEquals(canvasHeight - barHeight - margin, placed.y, 0.01f)
    }

    @Test
    fun `a bar wider than the pane is still pinned inside it`() {
        val placed = offset(
            spans = listOf(line(300f, 380f)),
            active = null,
            bar = 600f to 96f,
            canvas = 320f to canvasHeight,
        )!!
        assertEquals(margin, placed.x, 0.01f)
        assertTrue("and it does not start off the left of the pane", placed.x >= 0f)
    }

    @Test
    fun `a range scrolled out of the pane has no bar`() {
        assertNull(
            "no capsule is pinned to a pane that shows none of the selection",
            offset(listOf(line(-400f, -320f)), active = null),
        )
    }

    @Test
    fun `with the bar not yet measured it parks against the margin, not at the pane's origin`() {
        val placed = offset(listOf(line(300f, 380f)), active = null, bar = 0f to 0f)!!
        assertEquals(margin, placed.x, 0.01f)
        assertEquals(margin, placed.y, 0.01f)
    }
}
