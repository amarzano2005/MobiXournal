package com.mobixournal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.GuideKind
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import kotlin.math.roundToInt

private val PREVIEW_SLOT_OUTER_DP = 42.dp
private val PREVIEW_SLOT_GAP_DP = 2.dp

/**
 * **Focus mode** Settings page: live interactive preview of the Focus floating bar with
 * hold-and-drag reordering, single-badge removal, inline `+` slot, and a contextual visual
 * chip catalog for replacing or adding tools (3..10 slots).
 */
@Composable
fun FocusModeSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val slots = resolveFocusBarSlots(settings.focusBarSlots)
    var picked by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val isAdding = adding && slots.size < FOCUS_BAR_MAX_SLOTS
    val index = picked.coerceIn(0, (slots.size - 1).coerceAtLeast(0))

    SettingsGroup("Floating bar") {
        SettingsNote(
            "Floating bar shown during Focus mode. Drag it to the left or right " +
                "edge to switch between horizontal and vertical.",
        )
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            FocusBarSlotPreviewStrip(
                slots = slots,
                selectedIndex = index,
                onSelect = { idx ->
                    picked = idx
                    adding = false
                },
                onRemove = { removeIdx ->
                    val next = updateFocusBarSlotList(slots, removeIdx, null)
                    onChange(settings.copy(focusBarSlots = next.map { it.id }))
                    picked = removeIdx.coerceAtMost(next.lastIndex)
                    adding = false
                },
                onReorder = { from, to ->
                    val moved = reorderFocusBarSlot(slots, from, to)
                    onChange(settings.copy(focusBarSlots = moved.map { it.id }))
                    picked = to.coerceIn(0, moved.lastIndex)
                    adding = false
                },
                addingSelected = isAdding,
                onSelectAdd = { adding = true },
            )
        }
        Text(
            text = "Hold and drag a slot to reorder · ${slots.size} of $FOCUS_BAR_MAX_SLOTS slots",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        SettingsDivider()
        FocusBarToolCatalog(
            slots = slots,
            selectedIndex = index,
            isAdding = isAdding,
            onPick = { kind ->
                if (isAdding) {
                    val next = addFocusBarSlot(slots, kind)
                    if (next != slots) {
                        onChange(settings.copy(focusBarSlots = next.map { it.id }))
                        picked = next.lastIndex
                        adding = false
                    }
                } else {
                    val next = updateFocusBarSlotList(slots, index, kind)
                    onChange(settings.copy(focusBarSlots = next.map { it.id }))
                }
            },
        )
        if (settings.focusBarSlots.isNotEmpty() || settings.focusBarAnchor.isNotEmpty()) {
            TextButton(
                onClick = {
                    onChange(settings.copy(focusBarSlots = emptyList(), focusBarAnchor = ""))
                    picked = 0
                    adding = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset bar to defaults")
            }
        }
    }
    SettingsGroup("How to enter Focus mode") {
        SettingsNote(
            "Double-tap the centre of the canvas with a finger (any tool), press the Focus mode " +
                "shortcut key, or toggle Focus mode in ☰ → Pen & View. Left and right thirds turn pages.",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusBarToolCatalog(
    slots: List<FocusBarSlotKind>,
    selectedIndex: Int,
    isAdding: Boolean,
    onPick: (FocusBarSlotKind) -> Unit,
) {
    val grouped = remember { FocusBarSlotKind.entries.groupBy { it.category } }
    val selectedKind = if (isAdding) null else slots[selectedIndex]
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        ControlHeader(
            title = if (isAdding) "Add a tool" else "Slot ${selectedIndex + 1} · ${selectedKind!!.label}",
            subtitle = if (isAdding) {
                "Tap a tool to add it to the bar (${slots.size} of $FOCUS_BAR_MAX_SLOTS)."
            } else {
                "Tap any tool to replace this slot. Tools already in the bar swap positions."
            },
            modifier = Modifier.settingsBlockInset(),
        )
        grouped.forEach { (category, items) ->
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { kind ->
                    val isCurrent = kind == selectedKind
                    val inBar = kind in slots
                    val enabled = if (isAdding) !inBar else !isCurrent
                    FocusBarToolChip(
                        kind = kind,
                        isCurrent = isCurrent,
                        inBar = inBar,
                        enabled = enabled,
                        onClick = { onPick(kind) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun FocusBarToolChip(
    kind: FocusBarSlotKind,
    isCurrent: Boolean,
    inBar: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val bgColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        inBar && enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    }
    val borderWidth = if (isCurrent) 1.5.dp else 1.dp
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        inBar && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> ModernTheme.hairlineBorder(chromeIsDark())
    }
    val textTint = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val iconTint = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FocusPreviewSlotGlyph(kind = kind, tint = iconTint, size = 16.dp)
            Text(
                text = kind.label,
                style = MaterialTheme.typography.labelMedium,
                color = textTint,
                maxLines = 1,
            )
            if (inBar && !isCurrent) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)),
                )
            }
        }
    }
}

/** Interactive preview of the Focus bar slots shared with [FocusBarCustomizeDialog]. */
@Composable
internal fun FocusBarSlotPreviewStrip(
    slots: List<FocusBarSlotKind>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: ((Int) -> Unit)? = null,
    onReorder: ((from: Int, to: Int) -> Unit)? = null,
    addingSelected: Boolean = false,
    onSelectAdd: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(ModernTheme.panelCornerRadiusDp)
    val ink = rememberChromeInk()
    val pitchPx = with(LocalDensity.current) { (PREVIEW_SLOT_OUTER_DP + PREVIEW_SLOT_GAP_DP).toPx() }
    val pitchInt = pitchPx.roundToInt().coerceAtLeast(1)
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    val dropIndex = dragTargetIndex(dragIndex, dragOffsetX, pitchInt, slots.size)
    val showRemoveBadge = onRemove != null && slots.size > FOCUS_BAR_MIN_SLOTS

    Surface(
        shape = shape,
        color = rememberFloatingSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.floatingRim(ModernTheme.panelCornerRadiusDp),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(PREVIEW_SLOT_GAP_DP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { idx, kind ->
                val dragging = dragIndex == idx
                val selected = !addingSelected && idx == selectedIndex
                val targetShift = previewSlotShift(idx, dragIndex, dropIndex, pitchPx)
                val animatedShift by animateFloatAsState(
                    targetValue = if (dragging) 0f else targetShift,
                    label = "FocusSlotShift",
                )
                val shiftX = if (dragging) dragOffsetX else animatedShift
                FocusBarPreviewSlotBox(
                    kind = kind,
                    index = idx,
                    slotCount = slots.size,
                    selected = selected,
                    dragging = dragging,
                    shiftX = shiftX,
                    pitchInt = pitchInt,
                    showRemove = selected && dragIndex < 0 && showRemoveBadge,
                    reorderable = onReorder != null,
                    ink = ink,
                    onSelect = { onSelect(idx) },
                    onRemove = { onRemove?.invoke(idx) },
                    onDragStart = {
                        onSelect(idx)
                        dragIndex = idx
                        dragOffsetX = 0f
                    },
                    onDrag = { dx -> dragOffsetX += dx },
                    onDragEnd = { to ->
                        if (dragIndex >= 0 && to != dragIndex) onReorder?.invoke(dragIndex, to)
                        dragIndex = -1
                        dragOffsetX = 0f
                    },
                    onDragCancel = {
                        dragIndex = -1
                        dragOffsetX = 0f
                    },
                )
            }
            if (onSelectAdd != null && slots.size < FOCUS_BAR_MAX_SLOTS) {
                FocusBarAddSlotBox(selected = addingSelected, ink = ink, onClick = onSelectAdd)
            }
        }
    }
}

private fun previewSlotShift(idx: Int, dragIndex: Int, dropIndex: Int, pitchPx: Float): Float = when {
    dragIndex < 0 -> 0f
    idx in (dragIndex + 1)..dropIndex -> -pitchPx
    idx in dropIndex until dragIndex -> pitchPx
    else -> 0f
}

@Composable
private fun FocusBarPreviewSlotBox(
    kind: FocusBarSlotKind,
    index: Int,
    slotCount: Int,
    selected: Boolean,
    dragging: Boolean,
    shiftX: Float,
    pitchInt: Int,
    showRemove: Boolean,
    reorderable: Boolean,
    ink: Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Int) -> Unit,
    onDragCancel: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val latestCount by rememberUpdatedState(slotCount)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)
    val dragModifier = if (reorderable) {
        Modifier.pointerInput(index, slotCount) {
            var accumX = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    accumX = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestOnDragStart()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accumX += dragAmount.x
                    latestOnDrag(dragAmount.x)
                },
                onDragEnd = {
                    latestOnDragEnd(dragTargetIndex(index, accumX, pitchInt, latestCount))
                },
                onDragCancel = { latestOnDragCancel() },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(PREVIEW_SLOT_OUTER_DP)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = shiftX
                val scale = if (dragging) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ModernTheme.railSlotShape)
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                .then(
                    if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, ModernTheme.railSlotShape)
                    else Modifier,
                )
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            FocusPreviewSlotGlyph(kind = kind, tint = if (selected) MaterialTheme.colorScheme.primary else ink)
        }
        if (showRemove) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .border(0.5.dp, ModernTheme.hairlineBorder(chromeIsDark()), CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove slot",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun FocusBarAddSlotBox(selected: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(PREVIEW_SLOT_OUTER_DP),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ModernTheme.railSlotShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                )
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else ModernTheme.hairlineBorder(chromeIsDark()),
                    shape = ModernTheme.railSlotShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Add slot",
                tint = if (selected) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.72f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FocusPreviewSlotGlyph(
    kind: FocusBarSlotKind,
    tint: Color,
    size: Dp = 20.dp,
) {
    if (kind == FocusBarSlotKind.COLORS) {
        val hairline = ModernTheme.hairlineBorder(chromeIsDark())
        val dotSize = if (size <= 16.dp) 4.dp else 7.dp
        val dotGap = if (size <= 16.dp) 1.5.dp else 2.dp
        Column(verticalArrangement = Arrangement.spacedBy(dotGap), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary).forEach { c ->
                Box(Modifier.size(dotSize).clip(CircleShape).background(c).border(0.5.dp, hairline, CircleShape))
            }
        }
        return
    }
    Icon(
        imageVector = focusSlotGlyphVector(kind),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun focusSlotGlyphVector(kind: FocusBarSlotKind): ImageVector = when (kind) {
    FocusBarSlotKind.PEN -> EditorTool.PEN.icon
    FocusBarSlotKind.HIGHLIGHTER -> EditorTool.HIGHLIGHTER.icon
    FocusBarSlotKind.ERASER -> EditorTool.ERASER_WHOLE.icon
    FocusBarSlotKind.HAND -> EditorTool.HAND.icon
    FocusBarSlotKind.SELECT -> EditorTool.SELECT.icon
    FocusBarSlotKind.UNDO -> ChromeGlyph.UNDO.imageVector()
    FocusBarSlotKind.REDO -> ChromeGlyph.REDO.imageVector()
    FocusBarSlotKind.TEXT -> EditorTool.TEXT.icon
    FocusBarSlotKind.IMAGE -> EditorTool.IMAGE.icon
    FocusBarSlotKind.VSPACE -> EditorTool.VERTICAL_SPACE.icon
    FocusBarSlotKind.PAGES -> ChromeGlyph.DESCRIPTION.imageVector()
    FocusBarSlotKind.LAYERS -> ChromeGlyph.LAYERS.imageVector()
    FocusBarSlotKind.LINE -> EditorTool.LINE.icon
    FocusBarSlotKind.RECTANGLE -> EditorTool.RECTANGLE.icon
    FocusBarSlotKind.ELLIPSE -> EditorTool.ELLIPSE.icon
    FocusBarSlotKind.TRIANGLE -> EditorTool.TRIANGLE.icon
    FocusBarSlotKind.TRAPEZOID -> EditorTool.TRAPEZOID.icon
    FocusBarSlotKind.POLYGON -> EditorTool.POLYGON.icon
    FocusBarSlotKind.ARROW -> EditorTool.ARROW.icon
    FocusBarSlotKind.AXIS -> EditorTool.COORDINATE_AXIS.icon
    FocusBarSlotKind.GRAPH -> EditorTool.PLOT.icon
    FocusBarSlotKind.TABLE -> EditorTool.TABLE.icon
    FocusBarSlotKind.CIRCUIT -> EditorTool.RESISTOR.icon
    FocusBarSlotKind.CIRCUIT_ACTIVE -> EditorTool.DIODE.icon
    FocusBarSlotKind.LOGIC -> EditorTool.AND_GATE.icon
    FocusBarSlotKind.GUIDES -> guideButtonIcon(GuideKind.NONE)
    FocusBarSlotKind.COLORS -> EditorTool.PEN.icon
}
