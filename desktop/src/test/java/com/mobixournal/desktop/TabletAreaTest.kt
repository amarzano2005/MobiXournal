package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The **active area**: which part of the tablet's surface the window maps, and the driver counts a context
 * needs for it.
 *
 * The arithmetic is pure, so this is the whole of the area's geometry that can be checked without a pen on a
 * tablet — and the one thing the hardware pass must not have to discover: a rectangle that is the whole
 * surface must degrade to the system context, and a driver with no usable axis must be answered with a
 * fallback rather than a zero-sized range.
 */
class TabletAreaTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(name: String = "desktop.properties") = DesktopSettings(File(temp.root, name))

    @Test
    fun `the whole surface asks for no area at all`() {
        // The system context is the reader's default, and this is what keeps it: the row's first option
        // must not open a second context for a rectangle that covers everything.
        assertNull(TabletAreaMap.window(TabletArea.FULL, 80, 80, aspectLock = true))
        assertNull(
            "a Tablet area grown to the whole surface is the same rectangle",
            TabletAreaMap.window(TabletArea.TABLET, 100, 100, aspectLock = false),
        )
    }

    @Test
    fun `the hand's rectangle is centred on the surface`() {
        val area = TabletAreaMap.window(TabletArea.TABLET, 50, 50, aspectLock = false)
        assertNotNull(area)
        area!!
        assertEquals(0.25, area.x0, 1e-9)
        assertEquals(0.25, area.y0, 1e-9)
        assertEquals(0.75, area.x1, 1e-9)
        assertEquals(0.75, area.y1, 1e-9)
        assertEquals(0.5, area.width, 1e-9)
        // Centred is what makes the area feel like the middle of the tablet rather than one corner of it.
        assertEquals(1.0 - area.x1, area.x0, 1e-9)
        assertEquals(1.0 - area.y1, area.y0, 1e-9)
    }

    @Test
    fun `the aspect lock keeps the tablet's own ratio`() {
        // Both spans are percentages of the surface, so the lane's rule is simply that the two agree.
        assertEquals(70, TabletAreaMap.heightPercent(70, 30, aspectLock = true))
        assertEquals(30, TabletAreaMap.heightPercent(70, 30, aspectLock = false))

        val locked = TabletAreaMap.window(TabletArea.TABLET, 70, 30, aspectLock = true)!!
        assertEquals("a locked area is as square as the surface", locked.width, locked.height, 1e-9)
        val free = TabletAreaMap.window(TabletArea.TABLET, 70, 30, aspectLock = false)!!
        assertTrue("unlocked spans may differ", free.width > free.height)
    }

    @Test
    fun `a percentage outside the rows' range is clamped, not refused`() {
        val tiny = TabletAreaMap.window(TabletArea.TABLET, 1, 1, aspectLock = true)!!
        assertEquals(TabletAreaMap.MIN_PERCENT / 100.0, tiny.width, 1e-9)
        // The ends are the surface's own: a span past 100 % is the surface, not a rectangle outside it.
        val huge = TabletAreaMap.window(TabletArea.TABLET, 400, 50, aspectLock = false)!!
        assertEquals(1.0, huge.width, 1e-9)
        assertEquals(0.5, huge.height, 1e-9)
    }

    @Test
    fun `a device span is the driver's counts narrowed to the area`() {
        // The tablet's own axes, as the driver reports them (0…50800 across, 0…31750 down).
        val x = TabletAreaMap.deviceSpan(0, 50800, 0.25, 0.75)!!
        assertEquals(12700, x.first)
        assertEquals(25400, x.second)

        val full = TabletAreaMap.deviceSpan(0, 50800, 0.0, 1.0)!!
        assertEquals(0, full.first)
        assertEquals(50800, full.second)

        // A driver whose axis does not start at zero keeps its own origin.
        val offset = TabletAreaMap.deviceSpan(-500, 500, 0.5, 1.0)!!
        assertEquals(0, offset.first)
        assertEquals(500, offset.second)
    }

    @Test
    fun `a driver with no usable axis has no area, so the system context stays`() {
        assertNull(TabletAreaMap.deviceSpan(0, 0, 0.25, 0.75))
        assertNull(TabletAreaMap.deviceSpan(10, 10, 0.25, 0.75))
        assertNull(
            "an area narrower than one count cannot be mapped either",
            TabletAreaMap.deviceSpan(0, 1, 0.25, 0.5),
        )
    }

    @Test
    fun `the area is one key each way, and the lock writes both spans`() {
        val file = File(temp.root, "area.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        // The default is the whole surface: the window opens on exactly the reader it always had.
        assertEquals(TabletArea.FULL, policy.activeArea)
        assertNull(policy.windowArea)

        policy.chooseActiveArea(TabletArea.TABLET)
        policy.chooseAreaWidth(70)
        assertEquals("the lock moves the height with the width", 70, policy.areaHeightPercent)
        assertNotNull(policy.windowArea)

        val reopened = DesktopSettings(file)
        assertEquals(TabletArea.TABLET, reopened.activeArea)
        assertEquals(70, reopened.areaWidthPercent)
        assertEquals(70, reopened.areaHeightPercent)
        assertTrue(reopened.aspectLock)
        assertNotNull(reopened.windowArea())

        // Unlocking lets the two spans part, and the free height is what is stored.
        val second = DesktopSettingsState(reopened)
        second.chooseAspectLock(false)
        second.chooseAreaHeight(40)
        assertEquals(70, second.areaWidthPercent)
        assertEquals(40, second.areaHeightPercent)
        assertEquals(40, DesktopSettings(file).areaHeightPercent)
    }

    @Test
    fun `a hand-edited percentage is read back inside the range`() {
        val file = File(temp.root, "broken.properties")
        file.writeText("tablet.activeArea=Nothing\n tablet.areaWidthPct=900\n".replace(" ", ""))
        val store = store("broken.properties")
        assertEquals("an unknown area name is the whole surface", TabletArea.FULL, store.activeArea)
        assertEquals(TabletAreaMap.MAX_PERCENT, store.areaWidthPercent)
        val defaults = store("fresh.properties")
        assertEquals(TabletAreaMap.DEFAULT_PERCENT, defaults.areaWidthPercent)
        assertEquals(TabletAreaMap.DEFAULT_PERCENT, defaults.areaHeightPercent)
    }
}
