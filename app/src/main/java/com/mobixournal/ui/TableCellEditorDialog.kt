package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.replaceTableElement
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.liquidGlassSpringAnimation

/**
 * Rectangular cell grid an edit session works on: the model may carry short rows (they read as
 * empty cells, per `format/model/Living.kt`), but the editor always hands back a full grid.
 */
internal fun editorCellsOf(table: TableElement): List<List<TableCell>> =
    List(table.rowHeights.size) { r ->
        List(table.colWidths.size) { c -> table.cell(r, c) }
    }

/**
 * Commits an edited cell grid onto [original] in one step — one element swap, one undo entry.
 * Turns into a no-op (returns [original]) if the edited grid is not rectangular in the original's
 * dimensions, so a malformed edit session can never corrupt a table.
 */
internal fun applyEditedCells(original: TableElement, edited: List<List<TableCell>>): TableElement {
    val rectangular = edited.size == original.rowHeights.size &&
        edited.all { it.size == original.colWidths.size }
    if (!rectangular) return original
    return original.copy(cells = edited)
}

/** Replaces one cell's text, leaving every other cell — and the target's own styling — alone. */
internal fun cellTextEdited(
    cells: List<List<TableCell>>,
    row: Int,
    col: Int,
    text: String,
): List<List<TableCell>> = cells.mapIndexed { r, rowCells ->
    rowCells.mapIndexed { c, cell ->
        if (r == row && c == col) cell.copy(text = text) else cell
    }
}

/** Restyles one cell's alignment and/or bold flag, leaving text and every other cell alone. */
internal fun cellStyled(
    cells: List<List<TableCell>>,
    row: Int,
    col: Int,
    align: TableAlign? = null,
    bold: Boolean? = null,
): List<List<TableCell>> = cells.mapIndexed { r, rowCells ->
    rowCells.mapIndexed { c, cell ->
        if (r == row && c == col) {
            cell.copy(
                align = align ?: cell.align,
                bold = bold ?: cell.bold,
            )
        } else {
            cell
        }
    }
}

/**
 * One open editing session's working grid plus the cell the style strip currently acts on. The
 * grid lives only in this session: **Save** commits it onto the table wholesale, closing without
 * saving throws it away — there is no half-committed state in between.
 */
internal class TableCellEditorState(initial: TableElement) {
    var cells by mutableStateOf(editorCellsOf(initial))
    var currentRow by mutableStateOf(0)
    var currentCol by mutableStateOf(0)

    fun setText(row: Int, col: Int, text: String) {
        cells = cellTextEdited(cells, row, col, text)
    }

    fun setAlign(row: Int, col: Int, align: TableAlign) {
        cells = cellStyled(cells, row, col, align = align)
    }

    fun toggleBold(row: Int, col: Int) {
        val now = cells.getOrNull(row)?.getOrNull(col)?.bold ?: return
        cells = cellStyled(cells, row, col, bold = !now)
    }
}

/**
 * The fixed, **compact** table editor: every cell's text editable at once, on the same frosted
 * glass the rest of the floating chrome wears ([FloatingSurface] fill + rim + the family's fluid
 * entry spring) instead of a full-width material card. Save applies the whole grid to the table
 * in a single `replaceTableElement` (one undo step).
 *
 * Opened by **Edit table** on a selected table (`SelectionActionAnchor`) or a double-tap on a
 * cell (`DrawingSurfaceInput`); the tapped (or first) cell starts focused. Align/bold act on the
 * cell the user last focused (`TableCellEditorState.current*`).
 */
@Composable
fun TableCellEditorDialog(
    ui: EditorUiState,
    pane: PaneState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { ui.editingTableCell = null },
) {
    val editing = ui.editingTableCell ?: return
    val table = editing.table
    val state = remember(table.id ?: table.hashCode()) { TableCellEditorState(table) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FloatingSurface(
            modifier = modifier
                .widthIn(max = 380.dp)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TableCellEditorHeader(table)
                TableCellEditorGrid(
                    state = state,
                    initialCell = editing.row to editing.col,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                )
                TableCellEditorBottomBar(
                    state = state,
                    onCancel = onDismiss,
                    onSave = {
                        val updated = applyEditedCells(table, state.cells)
                        if (updated != table) {
                            pane.surface?.replaceTableElement(table, updated)
                        }
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** One compact line: the edit affordance on the left, the grid's shape on the right. */
@Composable
private fun TableCellEditorHeader(table: TableElement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Modifica tabella",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${table.rowHeights.size}×${table.colWidths.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Every row and every cell of the session grid, scrollable vertically, tappable per cell. */
@Composable
private fun TableCellEditorGrid(
    state: TableCellEditorState,
    initialCell: Pair<Int, Int>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.cells.forEachIndexed { row, rowCells ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowCells.forEachIndexed { col, cell ->
                    TableCellEditorField(
                        state = state,
                        row = row,
                        col = col,
                        cell = cell,
                        focusOnEnter = (row to col) == initialCell,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCellEditorField(
    state: TableCellEditorState,
    row: Int,
    col: Int,
    cell: TableCell,
    focusOnEnter: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(cell.text) { if (focusOnEnter) focusRequester.requestFocus() }
    OutlinedTextField(
        value = cell.text,
        onValueChange = { text -> state.setText(row, col, text) },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    state.currentRow = row
                    state.currentCol = col
                }
            },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (cell.align) {
                TableAlign.LEFT -> androidx.compose.ui.text.style.TextAlign.Start
                TableAlign.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
                TableAlign.RIGHT -> androidx.compose.ui.text.style.TextAlign.End
            },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
    )
}

/**
 * One strip instead of two: the align/bold pills at the left (acting on the last-focused cell,
 * whose coordinates the strip names) and Annulla / Salva buttons at the right.
 */
@Composable
private fun TableCellEditorBottomBar(
    state: TableCellEditorState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val active = state.cells.getOrNull(state.currentRow)?.getOrNull(state.currentCol) ?: TableCell()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "R${state.currentRow + 1}·C${state.currentCol + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TableCellEditorStyleIconButton(
            selected = active.align == TableAlign.LEFT,
            imageVector = Icons.AutoMirrored.Outlined.FormatAlignLeft,
            description = "Allinea a sinistra",
            onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.LEFT) },
        )
        TableCellEditorStyleIconButton(
            selected = active.align == TableAlign.CENTER,
            imageVector = Icons.Outlined.FormatAlignCenter,
            description = "Allinea al centro",
            onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.CENTER) },
        )
        TableCellEditorStyleIconButton(
            selected = active.align == TableAlign.RIGHT,
            imageVector = Icons.AutoMirrored.Outlined.FormatAlignRight,
            description = "Allinea a destra",
            onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.RIGHT) },
        )
        TableCellEditorStyleIconButton(
            selected = active.bold,
            imageVector = Icons.Outlined.FormatBold,
            description = "Grassetto",
            onClick = { state.toggleBold(state.currentRow, state.currentCol) },
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("Annulla", style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = onSave,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text("Salva", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TableCellEditorStyleIconButton(
    selected: Boolean,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .size(28.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
