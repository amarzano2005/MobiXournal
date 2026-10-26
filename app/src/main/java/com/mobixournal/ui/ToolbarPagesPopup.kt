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
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument
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
        text = { Text(if (current == null) "Bookmark this page" else "Edit bookmark") },
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
    var sheet by remember { mutableStateOf(false) }
    // A geometry picked in the page-format dialog, waiting on "this document or new documents only".
    // The fixed width/height the dialog was showing rides along: a strip created from this answer has
    // to be born on the bound the user just typed, not on the pane's own extent.
    var pendingCanvas by remember { mutableStateOf<NewDocumentCanvas?>(null) }
    var pendingExtent by remember { mutableStateOf<Double?>(null) }
    val board = callbacks.board
    val isInfinite = callbacks.canvasMode != CanvasMode.PAGINATED
    ToolbarPopupButton(
        icon = ChromeGlyph.DESCRIPTION.imageVector(),
        contentDescription = "Pages",
    ) { dismiss ->
        if (isInfinite) {
            PageFormatRow(callbacks, openSize = { sizing = true }, dismiss = dismiss)
            // A board has no page list, but it does have a sheet: its paper and ruling are still
            // settable, so the row travels with the format one rather than vanishing with the pages.
            PageBackgroundRow(callbacks, open = { sheet = true }, dismiss = dismiss)
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
            leadingIcon = { Icon(ChromeGlyph.ADD.imageVector(), contentDescription = null) },
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
        // The sheet's graphics are the other question about the page — so they are the one row that
        // opens a dialog of their own, like Page format, instead of stacking three chip rows in here.
        PageBackgroundRow(callbacks, open = { sheet = true }, dismiss = dismiss)
    }
    val outline = callbacks.outline
    DialogPresence(contents && outline != null) {
        // The tree survives the flag: closing clears `contents`, and the dialog is still on its way out.
        val tree = rememberClosingArgument(outline)
        if (tree != null) {
            PdfContentsDialog(
                outline = tree,
                onGoToPdfPage = callbacks.onGoToPdfPage,
                onDismiss = { contents = false },
            )
        }
    }
    PageBackgroundDialog(
        open = sheet,
        style = callbacks.backgroundStyle,
        config = callbacks.backgroundConfig,
        onStyle = callbacks.onBackgroundStyle,
        onConfig = callbacks.onBackgroundConfig,
        onStationery = { callbacks.onStationery(it); sheet = false },
        onDone = { sheet = false },
    )
    PageFormatDialogs(
        callbacks = callbacks,
        open = sizing,
        board = board,
        onOpen = { sizing = it },
        pending = pendingCanvas,
        pendingExtent = pendingExtent,
        onPending = { canvas, extent -> pendingCanvas = canvas; pendingExtent = extent },
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
    /** The fixed width/height the dialog had when the geometry was picked, for the new document. */
    pendingExtent: Double?,
    onPending: (NewDocumentCanvas?, Double?) -> Unit,
) {
    DialogPresence(open) {
        PageFormatDialog(
            initialWidthPt = callbacks.pageSize?.first ?: callbacks.defaultPageSize.first,
            initialHeightPt = callbacks.pageSize?.second ?: callbacks.defaultPageSize.second,
            defaultWidthPt = callbacks.defaultPageSize.first,
            defaultHeightPt = callbacks.defaultPageSize.second,
            defaultCanvas = callbacks.defaultCanvas,
            sized = callbacks.pageSize != null && board == null,
            canvasMode = callbacks.canvasMode,
            extentPt = callbacks.canvasExtentPt,
            extentEditable = callbacks.canvasEditable,
            onExtent = callbacks.onCanvasExtent,
            onConfirm = { w, h, canvas ->
                onOpen(false)
                val currentCanvas = NewDocumentCanvas.of(callbacks.canvasMode)
                if (canvas != currentCanvas) {
                    // The bound travels with the choice: on a vertical strip or a 2D board it is the
                    // dialog's *width*, on a horizontal one its *height*, and a page list has none.
                    onPending(
                        canvas,
                        when (canvas) {
                            NewDocumentCanvas.INFINITE_VERTICAL, NewDocumentCanvas.INFINITE_2D -> w
                            NewDocumentCanvas.INFINITE_HORIZONTAL -> h
                            NewDocumentCanvas.PAGINATED -> null
                        },
                    )
                } else {
                    when (canvas) {
                        NewDocumentCanvas.PAGINATED -> {
                            if (callbacks.pageSize != null) callbacks.onPageSize(w, h)
                        }
                        NewDocumentCanvas.INFINITE_VERTICAL, NewDocumentCanvas.INFINITE_2D -> {
                            callbacks.onCanvasExtent(w)
                        }
                        NewDocumentCanvas.INFINITE_HORIZONTAL -> {
                            callbacks.onCanvasExtent(h)
                        }
                    }
                }
            },
            onSaveAsDefault = { w, h -> callbacks.onSaveAsDefault(w, h) },
            onSaveDefaultSettings = { w, h, canvas ->
                callbacks.onSaveAsDefault(w, h)
                callbacks.onCanvasModeDefault(canvas)
            },
            onDismiss = { onOpen(false) },
        )
    }
    DialogPresence(pending != null) {
        // The chosen geometry is held past the flag so the question keeps naming it while it closes.
        val chosen = rememberClosingArgument(pending) ?: return@DialogPresence
        CanvasModeChangeDialog(
            canvas = chosen,
            onNewBlankPage = {
                onPending(null, null)
                callbacks.onCanvasMode(chosen, pendingExtent)
            },
            onDismiss = { onPending(null, null) },
        )
    }
}

/**
 * Dialog opened when setting a new canvas mode from an active page.
 * Prompts the user to start a new blank page with the selected canvas mode
 * or cancel the operation, as modifying the canvas geometry on the current page is not possible.
 */
@Composable
private fun CanvasModeChangeDialog(
    canvas: NewDocumentCanvas,
    onNewBlankPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canvas mode") },
        text = {
            Text(
                "The canvas can't be changed on the current page.\n\n" +
                    "Start a new blank page with the new canvas type («${canvas.label}»), or cancel the operation?",
            )
        },
        confirmButton = {
            TextButton(onClick = onNewBlankPage) { Text("New blank page") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The **Page format** row — the paper's size as its trailing hint on a page list, and the canvas
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
        text = { Text("Page format") },
        leadingIcon = { Icon(Icons.Outlined.AspectRatio, contentDescription = null) },
        trailingIcon = {
            // On a page list the useful fact is the paper; on a board there is no page to measure, so
            // the row names the geometry instead — the thing that chose that board's sheet.
            val size = callbacks.pageSize
            if (size != null && callbacks.canvasMode == CanvasMode.PAGINATED) {
                Text(pageSizeLabel(size.first, size.second))
            } else {
                Text(NewDocumentCanvas.of(callbacks.canvasMode).label)
            }
        },
        onClick = { openSize(); dismiss() },
    )
}

/**
 * The **Page background** row: the entry point into [PageBackgroundDialog], carrying the live paper
 * style as its trailing hint so the menu says which sheet is under the page. Kept to one row on
 * purpose — the dialog is the submenu, and a menu of page commands is not the place for paper chips.
 */
@Composable
private fun PageBackgroundRow(callbacks: ToolbarPagesCallbacks, open: () -> Unit, dismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Page background") },
        leadingIcon = { Icon(Icons.Outlined.GridOn, contentDescription = null) },
        trailingIcon = { paperStyleLabel(callbacks.backgroundStyle)?.let { Text(it) } },
        onClick = { open(); dismiss() },
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
            leadingIcon = { Icon(ChromeGlyph.COPY.imageVector(), contentDescription = null) },
            onClick = onCopySelectedPages,
        )
        DropdownMenuItem(
            text = { Text("Delete $selectedPages selected") },
            leadingIcon = { Icon(ChromeGlyph.DELETE.imageVector(), contentDescription = null) },
            enabled = selectedPages < pageCount,
            onClick = onDeleteSelectedPages,
        )
        DropdownMenuItem(
            text = { Text("Clear selection") },
            leadingIcon = { Icon(ChromeGlyph.CLOSE.imageVector(), contentDescription = null) },
            onClick = onClearPageSelection,
        )
    }
    if (copiedPages > 0) {
        DropdownMenuItem(
            text = { Text(if (copiedPages == 1) "Paste page" else "Paste $copiedPages pages") },
            leadingIcon = { Icon(ChromeGlyph.PASTE.imageVector(), contentDescription = null) },
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
