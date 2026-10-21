package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.InkTokens.FIGURE_LABEL_SIZE_PT

/**
 * A table compiled into the ink `.xopp` can hold: the grid as border strokes, and one text box per
 * non-empty cell (`docs/mobix-format.md`, the synthesis export) — and, through `LivingInk`, the drawing on screen.
 *
 * The grid is drawn from the element's **own** `colWidths`/`rowHeights` rather than through
 * [ShapeBuilder.table], because that helper divides a drag evenly and a table element's columns are
 * drag-resizable: a grid that re-divided the box would move every line the user placed. A filled cell
 * is a closed rectangle stroke carrying the desktop `fill` alpha, so a shaded header row survives as
 * the shading the desktop draws — and a coloured fill that differs from the table's own ink is a
 * second stroke underneath, since `.xopp` gives a stroke one colour and one interior alpha.
 */
object TableInk {

    /** Space between a cell's edge and its text, so a label never sits on the rule beside it. */
    private const val CELL_PAD_PT = 2.0

    /** [table] as ink: its grid, then its cells' text. A table with no columns draws nothing. */
    fun ink(table: TableElement): List<Element> {
        if (table.colWidths.isEmpty() || table.rowHeights.isEmpty()) return emptyList()
        val out = mutableListOf<Element>()
        out += grid(table)
        out += filledCells(table)
        out += cellTexts(table)
        return out
    }

    private fun grid(table: TableElement): List<Element> {
        val right = table.left + table.width
        val bottom = table.top + table.height
        val out = mutableListOf<Element>()
        out += line(table, table.left, table.top, right, table.top)
        out += line(table, right, table.top, right, bottom)
        out += line(table, right, bottom, table.left, bottom)
        out += line(table, table.left, bottom, table.left, table.top)

        var x = table.left
        for (index in 0 until table.colWidths.size - 1) {
            x += table.colWidths[index]
            out += line(table, x, table.top, x, bottom)
        }
        var y = table.top
        for (index in 0 until table.rowHeights.size - 1) {
            val rHeight = table.rowHeights[index]
            y += rHeight
            out += line(table, table.left, y, right, y)
            if (table.headerRow && index == 0) {
                val gap = (table.borderWidth * 2.5).coerceIn(2.0, (rHeight * 0.3).coerceAtLeast(2.0))
                val doubleY = (y + gap).coerceAtMost(bottom)
                if (doubleY > y && doubleY < bottom) {
                    out += line(table, table.left, doubleY, right, doubleY)
                }
            }
        }
        return out
    }

    private fun line(table: TableElement, x1: Double, y1: Double, x2: Double, y2: Double): Element =
        InkPrimitives.stroke(
            points = listOf(
                InkPrimitives.pt(x1, y1, table.borderWidth),
                InkPrimitives.pt(x2, y2, table.borderWidth),
            ),
            color = table.borderColor,
            widthPt = table.borderWidth,
        )

    /** A closed rectangle per filled cell, in the cell's own colour. */
    private fun filledCells(table: TableElement): List<Element> {
        val out = mutableListOf<Element>()
        forEachCell(table) { cell, left, top, width, height, _ ->
            val fill = cell.fillColor ?: return@forEachCell
            out += InkPrimitives.stroke(
                points = closedRect(left, top, left + width, top + height, table.borderWidth),
                color = fill,
                widthPt = table.borderWidth,
                fill = fill.alphaChannel(),
            )
        }
        return out
    }

    private fun cellTexts(table: TableElement): List<Element> {
        val out = mutableListOf<Element>()
        forEachCell(table) { cell, left, top, width, height, rowIdx ->
            if (cell.text.isNotEmpty()) out += textIn(cell, table, left, top, width, height, rowIdx)
        }
        return out
    }

    /**
     * A cell's text, placed inside its cell.
     *
     * Alignment needs the text's width, and there is no font engine in the format's own layer: the
     * estimate is the app's own ([ElementBounds], the same per-character approximation every hit test
     * and selection outline uses), which keeps this compilable and testable on the JVM. A cell too
     * short for its text still gets the text — clipped by the reader, not dropped by the exporter.
     */
    private fun textIn(
        cell: TableCell,
        table: TableElement,
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        rowIdx: Int,
    ): TextElement {
        val fontSize = autoFontSizePt(cell.text, width, height, cell.bold)
        val probe = InkPrimitives.text(
            content = cell.text,
            x = 0.0,
            y = 0.0,
            color = table.borderColor,
            bold = cell.bold,
            sizePt = fontSize,
        )
        val box = ElementBounds.of(probe)
        val x = when (cell.align) {
            TableAlign.LEFT -> left + CELL_PAD_PT
            TableAlign.CENTER -> (left + (width - box.width) / 2.0).coerceAtLeast(left + CELL_PAD_PT)
            TableAlign.RIGHT -> (left + width - box.width - CELL_PAD_PT).coerceAtLeast(left + CELL_PAD_PT)
        }
        val y = (top + (height - box.height) / 2.0).coerceAtLeast(top + CELL_PAD_PT)
        return probe.copy(x = x, y = y)
    }

    /**
     * Computes an auto-fitted font size in pt for text inside a cell of size [width] x [height].
     *
     * The size scales with cell height for comfortable proportion, but adapts down gracefully
     * if the text width or line count would overflow the available cell bounds.
     */
    fun autoFontSizePt(
        text: String,
        width: Double,
        height: Double,
        isBold: Boolean = false,
    ): Double {
        if (text.isEmpty()) return FIGURE_LABEL_SIZE_PT
        val pad = CELL_PAD_PT * 2.0
        val availW = (width - pad).coerceAtLeast(1.0)
        val availH = (height - pad).coerceAtLeast(1.0)
        val lines = text.split("\n")
        val maxChars = lines.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val numLines = lines.size.coerceAtLeast(1)

        val charW = if (isBold) 0.65 else 0.60
        val lineH = 1.30

        // Preferred font size proportional to cell height (e.g. ~38% of available height per line).
        val preferred = ((availH / numLines) * 0.38).coerceIn(8.0, 18.0)

        // Geometric limits so text fits within cell borders without clipping
        val maxFittingW = availW / (maxChars * charW)
        val maxFittingH = availH / (numLines * lineH)

        return minOf(preferred, maxFittingW, maxFittingH).coerceIn(5.0, 24.0)
    }

    /** Walk the grid, handing each cell its box: [table]'s own widths and heights, not an even split. */
    private inline fun forEachCell(
        table: TableElement,
        action: (cell: TableCell, left: Double, top: Double, width: Double, height: Double, row: Int) -> Unit,
    ) {
        var top = table.top
        for (row in table.rowHeights.indices) {
            val height = table.rowHeights[row]
            var left = table.left
            for (col in table.colWidths.indices) {
                val width = table.colWidths[col]
                action(table.cell(row, col), left, top, width, height, row)
                left += width
            }
            top += height
        }
    }

    private fun closedRect(left: Double, top: Double, right: Double, bottom: Double, widthPt: Double): List<StrokePoint> =
        listOf(
            InkPrimitives.pt(left, top, widthPt),
            InkPrimitives.pt(right, top, widthPt),
            InkPrimitives.pt(right, bottom, widthPt),
            InkPrimitives.pt(left, bottom, widthPt),
            InkPrimitives.pt(left, top, widthPt),
        )

    private fun Int.alphaChannel(): Int = (this ushr 24) and 0xFF
}
