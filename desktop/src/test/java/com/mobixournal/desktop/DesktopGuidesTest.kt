package com.mobixournal.desktop

import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a placed guide does to the **ink**: the projection itself, page by page.
 *
 * The reach limit and the geometry are `DrawingGuideTest`'s (they live in `mobix-core`, because the tablet
 * applies them too); what this suite owns is the window's rule — a guide rules only its own page, and the
 * points it pulls are the points the stroke is written with.
 */
class DesktopGuidesTest {

    private val eps = 1e-9

    /** A setsquare whose long leg runs along y = 245 from x = 110 to x = 290. */
    private fun setsquare(): DesktopGuidePose =
        placedGuideOn(GuideKind.SETSQUARE, page = 0, centre = 200.0 to 200.0)!!

    @Test
    fun `a wobbling stroke beside the long leg is written along it`() {
        val pose = setsquare()
        // Every vertex is nearer the long leg than the hypotenuse: near the tip the triangle's slant
        // side is the closest edge, which is the geometry's answer and not this suite's to override.
        val wobble = listOf(140.0 to 250.0, 175.0 to 247.5, 210.0 to 252.0, 235.0 to 248.0, 225.0 to 250.5)
        val projected = wobble.map { (x, y) -> pose.project(0, x, y) }
        for ((index, point) in projected.withIndex()) {
            assertEquals("vertex $index came onto the edge", 245.0, point.second, eps)
            assertEquals("and kept its place along it", wobble[index].first, point.first, eps)
        }
        // The whole stroke is one line: every vertex is on the same y, which is what makes it straight.
        assertEquals(1, projected.map { it.second }.toSet().size)
    }

    @Test
    fun `a point out of reach of every edge is the hand's own`() {
        val pose = setsquare()
        // The incenter of the triangle is ~38 pt from each side, so a point in the middle is out of
        // reach of all three edges and the pen keeps it, which is what lets it leave the instrument.
        assertEquals(170.0 to 280.0, pose.project(0, 170.0, 280.0))
    }

    @Test
    fun `a guide rules its own page and no other`() {
        val pose = setsquare()
        val point = 200.0 to 250.0
        assertEquals(point, pose.project(1, point.first, point.second))
        assertEquals(point, pose.project(7, point.first, point.second))
        val none: DesktopGuidePose? = null
        assertEquals(point, none.project(0, point.first, point.second))
    }

    @Test
    fun `the compass pulls a swept sample onto its circumference`() {
        val pose = placedGuideOn(GuideKind.COMPASS, page = 0, centre = 100.0 to 100.0)!!
        val radius = (pose.guide as DrawingGuide.Compass).radius
        val (x, y) = pose.project(0, 100.0 + radius + 6.0, 100.0)
        assertEquals(100.0 + radius, x, eps)
        assertEquals(100.0, y, eps)
        // And a point well inside the circle is left where it is: the instrument rules its edge, not its disc.
        assertEquals(100.0 + 40.0 to 100.0, pose.project(0, 100.0 + 40.0, 100.0))
    }

    @Test
    fun `the ruling and chemistry rules pull every vertex, unlike an instrument`() {
        // They have no reach limit: that is the whole difference between a physical instrument and a rule
        // drawn under the paper, and the window inherits it because the maths is shared.
        val lattice = com.mobixournal.render.Snapping.Lattice(stepX = 20.0, stepY = 20.0, phaseX = 0.0, phaseY = 0.0)
        val ruling = DesktopGuidePose(0, DrawingGuide.Ruling(0.0, 0.0, lattice))
        assertEquals(20.0 to 20.0, ruling.project(0, 27.0, 26.0))
        val chemistry = DesktopGuidePose(0, DrawingGuide.Chemistry(0.0, 0.0, spacingPt = 40.0))
        val (nx, ny) = chemistry.project(0, 23.0, 33.0)
        assertTrue("a bond node is where the lattice says", hypot(nx - 20.0, ny - 40.0 * kotlin.math.sqrt(3.0) / 2.0) < 1e-6)
    }

    @Test
    fun `the placement is the shared pose, and it comes back by kind`() {
        val pose = setsquare()
        assertEquals(GuideKind.SETSQUARE, pose.kind)
        assertEquals(GuideKind.COMPASS, placedGuideOn(GuideKind.COMPASS, 2, 10.0 to 10.0)!!.kind)
        assertEquals(2, placedGuideOn(GuideKind.COMPASS, 2, 10.0 to 10.0)!!.page)
        assertEquals(
            "a fresh instrument is the catalogue's default, not a second opinion",
            DrawingGuide.DEFAULT_SIZE_PT,
            (pose.guide as DrawingGuide.Setsquare).size,
            eps,
        )
    }
}
