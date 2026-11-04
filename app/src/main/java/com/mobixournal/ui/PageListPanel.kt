package com.mobixournal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.PageThumbnail
import com.mobixournal.render.pdfSheetFor
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

/** The number's column: a page's place in the document, and nothing else beside it. */
private val PAGE_NUMBER_WIDTH = 30.dp

/** The preview's height; its width follows the page's own proportions, so no sheet is forced upright. */
private val THUMB_HEIGHT = 48.dp

/** How narrow and how wide a preview may get, so a panorama page cannot eat the whole row. */
private val THUMB_MIN_WIDTH = 22.dp
private val THUMB_MAX_WIDTH = 68.dp

/** The shape a malformed page (no usable proportions) is given: A4's own. */
private const val PORTRAIT_RATIO = 0.707f

/** The preview is rasterised at twice its display width, within these bounds. */
private const val MIN_RASTER_PX = 64
private const val MAX_RASTER_PX = 320

/** The landing line's thickness: the accent that says where a dragged page will go. */
private val LANDING_LINE_HEIGHT = 2.dp

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
 * Each row is its **number** and its **preview** — the two things that identify a page, and no words
 * beside them — with a box that picks it and a handle that drags it. The number and the preview **go
 * to** the page (the list is a navigator first), the box **picks** it for a multi-page command, and the
 * handle **carries** the page to the row it is dropped on, which wears the accent line while it travels.
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
    /**
     * The live canvas, so a `pdf`-backed page's preview is its real sheet and not a blank card: the
     * rasteriser that already draws that page is the one that answers the thumbnail. Null before there
     * is a canvas, and a PDF page then falls back to its bare paper.
     */
    surface: DrawingSurfaceView? = null,
    /**
     * The lane's foot carries a door of its own when the document has one — the PDF's Contents — so a
     * jump between pages stands with the pages rather than in the menu of page commands.
     */
    contentsAction: (@Composable () -> Unit)? = null,
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
    // Both commands **carry** what they act on: the viewport and the pick follow the pages to their new
    // place, so the sheet in front of you is the page you just stepped — and the page the next tap acts on.
    val carry: (Set<Int>, Int) -> Unit = { pages, delta ->
        carryPages(pages, delta, pageCount, onMove, onSelection, onGoToPage)
    }
    val removeRun: (Set<Int>) -> Unit = { pages ->
        removePages(pages, pageCount, onDelete, onSelection, onGoToPage)
    }
    Surface(
        modifier = modifier.width(PAGE_LIST_WIDTH).fillMaxHeight(),
        color = rememberGlassSurfaceColor().compositeOver(rememberChromeColor()),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxHeight()) {
            PageListHeader(pageCount = pageCount, onClose = onClose)
            PageListRows(
                pages = pages,
                surface = surface,
                pageCount = pageCount,
                currentPage = currentPage,
                picked = picked,
                carry = carry,
                onReorder = onReorder,
                onSelection = onSelection,
                onGoToPage = onGoToPage,
            )
            PageListCommands(
                picked = picked,
                acting = acting,
                currentPage = currentPage,
                pageCount = pageCount,
                onMove = carry,
                onDelete = removeRun,
                contentsAction = contentsAction,
            )
        }
    }
}

/**
 * The lane's rows — the only part that knows about a drag, because the row that wears the landing line is
 * a **different** row from the lifted one, so one place has to hold both the origin and the target.
 *
 * The pitch is the row's own fixed [PAGE_ROW_HEIGHT], so a drop lands on the row the eye saw the page over
 * rather than on a pixel the finger happened to stop at.
 */
@Composable
private fun ColumnScope.PageListRows(
    pages: List<Page>,
    surface: DrawingSurfaceView?,
    pageCount: Int,
    currentPage: Int,
    picked: Set<Int>,
    carry: (Set<Int>, Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { PAGE_ROW_HEIGHT.toPx() }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val landing = dragFrom?.let {
        (it + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, pageCount - 1)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(pageCount) { index ->
            PageListRow(
                index = index,
                page = pages.getOrNull(index),
                surface = surface,
                inView = index == currentPage,
                picked = index in picked,
                lifted = dragFrom == index,
                lift = if (dragFrom == index) dragOffset else 0f,
                // The page would land on this row — and it is not already there.
                landing = landing == index && landing != dragFrom,
                onGoTo = { onGoToPage(index) },
                onToggle = { onSelection(if (index in picked) picked - index else picked + index) },
                onDragStart = {
                    dragFrom = index
                    dragOffset = 0f
                },
                onDragBy = { dragOffset = it },
                onDragEnd = {
                    val from = dragFrom
                    if (from != null) {
                        val to = (from + (dragOffset / rowHeightPx).roundToInt())
                            .coerceIn(0, pageCount - 1)
                        // A handle on a picked row carries the whole pick; on any other row it carries
                        // that page alone. Either way the view stays on it.
                        if (from in picked && picked.size > 1) {
                            carry(picked, to - from)
                        } else if (to != from) {
                            onReorder(from, to)
                            onSelection(setOf(to))
                            onGoToPage(to)
                        }
                    }
                    dragFrom = null
                    dragOffset = 0f
                },
                onDragCancel = {
                    dragFrom = null
                    dragOffset = 0f
                },
            )
        }
    }
}

/**
 * Carry [pages] by [delta] places, then leave the viewport and the pick on them at their new place.
 *
 * `PageOps.moveAll` drops the run's first page at the target, so the run's members land at `target + i`
 * — one arithmetic in one place, because the arrows and the drag both come through here. Without the
 * follow-up the arrows acted on whatever page had slid under the same index, which read as buttons that
 * worked now and then.
 */
internal fun carryPages(
    pages: Set<Int>,
    delta: Int,
    pageCount: Int,
    onMove: (pages: Set<Int>, delta: Int) -> Unit,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
) {
    if (delta == 0) return
    val run = pages.filter { it in 0 until pageCount }.sorted()
    val target = pickedMoveTarget(pages, delta, pageCount) ?: return
    onMove(pages, delta)
    onSelection(run.mapIndexed { i, _ -> target + i }.toSet())
    onGoToPage(target)
}

/**
 * Delete [pages], then land on the page that took the first removed place, so the sheet in front of you is
 * never one that no longer exists. A pick covering the whole document is refused — `PageOps.removeAll`'s
 * own rule, refused here rather than after the fact so the viewport is never asked to follow nothing.
 */
internal fun removePages(
    pages: Set<Int>,
    pageCount: Int,
    onDelete: (Set<Int>) -> Unit,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
) {
    val run = pages.filter { it in 0 until pageCount }.sorted()
    if (run.isEmpty() || run.size >= pageCount) return
    val landingPage = run.first().coerceAtMost(pageCount - run.size - 1).coerceAtLeast(0)
    onDelete(pages)
    onSelection(emptySet())
    onGoToPage(landingPage)
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
 * While it travels the page is **lifted** — raised, a touch transparent — and the row it would land on
 * wears the accent along its top edge, the same pair of marks the window's strip makes (`PageSidebar`), so
 * a drag reads as a move in progress rather than as a page that changes places after the fact. The drop is
 * `places = dragged / rowHeight` rounded, on the list's fixed row pitch, so the page lands on the row the
 * eye saw it over.
 */
@Composable
private fun PageListRow(
    index: Int,
    page: Page?,
    surface: DrawingSurfaceView?,
    inView: Boolean,
    picked: Boolean,
    lifted: Boolean,
    lift: Float,
    landing: Boolean,
    onGoTo: () -> Unit,
    onToggle: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
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
                alpha = if (lifted) 0.92f else 1f
                shadowElevation = if (lifted) 10.dp.toPx() else 0f
            }
            .background(if (lifted) dragFill else rowFill)
            // The row the page would land on wears the accent along its **top edge**: one line in the
            // chrome's accent, so where the page will go is readable while the finger travels.
            .drawBehind {
                if (landing) drawRect(accent, size = Size(size.width, LANDING_LINE_HEIGHT.toPx()))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The number, alone on its column: a list of pages is read by its numbers.
        Box(
            modifier = Modifier.width(PAGE_NUMBER_WIDTH).fillMaxHeight().clickable(onClick = onGoTo),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (inView || picked) accent else rememberChromeInk(),
            )
        }
        PageListPreview(page = page, surface = surface, onGoTo = onGoTo)
        Spacer(Modifier.weight(1f))
        PickBox(index = index, picked = picked, onToggle = onToggle)
        PageDragHandle(
            index = index,
            onStart = onDragStart,
            onBy = onDragBy,
            onEnd = onDragEnd,
            onCancel = onDragCancel,
        )
    }
}

/**
 * A page's own preview, beside its number — the sheet's paper and its ink, on a white card so that even
 * a blank page reads as a page rather than as an empty frame.
 *
 * **The card is the sheet's shape.** Its width follows the page's own proportions at [THUMB_HEIGHT] tall,
 * so a portrait page is a tall sliver, a landscape one a wide strip and a PDF page looks like the sheet the
 * canvas is showing — never one fixed vertical block with the page floating inside it. The raster is twice
 * the display width, so text on a PDF page stays legible instead of turning to grey.
 *
 * A `pdf` page asks the canvas for its raster ([pdfSheetFor]) rather than drawing its bare paper: the
 * sheet *is* that page, and a white card with the ink on it would preview the annotations and not the text
 * the reader is annotating.
 */
@Composable
private fun PageListPreview(page: Page?, surface: DrawingSurfaceView?, onGoTo: () -> Unit) {
    val ratio = page?.let { if (it.height > 0.0) (it.width / it.height).toFloat() else null }
    val width = (THUMB_HEIGHT * (ratio ?: PORTRAIT_RATIO)).coerceIn(THUMB_MIN_WIDTH, THUMB_MAX_WIDTH)
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val rasterPx = (widthPx * 2).coerceIn(MIN_RASTER_PX, MAX_RASTER_PX)
    val preview = remember(page, rasterPx) {
        page?.let { p ->
            val sheet = (p.background as? Background.Pdf)?.let { bg -> surface?.pdfSheetFor(bg, rasterPx) }
            runCatching { PageThumbnail.render(p, rasterPx, sheet) }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .width(width)
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
 *
 * It reports a **travelled distance**, not a decision: the list owns the row pitch and the landing row, so
 * the handle only has to say how far the finger went. Both callbacks are read through holders, because the
 * pointer handlers outlive the composition that built them.
 */
@Composable
private fun PageDragHandle(
    index: Int,
    onStart: () -> Unit,
    onBy: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    val start = rememberUpdatedState(onStart)
    val by = rememberUpdatedState(onBy)
    val end = rememberUpdatedState(onEnd)
    val cancel = rememberUpdatedState(onCancel)
    Box(
        modifier = Modifier
            .size(DRAG_HANDLE_SIZE)
            .semantics { contentDescription = "Drag page ${index + 1}" }
            .pointerInput(index) {
                var travelled = 0f
                detectDragGestures(
                    onDragStart = {
                        travelled = 0f
                        start.value()
                    },
                    onDragEnd = { end.value() },
                    onDragCancel = { cancel.value() },
                    onDrag = { change, amount ->
                        change.consume()
                        travelled += amount.y
                        by.value(travelled)
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
 * to a page and wants it gone should not have to tick a box first, and the figure above the buttons is
 * the subject — a count for a pick, the page's own number for the page in view, in the same ink as the
 * rows carry it.
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
    contentsAction: (@Composable () -> Unit)?,
) {
    val canMoveUp = acting.isNotEmpty() && acting.min() > 0
    val canMoveDown = acting.isNotEmpty() && acting.max() < pageCount - 1
    val canDelete = acting.isNotEmpty() && acting.size < pageCount
    val subject = when {
        picked.isNotEmpty() -> "${picked.size} picked"
        acting.isNotEmpty() -> "${currentPage + 1}"
        else -> "\u2014"
    }
    Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The subject on the left, the lane's own door on the right: the foot's bottom-right corner.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subject,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            contentsAction?.invoke()
        }
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
