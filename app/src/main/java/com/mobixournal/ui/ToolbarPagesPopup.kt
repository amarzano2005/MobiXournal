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
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.PageSizeStep
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.rememberClosingArgument
import kotlin.math.abs
import kotlin.math.roundToInt

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
    ToolbarPopupButton(
        icon = ChromeGlyph.DESCRIPTION.imageVector(),
        contentDescription = "Pages",
    ) { dismiss ->
        PageNavRow(callbacks.pageCount, callbacks.currentPage, callbacks.onGoToPage, dismiss)
        QuickSizeRows(callbacks)
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
    DialogPresence(sizing) {
        PageFormatDialog(
            initialWidthPt = callbacks.pageSize?.first ?: callbacks.defaultPageSize.first,
            initialHeightPt = callbacks.pageSize?.second ?: callbacks.defaultPageSize.second,
            defaultWidthPt = callbacks.defaultPageSize.first,
            defaultHeightPt = callbacks.defaultPageSize.second,
            onConfirm = { w, h ->
                sizing = false
                if (callbacks.pageSize != null) callbacks.onPageSize(w, h)
            },
            onSaveAsDefault = { w, h -> callbacks.onSaveAsDefault(w, h) },
            onDismiss = { sizing = false },
        )
    }
}

/**
 * The **quick size** section: four taps that widen or heighten the page in view by a fixed step, so a
 * sheet can be nudged to fit a figure without opening a dialog.
 *
 * The step is the one set in Settings ▸ Editor ([PageSizeStep]), and it is the same on both axes — the
 * buttons differ only in which dimension they change. Each tap applies at once and the menu stays open, so
 * three taps are three steps rather than three visits to the menu; [PageSizeStep.step] keeps the sheet from
 * collapsing when the minus side is tapped too often.
 */
@Composable
private fun QuickSizeRows(callbacks: ToolbarPagesCallbacks) {
    val size = callbacks.pageSize ?: return
    val stepPt = callbacks.sizeStepPt
    if (stepPt <= 0.0) return
    val stepPx = (stepPt / PageSizeStep.PT_PER_PX).roundToInt()
    MenuHeading("Quick size")
    QuickSizeRow(
        label = "Width",
        sizePt = size.first,
        stepPx = stepPx,
        onStep = { steps ->
            val (w, h) = PageSizeStep.step(size.first, size.second, steps, 0, stepPt)
            callbacks.onPageSize(w, h)
        },
    )
    QuickSizeRow(
        label = "Height",
        sizePt = size.second,
        stepPx = stepPx,
        onStep = { steps ->
            val (w, h) = PageSizeStep.step(size.first, size.second, 0, steps, stepPt)
            callbacks.onPageSize(w, h)
        },
    )
}

/**
 * One axis of [QuickSizeRows]: its name, the sheet's current measure, and the two arrows that step it.
 *
 * The measure is shown in points because that is the page's own unit — the *step* is in pixels because
 * that is how the setting is written, and the row says both (`−100 px`).
 */
@Composable
private fun QuickSizeRow(label: String, sizePt: Double, stepPx: Int, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("${sizePt.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = { onStep(-1) }, enabled = sizePt > PageSizeStep.MIN_PAGE_PT) {
            Icon(Icons.Outlined.Remove, contentDescription = "$label − $stepPx px")
        }
        IconButton(onClick = { onStep(1) }) {
            Icon(Icons.Outlined.Add, contentDescription = "$label + $stepPx px")
        }
    }
}

/**
 * The **Page format** row — the paper's size as its trailing hint, and the one entry point into the
 * page-format dialog.
 */
@Composable
private fun PageFormatRow(
    callbacks: ToolbarPagesCallbacks,
    openSize: () -> Unit,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Page format") },
        leadingIcon = { Icon(ChromeGlyph.PAGE_FORMAT.imageVector(), contentDescription = null) },
        trailingIcon = {
            callbacks.pageSize?.let { Text(pageSizeLabel(it.first, it.second)) }
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
        leadingIcon = { Icon(ChromeGlyph.PAGE_BACKGROUND.imageVector(), contentDescription = null) },
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

