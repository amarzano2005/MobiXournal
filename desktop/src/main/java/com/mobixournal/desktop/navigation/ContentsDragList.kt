package com.mobixournal.desktop.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.key.*
import com.mobixournal.render.OutlineSiblingOrder
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.navigation.PageDragHandle
import com.mobixournal.desktop.navigation.PageDragMetrics
import com.mobixournal.render.OutlineDragOrder
import com.mobixournal.render.OutlineRow

/** Same row highlight, insertion rule and edge scrolling as Pages; one gesture survives recycling. */
@Composable
internal fun ContentsDragList(
    all: List<OutlineRow>, shown: List<OutlineRow>, list: LazyListState,
    enabled: Boolean, onMove: (String, Int) -> Unit,
    content: @Composable (Modifier, (OutlineRow) -> Modifier, Boolean) -> Unit,
) {
    val moves = remember(all) { OutlineSiblingOrder.of(all) }
    val order = remember(all) { OutlineDragOrder(all) }
    val drag = remember(list) { PageDragHandle(list) }
    val move by rememberUpdatedState(onMove)
    val edge = with(LocalDensity.current) { PageDragMetrics.EDGE.toPx() }
    val step = with(LocalDensity.current) { PageDragMetrics.MAX_STEP.toPx() }
    val from = shown.getOrNull(drag.from)
    val landing = if (drag.active && from != null) order.move(from.id,
        shown.getOrNull(drag.target)?.id ?: from.id) else null
    LaunchedEffect(drag.active) {
        if (!drag.active) return@LaunchedEffect
        while (drag.active) {
            withFrameNanos { }
            val scroll = drag.autoScroll(edge, step)
            if (scroll != 0f) { list.dispatchRawDelta(scroll); drag.moveBy(0f) }
        }
    }
    DisposableEffect(shown, enabled) { onDispose { drag.cancel() } }
    Box(Modifier.fillMaxSize()) {
        content(Modifier.fillMaxSize().pointerInput(shown, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
                    down.position.y >= it.offset && down.position.y < it.offset + it.size
                } ?: return@awaitEachGesture
                if (shown.getOrNull(item.index)?.id?.startsWith("bkm_") != true) return@awaitEachGesture
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (drag.active) {
                                val released = !change.isConsumed
                                change.consume()
                                if (released) drag.drop()?.let { (from, to) ->
                                    order.move(shown[from].id, shown[to].id)?.let { move(it.id, it.delta) }
                                }
                            }
                            break
                        }
                        if (!drag.active && kotlin.math.abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                            drag.start(item.index, down.position.y - item.offset)
                            drag.moveBy(change.position.y - down.position.y)
                        } else if (drag.active) drag.moveBy(change.positionChange().y)
                        if (drag.active) change.consume()
                    }
                } finally { drag.cancel() }
            }
        }.drawWithContent {
            drawContent()
            val at = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == landing?.targetId }
            if (at != null) drawLine(DesktopTheme.accent, Offset(0f, at.offset.toFloat()),
                Offset(size.width, at.offset.toFloat()), 2.dp.toPx())
        }, { row ->
            val lifted = if (drag.active && shown.getOrNull(drag.from)?.id == row.id)
                Modifier.background(DesktopTheme.activeSlot) else Modifier
            if (!row.id.startsWith("bkm_")) lifted else lifted.semantics {
                customActions = buildList {
                    if (moves[row.id]?.up == true) add(CustomAccessibilityAction("Move up") { move(row.id, -1); true })
                    if (moves[row.id]?.down == true) add(CustomAccessibilityAction("Move down") { move(row.id, 1); true })
                }
            }.onPreviewKeyEvent { event ->
                val delta = when (event.key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> 0 }
                if (event.type == KeyEventType.KeyDown && event.isAltPressed && delta != 0) {
                    move(row.id, delta); true
                } else false
            }
        }, drag.active)
    }
}
