package com.mobixournal.desktop.shell
import com.mobixournal.desktop.navigation.ContentsDragList
import com.mobixournal.desktop.tools.DesktopTextAction
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.mobixournal.desktop.tools.GlassPopup
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
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
 * The sidebar's **Pages / Bookmarks** switch: one persistent control above either list, so the two navigation
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
            DesktopTextAction(
                label = if (option == DesktopSidebarView.PAGES) "Pages" else "Bookmarks",
                onClick = { onView(option) },
                modifier = Modifier.weight(1f),
                picked = picked,
                role = Role.Tab,
                style = DesktopTheme.field,
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
    LaunchedEffect(naming?.row?.id) {
        val at = rows.indexOfFirst { it.id == naming?.row?.id }
        if (at >= 0) {
            androidx.compose.runtime.withFrameNanos { }
            tab.contentsList.scrollToItem(at)
        }
    }
    Column(modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
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
        LaunchedEffect(rows, adding) {
            val at = rows.indexOfFirst { it.id == tab.contentsNavigation.anchorId }
            val reveal = rows.indexOfFirst { it.id == editing.revealId }
            if (adding) tab.contentsList.scrollToItem(0)
            else if (reveal >= 0) {
                tab.contentsList.scrollToItem(reveal)
                editing.revealId = null
            } else if (rows.isNotEmpty() && at >= 0 && at != tab.contentsList.firstVisibleItemIndex) {
                tab.contentsList.scrollToItem(at)
            }
        }
        LaunchedEffect(tab.contentsList.firstVisibleItemIndex, rows) {
            val row = rows.getOrNull(tab.contentsList.firstVisibleItemIndex - if (adding) 1 else 0) ?: return@LaunchedEffect
            tab.contentsNavigation = tab.contentsNavigation.scrolledTo(row.id, row.row)
        }
        if (rows.isEmpty() && !adding) {
            Text(
                text = if (query.isBlank()) "No bookmarks yet" else "No bookmarks match \"${query.trim()}\"",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.label,
            )
            return@Column
        }
        ContentsListBlock(
            tab = tab,
            rows = rows,
            allRows = resolved,
            editing = editing,
            adding = adding,
            contentsEdits = contentsEdits,
            onSelectPage = onSelectPage,
        )
    }
}

/**
 * The outline itself: the add draft when one is open, then every visible row — drawn by the **sidebar's** own
 * width, so the indent gives way to the title rather than the other way round, at every dp the sidebar has left.
 */
@Composable
private fun ColumnScope.ContentsListBlock(
    tab: DesktopTab,
    rows: List<OutlineRow>,
    allRows: List<OutlineRow>,
    editing: ContentsEditHost,
    adding: Boolean,
    contentsEdits: ContentsEdits,
    onSelectPage: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val widthDp = maxWidth.value
        val widest = largestPageNumber(rows)
        ContentsDragList(allRows, rows, tab.contentsList,
            editing.draft == null, { id, delta -> editing.submit(contentsEdits.move(id, delta)) }) { listModifier, rowModifier, dragging ->
            LazyColumn(state = tab.contentsList, modifier = listModifier, userScrollEnabled = !dragging) {
                if (adding) {
                    item(key = "add-draft") {
                        ContentsAddDraft(
                            page = editing.addPage,
                            title = editing.title,
                            error = editing.error,
                            onTitle = editing::typed,
                            onSave = {
                                val result = contentsEdits.add(editing.title, editing.addPage)
                                if (result is OutlineEdits.Result.Ok) {
                                    editing.revealId = result.tree.nodes.last().id
                                    tab.contentsNavigation = tab.contentsNavigation.withQuery("")
                                }
                                editing.submit(result)
                            },
                            onCancel = editing::cancel,
                        )
                    }
                }
                items(rows, key = { it.id }) { row ->
                    val renaming = (editing.draft as? ContentsEditState.Naming)?.takeIf { it.row?.id == row.id }
                    OutlineRowView(
                        row = row,
                        modifier = rowModifier(row),
                        rowWidthDp = widthDp,
                        largestPageNumber = widest,
                        onSelectPage = onSelectPage,
                        onToggle = { tab.contentsNavigation = tab.contentsNavigation.toggled(row.id) },
                        // Only the notebook's own rows can be edited: the PDF's rows are the file's, and a first
                        // bookmark seeds the tree from them so they stay in the list.
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
internal fun ContentsHeaderAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    DesktopTextAction(label = label, enabled = enabled, onClick = onClick)
}

/**
 * The window's bookmark filter: one flat field, a placeholder, and the keyboard's own Escape handled by the
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
            .bookmarkField(focused = focused)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        decorationBox = { field ->
            Box {
                if (query.isEmpty()) {
                    Text("Filter bookmarks", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                }
                field()
            }
        },
    )
}

/** Reserve actual tabular glyph widths at the reader's text scale, plus the title gap. */
@Composable
private fun measuredContentsPageColumn(largestPageNumber: Int): Float {
    val measurer = rememberTextMeasurer()
    val style = DesktopTheme.numeric
    val digits = largestPageNumber.coerceAtLeast(1).toString().length
    val widthPx = (0..9).maxOf { digit ->
        measurer.measure(digit.toString().repeat(digits), style, softWrap = false).size.width
    }
    return with(LocalDensity.current) { widthPx.toDp().value } + ContentsRowLayout.GAP_DP
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
    if (renaming) {
        ContentsTitleField(
            value = title, onValue = onTitle, error = error, placeholder = "Bookmark title",
            onSave = onSaveDraft, onCancel = onCancelDraft,
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            page = row.page,
        )
        return
    }
    val pageColumnDp = measuredContentsPageColumn(largestPageNumber)
    val target = row.page
    val number = target?.plus(1)
    val heading = ContentsRowLayout.isHeading(row.depth)
    Row(
        modifier
            .fillMaxWidth()
            // A row that leads nowhere answers the pointer with nothing: `active` keeps the hover light off
            // it, so the row's own "cannot be opened" is not contradicted by a highlight.
            .quietSidebarClick(enabled = target != null, role = Role.Button) {
                if (target != null) onSelectPage(target)
            }
            // One announcement for the whole row: the title, the page and whether it opens at all.
            .semantics(mergeDescendants = true) {
                contentDescription = ContentsRowLayout.announcement(row.title, number)
            }
            // The indent is what the **whole** row leaves it: the branch gutter, the action gutter, the page
            // column and the title's minimum are room the hierarchy cannot spend.
            .padding(
                start = minOf(row.depth.coerceAtLeast(0) * 8f, 16f,
                    ContentsRowLayout.indentInRow(row.depth, rowWidthDp - 4f, pageColumnDp)).dp,
                top = 2.dp,
                bottom = 2.dp,
                end = 4.dp,
            ),
        // The number sits on the title's first line, as on the tablet: a wrapped title must not leave its
        // page number floating between the two lines.
        verticalAlignment = Alignment.Top,
    ) {
        ContentsChevron(row = row, onToggle = onToggle)
        OutlineRowTitle(row = row, heading = heading, target = target)
        // Reserved on every row, empty where there is no page, so the titles end on one line.
        Box(Modifier.width(pageColumnDp.dp), contentAlignment = Alignment.TopEnd) {
            if (number != null) {
                // The number's line height is the title's, so it sits on the title's first baseline.
                Text(
                    text = "$number",
                    color = DesktopTheme.inkMuted,
                    style = DesktopTheme.numeric.copy(lineHeight = DesktopTheme.field.lineHeight),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(start = ContentsRowLayout.GAP_DP.dp),
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
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    Box {
        Box(
            Modifier
                .size(ContentsRowLayout.ACTION_DP.dp)
                .quietSidebarClick(interactions = interactions) { open = true }
                .semantics { contentDescription = "Actions for ${title.ifBlank { "bookmark" }}" },
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.size(16.dp)) { drawChromeGlyph(ChromeGlyph.MORE_HORIZ,
                if (hovered || focused) DesktopTheme.ink else DesktopTheme.inkSecondary) }
        }
        val drop = with(LocalDensity.current) { ContentsRowLayout.ACTION_DP.dp.roundToPx() }
        GlassPopup(visible = open, onDismissRequest = { open = false }, offset = IntOffset(0, drop)) {
            Column(
                Modifier.width(IntrinsicSize.Max).glassSurface().padding(4.dp),
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
internal fun ContentsMenuRow(label: String, title: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    Text(
        label,
        color = DesktopTheme.ink,
        style = DesktopTheme.label,
        modifier = Modifier
            .fillMaxWidth()
            .hoverFill(RoundedCornerShape(6.dp),
                base = if (focused || pressed) DesktopTheme.hoverSlot else Color.Transparent)
            .clickable(interactionSource = interactions, indication = null,
                role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label ${title.ifBlank { "bookmark" }}" }
            .heightIn(min = 40.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    )
}

/** One word of a row's own actions, named together with its row so two chapters stay distinguishable. */
@Composable
internal fun ContentsRowAction(label: String, title: String, enabled: Boolean = true, onClick: () -> Unit) {
    DesktopTextAction(
        label = label,
        description = "$label ${title.ifBlank { "bookmark" }}",
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * A row's title, with the whole name behind a hover: the pointer rests on a title that ended in an ellipsis and
 * the tooltip reads all of it, without the row's own click being spent on inspecting rather than jumping.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.OutlineRowTitle(row: OutlineRow, heading: Boolean, target: Int?) {
    TooltipArea(
        modifier = Modifier.weight(1f),
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
            style = DesktopTheme.field.copy(fontWeight = if (heading) FontWeight.Medium else FontWeight.Normal),
            maxLines = ContentsRowLayout.TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
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
            .quietSidebarClick(onClick = onToggle)
            .semantics {
                contentDescription = if (row.expanded) "Collapse ${row.title}" else "Expand ${row.title}"
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.size(14.dp)) {
            drawChromeGlyph(
                if (row.expanded) ChromeGlyph.CHEVRON_DOWN else ChromeGlyph.CHEVRON_UP,
                DesktopTheme.inkSecondary,
            )
        }
    }
}


/** A narrow keyboard-focus edge replaces the default filled indication. */
@Composable
internal fun Modifier.quietSidebarClick(
    enabled: Boolean = true,
    role: Role = Role.Button,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
): Modifier {
    val focused by interactions.collectIsFocusedAsState()
    return drawBehind {
        if (enabled && focused) drawLine(DesktopTheme.accent,
            Offset(4.dp.toPx(), size.height - 1.dp.toPx()),
            Offset(size.width - 4.dp.toPx(), size.height - 1.dp.toPx()), 1.dp.toPx())
    }.clickable(interactionSource = interactions, indication = null, enabled = enabled,
        role = role, onClick = onClick)
}

