package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.render.pdfSheetInView
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry
import com.mobixournal.render.PdfOutlineView
import com.mobixournal.render.PdfOutlineVisible

/**
 * The **Contents** door, as the page list's own: one glyph at the lane's foot, the PDF's outline behind it.
 *
 * It stands with the page commands because that is what it is — a way of jumping between pages — and it is
 * no longer a row of the Pages menu, which is about the page in front of you. A document with no outline
 * draws no button at all rather than a dead one, so the lane's foot says only what the file can do.
 */
@Composable
internal fun PdfContentsButton(
    outline: PdfOutline?,
    currentPdfPage: Int?,
    onGoToPdfPage: (Int) -> Unit,
) {
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
                currentPdfPage = currentPdfPage,
                onGoToPdfPage = onGoToPdfPage,
                onDismiss = { open = false },
            )
        }
    }
}

/**
 * A PDF's **Contents** — the outline bookmark tree the file carries — as a filterable, expandable list,
 * opened from the page list's foot ([PdfContentsButton]) of a document with a PDF background.
 *
 * The tree comes straight from the file (`PdfOutlineExtractor`), so it is the same table of contents
 * desktop Xournal++ or any PDF reader would show, and it travels with the document rather than being
 * app-side state. Tapping a row jumps the canvas to the page that entry names; a row with no destination of
 * its own — a chapter header that only groups sections — is shown but not tappable.
 *
 * **A long book needs two controls, and they must not fight.** A chapter's chevron closes it, so a thousand
 * bookmarks become a list a thumb can travel; the filter finds a section by name, showing the chapters above
 * it so the reader still knows where the hit sits in the book. The two rules live in `PdfOutlineView`, shared
 * with the window: rows are addressed by **position**, because two chapters may honestly share a title, and
 * filtering never touches the collapse state — clearing the box gives back the tree as it was left.
 */
@Composable
internal fun PdfContentsDialog(
    outline: PdfOutline,
    currentPdfPage: Int?,
    onGoToPdfPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(outline) { mutableStateOf("") }
    var collapsed by remember(outline) { mutableStateOf(emptySet<Int>()) }
    val rows = PdfOutlineView.visible(outline.entries, collapsed, query)
    val branches = remember(outline) { PdfOutlineView.branchRows(outline.entries) }
    val current = PdfOutlineView.currentRow(outline.entries, currentPdfPage)
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contents") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(Modifier.fillMaxWidth()) {
                ContentsFilter(
                    query = query,
                    onQuery = { query = it },
                    onClear = { query = "" },
                )
                Spacer(Modifier.height(8.dp))
                if (rows.isEmpty()) {
                    Text(
                        text = "No contents match \"${query.trim()}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                // The row's own width, not the screen's: in a split-screen pane the card is what the list has.
                BoxWithConstraints {
                    val widthDp = maxWidth.value
                    val widest = largestPageNumber(outline)
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(rows, key = { it.row }) { row ->
                            ContentsRow(
                                visible = row,
                                branched = row.row in branches,
                                expanded = row.row !in collapsed,
                                current = row.row == current,
                                rowWidthDp = widthDp,
                                largestPageNumber = widest,
                                onToggle = { collapsed = PdfOutlineView.toggled(collapsed, row.row) },
                                onClick = {
                                    onGoToPdfPage(row.entry.pageIndex)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * The Contents filter: one roomy field, a Clear button once it has words, and a keyboard that closes on Done
 * so the list is reachable again — a search box that keeps the IME over its own results is a search box
 * nobody can use with a keyboard attached.
 */
@Composable
private fun ContentsFilter(query: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Filter") },
        placeholder = { Text("Chapter or section") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        },
    )
}

/**
 * The Contents door for a **pane**: its outline, plus the sheet the page in view shows, so the card can mark
 * the row the reader already stands on.
 */
@Composable
internal fun PdfContentsLane(pane: PaneState, onGoToPdfPage: (Int) -> Unit) {
    PdfContentsButton(
        outline = pane.outline,
        currentPdfPage = pane.surface?.pdfSheetInView(),
        onGoToPdfPage = onGoToPdfPage,
    )
}

/** The widest page number the card will print — the number its column is reserved from. */
private fun largestPageNumber(outline: PdfOutline): Int =
    outline.entries.maxOfOrNull { if (it.hasDestination) it.pageIndex + 1 else 0 } ?: 0

/**
 * One Contents row: its chevron, its title indented by depth, and the page it leads to in a column of its own.
 *
 * The indentation, the column and the announcement come from [ContentsRowLayout], so this row and the
 * window's say the same thing about the same outline; what is the tablet's own is the type — a chapter
 * heading in the heading face and its sections under it in the body face. The chevron is a target of its own
 * (a chapter's row still jumps to the chapter), and an ancestor kept only for the filter's context is drawn
 * dimmer than the hit, so a search shows its one answer instead of shouting the path to it.
 */
@Composable
private fun ContentsRow(
    visible: PdfOutlineVisible,
    branched: Boolean,
    expanded: Boolean,
    current: Boolean,
    rowWidthDp: Float,
    largestPageNumber: Int,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val entry = visible.entry
    val pageColumnDp = ContentsRowLayout.pageColumnDp(
        largestPageNumber = largestPageNumber,
        rowWidthDp = rowWidthDp,
        fontScale = LocalDensity.current.fontScale,
    )
    val heading = ContentsRowLayout.isHeading(entry.depth)
    val number = (entry.pageIndex + 1).takeIf { entry.hasDestination }
    val ink = when {
        visible.context -> MaterialTheme.colorScheme.onSurfaceVariant
        entry.hasDestination -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                top = 2.dp,
                bottom = 2.dp,
            ),
        // The number sits on the title's **first** line, not halfway down a two-line title: a table of
        // contents whose figures float between the lines is a table of contents nobody can scan.
        verticalAlignment = Alignment.Top,
    ) {
        ContentsChevron(branched = branched, expanded = expanded, title = entry.title, onToggle = onToggle)
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
            color = if (current) MaterialTheme.colorScheme.primary else ink,
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

/**
 * The branch control, or the room it would take.
 *
 * A leaf keeps the gutter empty rather than pulling its title left: a tree whose titles start at two
 * different edges is a tree nobody can follow. The chevron toggles **only** its branch — the row itself
 * still jumps — and it is announced with the branch it belongs to, since a card of identical arrows says
 * nothing to a screen reader.
 */
@Composable
private fun ContentsChevron(branched: Boolean, expanded: Boolean, title: String, onToggle: () -> Unit) {
    if (!branched) {
        Spacer(Modifier.width(CHEVRON_DP))
        return
    }
    IconButton(
        onClick = onToggle,
        modifier = Modifier
            .size(CHEVRON_DP)
            .semantics {
                contentDescription = if (expanded) "Collapse $title" else "Expand $title"
            },
    ) {
        Icon(
            imageVector = (if (expanded) ChromeGlyph.CHEVRON_DOWN else ChromeGlyph.CHEVRON_UP).imageVector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The width a chevron (or its empty gutter) takes on every row. */
private val CHEVRON_DP = 28.dp
