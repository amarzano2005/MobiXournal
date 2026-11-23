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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val FOCUS_SLOT_DP = DesignTokens.COMPACT_BUTTON_SIZE_DP.dp
private val FOCUS_GAP_DP = 4.dp
private val FOCUS_HANDLE_DP = 14.dp
private val FOCUS_PAD_DP = 6.dp
private val FOCUS_EDGE_MARGIN_DP = 14.dp
private val FOCUS_SIDE_FINGER_ZONE_DP = 22.dp
private val FOCUS_SIDE_OVERPUSH_DP = 36.dp
private val FOCUS_UNDOCK_PULL_DP = 28.dp

private class CoordsRef {
    var coords: LayoutCoordinates? = null
}

private class RenderedOffsetRef {
    var x = 0f
    var y = 0f
}
private val FOCUS_TOP_SAFE_DP = 56.dp
private val FOCUS_BOTTOM_SAFE_DP = 14.dp

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
    val sideFingerZonePx: Float,
    val sideOverpushPx: Float,
    val undockPullPx: Float,
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
                sideFingerZonePx = FOCUS_SIDE_FINGER_ZONE_DP.toPx(),
                sideOverpushPx = FOCUS_SIDE_OVERPUSH_DP.toPx(),
                undockPullPx = FOCUS_UNDOCK_PULL_DP.toPx(),
                topSafePx = FOCUS_TOP_SAFE_DP.toPx(),
                bottomSafePx = FOCUS_BOTTOM_SAFE_DP.toPx(),
            )
        }
    }
}

private class FocusBarMotionState(
    initialDock: FocusBarDock,
    val scope: kotlinx.coroutines.CoroutineScope,
    val haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    val m: FocusBarMetrics,
) {
    var dock by mutableStateOf(initialDock)
    var dragging by mutableStateOf(false)
    val containerCoords = CoordsRef()
    val barCoords = CoordsRef()
    val lastRendered = RenderedOffsetRef()
    var lastCommittedAnchor by mutableStateOf<String?>(null)
    var lastLayoutKey by mutableStateOf<Triple<Float, Float, Int>?>(null)
    val jelly = Animatable(1f)
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)
    val dockJumpX = Animatable(0f)
    val dockJumpY = Animatable(0f)
    var dragFingerX by mutableFloatStateOf(0f)
    var dragFingerY by mutableFloatStateOf(0f)
    var dragGrabFracX by mutableFloatStateOf(0.5f)
    var dragGrabFracY by mutableFloatStateOf(0.5f)
    private var dockJumpJob: Job? = null
    private var settleJob: Job? = null
    private var dragGeneration by mutableIntStateOf(0)

    fun handleDragStart(startDock: FocusBarDock, fx: Float, fy: Float, gx: Float, gy: Float) {
        dragGeneration += 1
        settleJob?.cancel(); settleJob = null
        dockJumpJob?.cancel(); dockJumpJob = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) { dockJumpX.snapTo(0f); dockJumpY.snapTo(0f) }
        dock = startDock
        dragFingerX = fx; dragFingerY = fy
        dragGrabFracX = gx; dragGrabFracY = gy
        dragging = true
    }

    fun handleDragStep(
        slotCount: Int,
        containerW: Float,
        containerH: Float,
        morphProgress: Float,
        fx: Float,
        fy: Float,
        step: FocusBarDragStep,
    ) {
        if (!dragging) dragging = true
        if (!step.dockChanged) {
            dragFingerX = fx; dragFingerY = fy
            dragGrabFracX = step.state.grabFracX; dragGrabFracY = step.state.grabFracY
            return
        }
        val (oldX, oldY) = lastRendered.x to lastRendered.y
        val (bw, bh) = focusBarSizePx(slotCount, m.slotPx, m.gapPx, m.handlePx, m.padPx, morphProgress)
        val (newBaseX, newBaseY) = focusBarRenderOffsetPx(
            step.state.dock, fx, fy, step.state.grabFracX, step.state.grabFracY,
            bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
        )
        dock = step.state.dock
        dragFingerX = fx; dragFingerY = fy
        dragGrabFracX = step.state.grabFracX; dragGrabFracY = step.state.grabFracY
        dockJumpJob?.cancel()
        dockJumpJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dockJumpX.snapTo(oldX - newBaseX); dockJumpY.snapTo(oldY - newBaseY)
            launch { dockJumpX.animateTo(0f, spring(dampingRatio = 1.0f, stiffness = 520f)) }
            launch { dockJumpY.animateTo(0f, spring(dampingRatio = 1.0f, stiffness = 520f)) }
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (step.axisChanged) {
            scope.launch { jelly.snapTo(0.95f); jelly.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 420f)) }
        }
    }

    fun handleDragFinish(
        slotCount: Int,
        containerW: Float,
        containerH: Float,
        finalDock: FocusBarDock,
        fx: Float,
        fy: Float,
        gx: Float,
        gy: Float,
    ): String {
        val finalMorph = if (finalDock.isVertical) 1f else 0f
        val (bw, bh) = focusBarSizePx(slotCount, m.slotPx, m.gapPx, m.handlePx, m.padPx, finalMorph)
        val (sx, sy) = focusBarRenderOffsetPx(
            finalDock, fx, fy, gx, gy, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
        )
        val persistedDock = if (finalDock == FocusBarDock.FREE && sy >= containerH - bh - m.bottomSafePx - 2f) {
            FocusBarDock.BOTTOM_EDGE
        } else {
            finalDock
        }
        val encoded = encodeFocusBarAnchor(
            focusBarAnchorFromPixels(
                persistedDock, sx, sy, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
            ),
        )
        val (startX, startY) = lastRendered.x to lastRendered.y
        val gen = dragGeneration
        dock = persistedDock
        lastCommittedAnchor = encoded
        dockJumpJob?.cancel(); dockJumpJob = null
        settleJob?.cancel()
        settleJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dockJumpX.snapTo(0f); dockJumpY.snapTo(0f)
            offsetX.snapTo(startX); offsetY.snapTo(startY)
            if (dragGeneration != gen) return@launch
            dragging = false
            launch { offsetX.animateTo(sx, spring(dampingRatio = 0.90f, stiffness = 420f)) }
            launch { offsetY.animateTo(sy, spring(dampingRatio = 0.90f, stiffness = 420f)) }
        }
        return encoded
    }
}

private fun computeRenderedBarOffset(
    slotCount: Int,
    m: FocusBarMetrics,
    morphProgress: Float,
    motion: FocusBarMotionState,
    containerW: Float,
    containerH: Float,
): IntOffset {
    val (bw, bh) = focusBarSizePx(slotCount, m.slotPx, m.gapPx, m.handlePx, m.padPx, morphProgress)
    val (rawX, rawY) = if (motion.dragging) {
        val (baseX, baseY) = focusBarRenderOffsetPx(
            dock = motion.dock,
            fingerX = motion.dragFingerX,
            fingerY = motion.dragFingerY,
            grabFracX = motion.dragGrabFracX,
            grabFracY = motion.dragGrabFracY,
            bw = bw,
            bh = bh,
            containerW = containerW,
            containerH = containerH,
            marginPx = m.marginPx,
            topSafePx = m.topSafePx,
            bottomSafePx = m.bottomSafePx,
        )
        (baseX + motion.dockJumpX.value) to (baseY + motion.dockJumpY.value)
    } else {
        val tx = when (motion.dock) {
            FocusBarDock.LEFT_EDGE -> m.marginPx
            FocusBarDock.RIGHT_EDGE -> containerW - bw - m.marginPx
            FocusBarDock.FREE, FocusBarDock.BOTTOM_EDGE -> motion.offsetX.value
        }
        val ty = if (motion.dock == FocusBarDock.BOTTOM_EDGE) containerH - bh - m.bottomSafePx else motion.offsetY.value
        tx to ty
    }
    val (cx, cy) = clampFocusBarPosition(
        rawX, rawY, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
    )
    motion.lastRendered.x = cx
    motion.lastRendered.y = cy
    return IntOffset(cx.roundToIntPx(), cy.roundToIntPx())
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
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val m = rememberFocusBarMetrics()
    val motion = remember(m) { FocusBarMotionState(storedAnchor.dock, scope, haptics, m) }
    val morphProgress by animateFloatAsState(
        targetValue = if (motion.dock.isVertical) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 420f),
        label = "FocusBarMorph",
    )
    val latestSettings by rememberUpdatedState(settings)
    val latestOnSettings by rememberUpdatedState(onSettingsChange)

    BoxWithConstraints(modifier = modifier.fillMaxSize().onPlaced { motion.containerCoords.coords = it }) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val layoutKey = Triple(containerW, containerH, slots.size)

        LaunchedEffect(containerW, containerH, slots.size, settings.focusBarAnchor) {
            val sameLayout = motion.lastLayoutKey == layoutKey
            motion.lastLayoutKey = layoutKey
            if (sameLayout && settings.focusBarAnchor == motion.lastCommittedAnchor) return@LaunchedEffect
            if (!motion.dragging && containerW > 0f && containerH > 0f) {
                motion.dock = storedAnchor.dock
                val targetMorph = if (storedAnchor.dock.isVertical) 1f else 0f
                val (bw, bh) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, targetMorph)
                val (px, py) = resolveFocusBarPixelPosition(
                    storedAnchor, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                motion.offsetX.snapTo(px)
                motion.offsetY.snapTo(py)
            }
        }

        val dragModifier = focusBarDragModifier(
            slots = slots, containerW = containerW, containerH = containerH, m = m, dock = motion.dock,
            containerCoords = motion.containerCoords, barCoords = motion.barCoords, lastRendered = motion.lastRendered,
            morphProgress = morphProgress,
            onDragStart = { startDock, fx, fy, gx, gy -> motion.handleDragStart(startDock, fx, fy, gx, gy) },
            onDragStep = { fx, fy, step -> motion.handleDragStep(slots.size, containerW, containerH, morphProgress, fx, fy, step) },
            onDragFinish = { finalDock, fx, fy, gx, gy ->
                val encoded = motion.handleDragFinish(slots.size, containerW, containerH, finalDock, fx, fy, gx, gy)
                latestOnSettings(latestSettings.copy(focusBarAnchor = encoded))
            },
        )

        FocusGlassBarCapsule(
            slots = slots, morphProgress = morphProgress, jellyScale = motion.jelly.value, vertical = motion.dock.isVertical,
            ui = ui, pane = pane, settings = settings, onSettingsChange = onSettingsChange, onCustomize = onCustomize,
            modifier = Modifier
                .offset { computeRenderedBarOffset(slots.size, m, morphProgress, motion, containerW, containerH) }
                .onPlaced { motion.barCoords.coords = it }
                .then(dragModifier),
        )
    }
}

private fun fingerInContainer(
    containerCoords: CoordsRef,
    barCoords: CoordsRef,
    lastRendered: RenderedOffsetRef,
    localPos: Offset,
): Offset {
    val container = containerCoords.coords
    val bar = barCoords.coords
    if (container != null && container.isAttached && bar != null && bar.isAttached) {
        return container.localPositionOf(bar, localPos)
    }
    return Offset(lastRendered.x + localPos.x, lastRendered.y + localPos.y)
}

@Composable
private fun focusBarDragModifier(
    slots: List<FocusBarSlotKind>,
    containerW: Float,
    containerH: Float,
    m: FocusBarMetrics,
    dock: FocusBarDock,
    containerCoords: CoordsRef,
    barCoords: CoordsRef,
    lastRendered: RenderedOffsetRef,
    morphProgress: Float,
    onDragStart: (FocusBarDock, Float, Float, Float, Float) -> Unit,
    onDragStep: (Float, Float, FocusBarDragStep) -> Unit,
    onDragFinish: (FocusBarDock, Float, Float, Float, Float) -> Unit,
): Modifier {
    val currentDock by rememberUpdatedState(dock)
    val currentMorph by rememberUpdatedState(morphProgress)
    return Modifier.pointerInput(slots.size, containerW, containerH) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val sloppy = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            val (curW, curH) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, currentMorph)
            val grabFracX = (sloppy.position.x / curW.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val grabFracY = (sloppy.position.y / curH.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val startFinger = fingerInContainer(containerCoords, barCoords, lastRendered, sloppy.position)
            val startDock = if (currentDock == FocusBarDock.BOTTOM_EDGE) FocusBarDock.FREE else currentDock
            var dragState = FocusBarDragState(
                dock = startDock,
                grabFracX = grabFracX,
                grabFracY = grabFracY,
                dockReferencePx = when (startDock) {
                    FocusBarDock.LEFT_EDGE, FocusBarDock.RIGHT_EDGE -> startFinger.x
                    FocusBarDock.FREE, FocusBarDock.BOTTOM_EDGE -> Float.NaN
                },
            )
            var lastFingerX = startFinger.x
            var lastFingerY = startFinger.y
            onDragStart(startDock, startFinger.x, startFinger.y, grabFracX, grabFracY)

            fun applyChange(change: PointerInputChange) {
                change.consume()
                val finger = fingerInContainer(containerCoords, barCoords, lastRendered, change.position)
                lastFingerX = finger.x
                lastFingerY = finger.y
                val (horizW, horizH) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, 0f)
                val step = stepFocusBarDrag(
                    state = dragState,
                    fingerX = finger.x,
                    fingerY = finger.y,
                    horizW = horizW,
                    horizH = horizH,
                    currentMorph = currentMorph,
                    containerW = containerW,
                    containerH = containerH,
                    marginPx = m.marginPx,
                    topSafePx = m.topSafePx,
                    bottomSafePx = m.bottomSafePx,
                    undockPullPx = m.undockPullPx,
                    sideFingerZonePx = m.sideFingerZonePx,
                    sideOverpushPx = m.sideOverpushPx,
                )
                dragState = step.state
                onDragStep(finger.x, finger.y, step)
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
                onDragFinish(dragState.dock, lastFingerX, lastFingerY, dragState.grabFracX, dragState.grabFracY)
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
