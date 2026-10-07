package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Toolbar layout: where the Main Toolbar is docked, and which buttons the Main and Secondary toolbars show in what order. */
@Composable
fun ToolbarSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    OptionGroup(
        title = "Main Toolbar position",
        subtitle = "Which edge the Main Toolbar is docked to.",
        options = ToolbarPosition.values().toList(),
        selected = settings.toolbarPosition,
        label = { it.label },
        onSelect = { onChange(settings.copy(toolbarPosition = it)) },
    )

    Spacer(Modifier.height(8.dp))
    SwitchRow(
        title = "Show Secondary Toolbar (Dual toolbar)",
        subtitle = "Display geometric figures and drawing tools in the Secondary Toolbar within the top bar, keeping Main Toolbar and Secondary Toolbar accessible together.",
        checked = settings.showToolsInTopBar,
        onCheckedChange = { onChange(settings.copy(showToolsInTopBar = it)) },
    )

    Spacer(Modifier.height(12.dp))
    Text("Main Toolbar buttons", style = MaterialTheme.typography.titleSmall)
    Text(
        "Switch a button off to hide it, or press and hold a row and drag it up or down to reorder. " +
            "The Main Toolbar draws them top-to-bottom (left-to-right when docked horizontally).",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))

    ReorderableRowList(
        items = orderedRailItems(settings.railOrder),
        hidden = settings.railHidden,
        onOrder = { onChange(settings.copy(railOrder = it)) },
        onHidden = { onChange(settings.copy(railHidden = it)) },
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = {
                onChange(settings.copy(railHidden = settings.railHidden + AppSettings.DEFAULT_RAIL_HIDDEN))
            },
        ) {
            Text("Hide Secondary Toolbar tools in Main Toolbar")
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Secondary Toolbar buttons", style = MaterialTheme.typography.titleSmall)
    Text(
        "Customize the tools and geometric figures shown in the Secondary Toolbar. Switch a button off to hide it, or press and " +
            "hold a row and drag it up or down to reorder.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))

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
