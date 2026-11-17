package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.OutlineDragOrder
import com.mobixournal.render.OutlineRow
import kotlin.math.roundToInt

/** One list-owned gesture survives recycling; geometry comes from measured variable-height rows. */
@Composable
internal fun BookmarkDragList(
    all: List<OutlineRow>, shown: List<OutlineRow>, list: LazyListState,
    enabled: Boolean, onMove: (String, Int) -> Unit,
    content: @Composable (Modifier, Boolean) -> Unit,
) {
    val order = remember(all) { OutlineDragOrder(all) }
    var carried by remember { mutableStateOf<OutlineRow?>(null) }
    var y by remember { mutableFloatStateOf(0f) }
    var grab by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    fun target(): OutlineDragOrder.Move? {
        val info = list.layoutInfo.visibleItemsInfo
        val over = info.firstOrNull { y < it.offset + it.size } ?: info.lastOrNull()
        return carried?.let { from -> order.move(from.id, over?.key as? String ?: return null) }
    }
    val landing = target()
    BookmarkAutoScroll(carried?.id, list, { y - grab }, { height })
    Box(Modifier.fillMaxSize()) {
        val accent = MaterialTheme.colorScheme.primary
        content(Modifier.fillMaxSize().pointerInput(shown, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Initial)
                if (down.position.x < size.width - DRAG_HANDLE_SIZE.toPx()) return@awaitEachGesture
                val info = list.layoutInfo.visibleItemsInfo.firstOrNull {
                    down.position.y >= it.offset && down.position.y < it.offset + it.size
                } ?: return@awaitEachGesture
                val row = shown.firstOrNull { it.id == info.key && it.id.startsWith("bkm_") }
                    ?: return@awaitEachGesture
                down.consume()
                carried = row; y = down.position.y; grab = y - info.offset; height = info.size
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        y = change.position.y
                        if (!change.pressed) {
                            if (!change.isConsumed) target()?.let { move(it.id, it.delta) }
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                } finally { carried = null }
            }
        }.drawWithContent {
            drawContent()
            val at = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == landing?.targetId }
            if (at != null) drawLine(accent, Offset(0f, at.offset.toFloat()),
                Offset(size.width, at.offset.toFloat()), 2.dp.toPx())
        }, carried != null)
        carried?.let { row ->
            Row(Modifier.fillMaxWidth().height(with(density) { height.toDp() })
                .offset { IntOffset(0, (y - grab).roundToInt()) }
                .graphicsLayer { shadowElevation = DRAG_GHOST_SHADOW.toPx() }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .94f))
                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, modifier = Modifier.weight(1f), maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium)
                row.page?.let { Text("${it + 1}", style = MaterialTheme.typography.labelMedium) }
                Icon(ChromeGlyph.DRAG_HANDLE.imageVector(), null, Modifier.size(DRAG_HANDLE_SIZE))
            }
        }
    }
}

/** Accessibility offers the same move without reintroducing commands in the overflow menu. */
@Composable
internal fun BookmarkDragHandle(row: OutlineRow, actions: List<CustomAccessibilityAction>) {
    if (!row.id.startsWith("bkm_")) { Spacer(Modifier.size(DRAG_HANDLE_SIZE)); return }
    Box(Modifier.size(DRAG_HANDLE_SIZE).semantics(mergeDescendants = true) {
        contentDescription = "Reorder ${row.title}"
        customActions = actions
    }, contentAlignment = Alignment.Center) {
        Icon(ChromeGlyph.DRAG_HANDLE.imageVector(), null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** The drag owns scrolling while touch scrolling is disabled; one measured delta per frame. */
@Composable
private fun BookmarkAutoScroll(id: String?, list: LazyListState, top: () -> Float, height: () -> Int) {
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val speed = with(LocalDensity.current) { AUTO_SCROLL_SPEED.toPx() }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / 1e9f).coerceIn(0f, .05f)
            last = now
            val step = autoScrollStep(top(), height().toFloat(), list.layoutInfo.viewportSize.height, edge, speed)
            if (step != 0f) list.dispatchRawDelta(step * seconds)
        }
    }
}
