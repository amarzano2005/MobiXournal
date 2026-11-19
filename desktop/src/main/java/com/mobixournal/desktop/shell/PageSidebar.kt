package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.bandSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.navigation.PageDragHandle
import com.mobixournal.desktop.navigation.PageDragMetrics
import com.mobixournal.desktop.sheet.PageSelection
import com.mobixournal.desktop.sheet.PageSelectionBar
import com.mobixournal.desktop.sheet.PageSelectionRules
import com.mobixournal.desktop.sheet.drawPageContent
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.sheet.rememberSelectionTap
import com.mobixournal.render.OutlineEdits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import kotlin.math.roundToInt
import com.mobixournal.desktop.shell.search.SearchResultsSection
import com.mobixournal.desktop.tools.BandEdge
import com.mobixournal.desktop.tools.bandDivider

/** The three Contents edits the sidebar runs, kept together so the sidebar's own signature stays about pages. */
class ContentsEdits(
    /** [page] is the page the reader was on when the command was pressed — captured, never assumed. */
    val add: (String, Int?) -> OutlineEdits.Result = { _, _ ->
        OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND)
    },
    val rename: (String, String) -> OutlineEdits.Result = { _, _ -> OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND) },
    val move: (String, Int) -> OutlineEdits.Result = { _, _ -> OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND) },
    val remove: (String) -> OutlineEdits.Result = { OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND) },
)

/** The list's scroll gestures, measured here so [PageSidebar] stays a function that arranges its parts. */
@Composable
private fun PageListGestures(listState: LazyListState, drag: PageDragHandle) {
    val density = LocalDensity.current
    PageListScrolling(
        listState,
        drag,
        with(density) { PageDragMetrics.EDGE.toPx() },
        with(density) { PageDragMetrics.MAX_STEP.toPx() },
    )
}

/**
 * The navigation sidebar: page thumbnails and the current page's layers.
 *
 * It is a **panel beside the canvas**, not an overlay on it — collapsing it hands the width back and
 * the page refits in what remains (never hidden under chrome, the same rule the mobile bands follow).
 * `Ctrl+B` and the header glyph toggle it.
 *
 * Thumbnails are rendered once per page and kept in [cache] (owned by the shell, one map per tab): a
 * page that scrolls out of the sidebar and back must not be inflated and painted again, and a 300-page
 * notebook must not build 300 skia scenes on the way in — the lazy list only asks for the rows near the
 * viewport, and each of those is rendered the first time it is seen.
 *
 * An edit does not repaint one either. Rendering a thumbnail is a page painted **and** rasterised in
 * software — 29–39 ms on a dense sheet, measured — so the rows are keyed on [revision], which the shell
 * bumps once a burst of edits has gone quiet (`ThumbnailRefresh`), never on the document's edit count: that
 * key made every finished letter and every eraser bite pay for one inside composition, on the frame the pen
 * was still moving in. A stale bitmap is dropped where the edit happens, so the deferred re-render paints
 * the edited page and the other rows still hit the cache.
 */
@Composable
fun PageSidebar(
    tab: DesktopTab,
    cache: MutableMap<Int, ImageBitmap>,
    onSelectPage: (Int) -> Unit,
    /** The shell's deferred [DesktopShellState.thumbnailRevision] — see the KDoc above. */
    revision: Int,
    /** A row's own delete: the PDF-page list can drop a sheet where the reader sees it. */
    onRemovePage: (Int) -> Unit = {},
    /** A thumbnail dragged onto another row: the page moves to that row's index, one undo step. */
    onMovePage: (Int, Int) -> Unit = { _, _ -> },
    /** A drag that carries the picked pages: they travel as one block, one undo step. */
    onMovePages: (Set<Int>, Int) -> Unit = { _, _ -> },
    /** The selection bar's delete: every picked page goes, one undo step. */
    onRemovePages: (Set<Int>) -> Unit = {},
    /** The `+` row under the last thumbnail: a blank sheet after that page, one undo step. */
    onInsertPage: (Int) -> Unit = {},
    /** The selection bar's clipboard: the picked pages, not the caught elements. */
    onCopyPages: () -> Unit = {},
    onCutPages: () -> Unit = {},
    onPastePages: () -> Unit = {},
    /** A row of the search results: the page goes there and that match becomes the one in view. */
    onSearchHit: (Int) -> Unit = {},
    /** The Contents section's own edits: a row named for the page in view, retitled, or removed. */
    contentsEdits: ContentsEdits = ContentsEdits(),
    modifier: Modifier = Modifier,
) {
    val listState = remember(tab) {
        LazyListState(firstVisibleItemIndex = tab.page.coerceIn(0, (tab.pageCount - 1).coerceAtLeast(0)))
    }
    val current = tab.page
    val drag = remember(listState) { PageDragHandle(listState) }
    val selection = tab.pickedPages.sanitized(tab.pageCount)
    PageListGestures(listState, drag)

    Column(
        modifier
            .width(SIDEBAR_WIDTH).fillMaxHeight()
            // The flat dock's material, the same one the inspector lane wears on the other side: the band's
            // opaque tone and **one** 1 dp divider against the canvas, no rim all round and no lift.
            .bandSurface(DesktopTheme.glass)
            .bandDivider(BandEdge.END)
            .padding(vertical = 12.dp),
    ) {
        // Pages or Contents: one persistent switch, and the view it names fills the sidebar. The choice lives
        // on the tab, so two tabs can read two different views and neither loses its own.
        SidebarViewSwitch(view = tab.contentsView, onView = { tab.contentsView = it })
        when (tab.contentsView) {
            DesktopSidebarView.CONTENTS -> ContentsPanel(
                tab = tab,
                onSelectPage = onSelectPage,
                contentsEdits = contentsEdits,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            DesktopSidebarView.PAGES -> {
        PageThumbnailList(
            tab = tab,
            listState = listState,
            current = current,
            selection = selection,
            revision = revision,
            cache = cache,
            drag = drag,
            onSelectPage = onSelectPage,
            onRemovePage = onRemovePage,
            onMovePage = onMovePage,
            onMovePages = onMovePages,
            onInsertPage = onInsertPage,
        )
        SidebarSections(
            tab = tab,
            selection = selection,
            onSelectPage = onSelectPage,
            onRemovePages = onRemovePages,
            onCopyPages = onCopyPages,
            onCutPages = onCutPages,
            onPastePages = onPastePages,
            onSearchHit = onSearchHit,
        )
            }
        }
    }
}

/**
 * The edge a drag holds against so a page from the top of a long PDF can be dropped at the bottom without
 * letting go.
 *
 * Selecting a row in the sidebar deliberately does **not** scroll the list (`animateScrollToItem` used to
 * pull the clicked row to the top of the carousel); the list stays at the reader's scroll position and
 * steps only one frame at a time while a drag is against the edge.
 */
@Composable
private fun PageListScrolling(
    listState: LazyListState,
    drag: PageDragHandle,
    edgePx: Float,
    maxStepPx: Float,
) {
    LaunchedEffect(drag.active) {
        while (drag.active) {
            withFrameNanos { }
            val step = drag.autoScroll(edgePx, maxStepPx)
            if (step != 0f) listState.scrollBy(step)
        }
    }
}

/** A layer's own name, or its position in the page when it has none. */
internal fun Layer.label(index: Int): String = name ?: "Layer ${index + 1}"

/** How far through the document the current page is, for the status bar. */
internal fun pageProgress(page: Int, count: Int): Float =
    if (count <= 1) 1f else (page + 1).toFloat() / count

/** The thumbnail width the sidebar uses, in dp. */
internal val THUMB_WIDTH = 148.dp

private val SIDEBAR_WIDTH = 240.dp

/**
 * What sits **under** the page list: the current page's layers, or the picked pages' bar, then the search's
 * results and the PDF's Contents.
 *
 * One composable so the page list's own body stays the list, and so the order of these four — layers above
 * results above outline — is written once rather than implied by three trailing calls (`SourceStructureTest`
 * keeps `PageSidebar` a function that arranges the lane, not one that also prints everything in it).
 */
@Composable
private fun SidebarSections(
    tab: DesktopTab,
    selection: PageSelection,
    onSelectPage: (Int) -> Unit,
    onRemovePages: (Set<Int>) -> Unit,
    onCopyPages: () -> Unit,
    onCutPages: () -> Unit,
    onPastePages: () -> Unit,
    onSearchHit: (Int) -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    if (selection.isEmpty) {
        LayersSection(tab)
    } else {
        PageSelectionBar(
            count = selection.pages.size,
            onDelete = {
                onRemovePages(selection.pages)
                tab.pickedPages = PageSelection()
            },
            onClear = { tab.pickedPages = PageSelection() },
            onCopy = onCopyPages,
            onCut = onCutPages,
            onPaste = onPastePages,
        )
    }
    // The search's own list: while a query is up, the matches are what the reader is looking for. The
    // Contents are their own view now ([ContentsPanel]), not a section under these three.
    SearchResultsSection(tab, onSearchHit)
}

/**
 * The thread thumbnails are painted on: the pool's own, narrowed to **one** lane.
 *
 * One, because a page's paint builds a scene and walks the page it shares with the document; a single lane
 * keeps that walk serial — never the UI thread's, and never two pages' at once.
 */
internal val THUMBNAIL_PAINT = Dispatchers.Default.limitedParallelism(1)

/**
 * The page's thumbnail: rendered on first sight, then kept.
 *
 * [renderPageBitmap] is what makes this a *picture* of the page rather than a second painter — the same
 * `drawPageContent` paints it, so a thumbnail cannot disagree with the page it previews. (It is also why
 * thumbnails are cached: a page render per scroll would be far more expensive than the picture it keeps.)
 */
internal fun thumbnail(
    tab: DesktopTab,
    index: Int,
    widthDp: Float,
    cache: MutableMap<Int, ImageBitmap>,
): ImageBitmap? {
    val hidden = tab.hiddenOn(index)
    // The cache holds the *unfiltered* page. A page with layers switched off is painted fresh (and
    // remembered by the caller's `remember` keyed on the hidden set), so switching a layer on again
    // cannot hand back a thumbnail that is missing it.
    if (hidden.isEmpty()) cache[index]?.let { return it }
    val page = runCatching { tab.document.page(index) }.getOrNull() ?: return null
    if (page.width <= 0.0) return null
    val scale = widthDp / page.width.toFloat()
    val bitmap = runCatching {
        renderPageBitmap(page, scale, hiddenLayers = hidden, backdrop = tab.document.backdrop)
    }.getOrNull() ?: return null
    if (hidden.isEmpty()) cache[index] = bitmap
    return bitmap
}

/**
 * The font resolver thumbnails measure text with — built once, for the painting lane alone.
 *
 * A thumbnail is painted **outside any composition** (`ThumbnailPaint`), and a `TextMeasurer` needs a
 * resolver of its own there: the composition's is not reachable, and borrowing the window's would put two
 * threads on one font cache.
 */
private val THUMBNAIL_FONTS by lazy { createFontFamilyResolver() }

/**
 * Renders [page] at [scale] into a bitmap — thumbnails today, page-list previews tomorrow.
 *
 * Painted onto a **raster Skia surface** with the page's own painter, not through an `ImageComposeScene`:
 * a scene is a composition with a lifecycle, and one rendered on the sidebar's thread while the window's
 * own scene was drawing took that shared lifecycle apart ("must run runDetachLifecycle()…" on teardown).
 * A surface is just pixels, so the row can be painted wherever there is a thread to spare, and what is
 * drawn is still `drawPageContent` — the painter the page itself uses.
 */
internal fun renderPageBitmap(
    page: Page,
    scale: Float,
    density: Density = Density(1f),
    hiddenLayers: Set<Int> = emptySet(),
    /**
     * What the page sits on. A PDF-backed page needs it here as much as on the canvas: a page list whose
     * rows are bare paper cannot be reordered or pruned, because there is nothing to recognise.
     */
    backdrop: PageBackdrop? = null,
): ImageBitmap {
    val w = (page.width * scale).roundToInt().coerceAtLeast(1)
    val h = (page.height * scale).roundToInt().coerceAtLeast(1)
    val measurer = TextMeasurer(
        defaultFontFamilyResolver = THUMBNAIL_FONTS,
        defaultDensity = density,
        defaultLayoutDirection = LayoutDirection.Ltr,
    )
    val surface = Surface.makeRasterN32Premul(w, h)
    return try {
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = surface.canvas.asComposeCanvas(),
            size = Size(w.toFloat(), h.toFloat()),
        ) {
            drawPageContent(
                page,
                pxPerPt = scale * density.density,
                textMeasurer = measurer,
                backdrop = backdrop,
                hiddenLayers = hiddenLayers,
            )
        }
        surface.makeImageSnapshot().toComposeImageBitmap()
    } finally {
        surface.close()
    }
}
