package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mobixournal.render.OutlineEdits
import com.mobixournal.render.OutlineRow

/**
 * What the Contents card is doing to one row: naming a new one, retitling an existing one, or asking whether
 * to delete it. Kept beside the card rather than inside it so the row list stays about reading.
 *
 * Nothing here is reached by tapping a row: a row's own overflow opens a **neutral menu**, and only the
 * answers of that menu start an edit — so a row can be inspected without the card turning into a form.
 */
internal sealed interface ContentsEdit {

    /** Naming a row that does not exist yet. */
    data object Adding : ContentsEdit

    /** Retitling [row]; its id is what the edit names. */
    data class Renaming(val row: OutlineRow) : ContentsEdit

    /** Asking about [row] before removing it. */
    data class Deleting(val row: OutlineRow) : ContentsEdit
}

/**
 * The card's one edit at a time, kept in one object so the dialog's body stays the list it arranges.
 *
 * A refusal is shown where the reader is: under the title field, or under the delete question — the card only
 * closes its form when the command actually wrote something. The delete question is drawn **above the list**
 * rather than in place of it, so the rows the reader was reading stay behind the answer.
 */
internal class ContentsEditHost {

    private var edit by mutableStateOf<ContentsEdit?>(null)
    private var title by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)

    /** The row being asked about, or null when no delete question is up. */
    val deleting: ContentsEdit.Deleting? get() = edit as? ContentsEdit.Deleting

    /** The title a form is holding — the name being typed, or the row's own while that is retitled. */
    val typedTitle: String get() = title

    /** Naming a new row for the page in view. */
    fun adding() {
        title = ""
        error = null
        edit = ContentsEdit.Adding
    }

    /** Retitling [row]: the field opens on the title it already has. */
    fun renaming(row: OutlineRow) {
        title = row.title
        error = null
        edit = ContentsEdit.Renaming(row)
    }

    /** Asking about [row] before it goes. */
    fun deleting(row: OutlineRow) {
        title = row.title
        error = null
        edit = ContentsEdit.Deleting(row)
    }

    /** Closing whatever the card is doing, leaving the tree exactly as it was. */
    fun cancel() {
        edit = null
        error = null
    }

    /** The card's rename field — true when it drew one instead of the list. */
    @Composable
    fun form(
        onAdd: (String) -> OutlineEdits.Result,
        onRename: (String, String) -> OutlineEdits.Result,
    ): Boolean = when (val current = edit) {
        is ContentsEdit.Adding, is ContentsEdit.Renaming -> {
            val renaming = current as? ContentsEdit.Renaming
            ContentsTitleEditor(
                title = title,
                heading = if (renaming == null) "New bookmark" else "Rename bookmark",
                error = error,
                onTitle = { title = it },
                onSave = { settle(if (renaming == null) onAdd(title) else onRename(renaming.row.id, title)) },
                onCancel = ::cancel,
            )
            true
        }
        else -> false
    }

    /**
     * The delete question, drawn above the list it is about (the question never takes the card over): the
     * sections under the row move up, and the page stays in the notebook.
     */
    @Composable
    fun question(onDelete: (String) -> OutlineEdits.Result) {
        val current = deleting ?: return
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                "Delete \"${current.row.title.ifBlank { "this bookmark" }}\"? The sections under it move up, and the page stays in the notebook.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (error != null) {
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = ::cancel) { Text("Cancel") }
                TextButton(onClick = { settle(onDelete(current.row.id)) }) { Text("Delete") }
            }
        }
    }

    /** A command's answer: a refusal stays on screen in the reader's words, a write closes the form. */
    private fun settle(result: OutlineEdits.Result) {
        if (result is OutlineEdits.Result.Refused) {
            error = refusalCopy(result.reason)
        } else {
            edit = null
            error = null
        }
    }
}

/** The sentence a refusal is shown as — the reason in the reader's words, never a class name. */
internal fun refusalCopy(reason: OutlineEdits.Refusal): String = when (reason) {
    OutlineEdits.Refusal.EMPTY_TITLE -> "A bookmark needs a title."
    OutlineEdits.Refusal.NOT_FOUND -> "That bookmark is gone."
    OutlineEdits.Refusal.NO_TARGET -> "This page cannot hold a bookmark."
}

/**
 * The title field a bookmark is named in: one line, **Save** only once there is something to save, and
 * **Cancel** leaving the tree exactly as it was. Enter saves, so a keyboard user never has to reach for the
 * button. A refusal is shown under the field instead of closing it, so the reader can fix the title.
 */
@Composable
internal fun ContentsTitleEditor(
    title: String,
    heading: String,
    error: String?,
    onTitle: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            label = { Text("Title") },
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onSave() }),
        )
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = onSave, enabled = title.isNotBlank()) { Text("Save") }
        }
    }
}

/** The **Add bookmark** row above the list: the door to naming a row for the page in view. */
@Composable
internal fun ContentsAddRow(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(onClick = onAdd, modifier = Modifier.semantics { role = Role.Button }) { Text("Add bookmark") }
    }
}
