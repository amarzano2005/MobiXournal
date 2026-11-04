package com.mobixournal.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * One selectable option row in [SaveAsDialog]/[ImportPdfDialog]: a radio plus a title and explanation.
 *
 * It is a file of its own because two dialogs draw it and neither owns it: the chooser that names a file's
 * format and the one that asks which pages of a PDF to take are the same question — *which of these?* — and
 * one row drawn twice is how two dialogs start disagreeing about what a choice looks like.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
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
