package com.mobixournal.desktop

import androidx.compose.foundation.background
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The page's own setup: the **default sheet**, the format panel's arithmetic, and the two commands that
 * open it from the tool strip.
 *
 * The tablet answers *what is this page* with presets, custom dimensions, a **Default** entry and *Set as
 * default*; the window answered it with four presets and nowhere to put a custom size. These tests hold
 * the window to the tablet's set: a size typed in any unit lands as the same page, a stored default is
 * what the next new page is born with, and the paper and format commands live in the strip (after its
 * divider) rather than in the file menu.
 */
class DesktopPageSetupTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a size typed in any unit is the same page`() {
        // 100 mm in points, to the point the format section prints it with.
        val points = PageUnit.MM.toPt(100.0)
        assertEquals(283.46, points, 0.01)
        assertEquals(100.0, PageUnit.MM.fromPt(points), 1e-6)
        assertEquals(PageUnit.PT.toPt(283.46), 283.46, 1e-9)
        assertEquals("an inch is 72 points", 72.0, PageUnit.IN.toPt(1.0), 1e-9)
        assertEquals("a whole number prints without a pointless .0", "100", formatDimension(100.0))
        assertEquals("and a fraction keeps one decimal", "283.5", formatDimension(283.46))
    }

    @Test
    fun `a page matches a preset in either orientation`() {
        val a4 = PageSetup.PRESETS.first { it.label == "A4" }
        assertTrue(matchesSize(a4.widthPt, a4.heightPt, a4.widthPt, a4.heightPt))
        assertTrue("a landscape A4 is the same sheet", matchesSize(a4.heightPt, a4.widthPt, a4.widthPt, a4.heightPt))
        assertFalse("A5 is not A4", matchesSize(419.528, 595.276, a4.widthPt, a4.heightPt))
    }

    @Test
    fun `a stored default sheet is what the next new page is born with`() {
        val file = temp.newFile("setup.properties")
        val state = DesktopShellState(settings = DesktopSettings(file))

        state.setDefaultPageSize(419.528, 595.276)
        assertEquals(419.528 to 595.276, state.defaultPageSize)

        state.newDocument()
        val page = state.tabs.active!!.document.page(0)
        assertEquals("an A5 new page", 419.528, page.width, 1e-9)
        assertEquals(595.276, page.height, 1e-9)

        val reopened = DesktopSettings(file)
        assertEquals(419.528, reopened.pageWidthPt, 1e-9)
        assertEquals(595.276, reopened.pageHeightPt, 1e-9)

        // A hand-edited file cannot open a sheet of no size: the documented A4 comes back.
        file.writeText("page.width=0\npage.height=oops\n")
        val fallback = DesktopSettings(file)
        assertEquals(DesktopDocuments.PAGE_WIDTH_PT, fallback.pageWidthPt, 1e-9)
        assertEquals(DesktopDocuments.PAGE_HEIGHT_PT, fallback.pageHeightPt, 1e-9)
    }

    /**
     * The **format panel, drawn**: its presets, units, the two custom fields and the *Set as default*
     * answer, over a real page in a real window.
     *
     * Headless Skiko cannot click a field, but it can prove the surface composes and paints — the panel
     * used to offer four presets and no way to type a size at all, which is exactly a claim pixels can
     * settle. The PNG is kept under `build/desktop-render/` so the layout can be looked at.
     */
    @Test
    fun `the format panel draws the custom size it offers`() {
        val state = DesktopShellState(settings = DesktopSettings(temp.newFile("format.properties")))
        state.open(notebook())
        state.chooseToolPlacement(ToolPlacement.BOTH)
        state.openPageSetup(PageSetupSection.FORMAT)
        val scene = androidx.compose.ui.ImageComposeScene(1100, 900, androidx.compose.ui.unit.Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            // The surface fades in on the family's spring, and the effect that starts it is dispatched
            // *after* the composing frame — so the clock has to be advanced over several frames, not
            // once, before the frame that is kept.
            var clock = 0L
            repeat(5) {
                clock += 400_000_000L
                scene.render(clock)
            }
            val png = scene.render(clock + 400_000_000L).encodeToData()!!.bytes
            val file = java.io.File("build/desktop-render/page-setup-format.png")
            file.parentFile?.mkdirs()
            file.writeBytes(png)
            // The window's middle is the page, and the panel is a surface over it: the page's paper is
            // white, so a dark pixel there is the panel and nothing else.
            val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(png))
            val centre = image.getRGB(550, 300) and 0xFFFFFF
            assertTrue(
                "the format panel is drawn over the page, not behind it: 0x" +
                    centre.toString(16),
                maxOf(centre shr 16, (centre shr 8) and 0xFF, centre and 0xFF) < 0x80,
            )
            assertEquals("and it is the format section that is up", PageSetupSection.FORMAT, state.pageSetup)
        } finally {
            scene.close()
            state.tabs.active?.document?.close()
        }
    }

    private fun notebook(): java.io.File = temp.newFile("setup.mobix").also { file ->
        com.mobixournal.format.mobix.SyntheticNotebook.write(
            file,
            com.mobixournal.format.mobix.SyntheticNotebook.pages(2),
        )
    }

    @Test
    fun `the page section's two questions each open their own section`() {
        val state = DesktopShellState()
        // The Pages slot's menu rows call these; the menu no longer has a page row at all.
        state.stripState.pages.background()
        assertEquals(PageSetupSection.PAPER, state.pageSetup)
        state.closePageSetup()
        // The format section carries the page's size **and**, under it, the defaults for new sheets — the
        // tablet keeps them in the same dialog, so the section needs no third command for them.
        state.stripState.pages.format()
        assertEquals(PageSetupSection.FORMAT, state.pageSetup)
    }

    /**
     * The **defaults** section is a setting, and it must not resize the page in view.
     *
     * It stores the sheet new pages are born on and leaves the open page exactly as it was: a click meant
     * to set a default that resized the sheet under the reader would be an edit nobody asked for, and on a
     * page of ink it is the one mistake an undo may not be able to put back (the ink was laid out for the
     * other size).
     */
    @Test
    fun `setting a default leaves the open page alone`() {
        val state = DesktopShellState(settings = DesktopSettings(temp.newFile("defaults.properties")))
        state.open(notebook())
        val before = state.tabs.active!!.document.page(0)
        val undoDepth = state.tabs.active!!.canUndo

        state.setDefaultPageSize(612.0, 792.0)

        val after = state.tabs.active!!.document.page(0)
        assertEquals("the open sheet keeps its size", before.width, after.width, 1e-9)
        assertEquals(before.height, after.height, 1e-9)
        assertEquals("and it is not an undo step", undoDepth, state.tabs.active!!.canUndo)
        assertEquals(612.0 to 792.0, state.defaultPageSize)
        state.tabs.active?.document?.close()
    }
}
