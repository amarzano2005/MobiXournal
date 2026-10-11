package com.mobixournal.render

import com.mobixournal.format.model.Background
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** Grid/rotation snapping: the ruling a style snaps to, and the rounding itself. */
class SnappingTest {

    private fun solid(style: String) = Background.Solid(0xFFFFFFFF.toInt(), style)

    @Test fun graphSnapsBothAxesToTheGridSpacing() {
        assertEquals(BackgroundGrid.GRID_SPACING_PT, Snapping.spacingX(solid("graph")), 1e-9)
        assertEquals(BackgroundGrid.GRID_SPACING_PT, Snapping.spacingY(solid("graph")), 1e-9)
    }

    @Test fun dottedSnapsBothAxesLikeGraph() {
        assertEquals(BackgroundGrid.GRID_SPACING_PT, Snapping.spacingX(solid("dotted")), 1e-9)
        assertEquals(BackgroundGrid.GRID_SPACING_PT, Snapping.spacingY(solid("dotted")), 1e-9)
    }

    @Test fun linedSnapsVerticallyOnlyBecauseItRulesNoVerticals() {
        assertEquals(0.0, Snapping.spacingX(solid("lined")), 1e-9)
        assertEquals(BackgroundGrid.RULE_SPACING_PT, Snapping.spacingY(solid("lined")), 1e-9)
        assertEquals(BackgroundGrid.RULE_SPACING_PT, Snapping.spacingY(solid("ruled")), 1e-9)
    }

    @Test fun plainUnknownAndNonSolidBackgroundsSnapNothing() {
        for (b in listOf(solid("plain"), solid("weird"), Background.Pdf("a.pdf", 1, "absolute"), null)) {
            assertEquals(0.0, Snapping.spacingX(b), 1e-9)
            assertEquals(0.0, Snapping.spacingY(b), 1e-9)
        }
    }

    @Test fun anIsometricSheetRulesNoAxisSoItRingsNoAxisLine() {
        // Its paper is a triangular mesh of ±30° lines — there is no vertical or horizontal rule to
        // snap a single coordinate onto, so the axis-wise spacings are zero and vertical-space
        // snapping (which reads spacingY) has nothing to land on either.
        assertEquals(0.0, Snapping.spacingX(solid("isograph")), 1e-9)
        assertEquals(0.0, Snapping.spacingY(solid("isograph")), 1e-9)
        val l = Snapping.lattice(solid("isograph"), pageWidthPt = 595.0)
        assertTrue("the mesh still rules something to snap to", l.active)
        assertEquals("an axis-wise snap is a pass-through", 23.4, l.snapX(23.4), 1e-9)
        assertEquals("an axis-wise snap is a pass-through", 23.4, l.snapY(23.4), 1e-9)
        // Without a page width its phase is unresolvable, so it rules nothing.
        assertTrue(!Snapping.lattice(solid("isograph")).active)
        assertTrue(!Snapping.lattice(solid("isograph"), pageWidthPt = 0.0).active)
    }

    @Test fun theLatticeRulesTheAxesEachStyleActuallyHas() {
        val graph = Snapping.lattice(solid("graph"))
        assertTrue(graph.active)
        assertEquals(BackgroundGrid.GRID_SPACING_PT, graph.stepX, 1e-9)
        assertEquals(BackgroundGrid.GRID_SPACING_PT, graph.stepY, 1e-9)

        val lined = Snapping.lattice(solid("lined"))
        assertEquals(0.0, lined.stepX, 1e-9)
        assertEquals(BackgroundGrid.RULE_SPACING_PT, lined.stepY, 1e-9)
        assertEquals("a lined sheet leaves x alone", 37.4, lined.snapX(37.4), 1e-9)
        assertEquals(48.0, lined.snapY(52.0), 1e-9)

        for (b in listOf(solid("plain"), Background.Pdf("a.pdf", 1, "absolute"), null)) {
            val l = Snapping.lattice(b)
            assertTrue("$b rules nothing to snap to", !l.active)
            assertEquals(23.4, l.snapX(23.4), 1e-9)
            assertEquals(23.4, l.snapY(23.4), 1e-9)
        }
    }

    /**
     * The isometric snap is measured against the paper **the renderer draws**, not against a formula
     * repeated from the implementation: the nodes are taken as the actual crossings of the segments
     * [BackgroundGrid.isometric] emits, and every probe must land on the nearest of those.
     */
    @Test fun anIsometricSheetSnapsInkOntoTheNodesOfItsOwnDrawnMesh() {
        val width = 595.276
        val height = 841.89
        val side = BackgroundGrid.ISO_SPACING_PT
        val nodes = meshNodes(width, height, side)
        assertTrue("the mesh has nodes to land on (${nodes.size})", nodes.size > 100)

        val l = Snapping.lattice(Background.Solid(0xFFFFFFFF.toInt(), "isograph"), pageWidthPt = width)
        var worst = 0.0
        var moved = 0
        for (i in 1..20) {
            for (j in 1..20) {
                val x = width * i / 21.0 + 0.37
                val y = height * j / 21.0 + 0.11
                val (sx, sy) = l.snap(x, y)
                val nearest = nodes.minByOrNull { (nx, ny) -> (x - nx) * (x - nx) + (y - ny) * (y - ny) }!!
                val d = kotlin.math.hypot(sx - nearest.first, sy - nearest.second)
                if (d > worst) worst = d
                if (kotlin.math.hypot(sx - x, sy - y) > 1e-9) moved++
            }
        }
        assertTrue("every probe snapped somewhere ($moved of 400)", moved > 300)
        assertEquals("every probe landed on a node the paper really draws", 0.0, worst, 1e-6)
    }

    @Test fun aFinerIsometricSheetSnapsToItsOwnFinerNodes() {
        val width = 595.276
        val height = 841.89
        val fine = Snapping.lattice(
            Background.Solid(0xFFFFFFFF.toInt(), "isograph", "r1=14.173"),
            pageWidthPt = width,
        )
        val nodes = meshNodes(width, height, 14.173)
        val (sx, sy) = fine.snap(200.5, 300.25)
        val nearest = nodes.minByOrNull { (nx, ny) ->
            (200.5 - nx) * (200.5 - nx) + (300.25 - ny) * (300.25 - ny)
        }!!
        assertEquals(nearest.first, sx, 1e-6)
        assertEquals(nearest.second, sy, 1e-6)
    }

    /**
     * The crossings of the two ±30° families [BackgroundGrid.isometric] draws, i.e. the nodes the ruled
     * paper really shows. Each pair of one segment from each family crosses at most once, and a crossing
     * inside both segments is a node of the sheet.
     */
    private fun meshNodes(width: Double, height: Double, side: Double): List<Pair<Double, Double>> {
        val segments = BackgroundGrid.isometric(width, height, side)
        // The two families are the ±30° ones: one descends (its y grows), the other ascends.
        val up = segments.filter { it[3] > it[1] }
        val down = segments.filter { it[3] < it[1] }
        val out = ArrayList<Pair<Double, Double>>()
        for (a in up) {
            for (b in down) {
                val ax = a[0]; val ay = a[1]; val adx = a[2] - a[0]; val ady = a[3] - a[1]
                val bx = b[0]; val by = b[1]; val bdx = b[2] - b[0]; val bdy = b[3] - b[1]
                val denom = adx * bdy - ady * bdx
                if (kotlin.math.abs(denom) < 1e-12) continue
                val ex = bx - ax
                val ey = by - ay
                val t = (ex * bdy - ey * bdx) / denom
                val u = (ex * ady - ey * adx) / denom
                if (t < -1e-9 || t > 1 + 1e-9 || u < -1e-9 || u > 1 + 1e-9) continue
                out += (ax + t * adx) to (ay + t * ady)
            }
        }
        return out
    }

    @Test fun aGridWithAMarginSnapsOntoTheLinesTheRendererDraws() {
        // A graph page rules from its `m1` margin, so its lines lie on 20, 34.17, 48.34 … — snapping
        // to bare multiples of the spacing would pull ink a third of a line off the drawn grid.
        val page = Background.Solid(0xFFFFFFFF.toInt(), "graph", "r1=14.17,m1=20")
        val l = Snapping.lattice(page)
        assertEquals(20.0, l.phaseX, 1e-9)
        assertEquals(20.0, l.phaseY, 1e-9)
        val (x, y) = l.snap(33.0, 47.0)
        assertEquals("x lands on the ruled line 20 + 14.17", 34.17, x, 1e-9)
        assertEquals("y lands on the ruled line 20 + 2×14.17", 48.34, y, 1e-9)
        // The margin is inside the first line, so a point just inside it snaps to the margin itself.
        assertEquals(20.0, l.snapX(21.0), 1e-9)
    }

    @Test fun snapRoundsToTheNearestMultiple() {
        assertEquals(20.0, Snapping.snap(23.0, 10.0), 1e-9)
        assertEquals(30.0, Snapping.snap(26.0, 10.0), 1e-9)
        assertEquals(-30.0, Snapping.snap(-27.0, 10.0), 1e-9)
        assertEquals(0.0, Snapping.snap(4.9, 10.0), 1e-9)
    }

    @Test fun snapLeavesValuesAloneWhenSpacingIsNonPositive() {
        assertEquals(23.4, Snapping.snap(23.4, 0.0), 1e-9)
        assertEquals(23.4, Snapping.snap(23.4, -5.0), 1e-9)
    }

    @Test fun snapAngleLandsOnFifteenDegreeSteps() {
        val step = 15.0 * PI / 180.0
        assertEquals(step, Snapping.snapAngle(step * 1.2), 1e-9)
        assertEquals(0.0, Snapping.snapAngle(step * 0.4), 1e-9)
        assertEquals(-2 * step, Snapping.snapAngle(-step * 1.9), 1e-9)
        assertEquals(PI / 2, Snapping.snapAngle(PI / 2 + 0.01), 1e-9)
    }

    @Test fun snapAngleWithANonPositiveStepIsAPassThrough() {
        assertEquals(0.37, Snapping.snapAngle(0.37, 0.0), 1e-9)
    }
}
