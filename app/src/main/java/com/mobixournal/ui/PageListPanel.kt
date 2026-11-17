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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mobixournal.design.DocumentNavigationLayout
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.PageThumbnail
import com.mobixournal.render.pdfSheetFor
import com.mobixournal.render.pdfSourceFor
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeColor
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
import kotlin.math.roundToInt

/** The lane's width: a page number, its preview, a pick box and a drag handle — the window's strip. */
private val PAGE_LIST_WIDTH = 196.dp

/** One row: tall enough for the preview it carries, short enough that a dozen pages still fit. */
internal val PAGE_ROW_HEIGHT = DocumentNavigationLayout.PAGE_ROW_DP.dp

/** The number's column: a page's place in the document, and nothing else beside it. */
internal val PAGE_NUMBER_WIDTH = DocumentNavigationLayout.PAGE_NUMBER_DP.dp

/** The preview is rasterised at twice its display width, within these bounds. */
private const val MIN_RASTER_PX = 64
private const val MAX_RASTER_PX = 320

/** The landing line's thickness: the accent that says where a dragged page will go. */
private val LANDING_LINE_HEIGHT = 2.dp

/** The handle's target: small enough to leave the row's air, wide enough for a stylus. */
internal val DRAG_HANDLE_SIZE = DocumentNavigationLayout.PAGE_CONTROL_DP.dp

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

/**
 * The top bar's **one navigation capsule**: *Page list* and the bookmark ribbon, two halves under one
 * shell, divided by the chrome's own hairline ([TopBarToolDivider]).
 *
 * They answer one question — where else is there to look in this document — so they read as one object
 * rather than as two unrelated buttons, and the rule is the one the dock already divides its preview
 * with, so the chrome keeps a single separator drawn either way round. Each half holds its own flag:
 * the shell is there while either lane is open and the half you are looking at wears the accent.
 */
@Composable
internal fun NavigationCapsule(ui: EditorUiState) {
    Surface(
        shape = ModernTheme.capsuleShape,
        color = rememberGlassSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .floatingRim(ModernTheme.capsuleCornerRadiusDp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageListSegment(
                open = ui.pageListOpen && ui.navigationTab == NavigationTab.PAGES,
                onClick = { ui.toggleNavigation(NavigationTab.PAGES) },
            )
            // The rule takes no air of its own: each half already keeps its own inset, so the hairline
            // falls midway between the words and the ribbon instead of opening a second gap beside them.
            TopBarToolDivider(air = 0.dp)
            BookmarkSegment(ui)
        }
    }
}

/**
 * The navigation capsule's **page-list half**: its glyph, the words *Page list*, and the flag it holds.
 *
 * One tap drops the lateral page list down the rail's edge, the next takes it away. It stands beside the
 * search because the two are the same errand — looking *through* the document rather than at the sheet in
 * front of you — and it is lit while the lane is up, so the button says where the list went. It is
 * deliberately **not** a row in the Pages menu: that menu is a list of things to do to a page, and the
 * overview is not one of them.
 *
 * A **half with its name**, not a bare glyph: a page list is a place to go rather than a one-off action,
 * and the word is what tells a reader who has never opened it what the lane is for.
 */
@Composable
internal fun PageListSegment(open: Boolean, onClick: () -> Unit) {
    val compact = LocalConfiguration.current.screenWidthDp < DocumentNavigationLayout.COMPACT_WIDTH_DP
    val tint = if (open) MaterialTheme.colorScheme.primary else rememberChromeInk()
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Page list"; selected = open }
            .then(
                if (compact) Modifier.size(DocumentNavigationLayout.PAGE_CONTROL_DP.dp)
                else Modifier.height(DocumentNavigationLayout.PAGE_CONTROL_DP.dp).padding(horizontal = 10.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ViewList,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        if (!compact) {
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
 * to** the page (the list is a navigator first), the box **picks** it for a multi-page command and takes
 * the view there too — the dot and the highlighted row are never two different pages — and the handle
 * **carries** the page to the row it is dropped on, which wears the accent line while it travels.
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
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
    /**
     * Take the view to a page **without leaving the lane**: a pick is not a jump away, so on a compact
     * window the pick keeps the list open where a plain tap on the row would return to the page.
     */
    onShow: (Int) -> Unit,
    /** Move [picked] by [delta] positions, as one step. */
    onMove: (picked: Set<Int>, delta: Int) -> Unit,
    /** Carry the page at [from] to [to] — the drag's drop, as one step. */
    onReorder: (from: Int, to: Int) -> Unit,
    onDelete: (picked: Set<Int>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
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
    // A pick takes the view with it — the window's sidebar answers a click the same way (`PageSelectionRules`
    // chooses the set, then `onSelectPage` goes there). Without that the box could mark one page while the
    // canvas and the highlight stayed on another, which reads as a selection that did not happen.
    val toggle: (Int) -> Unit = { index ->
        onSelection(if (index in picked) picked - index else picked + index)
        onShow(index)
    }
    Surface(
        modifier = modifier.width(PAGE_LIST_WIDTH).fillMaxHeight(),
        color = rememberGlassSurfaceColor().compositeOver(rememberChromeColor()),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxHeight()) {
            if (showHeader) PageListHeader(pageCount = pageCount, onClose = onClose)
            PageListRows(
                pages = pages,
                surface = surface,
                pageCount = pageCount,
                currentPage = currentPage,
                acting = acting,
                onToggle = toggle,
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
    /** What a row's box asks for: the page joins or leaves the pick, and the view follows it. */
    onToggle: (Int) -> Unit,
    /**
     * What the commands act on ([actedPages]) rather than the bare pick: the page in view stands in
     * while nothing is picked, and the box at every row's end has to say the same thing the footer does.
     */
    acting: Set<Int>,
    carry: (Set<Int>, Int) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onSelection: (Set<Int>) -> Unit,
    onGoToPage: (Int) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { PAGE_ROW_HEIGHT.toPx() }
    val drag = remember(rowHeightPx) { PageListDrag(rowHeightPx) }
    val listState = rememberLazyListState()
    var viewportPx by remember { mutableIntStateOf(0) }
    // The list walks under the page while it sits in either end strip, so a page can be carried across a
    // long document without lifting the finger.
    PageListAutoScroll(drag = drag, listState = listState, rowHeightPx = rowHeightPx, viewportPx = viewportPx)
    val scroll = scrollPxOf(listState, rowHeightPx)
    val landing = drag.landing(scroll, pageCount)
    // The rows sit in a box because the carried page is drawn **over** them ([PageDragGhost]), and the
    // gesture lives on the list's own node ([pageListDragGesture]) rather than on a row's handle: the list
    // recycles the row that begins a drag as soon as that row scrolls out, and a recycled row takes its
    // pointer handler with it — which is why the auto-scroll used to stop a third of a second in.
    Box(Modifier.weight(1f)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportPx = it.height }
                .pointerInput(listState, rowHeightPx, pageCount, acting) {
                    pageListDragGesture(
                        drag = drag,
                        listState = listState,
                        rowHeightPx = rowHeightPx,
                        pageCount = pageCount,
                        onDrop = { from, to ->
                            if (from in acting && acting.size > 1) {
                                // A handle on a picked row carries the whole pick; on any other row it
                                // carries that page alone. Either way the view stays on it.
                                carry(acting, to - from)
                            } else if (to != from) {
                                onReorder(from, to)
                                onSelection(setOf(to))
                                onGoToPage(to)
                            }
                        },
                    )
                },
        ) {
            items(pageCount) { index ->
                PageListRow(
                    index = index,
                    page = pages.getOrNull(index),
                    surface = surface,
                    inView = index == currentPage,
                    acting = index in acting,
                    // The page would land on this row — and it is not already there.
                    landing = landing == index && landing != drag.from,
                    onGoTo = { onGoToPage(index) },
                    onToggle = { onToggle(index) },
                )
            }
        }
        PageDragGhost(
            drag = drag,
            listState = listState,
            rowHeightPx = rowHeightPx,
            pages = pages,
            surface = surface,
            picked = acting,
        )
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
    // The view goes first: a lane whose plain tap on a row reselects that page must not swallow the run
    // this command just moved, so the run's follow-up is the last word on the selection.
    onGoToPage(target)
    onSelection(run.mapIndexed { i, _ -> target + i }.toSet())
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
 * The box at the row's end wears the accent for **what the commands act on** ([actedPages]): the pick,
 * or the page in view while nothing is picked. The dot and the highlighted row therefore never
 * disagree — a plain tap on another page moves both, and the footer keeps naming the same page.
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
    acting: Boolean,
    landing: Boolean,
    onGoTo: () -> Unit,
    onToggle: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val rowFill = when {
        inView -> MaterialTheme.colorScheme.primaryContainer
        acting -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAGE_ROW_HEIGHT)
            .background(rowFill)
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
                color = if (inView || acting) accent else rememberChromeInk(),
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            PageListPreview(page = page, surface = surface, onGoTo = onGoTo)
        }
        PickBox(index = index, picked = acting, onToggle = onToggle)
        PageDragHandle(index = index)
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
internal fun PageListPreview(page: Page?, surface: DrawingSurfaceView?, onGoTo: () -> Unit) {
    val dimensions = DocumentNavigationLayout.previewSize(page?.width, page?.height)
    val width = dimensions.width.dp
    val height = dimensions.height.dp
    val widthPx = with(LocalDensity.current) { width.roundToPx() }
    val rasterPx = (widthPx * 2).coerceIn(MIN_RASTER_PX, MAX_RASTER_PX)
    val preview by produceState<android.graphics.Bitmap?>(null, page, surface, rasterPx) {
        // Resolve the view-owned cache on Main; only its thread-safe raster request runs on the worker.
        val source = (page?.background as? Background.Pdf)?.let { surface?.pdfSourceFor(it) }
        value = withContext(Dispatchers.Default) {
            page?.let { p ->
                runCatching {
                    val sheet = (p.background as? Background.Pdf)?.let { source?.request(it.pageNo, rasterPx) }
                    PageThumbnail.render(p, rasterPx, sheet)
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
            .clickable(onClick = onGoTo),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = preview
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The drag handle: the grabbable glyph at the row's end, and the band of the row where a drag may start.
 *
 * It draws the glyph and names it for a screen reader; the gesture itself belongs to the list
 * ([pageListDragGesture]), because a handler on this glyph dies with the row the moment the auto-scroll
 * recycles it. The band it marks is the row's last [DRAG_HANDLE_SIZE] of width, which is what the gesture
 * tests a press against.
 */
@Composable
private fun PageDragHandle(index: Int) {
    Box(
        modifier = Modifier
            .size(DRAG_HANDLE_SIZE)
            .semantics { contentDescription = "Drag page ${index + 1}" },
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
 *
 * The ring wears the row's muted **ink** and not the chrome `outline`: the outline is the same steel the
 * page in view is filled with (`#383838` against `#38383E`), so the ring disappeared on exactly the row a
 * reader had just landed on. `onSurfaceVariant` is the ink the row's handle already reads in, so the two
 * marks at the row's end keep one weight on every fill.
 */
@Composable
private fun PickBox(index: Int, picked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(DocumentNavigationLayout.PAGE_CONTROL_DP.dp).clickable(onClick = onToggle)
            .semantics { contentDescription = if (picked) "Page ${index + 1} picked" else "Pick page ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (picked) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(1.dp, if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (picked) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}
