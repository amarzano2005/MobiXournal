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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeInk

/**
 * Organized Liquid Glass dialog opened by long-pressing the Focus floating bar's drag handle.
 * Keeps the interactive preview strip, status/reorder row, and category filters fixed above a
 * scrollable icon+label catalog so users can customize 3–10 slots without leaving Focus mode.
 */
@Composable
internal fun FocusBarCustomizeDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
    onShowGuide: () -> Unit = {},
) {
    val slots = resolveFocusBarSlots(settings.focusBarSlots)
    var picked by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FocusBarCategoryFilter.ALL) }
    val isAdding = adding && slots.size < FOCUS_BAR_MAX_SLOTS
    val index = picked.coerceIn(0, (slots.size - 1).coerceAtLeast(0))

    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { FocusBarCustomizeTitle(onShowGuide = onShowGuide) },
        text = {
            FocusBarCustomizeBody(
                slots = slots,
                index = index,
                isAdding = isAdding,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it },
                onSelectSlot = { picked = it; adding = false },
                onSelectAdd = { adding = true },
                onMoveSlot = { delta ->
                    val moved = moveFocusBarSlot(slots, index, delta)
                    onSettingsChange(settings.copy(focusBarSlots = moved.map { it.id }))
                    picked = (index + delta).coerceIn(0, moved.lastIndex)
                },
                onReorderSlot = { from, to ->
                    val moved = reorderFocusBarSlot(slots, from, to)
                    onSettingsChange(settings.copy(focusBarSlots = moved.map { it.id }))
                    picked = to.coerceIn(0, moved.lastIndex)
                    adding = false
                },
                onRemoveSlot = { removeIdx ->
                    val next = updateFocusBarSlotList(slots, removeIdx, null)
                    onSettingsChange(settings.copy(focusBarSlots = next.map { it.id }))
                    picked = removeIdx.coerceAtMost(next.lastIndex)
                    adding = false
                },
                onPickTool = { kind ->
                    if (isAdding) {
                        val next = addFocusBarSlot(slots, kind)
                        if (next != slots) {
                            onSettingsChange(settings.copy(focusBarSlots = next.map { it.id }))
                            picked = next.lastIndex
                            adding = false
                        }
                    } else {
                        val next = updateFocusBarSlotList(slots, index, kind)
                        onSettingsChange(settings.copy(focusBarSlots = next.map { it.id }))
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (settings.focusBarSlots.isNotEmpty() || settings.focusBarAnchor.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onSettingsChange(settings.copy(focusBarSlots = emptyList(), focusBarAnchor = ""))
                        picked = 0
                        adding = false
                    },
                ) {
                    Text("Reset to defaults")
                }
            }
        },
    )
}

@Composable
private fun FocusBarCustomizeTitle(onShowGuide: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Focus floating bar", modifier = Modifier.weight(1f))
        IconButton(onClick = onShowGuide, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "Focus mode guide",
                tint = rememberChromeInk().copy(alpha = 0.78f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FocusBarCustomizeBody(
    slots: List<FocusBarSlotKind>,
    index: Int,
    isAdding: Boolean,
    selectedFilter: FocusBarCategoryFilter,
    onSelectFilter: (FocusBarCategoryFilter) -> Unit,
    onSelectSlot: (Int) -> Unit,
    onSelectAdd: () -> Unit,
    onMoveSlot: (Int) -> Unit,
    onReorderSlot: (from: Int, to: Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onPickTool: (FocusBarSlotKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FocusBarSlotPreviewStrip(
                slots = slots,
                selectedIndex = index,
                onSelect = onSelectSlot,
                onRemove = onRemoveSlot,
                onReorder = onReorderSlot,
                addingSelected = isAdding,
                onSelectAdd = onSelectAdd,
            )
        }
        FocusBarSlotStatusRow(
            index = index,
            total = slots.size,
            selected = slots[index],
            isAdding = isAdding,
            onMove = onMoveSlot,
        )
        FocusBarCategoryFilterRow(
            selected = selectedFilter,
            onSelect = onSelectFilter,
        )
        HorizontalDivider(color = ModernTheme.hairlineBorder(chromeIsDark()))
        FocusBarFilteredCatalog(
            slots = slots,
            selected = if (isAdding) null else slots[index],
            isAdding = isAdding,
            filter = selectedFilter,
            onPick = onPickTool,
        )
    }
}

@Composable
private fun FocusBarSlotStatusRow(
    index: Int,
    total: Int,
    selected: FocusBarSlotKind,
    isAdding: Boolean,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (isAdding) "Add a tool" else "Slot ${index + 1} · ${selected.label}",
                style = MaterialTheme.typography.labelLarge,
                color = rememberChromeInk(),
            )
            Text(
                text = if (isAdding) {
                    "Tap an unused tool below · $total / $FOCUS_BAR_MAX_SLOTS slots"
                } else {
                    "$total / $FOCUS_BAR_MAX_SLOTS slots · Hold & drag to reorder"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isAdding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = index > 0, onClick = { onMove(-1) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Move left")
                }
                IconButton(enabled = index < total - 1, onClick = { onMove(+1) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Move right")
                }
            }
        }
    }
}

@Composable
private fun FocusBarCategoryFilterRow(
    selected: FocusBarCategoryFilter,
    onSelect: (FocusBarCategoryFilter) -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val dark = chromeIsDark()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (filter in FocusBarCategoryFilter.entries) {
            val active = filter == selected
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    )
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) MaterialTheme.colorScheme.primary else ModernTheme.hairlineBorder(dark),
                        shape = shape,
                    )
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else rememberChromeInk(),
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusBarFilteredCatalog(
    slots: List<FocusBarSlotKind>,
    selected: FocusBarSlotKind?,
    isAdding: Boolean,
    filter: FocusBarCategoryFilter,
    onPick: (FocusBarSlotKind) -> Unit,
) {
    val items = remember(filter) { filterFocusBarSlots(filter) }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filter == FocusBarCategoryFilter.ALL) {
            val grouped = remember(items) { items.groupBy { it.category } }
            grouped.forEach { (category, groupItems) ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                FocusBarFilterChipFlow(
                    items = groupItems,
                    slots = slots,
                    selected = selected,
                    isAdding = isAdding,
                    onPick = onPick,
                )
            }
        } else {
            FocusBarFilterChipFlow(
                items = items,
                slots = slots,
                selected = selected,
                isAdding = isAdding,
                onPick = onPick,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusBarFilterChipFlow(
    items: List<FocusBarSlotKind>,
    slots: List<FocusBarSlotKind>,
    selected: FocusBarSlotKind?,
    isAdding: Boolean,
    onPick: (FocusBarSlotKind) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { kind ->
            val isCurrent = kind == selected
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
