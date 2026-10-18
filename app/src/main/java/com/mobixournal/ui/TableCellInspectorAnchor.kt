package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.PageBox
import com.mobixournal.render.TableElementFactory
import com.mobixournal.render.replaceTableElement
import com.mobixournal.ui.theme.FloatingSurface

/**
 * Pure positioning helper for the anchored table cell inspector.
 */
internal fun tableCellInspectorOffset(
    cellLeft: Float?,
    cellTop: Float?,
    cellRight: Float?,
    cellBottom: Float?,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    gapPx: Int,
    marginPx: Int,
    /**
     * View-bottom px already occluded by system UI — the IME above all (the window draws
     * edge-to-edge, so `adjustResize` never shrinks the pane for the keyboard), then the nav
     * bars when no keyboard is up. The vertical clamp keeps the panel out of that band.
     */
    occlusionBottomPx: Int = 0,
): IntOffset {
    val availBottom = (canvasSize.height - occlusionBottomPx).coerceAtLeast(0)
    if (cellLeft == null || cellRight == null || cellTop == null || cellBottom == null || inspectorSize.width <= 0) {
        val cx = ((canvasSize.width - inspectorSize.width) / 2).coerceAtLeast(marginPx)
        val cy = ((availBottom - inspectorSize.height) / 2).coerceAtLeast(marginPx)
        return IntOffset(cx, cy)
    }

    val cellCenterX = (cellLeft + cellRight) / 2f
    val idealX = (cellCenterX - inspectorSize.width / 2f).toInt()
    val maxX = canvasSize.width - inspectorSize.width - marginPx
    val clampedX = idealX.coerceIn(marginPx, maxX.coerceAtLeast(marginPx))

    val belowY = cellBottom.toInt() + gapPx
    val maxY = (availBottom - inspectorSize.height - marginPx).coerceAtLeast(marginPx)
    val freeBottom = availBottom - marginPx
    val clampedY = when {
        // Preferred: right under the cell, while the whole panel stays on open canvas.
        belowY + inspectorSize.height <= freeBottom -> belowY.coerceAtLeast(marginPx)
        // Then right above the cell — but only when the panel fits entirely above occluded
        // band (a cell that slid under the keyboard cannot carry a panel that straddles it).
        cellTop.toInt() - gapPx - inspectorSize.height >= marginPx &&
            cellTop.toInt() - gapPx - inspectorSize.height + inspectorSize.height <= freeBottom ->
            cellTop.toInt() - gapPx - inspectorSize.height
        // Neither fits: park the panel as low as the open canvas allows.
        else -> belowY.coerceIn(marginPx, maxY)
    }
    return IntOffset(clampedX, clampedY)
}

/**
 * Converts a table cell's document-space pt bounds to surface view px using the active [PageBox] and scroll.
 */
internal fun cellBoundsToViewPx(
    boundsPt: com.mobixournal.render.Bounds?,
    pageBox: PageBox?,
    scrollX: Float,
    scrollY: Float,
): com.mobixournal.render.Bounds? {
    if (boundsPt == null || pageBox == null) return null
    return com.mobixournal.render.Bounds(
        left = boundsPt.left * pageBox.scale + pageBox.leftPx - scrollX,
        top = boundsPt.top * pageBox.scale + pageBox.topPx - scrollY,
        right = boundsPt.right * pageBox.scale + pageBox.leftPx - scrollX,
        bottom = boundsPt.bottom * pageBox.scale + pageBox.topPx - scrollY,
    )
}

/**
 * Commits the draft text an open cell inspector holds into its cell, purely: the caller supplies
 * how the swap lands on the document (the surface's [replaceTableElement] in the editor, a captured
 * lambda in tests).
 *
 * Returns the updated editing target with the draft absorbed (null draft when the text did not
 * change, so a repeated flush never writes), or null when [editing] was already null. Nothing here
 * opens or closes the session — callers decide what the editing state becomes afterwards, so the
 * same helper serves *switching* to another cell and *closing* the editor.
 *
 * This is the seam that fixes the lost-text class of bugs: any path that replaces
 * [EditorUiState.editingTableCell] — a double-tap on another cell, back navigation — flushes before
 * it clobbers, because the typed text travels with the state itself.
 */
/**
 * The panel's anchoring offset for one editing session: converts the cell's pt bounds through the
 * pane's live page box into view px and clamps the panel inside the *unoccluded* canvas — the
 * window draws edge-to-edge (MainActivity sets decorFitsSystemWindows(false)), so `adjustResize`
 * never shrinks the pane when the keyboard opens, and the panel must clamp itself above the IME
 * instead. The IME and nav-bar reads are inset state reads, so the offset recomputes the moment
 * the keyboard slides in or out; scroll and zoom are component states republished per frame.
 */
@Composable
private fun rememberCellInspectorOffset(
    editing: EditingTableCell,
    pane: PaneState,
    canvasSizePx: IntSize,
    inspectorSize: IntSize,
): IntOffset {
    val density = LocalDensity.current
    val gapPx = with(density) { 12.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val bottomOcclusionPx = with(density) {
        val imePx = WindowInsets.ime.asPaddingValues().calculateBottomPadding().roundToPx()
        val navPx = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().roundToPx()
        maxOf(imePx, navPx)
    }
    val pageBox = if (editing.pageIndex >= 0) {
        pane.surface?.layout?.boxes?.getOrNull(editing.pageIndex)
    } else null
    return remember(
        editing, inspectorSize, canvasSizePx, pageBox,
        pane.scrollX, pane.scrollY, pane.zoom, pane.documentVersion, bottomOcclusionPx,
    ) {
        editingCellInspectorOffset(
            editing, pageBox, pane.scrollX, pane.scrollY,
            inspectorSize, canvasSizePx, gapPx, marginPx, bottomOcclusionPx,
        )
    }
}

/**
 * One step from the editing cell to the inspector's on-screen offset: the cell's pt bounds are
 * converted through the live page box into view px, then the pure placement helper clamps the
 * panel inside the canvas. Pure, so the offset recomputation in the composable is a single
 * `remember` keyed on the view state, and this step is unit-testable without Compose.
 */
private fun editingCellInspectorOffset(
    editing: EditingTableCell,
    pageBox: PageBox?,
    scrollX: Float,
    scrollY: Float,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    gapPx: Int,
    marginPx: Int,
    occlusionBottomPx: Int = 0,
): IntOffset {
    val rectPx = cellBoundsToViewPx(editing.cellBoundsPt, pageBox, scrollX, scrollY)
    return tableCellInspectorOffset(
        rectPx?.left?.toFloat(),
        rectPx?.top?.toFloat(),
        rectPx?.right?.toFloat(),
        rectPx?.bottom?.toFloat(),
        inspectorSize = inspectorSize,
        canvasSize = canvasSize,
        gapPx = gapPx,
        marginPx = marginPx,
        occlusionBottomPx = occlusionBottomPx,
    )
}

fun flushTableCellDraft(
    editing: EditingTableCell?,
    apply: (old: TableElement, new: TableElement) -> Unit,
): EditingTableCell? {
    val draft = editing?.draftText ?: return editing?.copy(draftText = null)
    val cell = editing.table.cell(editing.row, editing.col)
    if (draft == cell.text) return editing.copy(draftText = null)
    val updatedTable = TableElementFactory.updateCell(
        editing.table, editing.row, editing.col,
        cell.copy(text = draft),
    )
    apply(editing.table, updatedTable)
    return editing.copy(table = updatedTable, draftText = null)
}

/**
 * State holder for in-place table cell editing.
 */
internal class TableCellInspectorState(
    initialText: String,
    initialAlign: TableAlign,
    initialBold: Boolean,
) {
    var text by mutableStateOf(initialText)
    var align by mutableStateOf(initialAlign)
    var bold by mutableStateOf(initialBold)
}

@Composable
internal fun rememberTableCellInspectorState(
    tableId: String? = null,
    row: Int = 0,
    col: Int = 0,
    cell: TableCell,
    /** The pending draft the editing state carries; the cell text when nothing is pending. */
    drafting: String? = null,
): TableCellInspectorState {
    val state = remember(tableId, row, col) {
        TableCellInspectorState(
            initialText = drafting ?: cell.text,
            initialAlign = cell.align,
            initialBold = cell.bold,
        )
    }
    LaunchedEffect(tableId, row, col) {
        state.text = drafting ?: cell.text
        state.align = cell.align
        state.bold = cell.bold
    }
    return state
}

/**
 * In-place floating inspector anchored to an active table cell for editing its text and style.
 */
@Composable
fun TableCellInspectorAnchor(
    pane: PaneState,
    ui: EditorUiState,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val editing = ui.editingTableCell ?: return
    val table = editing.table
    val row = editing.row
    val col = editing.col
    val cell = table.cell(row, col)
    val state = rememberTableCellInspectorState(
        table.id ?: table.hashCode().toString(), row, col, cell,
        drafting = editing.draftText,
    )

    var inspectorSize by remember { mutableStateOf(IntSize.Zero) }
    val offset = rememberCellInspectorOffset(editing, pane, canvasSizePx, inspectorSize)

    fun commitDraftText(text: String) {
        state.text = text
        ui.editingTableCell = ui.editingTableCell?.copy(draftText = text)
    }

    fun commitCurrentCell(): TableElement {
        val currentTable = ui.editingTableCell?.table ?: table
        val targetCell = currentTable.cell(row, col)
        val updatedCell = targetCell.copy(text = state.text, align = state.align, bold = state.bold)
        val updatedTable = TableElementFactory.updateCell(currentTable, row, col, updatedCell)
        if (updatedTable != currentTable) {
            pane.surface?.replaceTableElement(currentTable, updatedTable)
            ui.editingTableCell = ui.editingTableCell?.copy(table = updatedTable, draftText = null)
        }
        return updatedTable
    }

    fun applyAndDismiss() {
        commitCurrentCell()
        ui.editingTableCell = null
        onDismiss()
    }

    fun navigateTo(nextR: Int, nextC: Int) {
        val currentTable = commitCurrentCell()
        val nextBounds = com.mobixournal.render.TableDividers.cellBoundsPt(currentTable, nextR, nextC)
        ui.editingTableCell = EditingTableCell(currentTable, nextR, nextC, nextBounds, pane.currentPage)
    }

    fun onStyleChange() {
        commitCurrentCell()
    }

    FloatingSurface(
        modifier = modifier
            .offset { offset }
            .onSizeChanged { inspectorSize = it }
            .pointerInput(Unit) {
                detectTapGestures { /* Swallow taps to prevent them leaking to the canvas below */ }
            },
    ) {
        TableCellInspectorContent(
            table = table,
            row = row,
            col = col,
            state = state,
            onChangeText = ::commitDraftText,
            onNavigate = ::navigateTo,
            onStyleChange = ::onStyleChange,
            onDone = ::applyAndDismiss,
            onDismiss = ::applyAndDismiss,
        )
    }
}

@Composable
private fun TableCellInspectorContent(
    table: TableElement,
    row: Int,
    col: Int,
    state: TableCellInspectorState,
    /** Every keystroke, mirrored into the editing state so an outside clobber can commit it. */
    onChangeText: (String) -> Unit,
    onNavigate: (Int, Int) -> Unit,
    onStyleChange: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalRows = table.rowHeights.size
    val totalCols = table.colWidths.size
    val hasNext = row < totalRows - 1 || col < totalCols - 1
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(row, col) {
        focusRequester.requestFocus()
    }

    fun advanceToNext() {
        val (nR, nC) = if (col < totalCols - 1) row to (col + 1) else (row + 1) to 0
        onNavigate(nR, nC)
    }

    Column(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 340.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TableCellInspectorHeader(
            table = table,
            row = row,
            col = col,
            onNavigate = onNavigate,
            onDismiss = onDone,
        )
        TableCellInspectorTextField(
            state = state,
            hasNext = hasNext,
            focusRequester = focusRequester,
            onChangeText = onChangeText,
            onNext = { advanceToNext() },
            onDone = { onDone() },
        )
        TableCellInspectorBottomBar(
            state = state,
            hasNext = hasNext,
            onNext = { advanceToNext() },
            onStyleChange = onStyleChange,
            onDone = onDone,
        )
    }
}

/**
 * The inspector's single-line cell text field, with the clear button mirroring the keystroke path
 * (so a clear cannot leave a stale draft behind) and the IME next/done wiring.
 */
@Composable
private fun TableCellInspectorTextField(
    state: TableCellInspectorState,
    hasNext: Boolean,
    focusRequester: FocusRequester,
    onChangeText: (String) -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = state.text,
        onValueChange = {
            state.text = it
            onChangeText(it)
        },
        placeholder = {
            Text(
                text = "Cell text...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (state.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (state.align) {
                TableAlign.LEFT -> TextAlign.Start
                TableAlign.CENTER -> TextAlign.Center
                TableAlign.RIGHT -> TextAlign.End
            },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
        ),
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                IconButton(
                    onClick = {
                        state.text = ""
                        onChangeText("")
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear text",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(
            imeAction = if (hasNext) ImeAction.Next else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { onDone() },
        ),
    )
}

@Composable
private fun TableCellInspectorBottomBar(
    state: TableCellInspectorState,
    hasNext: Boolean,
    onNext: () -> Unit,
    onStyleChange: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableCellInspectorStyleButtons(state, onStyleChange)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasNext) {
                Button(
                    onClick = onNext,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Next >", style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(
                onClick = onDone,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(if (hasNext) "Close" else "Done", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TableCellInspectorStyleButtons(
    state: TableCellInspectorState,
    onStyleChange: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = state.align == TableAlign.LEFT,
            onCheckedChange = {
                state.align = TableAlign.LEFT
                onStyleChange()
            },
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (state.align == TableAlign.LEFT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.FormatAlignLeft,
                contentDescription = "Left",
                tint = if (state.align == TableAlign.LEFT) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconToggleButton(
            checked = state.align == TableAlign.CENTER,
            onCheckedChange = {
                state.align = TableAlign.CENTER
                onStyleChange()
            },
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (state.align == TableAlign.CENTER) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.Outlined.FormatAlignCenter,
                contentDescription = "Center",
                tint = if (state.align == TableAlign.CENTER) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconToggleButton(
            checked = state.align == TableAlign.RIGHT,
            onCheckedChange = {
                state.align = TableAlign.RIGHT
                onStyleChange()
            },
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (state.align == TableAlign.RIGHT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.FormatAlignRight,
                contentDescription = "Right",
                tint = if (state.align == TableAlign.RIGHT) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        IconToggleButton(
            checked = state.bold,
            onCheckedChange = {
                state.bold = !state.bold
                onStyleChange()
            },
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (state.bold) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.Outlined.FormatBold,
                contentDescription = "Bold",
                tint = if (state.bold) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TableCellInspectorHeader(
    table: TableElement,
    row: Int,
    col: Int,
    onNavigate: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val totalRows = table.rowHeights.size
    val totalCols = table.colWidths.size
    val hasPrev = row > 0 || col > 0
    val hasNext = row < totalRows - 1 || col < totalCols - 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cell (${row + 1}, ${col + 1})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = {
                    val (nR, nC) = if (col > 0) row to (col - 1) else (row - 1) to (totalCols - 1)
                    onNavigate(nR, nC)
                },
                enabled = hasPrev,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Previous cell",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = {
                    val (nR, nC) = if (col < totalCols - 1) row to (col + 1) else (row + 1) to 0
                    onNavigate(nR, nC)
                },
                enabled = hasNext,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Next cell",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close inspector",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

