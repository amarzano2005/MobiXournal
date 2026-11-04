package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.of

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.BackgroundRuling
import com.mobixournal.render.PageSizeStep

/**
 * The window's **page setup**, as a piece of the shell's state: which section of the surface is up, and
 * the three edits it makes to the page in view.
 *
 * It is owned by [DesktopShellState] and forwarded there, so `state.pageSetup`, `state.setPageStyle(…)`
 * and the panel read exactly what they read before, while the commands that reach the document live here
 * (`SourceStructureTest` keeps `DesktopShell.kt` inside its budget).
 *
 * Every edit goes through [editActive], so it is **one edit and one undo step** — the sheet the user sees
 * after the click is a page the file will carry.
 */
internal class DesktopPageSetupState(
    private val tabs: DesktopTabs,
    private val edited: (DesktopTab, Int) -> Unit,
) {

    /** The section of the page-setup surface that is up, or null while it is closed. */
    var section by mutableStateOf<PageSetupSection?>(null)
        private set

    /** Open the page-setup surface on its paper or its format section. */
    fun open(section: PageSetupSection) {
        this.section = section
    }

    /** Close the surface without touching the page again. */
    fun close() {
        section = null
    }

    /** The active page as it is now, or null when nothing is open. */
    fun activePage(): Page? = tabs.active?.let { tab ->
        runCatching { tab.document.page(tab.page) }.getOrNull()
    }

    /** Change the active page's **paper style**. */
    fun setStyle(style: String) {
        editActive { page ->
            val solid = page.background as? Background.Solid ?: return@editActive page
            if (solid.style == style) page else page.copy(background = solid.copy(style = style))
        }
    }

    /**
     * Change the active page's **ruling spacing**, in millimetres, through desktop's own `r1` parameter
     * ([BackgroundRuling]) so a page ruled at 7 mm reopens at 7 mm on the tablet.
     */
    fun setSpacingMm(mm: Double) {
        if (mm <= 0.0) return
        editActive { page ->
            val solid = page.background as? Background.Solid ?: return@editActive page
            val config = BackgroundRuling.parse(solid.config)
                .withPt(BackgroundRuling.KEY_SPACING, mm * PageSetup.MM_PT)
                .text()
            page.copy(background = solid.copy(config = config))
        }
    }

    /** Resize the active page, keeping its ink and its paper. */
    fun setSize(widthPt: Double, heightPt: Double) {
        if (widthPt <= 0.0 || heightPt <= 0.0) return
        editActive { page ->
            if (page.width == widthPt && page.height == heightPt) page
            else page.copy(width = widthPt, height = heightPt)
        }
    }

    /**
     * Step the active page's size by [widthSteps] and [heightSteps] of the quick-size step, read from the
     * page **as it is now**.
     *
     * The Pages menu's four arrows used to hand back an *absolute* pair computed from the size the panel was
     * composed with. A tap resized the page, but the panel's own value only caught up with the next
     * recomposition, so a second tap sooner than that stepped the **old** size again and moved nothing (or
     * moved by two steps, when the frame happened to land between the two). Reading the page here makes one
     * tap one step, whatever the panel still prints — the numbers follow, the command does not wait for them.
     */
    fun nudge(widthSteps: Int, heightSteps: Int) {
        if (widthSteps == 0 && heightSteps == 0) return
        val page = activePage() ?: return
        val stepPt = PageSizeStep.pt(PageSizeStep.DEFAULT_MM)
        val (width, height) = PageSizeStep.step(page.width, page.height, widthSteps, heightSteps, stepPt)
        setSize(width, height)
    }

    /** Apply one change to the active page, as one undo step, and drop its stale thumbnail. */
    private fun editActive(transform: (Page) -> Page) {
        val tab = tabs.active ?: return
        val before = tab.document.snapshot()
        val changed = tab.document.editPage(tab.page, transform) ?: return
        tab.record(before)
        edited(tab, tab.page)
    }
}
