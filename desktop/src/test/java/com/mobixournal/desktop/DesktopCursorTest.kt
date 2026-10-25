package com.mobixournal.desktop

import androidx.compose.ui.input.pointer.PointerIcon
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
     * the pointer. The Hand moves the page and gets the hand, and Space (the hand's pan with any tool
     * picked) wears the hand too.
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
    }

    /**
     * The hand's pointer is the hand **even when a shape's ring is offered** — Space held with the pen
     * picked is the hand's pan, and a ring over it would promise ink that is not going to land.
     */
    @Test
    fun `the hand beats the ring, and a figure wears the ring`() {
        assertEquals(
            "space with any tool is the hand's pan",
            PointerIcon.Hand,
            toolPointerIcon(DesktopTool.RECTANGLE, ringVisible = true, panMode = true),
        )
        assertEquals(
            "and the hand itself",
            PointerIcon.Hand,
            toolPointerIcon(DesktopTool.HAND, ringVisible = false),
        )
        assertTrue(
            "a figure's ring is the pointer, with no arrow beside it",
            toolPointerIcon(DesktopTool.RECTANGLE, ringVisible = true) !=
                toolPointerIcon(DesktopTool.RECTANGLE, ringVisible = false),
        )
    }

    /**
     * Every tool that marks the page wears the ring; the **hand** is the only one that does not.
     *
     * A figure, a circuit, Text, vertical space and the selection all write at the pen's width, and the
     * person drawing at 27 % needs the same width readout they get from the pen — the window used to draw
     * the ring for the pen and the highlighter only, so half the tools acted at a point nobody could see.
     */
    @Test
    fun `every tool but the hand wears the ring`() {
        for (tool in DesktopTool.entries) {
            val ring = toolCursorDiameterPx(tool, 4f, zoom = 1f, density = 1f)
            if (tool == DesktopTool.HAND) {
                assertNull("the hand moves the page and wears the hand instead", ring)
            } else {
                assertTrue("$tool marks the page, so it wears a ring", ring != null && ring > 0f)
            }
        }
    }
}
