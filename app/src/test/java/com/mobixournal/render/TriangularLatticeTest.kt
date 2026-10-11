package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The 60° node lattice behind the chemistry guide: node snapping, the ring's geometry, the mesh. */
class TriangularLatticeTest {

    private val eps = 1e-9

    @Test
    fun `a point snaps to the nearest node`() {
        val s = 40.0
        // Just off the origin node.
        assertEquals(0.0 to 0.0, TriangularLattice.nearestNode(7.0, 5.0, s))
        // Near the node one row up and half a bond right: (s/2, s·√3/2).
        val (nx, ny) = TriangularLattice.nearestNode(s / 2 + 3.0, TriangularLattice.rowHeight(s) - 2.0, s)
        assertEquals(s / 2, nx, eps)
        assertEquals(TriangularLattice.rowHeight(s), ny, eps)
    }

    @Test
    fun `every node the snap returns is a real lattice node`() {
        val s = 37.0
        val h = TriangularLattice.rowHeight(s)
        for (i in -20..20) {
            for (j in -20..20) {
                val (x, y) = TriangularLattice.nearestNode(i * 3.7, j * 4.1, s)
                // Re-express as integer lattice coordinates and require them to be exact integers.
                val b = y / h
                val a = x / s - b / 2.0
                assertTrue("node ($x, $y) is on the lattice", kotlin.math.abs(a - Math.round(a)) < 1e-6)
                assertTrue("node ($x, $y) is on the lattice", kotlin.math.abs(b - Math.round(b)) < 1e-6)
            }
        }
    }

    @Test
    fun `the six nearest neighbours are a regular hexagon of edge length s`() {
        val s = 40.0
        val ring = TriangularLattice.hexagon(0.0, 0.0, s)
        assertEquals(6, ring.size)
        // Every ring vertex is a node at exactly one bond from the centre...
        assertTrue(ring.all { (x, y) -> kotlin.math.abs(hypot(x, y) - s) < 1e-9 })
        // ... and it is the six nodes the snap reaches from the centre, one per 60°.
        for ((x, y) in ring) {
            val (nx, ny) = TriangularLattice.nearestNode(x * 0.6, y * 0.6, s)
            assertEquals("neighbour ($x, $y) is reachable", x, nx, 1e-9)
            assertEquals("neighbour ($x, $y) is reachable", y, ny, 1e-9)
        }
        // Consecutive vertices are one bond apart, which is what makes benzene a regular hexagon.
        for (i in ring.indices) {
            val (ax, ay) = ring[i]
            val (bx, by) = ring[(i + 1) % 6]
            assertEquals("edge $i", s, hypot(bx - ax, by - ay), 1e-9)
        }
    }

    @Test
    fun `a non-positive spacing rules nothing`() {
        assertEquals(12.0 to 34.0, TriangularLattice.nearestNode(12.0, 34.0, 0.0))
        assertEquals(12.0 to 34.0, TriangularLattice.nearestNode(12.0, 34.0, -5.0))
    }

    @Test
    fun `the anchor is itself a node, so the lattice shifts with it`() {
        val s = 30.0
        val anchorX = 100.0
        val anchorY = 200.0
        // The anchor snaps to itself.
        assertEquals(anchorX to anchorY, TriangularLattice.nearestNode(anchorX, anchorY, s, anchorX, anchorY))
        // A node one bond away, in lattice coordinates, snaps back onto it.
        val h = TriangularLattice.rowHeight(s)
        val node = TriangularLattice.nearestNode(anchorX + s + 2.0, anchorY - 1.0, s, anchorX, anchorY)
        assertEquals(anchorX + s, node.first, eps)
        assertEquals(anchorY, node.second, eps)
        assertEquals("the rows still step by h", h, TriangularLattice.rowHeight(s), eps)
    }

    @Test
    fun `the mesh stays on the sheet and carries all three bond directions`() {
        val s = 40.0
        val segments = TriangularLattice.mesh(400.0, 600.0, s)
        assertTrue("the mesh is not empty", segments.isNotEmpty())
        // Nothing is ever drawn off the sheet.
        for (seg in segments) {
            for (x in listOf(seg[0], seg[2])) assertTrue("in-sheet x $x", x >= -1e-9 && x <= 400.0 + 1e-9)
            for (y in listOf(seg[1], seg[3])) assertTrue("in-sheet y $y", y >= -1e-9 && y <= 600.0 + 1e-9)
        }
        // Three families: horizontal, +60° and −60°. A line has no head or tail, so each angle is
        // folded into (−90°, 90°] before comparing.
        val angles = segments.map { seg ->
            val a = kotlin.math.atan2(seg[3] - seg[1], seg[2] - seg[0])
            ((a + Math.PI / 2) % Math.PI + Math.PI) % Math.PI - Math.PI / 2
        }
        assertTrue("a horizontal bond", angles.any { kotlin.math.abs(it) < 1e-9 })
        assertTrue("a +60° bond", angles.any { kotlin.math.abs(it - Math.PI / 3) < 1e-9 })
        assertTrue("a −60° bond", angles.any { kotlin.math.abs(it + Math.PI / 3) < 1e-9 })
    }

    @Test
    fun `the mesh is empty for a sheet or spacing with nothing to rule`() {
        assertTrue(TriangularLattice.mesh(0.0, 600.0, 40.0).isEmpty())
        assertTrue(TriangularLattice.mesh(400.0, 600.0, 0.0).isEmpty())
    }
}
