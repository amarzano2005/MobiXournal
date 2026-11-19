package com.mobixournal.desktop.shell

import com.mobixournal.desktop.io.DesktopExportQueue
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DesktopTextField
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.tools.rememberClosingValue
import com.mobixournal.render.ExportPageScope
import com.mobixournal.render.ExportPages
import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * **Export PDF** in the window — which pages the file should hold, asked before the system asks where.
 *
 * The export used to be the whole notebook, always. Importing can already name pages (`2,4-6,9`), so
 * exporting names them the same way, through the same reader ([ExportPages] / [PageRanges]): a chapter the
 * reader brought in can be handed on without a PDF editor in between. The three answers are the three a
 * reader has — everything, the page in view, a set they name — and *All pages* is the one the card opens on,
 * because that is what an export always was.
 *
 * The indices are the **notebook's**, so a reordered notebook writes its pages in the order they read on
 * screen, and a refusal names the sheet by the number the page counter shows. It is a modal like every
 * other question ([DesktopDialogFrame]) and a refusal is written in [DesktopTheme.danger] — the refusal
 * red, never the PDF's document red.
 */
data class ExportPdfScope(
    /** How many pages the document has — the card counts its answer against this. */
    val pageCount: Int,
    /** The page in view when the card was opened. */
    val current: Int,
    /** The answer being considered. */
    val scope: ExportPageScope = ExportPageScope.ALL,
    /** The pages typed for [ExportPageScope.SELECTED], as the reader types them (1-based). */
    val pages: String = PageRanges.whole(pageCount),
) {
    /** The answer as indices, or the sentence saying why it names none. */
    internal val plan: PageRangePlan get() = ExportPages.resolve(scope, current, pages, pageCount)

    /** The notebook indices to write, or null while the field is refused. */
    internal val chosen: List<Int>? get() = plan.indices()
}

/**
 * The card: one group of three answers, the field the third one types into, the count the answer comes to, and
 * the two buttons the family uses.
 *
 * It is the import card's grammar turned around — the same rule rows (radio role, the accent mark, the ring on
 * the focused row, Space and Enter taking the answer rather than panning the page behind), the same field and
 * the same readout under it. A reader who has imported a chapter already knows how to export one.
 */
@Composable
internal fun ExportPdfDialog(
    request: ExportPdfScope,
    onEdit: ((ExportPdfScope) -> ExportPdfScope) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val plan = request.plan
    val error = (plan as? PageRangePlan.Invalid)?.message
    val usable = request.chosen?.isNotEmpty() == true
    DesktopDialogFrame(
        title = "Export PDF",
        width = DialogWidth.Standard,
        subtitle = if (request.pageCount == 1) {
            "One page in this document."
        } else {
            "${request.pageCount} pages in this document."
        },
        onDismiss = onCancel,
        footer = {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            PanelButton(label = "Export", primary = true, enabled = usable, onClick = onConfirm)
        },
    ) {
        Text("Pages", color = DesktopTheme.inkSecondary, style = DesktopTheme.label)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ScopeRow(
                title = "All pages",
                note = if (request.pageCount == 1) {
                    "The one page this document has."
                } else {
                    "All ${request.pageCount} pages, in the order they read."
                },
                picked = request.scope == ExportPageScope.ALL,
                onPick = { onEdit { it.copy(scope = ExportPageScope.ALL) } },
            )
            ScopeRow(
                title = "This page",
                note = "Only page ${pageInView(request)}, the one in view.",
                picked = request.scope == ExportPageScope.CURRENT,
                onPick = { onEdit { it.copy(scope = ExportPageScope.CURRENT) } },
            )
            ScopeRow(
                title = "Pages…",
                note = "The pages you name, like 2,4-6,9.",
                picked = request.scope == ExportPageScope.SELECTED,
                onPick = { onEdit { it.copy(scope = ExportPageScope.SELECTED) } },
            )
        }
        if (request.scope == ExportPageScope.SELECTED) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pages", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
                Spacer(Modifier.width(10.dp))
                ScopeField(
                    value = request.pages,
                    label = "Pages to export",
                    errorText = error,
                    onValue = { typed ->
                        onEdit { it.copy(pages = typed.filter { c -> c.isDigit() || c == ',' || c == '-' || c == ' ' }.take(60)) }
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // The number the reader is deciding on, whatever way they answered: one line, both answers.
        Text(
            if (error != null) error else ExportPages.readout(plan, request.pageCount),
            color = if (error != null) DesktopTheme.danger else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
        )
    }
}

/** The page number the *This page* answer names, 1-based and inside the document. */
private fun pageInView(request: ExportPdfScope): Int =
    (request.current + 1).coerceIn(1, request.pageCount.coerceAtLeast(1))

/**
 * One answer of the card: its name, its sentence, and the mark that says it is the picked one.
 *
 * A **radio**, not a row that watches for taps: `selectable` gives it the role and the selected state in the
 * semantics tree, makes it reachable with Tab, and answers Space and Enter as every other radio does — and
 * [WindowKeys.focusControl] tells the window that this control owns those keys while focused, so Space
 * selects the answer instead of panning the page behind the card. The 12 dp dot is the mark the import card
 * draws on its own answers, so the two cards cannot disagree about what a picked answer looks like.
 */
@Composable
private fun ScopeRow(title: String, note: String, picked: Boolean, onPick: () -> Unit) {
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
            .onFocusChanged { state ->
                focused = state.isFocused
                WindowKeys.focusControl(owner, state.isFocused)
            }
            .selectable(
                selected = picked,
                interactionSource = interactions,
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$title. $note" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (picked) DesktopTheme.accent else DesktopTheme.ink,
                style = DesktopTheme.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
 * The selection's field: it holds a sentence the reader types rather than one number, it says its own name to a
 * screen reader, and a refusal travels on the field's node (`error`) as well as on its rim, so a keyboard-only
 * reader meets the sentence with the control that caused it.
 */
@Composable
private fun ScopeField(value: String, label: String, errorText: String?, onValue: (String) -> Unit) {
    Box(
        Modifier.width(168.dp).semantics {
            contentDescription = label
            if (errorText != null) this.error(errorText)
        },
    ) {
        DesktopTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            error = errorText != null,
            height = 32.dp,
        )
    }
}

/**
 * The window's overlay for the question: the card, on the family's spring. The frame supplies the blocker —
 * nothing on the page behind a question may take a stroke.
 */
@Composable
internal fun BoxScope.ExportPdfHost() {
    val open = rememberClosingValue(DesktopExportQueue.scopeRequest)
    GlassPresence(visible = DesktopExportQueue.scopeRequest != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (open != null) {
                ExportPdfDialog(
                    request = open,
                    onEdit = DesktopExportQueue::editScope,
                    onConfirm = { DesktopExportQueue.confirmScope() },
                    onCancel = { DesktopExportQueue.cancelScope() },
                )
            }
        }
    }
}
