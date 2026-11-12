package com.mobixournal.desktop

import com.mobixournal.desktop.sheet.SelectionHandle
import com.mobixournal.desktop.sheet.shaftStretch
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The window's **length** drag on a line or an arrow: the dragged end follows the pointer, the far end stays
 * put, and nothing else about the drawing changes — the head keeps the size it was drawn with, because a
 * figure is resized, never thickened (`FigureRedraw`).
 */
class ShaftStretchTest {

    /** `FigureGeometry`'s arrow: tail, tip, barbA, tip, barbB. */
    private val arrow = Stroke(
        tool = Tool.PEN,
        color = 0,
        capStyle = null,
        points = listOf(
            StrokePoint(100.0, 100.0, 1.0),
            StrokePoint(300.0, 100.0, 1.0),
            StrokePoint(280.0, 88.0, 1.0),
            StrokePoint(300.0, 100.0, 1.0),
            StrokePoint(280.0, 112.0, 1.0),
        ),
        uniformWidth = true,
    )

    private val page = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(listOf(arrow))),
    )

    private val selection = PageSelection(
        SelectionRegion.Rect(Bounds(100.0, 88.0, 300.0, 112.0)),
        setOf(ElementRef(0, 0)),
    )

    private fun edited(handle: SelectionHandle, x0: Double, x1: Double): Stroke {
        val (page, _) = shaftStretch(page, selection, handle, x0 = x0, y0 = 100.0, x1 = x1, y1 = 100.0)
        return page.layers[0].elements[0] as Stroke
    }

    @Test
    fun `dragging the tip doubles the arrow about its tail`() {
        val stroke = edited(SelectionHandle.SHAFT_TIP, x0 = 300.0, x1 = 500.0)
        assertEquals("the tail is the anchor", 100.0, stroke.points[0].x, 1e-6)
        assertEquals("the tip follows the pointer", 500.0, stroke.points[1].x, 1e-6)
        // The head is re-emitted at the size it was drawn with (`FigureRedraw`): 20 pt back, 12 pt across.
        assertEquals("the head keeps its own size", 480.0, stroke.points[2].x, 1e-6)
        assertEquals("the barbs keep their own reach", 88.0, stroke.points.minOf { it.y }, 1e-6)
        assertEquals(112.0, stroke.points.maxOf { it.y }, 1e-6)
    }

    @Test
    fun `dragging the tail doubles the arrow about its tip`() {
        val stroke = edited(SelectionHandle.SHAFT_TAIL, x0 = 100.0, x1 = -100.0)
        assertEquals("the tail follows the pointer", -100.0, stroke.points[0].x, 1e-6)
        assertEquals("the tip stays", 300.0, stroke.points[1].x, 1e-6)
    }
}
