package com.mobixournal.desktop

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.render.ImportPdfMode

/**
 * **Where the pages of an imported PDF go** — the one question an import asks, in the window's own grammar.
 *
 * A floating surface like every question here (`docs/interface-contract.md`): the floating glass, the
 * accent on the answer that keeps the work, English copy, and Cancel as a plain word under it. The two
 * answers are [ImportPdfMode]', with the words the reader needs to tell them apart — *add* continues after
 * the page in view, *replace* opens the PDF as its own tab and leaves what is open alone.
 *
 * The card names the file and its page count, because an import can bring a hundred pages and that is a
 * fact worth knowing before the button is pressed — and it asks **which** of them, because bringing a
 * chapter out of a four-hundred-page scan is a normal thing to want and rasterising the rest is not.
 */
@Composable
internal fun ImportPdfDialog(
    request: ImportPdfRequest,
    onEdit: ((ImportPdfRequest) -> ImportPdfRequest) -> Unit,
    onConfirm: (ImportPdfMode) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by remember(request) { mutableStateOf(request.mode) }
    val range = request.range
    val usable = range is PageRangePlan.Pages
    Column(modifier.width(420.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Import a PDF", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "“${request.file.name}” has ${pages(request.pageCount)}. Which of them, and where should they go?",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pages", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            Spacer(Modifier.width(10.dp))
            PageNumberField(
                value = request.first,
                label = "First page to import",
                onValue = { typed -> onEdit { it.copy(first = typed) } },
            )
            Spacer(Modifier.width(8.dp))
            Text("to", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            Spacer(Modifier.width(8.dp))
            PageNumberField(
                value = request.last,
                label = "Last page to import",
                onValue = { typed -> onEdit { it.copy(last = typed) } },
            )
            Spacer(Modifier.width(10.dp))
            Text("of ${request.pageCount}", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        if (!usable) {
            Spacer(Modifier.height(8.dp))
            Text(
                (range as PageRangePlan.Invalid).message,
                color = DesktopTheme.documentRed,
                style = DesktopTheme.label,
            )
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ImportModeRow(
                title = "Add the pages to this document",
                note = "Continues after the page in view, as one step you can undo.",
                picked = picked == ImportPdfMode.APPEND,
                onPick = { picked = ImportPdfMode.APPEND },
            )
            ImportModeRow(
                title = "Replace this document",
                note = "Opens the PDF in a new tab and leaves the open document alone.",
                picked = picked == ImportPdfMode.REPLACE,
                onPick = { picked = ImportPdfMode.REPLACE },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PanelButton(label = "Import", primary = true, enabled = usable, onClick = { onConfirm(picked) })
        }
    }
}

/** "3 pages" / "1 page" — a card that says "1 pages" reads as a machine talking. */
private fun pages(count: Int): String = if (count == 1) "1 page" else "$count pages"

/** One answer of [ImportPdfDialog]: its name, its sentence, and the mark that says it is the picked one. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ImportModeRow(title: String, note: String, picked: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(
                DesktopTheme.railSlotShape,
                active = picked,
                base = if (picked) DesktopTheme.activeSlot else Color.Transparent,
            )
            .pointerInput(title) { detectTapGestures { onPick() } }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (picked) DesktopTheme.accent else DesktopTheme.ink,
                style = DesktopTheme.body,
            )
            Spacer(Modifier.height(2.dp))
            Text(note, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(12.dp)
                .background(
                    if (picked) DesktopTheme.accent else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .border(
                    1.dp,
                    if (picked) DesktopTheme.accent else DesktopTheme.hairline,
                    DesktopTheme.capsuleShape,
                ),
        )
    }
}

/**
 * The shell's overlay for the question: the invisible blocker — nothing on the page behind a question may
 * take a stroke — and the card, on the family's spring.
 *
 * With no document open there is still an answer: *Replace* opens the PDF as a tab of its own, so the card
 * is drawn whenever an import is pending, unlike a Save As.
 */
@Composable
internal fun BoxScope.ImportPdfHost(state: DesktopShellState) {
    GlassPresence(visible = state.importRequest != null) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            state.importRequest?.let { request ->
                ImportPdfDialog(
                    request = request,
                    onEdit = state::editImportPdf,
                    onConfirm = { mode -> state.confirmImportPdf(mode) },
                    onCancel = { state.cancelImportPdf() },
                )
            }
        }
    }
}
