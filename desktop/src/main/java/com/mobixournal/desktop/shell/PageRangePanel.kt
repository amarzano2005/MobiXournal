package com.mobixournal.desktop.shell

import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.rememberClosingValue
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.ink.of

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * **Manage pages** — the page section's advanced half: a range of pages moved at once, or removed at once.
 *
 * The page list's one-step rows (move up, move down, remove) are the right tools for a sheet or two; a
 * forty-page scan reordered or pruned a chapter at a time is not a place for forty presses. So this panel
 * asks the question in the list's own numbering — *pages 3 to 7* — and acts on the whole run as **one**
 * undo step, through the same `PageOps` edits the rest of the list uses.
 *
 * It is a modal like every other question here ([DesktopDialogFrame]): the window's scrim, the opaque popup
 * surface, English copy, the accent on the move, the refusal red on the destructive answer, and a plain
 * *Done*. A refusal is stated under the fields rather than hidden behind a dead button, so the reader knows
 * why the answer is not available — and it is written in [DesktopTheme.danger], the refusal red, never the
 * PDF's document red.
 */
@Composable
internal fun PageRangePanel(
    request: PageRangeRequest,
    pageCount: Int,
    onEdit: ((PageRangeRequest) -> PageRangeRequest) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = PageRanges.pages(request, pageCount)
    val target = PageRanges.target(request, pageCount)
    val moveRefusal = PageRanges.moveRefusal(pages, target)
    val deleteRefusal = PageRanges.deleteRefusal(pages, pageCount)
    val canMove = pages is PageRangePlan.Pages && target is PageRangePlan.Pages && moveRefusal == null
    val canDelete = pages is PageRangePlan.Pages && deleteRefusal == null

    DesktopDialogFrame(
        title = "Manage pages",
        width = DialogWidth.Standard,
        subtitle = "Act on a whole run of pages at once, in one step you can undo.",
        onDismiss = onClose,
        modifier = modifier,
        // One row of answers, in the order they are read: what takes pages away, what moves them, and the
        // plain word that closes the card.
        footer = {
            PanelButton(
                label = "Delete pages",
                primary = false,
                enabled = canDelete,
                destructive = true,
                onClick = onDelete,
            )
            PanelButton(label = "Move pages", primary = true, enabled = canMove, onClick = onMove)
            PanelButton(label = "Done", primary = false, enabled = true, onClick = onClose)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pages", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            Spacer(Modifier.width(10.dp))
            PageNumberField(
                value = request.first,
                label = "First page of the range",
                onValue = { typed -> onEdit { it.copy(first = typed) } },
            )
            Spacer(Modifier.width(8.dp))
            Text("to", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            Spacer(Modifier.width(8.dp))
            PageNumberField(
                value = request.last,
                label = "Last page of the range",
                onValue = { typed -> onEdit { it.copy(last = typed) } },
            )
            Spacer(Modifier.width(10.dp))
            Text("of $pageCount", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start them on page", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            Spacer(Modifier.width(10.dp))
            PageNumberField(
                value = request.target,
                label = "Page the range should start on",
                onValue = { typed -> onEdit { it.copy(target = typed) } },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            noteFor(pages, target, moveRefusal, deleteRefusal),
            color = if (pages is PageRangePlan.Invalid) DesktopTheme.danger else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
        )
        Spacer(Modifier.height(2.dp))
    }
}

/** The one sentence under the fields: what is wrong, in the order the reader would fix it. */
private fun noteFor(
    pages: PageRangePlan,
    target: PageRangePlan,
    moveRefusal: String?,
    deleteRefusal: String?,
): String = when {
    pages is PageRangePlan.Invalid -> pages.message
    deleteRefusal != null -> deleteRefusal
    target is PageRangePlan.Invalid -> target.message
    moveRefusal != null -> moveRefusal
    else -> "Moving keeps every page; deleting takes the run out of the document. The PDF itself is untouched."
}

/**
 * One page number, typed: the window's one field ([DesktopTextField]) that takes digits only.
 *
 * Digits and a length cap, so a paste of prose cannot become a page number; the panel's own validation
 * still owns what the number means. Its spoken name and its refusal travel with the field, so a reader who
 * never sees the sentence under the row still meets it.
 */
@Composable
internal fun PageNumberField(
    value: String,
    label: String,
    onValue: (String) -> Unit,
    /** The sentence that makes this field unusable, if any — carried to a screen reader with the field. */
    errorText: String? = null,
) {
    Box(
        Modifier.width(64.dp).semantics {
            contentDescription = label
            if (errorText != null) this.error(errorText)
        },
    ) {
        DesktopTextField(
            value = value,
            onValueChange = { typed -> onValue(typed.filter(Char::isDigit).take(4)) },
            modifier = Modifier.fillMaxWidth(),
            error = errorText != null,
            height = 32.dp,
        )
    }
}

/**
 * The shell's overlay for the panel: the card, on the family's spring, held for its exit. The frame
 * supplies the blocker — no stroke lands on the page behind a question.
 */
@Composable
internal fun BoxScope.PageRangeHost(state: DesktopShellState) {
    val pages = state.pagesSection
    val request = rememberClosingValue(pages.range)
    GlassPresence(visible = pages.range != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (request != null) {
                PageRangePanel(
                    request = request,
                    pageCount = state.active?.document?.pageCount ?: 0,
                    onEdit = pages::editRange,
                    onMove = { pages.moveRange() },
                    onDelete = { pages.deleteRange() },
                    onClose = pages::closeRange,
                )
            }
        }
    }
}
