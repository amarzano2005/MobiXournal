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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
 */
@Composable
fun PageSidebar(
    tab: DesktopTab,
    cache: MutableMap<Int, ImageBitmap>,
    onSelectPage: (Int) -> Unit,
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
            fontFamily = DesktopTheme.inter,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
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
                // rather than leaving a preview that disagrees with the sheet beside it.
                val hidden = tab.hiddenOn(index)
                val preview = remember(tab, index, hidden) {
                    thumbnail(tab, index, THUMB_WIDTH.value, cache)
                }
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
                        fontFamily = DesktopTheme.inter,
                        fontSize = 11.sp,
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
    val page = remember(tab, tab.page) { runCatching { tab.document.page(tab.page) }.getOrNull() }
    val layers = page?.layers.orEmpty()
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            text = "Layers",
            color = DesktopTheme.inkSecondary,
            fontFamily = DesktopTheme.inter,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        if (layers.isEmpty()) {
            Text("No layers", color = DesktopTheme.inkMuted, fontFamily = DesktopTheme.inter, fontSize = 11.sp)
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
                    fontFamily = DesktopTheme.inter,
                    fontSize = 11.sp,
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
 * The page's thumbnail: rendered on first sight, then kept.
 *
 * Building an [ImageComposeScene] is what makes this a *picture* of the page rather than a second
 * painter — the same `PageView` draws it, so a thumbnail cannot disagree with the page it previews.
 * (It is also why thumbnails are cached: a scene per page per scroll would be far more expensive than
 * the page render it replaces.)
 */
@OptIn(ExperimentalComposeUiApi::class)
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

/** Renders [page] at [scale] into a bitmap — thumbnails today, page-list previews tomorrow. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun renderPageBitmap(
    page: Page,
    scale: Float,
    density: Density = Density(1f),
    hiddenLayers: Set<Int> = emptySet(),
): ImageBitmap {
    val w = (page.width * scale).roundToInt().coerceAtLeast(1)
    val h = (page.height * scale).roundToInt().coerceAtLeast(1)
    val scene = ImageComposeScene(w, h, density) { PageView(page, scale, hiddenLayers = hiddenLayers) }
    return try {
        scene.render().toComposeImageBitmap()
    } finally {
        scene.close()
    }
}
