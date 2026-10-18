package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement

/**
 * The `table` element codec (`docs/mobix-elements.md`): the editable grid `.xopp` can only bake into
 * border strokes plus loose text boxes.
 *
 * The grid is spelled as the model holds it — [TableElement.colWidths] and [TableElement.rowHeights]
 * are the authority and the box is their sum placed at the origin — so "a table" in a file and "a
 * table" in the app cannot disagree about how wide a column is.
 *
 * One rule shapes [decode]: **it models a table or preserves it whole, and never half-reads one.** A
 * missing `colWidths`, a cell whose `align` is a value this build has no name for, a `cells` entry
 * that is not a row of objects — each of those makes the whole element unreadable, and the caller then
 * keeps it verbatim as raw JSON (`MobixElementCodec.decode`) instead of building a table that would
 * silently write back a *different* table on the next save. That is the element-layer rule: the model
 * has no channel for a field it did not interpret, so half-reading is the one thing that loses data.
 */
internal object MobixTableCodec {

    /** The `type` discriminator this codec owns. */
    const val TYPE: String = "table"

    private const val COL_WIDTHS = "colWidths"
    private const val ROW_HEIGHTS = "rowHeights"
    private const val CELLS = "cells"

    /** [table] as JSON. */
    fun encode(table: TableElement): JsonNode.Obj = MobixJson.obj { out ->
        out["type"] = JsonNode.of(TYPE)
        table.id?.let { out["id"] = JsonNode.of(it) }
        // The box is the grid's own extent at its origin — derived, so a reader recomputes it.
        out[MobixJson.BOUNDS] = MobixJson.box(
            left = table.left,
            top = table.top,
            right = table.left + table.width,
            bottom = table.top + table.height,
        )
        out[COL_WIDTHS] = MobixJson.numbers(table.colWidths)
        out[ROW_HEIGHTS] = MobixJson.numbers(table.rowHeights)
        out["headerRow"] = JsonNode.of(table.headerRow)
        out["borderColor"] = MobixJson.colour(table.borderColor)
        out["borderWidth"] = JsonNode.of(table.borderWidth)
        out[CELLS] = JsonNode.arr(table.cells.map(::encodeRow))
        if (table.extraAttrs.isNotEmpty()) out[MobixJson.ATTRS] = MobixJson.attrsNode(table.extraAttrs)
    }

    private fun encodeRow(row: List<TableCell>): JsonNode = JsonNode.arr(row.map(::encodeCell))

    /**
     * A cell as JSON. Every field is written, `fillColor` as an explicit `null` when the cell has no
     * fill: a cell is a small record whose shape a reader should not have to infer from which keys
     * happen to be there.
     */
    private fun encodeCell(cell: TableCell): JsonNode = MobixJson.obj { out ->
        out["text"] = JsonNode.of(cell.text)
        out["align"] = JsonNode.of(cell.align.name)
        out["bold"] = JsonNode.of(cell.bold)
        out["fillColor"] = cell.fillColor?.let(MobixJson::colour) ?: JsonNode.Null
    }

    /**
     * The table [node] describes, or null when this build cannot read it whole — in which case the
     * caller preserves the element verbatim rather than modelling part of it.
     */
    fun decode(node: JsonNode.Obj, mint: (String) -> String = ElementId::mint): TableElement? {
        val box = MobixJson.readBox(node) ?: return null
        val colWidths = MobixJson.numberList(node, COL_WIDTHS) ?: return null
        val rowHeights = MobixJson.numberList(node, ROW_HEIGHTS) ?: return null
        val cells = decodeCells(node) ?: return null
        return TableElement(
            left = box.left,
            top = box.top,
            colWidths = colWidths,
            rowHeights = rowHeights,
            cells = cells,
            headerRow = node.getBool("headerRow", false),
            borderColor = MobixJson.colourOrNull(node, "borderColor") ?: TableElement.DEFAULT_BORDER,
            borderWidth = node.getDouble("borderWidth", 1.0),
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull("id") ?: mint(ElementId.TABLE),
        )
    }

    /** The `cells` grid of [node], null when a row or a cell is not the shape the schema describes. */
    private fun decodeCells(node: JsonNode.Obj): List<List<TableCell>>? {
        val rows = node.getArr(CELLS)?.list ?: return emptyList()
        val out = ArrayList<List<TableCell>>(rows.size)
        for (row in rows) {
            val cells = (row as? JsonNode.Arr)?.list ?: return null
            val parsed = ArrayList<TableCell>(cells.size)
            for (cell in cells) {
                val obj = cell as? JsonNode.Obj ?: return null
                parsed += decodeCell(obj) ?: return null
            }
            out += parsed
        }
        return out
    }

    private fun decodeCell(node: JsonNode.Obj): TableCell? {
        val alignName = node.getStr("align", TableAlign.CENTER.name)
        val align = TableAlign.entries.firstOrNull { it.name == alignName } ?: return null
        return TableCell(
            text = node.getStr("text", ""),
            align = align,
            bold = node.getBool("bold", false),
            fillColor = MobixJson.colourOrNull(node, "fillColor"),
        )
    }
}
