package com.mobixournal.ui

import android.graphics.RectF
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mobixournal.design.DesignTokens
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.LocalFloatingSurfaceClosing
import com.mobixournal.ui.theme.liquidGlassContentSize
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
 * (right, left, below, above) inside [canvasSize] and picks the candidate with minimum overlap
 * over the figure (preserving Right > Left > Below > Above on ties).
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

    val maxX = (canvasSize.width - inspectorSize.width - marginPx).coerceAtLeast(marginPx)
    val maxY = (canvasSize.height - inspectorSize.height - marginPx).coerceAtLeast(marginPx)
    val bottom = selectionBottom ?: selectionTop
    val centeredY = (((selectionTop + bottom) / 2f) - inspectorSize.height / 2f).toInt().coerceIn(marginPx, maxY)
    val centeredX = (((selectionLeft + selectionRight) / 2f) - inspectorSize.width / 2f).toInt().coerceIn(marginPx, maxX)

    val candidates = listOf(
        IntOffset((selectionRight + gapPx).toInt().coerceIn(marginPx, maxX), centeredY),
        IntOffset((selectionLeft - gapPx - inspectorSize.width).toInt().coerceIn(marginPx, maxX), centeredY),
        IntOffset(centeredX, (bottom + gapPx).toInt().coerceIn(marginPx, maxY)),
        IntOffset(centeredX, (selectionTop - gapPx - inspectorSize.height).toInt().coerceIn(marginPx, maxY)),
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
): IntOffset {
    val maxX = (canvasSize.width - inspectorSize.width).coerceAtLeast(0)
    val maxY = (canvasSize.height - inspectorSize.height).coerceAtLeast(0)
    return IntOffset(
        (start.x + delta.x.roundToInt()).coerceIn(0, maxX),
        (start.y + delta.y.roundToInt()).coerceIn(0, maxY),
    )
}

/**
 * Clamps a placed [offset] into [canvasSize] without mutating the underlying placement.
 */
internal fun clampInspectorToCanvas(
    offset: IntOffset,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    marginPx: Int = 0,
): IntOffset {
    val maxX = (canvasSize.width - inspectorSize.width - marginPx).coerceAtLeast(marginPx)
    val maxY = (canvasSize.height - inspectorSize.height - marginPx).coerceAtLeast(marginPx)
    return IntOffset(offset.x.coerceIn(marginPx, maxX), offset.y.coerceIn(marginPx, maxY))
}

/**
 * Shared floating container for **anchored** living-element inspectors (such as plot, shape and circuit).
 *
 * Picks the side with minimum overlap over the figure, animates arrival/departure and size/position
 * changes on [DesignTokens.GlassMotion], and stays instant during manual header drags.
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
    @Suppress("UNUSED_PARAMETER") animateSurface: Boolean = true,
    content: @Composable (dragModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { gapDp.roundToPx() }
    val marginPx = with(density) { marginDp.roundToPx() }
    val effectiveCanvas = effectiveInspectorCanvasSize(canvasSizePx, WindowInsets.ime.getBottom(density), keyboardHeightPx)
    val maxH = with(density) { (effectiveCanvas.height - 2 * marginPx).coerceAtLeast(0).toDp() }
    val maxW = minOf(maxWidth, with(density) { (effectiveCanvas.width - 2 * marginPx).coerceAtLeast(0).toDp() })
    val minW = minOf(minWidth, maxW)

    val rememberedAnchor = remember(sessionKey) { anchorRect }
    var inspectorSize by remember(sessionKey) { mutableStateOf(IntSize.Zero) }
    var placement by remember(sessionKey) { mutableStateOf<IntOffset?>(null) }
    var userDragged by remember(sessionKey) { mutableStateOf(false) }
    var isDragging by remember(sessionKey) { mutableStateOf(false) }

    LaunchedEffect(sessionKey, effectiveCanvas, inspectorSize, userDragged) {
        if (!userDragged && inspectorSize.width > 0) {
            placement = anchoredInspectorOffset(
                rememberedAnchor?.left, rememberedAnchor?.top, rememberedAnchor?.right, rememberedAnchor?.bottom,
                inspectorSize, effectiveCanvas, gapPx, marginPx,
            )
        }
    }

    val targetOffset = placement?.let { clampInspectorToCanvas(it, inspectorSize, effectiveCanvas, marginPx) }
    val animatedOffset by animateIntOffsetAsState(
        targetValue = targetOffset ?: IntOffset.Zero,
        animationSpec = spring(
            dampingRatio = DesignTokens.GlassMotion.OPEN_DAMPING_RATIO,
            stiffness = DesignTokens.GlassMotion.OPEN_STIFFNESS,
        ),
        label = "inspectorOffset",
    )
    val shownOffset = if (isDragging) targetOffset else animatedOffset

    val latestCanvas by rememberUpdatedState(effectiveCanvas)
    val latestSize by rememberUpdatedState(inspectorSize)
    val latestMargin by rememberUpdatedState(marginPx)
    val dragModifier = Modifier.pointerInput(sessionKey) {
        detectDragGestures(
            onDragStart = { isDragging = true },
            onDragEnd = { isDragging = false },
            onDragCancel = { isDragging = false },
        ) { change, dragAmount ->
            change.consume()
            val stored = placement ?: return@detectDragGestures
            userDragged = true
            val clamped = clampInspectorToCanvas(stored, latestSize, latestCanvas, latestMargin)
            placement = draggedInspectorOffset(clamped, dragAmount, latestSize, latestCanvas)
        }
    }

    FloatingSurface(
        modifier = modifier
            .alpha(if (targetOffset == null) 0f else 1f)
            .offset { shownOffset ?: IntOffset.Zero }
            .liquidGlassSpringAnimation(
                expanded = !LocalFloatingSurfaceClosing.current,
                origin = TransformOrigin(0.5f, 0.5f),
                role = DesignTokens.GlassMotion.SurfaceRole.POPOVER,
            )
            .onSizeChanged { inspectorSize = it }
            .widthIn(min = minW, max = maxW)
            .heightIn(max = maxH)
            .padding(4.dp),
    ) {
        Box(modifier = Modifier.liquidGlassContentSize()) {
            content(dragModifier)
        }
    }
}
