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
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.TrapezoidKind
import com.mobixournal.render.TriangleKind
import com.mobixournal.ui.theme.DialogPresence
import kotlin.math.roundToInt

/**
 * The figure (shape) tools: the default size a new figure starts at, and each tool's default
 * variant.
 *
 * The default size lives here because a figure is drawn at its set width with **no** pressure, while
 * a pen stroke thins with pressure — so with the pen's pressure sensitivity on, a figure can read
 * thicker than a light pen stroke. Picking a smaller slot here closes that gap; switching pressure
 * sensitivity off (under **Stylus**) makes the pen draw at the set width too, so the two match exactly.
 *
 * Which figures the Shapes submenu shows, and in what order, is **not** here: it is the Secondary
 * Toolbar's own subject, so it lives once under **Toolbar** (see `ToolbarSection`) rather than being
 * edited from two places.
 */
@Composable
fun FiguresSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    SettingsGroup("Default size") { DefaultFigureSize(settings, onChange) }
    SettingsGroup("Triangle") { TriangleGeometry(settings, onChange) }
    SettingsGroup("Trapezoid") { TrapezoidGeometry(settings, onChange) }
    SettingsGroup("Polygon") { PolygonGeometry(settings, onChange) }
    SettingsGroup("Table") { TableDefaults(settings, onChange) }
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

/**
 * A label with −/+ buttons stepping [value] within [min]..[max] — 1..50 by default, which is what a
 * table's rows and columns want; a figure bounded tighter brings its own ends.
 */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    noun: String,
    min: Int = 1,
    max: Int = 50,
    onValue: (Int) -> Unit,
) {
    Text("$label: $value")
    IconButton(onClick = { onValue((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
        Icon(Icons.Filled.Remove, contentDescription = "Fewer $noun")
    }
    IconButton(onClick = { onValue((value + 1).coerceAtMost(max)) }, enabled = value < max) {
        Icon(Icons.Filled.Add, contentDescription = "More $noun")
    }
}

/**
 * The generic polygon tool's side count. It is the one figure whose shape count is the user's, so the
 * number lives here instead of one toolbar button per polygon: the pentagon and the hexagon stay in
 * the Polygons slot as the two presets a hand reaches for most.
 */
@Composable
private fun PolygonGeometry(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    ControlHeader(
        "Sides",
        "How many sides the Polygon tool draws, from a triangle up to a dodecagon. " +
            "The pentagon and hexagon slots keep their own five and six.",
        Modifier.padding(top = 8.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Stepper(
            label = "Polygon",
            value = settings.polygonSides,
            noun = "sides",
            min = ShapeBuilder.MIN_POLYGON_SIDES,
            max = ShapeBuilder.MAX_POLYGON_SIDES,
        ) { onChange(settings.copy(polygonSides = it)) }
    }
}

/** The triangle tool's default variant, with the custom-angles dialog for scalene. */
@Composable
private fun TriangleGeometry(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    DialogPresence(showDialog) {
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
        icon = { triangleKindIcon(it) },
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
    DialogPresence(showDialog) {
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
        icon = { trapezoidKindIcon(it) },
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
