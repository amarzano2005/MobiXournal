package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mobixournal.render.ExportPageScope
import com.mobixournal.render.ExportPages
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRanges
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog

/**
 * The **Export PDF** card's host: the flag on [EditorUiState.exportPdfOpen], the card itself, and the answer
 * it hands on.
 *
 * The card lives here, beside the other choosers, for the reason `Export As` does: the flag is on [ui], so the
 * answer outlives the toolbar or menu that asked the question. The page count and the page in view are read
 * when the card is composed — the answer is about the document *now*, which is also what the export will be
 * handed.
 */
@Composable
internal fun ExportPdfHost(
    open: Boolean,
    pageCount: Int,
    currentPage: Int,
    onExport: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogPresence(open) {
        ExportPdfDialog(
            pageCount = pageCount,
            currentPage = currentPage,
            onExport = { pages ->
                onDismiss()
                onExport(pages)
            },
            onDismiss = onDismiss,
        )
    }
}

/**
 * **Export PDF** — which pages the file should hold, asked in a card before the system asks where it goes.
 *
 * The export used to be the whole notebook, always, with no way to say otherwise. The three answers are the
 * ones a reader actually has: everything, the page in front of them, or a set they name — and the third speaks
 * the grammar the import card already teaches (`2,4-6,9`), because naming pages is one skill, not two.
 *
 * The rules are [ExportPages]: the same function the window's card reads, so the two shells cannot write
 * different pages from the same answer. The indices are the **notebook's**, which is why a reordered notebook
 * exports its pages in the order they read rather than the order they were imported in.
 */
@Composable
private fun ExportPdfDialog(
    pageCount: Int,
    currentPage: Int,
    onExport: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var scope by remember { mutableStateOf(ExportPageScope.ALL) }
    var typed by remember { mutableStateOf(PageRanges.whole(pageCount)) }
    val plan = ExportPages.resolve(scope, currentPage, typed, pageCount)
    val chosen = plan.indices().orEmpty()
    val error = (plan as? PageRangePlan.Invalid)?.message
    val usable = chosen.isNotEmpty()
    val page = (currentPage + 1).coerceIn(1, pageCount.coerceAtLeast(1))

    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (pageCount == 1) "One page in this notebook." else "$pageCount pages in this notebook.",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text("Pages", style = MaterialTheme.typography.labelMedium)
                // One group, so a screen reader counts the answers instead of reading three unrelated radios.
                Column(Modifier.selectableGroup()) {
                    FormatOption(
                        selected = scope == ExportPageScope.ALL,
                        title = "All pages",
                        subtitle = if (pageCount == 1) {
                            "The one page this notebook has."
                        } else {
                            "All $pageCount pages, in the order they read."
                        },
                        onClick = { scope = ExportPageScope.ALL },
                    )
                    FormatOption(
                        selected = scope == ExportPageScope.CURRENT,
                        title = "This page",
                        subtitle = "Only page $page, the one in view.",
                        onClick = { scope = ExportPageScope.CURRENT },
                    )
                    FormatOption(
                        selected = scope == ExportPageScope.SELECTED,
                        title = "Pages…",
                        subtitle = "The pages you name, like 2,4-6,9.",
                        onClick = { scope = ExportPageScope.SELECTED },
                    )
                }
                if (scope == ExportPageScope.SELECTED) {
                    PagesField(
                        value = typed,
                        error = error,
                        onValue = { typed = it.filter { c -> c.isDigit() || c == ',' || c == '-' || c == ' ' }.take(60) },
                    )
                }
                // The count is the number the reader is deciding on: the same line whether the answer was a
                // tap or a typed run, so the card says one thing about every way of answering.
                Text(
                    if (error != null) error else ExportPages.readout(plan, pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(chosen) }, enabled = usable) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The field the **Pages…** answer types into: digits, a comma, a hyphen and spaces, so a refusal is always
 * about a page number and never about a character the keyboard could not hold. A refused answer carries its
 * `error` on the field's own node, so a screen reader says "error" and reads the sentence with it.
 */
@Composable
private fun PagesField(value: String, error: String?, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text("Pages") },
        singleLine = true,
        isError = error != null,
        modifier = Modifier.fillMaxWidth().then(
            if (error == null) Modifier else Modifier.semantics { this.error(error) },
        ),
    )
}
