package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drag that lets the **ticker** apply it, instead of applying itself per sample.
 *
 * Vertical space rewrites every element below the grab line, a few milliseconds on a dense sheet, and a pen
 * reports several hundred samples a second: run per sample, that work saturated the thread the samples
 * arrive on and the drag fell behind the hand. The contract this suite pins is the fix's whole mechanism —
 * `moved` records where the pointer is and nothing else, `tick` is the one place a flush happens (on the
 * canvas's own 8 ms ticker), and a tick that finds nothing new costs nothing.
 *
 * A drag that did not ask for it keeps the old behaviour, so figures, guides and the selection are untouched:
 * they apply themselves, and the ticker must not apply them a second time.
 */
class DragViewFlushTest {

    private val applies = mutableListOf<Float>()

    private fun view(): DragView = DragView(
        viewport = { Viewport() },
        pageW = 595f,
        pageH = 842f,
        viewW = 595f,
        viewH = 842f,
        density = 1f,
        onViewport = {},
    )

    @Test
    fun `a flushed drag applies once per tick, at the pointer's latest position`() {
        val view = view()
        view.beginDrag(100f, 100f, flushEveryTick = true) { _, y -> applies += y }
        repeat(10) { view.moved(100f, 100f + it) }
        assertEquals("no sample re-applied the drag on its own", 0, applies.size)

        view.tick()
        assertEquals("one tick, one apply, at the last sample", listOf(109f), applies)

        view.tick()
        assertEquals("a tick with no motion costs nothing at all", listOf(109f), applies)

        view.moved(100f, 100f)
        view.tick()
        assertEquals("moving back is motion again", listOf(109f, 100f), applies)
    }

    @Test
    fun `a drag that did not ask is never applied by the ticker`() {
        val view = view()
        view.beginDrag(100f, 100f) { _, y -> applies += y }
        view.moved(100f, 140f)
        view.tick()
        assertEquals("a figure's drag applies itself, so the ticker leaves it alone", 0, applies.size)
    }

    @Test
    fun `nothing survives the end of the drag`() {
        val view = view()
        view.beginDrag(100f, 100f, flushEveryTick = true) { _, y -> applies += y }
        view.moved(100f, 140f)
        view.endDrag()
        view.tick()
        assertEquals(0, applies.size)
    }
}
