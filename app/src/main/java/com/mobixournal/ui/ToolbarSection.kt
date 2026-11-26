package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Toolbar layout: where the Main Toolbar is docked, and which buttons the Main and Secondary
 * toolbars show in what order. The Secondary Toolbar is always on screen — there is no switch to hide
 * it; emptying it is done by switching its buttons off.
 *
 * There is **one layout**: the vertical rail plus the STEM row in the top bar. The rail offers
 * writing tools only, so there is no "hide the duplicates" shortcut to offer either.
 */
@Composable
fun ToolbarSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Main Toolbar") {
        OptionGroup(
            title = "Position",
            subtitle = "Keep it opposite your writing hand: Left for right-handed, Right for left-handed.",
            options = listOf(ToolbarPosition.LEFT, ToolbarPosition.RIGHT),
            selected = if (settings.toolbarPosition == ToolbarPosition.RIGHT) ToolbarPosition.RIGHT else ToolbarPosition.LEFT,
            label = { it.label },
            onSelect = { onChange(settings.copy(toolbarPosition = it)) },
        )
    }
    SettingsGroup("Main Toolbar buttons") {
        MainToolbarButtons(settings, onChange)
    }
    SettingsGroup("Secondary Toolbar buttons") {
        SecondaryToolbarButtons(settings, onChange)
    }
}

/** The Main Toolbar's reorderable button list, with user-editable dividers. */
@Composable
internal fun MainToolbarButtons(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsNote("Toggle switch to show or hide; hold and drag to reorder tools or dividers. Pages and Layers remain pinned at bottom.")
    val items = orderedRailItems(settings.railOrder).filterNot { it.id in PINNED_RAIL_IDS }
    val currentIds = items.map { it.id }
    ReorderableRowList(
        items = items,
        hidden = settings.railHidden,
        onOrder = { onChange(settings.copy(railOrder = it)) },
        onHidden = { onChange(settings.copy(railHidden = it)) },
        onDelete = { id -> onChange(settings.copy(railOrder = currentIds - id)) },
    )
    AddDividerRow {
        onChange(settings.copy(railOrder = currentIds + nextDividerId(currentIds)))
    }
}

/**
 * The Secondary Toolbar's reorderable button list (tools and movable dividers). Its figure buttons
 * mirror the Shapes submenu, so a reorder or hide here is written through to
 * [AppSettings.shapeOrder] / [AppSettings.shapeHidden].
 */
@Composable
internal fun SecondaryToolbarButtons(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsNote("Figures, tools, and dividers in the top bar. Switch a tool off to hide it; hold a row to reorder.")
    val items = orderedTopBarItems(settings.topBarOrder)
    val currentIds = items.map { it.id }
    ReorderableRowList(
        items = items,
        hidden = settings.topBarHidden,
        onOrder = { newTopBarOrder ->
            val figureToolNames = newTopBarOrder.mapNotNull { id ->
                TOP_BAR_ID_TO_FIGURE_TOOL[id]?.name
            }
            val nonFigureShapeNames = settings.shapeOrder.filterNot { name ->
                runCatching { EditorTool.valueOf(name) }.getOrNull() in FIGURE_TOOL_TO_TOP_BAR_ID
            }
            val newShapeOrder = figureToolNames + nonFigureShapeNames
            onChange(settings.copy(topBarOrder = newTopBarOrder, shapeOrder = newShapeOrder))
        },
        onHidden = { newTopBarHidden ->
            val hiddenShapeNames = newTopBarHidden.mapNotNull { id ->
                TOP_BAR_ID_TO_FIGURE_TOOL[id]?.name
            }.toSet()
            val figureNames = FIGURE_TOOL_TO_TOP_BAR_ID.keys.map { it.name }.toSet()
            val newShapeHidden = (settings.shapeHidden - figureNames) + hiddenShapeNames
            onChange(settings.copy(topBarHidden = newTopBarHidden, shapeHidden = newShapeHidden))
        },
        onDelete = { id -> onChange(settings.copy(topBarOrder = currentIds - id)) },
    )
    AddDividerRow {
        onChange(settings.copy(topBarOrder = currentIds + nextDividerId(currentIds)))
    }
}

@Composable
private fun AddDividerRow(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(onClick = onAdd) {
            Text("Add divider")
        }
    }
}
