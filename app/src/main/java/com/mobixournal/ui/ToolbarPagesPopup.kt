package com.mobixournal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.render.PageSizeStep
import com.mobixournal.render.PageSizeUnit
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.TabularFiguresStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The page navigator: shows the current page, jumps to the previous/next page, and adds or removes
 * a page. [currentPage] is 0-based; the label and jump targets present it 1-based.
 */
@Composable
internal fun PagesPopupButton(callbacks: ToolbarPagesCallbacks) {
    var open by remember { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    ToolbarPopupButton(
        icon = ChromeGlyph.DESCRIPTION.imageVector(),
        contentDescription = "Pages",
    ) { dismiss ->
        PageNavRow(callbacks.pageCount, callbacks.currentPage, callbacks.onGoToPage, dismiss)
        // Everything under the navigator is the window's order, so the rows both shells share are the
        // same rows in the same places (see `desktop/shell/PageMenuPanel.kt`). Two things are
        // deliberately absent: the PDF's **Contents**, which is a door of the page list's own foot
        // (`PdfContentsButton`), and the app-side **bookmarks** — this menu is what can be done to the
        // page in front of you, and neither of those is.
        QuickSizeRows(callbacks)
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
        HorizontalDivider()
        ManagePageRows(callbacks, dismiss)
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
            unit = callbacks.sizeUnit,
            onUnit = callbacks.onPageSizeUnit,
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
 * The **Manage pages** section, under its own heading: the two one-step moves of the page in view, and
 * the typed range card (*pages 3 to 7, start them on page 1*).
 *
 * The three rows are the window's, in the window's order (`PageMenuPanel`), so the same errand is the
 * same third row on both shells. The two moves leave the menu open, exactly as the quick sizes do:
 * three taps are three steps, not three visits to the menu.
 *
 * The tablet's own page list is **not** here: it is the capsule beside the search in the top bar
 * ([PageListCapsule]), because the list is a way of looking *through* the document rather than one of
 * the page's commands.
 */
@Composable
private fun ManagePageRows(callbacks: ToolbarPagesCallbacks, dismiss: () -> Unit) {
    MenuHeading("Manage pages")
    DropdownMenuItem(
        text = { Text("Move page up") },
        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
        enabled = callbacks.currentPage > 0,
        onClick = { callbacks.onMovePage(-1) },
    )
    DropdownMenuItem(
        text = { Text("Move page down") },
        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
        enabled = callbacks.currentPage < callbacks.pageCount - 1,
        onClick = { callbacks.onMovePage(1) },
    )
    DropdownMenuItem(
        text = { Text("Manage pages…") },
        leadingIcon = { Icon(ChromeGlyph.DESCRIPTION.imageVector(), contentDescription = null) },
        enabled = callbacks.pageCount > 1,
        onClick = { callbacks.onOpenPageRange(); dismiss() },
    )
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
    // The step is a length of paper, announced in the unit the page format was set to — the same
    // conversion the dialog uses, so a sheet read in inches steps by 3.9 in rather than 100 mm.
    val unit = callbacks.sizeUnit
    val stepLabel = PageSizeStep.label(PageSizeStep.mmOf(stepPt), unit)
    MenuHeading("Quick size")
    QuickSizeRow(
        label = "Width",
        sizePt = size.first,
        unit = unit,
        stepLabel = stepLabel,
        onStep = { steps ->
            val (w, h) = PageSizeStep.step(size.first, size.second, steps, 0, stepPt)
            callbacks.onPageSize(w, h)
        },
    )
    QuickSizeRow(
        label = "Height",
        sizePt = size.second,
        unit = unit,
        stepLabel = stepLabel,
        onStep = { steps ->
            val (w, h) = PageSizeStep.step(size.first, size.second, 0, steps, stepPt)
            callbacks.onPageSize(w, h)
        },
    )
}

/** The measure's slot: one width for both rows, so the two numbers end on the same line. */
private val QUICK_SIZE_MEASURE_WIDTH = 76.dp

/** One arrow's target: enough for a fingertip, small enough that the measure keeps its air. */
private val QUICK_SIZE_STEP_SIZE = 40.dp

/**
 * One axis of [QuickSizeRows]: its name, the sheet's current measure, and the two arrows that step it.
 *
 * The measure and the step are printed in the unit the page format was last set to
 * ([ToolbarPagesCallbacks.sizeUnit]) — a sheet read in inches shows `8.3 in` and steps by `3.9 in` —
 * while the sheet itself is stored in points, as it always is.
 *
 * The number gets a **slot of its own**: fixed width, trailing-aligned, in tabular figures. Two rows of
 * measurements then end on the same line, a wider unit does not shove the arrows aside, and the arrows
 * stop pressing against the digits — which is what made the pair read as one crowded line of text.
 */
@Composable
private fun QuickSizeRow(
    label: String,
    sizePt: Double,
    unit: PageSizeUnit,
    stepLabel: String,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            unit.measure(sizePt),
            style = MaterialTheme.typography.labelLarge.merge(TabularFiguresStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(QUICK_SIZE_MEASURE_WIDTH).padding(end = 10.dp),
        )
        IconButton(
            onClick = { onStep(-1) },
            enabled = sizePt > PageSizeStep.MIN_PAGE_PT,
            modifier = Modifier.size(QUICK_SIZE_STEP_SIZE),
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "$label − $stepLabel",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = { onStep(1) }, modifier = Modifier.size(QUICK_SIZE_STEP_SIZE)) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "$label + $stepLabel",
                modifier = Modifier.size(18.dp),
            )
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
            callbacks.pageSize?.let { Text(pageSizeLabel(it.first, it.second, callbacks.sizeUnit)) }
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

