package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import kotlin.math.roundToInt

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
    SettingsGroup("Default size") { DefaultFigureSize(settings, onChange) }
    SettingsGroup("Triangle") { TriangleGeometry(settings, onChange) }
    SettingsGroup("Trapezoid") { TrapezoidGeometry(settings, onChange) }
    SettingsGroup("Table") { TableDefaults(settings, onChange) }
    SettingsGroup("Figures shown") { FigureList(settings, onChange) }
}

/** The pen width slot a new figure starts at. */
@Composable
private fun DefaultFigureSize(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsNote(
        "A figure is drawn at this width with no pressure, so with the pen's pressure sensitivity on " +
            "it can look thicker than a light pen stroke — pick a smaller slot here to close that gap, " +
            "or turn pressure sensitivity off under Stylus and the pen and the figures match exactly.",
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
}

/** Default rows/columns for inserted tables, and the relational header line. */
@Composable
private fun TableDefaults(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ControlHeader("Grid", "Default number of rows and columns for inserted tables.", Modifier.padding(top = 8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Stepper("Rows", settings.tableRows, "rows") { onChange(settings.copy(tableRows = it)) }
        Spacer(Modifier.width(16.dp))
        Stepper("Cols", settings.tableCols, "columns") { onChange(settings.copy(tableCols = it)) }
    }
    SettingsDivider()
    SwitchRow(
        title = "Header row (relational)",
        subtitle = "Draw a double separator line under the first row for relational schema attributes.",
        checked = settings.tableHeader,
        onCheckedChange = { onChange(settings.copy(tableHeader = it)) },
    )
}

/** A label with −/+ buttons stepping [value] within 1..50. */
@Composable
private fun Stepper(label: String, value: Int, noun: String, onValue: (Int) -> Unit) {
    Text("$label: $value")
    IconButton(onClick = { onValue((value - 1).coerceAtLeast(1)) }, enabled = value > 1) {
        Icon(Icons.Filled.Remove, contentDescription = "Fewer $noun")
    }
    IconButton(onClick = { onValue((value + 1).coerceAtMost(50)) }, enabled = value < 50) {
        Icon(Icons.Filled.Add, contentDescription = "More $noun")
    }
}

/** The triangle tool's default variant, with the custom-angles dialog for scalene. */
@Composable
private fun TriangleGeometry(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        ScaleneAnglesDialog(
            angleA = settings.scaleneAngleA,
            angleB = settings.scaleneAngleB,
            angleC = settings.scaleneAngleC,
            onConfirm = { a, b, c ->
                showDialog = false
                onChange(
                    settings.copy(
                        triangleKind = TriangleKind.SCALENE,
                        scaleneAngleA = a,
                        scaleneAngleB = b,
                        scaleneAngleC = c,
                    )
                )
            },
            onDismiss = { showDialog = false },
        )
    }
    OptionGroup(
        title = "Type",
        subtitle = "Default variant for the triangle tool: equilateral (default), right-angled, isosceles, or scalene.",
        options = TriangleKind.values().toList(),
        selected = settings.triangleKind,
        label = { it.label },
        onSelect = { onChange(settings.copy(triangleKind = it)) },
    )
    if (settings.triangleKind == TriangleKind.SCALENE) {
        CustomAnglesRow(
            "Angles: A=${settings.scaleneAngleA.roundToInt()}°, B=${settings.scaleneAngleB.roundToInt()}°, " +
                "C=${settings.scaleneAngleC.roundToInt()}°",
        ) { showDialog = true }
    }
}

/** The trapezoid tool's default variant, with the custom-angles dialog for scalene. */
@Composable
private fun TrapezoidGeometry(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        TrapezoidAnglesDialog(
            angleA = settings.trapezoidAngleA,
            angleB = settings.trapezoidAngleB,
            onConfirm = { a, b ->
                showDialog = false
                onChange(
                    settings.copy(
                        trapezoidKind = TrapezoidKind.SCALENE,
                        trapezoidAngleA = a,
                        trapezoidAngleB = b,
                    )
                )
            },
            onDismiss = { showDialog = false },
        )
    }
    OptionGroup(
        title = "Type",
        subtitle = "Default variant for the trapezoid tool: isosceles (default), right-angled, or scalene.",
        options = TrapezoidKind.values().toList(),
        selected = settings.trapezoidKind,
        label = { it.label },
        onSelect = { onChange(settings.copy(trapezoidKind = it)) },
    )
    if (settings.trapezoidKind == TrapezoidKind.SCALENE) {
        CustomAnglesRow(
            "Base angles: left=${settings.trapezoidAngleA.roundToInt()}°, " +
                "right=${settings.trapezoidAngleB.roundToInt()}°",
        ) { showDialog = true }
    }
}

/** The current custom angles and the button that opens their editor. */
@Composable
private fun CustomAnglesRow(summary: String, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Customize angles")
        }
    }
}

/**
 * Which figures the Shapes submenu and the Secondary Toolbar offer, and in what order. A reorder or
 * hide here is written through to the Secondary Toolbar's own order/hidden lists.
 */
@Composable
private fun FigureList(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsNote(
        "The geometric figures offered in the Secondary Toolbar and shapes menu. Switch a figure off " +
            "to hide it, or press and hold a row and drag it to reorder.",
    )
    ReorderableRowList(
        items = orderedShapeTools(settings.shapeOrder).map { RailItem(it.name, it.label) },
        hidden = settings.shapeHidden,
        onOrder = { newShapeOrder ->
            val figureTopBarIds = newShapeOrder.mapNotNull { name ->
                runCatching { EditorTool.valueOf(name) }.getOrNull()?.let { FIGURE_TOOL_TO_TOP_BAR_ID[it] }
            }
            val nonFigureTopBarIds = settings.topBarOrder.filterNot { it in TOP_BAR_ID_TO_FIGURE_TOOL }
            val newTopBarOrder = figureTopBarIds + nonFigureTopBarIds
            onChange(settings.copy(shapeOrder = newShapeOrder, topBarOrder = newTopBarOrder))
        },
        onHidden = { newShapeHidden ->
            val hiddenTopBarIds = newShapeHidden.mapNotNull { name ->
                runCatching { EditorTool.valueOf(name) }.getOrNull()?.let { FIGURE_TOOL_TO_TOP_BAR_ID[it] }
            }.toSet()
            val newTopBarHidden = (settings.topBarHidden - TOP_BAR_ID_TO_FIGURE_TOOL.keys) + hiddenTopBarIds
            onChange(settings.copy(shapeHidden = newShapeHidden, topBarHidden = newTopBarHidden))
        },
    )
}
