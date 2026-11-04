package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges
import com.mobixournal.ui.theme.FloatingDialog

/**
 * **Manage pages** — the tablet's half of the window's `PageRangePanel`: a run of pages moved to
 * another place in the document, or taken out of it, as **one** undoable step.
 *
 * The one-step rows in the Pages menu are the right tools for a sheet or two; a forty-page scan
 * reordered or pruned a chapter at a time is not a place for forty presses. So the question is asked
 * in the page list's own numbering — *pages 3 to 7, starting on page 1* — and answered by the whole run
 * at once.
 *
 * Every rule lives in the shared `PageRanges`: what the fields mean, what a dead button means, and the
 * sentence under them. The window's panel reads the same three functions, so "pages 3 to 7" is the same
 * range on both shells and a refusal is never one shell's opinion.
 */
@Composable
internal fun PageRangeDialog(
    request: PageRangeRequest,
    pageCount: Int,
    onEdit: (PageRangeRequest) -> Unit,
    onMove: (first: Int, last: Int, target: Int) -> Unit,
    onDelete: (first: Int, last: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pages = PageRanges.pages(request, pageCount)
    val target = PageRanges.target(request, pageCount)
    val moveRefusal = PageRanges.moveRefusal(pages, target)
    val deleteRefusal = PageRanges.deleteRefusal(pages, pageCount)
    val canMove = pages is PageRangePlan.Pages && target is PageRangePlan.Pages && moveRefusal == null
    val canDelete = pages is PageRangePlan.Pages && deleteRefusal == null
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage pages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Act on a whole run of pages at once, in one step you can undo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Pages", style = MaterialTheme.typography.bodyMedium)
                    RangeField(request.first, "First page of the range") { onEdit(request.copy(first = it)) }
                    Text("to", style = MaterialTheme.typography.bodyMedium)
                    RangeField(request.last, "Last page of the range") { onEdit(request.copy(last = it)) }
                }
                Text("of $pageCount", style = MaterialTheme.typography.labelSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Start them on page", style = MaterialTheme.typography.bodyMedium)
                    RangeField(request.target, "Page the range should start on") {
                        onEdit(request.copy(target = it))
                    }
                }
                // A refusal is stated under the fields rather than hidden behind a dead button, so the
                // reader knows why the answer they typed cannot be used.
                Text(
                    rangeNote(pages, target, moveRefusal, deleteRefusal),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pages is PageRangePlan.Invalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = canDelete,
                    onClick = {
                        if (pages is PageRangePlan.Pages) onDelete(pages.first, pages.last)
                    },
                ) { Text("Delete pages") }
                TextButton(
                    enabled = canMove,
                    onClick = {
                        if (pages is PageRangePlan.Pages && target is PageRangePlan.Pages) {
                            onMove(pages.first, pages.last, target.first)
                        }
                    },
                ) { Text("Move pages") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** The one sentence under the fields: what is wrong, in the order the reader would fix it. */
private fun rangeNote(
    pages: PageRangePlan,
    target: PageRangePlan,
    moveRefusal: String?,
    deleteRefusal: String?,
): String = when {
    pages is PageRangePlan.Invalid -> pages.message
    deleteRefusal != null -> deleteRefusal
    target is PageRangePlan.Invalid -> target.message
    moveRefusal != null -> moveRefusal
    else -> "Moving keeps every page; deleting takes the run out of the document."
}

/**
 * One page number, typed: digits only, four characters at most — so a paste of prose cannot become a
 * page number, while the shared rules above still own what the number means.
 */
@Composable
private fun RangeField(value: String, label: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValue(typed.filter(Char::isDigit).take(4)) },
        singleLine = true,
        placeholder = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.width(110.dp),
    )
}
