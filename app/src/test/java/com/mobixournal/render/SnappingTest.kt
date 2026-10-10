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

        for (b in listOf(solid("plain"), solid("isograph"), Background.Pdf("a.pdf", 1, "absolute"), null)) {
            val l = Snapping.lattice(b)
            assertTrue("$b rules nothing to snap to", !l.active)
            assertEquals(23.4, l.snapX(23.4), 1e-9)
            assertEquals(23.4, l.snapY(23.4), 1e-9)
        }
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
