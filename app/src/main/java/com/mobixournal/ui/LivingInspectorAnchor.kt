package com.mobixournal.ui

import android.graphics.RectF
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.LocalFloatingSurfaceClosing
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import kotlin.math.roundToInt

/**
 * Returns [canvasSize] reduced by whichever bottom keyboard inset (system IME or a docked
 * custom keyboard) is taller, so an anchored inspector always lays out and clamps above it.
 */
internal fun effectiveInspectorCanvasSize(
    canvasSize: IntSize,
    imeBottomPx: Int,
    customKeyboardHeightPx: Int,
): IntSize {
    val keyboardInset = maxOf(imeBottomPx.coerceAtLeast(0), customKeyboardHeightPx.coerceAtLeast(0))
    return IntSize(canvasSize.width, (canvasSize.height - keyboardInset).coerceAtLeast(0))
}

/**
 * Pure positioning helper for an anchored living-element inspector: prefers the right side of the
 * selection, falls back to the left, and clamps inside [canvasSize].
 */
internal fun anchoredInspectorOffset(
    selectionLeft: Float?,
    selectionTop: Float?,
    selectionRight: Float?,
    selectionBottom: Float?,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): IntOffset {
    if (selectionLeft == null || selectionRight == null || selectionTop == null || inspectorSize.width <= 0) {
        val cx = ((canvasSize.width - inspectorSize.width) / 2).coerceAtLeast(marginPx)
        val cy = ((canvasSize.height - inspectorSize.height) / 2).coerceAtLeast(marginPx)
        return IntOffset(cx, cy)
    }

    val preferredRightX = (selectionRight + gapPx).toInt()
    val fitsRight = preferredRightX + inspectorSize.width <= canvasSize.width - marginPx

    val preferredLeftX = (selectionLeft - gapPx - inspectorSize.width).toInt()
    val fitsLeft = preferredLeftX >= marginPx

    val maxX = (canvasSize.width - inspectorSize.width - marginPx).coerceAtLeast(marginPx)
    val maxY = (canvasSize.height - inspectorSize.height - marginPx).coerceAtLeast(marginPx)

    if (fitsRight || fitsLeft) {
        val x = if (fitsRight) preferredRightX else preferredLeftX
        val alignedY = if (selectionBottom != null) {
            ((selectionTop + selectionBottom) / 2f - inspectorSize.height / 2f).toInt()
        } else {
            selectionTop.toInt()
        }
        val y = alignedY.coerceIn(marginPx, maxY)
        return IntOffset(x, y)
    }

    if (selectionBottom != null && inspectorSize.height > 0) {
        val preferredBelowY = (selectionBottom + gapPx).toInt()
        val fitsBelow = preferredBelowY + inspectorSize.height <= canvasSize.height - marginPx
        val preferredAboveY = (selectionTop - gapPx - inspectorSize.height).toInt()
        val fitsAbove = preferredAboveY >= marginPx
        if (fitsBelow || fitsAbove) {
            val x = ((selectionLeft + selectionRight) / 2f - inspectorSize.width / 2f).toInt().coerceIn(marginPx, maxX)
            val y = if (fitsBelow) preferredBelowY else preferredAboveY
            return IntOffset(x, y)
        }
    }

    val spaceRight = canvasSize.width - selectionRight
    val spaceLeft = selectionLeft
    val x = if (spaceRight >= spaceLeft) maxX else marginPx
    val alignedY = if (selectionBottom != null) {
        ((selectionTop + selectionBottom) / 2f - inspectorSize.height / 2f).toInt()
    } else {
        selectionTop.toInt()
    }
    val y = alignedY.coerceIn(marginPx, maxY)
    return IntOffset(x, y)
}

/**
 * Where a **freely dragged** inspector goes: [start] moved by [delta], brought back inside [canvasSize].
 *
 * A drag works on the panel's **absolute** position, never on a delta re-measured from the anchor
 * every frame, so a panel already against an edge keeps moving when dragged away from it.
 */
internal fun draggedInspectorOffset(
    start: IntOffset,
    delta: Offset,
    inspectorSize: IntSize,
    canvasSize: IntSize,
): IntOffset {
    val maxX = (canvasSize.width - inspectorSize.width).coerceAtLeast(0)
    val maxY = (canvasSize.height - inspectorSize.height).coerceAtLeast(0)
    return IntOffset(
        (start.x + delta.x.roundToInt()).coerceIn(0, maxX),
        (start.y + delta.y.roundToInt()).coerceIn(0, maxY),
    )
}

/**
 * Clamps a placed [offset] into [canvasSize] without mutating the underlying placement, so when
 * an IME or custom keyboard shrinks the available height the panel lifts above it and returns
 * when the keyboard closes.
 */
internal fun clampInspectorToCanvas(
    offset: IntOffset,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    marginPx: Int = 0,
): IntOffset {
    val maxX = (canvasSize.width - inspectorSize.width - marginPx).coerceAtLeast(marginPx)
    val maxY = (canvasSize.height - inspectorSize.height - marginPx).coerceAtLeast(marginPx)
    return IntOffset(
        offset.x.coerceIn(marginPx, maxX),
        offset.y.coerceIn(marginPx, maxY),
    )
}

/**
 * Shared floating container for **anchored** living-element inspectors (such as plot and circuit).
 *
 * Anchors once per [sessionKey] beside [anchorRect], supports free absolute dragging, and
 * automatically clamps its position and max height above both the system IME (`WindowInsets.ime`)
 * and any docked custom keyboard ([keyboardHeightPx]).
 */
@Composable
internal fun LivingInspectorAnchor(
    sessionKey: Any?,
    anchorRect: RectF?,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    minWidth: Dp = 240.dp,
    maxWidth: Dp = 360.dp,
    keyboardHeightPx: Int = 0,
    gapDp: Dp = 12.dp,
    marginDp: Dp = 8.dp,
    animateSurface: Boolean = false,
    content: @Composable (dragModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { gapDp.roundToPx() }
    val marginPx = with(density) { marginDp.roundToPx() }
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val effectiveCanvasSize = effectiveInspectorCanvasSize(
        canvasSize = canvasSizePx,
        imeBottomPx = imeBottomPx,
        customKeyboardHeightPx = keyboardHeightPx,
    )
    val maxPanelHeight = with(density) { (effectiveCanvasSize.height - 2 * marginPx).coerceAtLeast(0).toDp() }
    val maxPanelWidth = with(density) { (effectiveCanvasSize.width - 2 * marginPx).coerceAtLeast(0).toDp() }
    val resolvedMaxWidth = minOf(maxWidth, maxPanelWidth)
    val resolvedMinWidth = minOf(minWidth, resolvedMaxWidth)

    val rememberedAnchor = remember(sessionKey) { anchorRect }
    var inspectorSize by remember(sessionKey) { mutableStateOf(IntSize.Zero) }
    var placement by remember(sessionKey) { mutableStateOf<IntOffset?>(null) }
    var userDragged by remember(sessionKey) { mutableStateOf(false) }

    val displayOffset = placement?.let {
        clampInspectorToCanvas(it, inspectorSize, effectiveCanvasSize, marginPx)
    }

    val latestCanvasSize by rememberUpdatedState(effectiveCanvasSize)
    val latestInspectorSize by rememberUpdatedState(inspectorSize)
    val latestMarginPx by rememberUpdatedState(marginPx)

    val dragModifier = Modifier.pointerInput(sessionKey) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            val stored = placement ?: return@detectDragGestures
            userDragged = true
            val clamped = clampInspectorToCanvas(
                offset = stored,
                inspectorSize = latestInspectorSize,
                canvasSize = latestCanvasSize,
                marginPx = latestMarginPx,
            )
            placement = draggedInspectorOffset(
                start = clamped,
                delta = dragAmount,
                inspectorSize = latestInspectorSize,
                canvasSize = latestCanvasSize,
            )
        }
    }

    FloatingSurface(
        modifier = modifier
            .alpha(if (displayOffset == null) 0f else 1f)
            .offset { displayOffset ?: IntOffset.Zero }
            .onSizeChanged { size ->
                inspectorSize = size
                if (placement == null || !userDragged) {
                    placement = anchoredInspectorOffset(
                        selectionLeft = rememberedAnchor?.left,
                        selectionTop = rememberedAnchor?.top,
                        selectionRight = rememberedAnchor?.right,
                        selectionBottom = rememberedAnchor?.bottom,
                        inspectorSize = size,
                        canvasSize = effectiveCanvasSize,
                        gapPx = gapPx,
                        marginPx = marginPx,
                    )
                }
            }
            .widthIn(min = resolvedMinWidth, max = resolvedMaxWidth)
            .heightIn(max = maxPanelHeight)
            .padding(4.dp)
            .then(
                if (animateSurface) {
                    Modifier.liquidGlassSpringAnimation(
                        expanded = !LocalFloatingSurfaceClosing.current,
                        origin = TransformOrigin(0.5f, 0.5f),
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        content(dragModifier)
    }
}
