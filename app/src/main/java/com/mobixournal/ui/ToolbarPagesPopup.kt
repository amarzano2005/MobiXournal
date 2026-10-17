package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.render.BoardReading
import com.mobixournal.render.PageStacker
import kotlin.math.abs

/**
 * The bookmark section of the Pages menu: bookmark (or edit the bookmark on) the page in view, then
 * jump to any bookmark the document has. Each row carries the bookmark's own colour, so the list
 * reads as the same flags the pages do.
 *
 * Bookmarks are app-side navigation state — the `.xopp` format has no page label — so this list lives
 * in the store beside the document, never in the file (see [PageBookmarks]).
 */
@Composable
private fun BookmarkItems(callbacks: ToolbarPagesCallbacks, dismiss: () -> Unit) {
    val current = callbacks.bookmarks.firstOrNull { it.page == callbacks.currentPage }
    MenuHeading("Bookmarks")
    DropdownMenuItem(
        text = { Text(if (current == null) "Bookmark this page…" else "Edit bookmark…") },
        leadingIcon = {
            Icon(
                if (current == null) Icons.Outlined.BookmarkBorder else Icons.Outlined.Bookmark,
                contentDescription = null,
                tint = current?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {
            callbacks.onBookmarkPage(callbacks.currentPage)
            dismiss()
        },
    )
    for (bookmark in callbacks.bookmarks) {
        DropdownMenuItem(
            text = { Text(bookmark.displayLabel()) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(bookmark.color)),
                ) {}
            },
            trailingIcon = { Text("Page ${bookmark.page + 1}", style = MaterialTheme.typography.labelSmall) },
            onClick = {
                callbacks.onGoToPage(bookmark.page)
                dismiss()
            },
        )
    }
}

/**
 * The page navigator: shows the current page, jumps to the previous/next page, and adds or removes
 * a page. [currentPage] is 0-based; the label and jump targets present it 1-based.
 */
@Composable
internal fun PagesPopupButton(callbacks: ToolbarPagesCallbacks) {
    var open by remember { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    // A geometry picked in the page-format dialog, waiting on "this document or new documents only".
    var pendingCanvas by remember { mutableStateOf<NewDocumentCanvas?>(null) }
    val board = callbacks.board
    ToolbarPopupButton(
        icon = Icons.Outlined.Description,
        contentDescription = if (board == null) "Pages" else "Tiles",
    ) { dismiss ->
        if (board != null) {
            TileItems(board)
            // A board still needs a way back to pages — and that is the *format* of the document, so
            // it lives in the same dialog a page list opens (see `docs/infinite-canvas.md`).
            HorizontalDivider()
            PageFormatRow(callbacks, openSize = { sizing = true }, dismiss = dismiss)
            return@ToolbarPopupButton
        }
        PageNavRow(callbacks.pageCount, callbacks.currentPage, callbacks.onGoToPage, dismiss)
        // The PDF's own table of contents sits right above the app-side bookmarks: both are "jump to a
        // page", and the nearer one to the page navigator is the one that came with the document.
        if (callbacks.outline?.isEmpty == false) {
            DropdownMenuItem(
                text = { Text("Contents…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Toc, contentDescription = null) },
                onClick = { contents = true; dismiss() },
            )
        }
        BookmarkItems(callbacks) { dismiss() }
        DropdownMenuItem(
            text = { Text("Add page") },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            onClick = { callbacks.onAddPage(); dismiss() },
        )
        DropdownMenuItem(
            text = { Text("Remove page") },
            leadingIcon = { Icon(Icons.Outlined.Remove, contentDescription = null) },
            enabled = callbacks.pageCount > 1,
            onClick = { callbacks.onRemovePage(); dismiss() },
        )
        PageClipboardItems(
            pageCount = callbacks.pageCount,
            selectedPages = callbacks.selectedPages,
            copiedPages = callbacks.copiedPages,
            onCopySelectedPages = { callbacks.onCopySelectedPages(); dismiss() },
            onDeleteSelectedPages = { callbacks.onDeleteSelectedPages(); dismiss() },
            onClearPageSelection = { callbacks.onClearPageSelection(); dismiss() },
            onPastePages = { callbacks.onPastePages(); dismiss() },
        )
        HorizontalDivider()
        PagesPerRowRow(callbacks.pageColumns, callbacks.onPageColumns)
        OverviewModeRow(callbacks.pageColumns, callbacks.pagesEditMode, callbacks.onPagesEditMode)
        HorizontalDivider()
        PageFormatRow(callbacks, openSize = { sizing = true }, dismiss = dismiss)
    }
    val outline = callbacks.outline
    if (contents && outline != null) {
        PdfContentsDialog(
            outline = outline,
            onGoToPdfPage = callbacks.onGoToPdfPage,
            onDismiss = { contents = false },
        )
    }
    PageFormatDialogs(
        callbacks = callbacks,
        open = sizing,
        board = board,
        onOpen = { sizing = it },
        pending = pendingCanvas,
        onPending = { pendingCanvas = it },
    )
}

/**
 * The page-format dialog: the paper (when there is a page to size) and the document's canvas geometry,
 * which is the same question — *what is a page of this document* — answered twice. Behind it sits the
 * second half of a geometry change: **this document, or the next ones?**
 *
 * Both are driven through the caller's own state ([open], [pending]) rather than held here, because
 * `PagesPopupButton` owns the two and has to keep them across recompositions of the menu behind them.
 */
@Composable
private fun PageFormatDialogs(
    callbacks: ToolbarPagesCallbacks,
    open: Boolean,
    board: BoardReading?,
    onOpen: (Boolean) -> Unit,
    pending: NewDocumentCanvas?,
    onPending: (NewDocumentCanvas?) -> Unit,
) {
    if (open) {
        PageFormatDialog(
            initialWidthPt = callbacks.pageSize?.first ?: callbacks.defaultPageSize.first,
            initialHeightPt = callbacks.pageSize?.second ?: callbacks.defaultPageSize.second,
            defaultWidthPt = callbacks.defaultPageSize.first,
            defaultHeightPt = callbacks.defaultPageSize.second,
            // A board has no page to measure, so the paper half is left out — but its cells carry a
            // paper size, which is why the test is the geometry, not the presence of a size.
            sized = callbacks.pageSize != null && board == null,
            canvasMode = callbacks.canvasMode,
            extentPt = callbacks.canvasExtentPt,
            extentEditable = callbacks.canvasEditable,
            onExtent = callbacks.onCanvasExtent,
            onConfirm = { w, h, canvas ->
                if (callbacks.pageSize != null) callbacks.onPageSize(w, h)
                onOpen(false)
                // A geometry that changed has one more question to ask: this document, or the next
                // ones? Asked here rather than assumed, because the two answers do different things.
                if (canvas != NewDocumentCanvas.of(callbacks.canvasMode)) onPending(canvas)
            },
            onSaveAsDefault = { w, h -> callbacks.onSaveAsDefault(w, h) },
            onDismiss = { onOpen(false) },
        )
    }
    pending?.let { chosen ->
        CanvasScopeDialog(
            canvas = chosen,
            editable = callbacks.canvasEditable,
            onThisDocument = { callbacks.onCanvasMode(chosen); onPending(null) },
            onNewDocuments = { callbacks.onCanvasModeDefault(chosen); onPending(null) },
            onDismiss = { onPending(null) },
        )
    }
}

/**
 * "This document, or the next ones?" — the second half of choosing a canvas geometry, asked every
 * time because the two answers write in different places.
 *
 * **This document** changes the shape the open document is drawn (and saved) in, and is offered only
 * while the document is still unsaved ([ToolbarPagesCallbacks.canvasEditable]): a `.mobix` that came
 * from a file keeps the geometry its own `canvas.json` declares, because moving its parts between
 * `pages/` and `chunks/` is a conversion of the file, not a preference
 * (`docs/infinite-canvas.md`). **New documents** only moves the default, exactly as Settings' own
 * control does.
 */
@Composable
private fun CanvasScopeDialog(
    canvas: NewDocumentCanvas,
    editable: Boolean,
    onThisDocument: () -> Unit,
    onNewDocuments: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canvas: ${canvas.label}") },
        text = {
            Text(
                if (editable) {
                    "Change this document, or only the ones you create from now on?"
                } else {
                    "This document keeps the canvas it was saved with — a saved document's geometry " +
                        "is part of its file. The choice will apply to the documents you create from " +
                        "now on (and Save As can write this one out as a new document)."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onThisDocument, enabled = editable) { Text("This document") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNewDocuments) { Text("New documents only") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * The **Page format…** row — the paper's size as its trailing hint on a page list, and the canvas
 * geometry's name on a board (which has no paper to label). The one entry point into the page-format
 * dialog from both geometries.
 */
@Composable
private fun PageFormatRow(
    callbacks: ToolbarPagesCallbacks,
    openSize: () -> Unit,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Page format…") },
        leadingIcon = { Icon(Icons.Outlined.AspectRatio, contentDescription = null) },
        trailingIcon = {
            // On a page list the useful fact is the paper; on a board there is no page to measure, so
            // the row names the geometry instead — the thing that chose that board's sheet.
            val size = callbacks.pageSize
            if (size != null && callbacks.board == null) {
                Text(pageSizeLabel(size.first, size.second))
            } else {
                Text(NewDocumentCanvas.of(callbacks.canvasMode).label)
            }
        },
        onClick = { openSize(); dismiss() },
    )
}

/**
 * What the pages menu is on an **infinite canvas**: the tile in view and how many there are, and
 * nothing else.
 *
 * Every other row in this menu addresses a page — previous/next, add, remove, the overview grid, page
 * copy and paste, the page size, the bookmarks — and a board has no page to address. Adding or removing
 * one would rewrite the chunk grid the file names its parts by, and offering a control that is disabled,
 * or that quietly does nothing, is the same lie in a smaller font. So the menu says what is true: which
 * tile you are looking at (the one the badge shows) and how many tiles the document holds. Navigation
 * on a board is the canvas itself (`docs/chrome.md` → *A document with no pages*).
 */
@Composable
private fun TileItems(board: BoardReading) {
    ListItem(
        headlineContent = { Text("Tile ${tileLabel(board.cell)}") },
        supportingContent = {
            Text(if (board.tiles == 1) "1 tile" else "${board.tiles} tiles")
        },
    )
}

/** The "‹ Page n / N ›" row at the top of the pages menu. */
@Composable
private fun PageNavRow(pageCount: Int, currentPage: Int, onGoToPage: (Int) -> Unit, dismiss: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onGoToPage(currentPage - 1); dismiss() },
            enabled = currentPage > 0,
        ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous page") }
        Text(
            "Page " + pageLabel(currentPage, pageCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(
            onClick = { onGoToPage(currentPage + 1); dismiss() },
            enabled = currentPage < pageCount - 1,
        ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next page") }
    }
}

/**
 * The copy / delete / clear / paste rows. Copy-and-friends only appear once pages have been tapped
 * in the overview grid; paste appears whenever something is on the page clipboard, and lands after
 * the last selected page (or after the page in view when nothing is selected).
 */
@Composable
private fun PageClipboardItems(
    pageCount: Int,
    selectedPages: Int,
    copiedPages: Int,
    onCopySelectedPages: () -> Unit,
    onDeleteSelectedPages: () -> Unit,
    onClearPageSelection: () -> Unit,
    onPastePages: () -> Unit,
) {
    if (selectedPages > 0) {
        DropdownMenuItem(
            text = { Text("Copy $selectedPages selected") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = onCopySelectedPages,
        )
        DropdownMenuItem(
            text = { Text("Delete $selectedPages selected") },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            enabled = selectedPages < pageCount,
            onClick = onDeleteSelectedPages,
        )
        DropdownMenuItem(
            text = { Text("Clear selection") },
            leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
            onClick = onClearPageSelection,
        )
    }
    if (copiedPages > 0) {
        DropdownMenuItem(
            text = { Text(if (copiedPages == 1) "Paste page" else "Paste $copiedPages pages") },
            leadingIcon = { Icon(Icons.Outlined.ContentPaste, contentDescription = null) },
            onClick = onPastePages,
        )
    }
}

/**
 * A named page-size preset in points (1/72 in), portrait orientation.
 *
 * Used in the page-size dialog to offer common paper sizes (A4, A5, Letter, Legal).
 */
internal data class PagePreset(val name: String, val widthPt: Double, val heightPt: Double)

/** The presets offered in the page-size dialog — desktop Xournal++'s common sizes. */
internal val PAGE_PRESETS: List<PagePreset> = listOf(
    PagePreset("A4", 595.276, 841.89),
    PagePreset("A5", 419.528, 595.276),
    PagePreset("Letter", 612.0, 792.0),
    PagePreset("Legal", 612.0, 1008.0),
)

/**
 * The label of the extra entry in the preset row that restores the app's **default** page size —
 * the dimensions new documents and pages are born with (Settings ▸ Editor).
 */
internal const val DEFAULT_PAGE_PRESET = "Default"

/**
 * Whether a size equals a preset's dimensions, in **either orientation**, with the same one-point
 * slack the preset label uses — so a page set to "A4 landscape" still lights the A4 entry.
 */
internal fun matchesPageSize(widthPt: Double, heightPt: Double, presetWidthPt: Double, presetHeightPt: Double): Boolean {
    fun near(a: Double, b: Double) = abs(a - b) <= 1.0
    return (near(widthPt, presetWidthPt) && near(heightPt, presetHeightPt)) ||
        (near(widthPt, presetHeightPt) && near(heightPt, presetWidthPt))
}

/**
 * The page-overview control: how many pages sit side by side. One is the plain single-page stack;
 * two or more zooms out to a grid of page thumbnails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagesPerRowRow(pageColumns: Int, onPageColumns: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("Pages per row", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageStacker.COLUMN_CHOICES.forEach { n ->
                FilterChip(
                    selected = n == pageColumns,
                    onClick = { onPageColumns(n) },
                    label = { Text("$n") },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

/**
 * The page-overview mode control: **View** keeps the grid a pure display/navigation surface (a tap
 * jumps to the page you hit), **Edit** turns on selection, drag-to-reorder and the copy/delete
 * tooling. Only meaningful in the grid, so it's disabled at a single column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewModeRow(pageColumns: Int, editMode: Boolean, onEditMode: (Boolean) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("Overview mode", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !editMode,
                enabled = pageColumns > 1,
                onClick = { onEditMode(false) },
                label = { Text("View") },
                modifier = Modifier.padding(end = 4.dp),
            )
            FilterChip(
                selected = editMode,
                enabled = pageColumns > 1,
                onClick = { onEditMode(true) },
                label = { Text("Edit") },
            )
        }
    }
}
