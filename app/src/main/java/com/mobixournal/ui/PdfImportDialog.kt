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
                Text(
                    "“${request.name}” has ${pagesLabel(request.pageCount)}. Which of them, and where " +
                        "should they go?",
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
                    title = "Replace",
                    subtitle = "The PDF's pages become this document, replacing the current pages.",
                    onClick = { mode = ImportPdfMode.REPLACE },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode) }, enabled = usable) { Text("Import") }
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

/** "3 pages" / "1 page" — a card that says "1 pages" reads as a machine talking. */
private fun pagesLabel(count: Int): String = if (count == 1) "1 page" else "$count pages"
