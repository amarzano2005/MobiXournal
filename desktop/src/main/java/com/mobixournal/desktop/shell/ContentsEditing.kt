package com.mobixournal.desktop.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ContentsRowLayout
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.glassCapsule
import com.mobixournal.render.OutlineEdits
import com.mobixournal.render.OutlineRow

/** What the panel is doing to the tree right now: naming a row (null for a new one) or asking about a removal. */
internal sealed interface ContentsEditState {

    /** Naming a new row ([row] null) for [page], or retitling [row]. */
    data class Naming(val row: OutlineRow?, val page: Int?) : ContentsEditState

    /** Asking whether to remove [row]. */
    data class Deleting(val row: OutlineRow) : ContentsEditState
}

/**
 * The panel's one edit at a time, so the list stays the list it arranges: the draft, the name being typed, and
 * the page a new bookmark captured when **Add bookmark** was pressed (a page turned while typing cannot move it).
 */
internal class ContentsEditHost {

    private var edit by mutableStateOf<ContentsEditState?>(null)
    private var typedTitle by mutableStateOf("")
    private var refusal by mutableStateOf<String?>(null)

    /** The edit in progress, or null when the list is only being read. */
    val draft: ContentsEditState? get() = edit

    /** The name being typed. */
    val title: String get() = typedTitle

    /** The refusal beside the draft, once a command has answered. */
    val error: String? get() = refusal

    /** The page the add draft was opened on. */
    val addPage: Int? get() = (edit as? ContentsEditState.Naming)?.page

    /** The row being asked about, or null when no delete question is up. */
    val deleting: ContentsEditState.Deleting? get() = edit as? ContentsEditState.Deleting

    /** Naming a new row for [page]. */
    fun adding(page: Int?) {
        typedTitle = ""
        refusal = null
        edit = ContentsEditState.Naming(row = null, page = page)
    }

    /** Retitling [row]: the field opens on the title it already has. */
    fun renaming(row: OutlineRow) {
        typedTitle = row.title
        refusal = null
        edit = ContentsEditState.Naming(row = row, page = row.page)
    }

    /** Asking about [row] before it goes. */
    fun deleting(row: OutlineRow) {
        typedTitle = row.title
        refusal = null
        edit = ContentsEditState.Deleting(row)
    }

    /** The name as it is now typed. */
    fun typed(value: String) {
        typedTitle = value
    }

    /** Closing whatever the panel is doing, leaving the tree exactly as it was. */
    fun cancel() {
        edit = null
        refusal = null
    }

    /** A command's answer: a refusal stays beside the draft in the reader's words, a write closes it. */
    fun submit(result: OutlineEdits.Result) {
        if (result is OutlineEdits.Result.Refused) refusal = contentsRefusal(result.reason) else cancel()
    }
}

/** A refusal in the reader's words — never a class or an enum name on screen. */
private fun contentsRefusal(reason: OutlineEdits.Refusal): String = when (reason) {
    OutlineEdits.Refusal.EMPTY_TITLE -> "A bookmark needs a title."
    OutlineEdits.Refusal.NOT_FOUND -> "That bookmark is gone."
    OutlineEdits.Refusal.NO_TARGET -> "This page cannot hold a bookmark."
}

/**
 * The one-line field a bookmark is named in — inside the row being retitled, or in the draft the header's
 * command opens. **Enter** saves, **Escape** abandons, a blank name cannot be saved, and the refusal is printed
 * under the field. While it has focus [WindowKeys.focusControl] tells the shell the keyboard is typing, so a
 * name may contain a tool's letter without the window reading it as a shortcut.
 */
@Composable
internal fun ContentsTitleField(
    value: String,
    onValue: (String) -> Unit,
    error: String?,
    placeholder: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = remember { Any() }
    DisposableEffect(owner) { onDispose { WindowKeys.focusControl(owner, false) } }
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = DesktopTheme.label.copy(color = DesktopTheme.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(DesktopTheme.accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { WindowKeys.focusControl(owner, it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (value.isNotBlank()) onSave()
                            true
                        }
                        Key.Escape -> {
                            onCancel()
                            true
                        }
                        else -> false
                    }
                }
                .glassCapsule(cornerRadius = 8.dp)
                .border(1.dp, if (error != null) DesktopTheme.danger else DesktopTheme.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
                    field()
                }
            },
        )
        if (error != null) Text(error, color = DesktopTheme.danger, style = DesktopTheme.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ContentsRowAction("Cancel", "", onCancel)
            if (value.isNotBlank()) ContentsRowAction("Save", "", onSave)
        }
    }
}

/**
 * The draft **Add bookmark** opens: a compact row above its neighbours, holding the name being typed and the
 * page it captured when the command was pressed.
 */
@Composable
internal fun ContentsAddDraft(
    page: Int?,
    pageColumnDp: Float,
    title: String,
    error: String?,
    onTitle: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width(ContentsRowLayout.BRANCH_DP.dp))
        ContentsTitleField(
            value = title,
            onValue = onTitle,
            error = error,
            placeholder = "New bookmark",
            onSave = onSave,
            onCancel = onCancel,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(pageColumnDp.dp), contentAlignment = Alignment.TopEnd) {
            if (page != null) {
                Text(text = "${page + 1}", color = DesktopTheme.inkMuted, style = DesktopTheme.numeric)
            }
        }
    }
}

/** The delete question: asked above the list (which keeps its rows), and says the page stays in the notebook. */
@Composable
internal fun ContentsDeleteQuestion(
    row: OutlineRow,
    error: String?,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            "Delete \"${row.title.ifBlank { "this bookmark" }}\"? The sections under it move up, and the page stays in the notebook.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.label,
        )
        if (error != null) Text(error, color = DesktopTheme.danger, style = DesktopTheme.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ContentsRowAction("Cancel", row.title, onCancel)
            ContentsRowAction("Delete", row.title, onDelete)
        }
    }
}

