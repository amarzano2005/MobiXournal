package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor

/**
 * **Focus mode** Settings page: live Liquid Glass preview of the Focus floating bar, slot-by-slot
 * customization (`OptionGroup`), slot reordering, adding/removing slots (3..6), and resetting
 * both tools and saved screen placement to defaults.
 */
@Composable
fun FocusModeSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val slots = resolveFocusBarSlots(settings.focusBarSlots)
    var picked by remember { mutableStateOf(0) }
    val index = picked.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val answers: List<FocusBarSlotKind?> = FocusBarSlotKind.entries +
        (if (slots.size > FOCUS_BAR_MIN_SLOTS) listOf(null) else emptyList())

    SettingsGroup("Floating bar") {
        SettingsNote(
            "Liquid Glass floating bar shown during Focus mode. Drag it to the left or right " +
                "edge to morph between horizontal and vertical. Tap a slot below to customize it.",
        )
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            FocusBarSlotPreviewStrip(slots = slots, selectedIndex = index, onSelect = { picked = it })
        }
        FocusBarSlotReorderBar(
            index = index,
            total = slots.size,
            onMove = { delta ->
                val moved = moveFocusBarSlot(slots, index, delta)
                onChange(settings.copy(focusBarSlots = moved.map { it.id }))
                picked = (index + delta).coerceIn(0, moved.lastIndex)
            },
        )
        SettingsDivider()
        OptionGroup<FocusBarSlotKind?>(
            title = "Slot ${index + 1} of ${slots.size}",
            subtitle = "What this position holds.",
            options = answers,
            selected = slots[index],
            label = { it?.label ?: "Remove slot" },
            section = { it?.category ?: "Leave it out" },
            onSelect = { chosen ->
                val next = updateFocusBarSlotList(slots, index, chosen)
                onChange(settings.copy(focusBarSlots = next.map { it.id }))
                picked = index.coerceAtMost(next.lastIndex)
            },
        )
        if (slots.size < FOCUS_BAR_MAX_SLOTS) {
            OutlinedButton(
                onClick = {
                    val next = appendFocusBarSlot(slots)
                    onChange(settings.copy(focusBarSlots = next.map { it.id }))
                    picked = next.lastIndex
                },
                shape = SettingsActionShape,
                modifier = Modifier.settingsActionPill(top = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a slot")
            }
        }
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
    index: Int,
    total: Int,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Position ${index + 1} of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            IconButton(enabled = index > 0, onClick = { onMove(-1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Move slot left")
            }
            IconButton(enabled = index < total - 1, onClick = { onMove(+1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Move slot right")
            }
        }
    }
}

/** Interactive Liquid Glass preview of the Focus bar slots shared with [FocusBarCustomizeDialog]. */
@Composable
internal fun FocusBarSlotPreviewStrip(
    slots: List<FocusBarSlotKind>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(ModernTheme.panelCornerRadiusDp)
    val ink = rememberChromeInk()
    Surface(
        shape = shape,
        color = rememberFloatingSurfaceColor(),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.floatingRim(ModernTheme.panelCornerRadiusDp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { idx, kind ->
                val selected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ModernTheme.railSlotShape)
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .then(
                            if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, ModernTheme.railSlotShape)
                            else Modifier,
                        )
                        .clickable { onSelect(idx) },
                    contentAlignment = Alignment.Center,
                ) {
                    FocusPreviewSlotGlyph(kind = kind, tint = if (selected) MaterialTheme.colorScheme.primary else ink)
                }
            }
        }
    }
}

@Composable
private fun FocusPreviewSlotGlyph(kind: FocusBarSlotKind, tint: Color) {
    when (kind) {
        FocusBarSlotKind.PEN -> Icon(EditorTool.PEN.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.HIGHLIGHTER -> Icon(EditorTool.HIGHLIGHTER.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.ERASER -> Icon(EditorTool.ERASER_WHOLE.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.HAND -> Icon(EditorTool.HAND.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.SELECT -> Icon(EditorTool.SELECT.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.UNDO -> Icon(ChromeGlyph.UNDO.imageVector(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.REDO -> Icon(ChromeGlyph.REDO.imageVector(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.TEXT -> Icon(EditorTool.TEXT.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.IMAGE -> Icon(EditorTool.IMAGE.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.VSPACE -> Icon(EditorTool.VERTICAL_SPACE.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        FocusBarSlotKind.COLORS -> {
            val hairline = ModernTheme.hairlineBorder(chromeIsDark())
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary).forEach { c ->
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c).border(0.5.dp, hairline, CircleShape))
                }
            }
        }
    }
}
