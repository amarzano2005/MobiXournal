package com.mobixournal.render

import android.graphics.Canvas
import com.mobixournal.format.model.TableElement

/** Highlights [row],[col] of [table] directly on the sheet while live editing, or clears when null. */
fun DrawingSurfaceView.setActiveTableCellHighlight(
    table: TableElement?,
    row: Int = 0,
    col: Int = 0,
) {
    val next = table?.let { Triple(it, row, col) }
    if (gestures.activeTableCellHighlight == next) return
    gestures.activeTableCellHighlight = next
    invalidate()
}

/** Draws an accent highlight around the table cell currently active in the live inspector. */
internal fun DrawingSurfaceView.drawActiveTableCellHighlight(canvas: Canvas, box: PageBox) {
    val (table, row, col) = gestures.activeTableCellHighlight ?: return
    val selected = singleSelectedTable() ?: return
    if (selected.id != table.id && selected != table) return
    val cb = TableDividers.cellBoundsPt(table, row, col) ?: return
    val cl = (cb.left * box.scale + box.leftPx - scrollX).toFloat()
    val ct = (cb.top * box.scale + box.topPx - scrollY).toFloat()
    val cr = (cb.right * box.scale + box.leftPx - scrollX).toFloat()
    val cbot = (cb.bottom * box.scale + box.topPx - scrollY).toFloat()
    canvas.drawRect(cl, ct, cr, cbot, chrome.selectionFill)
    canvas.drawRect(cl, ct, cr, cbot, chrome.selectionStroke)
}

/**
 * The table tool's commit: a tap (under 6 pt of travel) gets a default-sized table centred on the
 * tap, a drag gets exactly the rectangle drawn. Either way the page holds a living `TableElement`,
 * not the ink the drag looked like.
 */
internal fun DrawingSurfaceView.commitTableInsertion() {
    val draft = shapeDraft
    val table = if (draft.isTap()) {
        val halfW = 100.0
        val halfH = 60.0
        TableElementFactory.tableElementFor(
            startX = draft.startX - halfW,
            startY = draft.startY - halfH,
            endX = draft.startX + halfW,
            endY = draft.startY + halfH,
            rows = tableRows,
            cols = tableCols,
            hasHeader = tableHeader,
            borderColor = strokeColor(),
            borderWidth = draft.widthPt,
        )
    } else {
        TableElementFactory.tableElementFor(
            startX = draft.startX,
            startY = draft.startY,
            endX = draft.endX,
            endY = draft.endY,
            rows = tableRows,
            cols = tableCols,
            hasHeader = tableHeader,
            borderColor = strokeColor(),
            borderWidth = draft.widthPt,
        )
    }
    val written = ElementEdits.addElement(doc, ink.currentPage, table) { resolvedActiveLayer(it) }
    if (written != null) {
        commitElementEdit(written)
        selectInsertedElement(ink.currentPage)
    }
    render()
}
