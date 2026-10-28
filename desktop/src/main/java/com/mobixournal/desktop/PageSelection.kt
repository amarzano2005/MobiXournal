package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The pages picked in the sidebar, and the row a range is measured from.
 *
 * Selection is **view state**, like a hidden layer: it belongs to the window, never to the document, so a
 * save cannot carry a highlight nobody asked for and a reopened file starts with nothing picked. What the
 * selection *means* is a page-list edit, and those are the page commands' own (`PageOps.removeAll`,
 * `PageOps.moveAll`).
 */
internal data class PageSelection(
    /** The picked pages, 0-based. */
    val pages: Set<Int> = emptySet(),
    /** The row a shift-click measures from — the last row clicked on its own, or toggled. */
    val anchor: Int = -1,
) {
    val isEmpty: Boolean get() = pages.isEmpty()

    /** The selection with anything outside [pageCount] dropped — the list changes under a selection. */
    fun sanitized(pageCount: Int): PageSelection {
        val kept = pages.filter { it in 0 until pageCount }.toSet()
        if (kept == pages) return this
        return PageSelection(kept, anchor.takeIf { it in 0 until pageCount } ?: -1)
    }
}

/**
 * How a click on a row changes the selection — the one rule the sidebar reads, pure and tested.
 *
 *  - a plain click picks **only** that page (a reader pointing at a sheet means that sheet);
 *  - **Ctrl** toggles it, so a scattered handful can be collected;
 *  - **Shift** takes the run from the anchor to the clicked page, which is how a chapter of a scan is
 *    picked in two clicks.
 */
internal object PageSelectionRules {

    fun click(current: PageSelection, index: Int, ctrl: Boolean, shift: Boolean, pageCount: Int): PageSelection {
        if (index !in 0 until pageCount) return current.sanitized(pageCount)
        val known = current.sanitized(pageCount)
        return when {
            ctrl -> {
                val pages = if (index in known.pages) known.pages - index else known.pages + index
                PageSelection(pages, index)
            }
            shift -> {
                val from = known.anchor.takeIf { it in 0 until pageCount } ?: index
                val run = if (from <= index) from..index else index..from
                PageSelection(run.toSet(), if (known.anchor >= 0) known.anchor else index)
            }
            else -> PageSelection(setOf(index), index)
        }
    }

    /**
     * The pages a drag starting on [index] carries: the whole selection when the dragged row is part of
     * it (dragging one of the picked rows means the block), otherwise the row alone, which is what a
     * reader who never selected anything expects a drag to do.
     */
    fun dragging(selection: PageSelection, index: Int): Set<Int> =
        if (index in selection.pages) selection.pages else setOf(index)

    /** The bar's word: `3 pages selected`, `1 page selected` — English copy, no bare number. */
    fun title(count: Int): String = if (count == 1) "1 page selected" else "$count pages selected"
}

/**
 * A row's click, with the keyboard **at the moment of the press** — the sidebar's one input path, which
 * a plain `clickable` cannot be, because it does not say whether Ctrl or Shift was held.
 *
 * It only answers a press that ends as a press: a drag is the reorder gesture beside it and consumes its
 * own movement, which cancels this one, so a drag never also selects the row it started on. It consumes
 * nothing of its own, so the drag handler still sees the same events.
 */
internal fun Modifier.selectionTap(pick: (ctrl: Boolean, shift: Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val modifiers = currentEvent.keyboardModifiers
            if (waitForUpOrCancellation() != null) {
                pick(modifiers.isCtrlPressed, modifiers.isShiftPressed)
            }
        }
    }

/**
 * [selectionTap] with the callback **held**, not baked in: a `pointerInput` keeps the lambda it was built
 * with, so a handler keyed on a fresh lambda restarts on every recomposition — and one keyed on `Unit`
 * would answer with the selection of the first frame forever. Both halves are the trap the lane's controls
 * document (`InspectorControls.kt`); the row picks through the latest value instead.
 */
@Composable
internal fun rememberSelectionTap(pick: (ctrl: Boolean, shift: Boolean) -> Unit): Modifier {
    val latest = rememberUpdatedState(pick)
    return remember { Modifier.selectionTap { ctrl, shift -> latest.value(ctrl, shift) } }
}

/**
 * The strip under the page list while pages are picked: how many, and the two things a selection is for.
 *
 * It sits where the layers section was and takes its place rather than pushing the list taller, because a
 * selection is a **mode** and the reader is looking at rows, not layers. The count is the accent's word —
 * the list already shows the pick in its own tint — and *Clear* is the way out without touching a row.
 */
@Composable
internal fun PageSelectionBar(
    count: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .height(PageSelectionMetrics.BAR_HEIGHT)
            .background(DesktopTheme.glass)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                PageSelectionRules.title(count),
                color = DesktopTheme.accent,
                style = DesktopTheme.label,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Clear",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
                modifier = Modifier
                    .clickable { onClear() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelButton(label = "Delete pages", primary = false, enabled = true, destructive = true, onClick = onDelete)
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** The bar's one measurement: tall enough for its count and its button, so the layers list is not shoved. */
internal object PageSelectionMetrics {
    val BAR_HEIGHT = 78.dp
}
