package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The list's three commands and the page they act on.
 *
 * The list keeps **one** selection — the page in view — so the commands need no pick to assemble first: a
 * reader who has scrolled to a page and wants it gone taps the bin, and the figure above the buttons is
 * that page's own number in the ink the rows carry it. A run of pages is the typed range's job
 * (`PageRangeDialog`, behind the lane's gear), not something the list makes you collect row by row.
 *
 * A move is refused at the ends of the document rather than wrapping round to the other end — a page that
 * quietly teleports reads as a bug — and a delete is refused when the page is the whole document, which is
 * [com.mobixournal.render.PageOps.removeAll]'s own rule.
 */
@Composable
internal fun PageListCommands(
    acting: Set<Int>,
    currentPage: Int,
    pageCount: Int,
    onMove: (picked: Set<Int>, delta: Int) -> Unit,
    onDelete: (picked: Set<Int>) -> Unit,
) {
    val canMoveUp = acting.isNotEmpty() && acting.min() > 0
    val canMoveDown = acting.isNotEmpty() && acting.max() < pageCount - 1
    val canDelete = acting.isNotEmpty() && acting.size < pageCount
    val subject = if (acting.isNotEmpty()) "Page ${currentPage + 1} of $pageCount" else "\u2014"
    // The foot reaches the lane's own bottom edge, on the lane's 6 dp of air: the app draws edge to edge
    // with transparent system bars, so the only thing under the commands is an invisible gesture strip,
    // and reserving it left the three buttons floating a whole strip higher than the counter on the page.
    Column(
        Modifier.fillMaxWidth().padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // The subject on the left, the lane's own door on the right: the foot's bottom-right corner.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                subject,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            // 48 dp each (Material's minimum target), which the 196 dp lane holds with room to spare.
            IconButton(onClick = { onMove(acting, -1) }, enabled = canMoveUp, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.ArrowUpward,
                    contentDescription = "Move pages up",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onMove(acting, 1) }, enabled = canMoveDown, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.ArrowDownward,
                    contentDescription = "Move pages down",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { onDelete(acting) }, enabled = canDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete pages",
                    modifier = Modifier.size(20.dp),
                    tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
