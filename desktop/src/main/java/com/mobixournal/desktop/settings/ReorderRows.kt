package com.mobixournal.desktop.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.desktop.tools.hoverFill
import kotlin.math.abs

/** How far a carried row sits off the page, the same lift the page list's ghost wears. */
private val DRAG_LIFT = 12.dp

/** The landing line's thickness: the accent that says where a dragged row will go. */
private val LANDING_LINE = 2.dp

/** How long the rows between take to open the gap the carried row will land in. */
private const val GLIDE_MS = 160

/**
 * Where the row picked up at [from] lands after [offsetPx] of travel: the place whose **own** slot is
 * nearest to where the carried row now sits, with the ends stopping rather than wrapping.
 *
 * A settings list is not one pitch. A row with a name alone is 40 dp; a row with a name and a sentence is
 * taller, and a group can hold both — the strip's slot rows and its *Insert image* row, the palette and its
 * commands. Reading every row against the first row's height made a drag land a place late under tall rows
 * and a place early under short ones, so each row contributes [heights]`[index]`, and a row that has not been
 * measured yet falls back to [fallbackPx] (the list's first pitch).
 *
 * The rule is the page sidebar's ([com.mobixournal.desktop.navigation.PageReorder]) generalised: the lifted
 * row's top is placed among the rows' tops, and the nearest one wins — ties to the later row, so a drag of
 * exactly half a row already counts as crossed. Over a uniform list it is `from + round(offset / rowHeight)`
 * exactly, which is what [stripDropTarget]'s one-pitch form has always been.
 */
internal fun stripDropTarget(
    from: Int,
    offsetPx: Float,
    heights: List<Int>,
    fallbackPx: Int,
    count: Int,
): Int {
    if (from !in 0 until count) return from
    val fallback = fallbackPx.takeIf { it > 0 } ?: heights.firstOrNull { it > 0 } ?: return from
    fun height(index: Int): Int = heights.getOrNull(index)?.takeIf { it > 0 } ?: fallback
    var start = 0f
    for (index in 0 until from) start += height(index)
    val lifted = start + offsetPx
    var best = from
    var bestDistance = Float.MAX_VALUE
    var top = 0f
    for (index in 0 until count) {
        val distance = abs(lifted - top)
        if (distance <= bestDistance) {
            bestDistance = distance
            best = index
        }
        top += height(index)
    }
    return best
}

/** The same question over a list of one uniform height — the shape a strip of chips has. */
internal fun stripDropTarget(from: Int, offsetPx: Float, rowHeightPx: Int, count: Int): Int =
    stripDropTarget(from, offsetPx, emptyList(), rowHeightPx, count)

/**
 * One settings list being arranged: the row in flight, how far the pointer has carried it, and the
 * pitch a drop lands on.
 *
 * The gesture lives in the grip ([DragGrip]); what a row *looks like* while it travels is
 * [ReorderRow]'s, and the two decisions a drag delegates — where it lands, and whether that landing
 * is a move at all — are here, so they are pure enough to test without a window.
 */
internal class ReorderState {

    /** The row being carried, or -1 when nothing is in flight. */
    var from by mutableStateOf(-1)
        private set

    /** How far the pointer has travelled, in px, sign included. */
    var offset by mutableStateOf(0f)
        private set

    /** The list's first measured row, in px — the pitch an unmeasured row falls back to. */
    var rowHeightPx by mutableStateOf(0)
        private set

    /** Every row's own height, in px: a row with a note is taller than one without. */
    private val heights = mutableStateMapOf<Int, Int>()

    /** Begin carrying [index]. */
    fun start(index: Int) {
        from = index
        offset = 0f
    }

    /** Add one pointer event's worth of travel. */
    fun by(delta: Float) {
        if (from >= 0) offset += delta
    }

    /** Let go: the row in flight stays where it is until the caller writes the move. */
    fun clear() {
        from = -1
        offset = 0f
    }

    /** The row the carried one would land on right now. */
    fun landing(count: Int): Int =
        stripDropTarget(from, offset, List(count) { heights[it] ?: 0 }, rowHeightPx, count)

    /**
     * The place the carried row lands, clearing the gesture — or null when nothing moved, so a drop
     * where it started writes nothing to the settings.
     */
    fun drop(count: Int): Int? {
        val to = landing(count)
        val moved = if (from >= 0 && to != from) to else null
        clear()
        return moved
    }

    /** Remember row [index]'s own height, and the list's first pitch as the fallback. */
    internal fun measured(index: Int, px: Int) {
        if (px <= 0) return
        heights[index] = px
        if (rowHeightPx == 0) rowHeightPx = px
    }

    /**
     * How far row [index] shifts while the gap opens: the rows between the carried row and its
     * landing move one pitch toward the hole it left, so the gap is exactly where the row will go.
     */
    fun shift(index: Int, count: Int): Float {
        if (from < 0 || rowHeightPx <= 0) return 0f
        val landing = landing(count)
        // The rows between move by the carried row's own height: the hole it leaves is its size, whatever
        // the rows it passes are.
        val carried = (heights[from] ?: rowHeightPx).toFloat()
        return when {
            index == from -> 0f
            from < landing && index in (from + 1)..landing -> -carried
            landing < from && index in landing until from -> carried
            else -> 0f
        }
    }
}

/**
 * One row of a drag-arranged list: the carried row follows the pointer on its shadow, the rows
 * between glide open on a short tween, and the row a drop would land on wears the accent along its
 * top edge — the page list's own drag, at one measured row's pitch.
 *
 * [count] is the list's length, read for the landing index: a settings list is a `Column`, not a lazy
 * one, so the rows know their own number.
 */
@Composable
internal fun ReorderRow(
    state: ReorderState,
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    val carried = state.from == index
    // The carried row must answer the pointer with no lag, so only the others animate.
    val glide by animateFloatAsState(
        targetValue = if (carried) 0f else state.shift(index, count),
        animationSpec = tween(GLIDE_MS),
        label = "reorder row",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(if (carried) 1f else 0f)
            .graphicsLayer {
                translationY = if (carried) state.offset else glide
                shadowElevation = if (carried) DRAG_LIFT.toPx() else 0f
            }
            .background(if (carried) DesktopTheme.activeSlot else Color.Transparent)
            .drawBehind {
                if (state.from >= 0 && !carried && state.landing(count) == index) {
                    drawRect(DesktopTheme.accent, size = Size(size.width, LANDING_LINE.toPx()))
                }
            }
            .onSizeChanged { state.measured(index, it.height) },
    ) {
        content()
    }
}

/**
 * The grip a row is dragged by: the catalogue's own handle, in a control-sized hit area with the
 * window's hover step and a spoken name.
 *
 * It is a **dedicated target** rather than a long press anywhere on the row, exactly as the page
 * list's handle is: a row that starts a drag on any press is a row a reader cannot click. It also
 * holds the list's **keyboard** path — focused, an arrow key steps the row one place through
 * [onStep] — so a list that no longer draws a pair of chevrons is still arrangeable without a mouse.
 */
@Composable
internal fun DragGrip(
    label: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onStep: (Int) -> Unit,
) {
    val start = rememberUpdatedState(onDragStart)
    val move = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onDragEnd)
    val cancel = rememberUpdatedState(onDragCancel)
    val step = rememberUpdatedState(onStep)
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(SettingsMetrics.CONTROL_HEIGHT)
            .background(Color.Transparent, PropertyControlShape)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = PropertyControlShape,
            )
            .hoverFill(PropertyControlShape)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionLeft -> {
                        step.value(-1); true
                    }
                    Key.DirectionDown, Key.DirectionRight -> {
                        step.value(1); true
                    }
                    else -> false
                }
            }
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
