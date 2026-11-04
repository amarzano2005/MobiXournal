package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/** The lane's width: a page number, a pick box and the three commands, and no more. */
private val PAGE_LIST_WIDTH = 132.dp

/** One row: `Page 12` and its pick box. Tall enough for a thumb, short enough to read the list. */
private val PAGE_ROW_HEIGHT = 40.dp

/**
 * Where the picked pages should **start** after a move of [delta] places, or null when nothing is
 * picked.
 *
 * `PageOps.moveAll` takes a position in the *result*, so a run of `k` pages can start no later than
 * `pageCount - k`: asking for more would clamp onto the end and read as a move that did nothing. Deep
 * in the document a step is a step; at either end the run stops instead of wrapping round, because a
 * page that quietly teleports reads as a bug. Indices past the end are ignored rather than trusted —
 * the list is a view of a document that may have shrunk under it.
 */
internal fun pickedMoveTarget(picked: Set<Int>, delta: Int, pageCount: Int): Int? {
    val run = picked.filter { it in 0 until pageCount }
    val first = run.minOrNull() ?: return null
    val room = (pageCount - run.size).coerceAtLeast(0)
    return (first + delta).coerceIn(0, room)
}

/**
 * The lateral **page list** — the tablet's page overview, with the multi-select and the commands a
 * picked run of pages needs.
 *
 * It docks **on the rail's side**: `EditorBody` places this lane between the rail's own lane and the
 * canvas, so turning the toolbar to the right-hand edge takes the list with it. A page list on the
 * opposite edge from the tools would put every command in it a second hand's reach away, and the
 * window's strip has always followed the same rule.
 *
 * Two gestures per row, each with its own target: the page's name **goes to** that page (the list is a
 * navigator first), and the box at the row's end **picks** it for the commands underneath. Mixing the
 * two onto one target is what makes a multi-select feel like a trap — a tap that navigates is not a
 * tap that selects.
 *
 * The list is only *offered* here; nothing in it edits a page on its own. A move and a delete are
 * `PageOps`' block rules through the canvas's own commands, so a picked run is one undo step, exactly
 * as it is on the window.
 */
@Composable
internal fun PageListPanel(
    pageCount: Int,
    /** The page in view — the row the canvas is showing, marked so the list says where you are. */
    currentPage: Int,
    /** The picked pages (0-based). Indices past the end are ignored, not trusted. */
    selection: Set<Int>,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
    /** Move the picked run by [delta] positions, as one step. */
    onMove: (delta: Int) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = selection.filter { it in 0 until pageCount }.toSet()
    Surface(
        modifier = modifier.width(PAGE_LIST_WIDTH).fillMaxHeight(),
        color = rememberGlassSurfaceColor().compositeOver(rememberChromeColor()),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxHeight()) {
            PageListHeader(pageCount = pageCount, onClose = onClose)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(pageCount) { index ->
                    PageListRow(
                        index = index,
                        inView = index == currentPage,
                        picked = index in picked,
                        onGoTo = { onGoToPage(index) },
                        onToggle = {
                            onSelection(if (index in picked) picked - index else picked + index)
                        },
                    )
                }
            }
            PageListCommands(
                picked = picked,
                pageCount = pageCount,
                onMove = onMove,
                onDelete = onDelete,
            )
        }
    }
}

/** The lane's name, the document's size, and the door back out. */
@Composable
private fun PageListHeader(pageCount: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(PAGE_ROW_HEIGHT).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Pages", style = MaterialTheme.typography.labelLarge)
            Text(
                "$pageCount ${if (pageCount == 1) "page" else "pages"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Hide page list", modifier = Modifier.size(18.dp))
        }
    }
}

/** One page: where you are, whether it is picked, and the two things a row can do. */
@Composable
private fun PageListRow(
    index: Int,
    inView: Boolean,
    picked: Boolean,
    onGoTo: () -> Unit,
    onToggle: () -> Unit,
) {
    val rowFill = when {
        inView -> MaterialTheme.colorScheme.primaryContainer
        picked -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(PAGE_ROW_HEIGHT).background(rowFill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onGoTo)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "Page ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = rememberChromeInk(),
            )
        }
        PickBox(index = index, picked = picked, onToggle = onToggle)
    }
}

/**
 * The pick box at a row's end: a ring while the page is not picked, filled with the accent and a tick
 * when it is. A ring rather than an empty square, so the row does not read as an unfinished form.
 */
@Composable
private fun PickBox(index: Int, picked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (picked) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (picked) "Page ${index + 1} picked" else "Pick page ${index + 1}"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The list's three commands and the size of the pick.
 *
 * A move is refused at the ends of the document rather than wrapping the run round to the other end —
 * a page that quietly teleports reads as a bug — and a delete is refused when the pick is the whole
 * document, which is [com.mobixournal.render.PageOps.removeAll]'s own rule.
 */
@Composable
private fun PageListCommands(
    picked: Set<Int>,
    pageCount: Int,
    onMove: (delta: Int) -> Unit,
    onDelete: () -> Unit,
) {
    val canMoveUp = picked.isNotEmpty() && picked.min() > 0
    val canMoveDown = picked.isNotEmpty() && picked.max() < pageCount - 1
    val canDelete = picked.isNotEmpty() && picked.size < pageCount
    Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (picked.isEmpty()) "Tap a box to pick pages" else "${picked.size} picked",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move picked pages up", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move picked pages down", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete picked pages",
                    modifier = Modifier.size(18.dp),
                    tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
