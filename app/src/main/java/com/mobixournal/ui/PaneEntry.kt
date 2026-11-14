package com.mobixournal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mobixournal.ui.entry.NotebookEntryScreen
import com.mobixournal.ui.entry.RecentNotebook

/**
 * The entry screen **inside one pane**, for a half of a split that holds no document.
 *
 * Closing the last tab of a pane leaves it empty on purpose ([com.mobixournal.MainActivity.closeTab]) —
 * a blank document opened in its place would make "nothing open" a state the app could never be in. In
 * single-pane mode the window then *is* the entry screen. In split view that answer was wrong twice
 * over: the empty half kept a dead canvas and no way to fill it, and focusing it took the whole
 * workspace away, because the entry screen was chosen by the focused pane's tabs rather than by any
 * pane's. Both are the same fix seen from two sides: the empty half draws this, so a notebook closed
 * out of the split is one tap from being reopened **into that half**.
 *
 * It is the same [NotebookEntryScreen] the window shows when nothing at all is open — same doors, same
 * cards, same faces — because it is the same question. A half is narrower, which is the layout that
 * screen already adapts to: the cards reflow and the block scrolls.
 */
@Composable
internal fun PaneEntry(
    recents: List<RecentNotebook>,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onOpenRecent: (RecentNotebook) -> Unit,
    onRemoveRecent: (RecentNotebook) -> Unit,
    onRestoreSession: (() -> Unit)?,
    modifier: Modifier = Modifier,
) = NotebookEntryScreen(
    recents = recents,
    onNew = onNew,
    onOpen = onOpen,
    onOpenRecent = onOpenRecent,
    onRemoveRecent = onRemoveRecent,
    onRestoreSession = onRestoreSession,
    modifier = modifier,
)
