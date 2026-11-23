package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.LocalFloatingSurfaceClosing
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import kotlinx.coroutines.launch

private val FOCUS_SLOT_DP = DesignTokens.COMPACT_BUTTON_SIZE_DP.dp
private val FOCUS_GAP_DP = 4.dp
private val FOCUS_HANDLE_DP = 14.dp
private val FOCUS_PAD_DP = 6.dp
private val FOCUS_EDGE_MARGIN_DP = 14.dp
private val FOCUS_ENTER_EDGE_DP = 12.dp
private val FOCUS_EXIT_EDGE_DP = 56.dp

private class BarCoordsRef {
    var coords: LayoutCoordinates? = null
}
private val FOCUS_TOP_SAFE_DP = 56.dp
private val FOCUS_BOTTOM_SAFE_DP = 16.dp

/**
 * Draggable Liquid Glass floating bar shown during Focus mode. Dragging near either side edge
 * morphs it fluidly from horizontal to vertical using [LiquidMorphBarLayout] with hysteresis,
 * elastic squash-and-stretch feedback, and normalized position persistence.
 */
@Composable
internal fun FocusModeFloatingBarOverlay(
    visible: Boolean,
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customizing by remember { mutableStateOf(false) }
    DialogPresence(visible) {
        FocusModeFloatingBarCanvas(
            ui = ui,
            pane = pane,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onCustomize = { customizing = true },
            modifier = modifier,
        )
    }
    DialogPresence(customizing) {
        FocusBarCustomizeDialog(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onDismiss = { customizing = false },
        )
    }
}

private data class FocusBarMetrics(
    val slotPx: Float,
    val gapPx: Float,
    val handlePx: Float,
    val padPx: Float,
    val marginPx: Float,
    val enterPx: Float,
    val exitPx: Float,
    val topSafePx: Float,
    val bottomSafePx: Float,
)

@Composable
private fun rememberFocusBarMetrics(): FocusBarMetrics {
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            FocusBarMetrics(
                slotPx = FOCUS_SLOT_DP.toPx(),
                gapPx = FOCUS_GAP_DP.toPx(),
                handlePx = FOCUS_HANDLE_DP.toPx(),
                padPx = FOCUS_PAD_DP.toPx(),
                marginPx = FOCUS_EDGE_MARGIN_DP.toPx(),
                enterPx = FOCUS_ENTER_EDGE_DP.toPx(),
                exitPx = FOCUS_EXIT_EDGE_DP.toPx(),
                topSafePx = FOCUS_TOP_SAFE_DP.toPx(),
                bottomSafePx = FOCUS_BOTTOM_SAFE_DP.toPx(),
            )
        }
    }
}

@Composable
private fun FocusModeFloatingBarCanvas(
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = remember(settings.focusBarSlots) { resolveFocusBarSlots(settings.focusBarSlots) }
    val storedAnchor = remember(settings.focusBarAnchor) { decodeFocusBarAnchor(settings.focusBarAnchor) }
    var dockState by remember(settings.focusBarAnchor) { mutableStateOf(FocusBarDockState(storedAnchor.dock)) }
    val dock = dockState.dock
    var dragging by remember { mutableStateOf(false) }
    val barCoords = remember { BarCoordsRef() }
    var lastCommittedAnchor by remember { mutableStateOf<String?>(null) }
    var lastLayoutKey by remember { mutableStateOf<Triple<Float, Float, Int>?>(null) }
    val morphProgress by animateFloatAsState(
        targetValue = if (dock == FocusBarDock.FREE) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
        label = "FocusBarMorph",
    )
    val jelly = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val m = rememberFocusBarMetrics()
    val latestSettings by rememberUpdatedState(settings)
    val latestOnSettings by rememberUpdatedState(onSettingsChange)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val layoutKey = Triple(containerW, containerH, slots.size)

        LaunchedEffect(containerW, containerH, slots.size, settings.focusBarAnchor) {
            val sameLayout = lastLayoutKey == layoutKey
            lastLayoutKey = layoutKey
            if (sameLayout && settings.focusBarAnchor == lastCommittedAnchor) return@LaunchedEffect
            if (!dragging && containerW > 0f && containerH > 0f) {
                val targetMorph = if (storedAnchor.dock == FocusBarDock.FREE) 0f else 1f
                val (bw, bh) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, targetMorph)
                val (px, py) = resolveFocusBarPixelPosition(
                    storedAnchor, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                offsetX.snapTo(px)
                offsetY.snapTo(py)
            }
        }

        EdgeDockGlow(visible = dragging && dock == FocusBarDock.LEFT_EDGE, alignment = Alignment.CenterStart, left = true)
        EdgeDockGlow(visible = dragging && dock == FocusBarDock.RIGHT_EDGE, alignment = Alignment.CenterEnd, left = false)

        val dragModifier = focusBarDragModifier(
            slots = slots, containerW = containerW, containerH = containerH, m = m, dockState = dockState,
            barCoords = barCoords, morphProgress = morphProgress,
            readOffset = { if (dragging) dragOffsetX to dragOffsetY else offsetX.value to offsetY.value },
            onDragStep = { cx, cy, nextState, dockChanged, axisChanged ->
                if (!dragging) dragging = true
                dragOffsetX = cx
                dragOffsetY = cy
                dockState = nextState
                if (dockChanged) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (axisChanged) scope.launch { jelly.snapTo(0.91f); jelly.animateTo(1f, spring(dampingRatio = 0.56f, stiffness = 420f)) }
                }
            },
            onDragFinish = { _, sx, sy, anchor ->
                val encoded = encodeFocusBarAnchor(anchor)
                lastCommittedAnchor = encoded
                val (fx, fy) = dragOffsetX to dragOffsetY
                scope.launch {
                    offsetX.snapTo(fx); offsetY.snapTo(fy); dragging = false
                    launch { offsetX.animateTo(sx, spring(dampingRatio = 0.78f, stiffness = 340f)) }
                    launch { offsetY.animateTo(sy, spring(dampingRatio = 0.78f, stiffness = 340f)) }
                }
                latestOnSettings(latestSettings.copy(focusBarAnchor = encoded))
            },
        )

        FocusGlassBarCapsule(
            slots = slots, morphProgress = morphProgress, jellyScale = jelly.value, vertical = dock != FocusBarDock.FREE,
            ui = ui, pane = pane, settings = settings, onSettingsChange = onSettingsChange, onCustomize = onCustomize,
            modifier = Modifier
                .offset {
                    val (bw, bh) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, morphProgress)
                    val (cx, cy) = clampFocusBarPosition(
                        if (dragging) dragOffsetX else offsetX.value, if (dragging) dragOffsetY else offsetY.value,
                        bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                    )
                    IntOffset(cx.roundToIntPx(), cy.roundToIntPx())
                }
                .onPlaced { barCoords.coords = it }
                .then(dragModifier),
        )
    }
}

@Composable
private fun focusBarDragModifier(
    slots: List<FocusBarSlotKind>,
    containerW: Float,
    containerH: Float,
    m: FocusBarMetrics,
    dockState: FocusBarDockState,
    barCoords: BarCoordsRef,
    morphProgress: Float,
    readOffset: () -> Pair<Float, Float>,
    onDragStep: (Float, Float, FocusBarDockState, Boolean, Boolean) -> Unit,
    onDragFinish: (FocusBarDock, Float, Float, FocusBarAnchor) -> Unit,
): Modifier {
    val currentDockState by rememberUpdatedState(dockState)
    val currentMorph by rememberUpdatedState(morphProgress)
    val currentReadOffset by rememberUpdatedState(readOffset)
    return Modifier.pointerInput(slots.size, containerW, containerH) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val sloppy = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            val (curW, curH) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, currentMorph)
            val grabFracX = (down.position.x / curW.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val grabFracY = (down.position.y / curH.coerceAtLeast(1f)).coerceIn(0f, 1f)

            fun applyChange(change: PointerInputChange) {
                change.consume()
                val coords = barCoords.coords
                val (fallbackX, fallbackY) = currentReadOffset()
                val origin = if (coords != null && coords.isAttached) coords.positionInParent() else Offset(fallbackX, fallbackY)
                val finger = origin + change.position
                val activeState = currentDockState
                val (horizW, _) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, 0f)
                val barLeftX = finger.x - horizW * grabFracX
                val barRightX = barLeftX + horizW
                val nextState = nextFocusBarDockState(
                    activeState, finger.x, barLeftX, barRightX, containerW, m.marginPx, m.enterPx, m.exitPx,
                )
                val (barW, barH) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, currentMorph)
                val desiredX = when (nextState.dock) {
                    FocusBarDock.LEFT_EDGE -> m.marginPx
                    FocusBarDock.RIGHT_EDGE -> containerW - barW - m.marginPx
                    FocusBarDock.FREE -> finger.x - barW * grabFracX
                }
                val (cx, cy) = clampFocusBarPosition(
                    desiredX, finger.y - barH * grabFracY,
                    barW, barH, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                onDragStep(
                    cx, cy, nextState,
                    nextState.dock != activeState.dock,
                    (activeState.dock != FocusBarDock.FREE) != (nextState.dock != FocusBarDock.FREE),
                )
            }

            try {
                applyChange(sloppy)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == sloppy.id } ?: break
                    if (!change.pressed) break
                    if (event.type == PointerEventType.Move) applyChange(change)
                }
            } finally {
                val finalDock = currentDockState.dock
                val finalMorph = if (finalDock == FocusBarDock.FREE) 0f else 1f
                val (bw, bh) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, finalMorph)
                val (curX, curY) = currentReadOffset()
                val (sx, sy) = snapFocusBarPosition(
                    finalDock, curX, curY, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                val anchor = focusBarAnchorFromPixels(
                    finalDock, sx, sy, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                onDragFinish(finalDock, sx, sy, anchor)
            }
        }
    }
}

@Composable
private fun FocusGlassBarCapsule(
    slots: List<FocusBarSlotKind>,
    morphProgress: Float,
    jellyScale: Float,
    vertical: Boolean,
    ui: EditorUiState,
    pane: PaneState,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ModernTheme.panelCornerRadiusDp)
    val style = rememberToolbarStyleCallbacks(ui, pane.surface, settings, onSettingsChange)
    val favorites = ToolbarFavoritesCallbacks(
        penFavorites = settings.penFavorites,
        highlighterFavorites = settings.highlighterFavorites,
        onPick = { colorOwner, color -> pickFavoriteColor(pane.surface, ui, settings, onSettingsChange, colorOwner, color) },
        onAssign = { colorOwner, idx, color -> onSettingsChange(assignFavorite(settings, colorOwner, idx, color)) },
    )
    // Gentle stretch along the emerging axis during liquid morphing.
    val stretchX = 1f + (1f - jellyScale) * (if (vertical) -0.45f else 0.7f)
    val stretchY = 1f + (1f - jellyScale) * (if (vertical) 0.7f else -0.45f)

    Surface(
        shape = shape,
        color = rememberFloatingSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = ModernTheme.capsuleShadowDp,
        modifier = modifier
            .liquidGlassSpringAnimation(
                expanded = !LocalFloatingSurfaceClosing.current,
                origin = TransformOrigin(0.5f, 0.5f),
            )
            .graphicsLayer {
                scaleX = stretchX
                scaleY = stretchY
            }
            .floatingRim(ModernTheme.panelCornerRadiusDp),
    ) {
        CompositionLocalProvider(LocalRailSlotSize provides FOCUS_SLOT_DP) {
            LiquidMorphBarLayout(
                slotCount = slots.size,
                morphProgress = morphProgress,
            ) {
                FocusBarDragHandle(morphProgress = morphProgress, onLongClick = onCustomize)
                slots.forEach { kind ->
                    Box(modifier = Modifier.size(FOCUS_SLOT_DP), contentAlignment = Alignment.Center) {
                        FocusBarSlotItem(
                            kind = kind,
                            vertical = vertical,
                            ui = ui,
                            pane = pane,
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            style = style,
                            favorites = favorites,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Compose [Layout] that continuously interpolates the capsule bounds and every child slot's
 * `(x, y)` coordinates between horizontal row (`0f`) and vertical column (`1f`).
 */
@Composable
private fun LiquidMorphBarLayout(
    slotCount: Int,
    morphProgress: Float,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slotPx = with(density) { FOCUS_SLOT_DP.toPx() }
    val gapPx = with(density) { FOCUS_GAP_DP.toPx() }
    val handlePx = with(density) { FOCUS_HANDLE_DP.toPx() }
    val padPx = with(density) { FOCUS_PAD_DP.toPx() }

    Layout(content = content) { measurables, _ ->
        val (barW, barH) = focusBarSizePx(slotCount, slotPx, gapPx, handlePx, padPx, morphProgress)
        val slotInt = slotPx.roundToIntPx()
        val handleInt = handlePx.roundToIntPx()
        val placeables = measurables.mapIndexed { idx, m ->
            if (idx == 0) {
                val hw = (handleInt + (slotInt - handleInt) * morphProgress).roundToIntPx()
                val hh = (slotInt - (slotInt - handleInt) * morphProgress).roundToIntPx()
                m.measure(Constraints.fixed(hw.coerceAtLeast(1), hh.coerceAtLeast(1)))
            } else {
                m.measure(Constraints.fixed(slotInt, slotInt))
            }
        }
        layout(barW.roundToIntPx(), barH.roundToIntPx()) {
            placeables.forEachIndexed { idx, placeable ->
                val (cx, cy) = focusBarChildOffsetPx(idx, slotPx, gapPx, handlePx, padPx, morphProgress)
                placeable.placeRelative(cx.roundToIntPx(), cy.roundToIntPx())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusBarDragHandle(
    morphProgress: Float,
    onLongClick: () -> Unit,
) {
    val ink = rememberChromeInk()
    val w = 4.dp + 16.dp * morphProgress
    val h = 20.dp - 16.dp * morphProgress
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .semantics { contentDescription = "Drag Focus bar or tap to customize" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = w, height = h)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.34f)),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeDockGlow(
    visible: Boolean,
    alignment: Alignment,
    left: Boolean,
) {
    val accent = MaterialTheme.colorScheme.primary
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(alignment),
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 48.dp, horizontal = 8.dp)
                .width(58.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(ModernTheme.panelCornerRadiusDp))
                .background(
                    Brush.horizontalGradient(
                        colors = if (left) listOf(accent.copy(alpha = 0.22f), Color.Transparent)
                        else listOf(Color.Transparent, accent.copy(alpha = 0.22f)),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(ModernTheme.panelCornerRadiusDp),
                ),
        )
    }
}
