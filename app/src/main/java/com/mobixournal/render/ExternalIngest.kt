package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement

/**
 * What a piece of external content becomes when it is dropped on the page.
 *
 * One sealed answer per shape a drop can take, each carrying the element already placed at the drop
 * point — the canvas only has to append [element] to the active layer, so the decision of *what this
 * text is* lives here, in pure code, and is testable without a device. A dropped picture or PDF never
 * reaches this file: those are file imports, not text interpretation.
 */
sealed interface Ingested {
    /** The living element the content became. */
    val element: Element

    /** A grid parsed from CSV/TSV — re-editable cells, not a baked table drawing. */
    data class Table(override val element: TableElement) : Ingested

    /** A formula; [MathBlockElement.source] is what was dropped. */
    data class Math(override val element: MathBlockElement) : Ingested

    /** Prose that is neither a grid nor a formula. */
    data class Text(override val element: TextElement) : Ingested
}

/**
 * Turn **text dropped from another application** into the living element it describes.
 *
 * Three shapes are worth recognising, and the order matters:
 *
 *  1. **A delimited grid** (CSV, TSV, semicolon-separated) becomes a [TableElement]. This is the
 *     spreadsheet/lecture-notes case: a copied range of cells should arrive as a table you can still
 *     edit, not as a pile of text boxes. Detection needs at least two rows with at least two columns
 *     on each of the first two, which is what keeps an ordinary sentence containing a comma a
 *     sentence.
 *  2. **A formula** (a LaTeX command, a caret or a subscript in one short line) becomes a
 *     [MathBlockElement] — the source stays editable.
 *  3. **Anything else** is prose and becomes a text box.
 *
 * Pure: a string and a page point in, a model element out. The quoting rules are RFC 4180's
 * (doubled quotes, delimiters and newlines inside quotes), because that is what spreadsheet
 * applications emit when a cell is copied.
 */
object ExternalIngest {

    /** Width a dropped grid is laid out to when the caller does not choose one, in points. */
    const val DEFAULT_TABLE_WIDTH_PT: Double = 320.0

    /** Height of every imported row, in points — the table tool's comfortable row. */
    const val ROW_HEIGHT_PT: Double = 24.0

    /** The text box's font size, in points. */
    const val TEXT_SIZE_PT: Double = 12.0

    /** A formula source longer than this (or with more than one line) is prose, not math. */
    private const val MAX_MATH_CHARS = 200

    /** The delimiters tried, in order: a tab is the least ambiguous, a comma the most common. */
    private val DELIMITERS = listOf('\t', ',', ';')

    /**
     * The element [text] describes, placed at ([left], [top]) in page points, or null for blank
     * input.
     */
    fun fromText(
        text: String,
        left: Double,
        top: Double,
        color: Int,
        defaultWidthPt: Double = DEFAULT_TABLE_WIDTH_PT,
    ): Ingested? {
        if (text.isBlank()) return null
        detectDelimited(text)?.let { rows ->
            return Ingested.Table(table(rows, left, top, color, defaultWidthPt))
        }
        val trimmed = text.trim()
        if (looksLikeMath(trimmed)) return Ingested.Math(math(trimmed, left, top, color))
        return Ingested.Text(TextElement("Sans", TEXT_SIZE_PT, left, top, color, text))
    }

    /**
     * Split [text] into rows of fields on [delimiter], honouring RFC 4180 quoting: a quoted field may
     * contain [delimiter], newlines and doubled quotes (`""` is one literal quote). Unquoted fields are
     * trimmed. A completely blank trailing line is not a row.
     */
    fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var inQuotes = false
        var i = 0
        fun endField() {
            row.add(if (quoted) field.toString() else field.toString().trim())
            field.setLength(0)
            quoted = false
        }
        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i += 2
                    }
                    c == '"' -> {
                        inQuotes = false
                        i++
                    }
                    else -> {
                        field.append(c)
                        i++
                    }
                }
                continue
            }
            when (c) {
                '"' -> {
                    // A quote is only an opening quote at the start of a field; inside prose it is a
                    // literal character (a drop of an email should not lose its quotation marks).
                    if (field.isEmpty()) {
                        inQuotes = true
                        quoted = true
                    } else {
                        field.append(c)
                    }
                    i++
                }
                delimiter -> {
                    endField()
                    i++
                }
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                    i++
                }
                '\n' -> {
                    endRow()
                    i++
                }
                else -> {
                    field.append(c)
                    i++
                }
            }
        }
        if (field.isNotEmpty() || quoted || row.isNotEmpty()) endRow()
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.lastIndex)
        return rows
    }

    /** The first delimiter that makes a grid of [text], with the rows it produced. */
    private fun detectDelimited(text: String): List<List<String>>? {
        for (delimiter in DELIMITERS) {
            val rows = parseDelimited(text, delimiter)
            if (rows.size < 2) continue
            if (rows.first().size < 2 || rows[1].size < 2) continue
            val widest = rows.maxOf { it.size }
            // A delimiter only counts when it appears in most rows: two incidental commas in running
            // prose must not turn a paragraph into a two-column table.
            if (rows.count { it.size >= 2 } * 2 < rows.size) continue
            return rows.map { row -> row + List(widest - row.size) { "" } }
        }
        return null
    }

    private fun table(
        rows: List<List<String>>,
        left: Double,
        top: Double,
        color: Int,
        requestedWidthPt: Double,
    ): TableElement {
        val cols = rows.maxOf { it.size }.coerceAtLeast(1)
        // The caller chose the footprint; the columns divide it exactly, so the table lands where the
        // drop was made and is the width the drop asked for.
        val width = requestedWidthPt.coerceAtLeast(1.0)
        val header = rows.first().any { it.isNotBlank() } && rows.first().none { isNumeric(it) }
        val cells = rows.mapIndexed { rowIndex, row ->
            List(cols) { col ->
                val text = row.getOrElse(col) { "" }
                if (text.isBlank()) {
                    TableCell()
                } else {
                    TableCell(
                        text = text,
                        align = if (isNumeric(text)) com.mobixournal.format.model.TableAlign.CENTER
                        else com.mobixournal.format.model.TableAlign.LEFT,
                        bold = header && rowIndex == 0,
                    )
                }
            }
        }
        return TableElement(
            left = left,
            top = top,
            colWidths = List(cols) { width / cols },
            rowHeights = List(rows.size) { ROW_HEIGHT_PT },
            cells = cells,
            headerRow = header,
            borderColor = color,
            borderWidth = TableElementFactory.DEFAULT_BORDER_WIDTH,
            id = ElementId.mint(ElementId.TABLE),
        )
    }

    /** A dropped formula: one short line carrying LaTeX syntax (a command, a caret or a subscript). */
    private fun looksLikeMath(text: String): Boolean {
        if (text.isEmpty() || text.length > MAX_MATH_CHARS) return false
        if (text.any { it == '\n' || it == '\r' }) return false
        return text.contains('\\') || text.contains('^') || text.contains('_')
    }

    private fun math(source: String, left: Double, top: Double, color: Int) = MathBlockElement(
        left = left,
        top = top,
        right = left + MathBlockElement.DEFAULT_WIDTH_PT,
        bottom = top + MathBlockElement.DEFAULT_HEIGHT_PT,
        source = source,
        color = color,
        id = ElementId.mint(ElementId.MATH),
    )

    /** Whether [cell] reads as a number (currency signs and a trailing `%` included). */
    private fun isNumeric(cell: String): Boolean {
        val cleaned = cell.trim()
            .removePrefix("$").removePrefix("€").removePrefix("£")
            .removeSuffix("%")
            .replace(",", ".")
        return cleaned.isNotEmpty() && cleaned.toDoubleOrNull() != null
    }
}
