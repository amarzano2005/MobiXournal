package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
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
import com.mobixournal.render.OutlineResolution
import com.mobixournal.render.PdfOutlineSeeding
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.PanelEdgeAir
import com.mobixournal.ui.theme.floatingRim
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

/** Direct entry beside Pages; the bookmark is shared catalogue artwork with a named, selected target. */
@Composable
internal fun BookmarksCapsule(ui: EditorUiState) {
    val selected = ui.pageListOpen && ui.navigationTab == NavigationTab.BOOKMARKS
    Surface(
        shape = ModernTheme.capsuleShape,
        color = rememberGlassSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = ModernTheme.capsuleShadowDp,
        modifier = Modifier.padding(horizontal = 2.dp).floatingRim(ModernTheme.capsuleCornerRadiusDp),
    ) {
        IconButton(
            onClick = { ui.toggleNavigation(NavigationTab.BOOKMARKS) },
            modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp).semantics { this.selected = selected },
        ) {
            Icon(
                ChromeGlyph.BOOKMARK.imageVector(), "Bookmarks",
                tint = if (selected) MaterialTheme.colorScheme.primary else rememberChromeInk(),
                modifier = Modifier.size(20.dp),
            )
        }
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
            if (railOnLeft) {
                RailLane(toolbar, canvasOnRight = true)
                navigator()
            }
            canvas(Modifier.weight(1f).fillMaxHeight())
            if (!railOnLeft) {
                navigator()
                RailLane(toolbar, canvasOnRight = false)
            }
        }
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
    AnimatedVisibility(
        visible = ui.pageListOpen && !ui.fullPage,
        enter = expandHorizontally(expandFrom = if (expandFromStart) Alignment.Start else Alignment.End) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = if (expandFromStart) Alignment.Start else Alignment.End) + fadeOut(),
    ) {
        Surface(
            color = rememberGlassSurfaceColor().compositeOver(rememberChromeColor()),
            modifier = Modifier.width(width).fillMaxHeight().imePadding(),
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                NavigationTabs(ui, onClose = if (ui.navigationTab == NavigationTab.BOOKMARKS) dismiss else close)
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

@Composable
private fun NavigationTabs(ui: EditorUiState, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            NavigationTab.entries.forEach { tab ->
                TextButton(
                    onClick = { ui.navigationTab = tab; ui.pageSelection = emptySet() },
                    modifier = Modifier.semantics { role = Role.Tab; selected = ui.navigationTab == tab },
                ) {
                    Text(tab.label, color = if (ui.navigationTab == tab) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
    val surface = pane.surface
    val rows = remember(surface, pane.documentVersion, pane.outline) {
        surface?.let { OutlineResolution.rowsOf(it.doc.outline, pane.outline, it.doc.pages) }.orEmpty()
    }
    val seed = remember(pane.outline) { pane.outline?.let { PdfOutlineSeeding.tree(it) } }
    val commands = surface?.pageCommands
    fun missing() = OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND)
    PdfContentsPanel(
        rows, pane.currentPage, session, onGoToPage,
        onAdd = { title, page -> commands?.addBookmark(title, seed, page) ?: missing() },
        onRename = { id, title -> commands?.renameBookmark(id, title) ?: missing() },
        onDelete = { id -> commands?.deleteBookmark(id) ?: missing() },
        modifier = modifier.navigationBarsPadding().padding(PanelEdgeAir),
    )
}

@Composable
private fun NavigationPages(ui: EditorUiState, pane: PaneState, onGoToPage: (Int) -> Unit, modifier: Modifier) {
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
