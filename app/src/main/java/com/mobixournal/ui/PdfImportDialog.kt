package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 */
@Composable
fun ImportPdfDialog(
    request: PdfImportPrompt,
    merging: Boolean,
    onEdit: ((PdfImportPrompt) -> PdfImportPrompt) -> Unit,
    onConfirm: (ImportPdfMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember(request) { mutableStateOf(request.mode) }
    val range = request.range
    val usable = range is PageRangePlan.Pages
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The file, as one block above the questions: its name is the longest thing here and a
                // scan can be called anything, so it gets a line of its own to wrap in.
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PageField(
                        value = request.first,
                        label = "First page",
                        onValue = { typed -> onEdit { it.copy(first = typed) } },
                        modifier = Modifier.weight(1f),
                    )
                    Text("to", style = MaterialTheme.typography.bodyMedium)
                    PageField(
                        value = request.last,
                        label = "Last page",
                        onValue = { typed -> onEdit { it.copy(last = typed) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (range is PageRangePlan.Invalid) {
                    Text(
                        range.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FormatOption(
                    selected = mode == ImportPdfMode.APPEND,
                    title = "Add to this document",
                    subtitle = if (merging)
                        "Continues after the pages already open, merging the two PDFs into this document's one background."
                    else "Continues after the pages already open, as one step you can undo.",
                    onClick = { mode = ImportPdfMode.APPEND },
                )
                FormatOption(
                    selected = mode == ImportPdfMode.REPLACE,
                    title = "Replace this document's pages",
                    subtitle = "The PDF's pages become this document, replacing the pages it has now.",
                    onClick = { mode = ImportPdfMode.REPLACE },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode) }, enabled = usable) { Text(confirmLabel(mode)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One typed page number: digits only, so the range reader never has to explain a letter. */
@Composable
private fun PageField(
    value: String,
    label: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValue(typed.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
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
