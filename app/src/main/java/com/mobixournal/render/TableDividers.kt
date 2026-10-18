package com.mobixournal.render

import com.mobixournal.format.model.TableElement
import kotlin.math.abs

/**
 * Hit on a table column or row divider line.
 */
internal data class TableDividerHit(
    val isColumn: Boolean,
    val index: Int,
    val table: TableElement,
    val tableRef: ElementRef,
    val startPt: Double,
    val initialSpanPt: Double,
)

/**
 * Coordinate maths and hit-testing for table grid divider lines and cells.
 */
internal object TableDividers {

    /** Maximum distance in view pixels to grab a divider line. */
    const val HIT_TOLERANCE_PX = 24f

    /**
     * Determines whether ([touchX], [touchY]) in view px hits a column or row divider of [table].
     */
    fun hitTest(
        table: TableElement,
        ref: ElementRef,
        pageBox: PageBox,
        scrollX: Float,
        scrollY: Float,
        touchX: Float,
        touchY: Float,
    ): TableDividerHit? {
        val lPx = (table.left * pageBox.scale + pageBox.leftPx - scrollX).toFloat()
        val tPx = (table.top * pageBox.scale + pageBox.topPx - scrollY).toFloat()
        val rPx = ((table.left + table.width) * pageBox.scale + pageBox.leftPx - scrollX).toFloat()
        val bPx = ((table.top + table.height) * pageBox.scale + pageBox.topPx - scrollY).toFloat()

        // Column dividers (vertical internal rules)
        var colX = table.left
        for (i in 0 until table.colWidths.size - 1) {
            colX += table.colWidths[i]
            val divXPx = (colX * pageBox.scale + pageBox.leftPx - scrollX).toFloat()
            if (abs(touchX - divXPx) <= HIT_TOLERANCE_PX && touchY in tPx..bPx) {
                val prevStartPt = table.left + (0 until i).sumOf { table.colWidths[it] }
                return TableDividerHit(
                    isColumn = true,
                    index = i,
                    table = table,
                    tableRef = ref,
                    startPt = prevStartPt,
                    initialSpanPt = table.colWidths[i],
                )
            }
        }

        // Row dividers (horizontal internal rules)
        var rowY = table.top
        for (j in 0 until table.rowHeights.size - 1) {
            rowY += table.rowHeights[j]
            val divYPx = (rowY * pageBox.scale + pageBox.topPx - scrollY).toFloat()
            if (abs(touchY - divYPx) <= HIT_TOLERANCE_PX && touchX in lPx..rPx) {
                val prevStartPt = table.top + (0 until j).sumOf { table.rowHeights[it] }
                return TableDividerHit(
                    isColumn = false,
                    index = j,
                    table = table,
                    tableRef = ref,
                    startPt = prevStartPt,
                    initialSpanPt = table.rowHeights[j],
                )
            }
        }

        return null
    }

    /**
     * Finds the cell (row, col) under page-local point ([xPt], [yPt]).
     */
    fun cellAt(table: TableElement, xPt: Double, yPt: Double): Pair<Int, Int>? {
        if (xPt < table.left || xPt > table.left + table.width ||
            yPt < table.top || yPt > table.top + table.height
        ) return null

        var top = table.top
        for (r in table.rowHeights.indices) {
            val h = table.rowHeights[r]
            var left = table.left
            for (c in table.colWidths.indices) {
                val w = table.colWidths[c]
                if (xPt in left..(left + w) && yPt in top..(top + h)) {
                    return r to c
                }
                left += w
            }
            top += h
        }
        return null
    }

    /**
     * Computes the page-local bounding box of cell ([row], [col]) in [table].
     */
    fun cellBoundsPt(table: TableElement, row: Int, col: Int): Bounds? {
        if (row !in table.rowHeights.indices || col !in table.colWidths.indices) return null
        val left = table.left + (0 until col).sumOf { table.colWidths[it] }
        val top = table.top + (0 until row).sumOf { table.rowHeights[it] }
        val width = table.colWidths[col]
        val height = table.rowHeights[row]
        return Bounds(left, top, left + width, top + height)
    }
}
