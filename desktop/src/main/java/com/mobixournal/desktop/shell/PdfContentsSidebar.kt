package com.mobixournal.desktop.shell
import com.mobixournal.desktop.tools.hoverFill
import com.mobixournal.desktop.io.OutlineRow
import com.mobixournal.desktop.io.contentsRowsOf
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.drawChromeGlyph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.design.ChromeGlyph

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
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            items(rows) { row -> OutlineRowView(row, onSelectPage) }
        }
    }
}

/** One Contents row: its indent, its title, and the page it leads to. */
@Composable
private fun OutlineRowView(row: OutlineRow, onSelectPage: (Int) -> Unit) {
    val target = row.page
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(RoundedCornerShape(8.dp))
            .then(if (target != null) Modifier.clickable { onSelectPage(target) } else Modifier)
            .padding(start = (row.depth * 10).dp, top = 3.dp, bottom = 3.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.title,
            // An entry with no page is shown but not jumpable: dimmed, as the tablet draws it.
            color = if (target != null) DesktopTheme.ink else DesktopTheme.inkMuted,
            style = DesktopTheme.label,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (target != null) {
            Text(
                text = "${target + 1}",
                color = DesktopTheme.inkMuted,
                style = DesktopTheme.numeric,
                fontSize = 11.sp,
            )
        }
    }
}
