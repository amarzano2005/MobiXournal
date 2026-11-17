package com.mobixournal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.render.ContentsNavigation
import com.mobixournal.render.OutlineEdits
import com.mobixournal.render.OutlineRow

/**
 * The card's **session**: what the reader has done to this document's Contents and where the list was left.
 *
 * Remembered against the **document**, not against the rows, so renaming a bookmark, adding one, undoing an
 * edit or closing and reopening the card cannot clear the filter, open every branch or throw the list back to
 * the top. A new document gets a new session and inherits nothing.
 */
internal class ContentsSession {
    val bookmarkRows = BookmarkRowsCache()

    // Scroll changes are deliberately not Compose state: a fling must not recompose/project the whole tree.
    var scrollAnchorId: String? = null
    var scrollAnchorRow: Int = 0
    var scrollAnchorOffset: Int = 0

    /** The filter, the folded branches and the row at the top of the list. */
    var navigation by mutableStateOf(ContentsNavigation())

    /** The one edit in progress, if any. */
    val editing = ContentsEditHost()

    /** The list's own scroll position, kept so reopening the card lands where it was left. */
    val list = LazyListState()
}

/**
 * What the Contents card is doing to one row: naming a new one, retitling an existing one, or asking whether
 * to delete it. Kept beside the card rather than inside it so the row list stays about reading.
 *
 * Nothing here is reached by tapping a row: a row's own overflow opens a **neutral menu**, and only the
 * answers of that menu start an edit — so a row can be inspected without the card turning into a form.
 */
internal sealed interface ContentsEdit {

    /**
     * Naming a row for [page] — the page the reader was on when **Add bookmark** was pressed. Captured then,
     * so a page turned while the name is typed cannot silently retarget the bookmark being written.
     */
    data class Adding(val page: Int?) : ContentsEdit

    /** Retitling [row]; its id is what the edit names. */
    data class Renaming(val row: OutlineRow) : ContentsEdit

    /** Asking about [row] before removing it. */
    data class Deleting(val row: OutlineRow) : ContentsEdit
}

/**
 * The card's one edit at a time, kept in one object so the dialog's body stays the list it arranges.
 *
 * The edit is **inline**: retitling happens where the row is, and a new bookmark is a draft row above its
 * neighbours, so the filter, the branches and the pages around it stay on screen while a name is typed. A
 * refusal is shown beside the draft — the card only closes the edit when the command actually wrote.
 */
internal class ContentsEditHost {

    private var edit by mutableStateOf<ContentsEdit?>(null)
    private var typedTitle by mutableStateOf("")
    private var refusal by mutableStateOf<String?>(null)

    /** The edit in progress, or null when the list is only being read. */
    val draft: ContentsEdit? get() = edit

    /** The name being typed. */
    val title: String get() = typedTitle

    /** The refusal under the draft, once a command has answered. */
    val error: String? get() = refusal

    /** The page the add draft was opened on — what the bookmark will point at. */
    val addPage: Int? get() = (edit as? ContentsEdit.Adding)?.page

    /** The row being asked about, or null when no delete question is up. */
    val deleting: ContentsEdit.Deleting? get() = edit as? ContentsEdit.Deleting

    /** Naming a new row for [page], the page in view when Add was pressed. */
    fun adding(page: Int?) {
        typedTitle = ""
        refusal = null
        edit = ContentsEdit.Adding(page)
    }

    /** Retitling [row]: the field opens on the title it already has. */
    fun renaming(row: OutlineRow) {
        typedTitle = row.title
        refusal = null
        edit = ContentsEdit.Renaming(row)
    }

    /** Asking about [row] before it goes. */
    fun deleting(row: OutlineRow) {
        typedTitle = row.title
        refusal = null
        edit = ContentsEdit.Deleting(row)
    }

    /** The name as it is now typed. */
    fun typed(value: String) {
        typedTitle = value
    }

    /** Closing whatever the card is doing, leaving the tree exactly as it was. */
    fun cancel() {
        edit = null
        refusal = null
    }

    /** A command's answer: a refusal stays beside the draft in the reader's words, a write closes it. */
    fun submit(result: OutlineEdits.Result) {
        if (result is OutlineEdits.Result.Refused) refusal = refusalCopy(result.reason) else cancel()
    }
}

/**
 * What the card's own dismiss means — Android's **Back**, a tap outside the card. An edit in progress goes
 * first: a name being typed or a delete being asked is the most transient thing on screen, and cancelling it
 * leaves the list exactly as it was. Only a card with nothing in progress closes (`EditorBackHandler`'s own
 * order, on the card's own door).
 */
internal fun ContentsEditHost.dismissOrCancel(onDismiss: () -> Unit): () -> Unit = {
    if (draft == null && deleting == null) onDismiss() else cancel()
}

/** The sentence a refusal is shown as — the reason in the reader's words, never a class name. */
internal fun refusalCopy(reason: OutlineEdits.Refusal): String = when (reason) {
    OutlineEdits.Refusal.EMPTY_TITLE -> "A bookmark needs a title."
    OutlineEdits.Refusal.NOT_FOUND -> "That bookmark is gone."
    OutlineEdits.Refusal.NO_TARGET -> "This page cannot hold a bookmark."
}

/**
 * The one-line field a bookmark is named in, wherever the name is typed: inside the row being retitled or in
 * the draft the **Add bookmark** command opens. **Enter** saves, the check saves, the cross abandons, and a
 * blank name cannot be saved at all. A refusal is printed under the field so the name can be fixed in place.
 */
@Composable
internal fun ContentsTitleField(
    value: String,
    error: String?,
    placeholder: String,
    onValue: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (value.isNotBlank()) {
                            focus.clearFocus()
                            onSave()
                        }
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        field()
                    }
                },
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onSave,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(ContentsRowLayout.ACTION_DP.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Save bookmark",
                    tint = if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The **Add bookmark** row above the list: the door to naming a row for the page in view. It stays reachable
 * while a name is being typed (the draft sits in the list, not over it), and it names the page it captured.
 */
@Composable
internal fun ContentsAddRow(enabled: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(
            onClick = onAdd,
            enabled = enabled,
            modifier = Modifier
                .semantics { role = Role.Button }
                .padding(vertical = 2.dp),
        ) { Text("Add bookmark") }
    }
}
