package com.mobixournal.render

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
