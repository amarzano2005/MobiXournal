package com.mobixournal.render

import kotlin.math.hypot

/**
 * The table tool's commit: a tap (under 6 pt of travel) gets a default-sized table centred on the
 * tap, a drag gets exactly the rectangle drawn. Either way the page holds a living `TableElement`,
 * not the ink the drag looked like.
 */
internal fun DrawingSurfaceView.commitTableInsertion() {
    val ex = shapeEndX
    val ey = shapeEndY
    val isTap = hypot(ex - shapeStartX, ey - shapeStartY) < 6.0
    val table = if (isTap) {
        val halfW = 100.0
        val halfH = 60.0
        TableElementFactory.tableElementFor(
            startX = shapeStartX - halfW,
            startY = shapeStartY - halfH,
            endX = shapeStartX + halfW,
            endY = shapeStartY + halfH,
            rows = tableRows,
            cols = tableCols,
            hasHeader = tableHeader,
            borderColor = strokeColor(),
            borderWidth = shapeWidthPt,
        )
    } else {
        TableElementFactory.tableElementFor(
            startX = shapeStartX,
            startY = shapeStartY,
            endX = ex,
            endY = ey,
            rows = tableRows,
            cols = tableCols,
            hasHeader = tableHeader,
            borderColor = strokeColor(),
            borderWidth = shapeWidthPt,
        )
    }
    ElementEdits.addElement(doc, currentPage, table) { resolvedActiveLayer(it) }
        ?.let { commitElementEdit(it) }
    render()
}
