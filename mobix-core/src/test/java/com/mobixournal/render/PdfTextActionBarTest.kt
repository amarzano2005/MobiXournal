package com.mobixournal.render

import com.mobixournal.render.PdfTextActionPhase.ABSENT
import com.mobixournal.render.PdfTextActionPhase.EXTENDING
import com.mobixournal.render.PdfTextActionPhase.SETTLED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PDF text bar's two shared rules: **when** it is due and **where** it goes.
 *
 * Both shells used to answer each question locally — a drag flag on one side, the union box on the other — so
 * the bar appeared while the reader was still dragging and hung under the middle of a passage whose endpoint
 * was three lines away. These cases pin the answers once, in the module both shells link.
 */
class PdfTextActionBarTest {

    private val gap = 10f
    private val margin = 8f
    private val canvas = Pair(900f, 1200f)

    private fun place(
        spans: List<PdfBarBox>,
        active: PdfBarBox?,
        barWidth: Float = 260f,
        barHeight: Float = 96f,
        canvasSize: Pair<Float, Float> = canvas,
        avoid: PdfBarBox? = null,
    ): PdfBarOffset? = PdfTextBarPlacement.offset(
        spans = spans,
        active = active,
        barWidth = barWidth,
        barHeight = barHeight,
        canvasWidth = canvasSize.first,
        canvasHeight = canvasSize.second,
        gapPx = gap,
        marginPx = margin,
        avoid = avoid,
    )

    // --- when the bar is due ---------------------------------------------------------------------

    @Test
    fun `a press on a word starts the range but not the bar`() {
        assertFalse("the capsule waits while the range is made", EXTENDING.showsBar)
        assertEquals(EXTENDING, PdfTextActionLifecycle.onPress(hasRange = true))
        assertEquals(ABSENT, PdfTextActionLifecycle.onPress(hasRange = false))
    }

    @Test
    fun `extending keeps the bar away, in either direction`() {
        assertEquals(EXTENDING, PdfTextActionLifecycle.onExtend(EXTENDING))
        // Re-grabbing a settled range (a handle) puts it back into the same state.
        assertEquals(EXTENDING, PdfTextActionLifecycle.onExtend(SETTLED))
        assertEquals(ABSENT, PdfTextActionLifecycle.onExtend(ABSENT))
    }

    @Test
    fun `the release of a nonempty range is what shows the bar`() {
        assertEquals(SETTLED, PdfTextActionLifecycle.onRelease(EXTENDING, hasRange = true))
        assertTrue(SETTLED.showsBar)
        assertEquals(ABSENT, PdfTextActionLifecycle.onRelease(EXTENDING, hasRange = false))
    }

    @Test
    fun `a release with no range in flight summons nothing`() {
        // An up event from a pan, a pen stroke or a second finger is not the end of a selection gesture.
        assertEquals(ABSENT, PdfTextActionLifecycle.onRelease(ABSENT, hasRange = true))
        assertEquals(ABSENT, PdfTextActionLifecycle.onRelease(ABSENT, hasRange = false))
    }

    @Test
    fun `dismissal takes the bar with the range from any state`() {
        assertEquals(ABSENT, PdfTextActionLifecycle.onDismiss())
        assertFalse(PdfTextActionLifecycle.onDismiss().showsBar)
    }

    // --- where it goes ---------------------------------------------------------------------------

    @Test
    fun `the bar stands on the side of the active line the range does not cover`() {
        val first = PdfBarBox(100f, 200f, 500f, 230f)
        val last = PdfBarBox(100f, 400f, 500f, 430f)
        // Selecting upwards: the endpoint is on the top line and the range continues below it, so the bar goes
        // above that line rather than over the words it just selected — and never under the union's bottom.
        val placed = place(listOf(first, last), active = first)!!
        assertEquals("one gap above that line", 200f - gap - 96f, placed.y, 0.01f)
        assertTrue("clear of the words", placed.y + 96f <= first.top)
    }

    @Test
    fun `a range ending where the pointer is gets the bar below its last line`() {
        val first = PdfBarBox(100f, 200f, 500f, 230f)
        val last = PdfBarBox(100f, 400f, 500f, 430f)
        val placed = place(listOf(first, last), active = last)!!
        assertEquals(440f, placed.y, 0.01f)
        assertTrue("and nothing of the range is under it", placed.y >= last.bottom)
    }

    @Test
    fun `the active line is recognised among the spans of the range`() {
        val first = PdfBarBox(100f, 200f, 500f, 230f)
        val last = PdfBarBox(100f, 400f, 500f, 430f)
        // The same line handed over as another instance (a shell that rebuilt its rect) still matches.
        val placed = place(listOf(first, last), active = PdfBarBox(100f, 400f, 500f, 430f))!!
        assertEquals(440f, placed.y, 0.01f)
        assertEquals("centred on that line", (100f + 500f) / 2f - 130f, placed.x, 0.01f)
    }

    @Test
    fun `a line the pane cuts off still counts`() {
        val half = PdfBarBox(100f, 1180f, 500f, 1240f)
        val placed = place(listOf(half), active = half)!!
        assertEquals("lifted above the line, inside the pane", 1180f - gap - 96f, placed.y, 0.01f)
        assertTrue(placed.y + 96f <= 1180f)
    }

    @Test
    fun `with the active line scrolled out, the nearest visible line carries the bar`() {
        val offscreen = PdfBarBox(100f, -300f, 500f, -270f)
        val visible = PdfBarBox(100f, 600f, 500f, 630f)
        val other = PdfBarBox(100f, 800f, 500f, 830f)
        val placed = place(listOf(offscreen, visible, other), active = offscreen)!!
        // The nearest visible line carries it, on the side the rest of the range does not cover.
        assertEquals("the visible line closest to the endpoint", 600f - gap - 96f, placed.y, 0.01f)
    }

    @Test
    fun `a selection with nothing visible has no bar`() {
        assertNull(
            "no bar is pinned to a pane that shows none of the range",
            place(listOf(PdfBarBox(100f, -300f, 500f, -270f)), active = null),
        )
        assertNull("and an empty range has none either", place(emptyList(), active = null))
    }

    @Test
    fun `the bar takes the other side when the handle is where it would land`() {
        val line = PdfBarBox(100f, 200f, 300f, 230f)
        // The handle sits under the line, where a centred capsule would fall.
        val handle = PdfBarBox(260f, 236f, 296f, 272f)
        val placed = place(listOf(line), line, avoid = handle)!!
        assertEquals("above the line instead", 200f - gap - 96f, placed.y, 0.01f)
        assertTrue("clear of the touch target", placed.y + 96f <= handle.top)
    }

    @Test
    fun `a wide bar at a large text scale stays inside the pane`() {
        val line = PdfBarBox(500f, 200f, 880f, 230f)
        val placed = place(listOf(line), line, barWidth = 600f)!!
        assertEquals("pinned to the right margin, not past it", 900f - 600f - margin, placed.x, 0.01f)
        assertTrue(placed.x >= margin)
    }

    @Test
    fun `an unmeasured bar over a selection nobody can see is still hidden`() {
        val offscreen = PdfBarBox(100f, -300f, 500f, -270f)
        assertNull(
            "visibility is decided before the capsule is measured",
            place(listOf(offscreen), active = null, barWidth = 0f, barHeight = 0f),
        )
    }

    @Test
    fun `an unmeasured bar parks at the margin instead of the pane's origin`() {
        val line = PdfBarBox(100f, 200f, 500f, 230f)
        val placed = place(listOf(line), line, barWidth = 0f, barHeight = 0f)!!
        assertEquals(PdfBarOffset(margin, margin), placed)
    }

    @Test
    fun `a bar taller than the room left is pulled inside, nearest the line`() {
        val line = PdfBarBox(100f, 200f, 500f, 230f)
        val placed = place(listOf(line), line, barHeight = 1180f)!!
        assertEquals("just inside the bottom edge", 1200f - 1180f - margin, placed.y, 0.01f)
    }
}
