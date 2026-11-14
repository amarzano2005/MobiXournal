package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry

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
            // The row's own width, not the screen's: in a split-screen pane the card is what the list has.
            BoxWithConstraints {
                val widthDp = maxWidth.value
                val widest = largestPageNumber(outline)
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(outline.entries) { entry ->
                        ContentsRow(
                            entry = entry,
                            rowWidthDp = widthDp,
                            largestPageNumber = widest,
                            onClick = {
                                onGoToPdfPage(entry.pageIndex)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** The widest page number the card will print — the number its column is reserved from. */
private fun largestPageNumber(outline: PdfOutline): Int =
    outline.entries.maxOfOrNull { if (it.hasDestination) it.pageIndex + 1 else 0 } ?: 0

/**
 * One Contents row: its title, indented by depth, with the page it leads to in a column of its own.
 *
 * The indentation, the column and the announcement come from [ContentsRowLayout], so this row and the
 * window's say the same thing about the same outline; what is the tablet's own is the type — a chapter
 * heading in the heading face and its sections under it in the body face.
 */
@Composable
private fun ContentsRow(
    entry: PdfOutlineEntry,
    rowWidthDp: Float,
    largestPageNumber: Int,
    onClick: () -> Unit,
) {
    val pageColumnDp = ContentsRowLayout.pageColumnDp(
        largestPageNumber = largestPageNumber,
        rowWidthDp = rowWidthDp,
        fontScale = LocalDensity.current.fontScale,
    )
    val heading = ContentsRowLayout.isHeading(entry.depth)
    val number = (entry.pageIndex + 1).takeIf { entry.hasDestination }
    val ink = if (entry.hasDestination) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.hasDestination, role = Role.Button, onClick = onClick)
            // One announcement for the whole row: a title, a page and whether it opens at all, instead of a
            // title with a lone number beside it and a click that may do nothing.
            .semantics(mergeDescendants = true) {
                contentDescription = ContentsRowLayout.announcement(entry.title, number)
            }
            .padding(
                start = ContentsRowLayout.indentDp(entry.depth, rowWidthDp, pageColumnDp).dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        // The number sits on the title's **first** line, not halfway down a two-line title: a table of
        // contents whose figures float between the lines is a table of contents nobody can scan.
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = entry.title,
            style = if (heading) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            // Inter ships 400, 500 and 700 (`TypeContractGuardTest`): the heading step is the larger face
            // plus the one weight the theme owns, not a 600 the family answers with 700.
            fontWeight = if (heading) FontWeight.Medium else FontWeight.Normal,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // The column is kept on every row, empty where a row leads nowhere, so the titles end on one line
        // and a four-digit page does not narrow its neighbours.
        Box(
            modifier = Modifier.width(pageColumnDp.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (number != null) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
