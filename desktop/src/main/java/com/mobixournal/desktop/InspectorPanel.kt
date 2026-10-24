package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.PlotShading
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.MathFlavor
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory
import com.mobixournal.render.TableElementFactory

/**
 * The **inspector lane**: the right-hand column that edits the living element a selection names.
 *
 * It is a lane, not a dialog (`docs/interface-contract.md`): 300 dp of its own column beside the page, so
 * nothing is ever laid over the sheet and the page keeps the rectangle that is left. [DesktopWorkspace]
 * puts it in the row, which is what makes that true mechanically — the lane takes width, the canvas takes
 * the remainder, and no glass floats on the ink.
 *
 * The sections are per family and only offer **fields the model carries**: a plot's formula, domain and
 * resolution; a table's grid, header and rules; a figure's read-only lengths and angles plus its outline
 * style; a component's label, value and stroke; a formula's source and language. A field that is not in the
 * element is not in the panel, because a value the model would drop is a lie about the file
 * (`docs/mobix-elements.md`). Every commit is one [replaceLiving] — one edit, one `Ctrl+Z` — and an
 * unchanged value writes nothing at all.
 *
 * `Esc` while a field has the caret cancels that field and puts it back; a second `Esc` closes the lane.
 */
@Composable
internal fun InspectorLane(
    state: DesktopShellState,
    tab: DesktopTab,
    modifier: Modifier = Modifier,
) {
    // A placement asking its values comes before any editor: the element does not exist yet.
    state.pendingPlacement?.let { pending ->
        PlacementCard(state, pending)
        return
    }
    val target = state.editingLiving ?: return
    val live = livingTarget(tab, target) ?: return
    Column(
        modifier
            .width(INSPECTOR_WIDTH)
            .fillMaxHeight()
            .bandSurface()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(live.family.title, color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.weight(1f))
            LaneClose { state.closeLivingEditor() }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (live.family) {
                LivingFamily.PLOT -> PlotSection(live.element as PlotElement) { state.commitLiving(it) }
                LivingFamily.TABLE -> TableSection(live.element as TableElement) { state.commitLiving(it) }
                LivingFamily.SHAPE -> ShapeSection(live.element as ParametricShapeElement) { state.commitLiving(it) }
                LivingFamily.CIRCUIT -> CircuitSection(live.element as CircuitElement) { state.commitLiving(it) }
                LivingFamily.MATH -> MathSection(live.element as MathBlockElement) { state.commitLiving(it) }
            }
        }
    }
}

/** The lane's width: `docs/interface-contract.md`'s inspector column. */
internal val INSPECTOR_WIDTH = 300.dp

// --- the placement question --------------------------------------------------------------------

/**
 * The lane's **placement card**: the question a living slot asks *before* its element exists.
 *
 * The tablet asks the same way (the graph dialog, the table grid, the component palette) and this is the
 * window's own form of it — in the lane, so the sheet is never covered by the question, and with the
 * family's own fields so the answer is exactly what the inspector would let you change afterwards. Nothing
 * is on the page until **Place** is pressed, so **Cancel** (or `Esc`) leaves no element and no undo step.
 */
@Composable
private fun PlacementCard(state: DesktopShellState, pending: PendingPlacement) {
    Column(
        Modifier
            .width(INSPECTOR_WIDTH)
            .fillMaxHeight()
            .bandSurface()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("New ${pending.slot.label().lowercase()}", color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.weight(1f))
            LaneClose { state.cancelPlacement() }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (pending.slot) {
                LivingSlot.PLOT -> PlotQuestion(pending)
                LivingSlot.TABLE -> TableQuestion(pending)
                // A component's part was the palette's question, so a circuit never reaches this card.
                LivingSlot.CIRCUIT -> SectionLabel("Component")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelButton("Place", primary = true, enabled = pending.ready, onClick = { state.confirmPlacement() })
            PanelButton("Cancel", primary = false, enabled = true, onClick = { state.cancelPlacement() })
        }
    }
}

/** The graph dialog's questions, in the lane: `f(x)`, the domain and the display flags. */
@Composable
private fun PlotQuestion(pending: PendingPlacement) {
    SectionLabel("Formula")
    Field("f(x)") {
        ValueField(pending.formula, onCommit = { pending.formula = it })
    }
    SectionLabel("Domain")
    NumberRow(
        left = { Field("x from") { ValueField(pending.xMin, numeric = true, onCommit = { pending.xMin = it }) } },
        right = { Field("x to") { ValueField(pending.xMax, numeric = true, onCommit = { pending.xMax = it }) } },
    )
    if (pending.domain == null) {
        Text("x to must be greater than x from", color = DesktopTheme.danger, style = DesktopTheme.label)
    }
    SectionLabel("Display")
    SwitchRow("Critical points", pending.showCriticalPoints) { pending.showCriticalPoints = it }
    SwitchRow("Derivative", pending.includeDerivative) { pending.includeDerivative = it }
    SwitchRow("Summary", pending.includeSummary) { pending.includeSummary = it }
    SwitchRow("Shade integral", pending.shade) { pending.shade = it }
    if (pending.shade) {
        NumberRow(
            left = { Field("shade from") { ValueField(pending.shadeFrom, numeric = true, onCommit = { pending.shadeFrom = it }) } },
            right = { Field("shade to") { ValueField(pending.shadeTo, numeric = true, onCommit = { pending.shadeTo = it }) } },
        )
    }
}

/** The table's questions: how many columns and rows, and whether the first row is a header. */
@Composable
private fun TableQuestion(pending: PendingPlacement) {
    SectionLabel("Grid")
    NumberRow(
        left = {
            NumberField("columns", pending.columns.toDouble()) { value ->
                pending.columns = value.toInt().coerceIn(1, 30)
            }
        },
        right = {
            NumberField("rows", pending.rows.toDouble()) { value ->
                pending.rows = value.toInt().coerceIn(1, 200)
            }
        },
    )
    SwitchRow("Header row", pending.header) { pending.header = it }
}

// --- the five sections -------------------------------------------------------------------------

@Composable
private fun PlotSection(plot: PlotElement, commit: (Element) -> Unit) {
    val curve = plot.curves.firstOrNull()
    SectionLabel("Formula")
    var formula by remember(plot) { mutableStateOf(curve?.source.orEmpty()) }
    ValueField(formula, onCommit = { entered ->
        formula = entered
        if (curve != null && entered.isNotBlank() && entered != curve.source) {
            commit(
                plot.copy(
                    curves = listOf(curve.copy(source = entered)) + plot.curves.drop(1),
                    summaryText = null,
                ),
            )
        }
    })
    SectionLabel("Domain")
    NumberRow(
        left = {
            NumberField("x min", plot.xMin) { value ->
                if (value < plot.xMax) commit(plot.copy(xMin = value))
            }
        },
        right = {
            NumberField("x max", plot.xMax) { value ->
                if (value > plot.xMin) commit(plot.copy(xMax = value))
            }
        },
    )
    SectionLabel("Resolution")
    NumberField("samples", plot.samples.toDouble()) { value ->
        val rounded = value.toInt()
        if (rounded in 8..4000 && rounded != plot.samples) commit(plot.copy(samples = rounded))
    }
    // The graph dialog's own switches, so the window's plot offers what the tablet's does. The summary
    // is a cache of the engine's analysis, so it is recomputed through PlotElementFactory (the same
    // path the tablet's edit takes) rather than left stale or dropped.
    SectionLabel("Display")
    SwitchRow("Critical points", plot.showCriticalPoints) { on -> commit(withFlags(plot, critical = on)) }
    SwitchRow("Derivative", plot.includeDerivative) { on -> commit(withFlags(plot, derivative = on)) }
    SwitchRow("Summary", plot.summaryText != null) { on -> commit(withFlags(plot, summary = on)) }
    SwitchRow("Shade integral", plot.shadeIntegral != null) { on ->
        commit(withFlags(plot, shade = if (on) PlotShading(plot.xMin, plot.xMax) else null))
    }
    plot.shadeIntegral?.let { shade ->
        NumberRow(
            left = {
                NumberField("shade from", shade.from) { value ->
                    if (value < shade.to) commit(withFlags(plot, shade = shade.copy(from = value)))
                }
            },
            right = {
                NumberField("shade to", shade.to) { value ->
                    if (value > shade.from) commit(withFlags(plot, shade = shade.copy(to = value)))
                }
            },
        )
    }
    SectionLabel("Stroke")
    NumberField("width pt", plot.curves.firstOrNull()?.strokeWidth ?: 1.0) { value ->
        if (curve != null && value > 0 && value != curve.strokeWidth) {
            commit(plot.copy(curves = listOf(curve.copy(strokeWidth = value)) + plot.curves.drop(1)))
        }
    }
    ColourRow { colour -> commit(plot.copy(axisColor = colour)) }
}

/**
 * [plot] with its display flags changed through the shared edit path, so the analysis cache is
 * recomputed exactly as a tablet edit would recompute it (an unedited field is preserved).
 */
private fun withFlags(
    plot: PlotElement,
    critical: Boolean = plot.showCriticalPoints,
    derivative: Boolean = plot.includeDerivative,
    summary: Boolean = plot.summaryText != null,
    shade: PlotShading? = plot.shadeIntegral,
): PlotElement = PlotElementFactory.applyPlotEdit(
    original = plot.copy(summaryText = plot.summaryText.takeIf { !summary }),
    editedCurves = plot.curves.map { curve ->
        PlotCurve(
            source = curve.source,
            color = curve.color,
            lineStyle = curve.lineStyle,
            kind = curve.kind,
            sourceY = curve.sourceY,
        )
    },
    xMin = plot.xMin,
    xMax = plot.xMax,
    showCriticalPoints = critical,
    includeDerivative = derivative,
    includeSummary = summary,
    shadeIntegral = shade,
)

@Composable
private fun TableSection(table: TableElement, commit: (Element) -> Unit) {
    SectionLabel("Grid")
    NumberRow(
        left = {
            NumberField("columns", table.colWidths.size.toDouble()) { value ->
                val columns = value.toInt().coerceIn(1, 30)
                if (columns != table.colWidths.size) commit(table.resized(columns, table.rowHeights.size))
            }
        },
        right = {
            NumberField("rows", table.rowHeights.size.toDouble()) { value ->
                val rows = value.toInt().coerceIn(1, 200)
                if (rows != table.rowHeights.size) commit(table.resized(table.colWidths.size, rows))
            }
        },
    )
    SectionLabel("Rules")
    SwitchRow("Header row", table.headerRow) { on -> commit(table.copy(headerRow = on)) }
    NumberField("border pt", table.borderWidth) { value ->
        if (value > 0 && value != table.borderWidth) commit(table.copy(borderWidth = value))
    }
    ColourRow { colour -> commit(table.copy(borderColor = colour)) }
    // The tablet writes a cell's content by tapping it; the lane writes it by naming it, with the same
    // cell in the same grid, so the two shells keep one table and one meaning per cell.
    SectionLabel("Cells")
    for (row in table.rowHeights.indices) {
        for (column in table.colWidths.indices) {
            val cell = table.cell(row, column)
            Field("${row + 1},${column + 1}") {
                ValueField(cell.text, onCommit = { entered ->
                    if (entered != cell.text) {
                        commit(TableElementFactory.updateCell(table, row, column, cell.copy(text = entered)))
                    }
                })
            }
        }
    }
}

@Composable
private fun ShapeSection(shape: ParametricShapeElement, commit: (Element) -> Unit) {
    SectionLabel("Geometry")
    for (index in shape.vertices.indices) {
        val side = ParametricShapes.sideLengthPt(shape, index)
        val angle = ParametricShapes.interiorAngleDeg(shape, index)
        Readout("side ${index + 1}", "${side.roundToTenth()} pt · ${angle.roundToTenth()}°")
    }
    SectionLabel("Outline")
    NumberField("width pt", shape.strokeWidth) { value ->
        if (value > 0 && value != shape.strokeWidth) commit(shape.copy(strokeWidth = value))
    }
    ColourRow { colour -> commit(shape.copy(strokeColor = colour)) }
}

@Composable
private fun CircuitSection(circuit: CircuitElement, commit: (Element) -> Unit) {
    SectionLabel("Component")
    Readout("symbol", circuit.symbol)
    var value by remember(circuit) { mutableStateOf(circuit.value.orEmpty()) }
    ValueField(value, onCommit = { entered ->
        value = entered
        commit(circuit.copy(value = entered.ifBlank { null }))
    })
    // The tablet's own two placement controls, as words: a quarter turn and a straighten, then the
    // side its label is written on (a four-way cycle, exactly the enum's four sides).
    SectionLabel("Placement")
    Readout("rotation", "${formatNumber(circuit.rotation)}°")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelButton("Rotate 90°", primary = false, enabled = true, onClick = {
            commit(circuit.copy(rotation = (circuit.rotation + 90.0) % 360.0))
        })
        PanelButton("Straighten", primary = false, enabled = true, onClick = {
            val straight = (kotlin.math.round(circuit.rotation / 90.0) * 90.0) % 360.0
            commit(circuit.copy(rotation = if (straight < 0.0) straight + 360.0 else straight))
        })
    }
    PanelButton("Label: ${labelSideName(circuit.labelSide)}", primary = false, enabled = true, onClick = {
        val sides = CircuitLabelSide.entries
        commit(circuit.copy(labelSide = sides[(circuit.labelSide.ordinal + 1) % sides.size]))
    })
    SectionLabel("Stroke")
    NumberField("width pt", circuit.strokeWidth) { width ->
        if (width > 0 && width != circuit.strokeWidth) commit(circuit.copy(strokeWidth = width))
    }
    ColourRow { colour -> commit(circuit.copy(strokeColor = colour)) }
}

/** A label side as the lane prints it: `ABOVE` reads as `Above`. */
private fun labelSideName(side: CircuitLabelSide): String =
    side.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun MathSection(block: MathBlockElement, commit: (Element) -> Unit) {
    SectionLabel("Source")
    var source by remember(block) { mutableStateOf(block.source) }
    ValueField(source, onCommit = { entered ->
        source = entered
        if (entered.isNotBlank() && entered != block.source) commit(block.copy(source = entered))
    }, multiline = true)
    SectionLabel("Language")
    SwitchRow("Typst", block.flavor == MathFlavor.TYPST) { typst ->
        commit(block.copy(flavor = if (typst) MathFlavor.TYPST else MathFlavor.LATEX))
    }
    ColourRow { colour -> commit(block.copy(color = colour)) }
}

// --- the controls ------------------------------------------------------------------------------

/** A section's heading: the one step of hierarchy the lane has. */
@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    Spacer(Modifier.height(2.dp))
}

/** A value the panel only reports (a side's length, a component's symbol). */
@Composable
private fun Readout(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        Spacer(Modifier.weight(1f))
        Text(value, color = DesktopTheme.ink, style = DesktopTheme.numeric)
    }
}

@Composable
private fun NumberRow(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

/**
 * A number that commits on `Enter` or when the caret leaves — never per keystroke, because every commit is
 * an undo step and a half-typed `1` is not a value the user asked for.
 */
@Composable
private fun NumberField(label: String, value: Double, onCommit: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Field(label = label) {
        ValueField(
            initial = text,
            numeric = true,
            onCommit = { entered ->
                entered.toDoubleOrNull()?.let { parsed ->
                    text = formatNumber(parsed)
                    onCommit(parsed)
                }
            },
        )
    }
}

@Composable
private fun Field(label: String, field: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        Spacer(Modifier.height(2.dp))
        field()
    }
}

/**
 * The one text control of the lane: a field that looks like the card a living element's value sits on,
 * commits on `Enter` or focus loss, and puts itself back on `Esc`.
 */
@Composable
private fun ValueField(
    initial: String,
    numeric: Boolean = false,
    multiline: Boolean = false,
    onCommit: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = text,
        onValueChange = { entered ->
            if (!numeric || entered.isEmpty() || entered.toDoubleOrNull() != null || entered == "-" || entered == ".") {
                text = entered
            }
        },
        singleLine = !multiline,
        textStyle = DesktopTheme.numeric.copy(color = DesktopTheme.ink),
        cursorBrush = SolidColor(DesktopTheme.accent),
        modifier = Modifier
            .fillMaxWidth()
            .background(DesktopTheme.activeSlot.copy(alpha = 0.45f), DesktopTheme.railSlotShape)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .onFocusChanged { focus ->
                if (focused && !focus.isFocused) onCommit(text)
                focused = focus.isFocused
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        onCommit(text)
                        true
                    }
                    Key.Escape -> {
                        text = initial
                        true
                    }
                    else -> false
                }
            },
    )
}

/** A labelled switch, drawn as two words: the contract's control for a boolean field. */
@Composable
private fun SwitchRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(on) { detectTapGestures { onToggle(!on) } }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 26.dp, height = 14.dp)
                .background(if (on) DesktopTheme.accent else DesktopTheme.hairline, DesktopTheme.capsuleShape),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(horizontal = 2.dp).size(10.dp).background(Color.White, DesktopTheme.capsuleShape))
        }
    }
}

/** The palette, as a row of swatches: a colour field is a colour, not a hex box. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ColourRow(onPick: (Int) -> Unit) {
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        for (colour in PenPalette.COLORS.take(8)) {
            var hovered by remember(colour) { mutableStateOf(false) }
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .background(Color(colour), DesktopTheme.railSlotShape)
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .pointerInput(colour) { detectTapGestures { onPick(colour) } },
                contentAlignment = Alignment.Center,
            ) {
                if (hovered) {
                    Canvas(Modifier.size(6.dp)) {
                        drawCircle(Color.White, radius = size.minDimension / 2f)
                    }
                }
            }
        }
    }
}

@Composable
private fun LaneClose(onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .hoverFill(DesktopTheme.capsuleShape)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawLine(DesktopTheme.chromeInk, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.4f)
            drawLine(DesktopTheme.chromeInk, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1.4f)
        }
    }
}

/** A number as the lane prints it: an integer without a pointless `.0`, one decimal otherwise. */
private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

private fun Double.roundToTenth(): String = "%.1f".format(this)

/**
 * [table] with a new grid: the columns and rows that survive keep their widths, heights and cells, and the
 * grid still covers the box it had — the table's own rule (`colWidths`/`rowHeights` are its geometry, so a
 * new column divides the width rather than growing the table).
 */
internal fun TableElement.resized(columns: Int, rows: Int): TableElement {
    val width = colWidths.sum().takeIf { it > 0 } ?: 200.0
    val height = rowHeights.sum().takeIf { it > 0 } ?: 80.0
    val newCols = List(columns.coerceAtLeast(1)) { index ->
        colWidths.getOrNull(index) ?: (width / columns.coerceAtLeast(1))
    }
    val newRows = List(rows.coerceAtLeast(1)) { index ->
        rowHeights.getOrNull(index) ?: (height / rows.coerceAtLeast(1))
    }
    val cells = List(newRows.size) { row ->
        List(newCols.size) { column -> cell(row, column) }
    }
    return copy(
        colWidths = newCols,
        rowHeights = newRows,
        cells = cells,
    )
}
