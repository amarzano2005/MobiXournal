package com.mobixournal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One selectable option row in [SaveAsDialog]/[ImportPdfDialog]: a radio plus a title and explanation.
 *
 * It is a file of its own because two dialogs draw it and neither owns it: the chooser that names a file's
 * format and the one that asks which pages of a PDF to take are the same question — *which of these?* — and
 * one row drawn twice is how two dialogs start disagreeing about what a choice looks like.
 *
 * The row is a **radio in the semantics tree** (`selectable` with [Role.RadioButton], so a screen reader
 * announces "radio button, selected", and `selectableGroup` on the caller's container adds "1 of 2"), and
 * it draws its **focus** ring from the theme's primary colour, because a choice reached with Tab or a D-pad
 * that shows nothing is a choice the reader cannot see they are on.
 */
@Composable
fun FormatOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // The **whole row** is the target, not only the radio: a title and an explanation are what a user
    // reads, and a choice that only answers to a 48dp circle beside them is a choice that gets missed
    // ("Save As" never offers .mobix because the tap landed on the words). `selectable` also gives the
    // row the radio's role in the semantics tree, so a screen reader announces it as the option it is.
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .onFocusChanged { focused = it.isFocused }
            // A hardware keyboard's Space takes the answer. Android's key dispatch never reached the
            // built-in handler for it here (Enter did, Space did not — measured on the emulator with a
            // keyboard and the IME disabled), so the row answers both keys itself, and TalkBack's own
            // double-tap needs nothing extra.
            .onKeyEvent { event ->
                val takes = event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Spacebar || event.key == Key.Enter || event.key == Key.NumPadEnter)
                if (takes && enabled) onClick()
                takes
            }
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            // One spoken sentence for the whole answer. A screen reader walked the title and the note as
            // two nodes of their own and announced the radio between them, so the choice was read as
            // "radio button" with its meaning in a neighbour — the label belongs on the node that is the
            // choice (the dump of the accessibility tree is what showed it).
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
            }
            // After the clickable, so the padding the row adds is part of what can be tapped.
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Inert on purpose: the row above owns the click (and reports the selection), so the button
        // must not be a second, smaller target competing with it.
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        // A disabled option stays visible (so the choice is explained) but reads as unavailable.
        val alpha = if (enabled) 1f else 0.38f
        Column(modifier = Modifier.alpha(alpha)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
