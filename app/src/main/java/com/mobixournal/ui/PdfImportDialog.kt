package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ImportCardLayout
import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.PageRangePlan
import com.mobixournal.ui.theme.FloatingDialog

/**
 * "Import PDF", asked **after** the file has been read: which of its pages, and where do they go.
 *
 * This is the window's card in the tablet's grammar (`ImportPdfPrompt`). The old flow asked the mode
 * before the picker and then brought the whole file — so bringing one chapter of a long scan was not a
 * thing the tablet could do. Reading the page count first makes the range a real question, and the card
 * can say how many pages there are before the button is pressed.
 *
 * [ImportPdfMode.APPEND] is the default because it keeps the work; a document already backed by a PDF
 * joins the two files on the way in (see [ImportPdfMode] and `PdfMerger`), which the subtitle says
 * rather than hides.
 *
 * The card is read top-down as one story: **which file**, then **which pages**, then **where they go**,
 * and the button under it names the answer that is picked (`Add pages`, `Replace pages`). Each answer's
 * title says what its command does to *this* document, so the window's card can title the same
 * [ImportPdfMode.REPLACE] with the tab it opens instead.
 *
 * **It adapts to the room it is given** ([ImportCardLayout]): a split-screen pane, the on-screen keyboard
 * and a 200 % text scale all shrink the dialog window, so the body scrolls inside whatever height it has
 * — name, count, range, error and both answers stay reachable — while the confirm and cancel buttons,
 * which `AlertDialog` keeps outside this slot, never scroll away. Under a narrow card or a large text
 * scale the two page fields stack, each under its own name.
 */
@Composable
fun ImportPdfDialog(
    request: PdfImportPrompt,
    merging: Boolean,
    onEdit: ((PdfImportPrompt) -> PdfImportPrompt) -> Unit,
    onConfirm: (ImportPdfMode) -> Unit,
    onDismiss: () -> Unit,
) {
    // The answer lives in the request, not in this composable: the card can be re-laid out (a rotation, a
    // keyboard, a narrower pane) without forgetting what was picked, and the same field is what the window's
    // card edits — one place, two shells.
    val mode = request.mode
    val usable = request.chosen != null
    val scroll = rememberScrollState()
    // The buttons are as cramped as the card: at 150 % text and a split-screen pane, Material's own button
    // flow puts them on two right-aligned lines with no air between them. The card decides instead — a row
    // when it fits, each answer full width otherwise — from the same rule and the same room as the fields.
    val compact = ImportCardLayout.stacked(
        ImportCardLayout.cardWidth(LocalConfiguration.current.screenWidthDp.toFloat()),
        LocalDensity.current.fontScale,
    )
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PDF") },
        text = {
            BoxWithConstraints {
                ImportQuestions(
                    request = request,
                    merging = merging,
                    onEdit = onEdit,
                    modifier = Modifier.heightIn(max = maxHeight).verticalScroll(scroll),
                )
            }
        },
        confirmButton = {
            if (compact) {
                StackedActions(
                    mode = mode,
                    usable = usable,
                    onConfirm = { onConfirm(mode) },
                    onCancel = onDismiss,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(mode) }, enabled = usable) {
                        Text(confirmLabel(mode))
                    }
                }
            }
        },
        // Cancel is drawn with the answer above, so both layouts are the card's own and neither is Material's
        // wrap.
        dismissButton = null,
    )
}

/**
 * The scrolling half of the card: the file and its page count, the question, the range with the reader's
 * refusal under it, and the two answers as one group. It is the dialog's scrollable slot, so whatever height
 * is left goes here and the buttons keep theirs.
 */
@Composable
private fun ImportQuestions(
    request: PdfImportPrompt,
    merging: Boolean,
    onEdit: ((PdfImportPrompt) -> PdfImportPrompt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = request.mode
    val invalid = (request.range as? PageRangePlan.Invalid)?.message
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The file, as one block above the questions: its name is the longest thing here and a scan can be
        // called anything, so it gets a line of its own to wrap in.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                request.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pagesLabel(request.pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Which of its pages, and where should they go?",
            style = MaterialTheme.typography.bodyMedium,
        )
        PagesField(request = request, error = invalid, onEdit = onEdit)
        if (invalid != null) {
            Text(
                invalid,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // One group, so a screen reader counts the answers ("1 of 2") instead of reading two unrelated
        // radios that happen to sit together.
        Column(Modifier.selectableGroup()) {
            FormatOption(
                selected = mode == ImportPdfMode.APPEND,
                title = "Add to this document",
                subtitle = if (merging)
                    "Continues after the pages already open, merging the two PDFs into this document's one background."
                else "Continues after the pages already open, as one step you can undo.",
                onClick = { onEdit { it.copy(mode = ImportPdfMode.APPEND) } },
            )
            FormatOption(
                selected = mode == ImportPdfMode.REPLACE,
                title = "Replace this document's pages",
                subtitle = "The PDF's pages become this document, replacing the pages it has now.",
                onClick = { onEdit { it.copy(mode = ImportPdfMode.REPLACE) } },
            )
        }
        // The previews come after the answers so a keyboard reaches *where the pages go* before a hundred
        // page cells: the destination is one decision, the grid is a place to browse.
        ImportPagesGrid(
            prompt = request,
            onPages = { pages -> onEdit { it.copy(pages = pages) } },
        )
    }
}

/**
 * The card's footer when a row would not fit: the answer that commits on its own line, Cancel a plain word
 * under it. Both full width, so a long answer label (*Replace this document's pages*'s button) is never
 * squeezed and neither button is clipped by the window's edge.
 */
@Composable
private fun StackedActions(
    mode: ImportPdfMode,
    usable: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onConfirm, enabled = usable, modifier = Modifier.fillMaxWidth()) {
            Text(confirmLabel(mode))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/**
 * **Which pages to bring**, in the reader's own grammar: `2,4-6,9`, and `3` or `3-5` as the one-run case.
 *
 * It used to be two fields, *First page* and *Last page*, which could only ever name one continuous
 * stretch — a chapter that is pages 2, 4–6 and 9 had to be imported as 2–9 and the unwanted pages deleted
 * afterwards. One field says what an import actually is, a set of source pages, and the line under it
 * answers back: how many pages the selection is, out of how many the file has. The field takes digits, a
 * comma, a hyphen and spaces, so the refusal that the selection reader writes is about a page number and
 * never about a character the keyboard could not even type.
 *
 * A refused selection carries its `error` both as the field's own error state — so a screen reader says
 * "error" and reads the sentence with the field it belongs to — and as the one visible message under it,
 * never twice.
 */
@Composable
private fun PagesField(
    request: PdfImportPrompt,
    error: String?,
    onEdit: ((PdfImportPrompt) -> PdfImportPrompt) -> Unit,
) {
    OutlinedTextField(
        value = request.pages,
        onValueChange = { typed ->
            onEdit { it.copy(pages = typed.filter { c -> c.isDigit() || c == ',' || c == '-' || c == ' ' }.take(60)) }
        },
        label = { Text("Pages") },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(selectionReadout(request)) },
        modifier = Modifier.fillMaxWidth().then(
            if (error == null) Modifier else Modifier.semantics { this.error(error) }
        ),
    )
}

/**
 * The line under the field: the count the selection comes to, or — while it is still empty — the shape of
 * an answer. "4 of 108 pages" is the number the reader is deciding on; the file's own count is already
 * above, and saying it here is what makes the field's grammar teachable.
 */
private fun selectionReadout(request: PdfImportPrompt): String {
    val chosen = request.count
    if (chosen == 0) return "Type pages or runs, like 2,4-6,9"
    val pages = if (chosen == 1) "1 page" else "$chosen pages"
    return "$pages of ${request.pageCount}"
}

/**
 * The button that commits the card, named after what it will do rather than a generic *Import*: the two
 * answers land differently here (the pages continue this document, or replace it), and the reader should
 * not have to remember which was picked to know what the button does.
 */
private fun confirmLabel(mode: ImportPdfMode): String = when (mode) {
    ImportPdfMode.APPEND -> "Add pages"
    ImportPdfMode.REPLACE -> "Replace pages"
}

/** "3 pages" / "1 page" — a card that says "1 pages" reads as a machine talking. */
private fun pagesLabel(count: Int): String = if (count == 1) "1 page" else "$count pages"
