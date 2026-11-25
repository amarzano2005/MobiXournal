package com.mobixournal.ui

import android.graphics.RectF
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
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

private fun overlapAreaPx(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Long {
    val ix = (minOf((x + w).toFloat(), right) - maxOf(x.toFloat(), left)).coerceAtLeast(0f)
    val iy = (minOf((y + h).toFloat(), bottom) - maxOf(y.toFloat(), top)).coerceAtLeast(0f)
    return (ix * iy).toLong()
}

/**
 * Pure positioning helper for an anchored living-element inspector: evaluates all four sides
 * (right, left, below, above) inside `[minX..maxX] x [minY..maxY]` and picks the candidate with
 * minimum overlap over the figure (preserving Right > Left > Below > Above on ties).
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
    safeLeftPx: Int = 0,
    safeTopPx: Int = 0,
    safeRightPx: Int = 0,
    safeBottomPx: Int = 0,
): IntOffset {
    val minX = safeLeftPx + marginPx
    val minY = safeTopPx + marginPx
    val maxX = (canvasSize.width - safeRightPx - inspectorSize.width - marginPx).coerceAtLeast(minX)
    val maxY = (canvasSize.height - safeBottomPx - inspectorSize.height - marginPx).coerceAtLeast(minY)

    if (selectionLeft == null || selectionRight == null || selectionTop == null || inspectorSize.width <= 0) {
        val cx = ((minX + maxX) / 2).coerceIn(minX, maxX)
        val cy = ((minY + maxY) / 2).coerceIn(minY, maxY)
        return IntOffset(cx, cy)
    }

    val bottom = selectionBottom ?: selectionTop
    val centeredY = (((selectionTop + bottom) / 2f) - inspectorSize.height / 2f).toInt().coerceIn(minY, maxY)
    val centeredX = (((selectionLeft + selectionRight) / 2f) - inspectorSize.width / 2f).toInt().coerceIn(minX, maxX)

    val candidates = listOf(
        IntOffset((selectionRight + gapPx).toInt().coerceIn(minX, maxX), centeredY),
        IntOffset((selectionLeft - gapPx - inspectorSize.width).toInt().coerceIn(minX, maxX), centeredY),
        IntOffset(centeredX, (bottom + gapPx).toInt().coerceIn(minY, maxY)),
        IntOffset(centeredX, (selectionTop - gapPx - inspectorSize.height).toInt().coerceIn(minY, maxY)),
    )
    return candidates.minByOrNull { pos ->
        overlapAreaPx(pos.x, pos.y, inspectorSize.width, inspectorSize.height, selectionLeft, selectionTop, selectionRight, bottom)
    } ?: candidates.first()
}

/**
 * Where a **freely dragged** inspector goes: [start] moved by [delta], brought back inside [canvasSize].
 */
internal fun draggedInspectorOffset(
    start: IntOffset,
    delta: Offset,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    minX: Int = 0,
    minY: Int = 0,
    safeRightPx: Int = 0,
    safeBottomPx: Int = 0,
): IntOffset {
    val maxX = (canvasSize.width - safeRightPx - inspectorSize.width).coerceAtLeast(minX)
    val maxY = (canvasSize.height - safeBottomPx - inspectorSize.height).coerceAtLeast(minY)
    return IntOffset(
        (start.x + delta.x.roundToInt()).coerceIn(minX, maxX),
        (start.y + delta.y.roundToInt()).coerceIn(minY, maxY),
    )
}

/**
 * Clamps a placed [offset] into the safe region of [canvasSize] without mutating the underlying placement.
 */
internal fun clampInspectorToCanvas(
    offset: IntOffset,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    marginPx: Int = 0,
    safeLeftPx: Int = 0,
    safeTopPx: Int = 0,
    safeRightPx: Int = 0,
    safeBottomPx: Int = 0,
): IntOffset {
    val minX = safeLeftPx + marginPx
    val minY = safeTopPx + marginPx
    val maxX = (canvasSize.width - safeRightPx - inspectorSize.width - marginPx).coerceAtLeast(minX)
    val maxY = (canvasSize.height - safeBottomPx - inspectorSize.height - marginPx).coerceAtLeast(minY)
    return IntOffset(offset.x.coerceIn(minX, maxX), offset.y.coerceIn(minY, maxY))
}

/**
 * Shared floating container for **anchored** living-element inspectors (such as plot, shape and circuit).
 *
 * Keeps the canvas's own `(0, 0)` coordinate space so [anchorRect] matches directly, while clamping
 * strictly inside [safeInsets] (below the tab strip / top bar and clear of the rail) and above any
 * docked keyboard. Plays [DesignTokens.GlassMotion.SurfaceRole.POPOVER] in place on arrival/departure.
 */
@Composable
internal fun LivingInspectorAnchor(
    sessionKey: Any?,
    anchorRect: RectF?,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    safeInsets: PaddingValues = PaddingValues(0.dp),
    minWidth: Dp = 240.dp,
    maxWidth: Dp = 360.dp,
    keyboardHeightPx: Int = 0,
    gapDp: Dp = 12.dp,
    marginDp: Dp = 8.dp,
    @Suppress("UNUSED_PARAMETER") animateSurface: Boolean = true,
    content: @Composable (dragModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val gapPx = with(density) { gapDp.roundToPx() }
    val marginPx = with(density) { marginDp.roundToPx() }
    val safeL = with(density) { safeInsets.calculateStartPadding(dir).roundToPx() }
    val safeT = with(density) { safeInsets.calculateTopPadding().roundToPx() }
    val safeR = with(density) { safeInsets.calculateEndPadding(dir).roundToPx() }
    val safeB = with(density) { safeInsets.calculateBottomPadding().roundToPx() }

    val effectiveCanvas = effectiveInspectorCanvasSize(canvasSizePx, WindowInsets.ime.getBottom(density), keyboardHeightPx)
    val availH = (effectiveCanvas.height - safeT - safeB - 2 * marginPx).coerceAtLeast(0)
    val availW = (effectiveCanvas.width - safeL - safeR - 2 * marginPx).coerceAtLeast(0)
    val maxH = with(density) { availH.toDp() }
    val maxW = minOf(maxWidth, with(density) { availW.toDp() })
    val minW = minOf(minWidth, maxW)

    val rememberedAnchor = remember(sessionKey) { anchorRect }
    var inspectorSize by remember(sessionKey) { mutableStateOf(IntSize.Zero) }
    var draggedPlacement by remember(sessionKey) { mutableStateOf<IntOffset?>(null) }

    val computedOffset = if (inspectorSize.width > 0) {
        draggedPlacement?.let {
            clampInspectorToCanvas(it, inspectorSize, effectiveCanvas, marginPx, safeL, safeT, safeR, safeB)
        } ?: anchoredInspectorOffset(
            rememberedAnchor?.left, rememberedAnchor?.top, rememberedAnchor?.right, rememberedAnchor?.bottom,
            inspectorSize, effectiveCanvas, gapPx, marginPx, safeL, safeT, safeR, safeB,
        )
    } else null

    val latestCanvas by rememberUpdatedState(effectiveCanvas)
    val latestSize by rememberUpdatedState(inspectorSize)
    val latestOffset by rememberUpdatedState(computedOffset)
    val dragModifier = Modifier.pointerInput(sessionKey) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            val current = draggedPlacement ?: latestOffset ?: return@detectDragGestures
            draggedPlacement = draggedInspectorOffset(
                current, dragAmount, latestSize, latestCanvas, safeL + marginPx, safeT + marginPx, safeR + marginPx, safeB + marginPx,
            )
        }
    }

    FloatingSurface(
        modifier = modifier
            .alpha(if (computedOffset == null) 0f else 1f)
            .offset { computedOffset ?: IntOffset.Zero }
            .liquidGlassSpringAnimation(
                expanded = !LocalFloatingSurfaceClosing.current && computedOffset != null,
                origin = TransformOrigin(0.5f, 0.5f),
                role = DesignTokens.GlassMotion.SurfaceRole.POPOVER,
            )
            .onSizeChanged { inspectorSize = it }
            .widthIn(min = minW, max = maxW)
            .heightIn(max = maxH)
            .padding(4.dp),
    ) {
        content(dragModifier)
    }
}
