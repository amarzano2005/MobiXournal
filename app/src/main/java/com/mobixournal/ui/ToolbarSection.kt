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
 */
@Composable
fun ToolbarSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Main Toolbar") {
        OptionGroup(
            title = "Position",
            subtitle = "Which edge the Main Toolbar is docked to.",
            options = ToolbarPosition.values().toList(),
            selected = settings.toolbarPosition,
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

/** The Main Toolbar's reorderable button list, plus the shortcut that hides what the Secondary shows. */
@Composable
private fun MainToolbarButtons(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsNote(
        "Switch a button off to hide it, or press and hold a row and drag it to reorder. " +
            "The Main Toolbar draws them top-to-bottom.",
    )
    ReorderableRowList(
        items = orderedRailItems(settings.railOrder),
        hidden = settings.railHidden,
        onOrder = { onChange(settings.copy(railOrder = it)) },
        onHidden = { onChange(settings.copy(railHidden = it)) },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = {
                onChange(settings.copy(railHidden = settings.railHidden + AppSettings.DEFAULT_RAIL_HIDDEN))
            },
        ) {
            Text("Hide tools already in the Secondary Toolbar")
        }
    }
}

/**
 * The Secondary Toolbar's reorderable button list. Its figure buttons mirror the Shapes submenu, so
 * a reorder or hide here is written through to [AppSettings.shapeOrder] / [AppSettings.shapeHidden].
 */
@Composable
private fun SecondaryToolbarButtons(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsNote(
        "The geometric figures and tools shown in the top bar. Switch a button off to hide it, or " +
            "press and hold a row and drag it to reorder.",
    )
    ReorderableRowList(
        items = orderedTopBarItems(settings.topBarOrder),
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
    )
}
