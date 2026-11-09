package com.mobixournal.desktop.settings
import com.mobixournal.desktop.tools.StripItem
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.tools.stripItems

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobixournal.design.ChromeGlyph
import kotlin.math.roundToInt

/** How far a carried row sits off the page, the same lift the page list's ghost wears. */
private val DRAG_LIFT = 12.dp

/** The landing line's thickness: the accent that says where a dragged slot will go. */
private val LANDING_LINE = 2.dp

/**
 * Where the slot picked up at [from] lands after [offsetPx] of travel: one place per measured row
 * crossed, rounded, with the ends stopping rather than wrapping.
 *
 * The page sidebar's own arithmetic ([com.mobixournal.desktop.sheet.PageReorder]) generalises to any
 * pitch; this is the version a settings row list can use, where every row is the height of the first
 * one measured. An unmeasured row, no drag, or an index past the end answers [from].
 */
internal fun stripDropTarget(from: Int, offsetPx: Float, rowHeightPx: Int, count: Int): Int {
    if (from !in 0 until count || rowHeightPx <= 0) return from
    return (from + (offsetPx / rowHeightPx).roundToInt()).coerceIn(0, count - 1)
}

/**
 * The **Buttons** group's rows, each tool slot carrying a drag handle as well as its two chevrons.
 *
 * The drag is the page list's, in miniature: the carried row is lifted over the list on its shadow,
 * follows the pointer, and the row it would land on wears the accent along its top edge. A drop
 * commits the whole arrangement through [StripOrderState.moveSlotTo], so both strips and the next
 * launch read exactly what the list showed.
 *
 * The chevrons stay for the same reason they were written: this page is read with the keyboard as
 * often as with a mouse, and a list that only answered a drag would be a list half the window could
 * not use. Insert image keeps its own pair — its place is an anchor under a slot, not an index in the
 * order — so only the tool slots travel here.
 */
@Composable
internal fun ToolbarOrderRows(policy: DesktopSettingsState) {
    val groups = policy.toolGroups
    var dragFrom by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0) }
    val landing = stripDropTarget(dragFrom, dragOffset, rowHeightPx, groups.size)

    fun dropDrag() {
        val to = stripDropTarget(dragFrom, dragOffset, rowHeightPx, groups.size)
        if (dragFrom in groups.indices && to != dragFrom) policy.strips.moveSlotTo(dragFrom, to)
        dragFrom = -1
        dragOffset = 0f
    }

    fun cancelDrag() {
        dragFrom = -1
        dragOffset = 0f
    }

    // The rows are the strip's own contents in the strip's own order ([stripItems]): the list said
    // "bottom of the strip" while the strip drew Insert image under Text.
    for (item in stripItems(groups, policy.imageAfter)) when (item) {
        // The stroke's settings travel with the highlighter; they are not a button, so they get no row.
        StripItem.Stroke -> Unit
        is StripItem.Tools -> {
            val index = item.index
            val dragging = index == dragFrom
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        shadowElevation = if (dragging) DRAG_LIFT.toPx() else 0f
                    }
                    .background(if (dragging) DesktopTheme.activeSlot else Color.Transparent)
                    .drawBehind {
                        if (dragFrom >= 0 && landing == index && landing != dragFrom) {
                            drawRect(DesktopTheme.accent, size = Size(size.width, LANDING_LINE.toPx()))
                        }
                    }
                    .onSizeChanged { if (rowHeightPx == 0) rowHeightPx = it.height },
            ) {
                SettingsRow(
                    label = item.group.face.label,
                    // Only a slot holding more than one tool says what it holds: "Pen — Pen" teaches nothing.
                    note = item.group.members.takeIf { it.size > 1 }?.joinToString(" · ") { it.label }.orEmpty(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DragGrip(
                            label = item.group.face.label,
                            onDragStart = { dragFrom = index; dragOffset = 0f },
                            onDrag = { dragOffset += it },
                            onDragEnd = { dropDrag() },
                            onDragCancel = { cancelDrag() },
                        )
                        Spacer(Modifier.width(6.dp))
                        SettingsStepButtons(
                            upEnabled = index > 0,
                            downEnabled = index < groups.lastIndex,
                            onUp = { policy.moveToolSlot(index, -1) },
                            onDown = { policy.moveToolSlot(index, 1) },
                        )
                    }
                }
            }
        }
        StripItem.Image -> SettingsRow(
            label = "Insert image",
            note = "Follows the tool slot directly above it.",
        ) {
            val anchor = groups.indexOfFirst { it.face.name == policy.imageAfter }
            SettingsStepButtons(
                upEnabled = anchor > 0,
                downEnabled = anchor in 0 until groups.lastIndex,
                onUp = { policy.moveImageSlot(-1) },
                onDown = { policy.moveImageSlot(1) },
            )
        }
    }
}

/**
 * The grip a slot is dragged by: the catalogue's own handle, in a control-sized hit area with the
 * window's hover step and a spoken name.
 *
 * It is a **dedicated target** rather than a long press anywhere on the row, exactly as the page list's
 * handle is: a row that starts a drag on any press is a row a reader cannot click.
 */
@Composable
private fun DragGrip(
    label: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val start = rememberUpdatedState(onDragStart)
    val move = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onDragEnd)
    val cancel = rememberUpdatedState(onDragCancel)
    Box(
        Modifier
            .size(SettingsMetrics.CONTROL_HEIGHT)
            .background(Color.Transparent, PropertyControlShape)
            .hoverFill(PropertyControlShape)
            .semantics { contentDescription = "Drag to reorder $label" }
            .pointerInput(label) {
                detectDragGestures(
                    onDragStart = { start.value() },
                    onDrag = { change, amount ->
                        change.consume()
                        move.value(amount.y)
                    },
                    onDragEnd = { end.value() },
                    onDragCancel = { cancel.value() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawChromeGlyph(ChromeGlyph.DRAG_HANDLE, DesktopTheme.inkSecondary)
        }
    }
}
