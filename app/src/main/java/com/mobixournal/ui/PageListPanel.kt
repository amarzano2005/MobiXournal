package com.mobixournal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.Page
import com.mobixournal.render.PageThumbnail
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
import kotlin.math.roundToInt

/** The lane's width: a preview, a page number and a pick box — the window's strip, narrowed. */
private val PAGE_LIST_WIDTH = 168.dp

/** One row: preview, name, pick box. Tall enough for a thumb, short enough to read the list. */
private val PAGE_ROW_HEIGHT = 62.dp

/** The preview's box, and the width it is rasterised at — a box on a tablet, no more. */
private val THUMB_BOX = 42.dp
private const val THUMB_WIDTH_PX = 96

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
 * The **page list** capsule in the top bar: one tap drops the lateral page list down the rail's edge,
 * the next takes it away.
 *
 * It stands beside the search because the two are the same errand — looking *through* the document
 * rather than at the sheet in front of you — and it is lit while the lane is up, so the button says
 * where the list went. It is deliberately **not** a row in the Pages menu: the menu is a list of things
 * to do to a page, and the overview is not one of them (the window keeps its strip on the same terms).
 */
/** The capsule wired to the chrome's own flag, so the top bar only says where it stands. */
@Composable
internal fun PageListCapsuleFor(ui: EditorUiState) {
    PageListCapsule(open = ui.pageListOpen) {
        ui.pageListOpen = !ui.pageListOpen
        if (!ui.pageListOpen) ui.pageSelection = emptySet()
    }
}

@Composable
internal fun PageListCapsule(open: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = rememberGlassSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(horizontal = 2.dp).floatingRim(18.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.ViewList,
                contentDescription = if (open) "Hide page list" else "Page list",
                tint = if (open) MaterialTheme.colorScheme.primary else rememberChromeInk(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The lateral **page list** — the tablet's page overview, the window's strip on the same terms.
 *
 * It docks **on the rail's side** (`EditorBody` places this lane between the rail's lane and the
 * canvas), so turning the toolbar to the right-hand edge takes the list with it, exactly as the
 * window's strip follows its tools. Like the rail it is a lane of the layout and not a sheet over the
 * page: every pixel of the visible page stays writable.
 *
 * Three gestures per row, each with its own target. A tap on the row **goes to** that page (the list is
 * a navigator first), the box at the row's end **picks** it for the commands underneath (a tap that
 * navigates is not a tap that selects), and a **long-press drag** carries the page to the place it is
 * dropped on — the window's drag, on a fixed row pitch.
 *
 * The previews are drawn from the live pages ([PageThumbnail]), so a row shows its own ink and its own
 * paper rather than a placeholder that has to catch up later.
 */
@Composable
internal fun PageListPanel(
    pageCount: Int,
    /** The page in view — the row the canvas is showing, marked so the list says where you are. */
    currentPage: Int,
    /** The picked pages (0-based). Indices past the end are ignored, not trusted. */
    selection: Set<Int>,
    /** The document's pages, in order — the previews' source. */
    pages: List<Page>,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
    /** Move the picked run by [delta] positions, as one step. */
    onMove: (delta: Int) -> Unit,
    /** Carry the page at [from] to [to] — the drag's drop, as one step. */
    onReorder: (from: Int, to: Int) -> Unit,
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
                        page = pages.getOrNull(index),
                        inView = index == currentPage,
                        picked = index in picked,
                        pageCount = pageCount,
                        onGoTo = { onGoToPage(index) },
                        onToggle = {
                            onSelection(if (index in picked) picked - index else picked + index)
                        },
                        onReorder = onReorder,
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

/**
 * One page: its preview, its name, and the box that picks it.
 *
 * The drag is a **long press** then a move, on a fixed row pitch: the row follows the finger and the
 * drop is `places = dragY / rowHeight` rounded, so the page lands where the eye saw it go. A long press
 * rather than a plain drag, because a plain drag on a list of rows is a scroll.
 */
@Composable
private fun PageListRow(
    index: Int,
    page: Page?,
    inView: Boolean,
    picked: Boolean,
    pageCount: Int,
    onGoTo: () -> Unit,
    onToggle: () -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { PAGE_ROW_HEIGHT.toPx() }
    var dragOffset by remember(index) { mutableFloatStateOf(0f) }
    var dragging by remember(index) { mutableStateOf(false) }
    val rowFill = when {
        inView -> MaterialTheme.colorScheme.primaryContainer
        picked -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val dragFill = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAGE_ROW_HEIGHT)
            .graphicsLayer {
                translationY = dragOffset
                alpha = if (dragging) 0.9f else 1f
                shadowElevation = if (dragging) 12.dp.toPx() else 0f
            }
            .background(if (dragging) dragFill else rowFill)
            .pointerInput(index, pageCount) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        val places = (dragOffset / rowHeightPx).roundToInt()
                        dragging = false
                        dragOffset = 0f
                        if (places != 0) onReorder(index, (index + places).coerceIn(0, pageCount - 1))
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageListPreview(page)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onGoTo),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Page ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = rememberChromeInk(),
            )
            if (inView) {
                Text(
                    "In view",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        PickBox(index = index, picked = picked, onToggle = onToggle)
    }
}

/** A page's own preview, in the row's leading box — blank while the page has no size to draw. */
@Composable
private fun PageListPreview(page: Page?) {
    val preview = remember(page) {
        page?.let { runCatching { PageThumbnail.render(it, THUMB_WIDTH_PX) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .width(THUMB_BOX)
            .height(THUMB_BOX)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
                Icon(
                    Icons.Outlined.ArrowUpward,
                    contentDescription = "Move picked pages up",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.ArrowDownward,
                    contentDescription = "Move picked pages down",
                    modifier = Modifier.size(18.dp),
                )
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
