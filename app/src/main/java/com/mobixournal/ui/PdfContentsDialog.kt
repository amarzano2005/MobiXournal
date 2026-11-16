package com.mobixournal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.mobixournal.render.OutlineResolution
import com.mobixournal.render.OutlineRow
import com.mobixournal.render.PdfOutlineSeeding
import com.mobixournal.render.PdfOutlineView

/**
 * The **Contents** door, as the page list's own: one glyph at the lane's foot, the notebook's index behind it.
 *
 * It stands with the page commands because that is what it is — a way of jumping between pages — and it is
 * no longer a row of the Pages menu, which is about the page in front of you. A document whose index is empty
 * draws no button at all rather than a dead one, so the lane's foot says only what the document can do.
 */
@Composable
internal fun PdfContentsButton(
    rows: List<OutlineRow>,
    currentPage: Int?,
    session: ContentsSession,
    onGoToPage: (Int) -> Unit,
    onAdd: (String, Int?) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
) {
    // The door is always there, even with no index yet: the card is also where a bookmark is added, and a
    // document with an ordinary page still has a Contents worth opening.
    var open by remember { mutableStateOf(false) }
    // A full contract slot, not a 34 dp dot a thumb has to find (docs/interface-contract.md).
    IconButton(onClick = { open = true }, modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp)) {
        Icon(
            Icons.AutoMirrored.Outlined.Toc,
            contentDescription = "Contents",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
    DialogPresence(open) {
        // The tree survives the flag: closing clears `open`, and the dialog is still on its way out.
        val tree = rememberClosingArgument(rows)
        if (tree != null) {
            PdfContentsDialog(
                rows = tree,
                currentPage = currentPage,
                session = session,
                onGoToPage = onGoToPage,
                onAdd = onAdd,
                onRename = onRename,
                onDelete = onDelete,
                onDismiss = { open = false },
            )
        }
    }
}

/**
 * The document's **Contents** — its own index, or the source PDF's outline while it has none — as a
 * filterable, expandable list, opened from the page list's foot ([PdfContentsButton]) of a document that has
 * one.
 *
 * The rows arrive **resolved** ([OutlineResolution]): each one already knows the document page it opens, so a
 * tap is a page jump rather than a search for a sheet number, and a row whose page is gone is shown but not
 * tappable. That also made the notebook's own bookmarks reachable here without changing this card: a `page`
 * target and a `sheet` target are the same row to a reader.
 *
 * **A long book needs two controls, and they must not fight.** A chapter's chevron closes it, so a thousand
 * bookmarks become a list a thumb can travel; the filter finds a section by name, showing the chapters above
 * it so the reader still knows where the hit sits in the book. The two rules live in `PdfOutlineView`, shared
 * with the window: rows are addressed by **position**, because two chapters may honestly share a title, and
 * filtering never touches the collapse state — clearing the box gives back the tree as it was left.
 */
@Composable
internal fun PdfContentsDialog(
    rows: List<OutlineRow>,
    currentPage: Int?,
    session: ContentsSession,
    onGoToPage: (Int) -> Unit,
    onAdd: (String, Int?) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
    onDismiss: () -> Unit,
) {
    // The session belongs to the **document**: an edit that replaces the rows must not clear the filter, open
    // every branch or lose the reader's place (docs/pdf.md).
    val navigation = session.navigation
    val query = navigation.query
    val editing = session.editing
    // The folds and the anchor follow the rows as they change: a removed branch is forgotten, a vanished anchor
    // falls back to the surviving row nearest where it stood.
    LaunchedEffect(rows) { session.navigation = session.navigation.against(rows) }
    val shown = PdfOutlineView.visible(rows, navigation.collapsed, query, currentPage)
    // Back to where the list was left, and remember where it is now — an edit, a filter or a rename moves the
    // rows but never the reader.
    LaunchedEffect(shown) {
        val at = shown.indexOfFirst { it.id == session.navigation.anchorId }
        if (shown.isNotEmpty() && at >= 0 && at != session.list.firstVisibleItemIndex) {
            session.list.scrollToItem(at)
        }
    }
    LaunchedEffect(session.list.firstVisibleItemIndex, shown) {
        val row = shown.getOrNull(session.list.firstVisibleItemIndex) ?: return@LaunchedEffect
        session.navigation = session.navigation.scrolledTo(row.id, rows.indexOfFirst { it.id == row.id })
    }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contents") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(Modifier.fillMaxWidth()) {
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
                val adding = editing.draft is ContentsEdit.Adding
                ContentsAddRow(enabled = !adding) { editing.adding(currentPage) }
                ContentsFilter(
                    query = query,
                    onQuery = { session.navigation = session.navigation.withQuery(it) },
                    onClear = { session.navigation = session.navigation.withQuery("") },
                )
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
                    onDismiss = onDismiss,
                    onAdd = onAdd,
                    onRename = onRename,
                    onDelete = onDelete,
                )
            }
        },
    )
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
    onDismiss: () -> Unit,
    onAdd: (String, Int?) -> OutlineEdits.Result,
    onRename: (String, String) -> OutlineEdits.Result,
    onDelete: (String) -> OutlineEdits.Result,
) {
    val editing = session.editing
    BoxWithConstraints {
        val widthDp = maxWidth.value
        val widest = largestPageNumber(rows)
        val pageColumnDp = ContentsRowLayout.pageColumnDp(
            largestPageNumber = widest,
            rowWidthDp = widthDp,
            fontScale = LocalDensity.current.fontScale,
        )
        LazyColumn(state = session.list, modifier = Modifier.fillMaxWidth()) {
            if (adding) {
                item(key = "add-draft") {
                    AddDraftRow(
                        page = editing.addPage,
                        pageColumnDp = pageColumnDp,
                        title = editing.title,
                        error = editing.error,
                        onTitle = editing::typed,
                        onSave = { editing.submit(onAdd(editing.title, editing.addPage)) },
                        onCancel = editing::cancel,
                    )
                }
            }
            items(shown, key = { it.row }) { row ->
                val renaming = (editing.draft as? ContentsEdit.Renaming)?.takeIf { it.row.id == row.id }
                ContentsRow(
                    row = row,
                    rowWidthDp = widthDp,
                    largestPageNumber = widest,
                    onToggle = { session.navigation = session.navigation.toggled(row.id) },
                    onClick = {
                        row.page?.let(onGoToPage)
                        onDismiss()
                    },
                    // Only the notebook's own rows can be edited: a row read out of the PDF is the file's
                    // bookmark, and it becomes the notebook's the moment one is added.
                    onRename = if (row.id.startsWith("bkm_")) ({ editing.renaming(row) }) else null,
                    onDelete = if (row.id.startsWith("bkm_")) ({ editing.deleting(row) }) else null,
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

/**
 * The Contents filter: one roomy field, a Clear button once it has words, and a keyboard that closes on Done
 * so the list is reachable again — a search box that keeps the IME over its own results is a search box
 * nobody can use with a keyboard attached.
 */
@Composable
private fun ContentsFilter(query: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Filter") },
        placeholder = { Text("Chapter or section") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        },
    )
}

/**
 * The Contents door for a **pane**: the document's index resolved against its own pages, plus the page in
 * view, so the card can mark the row the reader already stands on.
 *
 * Resolved from the canvas's document rather than from the PDF alone, which is what lets a bookmark on an
 * ordinary sheet appear here at all — and re-resolved when the document changes, since a page deleted or
 * moved turns a row into one that leads nowhere.
 */
@Composable
internal fun PdfContentsLane(pane: PaneState, onGoToPage: (Int) -> Unit) {
    val surface = pane.surface
    val rows = remember(surface, pane.documentVersion, pane.outline) {
        surface?.let { OutlineResolution.rowsOf(it.doc.outline, pane.outline, it.doc.pages) }.orEmpty()
    }
    // A document still showing the PDF's index is seeded with it when the first bookmark arrives, so the rows
    // the reader is looking at stay on screen instead of being replaced by the one row just named.
    val seed = remember(pane.outline) { pane.outline?.let { PdfOutlineSeeding.tree(it) } }
    val commands = surface?.pageCommands
    // One session per open document: closing the card keeps its filter and place, opening another notebook
    // does not inherit them.
    val session = remember(surface) { ContentsSession() }
    PdfContentsButton(
        rows = rows,
        // The pane's own page state, not a fresh read of the canvas: it is what recomposes when the reader
        // scrolls or a row is followed, so the mark moves with them instead of staying where the card opened.
        currentPage = pane.currentPage,
        session = session,
        onGoToPage = onGoToPage,
        onAdd = { title, page -> commands?.addBookmark(title, seed, page) ?: missingCommands() },
        onRename = { id, title -> commands?.renameBookmark(id, title) ?: missingCommands() },
        onDelete = { id -> commands?.deleteBookmark(id) ?: missingCommands() },
    )
}

/** The answer a card without a surface gets: there is no document to write to. */
private fun missingCommands(): OutlineEdits.Result =
    OutlineEdits.Result.Refused(OutlineEdits.Refusal.NOT_FOUND)

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
            .clickable(enabled = row.hasDestination && !renaming, role = Role.Button, onClick = onClick)
            // One announcement for the whole row: a title, a page and whether it opens at all, instead of a
            // title with a lone number beside it and a click that may do nothing.
            .semantics(mergeDescendants = true) {
                contentDescription = ContentsRowLayout.announcement(row.title, number)
            }
            // The indent is what the **whole** row leaves it: the branch gutter, the action gutter, the page
            // column and the title's minimum are room the hierarchy cannot spend.
            .padding(
                start = ContentsRowLayout.indentInRow(row.depth, rowWidthDp, pageColumnDp).dp,
                top = 2.dp,
                bottom = 2.dp,
            ),
        // The number sits on the title's **first** line, not halfway down a two-line title: a table of
        // contents whose figures float between the lines is a table of contents nobody can scan.
        verticalAlignment = Alignment.Top,
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
                .combinedClickable(onClick = onClick, enabled = row.hasDestination, onLongClick = { fullTitle = true }),
        )
        DropdownMenu(expanded = fullTitle, onDismissRequest = { fullTitle = false }) {
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
                style = MaterialTheme.typography.labelSmall.copy(
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
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
            imageVector = (if (expanded) ChromeGlyph.CHEVRON_DOWN else ChromeGlyph.CHEVRON_UP).imageVector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
