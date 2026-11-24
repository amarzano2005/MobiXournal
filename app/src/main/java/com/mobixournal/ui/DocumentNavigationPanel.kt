package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.mobixournal.ui.theme.liquidGlassFadeTransform
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
 * Like its twin, **the ribbon wears the accent while the bookmark lane is up**, so the header and the lane
 * agree on which view is open, and it takes **no press indication**: the ripple only flashed beside the
 * accent that already marks the open state (the tool indicator's face sets the precedent).
 */
@Composable
internal fun BookmarkSegment(ui: EditorUiState) {
    val selected = ui.pageListOpen && ui.navigationTab == NavigationTab.BOOKMARKS
    val interaction = remember { MutableInteractionSource() }
    // A bare target rather than an `IconButton`, because Material's button always draws its own ripple.
    // The name and the role move to the target so a screen reader still reads one button, not two.
    Box(
        modifier = Modifier
            .size(ContentsRowLayout.ACTION_DP.dp)
            .clickable(interactionSource = interaction, indication = null) {
                ui.toggleNavigation(NavigationTab.BOOKMARKS)
            }
            .semantics {
                contentDescription = "Bookmarks"
                role = Role.Button
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ChromeGlyph.BOOKMARK.imageVector(), null,
            tint = if (selected) MaterialTheme.colorScheme.primary else rememberChromeInk(),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The canvas stays composed at fixed Surface size while sliding chrome pushes animated insets. */
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
        val laneOpen = ui.pageListOpen && !ui.fullPage
        val railOpen = !ui.fullPage
        val railProgress = animateFloatAsState(
            targetValue = if (railOpen) 1f else 0f,
            animationSpec = chromeSlideSpring(),
            label = "RailLaneChrome",
        )
        val laneProgress = animateFloatAsState(
            targetValue = if (laneOpen) 1f else 0f,
            animationSpec = chromeSlideSpring(),
            label = "SidebarLaneChrome",
        )
        val density = LocalDensity.current
        val railFullPx = with(density) { SideToolbarModernTotalWidth.toPx() }
        val laneFullPx = with(density) { width.toPx() }
        val controller = LocalChromeInsetController.current
        LaunchedEffect(controller, railOnLeft, compact, railFullPx, laneFullPx, railOpen, laneOpen) {
            snapshotFlow { railProgress.value to laneProgress.value }.collectLatest { (rp, lp) ->
                val railPx = railFullPx * rp
                val sidebarPx = if (compact) 0f else laneFullPx * lp
                controller.leftInsetPx = if (railOnLeft) railPx + sidebarPx else 0f
                controller.rightInsetPx = if (!railOnLeft) railPx + sidebarPx else 0f
                controller.sideAnimating = rp in 0.001f..0.999f || lp in 0.001f..0.999f
                val settledTotal = (if (railOpen) railFullPx else 0f) + (if (!compact && laneOpen) laneFullPx else 0f)
                controller.settledLeftPx = if (railOnLeft) settledTotal else 0f
                controller.settledRightPx = if (!railOnLeft) settledTotal else 0f
            }
        }
        val topInsetDp = with(density) { controller.topInsetPx.toDp() }
        val railEdgeDp = with(density) { (railFullPx * railProgress.value).toDp() }

        Box(Modifier.fillMaxSize().background(rememberChromeColor())) {
            canvas(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().padding(top = topInsetDp)) {
                if (laneProgress.value > 0.001f || laneOpen) {
                    OverlayNavigationSidebar(
                        ui = ui,
                        pane = pane,
                        session = session,
                        compact = compact,
                        railOnLeft = railOnLeft,
                        laneOpen = laneOpen,
                        laneProgress = laneProgress.value,
                        laneFullPx = laneFullPx,
                        width = width,
                        railEdgeDp = railEdgeDp,
                    )
                }
                if (railProgress.value > 0.001f || railOpen) {
                    OverlayRailLane(
                        railOnLeft = railOnLeft,
                        railOpen = railOpen,
                        railProgress = railProgress.value,
                        railFullPx = railFullPx,
                        toolbar = toolbar,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.OverlayNavigationSidebar(
    ui: EditorUiState,
    pane: PaneState,
    session: ContentsSession,
    compact: Boolean,
    railOnLeft: Boolean,
    laneOpen: Boolean,
    laneProgress: Float,
    laneFullPx: Float,
    width: Dp,
    railEdgeDp: Dp,
) {
    if (compact) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = laneProgress.coerceIn(0f, 1f) }
                .then(if (laneOpen) Modifier.blockCanvasPointers() else Modifier),
        ) {
            DocumentNavigationLane(ui, pane, session, compact = true, width = width, backEnabled = laneOpen)
        }
        return
    }
    val edgeAlign = if (railOnLeft) Alignment.CenterStart else Alignment.CenterEnd
    val railPad = if (railOnLeft) Modifier.padding(start = railEdgeDp) else Modifier.padding(end = railEdgeDp)
    Box(
        Modifier
            .align(edgeAlign)
            .then(railPad)
            .width(width)
            .fillMaxHeight()
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (railOnLeft) -(1f - laneProgress) * laneFullPx
                    else (1f - laneProgress) * laneFullPx
                }
                .then(if (laneOpen) Modifier.blockCanvasPointers() else Modifier),
        ) {
            DocumentNavigationLane(ui, pane, session, compact = false, width = width, backEnabled = laneOpen)
        }
    }
}

@Composable
private fun BoxScope.OverlayRailLane(
    railOnLeft: Boolean,
    railOpen: Boolean,
    railProgress: Float,
    railFullPx: Float,
    toolbar: @Composable () -> Unit,
) {
    val railAlign = if (railOnLeft) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        Modifier
            .align(railAlign)
            .fillMaxHeight()
            .graphicsLayer {
                translationX = if (railOnLeft) -(1f - railProgress) * railFullPx
                else (1f - railProgress) * railFullPx
            }
            .then(if (railOpen) Modifier.blockCanvasPointers() else Modifier),
    ) {
        RailLane(toolbar, canvasOnRight = railOnLeft)
    }
}

@Composable
private fun DocumentNavigationLane(
    ui: EditorUiState,
    pane: PaneState,
    session: ContentsSession,
    compact: Boolean,
    width: Dp,
    backEnabled: Boolean,
) {
    val close = { ui.closeNavigation() }
    val dismiss = session.editing.dismissOrCancel(close)
    BackHandler(enabled = backEnabled) {
        if (ui.navigationTab == NavigationTab.BOOKMARKS) dismiss() else close()
    }
    Surface(
        color = rememberGlassSurfaceColor().compositeOver(rememberChromeColor()),
        modifier = (if (compact) Modifier.fillMaxSize() else Modifier.width(width).fillMaxHeight()).imePadding(),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            NavigationTabs(ui, pane, onClose = if (ui.navigationTab == NavigationTab.BOOKMARKS) dismiss else close)
            val goToPage: (Int) -> Unit = {
                pane.surface?.goToPage(it)
                if (compact) close()
            }
            AnimatedContent(
                targetState = ui.navigationTab,
                transitionSpec = { liquidGlassFadeTransform() },
                modifier = Modifier.weight(1f),
                label = "NavigationTab",
            ) { tab ->
                if (tab == NavigationTab.BOOKMARKS) {
                    BookmarkNavigationBody(pane, session, goToPage, Modifier.fillMaxSize())
                } else {
                    NavigationPages(ui, pane, goToPage, Modifier.fillMaxSize())
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
                    // **The tab in front wears the accent**, the same ink the header's half for that view
                    // carries, so the lane and the pill name the open view the same way. No indication on
                    // the press: the accent is the state, not a flash.
                    val tint = if (ui.navigationTab == tab) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
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
