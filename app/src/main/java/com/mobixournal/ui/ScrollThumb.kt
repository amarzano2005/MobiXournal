package com.mobixournal.ui

import com.mobixournal.render.resetZoom
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import com.mobixournal.ui.theme.rememberScrollThumbColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private val THUMB_GRAB_WIDTH = 20.dp

/**
 * The painted bar: a hairline, sat flush against the pane's edge.
 *
 * It carries no inset and no grip notch — an earlier thumb was a wider bar with a bulge in the
 * middle and a margin off the edge, which read as a control sitting *on* the page. This is the quiet
 * hint beside it: thin enough to hide under a fingernail's width, and at the edge so it never covers
 * a line of the ruling.
 */
private val THUMB_WIDTH = 2.dp
private val THUMB_MIN_HEIGHT = 32.dp
private const val FADE_MS = 1400L

@Composable
fun ScrollThumb(
    scrollY: () -> Float,
    totalHeightPx: () -> Float,
    viewportPx: () -> Float,
    currentPage: () -> Int,
    pageCount: () -> Int,
    onScrollTo: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxScroll by remember { derivedStateOf { (totalHeightPx() - viewportPx()).coerceAtLeast(0f) } }
    val density = LocalDensity.current
    val minThumbPx = with(density) { THUMB_MIN_HEIGHT.toPx() }
    val thumbHeightPx by remember(minThumbPx) {
        derivedStateOf {
            val vp = viewportPx()
            val tot = totalHeightPx()
            if (vp <= 0f || tot <= 0f) 0f else (vp * vp / tot).coerceIn(minThumbPx, vp)
        }
    }
    val travelPx by remember { derivedStateOf { (viewportPx() - thumbHeightPx).coerceAtLeast(0f) } }
    if (maxScroll <= 0f || viewportPx() <= 0f) return

    val thumbTop: () -> Float = {
        val max = maxScroll
        if (max <= 0f) 0f else (scrollY() / max).coerceIn(0f, 1f) * travelPx
    }

    var dragging by remember { mutableStateOf(false) }
    var recentlyScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var initial = true
        snapshotFlow { scrollY() to dragging }.collectLatest { (_, isDragging) ->
            if (initial) {
                initial = false
                return@collectLatest
            }
            recentlyScrolled = true
            if (!isDragging) {
                delay(FADE_MS)
                recentlyScrolled = false
            }
        }
    }

    val alpha = when {
        dragging -> 0.92f
        recentlyScrolled -> 0.58f
        else -> 0.30f
    }

    Box(modifier = modifier.fillMaxSize()) {
        ScrollThumbBand(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop().roundToInt()) },
            thumbHeightPx = thumbHeightPx,
            travelPx = travelPx,
            maxScroll = maxScroll,
            bandTop = thumbTop,
            alpha = alpha,
            onDraggingChange = { dragging = it },
            onScrollTo = onScrollTo,
        )
        if (dragging && pageCount() > 0) {
            val bubbleOffsetPx = with(density) { 16.dp.toPx() }
            PageBubble(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, (thumbTop() + thumbHeightPx / 2f - bubbleOffsetPx).roundToInt()) }
                    .padding(end = THUMB_GRAB_WIDTH + 4.dp),
                currentPage = currentPage(),
                pageCount = pageCount(),
            )
        }
    }
}

@Composable
private fun ScrollThumbBand(
    modifier: Modifier,
    thumbHeightPx: Float,
    travelPx: Float,
    maxScroll: Float,
    bandTop: () -> Float,
    alpha: Float,
    onDraggingChange: (Boolean) -> Unit,
    onScrollTo: (Float) -> Unit,
) {
    var grip by remember { mutableStateOf(0f) }

    val travel by rememberUpdatedState(travelPx)
    val max by rememberUpdatedState(maxScroll)
    val top by rememberUpdatedState(bandTop)
    val setDragging by rememberUpdatedState(onDraggingChange)

    Box(
        modifier = modifier
            .width(THUMB_GRAB_WIDTH)
            .heightPx(thumbHeightPx)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start ->
                        setDragging(true)
                        grip = start.y
                    },
                    onDragEnd = { setDragging(false) },
                    onDragCancel = { setDragging(false) },
                ) { change, _ ->
                    change.consume()
                    if (travel > 0f) {
                        val newTop = top() + change.position.y - grip
                        onScrollTo((newTop / travel * max).coerceIn(0f, max))
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        ThumbVisual(
            modifier = Modifier
                .fillMaxHeight()
                .width(THUMB_WIDTH),
            alpha = alpha,
        )
    }
}

@Composable
private fun ThumbVisual(
    modifier: Modifier,
    alpha: Float,
) {
    val thumbColor = rememberScrollThumbColor()
    Canvas(modifier = modifier) {
        // A plain rounded capsule: no grip bulge, so nothing pokes into the page's margin.
        drawRoundRect(
            color = thumbColor.copy(alpha = alpha),
            topLeft = Offset(0f, 0f),
            size = size,
            cornerRadius = CornerRadius(size.width / 2f),
        )
    }
}

@Composable
private fun PageBubble(
    modifier: Modifier,
    currentPage: Int,
    pageCount: Int,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        // The page bubble is a floating badge like the page counter: the one floating material.
        color = rememberFloatingSurfaceColor(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = pageLabel(currentPage, pageCount),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun Modifier.heightPx(px: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val h = px.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    },
)

/** Narrow viewport badge wrapper isolating zoom and page-counter reads from [EditorPaneView]. */
@Composable
internal fun PaneViewportBadges(
    state: PaneState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ZoomBadge(
            zoom = state.zoom,
            onClick = { state.surface?.resetZoom() },
        )
        PageCounter(
            currentPage = state.currentPage,
            pageCount = state.pageCount,
        )
    }
}
