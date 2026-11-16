package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.io.contentsRowsOf
import com.mobixournal.render.OutlineEdits
import com.mobixournal.render.OutlineRow
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.render.PdfOutlineView

/**
 * The PDF's **Contents** in the page sidebar: a collapsible section under the page list, filled from the
 * document's outline.
 *
 * The rows are read **only when the section is opened** — a thousand bookmarks cost nothing until someone
 * asks for them, and a document with no outline (or no PDF at all) shows no section rather than an empty
 * one. Each row is indented by its outline depth and prints the page it leads to; an entry that names no
 * page is dimmed and cannot be clicked, exactly as `docs/pdf.md` says for the tablet.
 *
 * A long book gets the same two controls the tablet's card has, from the same rules (`PdfOutlineView`): a
 * chapter's chevron closes its branch, and the filter finds a section by name while keeping the chapters
 * above it for context. Rows are addressed by **position**, so two chapters called "Introduction" fold
 * separately, and the filter never writes the collapse state — clearing the box restores the tree.
 *
 * Nothing here writes the file: the outline is navigation, so a click selects a page and leaves the
 * document byte-identical.
 */
@Composable
internal fun PdfContentsSection(
    tab: DesktopTab,
    onSelectPage: (Int) -> Unit,
    onAdd: (String) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
) {
    // Read only when opened: the walk over the page list is what this section must not pay for by merely
    // existing (`docs/desktop-workstation.md`).
    var open by remember(tab) { mutableStateOf(false) }
    var query by remember(tab) { mutableStateOf("") }
    var collapsed by remember(tab) { mutableStateOf(emptySet<Int>()) }
    val rows = if (open) {
        remember(tab, tab.pdfIndex, tab.document.edits, collapsed, query) {
            contentsRowsOf(tab, collapsed, query)
        }
    } else {
        emptyList()
    }
    // One Contents edit at a time: naming a row for the page in view, retitling one, or asking about a
    // removal. A document with no index still shows the section, because this is also where a bookmark is
    // added and an ordinary sheet deserves an index too.
    val editing = remember(tab) { ContentsEditHost() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .hoverFill(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(14.dp)) {
                drawChromeGlyph(
                    if (open) ChromeGlyph.CHEVRON_DOWN else ChromeGlyph.CHEVRON_UP,
                    DesktopTheme.inkSecondary,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text("Contents", color = DesktopTheme.inkSecondary, style = DesktopTheme.eyebrow)
        }
        if (!open) return@Column
        Spacer(Modifier.height(6.dp))
        if (editing.pane(onAdd, onRename, onDelete)) return@Column
        ContentsFilterField(query = query, onQuery = { query = it })
        Spacer(Modifier.height(4.dp))
        if (rows.isEmpty()) {
            Text(
                text = if (query.isBlank()) "No contents" else "No contents match \"${query.trim()}\"",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
            )
            return@Column
        }
        // The row's own width, not the window's: the sidebar is a column the reader can narrow, and the
        // indentation gives way to the title rather than the other way round.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value
            val widest = largestPageNumber(rows)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(rows, key = { it.row }) { row ->
                    OutlineRowView(
                        row = row,
                        rowWidthDp = widthDp,
                        largestPageNumber = widest,
                        onSelectPage = onSelectPage,
                        onToggle = { collapsed = PdfOutlineView.toggled(collapsed, row.row) },
                        // Only the notebook's own rows can be edited: the PDF's rows are the file's, and a
                        // first bookmark seeds the tree from them so they stay in the list.
                        onRename = if (row.id.startsWith("bkm_")) ({ editing.renaming(row) }) else null,
                        onDelete = if (row.id.startsWith("bkm_")) ({ editing.deleting(row) }) else null,
                    )
                }
            }
        }
    }
}

/**
 * The window's Contents filter: one capsule, a placeholder, and the keyboard's own Escape handled by the
 * dialog-free sidebar — [WindowKeys.focusControl] tells the shell that this field owns typing while it has
 * focus, so a chapter search is not eaten by the page commands' single-letter keys.
 */
@Composable
private fun ContentsFilterField(query: String, onQuery: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = DesktopTheme.label.copy(color = DesktopTheme.ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(DesktopTheme.accent),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                WindowKeys.focusControl(owner, focused)
            }
            .glassCapsule(cornerRadius = 8.dp)
            .border(
                width = 1.dp,
                color = if (focused) DesktopTheme.accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { field ->
            Box {
                if (query.isEmpty()) {
                    Text("Filter contents", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                }
                field()
            }
        },
    )
}

/** The widest page number the list will print — the number its column is reserved from. */
private fun largestPageNumber(rows: List<OutlineRow>): Int = (rows.maxOfOrNull { it.page ?: 0 } ?: 0) + 1

/**
 * One Contents row: its chevron, its indent, its title, and the page it leads to in a column of its own.
 *
 * The indentation, the column and the announcement come from [ContentsRowLayout], so this row and the
 * tablet's say the same thing about the same outline — a chapter heading in the heading face, its sections
 * under it smaller and lighter, a row that leads nowhere dimmed and not clickable. A leaf keeps the chevron's
 * gutter empty so the titles share one left edge, and an ancestor shown only for a filter's context is dimmed
 * so the match reads as the answer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OutlineRowView(
    row: OutlineRow,
    rowWidthDp: Float,
    largestPageNumber: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val pageColumnDp = ContentsRowLayout.pageColumnDp(
        largestPageNumber = largestPageNumber,
        rowWidthDp = rowWidthDp,
        fontScale = LocalDensity.current.fontScale,
    )
    val target = row.page
    val number = target?.plus(1)
    val heading = ContentsRowLayout.isHeading(row.depth)
    Row(
        modifier
            .fillMaxWidth()
            // A row that leads nowhere answers the pointer with nothing: `active` keeps the hover light off
            // it, so the row's own "cannot be opened" is not contradicted by a highlight.
            .hoverFill(RoundedCornerShape(8.dp), active = target == null)
            .clickable(enabled = target != null, role = Role.Button) {
                if (target != null) onSelectPage(target)
            }
            // One announcement for the whole row: the title, the page and whether it opens at all.
            .semantics(mergeDescendants = true) {
                contentDescription = ContentsRowLayout.announcement(row.title, number)
            }
            // The indent is what the **whole** row leaves it: the branch gutter, the action gutter, the page
            // column and the title's minimum are room the hierarchy cannot spend.
            .padding(
                start = ContentsRowLayout.indentInRow(row.depth, rowWidthDp, pageColumnDp).dp,
                top = 2.dp,
                bottom = 2.dp,
                end = 4.dp,
            ),
        // The number sits on the title's first line, as on the tablet: a wrapped title must not leave its
        // page number floating between the two lines.
        verticalAlignment = Alignment.Top,
    ) {
        ContentsChevron(row = row, onToggle = onToggle)
        TooltipArea(
            tooltip = {
                Box(Modifier.glassSurface().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        row.title.ifBlank { "Bookmark" },
                        color = DesktopTheme.ink,
                        style = DesktopTheme.label,
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
            },
            delayMillis = 500,
            tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
        ) {
        Text(
            text = row.title,
            // An entry with no page is shown but not jumpable: dimmed, as the tablet draws it; the row the
            // reader stands on wears the accent instead, so "where am I" is one glance.
            color = when {
                row.current -> DesktopTheme.accent
                row.context || target == null -> DesktopTheme.inkMuted
                else -> DesktopTheme.ink
            },
            style = if (heading) DesktopTheme.tab else DesktopTheme.label,
            maxLines = ContentsRowLayout.TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        }
        // Reserved on every row, empty where there is no page, so the titles end on one line.
        Box(Modifier.width(pageColumnDp.dp), contentAlignment = Alignment.TopEnd) {
            if (number != null) {
                // The number's line height is the title's, so it sits on the title's first baseline.
                Text(
                    text = "$number",
                    color = DesktopTheme.inkMuted,
                    style = DesktopTheme.numeric.copy(lineHeight = if (heading) DesktopTheme.tab.lineHeight else DesktopTheme.label.lineHeight),
                )
            }
        }
        // One action gutter on every row — empty on a row the file owns, so the titles still end on one
        // edge and a source-PDF row offers no command it cannot run.
        Box(Modifier.width(ContentsRowLayout.ACTION_DP.dp), contentAlignment = Alignment.TopEnd) {
            if (onRename != null || onDelete != null) {
                ContentsRowMenu(title = row.title, onRename = onRename, onDelete = onDelete)
            }
        }
    }
}

/**
 * A row's **neutral menu**: one overflow glyph that opens **Rename** and **Delete**, and nothing else.
 *
 * Anchored under its own glyph, it is a question rather than an act: dismissing it or picking Rename leaves the
 * tree untouched, and Delete only *asks* — the section's delete question is where a bookmark actually goes.
 */
@Composable
private fun ContentsRowMenu(
    title: String,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(ContentsRowLayout.ACTION_DP.dp)
                .hoverFill(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "Actions for ${title.ifBlank { "bookmark" }}" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) { drawChromeGlyph(ChromeGlyph.MENU, DesktopTheme.inkSecondary) }
        }
        val drop = with(LocalDensity.current) { ContentsRowLayout.ACTION_DP.dp.roundToPx() }
        GlassPopup(visible = open, onDismissRequest = { open = false }, offset = IntOffset(0, drop)) {
            Column(
                Modifier.glassSurface().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onRename != null) {
                    ContentsMenuRow("Rename", title) {
                        open = false
                        onRename()
                    }
                }
                if (onDelete != null) {
                    ContentsMenuRow("Delete", title) {
                        open = false
                        onDelete()
                    }
                }
            }
        }
    }
}

/** One answer of a row's menu: its word, on a hoverable slot, announced with the row it belongs to. */
@Composable
private fun ContentsMenuRow(label: String, title: String, onClick: () -> Unit) {
    Text(
        label,
        color = DesktopTheme.ink,
        style = DesktopTheme.label,
        modifier = Modifier
            .hoverFill(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label ${title.ifBlank { "bookmark" }}" }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** One word of a row's own actions, named together with its row so two chapters stay distinguishable. */
@Composable
private fun ContentsRowAction(label: String, title: String, onClick: () -> Unit) {
    Text(
        label,
        color = DesktopTheme.inkMuted,
        style = DesktopTheme.label,
        modifier = Modifier
            .hoverFill(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label ${title.ifBlank { "bookmark" }}" }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/**
 * The section while one row is being named, retitled or asked about — true when it drew that instead of the list.
 * Pulled out so the section reads as the list and the filter it arranges, not the three forms beside them.
 */
@Composable
private fun ContentsEditPane(
    edit: ContentsEditState?,
    title: String,
    error: String?,
    onTitle: (String) -> Unit,
    onSettle: (OutlineEdits.Result) -> Unit,
    onStartAdd: () -> Unit,
    onAdd: (String) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
    onCancel: () -> Unit,
): Boolean = when (edit) {
    null -> {
        Text(
            "Add bookmark",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            modifier = Modifier
                .hoverFill(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onStartAdd)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        false
    }
    is ContentsEditState.Deleting -> {
        ContentsDeleteQuestion(
            row = edit.row,
            error = error,
            onDelete = { onSettle(onDelete(edit.row.id)) },
            onCancel = onCancel,
        )
        true
    }
    is ContentsEditState.Naming -> {
        ContentsTitleField(
            value = title,
            onValue = onTitle,
            error = error,
            onSave = { onSettle(if (edit.row == null) onAdd(title) else onRename(edit.row.id, title)) },
            onCancel = onCancel,
        )
        true
    }
}

/** The section's one edit at a time, so the section's body stays the list and the filter it arranges. */
internal class ContentsEditHost {

    private var edit by mutableStateOf<ContentsEditState?>(null)
    private var title by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)

    /** Retitling [row]: the field opens on the title it already has. */
    fun renaming(row: OutlineRow) {
        title = row.title
        error = null
        edit = ContentsEditState.Naming(row)
    }

    /** Asking about [row] before it goes. */
    fun deleting(row: OutlineRow) {
        error = null
        edit = ContentsEditState.Deleting(row)
    }

    /** The pane's own composable — true when it drew an edit instead of the list. */
    @Composable
    fun pane(
        onAdd: (String) -> OutlineEdits.Result,
        onRename: (String, String) -> OutlineEdits.Result,
        onDelete: (String) -> OutlineEdits.Result,
    ): Boolean = ContentsEditPane(
        edit = edit,
        title = title,
        error = error,
        onTitle = { title = it },
        onSettle = { result ->
            if (result is OutlineEdits.Result.Refused) error = contentsRefusal(result.reason) else edit = null
        },
        onStartAdd = {
            title = ""
            error = null
            edit = ContentsEditState.Naming(null)
        },
        onAdd = onAdd,
        onRename = onRename,
        onDelete = onDelete,
        onCancel = { edit = null; error = null },
    )
}

/** What the section is doing to the tree right now. */
internal sealed interface ContentsEditState {

    /** Naming a new row (null) or retitling [row]. */
    data class Naming(val row: OutlineRow?) : ContentsEditState

    /** Asking whether to remove [row]. */
    data class Deleting(val row: OutlineRow) : ContentsEditState
}

/** A refusal in the reader's words — never a class or an enum name on screen. */
private fun contentsRefusal(reason: OutlineEdits.Refusal): String = when (reason) {
    OutlineEdits.Refusal.EMPTY_TITLE -> "A bookmark needs a title."
    OutlineEdits.Refusal.NOT_FOUND -> "That bookmark is gone."
    OutlineEdits.Refusal.NO_TARGET -> "This page cannot hold a bookmark."
}

/** The title a row is named in, with Save, Cancel and the refusal under it instead of a silent close. */
@Composable
private fun ContentsTitleField(
    value: String,
    onValue: (String) -> Unit,
    error: String?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = DesktopTheme.label.copy(color = DesktopTheme.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(DesktopTheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .glassCapsule(cornerRadius = 8.dp)
                .border(1.dp, if (error != null) DesktopTheme.danger else DesktopTheme.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text("Title", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                    field()
                }
            },
        )
        if (error != null) Text(error, color = DesktopTheme.danger, style = DesktopTheme.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ContentsRowAction("Cancel", "", onCancel)
            if (value.isNotBlank()) ContentsRowAction("Save", "", onSave)
        }
    }
}

/** The delete question: says the page stays in the notebook before the row goes. */
@Composable
private fun ContentsDeleteQuestion(
    row: OutlineRow,
    error: String?,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            "Delete \"${row.title.ifBlank { "this bookmark" }}\"? The sections under it move up, and the page stays in the notebook.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
        )
        if (error != null) Text(error, color = DesktopTheme.danger, style = DesktopTheme.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ContentsRowAction("Cancel", row.title, onCancel)
            ContentsRowAction("Delete", row.title, onDelete)
        }
    }
}

/** The branch control, or the gutter it would take — an empty one, so titles share a left edge. */
@Composable
private fun ContentsChevron(row: OutlineRow, onToggle: (() -> Unit)?) {
    if (!row.branch || onToggle == null) {
        Spacer(Modifier.width(ContentsRowLayout.BRANCH_DP.dp))
        return
    }
    Box(
        Modifier
            .size(ContentsRowLayout.BRANCH_DP.dp)
            .hoverFill(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = if (row.expanded) "Collapse ${row.title}" else "Expand ${row.title}"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawChromeGlyph(
                if (row.expanded) ChromeGlyph.CHEVRON_DOWN else ChromeGlyph.CHEVRON_UP,
                DesktopTheme.inkSecondary,
            )
        }
    }
}

