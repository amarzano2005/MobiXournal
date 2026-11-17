package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.design.DocumentNavigationLayout
import com.mobixournal.render.OutlineEdits
import com.mobixournal.render.PageRanges
import com.mobixournal.render.PdfOutlineSeeding
import com.mobixournal.ui.theme.PanelEdgeAir
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

internal enum class NavigationTab(val label: String) { PAGES("Pages"), BOOKMARKS("Bookmarks") }

/** Selecting the other view opens it immediately; only selecting the already-open view closes it. */
internal fun EditorUiState.toggleNavigation(tab: NavigationTab) {
    pageListOpen = !pageListOpen || navigationTab != tab
    navigationTab = tab
    pageSelection = emptySet()
}

internal fun EditorUiState.closeNavigation() {
    pageListOpen = false
    pageSelection = emptySet()
}

/**
 * The navigation capsule's **bookmark half**: the ribbon in shared catalogue artwork, one named, selected
 * target beside the page list it shares the shell with ([NavigationCapsule]).
 *
 * Like its twin it does **not** light up while the lane is up: the state stays for a screen reader, the
 * ribbon keeps the chrome's ink, and the lane in front of the canvas is what says the tap took.
 */
@Composable
internal fun BookmarkSegment(ui: EditorUiState) {
    val selected = ui.pageListOpen && ui.navigationTab == NavigationTab.BOOKMARKS
    IconButton(
        onClick = { ui.toggleNavigation(NavigationTab.BOOKMARKS) },
        modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp).semantics { this.selected = selected },
    ) {
        Icon(
            ChromeGlyph.BOOKMARK.imageVector(), "Bookmarks",
            tint = rememberChromeInk(),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The canvas stays composed, including on compact windows while the navigator occupies its space. */
@Composable
internal fun DocumentNavigationWorkspace(
    ui: EditorUiState,
    pane: PaneState,
    railOnLeft: Boolean,
    toolbar: @Composable () -> Unit,
    canvas: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val compact = DocumentNavigationLayout.compact(maxWidth.value)
        val width = DocumentNavigationLayout.width(
            maxWidth.value, SideToolbarModernTotalWidth.value, ui.navigationTab == NavigationTab.BOOKMARKS,
        ).dp
        // Outside visibility: closing the navigator or changing its tab must not reset the document's list.
        val session = pane.navigationSessions.getOrPut(pane.navigationDocumentKey) { ContentsSession() }
        val navigator: @Composable () -> Unit = {
            DocumentNavigationLane(ui, pane, session, railOnLeft, compact, width)
        }
        Row(Modifier.fillMaxSize()) {
            if (railOnLeft) RailLane(toolbar, canvasOnRight = true)
            if (compact) {
                CompactNavigationBody(ui.pageListOpen, canvas, navigator)
            } else {
                if (railOnLeft) navigator()
                canvas(Modifier.weight(1f).fillMaxHeight())
                if (!railOnLeft) navigator()
            }
            if (!railOnLeft) RailLane(toolbar, canvasOnRight = false)
        }
    }
}

/** Replacement view, not a one-dp canvas: the hidden SurfaceView keeps its viewport, page and undo state. */
@Composable
private fun RowScope.CompactNavigationBody(
    open: Boolean,
    canvas: @Composable (Modifier) -> Unit,
    navigator: @Composable () -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxHeight()) {
        canvas(Modifier.fillMaxSize().graphicsLayer { alpha = if (open) 0f else 1f }
            .pointerInput(open) {
                if (open) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
        navigator()
    }
}

@Composable
private fun DocumentNavigationLane(
    ui: EditorUiState,
    pane: PaneState,
    session: ContentsSession,
    expandFromStart: Boolean,
    compact: Boolean,
    width: Dp,
) {
    val close = { ui.closeNavigation() }
    val dismiss = session.editing.dismissOrCancel(close)
    BackHandler(enabled = ui.pageListOpen && !ui.fullPage) {
        if (ui.navigationTab == NavigationTab.BOOKMARKS) dismiss() else close()
    }
    if (ui.pageListOpen && !ui.fullPage) {
        Surface(
            color = rememberGlassSurfaceColor().compositeOver(rememberChromeColor()),
            modifier = Modifier.width(width).fillMaxHeight().imePadding(),
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                NavigationTabs(ui, pane, onClose = if (ui.navigationTab == NavigationTab.BOOKMARKS) dismiss else close)
                val goToPage: (Int) -> Unit = {
                    pane.surface?.goToPage(it)
                    if (compact) close()
                }
                if (ui.navigationTab == NavigationTab.BOOKMARKS) {
                    BookmarkNavigationBody(pane, session, goToPage, Modifier.weight(1f))
                } else {
                    NavigationPages(ui, pane, goToPage, Modifier.weight(1f))
                }
            }
        }
    }
}

/** The mark each lane wears — the same two the header's capsule carries ([NavigationCapsule]). */
private fun NavigationTab.glyph(): ImageVector = when (this) {
    NavigationTab.PAGES -> Icons.Outlined.ViewList
    NavigationTab.BOOKMARKS -> ChromeGlyph.BOOKMARK.imageVector()
}

@Composable
private fun NavigationTabs(ui: EditorUiState, pane: PaneState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            NavigationTab.entries.forEach { tab ->
                TextButton(
                    onClick = { ui.navigationTab = tab; ui.pageSelection = emptySet() },
                    modifier = Modifier.semantics { role = Role.Tab; selected = ui.navigationTab == tab },
                ) {
                    // **One ink for both tabs.** The tab in front used to wear the accent, and with the
                    // header's two halves quiet that left the only light in the lane on a label. What says
                    // which view is showing is the body under the tabs; `selected` still tells a screen
                    // reader which tab that is.
                    val tint = MaterialTheme.colorScheme.onSurfaceVariant
                    // The tab wears the mark its half of the header capsule wears, so the lane and the
                    // pill name the two views with one picture each.
                    Icon(tab.glyph(), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tab.label, color = tint)
                }
            }
        }
        // The typed range's door, between the two views and the close: it asks something of the whole page
        // list rather than opening one of the views, so it stands beside them instead of inside them
        // (`PageRangeHost` draws the card the window's own *Manage pages* panel answers).
        IconButton(
            onClick = { ui.pageRange = PageRanges.opening(pane.pageCount, pane.currentPage) },
            enabled = pane.pageCount > 1,
            modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp),
        ) {
            Icon(
                ChromeGlyph.PAGE_RANGE.imageVector(), "Manage pages",
                tint = rememberChromeInk(), modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp)) {
            Icon(ChromeGlyph.CLOSE.imageVector(), "Close navigation")
        }
    }
}

@Composable
private fun BookmarkNavigationBody(
    pane: PaneState, session: ContentsSession, onGoToPage: (Int) -> Unit, modifier: Modifier,
) {
    BookmarkRowsHost(pane, session, modifier) { rows ->
        val commands = pane.surface?.pageCommands
        // Do not mint a second tree while merely opening the index; fallback seeding is needed only on Add.
        fun missing() = OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND)
        PdfContentsPanel(
            rows, pane.currentPage, session, onGoToPage,
            onAdd = { title, page -> commands?.addBookmark(title,
                pane.outline?.let { PdfOutlineSeeding.tree(it) }, page) ?: missing() },
            onRename = { id, title -> commands?.renameBookmark(id, title) ?: missing() },
            onDelete = { id -> commands?.deleteBookmark(id) ?: missing() },
            onMove = { id, delta -> commands?.moveBookmark(id, delta) ?: missing() },
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(PanelEdgeAir),
        )
    }
}

@Composable
private fun NavigationPages(ui: EditorUiState, pane: PaneState, onGoToPage: (Int) -> Unit, modifier: Modifier) {
    // The page in view anchors the selection. Turning the canvas to another page — a jump, the counter, a
    // scroll, an undo — leaves a pick about the page you left behind, which is the state that read as a
    // dot stuck on the wrong row; the lane follows the page instead. A run that already owns the page the
    // lane is showing survives, so an arrow move or a drop keeps every page it carried.
    LaunchedEffect(pane.currentPage) {
        if (pane.currentPage !in ui.pageSelection) ui.pageSelection = setOf(pane.currentPage)
    }
    PageListPanel(
        pageCount = pane.pageCount, currentPage = pane.currentPage, selection = ui.pageSelection,
        pages = pane.surface?.pageCommands?.pages().orEmpty(), surface = pane.surface,
        onSelection = { ui.pageSelection = it }, onGoToPage = onGoToPage,
        onMove = { picked, delta -> pickedMoveTarget(picked, delta, pane.pageCount)
            ?.let { pane.surface?.pageCommands?.movePages(picked, it) } },
        onReorder = { from, to -> pane.surface?.pageCommands?.movePages(setOf(from), to) },
        onDelete = { picked -> pane.surface?.pageCommands?.removePages(picked); ui.pageSelection = emptySet() },
        onClose = { ui.closeNavigation() },
        showHeader = false, modifier = modifier.fillMaxWidth(),
    )
}
