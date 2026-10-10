package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * A PDF's **Contents** — the outline bookmark tree the file carries — as a tappable list, opened from
 * the Pages menu of a document with a PDF background.
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
    AlertDialog(
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
