package com.mobixournal.desktop

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
    val previousPage: () -> Unit = {},
    val nextPage: () -> Unit = {},
    val format: () -> Unit = {},
    val background: () -> Unit = {},
    /** A one-page document has nothing to step to, and the last page cannot be removed. */
    val canStep: Boolean = false,
    val canRemove: Boolean = false,
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
        val count = tab.document.pageCount
        if (count <= 1) return false
        val at = tab.page.coerceIn(0, count - 1)
        val before = tab.document.snapshot()
        val pages = PageOps.removeAt(tab.document.model.pages, at)
        if (pages === tab.document.model.pages) return false
        tab.document.replacePages(pages)
        tab.record(before)
        onStructure(tab)
        selectPage(tab, at.coerceAtMost(tab.document.pageCount - 1))
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
        return PageMenuActions(
            addPage = { addPage() },
            removePage = { removePage() },
            previousPage = { step(-1) },
            nextPage = { step(1) },
            format = format,
            background = background,
            canStep = count > 1,
            canRemove = count > 1,
        )
    }
}

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
                .pointerInput(actions) { detectTapGestures { open = !open } },
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
                onDismissRequest = { open = false },
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
        PageMenuRow("Previous page", enabled = actions.canStep) { onClose(); actions.previousPage() }
        PageMenuRow("Next page", enabled = actions.canStep) { onClose(); actions.nextPage() }
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

    /** The rows in the order the panel draws them; the two rules sit between rows 1-2 and 3-4. */
    const val ROWS = 6

    /** The top edge of row [index], from the panel's own top padding and its inter-row gaps. */
    fun rowTop(index: Int): androidx.compose.ui.unit.Dp {
        val rulesAbove = when {
            index < 2 -> 0
            index < 4 -> 1
            else -> 2
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
