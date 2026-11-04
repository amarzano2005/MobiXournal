package com.mobixournal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.DragHandle
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
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
import kotlin.math.roundToInt

/** The lane's width: a page number, its preview, a pick box and a drag handle — the window's strip. */
private val PAGE_LIST_WIDTH = 196.dp

/** One row: tall enough for the preview it carries, short enough that a dozen pages still fit. */
private val PAGE_ROW_HEIGHT = 64.dp

/** The number's column, so every preview starts on the same line down the lane. */
private val PAGE_NUMBER_WIDTH = 54.dp

/** The preview: a sheet a thumb wide, as tall as its page's own proportions allow. */
private val THUMB_WIDTH = 36.dp
private val THUMB_HEIGHT = 50.dp
private const val THUMB_WIDTH_PX = 120

/** The handle's target: small enough to leave the row's air, wide enough for a stylus. */
private val DRAG_HANDLE_SIZE = 32.dp

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
 * The pages a command acts on: the pick, or — when nothing is picked — the page in view.
 *
 * The list is a navigator as much as a picker, so a reader who has scrolled to a page and wants it gone
 * should not have to tick a box first. Picking a run is how several pages are acted on at once; acting
 * on the one you are looking at is the plain case, and it needs no ceremony.
 */
internal fun actedPages(picked: Set<Int>, currentPage: Int, pageCount: Int): Set<Int> {
    val run = picked.filter { it in 0 until pageCount }
    if (run.isNotEmpty()) return run.toSet()
    return if (currentPage in 0 until pageCount) setOf(currentPage) else emptySet()
}

/** The capsule wired to the chrome's own flag, so the top bar only says where it stands. */
@Composable
internal fun PageListCapsuleFor(ui: EditorUiState) {
    PageListCapsule(open = ui.pageListOpen) {
        ui.pageListOpen = !ui.pageListOpen
        if (!ui.pageListOpen) ui.pageSelection = emptySet()
    }
}

/**
 * The **page list** capsule in the top bar: its glyph, the words *Page list*, and the flag it holds.
 *
 * One tap drops the lateral page list down the rail's edge, the next takes it away. It stands beside the
 * search because the two are the same errand — looking *through* the document rather than at the sheet in
 * front of you — and it is lit while the lane is up, so the button says where the list went. It is
 * deliberately **not** a row in the Pages menu: that menu is a list of things to do to a page, and the
 * overview is not one of them.
 *
 * A **capsule with its name**, not a bare glyph: a page list is a place to go rather than a one-off
 * action, and the word is what tells a reader who has never opened it what the lane is for.
 */
@Composable
internal fun PageListCapsule(open: Boolean, onClick: () -> Unit) {
    val tint = if (open) MaterialTheme.colorScheme.primary else rememberChromeInk()
    Surface(
        shape = ModernTheme.capsuleShape,
        color = rememberGlassSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .floatingRim(ModernTheme.capsuleCornerRadiusDp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ViewList,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Page list", style = MaterialTheme.typography.labelLarge, color = tint)
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
 * Each row is its **number** with its **preview** beside it, a box that picks it and a handle that
 * drags it. The number and the preview **go to** the page (the list is a navigator first), the box
 * **picks** it for a multi-page command, and the handle **carries** the page to the row it is dropped on.
 *
 * The previews are drawn from the live pages ([PageThumbnail]), so a row shows its own ink and its own
 * paper rather than a placeholder that has to catch up later.
 */
@Composable
internal fun PageListPanel(
    pageCount: Int,
    /** The page in view — the row the canvas is showing, and what the commands act on when nothing is picked. */
    currentPage: Int,
    /** The picked pages (0-based). Indices past the end are ignored, not trusted. */
    selection: Set<Int>,
    /** The document's pages, in order — the previews' source. */
    pages: List<Page>,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
    /** Move [picked] by [delta] positions, as one step. */
    onMove: (picked: Set<Int>, delta: Int) -> Unit,
    /** Carry the page at [from] to [to] — the drag's drop, as one step. */
    onReorder: (from: Int, to: Int) -> Unit,
    onDelete: (picked: Set<Int>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = selection.filter { it in 0 until pageCount }.toSet()
    val acting = actedPages(picked, currentPage, pageCount)
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
                acting = acting,
                currentPage = currentPage,
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
 * One page: its number, its preview beside it, the box that picks it and the handle that drags it.
 *
 * The drag lives on the **handle**, not on the row. A row that starts a drag on any touch is a row that
 * cannot be scrolled — the list would have to guess whether a finger meant "move this page" or "look
 * further down" — and a long press to disambiguate loses the gesture to the list's own scroll the moment
 * the finger travels. A handle is one explicit target, its drag is immediate, and the row keeps its
 * plain tap.
 *
 * The drop is `places = dragged / rowHeight` rounded, on the list's fixed row pitch, so the page lands
 * on the row the eye saw it over.
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
    val lift = dragOffset
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
                translationY = lift
                alpha = if (dragging) 0.92f else 1f
                shadowElevation = if (dragging) 10.dp.toPx() else 0f
            }
            .background(if (dragging) dragFill else rowFill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The number, first, on its own column: a list of pages is read by its numbers.
        Box(
            modifier = Modifier.width(PAGE_NUMBER_WIDTH).fillMaxHeight().clickable(onClick = onGoTo),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(Modifier.padding(start = 12.dp)) {
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
        }
        PageListPreview(page, onGoTo)
        Spacer(Modifier.weight(1f))
        PickBox(index = index, picked = picked, onToggle = onToggle)
        PageDragHandle(
            index = index,
            pageCount = pageCount,
            rowHeightPx = rowHeightPx,
            onDragOffset = { dragOffset = it },
            onDragging = { dragging = it },
            onDrop = { places ->
                if (places != 0) onReorder(index, (index + places).coerceIn(0, pageCount - 1))
            },
        )
    }
}

/**
 * A page's own preview, beside its number — the sheet's paper and its ink, on a white card so that even
 * a blank page reads as a page rather than as an empty frame.
 */
@Composable
private fun PageListPreview(page: Page?, onGoTo: () -> Unit) {
    val preview = remember(page) {
        page?.let { runCatching { PageThumbnail.render(it, THUMB_WIDTH_PX) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .width(THUMB_WIDTH)
            .height(THUMB_HEIGHT)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
            .clickable(onClick = onGoTo),
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
 * The drag handle: a grabbable glyph at the row's end, and the only place a drag can start.
 *
 * `detectDragGestures` (immediate, not after a long press) on a target this small is what makes the
 * gesture reliable inside a scrolling list: the handle claims the touch before the list's scroll does,
 * so pulling a page down moves the page rather than the list.
 */
@Composable
private fun PageDragHandle(
    index: Int,
    pageCount: Int,
    rowHeightPx: Float,
    onDragOffset: (Float) -> Unit,
    onDragging: (Boolean) -> Unit,
    onDrop: (places: Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(DRAG_HANDLE_SIZE)
            .semantics { contentDescription = "Drag page ${index + 1}" }
            .pointerInput(index, pageCount) {
                var travelled = 0f
                detectDragGestures(
                    onDragStart = {
                        travelled = 0f
                        onDragging(true)
                    },
                    onDragEnd = {
                        onDragging(false)
                        onDragOffset(0f)
                        onDrop((travelled / rowHeightPx).roundToInt())
                    },
                    onDragCancel = {
                        onDragging(false)
                        onDragOffset(0f)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travelled += amount.y
                        onDragOffset(travelled)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
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
            .padding(end = 4.dp)
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
 * The list's three commands and what they will act on.
 *
 * The commands take the **pick**, or the page in view when nothing is picked: a reader who has scrolled
 * to a page and wants it gone should not have to tick a box first, and the line above the buttons says
 * which of the two is in force.
 *
 * A move is refused at the ends of the document rather than wrapping the run round to the other end —
 * a page that quietly teleports reads as a bug — and a delete is refused when the pick is the whole
 * document, which is [com.mobixournal.render.PageOps.removeAll]'s own rule.
 */
@Composable
private fun PageListCommands(
    picked: Set<Int>,
    acting: Set<Int>,
    currentPage: Int,
    pageCount: Int,
    onMove: (picked: Set<Int>, delta: Int) -> Unit,
    onDelete: (picked: Set<Int>) -> Unit,
) {
    val canMoveUp = acting.isNotEmpty() && acting.min() > 0
    val canMoveDown = acting.isNotEmpty() && acting.max() < pageCount - 1
    val canDelete = acting.isNotEmpty() && acting.size < pageCount
    val subject = when {
        picked.isNotEmpty() -> "${picked.size} picked"
        acting.isNotEmpty() -> "Page ${currentPage + 1}"
        else -> "No page"
    }
    Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            subject,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(acting, -1) }, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.ArrowUpward,
                    contentDescription = "Move pages up",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { onMove(acting, 1) }, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.ArrowDownward,
                    contentDescription = "Move pages down",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { onDelete(acting) }, enabled = canDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete pages",
                    modifier = Modifier.size(18.dp),
                    tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
