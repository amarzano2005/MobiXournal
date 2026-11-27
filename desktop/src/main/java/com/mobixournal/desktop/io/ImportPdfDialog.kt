package com.mobixournal.desktop.io

import com.mobixournal.desktop.previews.ImportPdfPagesGrid
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.PropertyCheckboxRow
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.tools.rememberClosingValue

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.render.ImportPdfMode

/**
 * **Where the pages of an imported PDF go** — the one question an import asks, in the window's own grammar.
 *
 * A modal like every other question here ([DesktopDialogFrame]): the window's scrim, the opaque popup
 * surface, the accent on the answer that keeps the work, English copy, and Cancel as a plain word under it.
 * The two answers are [ImportPdfMode]', with the words the reader needs to tell them apart — *add* continues
 * after the page in view, and the second one is titled **Open in a new tab**, because that is its command
 * here: it was called *Replace this document* while it opened a tab, which named the tablet's behaviour
 * rather than this window's. A card whose title contradicts its own sentence teaches the reader not to trust
 * either.
 *
 * The card is read top-down as one story: **which file** (its name and page count, grouped above and
 * allowed to wrap — a scan can be called anything), then **which pages**, then **where they go**. The
 * button under it names the answer that is picked, so a card with no chooser still says what pressing it
 * does ([ImportPdfMode.REPLACE] there: the pages open as a tab).
 *
 * **It is sized by the room it is given, not by a number** ([DesktopDialogFrame] narrows the card to the
 * window and caps its height): a narrow window gets a narrower card instead of one spilling past its edge,
 * a short one scrolls the body — the file, the range and the two answers — while the buttons stay put. The
 * window sets no minimum size of its own, so the card's floor is the space the OS leaves it.
 */
@Composable
internal fun ImportPdfDialog(
    request: ImportPdfRequest,
    onEdit: ((ImportPdfRequest) -> ImportPdfRequest) -> Unit,
    onConfirm: (ImportPdfMode) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The answer lives in the request, like the tablet's, so it survives a re-layout and the same field is
    // what both shells read when the card is confirmed.
    val picked = request.mode
    val range = request.range
    val scroll = rememberScrollState()
    DesktopDialogFrame(
        title = "Import PDF",
        width = DialogWidth.Standard,
        onDismiss = onCancel,
        modifier = modifier,
        footer = {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            PanelButton(
                label = confirmLabel(if (request.chooseMode) picked else ImportPdfMode.REPLACE),
                primary = true,
                enabled = request.chosen != null,
                onClick = { onConfirm(if (request.chooseMode) picked else ImportPdfMode.REPLACE) },
            )
        },
    ) {
        // Only the title and the two buttons are fixed: on a short window — a split-screen pane, or 150 %
        // text — the answers must still be readable *and* Cancel still clickable.
        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
            CardHeading(request)
            Spacer(Modifier.height(12.dp))
            PagesSection(
                request = request,
                invalidMessage = (range as? PageRangePlan.Invalid)?.message,
                onEdit = onEdit,
            )
            Spacer(Modifier.height(12.dp))
            BookmarkSection(request = request, onEdit = onEdit)
            Spacer(Modifier.height(10.dp))
            val sizeMb = request.file.length() / (1024.0 * 1024.0)
            val sizeLabel = if (sizeMb >= 1.0) "%.1f MB".format(sizeMb) else "${(request.file.length() / 1024L).coerceAtLeast(1L)} KB"
            PropertyCheckboxRow(
                label = "Embed PDF in document ($sizeLabel)",
                checked = request.embedPdf,
                onChange = { on -> onEdit { it.copy(embedPdf = on) } },
            )
            if (request.chooseMode) {
                Spacer(Modifier.height(14.dp))
                ModeChoices(
                    picked = picked,
                    onPick = { mode -> onEdit { it.copy(mode = mode) } },
                )
            }
            // The previews come after the answers so a keyboard reaches *where the pages go* before a
            // hundred page cells: the destination is one decision, the grid is a place to browse.
            Spacer(Modifier.height(12.dp))
            ImportPdfPagesGrid(request = request, onEdit = onEdit)
        }
    }
}

/**
 * **The index row the import offers**, and its title: on by default, named after the file, editable.
 *
 * The title starts as the file's own name with only its `.pdf` dropped, so the common answer needs no typing;
 * turning the row off leaves the PDF's chapters joining the index at the top level, which is what an import
 * did before this row existed. The field stays visible but disabled when the row is off, so the card does not
 * change height under a pointer reaching for it. Both are ordinary controls — a checkbox row and a field —
 * rather than the boolean-toggle pair of buttons this section used to be: a button labelled *No bookmark*
 * asks the reader to work out whether it describes the state or the command.
 */
@Composable
private fun BookmarkSection(
    request: ImportPdfRequest,
    onEdit: ((ImportPdfRequest) -> ImportPdfRequest) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PropertyCheckboxRow("A bookmark for this file", request.bookmark) { on ->
            onEdit { it.copy(bookmark = on) }
        }
        Spacer(Modifier.height(6.dp))
        DesktopTextField(
            value = request.bookmarkTitle,
            onValueChange = { title -> onEdit { it.copy(bookmarkTitle = title.take(80)) } },
            placeholder = "Bookmark title",
            modifier = Modifier.fillMaxWidth(),
            height = 32.dp,
            enabled = request.bookmark,
        )
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
 * **Which pages to bring**, in the reader's own grammar: `2,4-6,9`, and `3` or `3-5` as the one-run case.
 *
 * The card used to hold two fields, *First page* and *Last page*, which can only name one continuous
 * stretch; a chapter that is pages 2, 4–6 and 9 had to be brought as 2–9. One field says what an import
 * is — a set of source pages — and the words beside it answer back with the count the selection comes to,
 * out of how many the file has. The field takes digits, a comma, a hyphen and spaces: the refusal that
 * follows is always about a page number, never about a keystroke the field could not hold.
 */
@Composable
private fun PagesSection(
    request: ImportPdfRequest,
    invalidMessage: String?,
    onEdit: ((ImportPdfRequest) -> ImportPdfRequest) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Pages", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.width(168.dp).semantics {
                contentDescription = "Pages to import"
                if (invalidMessage != null) this.error(invalidMessage)
            },
        ) {
            DesktopTextField(
                value = request.pages,
                onValueChange = { typed ->
                    onEdit { it.copy(pages = typed.filter { c -> c.isDigit() || c == ',' || c == '-' || c == ' ' }.take(60)) }
                },
                modifier = Modifier.fillMaxWidth(),
                error = invalidMessage != null,
                height = 32.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(selectionReadout(request), color = DesktopTheme.inkMuted, style = DesktopTheme.label)
    }
    if (invalidMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(invalidMessage, color = DesktopTheme.danger, style = DesktopTheme.label)
    }
}

/**
 * The line beside the field: the count the selection comes to, or — while it names nothing — the shape of
 * an answer. "4 of 108 pages" is the number the reader is deciding on; the file's own count is already
 * above, and saying it here is what makes the field's grammar teachable.
 */
private fun selectionReadout(request: ImportPdfRequest): String {
    val chosen = request.chosen?.size ?: 0
    if (chosen == 0) return "pages or runs, like 2,4-6,9"
    val pages = if (chosen == 1) "1 page" else "$chosen pages"
    return "$pages of ${request.pageCount}"
}

/** The two answers, with the accent and the mark on the one that keeps the work. */
@Composable
private fun ModeChoices(picked: ImportPdfMode, onPick: (ImportPdfMode) -> Unit) {
    // One group, so a screen reader counts the answers ("1 of 2") instead of reading two unrelated radios.
    Column(
        Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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

/**
 * One answer of [ImportPdfDialog]: its name, its sentence, and the mark that says it is the picked one.
 *
 * It is a **radio**, not a row that watches for taps: `selectable` gives it the radio's role and its
 * selected state in the semantics tree (so a screen reader reads the answer, the sentence and whether it is
 * the picked one), makes it reachable with Tab, and answers Space and Enter the way every other radio does.
 * [WindowKeys.focusControl] tells the window that this control owns Enter, Space and the arrows while it is
 * focused, so pressing Space selects the answer instead of panning the page behind the card.
 *
 * The row wears the contract's control corner (4 dp) and the accent ring on its own rim while it has the
 * keyboard, like every other control in a card.
 */
@Composable
private fun ImportModeRow(title: String, note: String, picked: Boolean, onPick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(
                DesktopTheme.controlShape,
                active = picked,
                base = if (picked) DesktopTheme.activeSlot else Color.Transparent,
                focused = focused,
            )
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, it.isFocused)
            }
            .selectable(
                selected = picked,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            )
            // The whole answer reads as one sentence to a screen reader — name, then its note — instead of
            // the two text runs and a radio between them.
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $note"
            }
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
 * The shell's overlay for the question: the card, on the family's spring, held for its exit. The frame
 * supplies the blocker — nothing on the page behind a question may take a stroke.
 *
 * With no document open there is still an answer: the second one opens the PDF as a tab of its own, so the
 * card is drawn whenever an import is pending, unlike a Save As.
 */
@Composable
internal fun BoxScope.ImportPdfHost(state: DesktopShellState) {
    val request = rememberClosingValue(state.importRequest)
    GlassPresence(visible = state.importRequest != null) {
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            if (request != null) {
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
