package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
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
 * It is a floating surface like every question here (`docs/interface-contract.md`): the family's glass and
 * spring, English copy, the accent on the commit, the document red on the destructive answer, and a plain
 * *Done*. A refusal is stated under the fields rather than hidden behind a dead button, so the reader knows
 * why the answer is not available.
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

    Column(modifier.width(420.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Manage pages", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Act on a whole run of pages at once, in one step you can undo.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(16.dp))
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
            color = if (pages is PageRangePlan.Invalid) DesktopTheme.documentRed else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Delete pages", primary = false, enabled = canDelete, destructive = true, onClick = onDelete)
            Spacer(Modifier.width(8.dp))
            PanelButton(label = "Move pages", primary = true, enabled = canMove, onClick = onMove)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Done", primary = false, enabled = true, onClick = onClose)
        }
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
 * One page number, typed: a capsule of the window's glass that takes digits only.
 *
 * Digits and a length cap, so a paste of prose cannot become a page number; the panel's own validation
 * still owns what the number means.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageNumberField(value: String, label: String, onValue: (String) -> Unit) {
    Box(
        Modifier
            .width(64.dp)
            .height(32.dp)
            .background(DesktopTheme.glass, DesktopTheme.capsuleShape)
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { typed -> onValue(typed.filter(Char::isDigit).take(4)) },
            singleLine = true,
            textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
            cursorBrush = SolidColor(DesktopTheme.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The shell's overlay for the panel: the invisible blocker — no stroke on the page behind a question — and
 * the card, on the family's spring.
 */
@Composable
internal fun BoxScope.PageRangeHost(state: DesktopShellState) {
    val pages = state.pagesSection
    val request = pages.range
    GlassPresence(visible = request != null) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            request?.let { typed ->
                PageRangePanel(
                    request = typed,
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
