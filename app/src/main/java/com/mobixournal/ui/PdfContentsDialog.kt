package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.render.OutlineEdits
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.mobixournal.render.OutlineSiblingOrder
import com.mobixournal.render.OutlineRow
import com.mobixournal.render.PdfOutlineView

/** The bookmark navigator's inline body; rows and edits stay in the document's lane. */
@Composable
internal fun PdfContentsPanel(
    rows: List<OutlineRow>,
    currentPage: Int?,
    session: ContentsSession,
    onGoToPage: (Int) -> Unit,
    onAdd: (String, Int?) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
    onMove: (String, Int) -> OutlineEdits.Result,
    modifier: Modifier = Modifier,
) {
    // The session belongs to the **document**: an edit that replaces the rows must not clear the filter, open
    // every branch or lose the reader's place (docs/pdf.md).
    val navigation = remember(rows, session.navigation) {
        session.navigation.against(rows, closeNewBranches = true)
    }
    val query = navigation.query
    val editing = session.editing
    LaunchedEffect(navigation) { if (session.navigation != navigation) session.navigation = navigation }
    val shown = remember(rows, navigation.collapsed, query, currentPage) {
        PdfOutlineView.visible(rows, navigation.collapsed, query, currentPage)
    }
    val adding = editing.draft is ContentsEdit.Adding
    ContentsScrollAnchor(session, shown, adding, (editing.draft as? ContentsEdit.Renaming)?.row?.id)
    Column(modifier.fillMaxWidth()) {
        // A delete question sits above the list, not in place of it: the rows the reader was reading
        // stay behind the answer, so cancelling costs them nothing.
        val deleting = editing.deleting
        if (deleting != null) {
            DeleteQuestion(
                title = deleting.row.title,
                error = editing.error,
                onDelete = { editing.submit(onDelete(deleting.row.id)) },
                onCancel = editing::cancel,
            )
        }
        // Renaming is in the row it names and a new bookmark is a draft above its neighbours: the
        // filter and the surrounding rows stay where they were while a name is typed.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContentsFilter(
                query = query,
                onQuery = { session.navigation = session.navigation.withQuery(it) },
                onClear = { session.navigation = session.navigation.withQuery("") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { editing.adding(currentPage) }, enabled = !adding,
                modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp)) {
                Icon(ChromeGlyph.ADD.imageVector(), "Add bookmark", modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty() && !adding) {
            Text(
                text = if (query.isBlank()) "No bookmarks yet" else "No contents match \"${query.trim()}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        // The row's own width, not the screen's: in a split-screen pane the card is what the list has.
        ContentsRowsBlock(
            rows = rows,
            shown = shown,
            adding = adding,
            session = session,
            onGoToPage = onGoToPage,
            onAdd = onAdd,
            onRename = onRename,
            onDelete = onDelete,
            onMove = onMove,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Collect offsets off the composition path, restoring only after a filter/fold/edit changes the list. */
@Composable
private fun ContentsScrollAnchor(session: ContentsSession, shown: List<OutlineRow>, adding: Boolean, renaming: String?) {
    // Restore only when the visible tree changes. Scroll itself never projects the tree or drives composition.
    LaunchedEffect(shown, adding, renaming) {
        val at = shown.indexOfFirst { it.id == session.scrollAnchorId }
        val fallback = shown.indexOfFirst { it.row >= session.scrollAnchorRow }.coerceAtLeast(0)
        val reveal = shown.indexOfFirst { it.id == session.revealId }
        if (adding) session.list.scrollToItem(0)
        else if (renaming != null) {
            androidx.compose.runtime.withFrameNanos { }
            val draft = shown.indexOfFirst { it.id == renaming }
            if (draft >= 0) session.list.scrollToItem(draft)
        } else if (reveal >= 0) {
            session.list.scrollToItem(reveal)
            session.revealId = null
        } else if (shown.isNotEmpty()) {
            session.list.scrollToItem((if (at >= 0) at else fallback) + if (adding) 1 else 0,
                if (at >= 0) session.scrollAnchorOffset else 0)
        }
        snapshotFlow { session.list.firstVisibleItemIndex to session.list.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                shown.getOrNull(index - if (adding) 1 else 0)?.let { row ->
                    session.scrollAnchorId = row.id
                    session.scrollAnchorRow = row.row
                    session.scrollAnchorOffset = offset
                }
            }
    }
}

/**
 * The list the card draws: an add draft when one is open, then the visible rows — the width being the card's
 * own, so a split-screen pane measures its own indent and its own page column.
 */
@Composable
private fun ContentsRowsBlock(
    rows: List<OutlineRow>,
    shown: List<OutlineRow>,
    adding: Boolean,
    session: ContentsSession,
    onGoToPage: (Int) -> Unit,
    onAdd: (String, Int?) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
    onMove: (String, Int) -> OutlineEdits.Result,
    modifier: Modifier,
) {
    val editing = session.editing
    val moves = remember(rows) { OutlineSiblingOrder.of(rows) }
    val widest = remember(rows) { largestPageNumber(rows) }
    BoxWithConstraints(modifier) {
        val widthDp = maxWidth.value
        val pageColumnDp = ContentsRowLayout.pageColumnDp(
            largestPageNumber = widest,
            rowWidthDp = widthDp,
            fontScale = LocalDensity.current.fontScale,
        )
        BookmarkDragList(rows, shown, session.list, editing.draft == null,
            { id, delta ->
                val result = onMove(id, delta)
                if (result is OutlineEdits.Result.Ok) session.revealId = id
                editing.submit(result)
            }) { listModifier, dragging ->
            LazyColumn(state = session.list, modifier = listModifier, userScrollEnabled = !dragging) {
                if (adding) {
                    item(key = "add-draft") {
                        AddDraftRow(
                            page = editing.addPage,
                            pageColumnDp = pageColumnDp,
                            title = editing.title,
                            error = editing.error,
                            onTitle = editing::typed,
                            onSave = {
                                val result = onAdd(editing.title, editing.addPage)
                                if (result is OutlineEdits.Result.Ok) {
                                    session.revealId = result.tree.nodes.last().id
                                    session.navigation = session.navigation.withQuery("")
                                }
                                editing.submit(result)
                            },
                            onCancel = editing::cancel,
                        )
                    }
                }
                items(shown, key = { it.id }, contentType = { "bookmark" }) { row ->
                    val renaming = (editing.draft as? ContentsEdit.Renaming)?.takeIf { it.row.id == row.id }
                    ContentsRow(
                        row = row,
                        rowWidthDp = widthDp,
                        largestPageNumber = widest,
                        onToggle = { session.navigation = session.navigation.toggled(row.id) },
                        onClick = {
                            row.page?.let(onGoToPage)
                        },
                        // Only the notebook's own rows can be edited: a row read out of the PDF is the file's
                        // bookmark, and it becomes the notebook's the moment one is added.
                        onRename = if (row.id.startsWith("bkm_")) ({ editing.renaming(row) }) else null,
                        onDelete = if (row.id.startsWith("bkm_")) ({ editing.deleting(row) }) else null,
                        dragActions = buildList {
                            if (row.id.startsWith("bkm_")) {
                                if (moves[row.id]?.up == true) add(CustomAccessibilityAction("Move up") { onMove(row.id, -1) is OutlineEdits.Result.Ok })
                                if (moves[row.id]?.down == true) add(CustomAccessibilityAction("Move down") { onMove(row.id, 1) is OutlineEdits.Result.Ok })
                            }
                        },
                        renaming = renaming != null,
                        title = editing.title,
                        error = editing.error,
                        onTitle = editing::typed,
                        onSaveDraft = { editing.submit(onRename(row.id, editing.title)) },
                        onCancelDraft = editing::cancel,
                    )
                }
            }
        }
    }
}

/** The widest page number the card will print — the number its column is reserved from. */
private fun largestPageNumber(rows: List<OutlineRow>): Int =
    rows.maxOfOrNull { if (it.hasDestination) it.page!! + 1 else 0 } ?: 0

/**
 * One Contents row: its chevron, its title indented by depth, and the page it leads to in a column of its own.
 *
 * The indentation, the column and the announcement come from [ContentsRowLayout], so this row and the
 * window's say the same thing about the same outline; what is the tablet's own is the type — a chapter
 * heading in the heading face and its sections under it in the body face. The chevron is a target of its own
 * (a chapter's row still jumps to the chapter), and an ancestor kept only for the filter's context is drawn
 * dimmer than the hit, so a search shows its one answer instead of shouting the path to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentsRow(
    row: OutlineRow,
    rowWidthDp: Float,
    largestPageNumber: Int,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    dragActions: List<CustomAccessibilityAction> = emptyList(),
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
    val heading = ContentsRowLayout.isHeading(row.depth)
    val number = row.page?.plus(1)
    val ink = when {
        row.context -> MaterialTheme.colorScheme.onSurfaceVariant
        row.hasDestination -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleStyle = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ContentsRowLayout.MOBILE_ROW_DP.dp)
            .background(if (row.current) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            // A disabled clickable ancestor also blocks the inline field's focus.
            .then(if (renaming) Modifier else Modifier.clickable(enabled = row.hasDestination, role = Role.Button, onClick = onClick))
            // One announcement for the whole row: a title, a page and whether it opens at all, instead of a
            // title with a lone number beside it and a click that may do nothing.
            .semantics(mergeDescendants = true) {
                contentDescription = ContentsRowLayout.announcement(row.title, number)
            }
            // The indent is what the **whole** row leaves it: the branch gutter, the action gutter, the page
            // column and the title's minimum are room the hierarchy cannot spend.
            .padding(
                start = ContentsRowLayout.mobileIndent(row.depth, rowWidthDp, pageColumnDp).dp,
                top = 2.dp,
                bottom = 2.dp,
            ),
        // Controls, labels and page numbers share the row centre, including two-line chapter titles.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContentsChevron(branched = row.branch, expanded = row.expanded, title = row.title, onToggle = onToggle)
        if (renaming) {
            // Retitling happens **in the row it names**: the name is typed where the old one was read, between
            // the same paragraphs, not in a form that replaces the list.
            ContentsTitleField(
                value = title,
                error = error,
                placeholder = "Title",
                onValue = onTitle,
                onSave = onSaveDraft,
                onCancel = onCancelDraft,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            )
        } else {
            ContentsRowTitle(
                row = row,
                titleStyle = titleStyle,
                heading = heading,
                ink = ink,
                onClick = onClick,
            )
            ContentsRowTrailing(
                row = row,
                titleStyle = titleStyle,
                pageColumnDp = pageColumnDp,
                number = number,
                onRename = onRename,
                onDelete = onDelete,
            )
            BookmarkDragHandle(row, dragActions)
        }
    }
}

/**
 * A row's title, and the way to read all of it: two lines then an ellipsis, with a long press opening the whole
 * name. The row's own tap stays the page jump, so inspecting a title never navigates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.ContentsRowTitle(
    row: OutlineRow,
    titleStyle: androidx.compose.ui.text.TextStyle,
    heading: Boolean,
    ink: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    var fullTitle by remember(row.id) { mutableStateOf(false) }
    Box(Modifier.weight(1f)) {
        Text(
            text = row.title,
            style = titleStyle,
            // Inter ships 400, 500 and 700 (`TypeContractGuardTest`): the heading step is the larger face plus
            // the one weight the theme owns, not a 600 the family answers with 700.
            fontWeight = if (heading) FontWeight.Medium else FontWeight.Normal,
            color = if (row.current) MaterialTheme.colorScheme.primary else ink,
            maxLines = ContentsRowLayout.TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { if (row.hasDestination) onClick() }, enabled = true, onLongClick = { fullTitle = true }),
        )
        ToolbarMenu(expanded = fullTitle, onDismissRequest = { fullTitle = false }, fill = com.mobixournal.ui.theme.rememberPopupPanelColor()) {
            Text(
                row.title.ifBlank { "Bookmark" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * The page column and the one action gutter: the column is kept on every row (empty where a row leads nowhere)
 * so the titles end on one line and a four-digit page never narrows its neighbours, and the gutter stays empty on
 * a row the file owns because a source-PDF bookmark offers no command this app may run.
 */
@Composable
private fun ContentsRowTrailing(
    row: OutlineRow,
    titleStyle: androidx.compose.ui.text.TextStyle,
    pageColumnDp: Float,
    number: Int?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.width(pageColumnDp.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (number != null) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum",
                    lineHeight = titleStyle.lineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
    Box(Modifier.width(ContentsRowLayout.ACTION_DP.dp), contentAlignment = Alignment.TopEnd) {
        if (onRename != null || onDelete != null) {
            ContentsRowMenu(title = row.title, onRename = onRename, onDelete = onDelete)
        }
    }
}

/**
 * A row's **neutral menu**: one overflow glyph that opens **Rename** and **Delete**, and nothing else.
 *
 * The menu is the row's own, anchored under its glyph, and every answer is a separate act: opening it,
 * dismissing it or choosing Rename writes nothing and leaves the row focus where it was. Delete only
 * *asks* — the question above the list is where the removal is confirmed — so a mis-tap cannot lose a
 * bookmark.
 */
@Composable
private fun ContentsRowMenu(
    title: String,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Actions for ${title.ifBlank { "bookmark" }}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        ToolbarMenu(expanded = open, onDismissRequest = { open = false }, fill = com.mobixournal.ui.theme.rememberPopupPanelColor()) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        open = false
                        onRename()
                    },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        open = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * The draft **Add bookmark** opens: a compact row above its neighbours, holding the name being typed and the
 * page it captured when the command was pressed. A page turned while typing cannot move the bookmark, because
 * the destination was fixed before the first letter.
 */
@Composable
private fun AddDraftRow(
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
            error = error,
            placeholder = "New bookmark",
            onValue = onTitle,
            onSave = onSave,
            onCancel = onCancel,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(pageColumnDp.dp), contentAlignment = Alignment.TopEnd) {
            if (page != null) {
                Text(
                    text = "${page + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * The delete question: asked **above** the list, which keeps its rows, its filter and its scroll behind the
 * answer. It says what actually happens — the sections under the row move up and the page stays in the
 * notebook — because the one thing a reader must not fear from a bookmark is losing the page with it.
 */
@Composable
private fun DeleteQuestion(
    title: String,
    error: String?,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            "Delete \"${title.ifBlank { "this bookmark" }}\"? The sections under it move up, and the page stays in the notebook.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/**
 * The branch control, or the room it would take.
 *
 * A leaf keeps the gutter empty rather than pulling its title left: a tree whose titles start at two
 * different edges is a tree nobody can follow. The chevron toggles **only** its branch — the row itself
 * still jumps — and it is announced with the branch it belongs to, since a card of identical arrows says
 * nothing to a screen reader.
 */
@Composable
private fun ContentsChevron(branched: Boolean, expanded: Boolean, title: String, onToggle: () -> Unit) {
    if (!branched) {
        Spacer(Modifier.width(ContentsRowLayout.BRANCH_DP.dp))
        return
    }
    IconButton(
        onClick = onToggle,
        modifier = Modifier
            .size(ContentsRowLayout.BRANCH_DP.dp)
            .semantics {
                contentDescription = if (expanded) "Collapse $title" else "Expand $title"
            },
    ) {
        Icon(
            imageVector = (if (expanded) ChromeGlyph.CHEVRON_DOWN else ChromeGlyph.CHEVRON_RIGHT).imageVector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
