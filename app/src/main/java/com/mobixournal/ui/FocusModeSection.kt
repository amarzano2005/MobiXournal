package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.GuideKind
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

/**
 * **Focus mode** Settings page: live preview of the Focus floating bar with integrated
 * reordering and removal, a categorized visual chip catalog for toggling tools (3..10 slots),
 * and a reset action for both tools and saved screen placement.
 */
@Composable
fun FocusModeSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val slots = resolveFocusBarSlots(settings.focusBarSlots)
    var picked by remember { mutableStateOf(0) }
    val index = picked.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val canRemove = slots.size > FOCUS_BAR_MIN_SLOTS

    SettingsGroup("Floating bar") {
        SettingsNote(
            "Floating bar shown during Focus mode. Drag it to the left or right " +
                "edge to switch between horizontal and vertical. Tap a slot to reorder or remove it.",
        )
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            FocusBarSlotPreviewStrip(
                slots = slots,
                selectedIndex = index,
                onSelect = { picked = it },
                onRemove = { removeIdx ->
                    val next = updateFocusBarSlotList(slots, removeIdx, null)
                    onChange(settings.copy(focusBarSlots = next.map { it.id }))
                    picked = removeIdx.coerceAtMost(next.lastIndex)
                },
            )
        }
        FocusBarSlotReorderBar(
            selectedLabel = slots[index].label,
            index = index,
            total = slots.size,
            canRemove = canRemove,
            onMove = { delta ->
                val moved = moveFocusBarSlot(slots, index, delta)
                onChange(settings.copy(focusBarSlots = moved.map { it.id }))
                picked = (index + delta).coerceIn(0, moved.lastIndex)
            },
            onRemove = {
                val next = updateFocusBarSlotList(slots, index, null)
                onChange(settings.copy(focusBarSlots = next.map { it.id }))
                picked = index.coerceAtMost(next.lastIndex)
            },
        )
        SettingsDivider()
        FocusBarToolCatalog(
            slots = slots,
            onToggle = { kind ->
                val wasPresent = kind in slots
                val next = toggleFocusBarSlot(slots, kind)
                if (next != slots) {
                    onChange(settings.copy(focusBarSlots = next.map { it.id }))
                    picked = if (!wasPresent) next.lastIndex else index.coerceAtMost(next.lastIndex)
                }
            },
        )
        if (settings.focusBarSlots.isNotEmpty() || settings.focusBarAnchor.isNotEmpty()) {
            TextButton(
                onClick = {
                    onChange(settings.copy(focusBarSlots = emptyList(), focusBarAnchor = ""))
                    picked = 0
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

@Composable
private fun FocusBarSlotReorderBar(
    selectedLabel: String,
    index: Int,
    total: Int,
    canRemove: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "$selectedLabel · ${index + 1} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = index > 0, onClick = { onMove(-1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Move slot left")
            }
            IconButton(enabled = index < total - 1, onClick = { onMove(+1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Move slot right")
            }
            IconButton(enabled = canRemove, onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove slot")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusBarToolCatalog(
    slots: List<FocusBarSlotKind>,
    onToggle: (FocusBarSlotKind) -> Unit,
) {
    val grouped = remember { FocusBarSlotKind.entries.groupBy { it.category } }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        ControlHeader(
            title = "Tools in bar",
            subtitle = "Tap a tool to add or remove it (${slots.size} of $FOCUS_BAR_MAX_SLOTS active).",
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
                    val active = kind in slots
                    val enabled = if (active) slots.size > FOCUS_BAR_MIN_SLOTS else slots.size < FOCUS_BAR_MAX_SLOTS
                    FocusBarToolChip(
                        kind = kind,
                        active = active,
                        enabled = enabled,
                        onClick = { onToggle(kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusBarToolChip(
    kind: FocusBarSlotKind,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val bgColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.45f else 0.22f)
    }
    val borderColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        ModernTheme.hairlineBorder(chromeIsDark())
    }
    val textTint = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.88f else 0.38f)
    }
    val iconTint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.78f else 0.38f)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
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
) {
    val shape = RoundedCornerShape(ModernTheme.panelCornerRadiusDp)
    val ink = rememberChromeInk()
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
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { idx, kind ->
                val selected = idx == selectedIndex
                FocusBarPreviewSlotBox(
                    kind = kind,
                    selected = selected,
                    showRemove = selected && showRemoveBadge,
                    ink = ink,
                    onSelect = { onSelect(idx) },
                    onRemove = { onRemove?.invoke(idx) },
                )
            }
        }
    }
}

@Composable
private fun FocusBarPreviewSlotBox(
    kind: FocusBarSlotKind,
    selected: Boolean,
    showRemove: Boolean,
    ink: Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier.size(42.dp),
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
