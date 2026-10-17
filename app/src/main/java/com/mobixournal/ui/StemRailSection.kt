package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Color
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark

/** One 1-tap STEM entry: a tool to activate, or (tool == null) the function-plot dialog. */
internal data class StemEntry(val label: String, val icon: ImageVector, val tool: EditorTool?)

/** Number of 1-tap STEM buttons the rail reserves room for. */
internal const val STEM_ENTRY_COUNT = 4

/** The 1-tap STEM section: f(x), Geometry, Table, Circuit. */
internal val STEM_ENTRIES: List<StemEntry> = listOf(
    StemEntry("Function plot f(x)", CoordinateAxisIcon, null),
    StemEntry("Geometry", EditorTool.TRIANGLE.icon, EditorTool.TRIANGLE),
    StemEntry("Table", EditorTool.TABLE.icon, EditorTool.TABLE),
    StemEntry("Circuit", EditorTool.RESISTOR.icon, EditorTool.RESISTOR),
)

/** Whether [tool] is the live one for [entry] (geometry/circuit cover their whole families). */
internal fun stemEntryActive(entry: StemEntry, tool: EditorTool, showGraphDialog: Boolean = false): Boolean {
    if (entry.label.startsWith("Function plot")) return showGraphDialog
    val t = entry.tool ?: return false
    val group = TOOL_GROUPS.firstOrNull { t in it.tools }
    return tool == t || (group != null && tool in group.tools)
}

/**
 * The accent-tinted STEM capsule on the rail: every button is one tap, no sub-menus, and a hairline
 * divider separates it from the inking tools.
 */
@Composable
internal fun StemRailSection(
    tool: EditorTool,
    onTool: (EditorTool) -> Unit,
    onShowGraph: () -> Unit,
    horizontal: Boolean = false,
    leadingDivider: Boolean = false,
    trailingDivider: Boolean = true,
    showGraphDialog: Boolean = false,
) {
    val slot = LocalRailSlotSize.current
    val isDark = chromeIsDark()
    val hairlineColor = ModernTheme.hairlineBorder(isDark)
    if (horizontal) {
        if (leadingDivider) {
            VerticalDivider(color = hairlineColor, thickness = ModernTheme.hairlineStrokeWidthDp)
        }
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (entry in STEM_ENTRIES) {
                val active = stemEntryActive(entry, tool, showGraphDialog)
                Box(
                    modifier = Modifier
                        .size(slot)
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable { entry.tool?.let(onTool) ?: onShowGraph() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = "STEM: ${entry.label}",
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(slot / 2),
                    )
                }
            }
        }
        if (trailingDivider) {
            VerticalDivider(color = hairlineColor, thickness = ModernTheme.hairlineStrokeWidthDp)
        }
    } else {
        if (leadingDivider) {
            HorizontalDivider(color = hairlineColor, thickness = ModernTheme.hairlineStrokeWidthDp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (entry in STEM_ENTRIES) {
                val active = stemEntryActive(entry, tool, showGraphDialog)
                Box(
                    modifier = Modifier
                        .size(slot)
                        .clip(RoundedCornerShape(10.dp))
                        .then(if (active) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable { entry.tool?.let(onTool) ?: onShowGraph() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = "STEM: ${entry.label}",
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(slot / 2),
                    )
                }
            }
        }
        if (trailingDivider) {
            HorizontalDivider(color = hairlineColor, thickness = ModernTheme.hairlineStrokeWidthDp)
        }
    }
}
