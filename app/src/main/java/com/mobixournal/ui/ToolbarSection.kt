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

/** Toolbar layout: where the rail is docked, and which buttons it shows in what order. */
@Composable
fun ToolbarSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    OptionGroup(
        title = "Toolbar position",
        subtitle = "Which edge the tool rail is docked to.",
        options = ToolbarPosition.values().toList(),
        selected = settings.toolbarPosition,
        label = { it.label },
        onSelect = { onChange(settings.copy(toolbarPosition = it)) },
    )

    Spacer(Modifier.height(8.dp))
    SwitchRow(
        title = "Show tools in top bar (Dual toolbar)",
        subtitle = "Display drawing tools in the empty space of the top app bar without shrinking the page, keeping tools and side rail accessible together.",
        checked = settings.showToolsInTopBar,
        onCheckedChange = { onChange(settings.copy(showToolsInTopBar = it)) },
    )

    Spacer(Modifier.height(12.dp))
    Text("Rail buttons", style = MaterialTheme.typography.titleSmall)
    Text(
        "Switch a button off to hide it, or press and hold a row and drag it up or down to reorder. " +
            "The rail draws them top-to-bottom (left-to-right when docked horizontally).",
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
            Text("Hide top bar tools in rail")
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Top bar buttons", style = MaterialTheme.typography.titleSmall)
    Text(
        "Customize the tools shown in the additional top bar. Switch a button off to hide it, or press and " +
            "hold a row and drag it up or down to reorder.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))

    ReorderableRowList(
        items = orderedTopBarItems(settings.topBarOrder),
        hidden = settings.topBarHidden,
        onOrder = { onChange(settings.copy(topBarOrder = it)) },
        onHidden = { onChange(settings.copy(topBarHidden = it)) },
    )
}
