package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** The chemistry tool geometry: single/double/triple bonds and the aromatic ring. */
class ChemShapesTest {

    private val eps = 1e-9

    @Test
    fun `a single bond is one line from the drag's start to its end`() {
        val strokes = ChemShapes.bondSingle(10.0, 20.0, 40.0, 50.0, 1.5)
        assertEquals(1, strokes.size)
        assertEquals(2, strokes[0].size)
        assertEquals(10.0, strokes[0][0].x, eps)
        assertEquals(20.0, strokes[0][0].y, eps)
        assertEquals(40.0, strokes[0][1].x, eps)
        assertEquals(50.0, strokes[0][1].y, eps)
    }

    @Test
    fun `a double bond is two parallel lines a gap apart`() {
        val strokes = ChemShapes.bondDouble(0.0, 0.0, 100.0, 0.0, 1.5)
        assertEquals(2, strokes.size)
        // A horizontal drag: the lines are offset vertically by ±gap, running the full length.
        val gap = ChemShapes.bondGapPt(1.5)
        assertEquals(-gap, strokes[0][0].y, eps)
        assertEquals(gap, strokes[1][0].y, eps)
        assertEquals(0.0, strokes[0][0].x, eps)
        assertEquals(100.0, strokes[0][1].x, eps)
        // Both lines are parallel to the drag (same direction vector).
        val dir0 = strokes[0][1].x - strokes[0][0].x to strokes[0][1].y - strokes[0][0].y
        val dir1 = strokes[1][1].x - strokes[1][0].x to strokes[1][1].y - strokes[1][0].y
        assertEquals(dir0.first, dir1.first, eps)
        assertEquals(dir0.second, dir1.second, eps)
    }

    @Test
    fun `a triple bond is three evenly spaced parallel lines with one on the drag line`() {
        val strokes = ChemShapes.bondTriple(0.0, 0.0, 0.0, 100.0, 2.0)
        assertEquals(3, strokes.size)
        val gap = ChemShapes.bondGapPt(2.0)
        // A vertical drag offsets the lines horizontally by −gap, 0 and +gap (whichever side each
        // comes out on), so the middle line lies on the drag itself.
        assertEquals(listOf(-gap, 0.0, gap), strokes.map { it[0].x }.sorted())
    }

    @Test
    fun `a zero-length drag degenerates a multiple bond to one point stroke`() {
        val strokes = ChemShapes.bondDouble(5.0, 5.0, 5.0, 5.0, 1.5)
        assertEquals(1, strokes.size)
        assertEquals(2, strokes[0].size)
    }

    @Test
    fun `the benzene ring is a regular hexagon with an inner circle`() {
        val strokes = ChemShapes.benzeneRing(0.0, 0.0, 200.0, 200.0, 1.5)
        assertEquals("the ring and its inner circle", 2, strokes.size)
        val ring = strokes[0]
        val circle = strokes[1]

        // The ring is closed: its last vertex repeats the first.
        assertEquals(7, ring.size)
        assertEquals(ring[0].x, ring[6].x, eps)
        assertEquals(ring[0].y, ring[6].y, eps)

        // Every corner sits at the same radius (the drag's half-height) about the drag's centre, and
        // consecutive corners are one radius apart — a regular hexagon.
        val r = 100.0
        val centre = 100.0 to 100.0
        val corners = ring.dropLast(1)
        for (pt in corners) assertEquals(r, hypot(pt.x - centre.first, pt.y - centre.second), 1e-6)
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % 6]
            assertEquals("hexagon edge $i", r, hypot(b.x - a.x, b.y - a.y), 1e-6)
        }

        // The inner circle is centred, closed and smaller than the ring.
        val circleR = hypot(circle[0].x - centre.first, circle[0].y - centre.second)
        assertTrue("the circle is inside the ring", circleR > 0.0 && circleR < r)
        assertEquals(circle[0].x, circle.last().x, eps)
        assertEquals(circle[0].y, circle.last().y, eps)
    }

    @Test
    fun `a benzene ring with a degenerate drag falls back to a single bond line`() {
        val strokes = ChemShapes.benzeneRing(10.0, 10.0, 10.0, 10.0, 1.5)
        assertEquals(1, strokes.size)
        assertEquals(2, strokes[0].size)
    }

    @Test
    fun `a thicker pen pushes the bond lines further apart`() {
        assertTrue(ChemShapes.bondGapPt(1.0) < ChemShapes.bondGapPt(3.0))
        assertTrue("the gap is bounded", ChemShapes.bondGapPt(100.0) <= 9.0)
        assertTrue("the gap never vanishes", ChemShapes.bondGapPt(0.0) >= 3.0)
    }

    @Test
    fun `the shape builder emits the chemistry figures as ordinary strokes`() {
        val single = ShapeBuilder.build(ShapeKind.CHEM_BOND_SINGLE, 0.0, 0.0, 50.0, 0.0, 1.5)
        assertEquals(2, single.size)

        val benzene = ShapeBuilder.build(ShapeKind.CHEM_BENZENE, 0.0, 0.0, 100.0, 100.0, 1.5)
        assertTrue("both ring strokes are flattened into the single-stroke call", benzene.size > 2)

        val multi = ShapeBuilder.buildMulti(ShapeKind.CHEM_BOND_TRIPLE, 0.0, 0.0, 0.0, 50.0, 1.5)
        assertTrue(multi != null)
        assertEquals(3, multi!!.size)
        assertTrue(
            "the multi-stroke path and the flat path agree on the ink",
            multi.sumOf { it.size } == ShapeBuilder.build(
                ShapeKind.CHEM_BOND_TRIPLE, 0.0, 0.0, 0.0, 50.0, 1.5,
            ).size,
        )
    }

    @Test
    fun `every chemistry figure keeps the tool's constant width`() {
        for (kind in listOf(
            ShapeKind.CHEM_BOND_SINGLE, ShapeKind.CHEM_BOND_DOUBLE,
            ShapeKind.CHEM_BOND_TRIPLE, ShapeKind.CHEM_BENZENE,
        )) {
            val pts = ShapeBuilder.build(kind, 10.0, 10.0, 110.0, 110.0, 2.25)
            assertTrue("$kind emitted points", pts.isNotEmpty())
            assertTrue("$kind kept its width", pts.all { abs(it.width - 2.25) < eps })
        }
    }
}
