package com.mobixournal.desktop.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.WindowKeys

/** Window-owned geometry: changing panel width never edits a document or its undo history. */
internal class InspectorSizing {
    var width by mutableStateOf(INSPECTOR_WIDTH)
        private set
    fun resize(delta: Dp) { width = (width - delta).coerceIn(320.dp, 520.dp) }
    fun reset() { width = INSPECTOR_WIDTH }
    fun effective(available: Dp): Dp = minOf(width, (available * 0.55f).coerceAtLeast(0.dp))
}

@Composable internal fun InspectorFrame(sizing: InspectorSizing, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        Row(Modifier.width(sizing.effective(maxWidth)).fillMaxHeight()) {
            InspectorResizeHandle(sizing)
            Column(Modifier.weight(1f).fillMaxHeight().bandSurface(fill = DesktopTheme.settingsPanel), content = content)
        }
    }
}

@Composable private fun InspectorResizeHandle(sizing: InspectorSizing) {
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val focusOwner = remember { Any() }
    DisposableEffect(focusOwner) { onDispose { WindowKeys.focusControl(focusOwner, false) } }
    Box(Modifier.width(6.dp).fillMaxHeight()
        .hoverFill(DesktopTheme.railSlotShape)
        .drawBehind {
            drawLine(if (focused || dragging) DesktopTheme.accent else DesktopTheme.hairline,
                Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height),
                strokeWidth = if (focused || dragging) 2.dp.toPx() else 1.dp.toPx())
        }
        .semantics { contentDescription = "Resize inspector. Left and Right adjust width; Home resets." }
        .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR)))
        .onFocusChanged { focused = it.isFocused; WindowKeys.focusControl(focusOwner, focused) }
        .onPreviewKeyEvent {
            if (it.type != KeyEventType.KeyDown) false else when (it.key) {
                Key.DirectionLeft -> { sizing.resize((-16).dp); true }
                Key.DirectionRight -> { sizing.resize(16.dp); true }
                Key.Home, Key.MoveHome -> { sizing.reset(); true }
                else -> false
            }
        }.focusable()
        .pointerInput(sizing, density) {
            detectHorizontalDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false },
                onDragCancel = { dragging = false }) { change, delta ->
                change.consume()
                sizing.resize(with(density) { delta.toDp() })
            }
        })
}
