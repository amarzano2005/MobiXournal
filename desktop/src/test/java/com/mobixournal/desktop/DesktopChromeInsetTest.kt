package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The page's resting place beside the floating strips**, as arithmetic.
 *
 * The strips lie on the canvas and take nothing from it, but a *fitted* page is framed in the room they
 * leave — that is what puts the sheet on the black desk instead of under the bar when a document opens.
 * Two things have to hold at once, and they pull in opposite directions:
 *
 *  - a page that fits the room is **centred in the room**, so the bar never covers what was just opened;
 *  - a page too big for the room keeps the window's **full travel**, so it can still be dragged behind the
 *    glass to read the part the bar sits over.
 *
 * `DesktopStripLayoutTest` checks the same claim in rendered pixels; this pins the arithmetic that produces
 * them, including the free-zoom case a headless render would have to drag into place to reach.
 */
class DesktopChromeInsetTest {

    private val rail = ChromeInset(left = 64f)

    @Test
    fun `a page that fits the room is centred beside the rail`() {
        val page = Viewport(zoom = 1f).inChrome(rail, pageW = 400f, pageH = 500f, viewW = 1000f, viewH = 700f)

        val free = 1000f - 64f
        assertEquals("the page sits in the room, not under the bar", 64f + (free - 400f) / 2f, page.offsetX, 1e-3f)
        assertEquals("and is centred in the height as before", (700f - 500f) / 2f, page.offsetY, 1e-3f)
    }

    @Test
    fun `a ribbon centres the page below its band`() {
        val top = ChromeInset(top = 56f)
        val page = Viewport(zoom = 1f).inChrome(top, pageW = 400f, pageH = 500f, viewW = 1000f, viewH = 700f)

        assertEquals("the sheet starts under the ribbon's lane", 56f + (700f - 56f - 500f) / 2f, page.offsetY, 1e-3f)
        assertEquals("and keeps the whole width", (1000f - 400f) / 2f, page.offsetX, 1e-3f)
    }

    /**
     * A page larger than the room keeps the window's own range: it can be pushed until its edge is at the
     * window's edge — behind the rail — which is the freedom the floating bar was asked for.
     */
    @Test
    fun `a page larger than the room still travels behind the glass`() {
        val viewport = Viewport(zoom = 1f)
        val underTheRail = viewport.copy(offsetX = 0f).inChrome(rail, pageW = 1200f, pageH = 900f, viewW = 1000f, viewH = 700f)
        assertEquals("the sheet's own left edge can reach the window's", 0f, underTheRail.offsetX, 1e-3f)
        assertTrue(
            "so its first columns are behind the rail (offset ${underTheRail.offsetX})",
            underTheRail.offsetX < 64f,
        )

        val pastTheRight = viewport.copy(offsetX = -200f)
            .inChrome(ChromeInset(right = 64f), pageW = 1200f, pageH = 900f, viewW = 1000f, viewH = 700f)
        assertEquals("and its right edge can reach the other side", -200f, pastTheRight.offsetX, 1e-3f)
    }

    @Test
    fun `without strips the page is exactly where it always was`() {
        val plain = Viewport(zoom = 1f).clamped(400f, 500f, 1000f, 700f)
        val noChrome = Viewport(zoom = 1f).inChrome(ChromeInset.NONE, 400f, 500f, 1000f, 700f)

        assertEquals(plain.offsetX, noChrome.offsetX, 1e-3f)
        assertEquals(plain.offsetY, noChrome.offsetY, 1e-3f)
    }
}
