package com.mobixournal.desktop

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.PageOps

/**
 * What the **Pages** section can do, in one value the strip hands its slot.
 *
 * The tablet's page section is a button with a menu of what a page *is* (its paper, its format) and
 * what the document's page **list** can become (add, remove, previous, next); the window had two
 * separate strip slots — background and format — and no way at all to add or remove a page. This is the
 * one menu both shells now present, through the shared rules in `PageOps`.
 */
data class PageMenuActions(
    val addPage: () -> Unit = {},
    val removePage: () -> Unit = {},
    val movePageUp: () -> Unit = {},
    val movePageDown: () -> Unit = {},
    /** The advanced half: a whole range of pages moved or removed at once (`PageRangePanel`). */
    val managePages: () -> Unit = {},
    val previousPage: () -> Unit = {},
    val nextPage: () -> Unit = {},
    val format: () -> Unit = {},
    val background: () -> Unit = {},
    /** A one-page document has nothing to step to, and the last page cannot be removed. */
    val canStep: Boolean = false,
    val canRemove: Boolean = false,
    /** The first page cannot go up, the last cannot go down, and one page can go nowhere. */
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    /** With no document open there is no page list to manage. */
    val canManage: Boolean = false,
)

/**
 * The window's **page section**: the shell's commands over the tab's page list.
 *
 * Kept out of `DesktopShell.kt` for the module's structural ratchet, and out of `DesktopPageSetupState`
 * because these are not edits to one page — adding or removing a page replaces the **list**, which is a
 * different operation with a different cost (`DesktopDocument.replacePages`).
 *
 * Every command records **one** undo step before it changes anything, so `Ctrl+Z` takes a page back the
 * same way it takes a stroke back, and the added page is a page the file will carry.
 */
internal class DesktopPages(
    private val tabs: DesktopTabs,
    private val edited: (DesktopTab, Int) -> Unit,
    private val defaultSize: () -> Pair<Double, Double>,
    /** What a change to the page **list** invalidates: every thumbnail after the one that moved. */
    private val onStructure: (DesktopTab) -> Unit = {},
) {

    /** The tab the command acts on, or null when the window is empty. */
    private fun active(): DesktopTab? = tabs.active

    /** Insert a blank sheet after the page in view and step onto it — the tablet's **Add page**. */
    fun addPage(): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        if (count <= 0) return false
        val at = tab.page.coerceIn(0, count - 1)
        val (width, height) = defaultSize()
        val before = tab.document.snapshot()
        val pages = PageOps.addAfter(tab.document.model.pages, at, width, height)
        if (pages === tab.document.model.pages) return false
        tab.document.replacePages(pages)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, at + 1)
        return true
    }

    /** Drop the page in view, keeping the one in view sensible — the tablet's **Remove page**. */
    fun removePage(): Boolean {
        val tab = active() ?: return false
        return removePageAt(tab.page)
    }

    /**
     * Drop the page at [index] — the sidebar row's own delete, and the page menu's.
     *
     * A document's PDF lives in one page's background (`docs/pdf.md`), so removing **that** page must not
     * take the PDF out of the document: the reference moves to the first pdf page that survives
     * ([PdfPageReferences.keep]), and every remaining page still draws the sheet it names.
     */
    fun removePageAt(index: Int): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        if (count <= 1) return false
        val at = index.coerceIn(0, count - 1)
        val before = tab.document.snapshot()
        val kept = PageOps.removeAt(tab.document.model.pages, at)
        if (kept === tab.document.model.pages) return false
        val pages = PdfPageReferences.keep(tab.document.model.pages, kept)
        tab.document.replacePages(pages)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, at.coerceAtMost(tab.document.pageCount - 1))
        return true
    }

    /**
     * Drop every page in [indices] — the sidebar's multi-page delete, one undo step.
     *
     * It follows [removePageAt]'s rule for the PDF reference: a document's PDF lives in one page's
     * background, so a selection that contains that page must not take the file with it — the reference
     * moves to a surviving pdf page ([PdfPageReferences.keep]) and the rest keeps drawing their sheets.
     * The document is never emptied ([PageOps.removeAll] refuses a selection covering every page).
     */
    fun removePages(indices: Set<Int>): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        if (count <= 1 || indices.isEmpty()) return false
        val before = tab.document.snapshot()
        val kept = PageOps.removeAll(tab.document.model.pages, indices)
        if (kept === tab.document.model.pages) return false
        val pages = PdfPageReferences.keep(tab.document.model.pages, kept)
        tab.document.replacePages(pages)
        tab.record(before)
        onStructure(tab)
        val first = indices.filter { it in 0 until count }.minOrNull() ?: 0
        selectPage(tab, first.coerceAtMost(tab.document.pageCount - 1))
        return true
    }

    /**
     * Move the pages in [indices], in document order, so the first of them lands at [target] — the
     * sidebar's multi-page drag, one undo step through [PageOps.moveAll].
     *
     * A scattered selection travels as one block, which is what dragging two rows means: the pages you
     * picked keep their relative order and the rest of the document closes up behind them.
     */
    fun movePages(indices: Set<Int>, target: Int): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        val picked = indices.filter { it in 0 until count }.toSet()
        if (count <= 1 || picked.size >= count) return false
        val before = tab.document.snapshot()
        val pages = PageOps.moveAll(tab.document.model.pages, picked, target)
        if (pages === tab.document.model.pages) return false
        tab.document.replacePages(pages)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, target.coerceIn(0, tab.document.pageCount - 1))
        return true
    }

    /**
     * Move the page at [from] to [to] — the page list's one reordering command, through
     * [PageOps.move] and one undo step.
     *
     * The reference stays where it is: a reference is a fact about the **file**, and with the page order
     * changed it is still the file every page names ([PdfPageReferences] answers from anywhere in the
     * list). Only a delete can take it away, which is why that one [PdfPageReferences.keep]s it.
     */
    fun movePage(from: Int, to: Int): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        if (count <= 1) return false
        if (from !in 0 until count) return false
        val target = to.coerceIn(0, count - 1)
        if (target == from) return false
        val before = tab.document.snapshot()
        val pages = PageOps.move(tab.document.model.pages, from, target)
        if (pages === tab.document.model.pages) return false
        tab.document.replacePages(pages)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, target)
        return true
    }

    /**
     * The advanced page-management question, or null while it is closed.
     *
     * It lives with the page commands rather than in the shell because it is one more way to change the
     * **list**, and every one of those goes through this class.
     */
    var range by mutableStateOf<PageRangeRequest?>(null)
        private set

    /** Open the panel on the page in view — a range of one, which the reader widens by typing. */
    fun openRange(): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        if (count <= 0) return false
        range = PageRanges.opening(count, tab.page)
        return true
    }

    fun closeRange() {
        range = null
    }

    /** Retype one field of the open question. */
    fun editRange(transform: (PageRangeRequest) -> PageRangeRequest) {
        range = range?.let(transform)
    }

    /**
     * **Move** the typed range so that its first page lands where the target field says — one undo step.
     *
     * The list is the document's order, so a PDF-backed document reorders which source page is drawn where
     * without touching the PDF, and the reference stays wherever it was (a reference is a fact about the
     * file, not about a position in the list).
     */
    fun moveRange(): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        val typed = range ?: return false
        val pages = PageRanges.pages(typed, count) as? PageRangePlan.Pages ?: return false
        val target = PageRanges.target(typed, count) as? PageRangePlan.Pages ?: return false
        if (PageRanges.moveRefusal(pages, target) != null) return false
        val before = tab.document.snapshot()
        val moved = PageOps.moveRange(tab.document.model.pages, pages.first, pages.last, target.first)
        if (moved === tab.document.model.pages) return false
        tab.document.replacePages(moved)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, target.first.coerceIn(0, tab.document.pageCount - 1))
        range = null
        return true
    }

    /**
     * **Delete** the typed range as one undo step, keeping the document's PDF reference when the run
     * happens to hold the page that carries it ([PdfPageReferences.keep]).
     */
    fun deleteRange(): Boolean {
        val tab = active() ?: return false
        val count = tab.document.pageCount
        val typed = range ?: return false
        val pages = PageRanges.pages(typed, count) as? PageRangePlan.Pages ?: return false
        if (PageRanges.deleteRefusal(pages, count) != null) return false
        val before = tab.document.snapshot()
        val kept = PageOps.removeAll(tab.document.model.pages, (pages.first..pages.last).toSet())
        if (kept === tab.document.model.pages) return false
        val remaining = PdfPageReferences.keep(tab.document.model.pages, kept)
        tab.document.replacePages(remaining)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, pages.first.coerceIn(0, tab.document.pageCount - 1))
        range = null
        return true
    }

    /** One page forward or back — the menu's two arrows, and what the page keys already do. */
    fun step(delta: Int): Boolean {
        val tab = active() ?: return false
        val target = tab.page + delta
        if (target !in 0 until tab.document.pageCount) return false
        selectPage(tab, target)
        edited(tab, target)
        return true
    }

    /** The strip's slot, reading the state it acts on. */
    fun actions(
        background: () -> Unit,
        format: () -> Unit,
    ): PageMenuActions {
        val tab = active()
        val count = tab?.document?.pageCount ?: 0
        val at = tab?.page ?: 0
        return PageMenuActions(
            addPage = { addPage() },
            removePage = { removePage() },
            movePageUp = { tab?.let { movePage(it.page, it.page - 1) } },
            movePageDown = { tab?.let { movePage(it.page, it.page + 1) } },
            managePages = { openRange() },
            previousPage = { step(-1) },
            nextPage = { step(1) },
            format = format,
            background = background,
            canStep = count > 1,
            canRemove = count > 1,
            canMoveUp = count > 1 && at > 0,
            canMoveDown = count > 1 && at < count - 1,
            canManage = count > 0,
        )
    }
}

/**
 * Reorder the page list — the Pages menu's two move rows, one undo step.
 *
 * The page list is the **document's** order: a PDF-backed document reorders the sheets it writes on
 * without touching the PDF, and the export follows the new order (`DesktopPdfExport` writes each page by
 * the `pageno` its background still names).
 */
internal fun DesktopShellState.movePage(from: Int, to: Int): Boolean = pagesSection.movePage(from, to)

/** Drop one page by index — the sidebar row's own delete, refused for the last page. */
internal fun DesktopShellState.removePageAt(index: Int): Boolean = pagesSection.removePageAt(index)

/** Drop every picked page — the sidebar's multi-page delete, one undo step. */
internal fun DesktopShellState.removePages(indices: Set<Int>): Boolean = pagesSection.removePages(indices)

/** Move the picked pages as one block — the sidebar's multi-page drag, one undo step. */
internal fun DesktopShellState.movePages(indices: Set<Int>, target: Int): Boolean =
    pagesSection.movePages(indices, target)

/**
 * A change to the page **list** invalidates every cached thumbnail: the bitmaps are filed by index, and an
 * inserted or removed sheet moves every index after it. The window clears the tab's map and lets the
 * sidebar's debounce repaint what is on screen (`DesktopQuiet`).
 */
internal fun DesktopShellState.pagesChanged(tab: DesktopTab) {
    thumbnails[tab.key]?.clear()
    refreshThumbnails()
}

/**
 * The section's **menu**, in the strip's own slot: the tablet's Pages button, one page-shaped glyph and
 * a panel of rows.
 *
 * The rows are the tablet's — Add page, Remove page, the two page steps, Page format and Page
 * background — in the order its menu uses, and the two destructive-ish ones go dead on a one-page
 * document rather than disappearing, so the menu keeps its shape.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PageSectionSlot(actions: PageMenuActions, side: StripSide) {
    var open by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    // A second click on the slot closes its menu instead of reopening it (see [DismissGuard]).
    val guard = remember { DismissGuard() }
    val density = LocalDensity.current
    val slotPx = with(density) { ToolStripMetrics.slotSize.roundToPx() }
    val gapPx = with(density) { ToolStripMetrics.tooltipGap.roundToPx() }
    Box {
        Box(
            Modifier
                .size(ToolStripMetrics.slotSize)
                .background(
                    if (open || hovered) DesktopTheme.hoverSlot else Color.Transparent,
                    DesktopTheme.railSlotShape,
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .pointerInput(actions) { detectTapGestures { open = !open && guard.mayOpen(Unit) } },
            contentAlignment = Alignment.Center,
        ) {
            // The tablet's own Description mark: the section is the page, not the ruling or the size.
            androidx.compose.foundation.Canvas(Modifier.size(ToolStripMetrics.glyphSize)) {
                drawChromeGlyph(ChromeGlyph.DESCRIPTION, DesktopTheme.chromeInk)
            }
        }
        if (hovered && !open) {
            StripPill(
                label = "Pages",
                accent = "",
                offset = when (side) {
                    StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                    StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
                },
            )
        }
        if (open) {
            Popup(
                offset = when (side) {
                    StripSide.RIGHT -> IntOffset(slotPx + gapPx, 0)
                    StripSide.BELOW -> IntOffset(0, slotPx + gapPx)
                },
                onDismissRequest = {
                    guard.closed(Unit)
                    open = false
                },
                properties = FloatingSurface,
            ) {
                PageMenuPanel(actions) { open = false }
            }
        }
    }
}

/** The panel behind that slot: six rows, each one command, closing as it runs it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PageMenuPanel(actions: PageMenuActions, onClose: () -> Unit) {
    Column(
        Modifier
            .glassSurface()
            .width(PageMenuMetrics.width)
            .padding(PageMenuMetrics.padding),
        verticalArrangement = Arrangement.spacedBy(PageMenuMetrics.gap),
    ) {
        PageMenuRow("Add page", enabled = true) { onClose(); actions.addPage() }
        PageMenuRow("Remove page", enabled = actions.canRemove) { onClose(); actions.removePage() }
        PageMenuRule()
        PageMenuRow("Move page up", enabled = actions.canMoveUp) { onClose(); actions.movePageUp() }
        PageMenuRow("Move page down", enabled = actions.canMoveDown) { onClose(); actions.movePageDown() }
        PageMenuRule()
        PageMenuRow("Previous page", enabled = actions.canStep) { onClose(); actions.previousPage() }
        PageMenuRow("Next page", enabled = actions.canStep) { onClose(); actions.nextPage() }
        PageMenuRule()
        PageMenuRow("Manage pages…", enabled = actions.canManage) { onClose(); actions.managePages() }
        PageMenuRule()
        PageMenuRow("Page format", enabled = true) { onClose(); actions.format() }
        PageMenuRow("Page background", enabled = true) { onClose(); actions.background() }
    }
}

/**
 * The panel's numbers, in one place: the layout reads them and so does `DesktopPagesTest`, which clicks
 * the rows by the same arithmetic the panel was laid out with — a test that guesses a coordinate fails
 * the day a gap changes for a good reason.
 */
internal object PageMenuMetrics {
    val width = 208.dp
    val padding = 6.dp
    val rowHeight = 30.dp
    val ruleHeight = 11.dp
    val gap = 1.dp

    /** The rows in the order the panel draws them; a rule sits after every group of two. */
    const val ROWS = 9

    /** The top edge of row [index], from the panel's own top padding and its inter-row gaps. */
    fun rowTop(index: Int): androidx.compose.ui.unit.Dp {
        val rulesAbove = when {
            index < 2 -> 0
            index < 4 -> 1
            index < 6 -> 2
            index < 7 -> 3
            else -> 4
        }
        return padding + (rowHeight + gap) * index + (ruleHeight + gap) * rulesAbove
    }
}

/** One row: a label, a hover fill, and the muted tone when the document cannot do it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageMenuRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.rowHeight)
            .background(Color.Transparent, DesktopTheme.capsuleShape)
            .hoverFill(DesktopTheme.capsuleShape, active = !enabled)
            .pointerInput(label, enabled) { if (enabled) detectTapGestures { onClick() } }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.body,
            maxLines = 1,
        )
    }
}

/** A hairline between the page list's commands and the page's own two questions. */
@Composable
private fun PageMenuRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PageMenuMetrics.ruleHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopTheme.hairline))
    }
}
