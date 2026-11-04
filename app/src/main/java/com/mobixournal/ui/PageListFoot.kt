package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
 * The list's three commands and what they will act on.
 *
 * The commands take the **pick**, or the page in view when nothing is picked: a reader who has scrolled
 * to a page and wants it gone should not have to tick a box first, and the figure above the buttons is
 * the subject — a count for a pick, the page's own number for the page in view, in the same ink as the
 * rows carry it.
 *
 * A move is refused at the ends of the document rather than wrapping the run round to the other end —
 * a page that quietly teleports reads as a bug — and a delete is refused when the pick is the whole
 * document, which is [com.mobixournal.render.PageOps.removeAll]'s own rule.
 */
@Composable
internal fun PageListCommands(
    picked: Set<Int>,
    acting: Set<Int>,
    currentPage: Int,
    pageCount: Int,
    onMove: (picked: Set<Int>, delta: Int) -> Unit,
    onDelete: (picked: Set<Int>) -> Unit,
    contentsAction: (@Composable () -> Unit)?,
) {
    val canMoveUp = acting.isNotEmpty() && acting.min() > 0
    val canMoveDown = acting.isNotEmpty() && acting.max() < pageCount - 1
    val canDelete = acting.isNotEmpty() && acting.size < pageCount
    val subject = when {
        picked.isNotEmpty() -> "${picked.size} picked"
        acting.isNotEmpty() -> "${currentPage + 1}"
        else -> "\u2014"
    }
    // The foot clears the system navigation bar. The app draws edge to edge, so the lane runs to the
    // bottom of the screen: a control sitting under the gesture strip reads as one that needs two taps,
    // because the first one belongs to the system.
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(6.dp),
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
            contentsAction?.invoke()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
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
