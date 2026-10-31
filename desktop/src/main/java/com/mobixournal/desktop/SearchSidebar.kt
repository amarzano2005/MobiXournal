package com.mobixournal.desktop

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph

/**
 * The search's **results**, in the page sidebar under the page list.
 *
 * It exists only while a query is up: a heading over nothing is chrome pretending to be a feature, the
 * same rule the PDF Contents section follows. The list is not a second search — it renders the hits the
 * tab's [DesktopSearch] already holds (`SearchRunner` walked the document once), so scrolling it or
 * repainting the page costs nothing.
 *
 * One row per match: the page it is on, the snippet the engine cut for it, and the accent mark on the row
 * the reader is standing on. A click asks for that match, which goes to its page and makes it current —
 * the same pair of things `Enter` in the field does.
 */
@Composable
internal fun SearchResultsSection(tab: DesktopTab, onSearchHit: (Int) -> Unit) {
    val search = tab.search
    if (!search.isActive) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp)) {
                drawChromeGlyph(ChromeGlyph.SEARCH, DesktopTheme.inkSecondary)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                when (search.total) {
                    0 -> "No results"
                    1 -> "1 result"
                    else -> "${search.total} results"
                },
                color = DesktopTheme.inkSecondary,
                style = DesktopTheme.eyebrow,
            )
        }
        Spacer(Modifier.height(4.dp))
        if (search.hits.isEmpty()) {
            // A scanned page and a document with no typed text answer this: the query was searched, it
            // simply matched nothing — saying so is the whole answer.
            Text("Nothing matched “${search.query}”", color = DesktopTheme.inkMuted, style = DesktopTheme.label)
            return@Column
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            itemsIndexed(search.hits) { index, hit ->
                SearchResultRow(
                    pageLabel = pageLabel(hit.pageIndex, tab.pageCount),
                    snippet = hit.snippet.ifEmpty { "Match on this page" },
                    current = index == search.current,
                    onClick = { onSearchHit(index) },
                )
            }
        }
    }
}

/** One result: its page, its snippet, and the accent mark on the match in view. */
@Composable
private fun SearchResultRow(pageLabel: String, snippet: String, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pageLabel,
            color = if (current) DesktopTheme.accent else DesktopTheme.inkMuted,
            style = DesktopTheme.numeric,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            snippet,
            color = if (current) DesktopTheme.ink else DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
