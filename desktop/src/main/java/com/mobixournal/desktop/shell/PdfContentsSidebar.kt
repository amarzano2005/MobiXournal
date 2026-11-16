package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.GlassPopup
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.io.contentsResolved
import com.mobixournal.desktop.io.visibleContentsRows
import com.mobixournal.render.OutlineEdits
import com.mobixournal.render.OutlineRow
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

/** Which view a tab's navigation sidebar shows: the page thumbnails, or the document's Contents. */
enum class DesktopSidebarView {
    PAGES,
    CONTENTS,
}

/**
 * The sidebar's **Pages / Contents** switch: one persistent control above either list, so the two navigation
 * views are a choice rather than a section folded under three others. The choice belongs to the tab
 * ([DesktopTab.contentsView]), so switching tabs and coming back finds the view where it was left.
 */
@Composable
internal fun SidebarViewSwitch(view: DesktopSidebarView, onView: (DesktopSidebarView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DesktopSidebarView.entries.forEach { option ->
            val picked = option == view
            Text(
                text = if (option == DesktopSidebarView.PAGES) "Pages" else "Contents",
                color = if (picked) DesktopTheme.accent else DesktopTheme.inkSecondary,
                style = DesktopTheme.eyebrow,
                modifier = Modifier
                    .weight(1f)
                    .hoverFill(RoundedCornerShape(8.dp), active = picked)
                    .then(
                        if (picked) Modifier.background(DesktopTheme.activeSlot, RoundedCornerShape(8.dp))
                        else Modifier,
                    )
                    .clickable(role = Role.Tab) { onView(option) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The document's **Contents** as the sidebar's own view: its header and **Add bookmark**, a compact filter, and
 * the outline using the height the sidebar has left — no 260 dp cap, and no second panel over the page the
 * reader is annotating.
 *
 * A document with no bookmarks still shows the view, because this is also where a bookmark is made: the empty
 * state says so and the header's command stays reachable. Each row prints the page it leads to; an entry that
 * names no page is dimmed and cannot be clicked, exactly as `docs/pdf.md` says for the tablet.
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
internal fun ContentsPanel(
    tab: DesktopTab,
    onSelectPage: (Int) -> Unit,
    contentsEdits: ContentsEdits,
    modifier: Modifier = Modifier,
) {
    // The filter, the folds and the place in the list belong to the **tab**, not to the rows: an edit replaces
    // the rows on every keystroke of a rename, and none of those may clear a filter or move the reader
    // (docs/pdf.md).
    val navigation = tab.contentsNavigation
    val query = navigation.query
    // Resolving an outline costs a walk over every page's background; marking "you are here" costs nothing.
    // So the rows are resolved once per document edit and the view is decorated per page — scrolling a long
    // index must not re-resolve it, and turning a page must not load a notebook page to move one accent.
    val resolved = remember(tab, tab.pdfIndex, tab.document.edits) { contentsResolved(tab) }
    LaunchedEffect(resolved) { tab.contentsNavigation = tab.contentsNavigation.against(resolved) }
    val rows = remember(resolved, navigation.collapsed, query, tab.page) {
        visibleContentsRows(resolved, navigation.collapsed, query, tab.page)
    }
    // One Contents edit at a time: naming a row for the page in view, retitling one, or asking about a removal.
    val editing = remember(tab) { ContentsEditHost() }
    // A new bookmark is a draft row in the list; retitling is in the row it names. Either way the filter and
    // the neighbouring rows stay where they were.
    val naming = editing.draft as? ContentsEditState.Naming
    val adding = naming != null && naming.row == null
    Column(modifier.padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Contents",
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.eyebrow,
                modifier = Modifier.weight(1f),
            )
            ContentsHeaderAction("Add bookmark", enabled = !adding) { editing.adding(tab.page) }
        }
        val deleting = editing.deleting
        if (deleting != null) {
            ContentsDeleteQuestion(
                row = deleting.row,
                error = editing.error,
                onDelete = { editing.submit(contentsEdits.remove(deleting.row.id)) },
                onCancel = editing::cancel,
            )
        }
        ContentsFilterField(query = query, onQuery = { tab.contentsNavigation = tab.contentsNavigation.withQuery(it) })
        Spacer(Modifier.height(4.dp))
        LaunchedEffect(rows) {
            val at = rows.indexOfFirst { it.id == tab.contentsNavigation.anchorId }
            if (rows.isNotEmpty() && at >= 0 && at != tab.contentsList.firstVisibleItemIndex) {
                tab.contentsList.scrollToItem(at)
            }
        }
        LaunchedEffect(tab.contentsList.firstVisibleItemIndex, rows) {
            val row = rows.getOrNull(tab.contentsList.firstVisibleItemIndex) ?: return@LaunchedEffect
            tab.contentsNavigation = tab.contentsNavigation.scrolledTo(row.id, row.row)
        }
        if (rows.isEmpty() && !adding) {
            Text(
                text = if (query.isBlank()) "No bookmarks yet" else "No contents match \"${query.trim()}\"",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
            )
            return@Column
        }
        // The row's own width, not the window's: the sidebar is a column the reader can narrow, and the
        // indentation gives way to the title rather than the other way round. The list takes every dp the
        // sidebar has left, so a thousand bookmarks are read at the height of the window, not a 260 dp box.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val widthDp = maxWidth.value
            val widest = largestPageNumber(rows)
            val pageColumnDp = ContentsRowLayout.pageColumnDp(
                largestPageNumber = widest,
                rowWidthDp = widthDp,
                fontScale = LocalDensity.current.fontScale,
            )
            LazyColumn(state = tab.contentsList, modifier = Modifier.fillMaxSize()) {
                if (adding) {
                    item(key = "add-draft") {
                        ContentsAddDraft(
                            page = editing.addPage,
                            pageColumnDp = pageColumnDp,
                            title = editing.title,
                            error = editing.error,
                            onTitle = editing::typed,
                            onSave = { editing.submit(contentsEdits.add(editing.title, editing.addPage)) },
                            onCancel = editing::cancel,
                        )
                    }
                }
                items(rows, key = { it.row }) { row ->
                    val renaming = (editing.draft as? ContentsEditState.Naming)?.takeIf { it.row?.id == row.id }
                    OutlineRowView(
                        row = row,
                        rowWidthDp = widthDp,
                        largestPageNumber = widest,
                        onSelectPage = onSelectPage,
                        onToggle = { tab.contentsNavigation = tab.contentsNavigation.toggled(row.id) },
                        // Only the notebook's own rows can be edited: the PDF's rows are the file's, and a
                        // first bookmark seeds the tree from them so they stay in the list.
                        onRename = if (row.id.startsWith("bkm_")) ({ editing.renaming(row) }) else null,
                        onDelete = if (row.id.startsWith("bkm_")) ({ editing.deleting(row) }) else null,
                        renaming = renaming != null,
                        title = editing.title,
                        error = editing.error,
                        onTitle = editing::typed,
                        onSaveDraft = { editing.submit(contentsEdits.rename(row.id, editing.title)) },
                        onCancelDraft = editing::cancel,
                    )
                }
            }
        }
    }
}

/** The Contents header's own command: a word on a full-height slot, so the action is reachable by thumb and pen. */
@Composable
private fun ContentsHeaderAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) DesktopTheme.inkSecondary else DesktopTheme.inkMuted,
        style = DesktopTheme.label,
        modifier = Modifier
            .hoverFill(RoundedCornerShape(8.dp), active = !enabled)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
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
    renaming: Boolean = false,
    title: String = "",
    error: String? = null,
    onTitle: (String) -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onCancelDraft: () -> Unit = {},
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
        if (renaming) {
            // The name is typed where the old one was read: the row itself, between the same neighbours.
            ContentsTitleField(
                value = title,
                onValue = onTitle,
                error = error,
                placeholder = "Title",
                onSave = onSaveDraft,
                onCancel = onCancelDraft,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            )
        } else {
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

/** What the panel is doing to the tree right now: naming a row (null for a new one) or asking about a removal. */
internal sealed interface ContentsEditState {

    /** Naming a new row ([row] null) for [page], or retitling [row]. */
    data class Naming(val row: OutlineRow?, val page: Int?) : ContentsEditState

    /** Asking whether to remove [row]. */
    data class Deleting(val row: OutlineRow) : ContentsEditState
}

/**
 * The panel's one edit at a time, so the list stays the list it arranges: the draft, the name being typed, and
 * the page a new bookmark captured when **Add bookmark** was pressed (a page turned while typing cannot move it).
 */
internal class ContentsEditHost {

    private var edit by mutableStateOf<ContentsEditState?>(null)
    private var typedTitle by mutableStateOf("")
    private var refusal by mutableStateOf<String?>(null)

    /** The edit in progress, or null when the list is only being read. */
    val draft: ContentsEditState? get() = edit

    /** The name being typed. */
    val title: String get() = typedTitle

    /** The refusal beside the draft, once a command has answered. */
    val error: String? get() = refusal

    /** The page the add draft was opened on. */
    val addPage: Int? get() = (edit as? ContentsEditState.Naming)?.page

    /** The row being asked about, or null when no delete question is up. */
    val deleting: ContentsEditState.Deleting? get() = edit as? ContentsEditState.Deleting

    /** Naming a new row for [page]. */
    fun adding(page: Int?) {
        typedTitle = ""
        refusal = null
        edit = ContentsEditState.Naming(row = null, page = page)
    }

    /** Retitling [row]: the field opens on the title it already has. */
    fun renaming(row: OutlineRow) {
        typedTitle = row.title
        refusal = null
        edit = ContentsEditState.Naming(row = row, page = row.page)
    }

    /** Asking about [row] before it goes. */
    fun deleting(row: OutlineRow) {
        typedTitle = row.title
        refusal = null
        edit = ContentsEditState.Deleting(row)
    }

    /** The name as it is now typed. */
    fun typed(value: String) {
        typedTitle = value
    }

    /** Closing whatever the panel is doing, leaving the tree exactly as it was. */
    fun cancel() {
        edit = null
        refusal = null
    }

    /** A command's answer: a refusal stays beside the draft in the reader's words, a write closes it. */
    fun submit(result: OutlineEdits.Result) {
        if (result is OutlineEdits.Result.Refused) refusal = contentsRefusal(result.reason) else cancel()
    }
}

/** A refusal in the reader's words — never a class or an enum name on screen. */
private fun contentsRefusal(reason: OutlineEdits.Refusal): String = when (reason) {
    OutlineEdits.Refusal.EMPTY_TITLE -> "A bookmark needs a title."
    OutlineEdits.Refusal.NOT_FOUND -> "That bookmark is gone."
    OutlineEdits.Refusal.NO_TARGET -> "This page cannot hold a bookmark."
}

/**
 * The one-line field a bookmark is named in — inside the row being retitled, or in the draft the header's
 * command opens. **Enter** saves, **Escape** abandons, a blank name cannot be saved, and the refusal is printed
 * under the field. While it has focus [WindowKeys.focusControl] tells the shell the keyboard is typing, so a
 * name may contain a tool's letter without the window reading it as a shortcut.
 */
@Composable
private fun ContentsTitleField(
    value: String,
    onValue: (String) -> Unit,
    error: String?,
    placeholder: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = DesktopTheme.label.copy(color = DesktopTheme.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(DesktopTheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { WindowKeys.focusControl(owner, it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (value.isNotBlank()) onSave()
                            true
                        }
                        Key.Escape -> {
                            onCancel()
                            true
                        }
                        else -> false
                    }
                }
                .glassCapsule(cornerRadius = 8.dp)
                .border(1.dp, if (error != null) DesktopTheme.danger else DesktopTheme.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
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

/**
 * The draft **Add bookmark** opens: a compact row above its neighbours, holding the name being typed and the
 * page it captured when the command was pressed.
 */
@Composable
private fun ContentsAddDraft(
    page: Int?,
    pageColumnDp: Float,
    title: String,
    error: String?,
    onTitle: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(ContentsRowLayout.BRANCH_DP.dp))
        ContentsTitleField(
            value = title,
            onValue = onTitle,
            error = error,
            placeholder = "New bookmark",
            onSave = onSave,
            onCancel = onCancel,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(pageColumnDp.dp), contentAlignment = Alignment.TopEnd) {
            if (page != null) {
                Text(text = "${page + 1}", color = DesktopTheme.inkMuted, style = DesktopTheme.numeric)
            }
        }
    }
}

/** The delete question: asked above the list (which keeps its rows), and says the page stays in the notebook. */
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

