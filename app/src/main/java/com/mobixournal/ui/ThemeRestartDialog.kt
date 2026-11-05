package com.mobixournal.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mobixournal.ui.theme.FloatingDialog

/**
 * The notice a theme change raises: the **startup screen and the app icon** are read at launch, so
 * only a restart wears them. The theme itself is already live — the colour scheme, the chrome and
 * the bars repaint the moment the row is picked — so this asks for the restart that repaints the
 * launch, not for permission to save anything.
 *
 * *Restart now* closes the window and comes back through the launcher entry
 * (`MobiXournalApp.restartForTheme`); *Later* leaves the current session alone, and the chosen theme
 * is what the next launch comes up in, since it was written to the store before this notice opened.
 */
@Composable
fun ThemeRestartDialog(
    onRestartNow: () -> Unit,
    onRestartLater: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onRestartLater,
        title = { Text("Restart to finish the theme change") },
        text = {
            Text(
                "The theme is saved and already applied here. The startup screen and the app icon " +
                    "take it when the app starts, so they need a restart.",
            )
        },
        confirmButton = { TextButton(onClick = onRestartNow) { Text("Restart now") } },
        dismissButton = { TextButton(onClick = onRestartLater) { Text("Later") } },
    )
}
