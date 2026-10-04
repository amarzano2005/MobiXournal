package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The figure (shape) tools: the default size a new figure starts at, and the order / visibility of
 * the rail's **Shapes** submenu.
 *
 * The default size lives here because a figure is drawn at its set width with **no** pressure, while
 * a pen stroke thins with pressure — so with the pen's pressure sensitivity on, a figure can read
 * thicker than a light pen stroke. Picking a smaller slot here closes that gap; switching pressure
 * sensitivity off (under **Stylus**) makes the pen draw at the set width too, so the two match exactly.
 */
@Composable
fun FiguresSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    Text("Default figure size", style = MaterialTheme.typography.bodyLarge)
    Text(
        "The size a new figure starts at. A figure is drawn at this width with no pressure, so with " +
            "the pen's pressure sensitivity on it can look thicker than a light pen stroke — pick a " +
            "smaller slot here to close that gap, or turn pressure sensitivity off under Stylus and " +
            "the pen and the figures match exactly.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    OptionGroup(
        title = "Slot",
        subtitle = "Reuses the pen's S/M/L width slots — edit them from any tool's Colour & size popup.",
        options = settings.penWidths.indices.toList(),
        selected = settings.defaultShapeSlot,
        label = { i ->
            val name = PEN_WIDTH_LABELS.getOrNull(i) ?: "${i + 1}"
            "$name — ${settings.penWidths.getOrElse(i) { 0f }} pt"
        },
        onSelect = { i ->
            // Picking the slot both records the default and applies it to the live figure width, so
            // the change is visible at once rather than only on the next new document.
            onChange(settings.copy(defaultShapeSlot = i, shapeWidth = settings.penWidths[i]))
        },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Table grid", style = MaterialTheme.typography.bodyLarge)
    Text(
        "Default number of rows and columns for inserted tables.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Rows: ${settings.tableRows}")
        IconButton(
            onClick = { onChange(settings.copy(tableRows = (settings.tableRows - 1).coerceAtLeast(1))) },
            enabled = settings.tableRows > 1,
        ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer rows") }
        IconButton(
            onClick = { onChange(settings.copy(tableRows = (settings.tableRows + 1).coerceAtMost(50))) },
            enabled = settings.tableRows < 50,
        ) { Icon(Icons.Filled.Add, contentDescription = "More rows") }

        Spacer(Modifier.width(16.dp))

        Text("Cols: ${settings.tableCols}")
        IconButton(
            onClick = { onChange(settings.copy(tableCols = (settings.tableCols - 1).coerceAtLeast(1))) },
            enabled = settings.tableCols > 1,
        ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer columns") }
        IconButton(
            onClick = { onChange(settings.copy(tableCols = (settings.tableCols + 1).coerceAtMost(50))) },
            enabled = settings.tableCols < 50,
        ) { Icon(Icons.Filled.Add, contentDescription = "More columns") }
    }

    Spacer(Modifier.height(8.dp))
    SwitchRow(
        title = "Table header (relational)",
        subtitle = "Draw a double separator line under the first row for relational schema attributes.",
        checked = settings.tableHeader,
        onCheckedChange = { onChange(settings.copy(tableHeader = it)) },
    )

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Shapes submenu", style = MaterialTheme.typography.bodyLarge)
    Text(
        "The figures offered by the rail's Shapes slot. Switch one off to hide it, or press and " +
            "hold a row and drag it up or down to reorder. The picker lists them in this order.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    ReorderableRowList(
        items = orderedShapeTools(settings.shapeOrder).map { RailItem(it.name, it.label) },
        hidden = settings.shapeHidden,
        onOrder = { onChange(settings.copy(shapeOrder = it)) },
        onHidden = { onChange(settings.copy(shapeHidden = it)) },
    )
}
