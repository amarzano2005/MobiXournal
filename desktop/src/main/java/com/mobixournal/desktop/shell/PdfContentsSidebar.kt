package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.io.OutlineRow
import com.mobixournal.desktop.io.contentsRowsOf
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ContentsRowLayout

/**
 * The PDF's **Contents** in the page sidebar: a collapsible section under the page list, filled from the
 * document's outline.
 *
 * The rows are read **only when the section is opened** — a thousand bookmarks cost nothing until someone
 * asks for them, and a document with no outline (or no PDF at all) shows no section rather than an empty
 * one. Each row is indented by its outline depth and prints the page it leads to; an entry that names no
 * page is dimmed and cannot be clicked, exactly as `docs/pdf.md` says for the tablet.
 *
 * Nothing here writes the file: the outline is navigation, so a click selects a page and leaves the
 * document byte-identical.
 */
@Composable
internal fun PdfContentsSection(tab: DesktopTab, onSelectPage: (Int) -> Unit) {
    // Read only when opened: the walk over the page list is what this section must not pay for by merely
    // existing (`docs/desktop-workstation.md`).
    var open by remember(tab) { mutableStateOf(false) }
    val rows = if (open) remember(tab, tab.pdfIndex, tab.document.edits) { contentsRowsOf(tab) } else emptyList()
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
        Spacer(Modifier.height(4.dp))
        if (rows.isEmpty()) {
            Text("No contents", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            return@Column
        }
        // The row's own width, not the window's: the sidebar is a column the reader can narrow, and the
        // indentation gives way to the title rather than the other way round.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value
            val widest = largestPageNumber(rows)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(rows) { row -> OutlineRowView(row, widthDp, widest, onSelectPage) }
            }
        }
    }
}

/** The widest page number the list will print — the number its column is reserved from. */
private fun largestPageNumber(rows: List<OutlineRow>): Int = (rows.maxOfOrNull { it.page ?: 0 } ?: 0) + 1

/**
 * One Contents row: its indent, its title, and the page it leads to in a column of its own.
 *
 * The indentation, the column and the announcement come from [ContentsRowLayout], so this row and the
 * tablet's say the same thing about the same outline — a chapter heading in the heading face, its sections
 * under it smaller and lighter, a row that leads nowhere dimmed and not clickable.
 */
@Composable
internal fun OutlineRowView(
    row: OutlineRow,
    rowWidthDp: Float,
    largestPageNumber: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
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
                top = 3.dp,
                bottom = 3.dp,
                end = 4.dp,
            ),
        // The number sits on the title's first line, as on the tablet: a wrapped title must not leave its
        // page number floating between the two lines.
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.title,
            // An entry with no page is shown but not jumpable: dimmed, as the tablet draws it.
            color = if (target != null) DesktopTheme.ink else DesktopTheme.inkMuted,
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
