package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry

/** How far one nesting level indents a row of the list. */
private val CONTENT_INDENT = 14.dp

/**
 * The **Contents** door, as the page list's own: one glyph at the lane's foot, the PDF's outline behind it.
 *
 * It stands with the page commands because that is what it is — a way of jumping between pages — and it is
 * no longer a row of the Pages menu, which is about the page in front of you. A document with no outline
 * draws no button at all rather than a dead one, so the lane's foot says only what the file can do.
 */
@Composable
internal fun PdfContentsButton(outline: PdfOutline?, onGoToPdfPage: (Int) -> Unit) {
    if (outline == null || outline.entries.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(34.dp)) {
        Icon(
            Icons.AutoMirrored.Outlined.Toc,
            contentDescription = "Contents",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    DialogPresence(open) {
        // The tree survives the flag: closing clears `open`, and the dialog is still on its way out.
        val tree = rememberClosingArgument(outline)
        if (tree != null) {
            PdfContentsDialog(
                outline = tree,
                onGoToPdfPage = onGoToPdfPage,
                onDismiss = { open = false },
            )
        }
    }
}

/**
 * A PDF's **Contents** — the outline bookmark tree the file carries — as a tappable list, opened from
 * the page list's foot ([PdfContentsButton]) of a document with a PDF background.
 *
 * The tree comes straight from the file (`PdfOutlineExtractor`), so it is the same table of contents
 * desktop Xournal++ or any PDF reader would show, and it travels with the document rather than being
 * app-side state. Tapping a row jumps the canvas to the page that entry names; a row with no
 * destination of its own — a chapter header that only groups sections — is shown but not tappable.
 */
@Composable
internal fun PdfContentsDialog(
    outline: PdfOutline,
    onGoToPdfPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contents") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(outline.entries) { entry ->
                    ContentsRow(
                        entry = entry,
                        onClick = {
                            onGoToPdfPage(entry.pageIndex)
                            onDismiss()
                        },
                    )
                }
            }
        },
    )
}

/** One Contents row: its title, indented by depth, with the target page number on the right. */
@Composable
private fun ContentsRow(entry: PdfOutlineEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.hasDestination, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(CONTENT_INDENT * entry.depth + 4.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (entry.depth == 0) FontWeight.Medium else FontWeight.Normal,
            color = if (entry.hasDestination) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.hasDestination) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${entry.pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
