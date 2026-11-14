package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.io.OutlineRow
import com.mobixournal.desktop.io.contentsRowsOf
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
internal fun PdfContentsSection(tab: DesktopTab, onSelectPage: (Int) -> Unit) {
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
    // A document whose outline was never read (or is empty) shows no section at all: a heading over
    // nothing is chrome pretending to be a feature.
    val available = tab.pdfIndex?.outline?.isEmpty == false
    if (!available) return
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
@Composable
internal fun OutlineRowView(
    row: OutlineRow,
    rowWidthDp: Float,
    largestPageNumber: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
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
            .padding(
                start = ContentsRowLayout.indentDp(row.depth, rowWidthDp, pageColumnDp).dp,
                top = 2.dp,
                bottom = 2.dp,
                end = 4.dp,
            ),
        // The number sits on the title's first line, as on the tablet: a wrapped title must not leave its
        // page number floating between the two lines.
        verticalAlignment = Alignment.Top,
    ) {
        ContentsChevron(row = row, onToggle = onToggle)
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Reserved on every row, empty where there is no page, so the titles end on one line.
        Box(Modifier.width(pageColumnDp.dp), contentAlignment = Alignment.TopEnd) {
            if (number != null) {
                Text(text = "$number", color = DesktopTheme.inkMuted, style = DesktopTheme.numeric)
            }
        }
    }
}

/** The branch control, or the gutter it would take — an empty one, so titles share a left edge. */
@Composable
private fun ContentsChevron(row: OutlineRow, onToggle: (() -> Unit)?) {
    if (!row.branch || onToggle == null) {
        Spacer(Modifier.width(CHEVRON_DP))
        return
    }
    Box(
        Modifier
            .size(CHEVRON_DP)
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

/** The width a chevron (or its empty gutter) takes on every row. */
private val CHEVRON_DP = 20.dp
