package com.mobixournal.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.MathFlavor
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotShading
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ParametricShapes
import com.mobixournal.render.PlotCurve
import com.mobixournal.render.PlotElementFactory
import com.mobixournal.render.SelectionOps
import com.mobixournal.render.TableElementFactory

/**
 * The **inspector lane**: the right-hand column that edits the living element a selection names, asks a
 * placement's questions, and says what is selected when nothing is being edited.
 *
 * It is a lane, not a dialog (`docs/interface-contract.md`): 300 dp of its own column beside the page, so
 * nothing is ever laid over the sheet and the page keeps the rectangle that is left. [DesktopWorkspace]
 * puts it in the row, which is what makes that true mechanically — the lane takes width, the canvas takes
 * the remainder, and no glass floats on the ink.
 *
 * The lane is **always there** while a document is open, and that is not decoration: it used to return
 * early with nothing selected, so opening the pencil grew the column and closing it shrank it, and the page
 * jumped under the pointer every time. A stable frame is read in three parts — the header that says which
 * element this is, the cards that hold its fields, and the footer that holds the one action — and every
 * state fills the same three parts: the placement question, the editor, one element selected, several
 * selected, and nothing selected.
 *
 * The sections only offer **fields the model carries**: a value the file would drop is a lie about the
 * document (`docs/mobix-elements.md`). Every commit is one [DesktopShellState.commitLiving] — one edit, one
 * `Ctrl+Z` — and an unchanged value writes nothing at all.
 */
@Composable
internal fun InspectorLane(
    state: DesktopShellState,
    tab: DesktopTab,
    modifier: Modifier = Modifier,
) {
    val pending = state.pendingPlacement
    val live = state.editingLiving?.let { livingTarget(tab, it) }
    // One living element selected but not opened: the lane still has something to say about it.
    val picked = if (live == null) pickedLiving(tab) else null
    val selection = tab.selection

    Column(
        modifier
            .width(INSPECTOR_WIDTH)
            .fillMaxHeight()
            .bandSurface(),
    ) {
        LaneHeader(
            tool = pending?.slot?.tool() ?: live?.family?.tool() ?: picked?.family?.tool(),
            title = when {
                pending != null -> "New ${pending.slot.label().lowercase()}"
                live != null -> live.family.title
                picked != null -> picked.family.title
                else -> "Inspector"
            },
            summary = when {
                pending != null -> pending.summary()
                live != null -> livingSummary(live)
                picked != null -> livingSummary(picked)
                selection != null -> selectionSummary(selection.refs.size)
                else -> null
            },
            onClose = when {
                pending != null -> state::cancelPlacement
                live != null -> state::closeLivingEditor
                else -> null
            },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            when {
                pending != null -> PlacementBody(pending)
                live != null -> EditorBody(live) { element -> state.commitLiving(element) }
                picked != null -> PickedBody(picked)
                selection != null -> ManyBody(selection.refs.size)
                else -> EmptyBody()
            }
        }
        LaneFooter {
            when {
                pending != null -> {
                    PanelButton("Place", primary = true, enabled = pending.ready, onClick = { state.confirmPlacement() })
                    PanelButton("Cancel", primary = false, enabled = true, onClick = { state.cancelPlacement() })
                }
                live != null -> PanelButton("Done", primary = true, enabled = true, onClick = { state.closeLivingEditor() })
                picked != null -> PanelButton("Edit element", primary = true, enabled = true, onClick = {
                    state.runSelection(SelectionAction.EDIT)
                })
                else -> Unit
            }
        }
    }
}

// --- what the lane is showing ------------------------------------------------------------------

/** The one living element the selection names, if it names exactly one — the lane's quiet state. */
private fun pickedLiving(tab: DesktopTab): LivingTarget? {
    val ref = livingTargetOf(tab) ?: return null
    return livingTarget(tab, LivingTargetRef(tab.key, tab.page, ref))
}

/** The line under a header when several things are caught. */
private fun selectionSummary(count: Int): String =
    if (count == 1) "1 element selected" else "$count elements selected"

/** The state with nothing selected: what the lane is for, in one calm card. */
@Composable
private fun EmptyBody() {
    SectionCard("Nothing selected") {
        Text(
            "Click an element on the page to edit it, or drag the Plot, Table or a circuit slot to place " +
                "one. A selection can be moved, resized and turned with the handles.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
    }
}

/** One element caught but not opened: its identity, and a word about the pencil in the footer. */
@Composable
private fun PickedBody(target: LivingTarget) {
    SectionCard("Selected") {
        Readout("kind", target.family.title)
        Readout("summary", livingSummary(target))
        Spacer(Modifier.height(4.dp))
        Text(
            "Edit element opens the fields; the bar under the outline has copy, align, recolour and delete.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
        )
    }
}

/** Several elements caught: the lane counts them and points at the bar, which is where they are edited. */
@Composable
private fun ManyBody(count: Int) {
    SectionCard("Selection") {
        Readout("elements", count.toString())
        Spacer(Modifier.height(4.dp))
        Text(
            "Drag to move the group, or use the handles to resize and turn it. The bar under the outline " +
                "copies, duplicates, aligns, recolours and deletes; a living element is opened with its pencil.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
        )
    }
}

// --- the placement question --------------------------------------------------------------------

/** The question a living slot asks before its element exists, in the lane's own cards. */
@Composable
private fun PlacementBody(pending: PendingPlacement) {
    when (pending.slot) {
        LivingSlot.PLOT -> PlotQuestion(pending)
        LivingSlot.TABLE -> TableQuestion(pending)
        // A component's part was the palette's question, so a circuit never reaches this card.
        LivingSlot.CIRCUIT -> SectionCard("Component") { Readout("part", pending.slot.label()) }
    }
}

/** The graph dialog's questions: `f(x)`, the domain and the display flags. */
@Composable
private fun PlotQuestion(pending: PendingPlacement) {
    SectionCard("Function") {
        Field("f(x)") { ValueField(pending.formula, onCommit = { pending.formula = it }) }
        NumberRow(
            left = { Field("x from") { ValueField(pending.xMin, numeric = true, onCommit = { pending.xMin = it }) } },
            right = { Field("x to") { ValueField(pending.xMax, numeric = true, onCommit = { pending.xMax = it }) } },
        )
        if (pending.domain == null) {
            Text("x to must be greater than x from", color = DesktopTheme.danger, style = DesktopTheme.label)
        }
    }
    SectionCard("Display") {
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
}

/** The table's questions: how many columns and rows, and whether the first row is a header. */
@Composable
private fun TableQuestion(pending: PendingPlacement) {
    SectionCard("Grid") {
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
}

/** The line a pending question shows under the lane's title — the answer so far. */
private fun PendingPlacement.summary(): String = when (slot) {
    LivingSlot.PLOT -> {
        val formula = formula.trim().ifBlank { "no formula" }
        val range = domain?.let { "${formatNumber(it.first)}…${formatNumber(it.second)}" } ?: "range not open"
        "$formula · $range"
    }
    LivingSlot.TABLE -> "$columns × $rows" + if (header) " · header" else ""
    LivingSlot.CIRCUIT -> slot.label()
}

// --- the five editors --------------------------------------------------------------------------

/** The editor of one element, dispatched by family — the lane's body once the element exists. */
@Composable
private fun EditorBody(live: LivingTarget, commit: (Element) -> Unit) {
    when (live.family) {
        LivingFamily.PLOT -> PlotBody(live.element as PlotElement, commit)
        LivingFamily.TABLE -> TableBody(live.element as TableElement, commit)
        LivingFamily.SHAPE -> ShapeBody(live.element as ParametricShapeElement, commit)
        LivingFamily.CIRCUIT -> CircuitBody(live.element as CircuitElement, commit)
        LivingFamily.MATH -> MathBody(live.element as MathBlockElement, commit)
    }
}

@Composable
private fun PlotBody(plot: PlotElement, commit: (Element) -> Unit) {
    val curve = plot.curves.firstOrNull()
    SectionCard("Function") {
        var formula by remember(plot) { mutableStateOf(curve?.source.orEmpty()) }
        Field("f(x)") {
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
        }
        NumberRow(
            left = {
                NumberField("x from", plot.xMin) { value ->
                    if (value < plot.xMax) commit(plot.copy(xMin = value))
                }
            },
            right = {
                NumberField("x to", plot.xMax) { value ->
                    if (value > plot.xMin) commit(plot.copy(xMax = value))
                }
            },
        )
        NumberField("samples", plot.samples.toDouble()) { value ->
            val rounded = value.toInt()
            if (rounded in 8..4000 && rounded != plot.samples) commit(plot.copy(samples = rounded))
        }
    }
    // The graph dialog's own switches, so the window's plot offers what the tablet's does. The summary is
    // a cache of the engine's analysis, so it is recomputed through PlotElementFactory (the same path the
    // tablet's edit takes) rather than left stale or dropped.
    SectionCard("Display") {
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
    }
    SectionCard("Look") {
        NumberField("width pt", curve?.strokeWidth ?: 1.0) { value ->
            if (curve != null && value > 0 && value != curve.strokeWidth) {
                commit(plot.copy(curves = listOf(curve.copy(strokeWidth = value)) + plot.curves.drop(1)))
            }
        }
        // The curve's own ink, not the axis': a colour row under "Look" is what the line will be drawn in.
        ColourRow(current = curve?.color) { colour ->
            if (curve != null) {
                commit(plot.copy(curves = listOf(curve.copy(color = colour)) + plot.curves.drop(1)))
            } else {
                commit(plot.copy(axisColor = colour))
            }
        }
    }
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
private fun TableBody(table: TableElement, commit: (Element) -> Unit) {
    SectionCard("Grid") {
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
        SwitchRow("Header row", table.headerRow) { on -> commit(table.copy(headerRow = on)) }
    }
    SectionCard("Rules") {
        NumberField("border pt", table.borderWidth) { value ->
            if (value > 0 && value != table.borderWidth) commit(table.copy(borderWidth = value))
        }
        ColourRow(current = table.borderColor) { colour -> commit(table.copy(borderColor = colour)) }
    }
    // The tablet writes a cell's content by tapping it; the lane writes it by naming it, with the same
    // cell in the same grid, so the two shells keep one table and one meaning per cell.
    SectionCard("Cells") {
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
}

@Composable
private fun ShapeBody(shape: ParametricShapeElement, commit: (Element) -> Unit) {
    SectionCard("Geometry") {
        for (index in shape.vertices.indices) {
            val side = ParametricShapes.sideLengthPt(shape, index)
            val angle = ParametricShapes.interiorAngleDeg(shape, index)
            Readout("side ${index + 1}", "${side.roundToTenth()} pt · ${angle.roundToTenth()}°")
        }
    }
    SectionCard("Outline") {
        NumberField("width pt", shape.strokeWidth) { value ->
            if (value > 0 && value != shape.strokeWidth) commit(shape.copy(strokeWidth = value))
        }
        ColourRow(current = shape.strokeColor) { colour -> commit(shape.copy(strokeColor = colour)) }
    }
}

@Composable
private fun CircuitBody(circuit: CircuitElement, commit: (Element) -> Unit) {
    SectionCard("Component") {
        Readout("symbol", circuit.symbol.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
        var value by remember(circuit) { mutableStateOf(circuit.value.orEmpty()) }
        Field("value") {
            ValueField(value, onCommit = { entered ->
                value = entered
                commit(circuit.copy(value = entered.ifBlank { null }))
            })
        }
        ColourRow(current = circuit.strokeColor) { colour -> commit(circuit.copy(strokeColor = colour)) }
    }
    // The tablet's own two placement controls, as words: a quarter turn and a straighten, then the side
    // its label is written on (a four-way cycle, exactly the enum's four sides).
    SectionCard("Placement") {
        Readout("rotation", "${formatNumber(circuit.rotation)}°")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PanelButton("Rotate 90°", primary = false, enabled = true, onClick = {
                commit(circuit.copy(rotation = (circuit.rotation + 90.0) % 360.0))
            })
            PanelButton("Straighten", primary = false, enabled = true, onClick = {
                val straight = (kotlin.math.round(circuit.rotation / 90.0) * 90.0) % 360.0
                commit(circuit.copy(rotation = if (straight < 0.0) straight + 360.0 else straight))
            })
        }
        Spacer(Modifier.height(6.dp))
        PanelButton("Label: ${labelSideName(circuit.labelSide)}", primary = false, enabled = true, onClick = {
            val sides = CircuitLabelSide.entries
            commit(circuit.copy(labelSide = sides[(circuit.labelSide.ordinal + 1) % sides.size]))
        })
    }
    SectionCard("Stroke") {
        NumberField("width pt", circuit.strokeWidth) { width ->
            if (width > 0 && width != circuit.strokeWidth) commit(circuit.copy(strokeWidth = width))
        }
    }
}

/** A label side as the lane prints it: `ABOVE` reads as `Above`. */
private fun labelSideName(side: CircuitLabelSide): String =
    side.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun MathBody(block: MathBlockElement, commit: (Element) -> Unit) {
    SectionCard("Source") {
        var source by remember(block) { mutableStateOf(block.source) }
        ValueField(source, onCommit = { entered ->
            source = entered
            if (entered.isNotBlank() && entered != block.source) commit(block.copy(source = entered))
        }, multiline = true)
        SwitchRow("Typst", block.flavor == MathFlavor.TYPST) { typst ->
            commit(block.copy(flavor = if (typst) MathFlavor.TYPST else MathFlavor.LATEX))
        }
    }
    SectionCard("Look") {
        ColourRow(current = block.color) { colour -> commit(block.copy(color = colour)) }
    }
}

// --- the bridge to the header ------------------------------------------------------------------

/** The strip slot whose glyph stands for [this] family in the lane's header. */
private fun LivingFamily.tool(): DesktopTool = when (this) {
    LivingFamily.PLOT -> DesktopTool.PLOT
    LivingFamily.TABLE -> DesktopTool.TABLE
    LivingFamily.SHAPE -> DesktopTool.RECTANGLE
    LivingFamily.CIRCUIT -> DesktopTool.CIRCUIT
    LivingFamily.MATH -> DesktopTool.TEXT
}

/** The slot whose glyph stands for a pending placement's family. */
private fun LivingSlot.tool(): DesktopTool = when (this) {
    LivingSlot.PLOT -> DesktopTool.PLOT
    LivingSlot.TABLE -> DesktopTool.TABLE
    LivingSlot.CIRCUIT -> DesktopTool.CIRCUIT
}

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
