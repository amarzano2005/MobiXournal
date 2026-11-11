package com.mobixournal.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A **save in progress**, said on the sheet rather than instead of it: one line in the accent, in the corner,
 * no scrim and no second surface — the tablet's version of the window's own notice.
 *
 * It replaces a full-screen transfer overlay **for saves only**. The overlay is right for a document moving
 * to or from storage on a slow share, where the canvas must not be edited mid-transfer; a save writes an
 * immutable snapshot of the pages (`MainActivity.saveDocument` hands `io.encode` the document it captured),
 * so the page can stay in view and the reader is told what is happening without being taken away from the
 * notebook. The label is the shell's own ("Saving notes.mobix."), because on a tablet the document's name is
 * the one thing a reader can still be unsure about.
 */
@Composable
internal fun BoxScope.SavingNotice(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
    )
}
