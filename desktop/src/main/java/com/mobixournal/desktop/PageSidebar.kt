package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val current = tab.page

    // Selecting a page elsewhere (arrow keys, status bar) scrolls the sidebar to it.
    LaunchedEffect(tab, current) {
        if (current in 0 until tab.pageCount) listState.animateScrollToItem(current)
    }

    Column(
        modifier
            .width(SIDEBAR_WIDTH)
            .fillMaxHeight()
            .bandSurface()
            // One edge, one pixel: the sidebar's own boundary with the canvas, no rim all round.
            .drawBehind {
                drawLine(
                    color = DesktopTheme.hairline,
                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = "Pages",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.eyebrow,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count = tab.pageCount) { index ->
                // The hidden set is part of the key: switching a layer off repaints this page's thumbnail
                // rather than leaving a preview that disagrees with the sheet beside it. So is the
                // shell's **deferred** revision, and deliberately not the document's edit count: an edit
                // drops the page's cached bitmap, and the next quiet moment re-renders that one page here
                // (the other rows hit the cache) instead of re-rendering it in the middle of a stroke.
                val hidden = tab.hiddenOn(index)
                val preview = rememberThumbnail(tab, index, hidden, revision, cache)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (index == current) DesktopTheme.accent.copy(alpha = 0.10f) else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .hoverFill(RoundedCornerShape(8.dp), active = index == current)
                        .border(
                            1.dp,
                            if (index == current) DesktopTheme.accent else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(6.dp)
                        .clickable { onSelectPage(index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (index == current) DesktopTheme.accent else DesktopTheme.inkMuted,
                        style = DesktopTheme.numeric,
                        modifier = Modifier.width(22.dp),
                    )
                    if (preview == null) {
                        Box(Modifier.width(THUMB_WIDTH).height(72.dp).background(DesktopTheme.activeSlot))
                    } else {
                        val aspect = preview.height.toFloat() / preview.width.toFloat()
                        Image(
                            bitmap = preview,
                            contentDescription = "Page ${index + 1}",
                            modifier = Modifier.width(THUMB_WIDTH).height((THUMB_WIDTH.value * aspect).dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LayersSection(tab)
    }
}

/** The current page's layers, topmost first — the order the eye reads a stack of ink in. */
@Composable
private fun LayersSection(tab: DesktopTab) {
    val page = remember(tab, tab.page, tab.document.edits) {
        runCatching { tab.document.page(tab.page) }.getOrNull()
    }
    val layers = page?.layers.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            text = "Layers",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.eyebrow,
        )
        Spacer(Modifier.height(4.dp))
        if (layers.isEmpty()) {
            Text("No layers", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        for (index in layers.indices.reversed()) {
            val hidden = tab.isLayerHidden(tab.page, index)
            Row(
                Modifier
                    .fillMaxWidth()
                    .hoverFill(RoundedCornerShape(8.dp))
                    .clickable { tab.toggleLayer(tab.page, index) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayerEye(hidden = hidden)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = layers[index].label(index),
                    // A hidden layer stays listed, in the muted ink: it is switched off, not gone.
                    color = if (hidden) DesktopTheme.inkMuted else DesktopTheme.ink,
                    style = DesktopTheme.label,
                )
            }
        }
    }
}

/**
 * The eye beside a layer's name: open when the layer is painted, struck through when it is not.
 *
 * Monoline like every other glyph in the window, and drawn rather than shipped as an icon: the chrome
 * has one stroke weight and one ink, and an image would be a second material in a 16 dp square.
 */
@Composable
private fun LayerEye(hidden: Boolean) {
    val ink = if (hidden) DesktopTheme.inkMuted else DesktopTheme.ink
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.2.dp.toPx()
        val lid = Path().apply {
            moveTo(w * 0.08f, h * 0.5f)
            cubicTo(w * 0.28f, h * 0.3f, w * 0.72f, h * 0.3f, w * 0.92f, h * 0.5f)
            cubicTo(w * 0.72f, h * 0.7f, w * 0.28f, h * 0.7f, w * 0.08f, h * 0.5f)
        }
        drawPath(lid, ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(
            color = ink,
            radius = w * 0.1f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke),
        )
        if (hidden) {
            drawLine(
                ink,
                Offset(w * 0.14f, h * 0.86f),
                Offset(w * 0.86f, h * 0.14f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
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
 * One row's thumbnail, painted **off the UI thread** and handed over when it is ready.
 *
 * A thumbnail is a full software paint of the page — 29–39 ms on a dense sheet, measured — and it used to
 * run inside composition, so every quiet moment in a burst of writing or erasing paid for one on the frame
 * the window was drawing. The render now runs on [THUMBNAIL_PAINT], one at a time, and only the finished
 * bitmap reaches the UI thread; the row keeps the picture it already had until then, so a refresh never
 * blanks the sidebar.
 *
 * The slot is keyed on the **page**, not on [revision]: that is what keeps the old picture up across a
 * refresh. The effect re-runs on every key change (the hidden layers, the shell's deferred revision) and
 * only publishes a bitmap it actually got.
 */
@Composable
private fun rememberThumbnail(
    tab: DesktopTab,
    index: Int,
    hidden: Set<Int>,
    revision: Int,
    cache: MutableMap<Int, ImageBitmap>,
): ImageBitmap? {
    val slot = remember(tab, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(tab, index, hidden, revision) {
        val painted = withContext(THUMBNAIL_PAINT) { thumbnail(tab, index, THUMB_WIDTH.value, cache) }
        if (painted != null) slot.value = painted
    }
    return slot.value
}

/**
 * The thread thumbnails are painted on: the pool's own, narrowed to **one** lane.
 *
 * One, because a page's paint builds a scene and walks the page it shares with the document; a single lane
 * keeps that walk serial — never the UI thread's, and never two pages' at once.
 */
private val THUMBNAIL_PAINT = Dispatchers.Default.limitedParallelism(1)

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
    val bitmap = runCatching { renderPageBitmap(page, scale, hiddenLayers = hidden) }.getOrNull() ?: return null
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
                hiddenLayers = hiddenLayers,
            )
        }
        surface.makeImageSnapshot().toComposeImageBitmap()
    } finally {
        surface.close()
    }
}
