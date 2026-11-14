package com.mobixournal.desktop.io
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.PageNumberField
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton

import com.mobixournal.design.ImportCardLayout
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.render.ImportPdfMode

/**
 * **Where the pages of an imported PDF go** — the one question an import asks, in the window's own grammar.
 *
 * A floating surface like every question here (`docs/interface-contract.md`): the floating glass, the
 * accent on the answer that keeps the work, English copy, and Cancel as a plain word under it. The two
 * answers are [ImportPdfMode]', with the words the reader needs to tell them apart — *add* continues after
 * the page in view, and the second one is titled **Open in a new tab**, because that is its command here:
 * it was called *Replace this document* while it opened a tab, which named the tablet's behaviour rather
 * than this window's. A card whose title contradicts its own sentence teaches the reader not to trust
 * either.
 *
 * The card is read top-down as one story: **which file** (its name and page count, grouped above and
 * allowed to wrap — a scan can be called anything), then **which pages**, then **where they go**. The
 * button under it names the answer that is picked, so a card with no chooser still says what pressing it
 * does ([ImportPdfMode.REPLACE] there: the pages open as a tab).
 *
 * **It is sized by the room it is given, not by a number** ([ImportCardLayout]): a narrow window gets a
 * narrower card instead of one spilling past its edge, a short one scrolls the body — the file, the range
 * and the two answers — while the buttons stay put, and a large text scale stacks the two page fields
 * rather than squeezing their labels out of the row. The window sets no minimum size of its own, so the
 * card's floor is the space the OS leaves it.
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
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val width = ImportCardLayout.cardWidth(maxWidth.value).dp
        val stacked = ImportCardLayout.stacked(maxWidth.value, LocalDensity.current.fontScale)
        Column(
            // The caller's modifier lands on the card itself, so a harness can measure it and a host can
            // style it without reaching through the centring box.
            modifier
                .width(width)
                .heightIn(max = (maxHeight - ImportCardLayout.EDGE_DP.dp * 2).coerceAtLeast(0.dp))
                .glassSpring()
                .glassSurface()
                .padding(18.dp),
        ) {
            Text("Import PDF", color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.height(8.dp))
            // Only the title and the two buttons are fixed: on a short window — a split-screen pane, or
            // 150 % text — the answers must still be readable *and* Cancel still clickable, and a fixed
            // header would spend the height twice over before the body got any of it.
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                CardHeading(request)
                Spacer(Modifier.height(12.dp))
                RangeSection(
                    request = request,
                    stacked = stacked,
                    usable = usable,
                    invalidMessage = (range as? PageRangePlan.Invalid)?.message,
                    onEdit = onEdit,
                )
                if (request.chooseMode) {
                    Spacer(Modifier.height(14.dp))
                    ModeChoices(picked = picked, onPick = { picked = it })
                }
            }
            Spacer(Modifier.height(16.dp))
            Actions(
                row = !stacked,
                label = confirmLabel(if (request.chooseMode) picked else ImportPdfMode.REPLACE),
                enabled = usable,
                onConfirm = { onConfirm(if (request.chooseMode) picked else ImportPdfMode.REPLACE) },
                onCancel = onCancel,
            )
        }
    }
}

/** The file, as one block: its name (wrapping, never widening the card) with its page count under it. */
@Composable
private fun CardHeading(request: ImportPdfRequest) {
    Text(
        request.file.name,
        color = DesktopTheme.ink,
        style = DesktopTheme.body,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Text(pages(request.pageCount), color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    Spacer(Modifier.height(8.dp))
    Text(
        if (request.chooseMode) {
            "Which of its pages, and where should they go?"
        } else {
            "Which of its pages should be imported?"
        },
        color = DesktopTheme.inkSecondary,
        style = DesktopTheme.body,
    )
}

/**
 * The card's two buttons: one row when the card is roomy, one above the other when it is not — a row that
 * does not fit its own labels is worse than a stack, and the confirm button's words change with the answer.
 */
@Composable
private fun Actions(row: Boolean, label: String, enabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    if (row) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PanelButton(label = label, primary = true, enabled = enabled, onClick = onConfirm)
        }
    } else {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            PanelButton(label = label, primary = true, enabled = enabled, onClick = onConfirm)
            Spacer(Modifier.height(6.dp))
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
        }
    }
}

/**
 * The range, on one line when there is room for it and one field per line when there is not.
 *
 * The stacked form says each field's name out loud: two bare capsules over one another are a puzzle,
 * and at 150 % text the words would not have fitted the row anyway.
 */
@Composable
private fun RangeSection(
    request: ImportPdfRequest,
    stacked: Boolean,
    usable: Boolean,
    invalidMessage: String?,
    onEdit: ((ImportPdfRequest) -> ImportPdfRequest) -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RangeLine(
                label = "First page",
                value = request.first,
                field = "First page to import",
                onValue = { typed -> onEdit { it.copy(first = typed) } },
            )
            RangeLine(
                label = "Last page",
                value = request.last,
                field = "Last page to import",
                onValue = { typed -> onEdit { it.copy(last = typed) } },
            )
            Text("of ${request.pageCount}", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
    } else {
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
    }
    if (!usable) {
        Spacer(Modifier.height(8.dp))
        Text(
            invalidMessage ?: "These pages cannot be imported.",
            color = DesktopTheme.documentRed,
            style = DesktopTheme.label,
        )
    }
}

/** One field with its word in front, for a card too narrow (or too large-typed) to hold both. */
@Composable
private fun RangeLine(label: String, value: String, field: String, onValue: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        Spacer(Modifier.width(10.dp))
        PageNumberField(value = value, label = field, onValue = onValue)
    }
}

/** The two answers, with the accent and the mark on the one that keeps the work. */
@Composable
private fun ModeChoices(picked: ImportPdfMode, onPick: (ImportPdfMode) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ImportModeRow(
            title = "Add to this document",
            note = "Continues after the page in view, as one step you can undo.",
            picked = picked == ImportPdfMode.APPEND,
            onPick = { onPick(ImportPdfMode.APPEND) },
        )
        ImportModeRow(
            title = "Open in a new tab",
            note = "The PDF's pages become a document of their own; the open one is left alone.",
            picked = picked == ImportPdfMode.REPLACE,
            onPick = { onPick(ImportPdfMode.REPLACE) },
        )
    }
}

/** "3 pages" / "1 page" — a card that says "1 pages" reads as a machine talking. */
private fun pages(count: Int): String = if (count == 1) "1 page" else "$count pages"

/**
 * The button that commits the card, named after the command it runs: [ImportPdfMode.REPLACE] opens a tab
 * here, so it says so — including when there was nothing to choose and the card went straight to that
 * answer.
 */
private fun confirmLabel(mode: ImportPdfMode): String = when (mode) {
    ImportPdfMode.APPEND -> "Add pages"
    ImportPdfMode.REPLACE -> "Open in a new tab"
}

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
 * With no document open there is still an answer: the second one opens the PDF as a tab of its own, so the
 * card is drawn whenever an import is pending, unlike a Save As.
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
