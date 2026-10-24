package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared selection: what a traced region **catches**, and what each edit does to it.
 *
 * Both shells draw from this, so a mistake here is two editors selecting different ink for the same drag.
 * The cases pin the rules that are easy to break: a lasso keeps the diagonal stroke its bounding box would
 * drop, a move takes its outline with it, a resize keeps the anchor still and the ink's width unchanged,
 * and a rotation turns the outline into a polygon because a rotated rectangle is not a rectangle.
 */
class PageSelectionTest {

    private fun stroke(x0: Double, y0: Double, x1: Double, y1: Double) = Stroke(
        Tool.PEN, 0, "round",
        listOf(StrokePoint(x0, y0, 1.0), StrokePoint(x1, y1, 1.0)),
        uniformWidth = true,
    )

    private fun page(vararg layers: Layer) =
        Page(200.0, 400.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), layers.toList())

    private fun pageOf(vararg elements: com.mobixournal.format.model.Element) = page(Layer(elements.toList()))

    @Test
    fun `a marquee catches the elements it encloses and nothing else`() {
        val page = pageOf(stroke(10.0, 10.0, 40.0, 40.0), stroke(120.0, 120.0, 180.0, 180.0))
        val region = rectRegion(0.0, 0.0, 60.0, 60.0)
        assertEquals(setOf(ElementRef(0, 0)), region.catches(page))
    }

    @Test
    fun `a lasso keeps the diagonal stroke its bounding box would drop`() {
        // A stroke from (10,10) to (50,50): its box's corners (10,50) and (50,10) are outside the loop,
        // so a box rule would miss it — the reason `SelectionTester.inPolygon` tests a stroke's own points.
        val page = pageOf(stroke(10.0, 10.0, 50.0, 50.0))
        val loop = SelectionRegion.Polygon(
            listOf(Vec2(0.0, 0.0), Vec2(60.0, 0.0), Vec2(60.0, 60.0), Vec2(0.0, 60.0)),
        )
        assertEquals(setOf(ElementRef(0, 0)), loop.catches(page))
    }

    @Test
    fun `a rectangle drag normalises whatever corner it started from`() {
        assertEquals(Bounds(10.0, 20.0, 40.0, 50.0), rectRegion(40.0, 50.0, 10.0, 20.0).box)
    }

    @Test
    fun `a move takes its outline with it`() {
        val page = pageOf(stroke(10.0, 10.0, 40.0, 40.0))
        val selection = PageSelection(rectRegion(0.0, 0.0, 60.0, 60.0), setOf(ElementRef(0, 0)))
        val (moved, after) = SelectionEdit.move(page, selection, 100.0, 25.0)
        assertEquals(109.5, ElementBounds.of(moved.layers[0].elements[0]).left, 1e-6)
        assertEquals("the outline follows the ink", 100.0, after.bounds.left, 1e-6)
        assertEquals("the caught elements are the same ones", selection.refs, after.refs)
    }

    @Test
    fun `a resize keeps the anchor still and the ink at its width`() {
        val page = pageOf(stroke(10.0, 10.0, 30.0, 10.0))
        val selection = PageSelection(rectRegion(0.0, 0.0, 40.0, 40.0), setOf(ElementRef(0, 0)))
        val (scaled, after) = SelectionEdit.scale(page, selection, 2.0, 2.0, anchorX = 0.0, anchorY = 0.0)
        val box = ElementBounds.of(scaled.layers[0].elements[0])
        assertEquals("the far end doubled away from the anchor", 60.0, box.right, 0.5)
        assertEquals("0 stays 0", 10.0, box.left - (box.left - 10.0), 1e-6)
        val ink = (scaled.layers[0].elements[0] as Stroke).points
        assertEquals("a figure is resized, not thickened", 1.0, ink[0].width, 1e-6)
        assertEquals("the outline grew with it", 80.0, after.bounds.right, 1e-6)
    }

    @Test
    fun `a rotation turns the outline into a polygon and keeps the centre`() {
        val page = pageOf(stroke(40.0, 50.0, 60.0, 50.0))
        val selection = PageSelection(rectRegion(40.0, 40.0, 60.0, 60.0), setOf(ElementRef(0, 0)))
        val (turned, after) = SelectionEdit.rotate(page, selection, Math.PI / 2)
        assertTrue("a rotated rectangle is not a rectangle", after.region is SelectionRegion.Polygon)
        assertEquals(50.0, (after.bounds.left + after.bounds.right) / 2.0, 1e-6)
        assertEquals(50.0, (after.bounds.top + after.bounds.bottom) / 2.0, 1e-6)
        val points = (turned.layers[0].elements[0] as Stroke).points
        assertEquals("a quarter turn stands the stroke up", 50.0, (points[0].x + points[1].x) / 2.0, 1e-6)
        assertEquals(40.0, points[0].y, 1e-6)
    }

    @Test
    fun `delete removes exactly the caught elements`() {
        val page = pageOf(stroke(10.0, 10.0, 40.0, 40.0), stroke(120.0, 120.0, 140.0, 140.0))
        val selection = PageSelection(rectRegion(0.0, 0.0, 60.0, 60.0), setOf(ElementRef(0, 0)))
        val left = SelectionEdit.delete(page, selection)
        assertEquals(1, left.layers[0].elements.size)
        assertEquals(120.0, ElementBounds.of(left.layers[0].elements[0]).left, 0.5)
    }

    @Test
    fun `an empty selection edits nothing`() {
        val page = pageOf(stroke(10.0, 10.0, 40.0, 40.0))
        val empty = PageSelection(rectRegion(80.0, 80.0, 100.0, 100.0), emptySet())
        assertTrue(empty.isEmpty)
        assertSame("a move with nothing caught hands the page back", page, SelectionEdit.move(page, empty, 5.0, 5.0).first)
        assertSame(page, SelectionEdit.delete(page, empty))
    }

    @Test
    fun `a point is inside the loop only when it is`() {
        val loop = SelectionRegion.Polygon(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0)))
        assertTrue(loop.inside(8.0, 2.0))
        assertFalse(loop.inside(2.0, 8.0))
    }

    @Test
    fun `a group of elements shares one outline`() {
        val page = pageOf(stroke(10.0, 10.0, 30.0, 30.0), stroke(50.0, 50.0, 70.0, 70.0))
        val refs = setOf(ElementRef(0, 0), ElementRef(0, 1))
        val selection = PageSelection(rectRegion(0.0, 0.0, 80.0, 80.0), refs)
        assertEquals("both caught together", refs, selection.region.catches(page))
        val (moved, after) = SelectionEdit.move(page, selection, 10.0, 0.0)
        val boxes = moved.layers[0].elements.map { ElementBounds.of(it).left }
        assertTrue("both moved", boxes.all { it > 19.0 })
        assertEquals(10.0, after.bounds.left, 1e-6)
    }
}
