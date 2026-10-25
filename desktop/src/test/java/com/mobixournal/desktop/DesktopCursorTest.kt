package com.mobixournal.desktop

import com.mobixournal.render.eraserRadiusPt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pen's pointer: a ring the size of the line the tool will write.
 *
 * What the window must not get wrong is the size: a ring that says 4 pt and a stroke that lands at 12 pt
 * is worse than the arrow it replaced. The eraser is the interesting case — its geometry is a **radius**
 * (`eraserRadiusPt`), so the ring is not the pen's width doubled, and a test that assumed so would pass
 * while the window drew half a rubber.
 */
class DesktopCursorTest {

    @Test
    fun `the ring is the tool's width on screen, with a floor`() {
        // A 10 pt pen at 100 % on a 1× display: 10 pixels across.
        assertEquals(10f, toolCursorDiameterPx(DesktopTool.PEN, 10f, zoom = 1f, density = 1f)!!, 1e-4f)
        // At 200 % the line is twice as wide on screen, so the ring is too.
        assertEquals(20f, toolCursorDiameterPx(DesktopTool.PEN, 10f, zoom = 2f, density = 1f)!!, 1e-4f)
        assertEquals(
            "and a highlighter is a band of its own width",
            10.0,
            toolCursorDiameterPx(DesktopTool.HIGHLIGHTER, 10f, zoom = 1f, density = 1f)!!.toDouble(),
            1e-4,
        )
        assertEquals(
            "a 0.5 pt pen still has to be findable",
            CURSOR_MIN_PX.toDouble(),
            toolCursorDiameterPx(DesktopTool.PEN, 0.5f, zoom = 1f, density = 1f)!!.toDouble(),
            1e-4,
        )
    }

    @Test
    fun `the eraser's ring is its radius doubled, not its width`() {
        val ring = toolCursorDiameterPx(DesktopTool.ERASER, 10f, zoom = 1f, density = 1f)!!
        assertEquals(
            "the shared eraser geometry decides the size",
            eraserRadiusPt(10f) * 2.0,
            ring.toDouble(),
            1e-4,
        )
        assertTrue("and it is wider than the pen's ring at the same setting", ring > 10f)
    }

    /**
     * The pointer over the sheet is the **tool's**: no arrow beside the ring, the hand for the hand.
     *
     * A ring drawn beside the mouse's arrow pointed at nothing — two marks for one point, one of them
     * saying the wrong thing — so a tool whose ring is up wears a cursor with no picture, and the ring is
     * the pointer. The Hand moves the page and gets the hand; every other slot keeps the arrow it acts
     * with, and Space (the hand's pan with any tool picked) wears the hand too.
     */
    @Test
    fun `the pointer is the tool's, not always the arrow`() {
        val blank = toolPointerIcon(DesktopTool.PEN, ringVisible = true)
        assertEquals("a drawing tool's ring is the pointer", blank, toolPointerIcon(DesktopTool.ERASER, true))
        assertEquals("and a highlighter's too", blank, toolPointerIcon(DesktopTool.HIGHLIGHTER, true))
        assertTrue(
            "which is not the desktop's arrow",
            blank != toolPointerIcon(DesktopTool.PEN, ringVisible = false),
        )
        assertEquals(
            "the hand pans, so it wears the hand",
            toolPointerIcon(DesktopTool.HAND, ringVisible = false),
            toolPointerIcon(DesktopTool.PEN, ringVisible = false, panMode = true),
        )
        assertEquals(
            "a figure or a selection keeps the arrow",
            toolPointerIcon(DesktopTool.PEN, ringVisible = false),
            toolPointerIcon(DesktopTool.RECTANGLE, ringVisible = false),
        )
    }

    @Test
    fun `a tool that draws nothing keeps the desktop's arrow`() {
        for (tool in DesktopTool.entries - setOf(
            DesktopTool.PEN,
            DesktopTool.HIGHLIGHTER,
            DesktopTool.ERASER,
            DesktopTool.ERASER_WHOLE,
        )) {
            assertNull("$tool has no ring", toolCursorDiameterPx(tool, 4f, zoom = 1f, density = 1f))
        }
    }
}
