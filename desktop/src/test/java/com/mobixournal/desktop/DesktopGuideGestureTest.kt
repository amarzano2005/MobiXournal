package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.DesktopGuidePose
import com.mobixournal.desktop.sheet.GUIDE_HANDLE_PX
import com.mobixournal.desktop.sheet.GuideGrab
import com.mobixournal.desktop.sheet.guideDragged
import com.mobixournal.desktop.sheet.guidePress
import com.mobixournal.desktop.sheet.guideReachPt
import com.mobixournal.desktop.sheet.placedGuideOn

import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import com.mobixournal.render.Snapping
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand on the instrument, without a window: which part a press took and what a drag does to the pose.
 *
 * The rules under test are the tablet's, reached through the shared `DrawingGuide` — a body press slides,
 * a handle press aims or opens, the snap is `Snapping.snapAngle`'s, and the drawing edge is deliberately
 * the pen's, so a stroke drawn along it never drags the instrument away.
 */
class DesktopGuideGestureTest {

    private val eps = 1e-6

    /**
     * A setsquare whose anchor is the right-angle corner (110, 245): the long leg runs along y = 245 to
     * (290, 245), and the triangle's body reaches down to (110, 349) — the shared `GuideKind.place` pose.
     */
    private fun setsquare(): DesktopGuidePose =
        placedGuideOn(GuideKind.SETSQUARE, page = 0, centre = 200.0 to 200.0)!!

    @Test
    fun `a press on the body takes the body and a press on the edge takes nothing`() {
        val pose = setsquare()
        assertEquals(GuideGrab.BODY, guidePress(pose, page = 0, px = 150.0, py = 300.0, reachPt = 12.0))
        assertEquals(
            "the long leg is the pen's, not a grab",
            GuideGrab.NONE,
            guidePress(pose, page = 0, px = 200.0, py = 230.0, reachPt = 12.0),
        )
        assertEquals(GuideGrab.NONE, guidePress(pose, page = 0, px = 500.0, py = 500.0, reachPt = 12.0))
    }

    @Test
    fun `a press on the long-leg tip takes the handle`() {
        val pose = setsquare()
        // The handle is the long-leg tip; a press within reach of it aims the instrument.
        assertEquals(GuideGrab.TIP, guidePress(pose, page = 0, px = 292.0, py = 247.0, reachPt = 12.0))
    }

    @Test
    fun `a body drag slides the anchor by exactly its travel`() {
        val start = setsquare()
        val anchorX = start.guide.x
        val anchorY = start.guide.y
        val moved = guideDragged(
            start = start,
            grab = GuideGrab.BODY,
            // The press landed 30 pt right and 20 pt below the anchor, so the body does not jump under it.
            grabDx = 30.0,
            grabDy = 20.0,
            px = anchorX + 30.0 + 40.0,
            py = anchorY + 20.0 + 40.0,
            snapAngle = false,
        )
        assertEquals(anchorX + 40.0, moved.guide.x, eps)
        assertEquals(anchorY + 40.0, moved.guide.y, eps)
        assertEquals("sliding never changes the size", (start.guide as DrawingGuide.Setsquare).size,
            (moved.guide as DrawingGuide.Setsquare).size, eps)
    }

    @Test
    fun `a tip drag aims the setsquare, and Shift lands on 45 degrees`() {
        val start = setsquare()
        val anchor = start.guide.x to start.guide.y
        // 135 pt right and 135 pt up from the anchor is exactly -45 degrees, measured from page +X.
        val target = (anchor.first + 135.0) to (anchor.second - 135.0)
        val free = guideDragged(start, GuideGrab.TIP, 0.0, 0.0, target.first, target.second, snapAngle = false)
        assertEquals(-PI / 4, (free.guide as DrawingGuide.Setsquare).angle, eps)
        assertEquals(kotlin.math.hypot(135.0, 135.0), (free.guide as DrawingGuide.Setsquare).size, eps)

        // A hand that stopped a few degrees short lands on the step when Shift holds it.
        val nearly = (anchor.first + 134.0) to (anchor.second - 131.0)
        val raw = kotlin.math.atan2(nearly.second - anchor.second, nearly.first - anchor.first)
        val snapped = guideDragged(start, GuideGrab.TIP, 0.0, 0.0, nearly.first, nearly.second, snapAngle = true)
        assertEquals(Snapping.snapAngle(raw), (snapped.guide as DrawingGuide.Setsquare).angle, eps)
    }

    @Test
    fun `a tip drag opens the compass to the drag distance`() {
        val start = placedGuideOn(GuideKind.COMPASS, page = 0, centre = 100.0 to 100.0)!!
        val opened = guideDragged(start, GuideGrab.TIP, 0.0, 0.0, 100.0, 160.0, snapAngle = false)
        assertEquals(60.0, (opened.guide as DrawingGuide.Compass).radius, eps)
        val tiny = guideDragged(start, GuideGrab.TIP, 0.0, 0.0, 100.0, 101.0, snapAngle = false)
        assertEquals(
            "a drag never shrinks the instrument to an unhittable point",
            DrawingGuide.MIN_SIZE_PT,
            (tiny.guide as DrawingGuide.Compass).radius,
            eps,
        )
    }

    @Test
    fun `a guide is only there to be grabbed on its own page`() {
        val pose = setsquare()
        assertEquals(GuideGrab.NONE, guidePress(pose, page = 1, px = 150.0, py = 200.0, reachPt = 12.0))
        assertEquals(GuideGrab.NONE, guidePress(null, page = 0, px = 150.0, py = 200.0, reachPt = 12.0))
    }

    @Test
    fun `the invisible rules have no handle and no body to hold`() {
        val ruling = DesktopGuidePose(0, DrawingGuide.Ruling(100.0, 100.0))
        val chemistry = DesktopGuidePose(0, DrawingGuide.Chemistry(100.0, 100.0))
        for (pose in listOf(ruling, chemistry)) {
            assertEquals(GuideGrab.NONE, guidePress(pose, page = 0, px = 100.0, py = 100.0, reachPt = 12.0))
        }
    }

    @Test
    fun `the handle's reach is a screen distance, so it shrinks as the sheet is zoomed in`() {
        val wide = guideReachPt(zoom = 0.5f, density = 2f)
        val close = guideReachPt(zoom = 4f, density = 2f)
        assertEquals(GUIDE_HANDLE_PX.toDouble(), wide, eps)
        assertTrue("a zoomed-in sheet needs a shorter reach in page pt", close < wide)
        assertEquals(3.0, close, eps)
    }
}
