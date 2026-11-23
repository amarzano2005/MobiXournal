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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
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
private val FOCUS_ENTER_EDGE_DP = 128.dp
private val FOCUS_EXIT_EDGE_DP = 178.dp
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
    var dock by remember(settings.focusBarAnchor) { mutableStateOf(storedAnchor.dock) }
    var dragging by remember { mutableStateOf(false) }
    var barLayoutPos by remember { mutableStateOf(Offset.Zero) }
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
            slots = slots,
            containerW = containerW,
            containerH = containerH,
            m = m,
            dock = dock,
            barLayoutPos = barLayoutPos,
            onDockChange = { nextDock, wasVertical, nowVertical ->
                dock = nextDock
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (wasVertical != nowVertical) {
                    scope.launch {
                        jelly.snapTo(0.91f)
                        jelly.animateTo(1f, spring(dampingRatio = 0.56f, stiffness = 420f))
                    }
                }
            },
            morphProgress = morphProgress,
            offsetX = offsetX,
            offsetY = offsetY,
            onDraggingChange = { dragging = it },
            onCommitAnchor = { anchor ->
                val encoded = encodeFocusBarAnchor(anchor)
                lastCommittedAnchor = encoded
                latestOnSettings(latestSettings.copy(focusBarAnchor = encoded))
            },
        )

        FocusGlassBarCapsule(
            slots = slots,
            morphProgress = morphProgress,
            jellyScale = jelly.value,
            vertical = dock != FocusBarDock.FREE,
            ui = ui,
            pane = pane,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onCustomize = onCustomize,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToIntPx(), offsetY.value.roundToIntPx()) }
                .onGloballyPositioned { barLayoutPos = it.positionInParent() }
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
    dock: FocusBarDock,
    barLayoutPos: Offset,
    onDockChange: (FocusBarDock, Boolean, Boolean) -> Unit,
    morphProgress: Float,
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>,
    onDraggingChange: (Boolean) -> Unit,
    onCommitAnchor: (FocusBarAnchor) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val currentDock by rememberUpdatedState(dock)
    val currentMorph by rememberUpdatedState(morphProgress)
    val currentBarPos by rememberUpdatedState(barLayoutPos)
    return Modifier.pointerInput(slots.size, containerW, containerH) {
        var grabFracX = 0.5f
        var grabFracY = 0.5f
        detectDragGestures(
            onDragStart = { startLocal ->
                val (curW, curH) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, currentMorph)
                grabFracX = (startLocal.x / curW.coerceAtLeast(1f)).coerceIn(0f, 1f)
                grabFracY = (startLocal.y / curH.coerceAtLeast(1f)).coerceIn(0f, 1f)
                onDraggingChange(true)
            },
            onDrag = { change, _ ->
                change.consume()
                val activeDock = currentDock
                val fingerPos = currentBarPos + change.position
                val (enterPx, exitPx) = effectiveEdgeThresholds(containerW, m.enterPx, m.exitPx)
                val nextDock = nextFocusBarDock(activeDock, fingerPos.x, containerW, enterPx, exitPx)
                if (nextDock != activeDock) {
                    onDockChange(nextDock, activeDock != FocusBarDock.FREE, nextDock != FocusBarDock.FREE)
                }
                val (barW, barH) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, currentMorph)
                val desiredX = fingerPos.x - barW * grabFracX
                val desiredY = fingerPos.y - barH * grabFracY
                val (cx, cy) = clampFocusBarPosition(
                    desiredX, desiredY, barW, barH, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                scope.launch {
                    offsetX.snapTo(cx)
                    offsetY.snapTo(cy)
                }
            },
            onDragEnd = {
                onDraggingChange(false)
                val activeDock = currentDock
                val finalMorph = if (activeDock == FocusBarDock.FREE) 0f else 1f
                val (bw, bh) = focusBarSizePx(slots.size, m.slotPx, m.gapPx, m.handlePx, m.padPx, finalMorph)
                val (sx, sy) = snapFocusBarPosition(
                    activeDock, offsetX.value, offsetY.value, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx,
                )
                scope.launch {
                    launch { offsetX.animateTo(sx, spring(dampingRatio = 0.78f, stiffness = 340f)) }
                    launch { offsetY.animateTo(sy, spring(dampingRatio = 0.78f, stiffness = 340f)) }
                }
                onCommitAnchor(
                    focusBarAnchorFromPixels(activeDock, sx, sy, bw, bh, containerW, containerH, m.marginPx, m.topSafePx, m.bottomSafePx),
                )
            },
            onDragCancel = { onDraggingChange(false) },
        )
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
            .combinedClickable(onClick = onLongClick, onLongClick = onLongClick)
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
