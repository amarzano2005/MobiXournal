package com.mobixournal.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobixournal.BuildConfig
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.rememberClosingArgument
import kotlin.math.roundToInt

/**
 * The tablet's **update available** popup: the newer version, the door to what changed, and the two
 * answers the reader has — [UpdatePhase.AVAILABLE] offers the install, and *Remind me later* keeps the
 * offer on the About page without asking again this launch (`docs/updates.md`).
 *
 * It is a **floating surface**, the app's one material for anything shown over the page
 * (`docs/interface-contract.md`): `FloatingDialog` gives it the glass fill, the family's rim and the
 * floating corner, and [DialogPresence] holds the window while the closing spring plays, so the card
 * arrives *and* leaves like every menu around it. It used to be a plain Material `AlertDialog` — an
 * opaque card with none of those — which is the one thing the contract names as never acceptable for a
 * surface that comes and goes. The window's copy of this card wears the same material (`glassSurface`),
 * and [ColorContractGuardTest] fails the build if this one goes back.
 *
 * The copy says what is happening: a failing download names the failure instead of leaving the button
 * pressed and silent.
 */
@Composable
fun UpdateAvailableDialog() {
    DialogPresence(AppUpdates.dialog) {
        // Read through the closing value: the flag clears first, and the card still has to draw the release
        // it was opened with for the ~130 ms it takes to leave.
        val release = rememberClosingArgument(AppUpdates.release) ?: return@DialogPresence
        UpdateCard(release)
    }
}

/** The card itself: the newer version, its state, and the two answers. */
@Composable
private fun UpdateCard(release: UpdateRelease) {
    val context = LocalContext.current
    val downloading = AppUpdates.phase == UpdatePhase.DOWNLOADING
    val status = AppUpdates.message
    FloatingDialog(
        onDismissRequest = { AppUpdates.remindLater() },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("MobiXournal ${release.version} is available. This build is ${BuildConfig.VERSION_NAME}.")
                if (downloading) {
                    Text("Downloading ${(AppUpdates.progress * 100).roundToInt()}%")
                } else if (!status.isNullOrBlank()) {
                    Text(status)
                }
                // The release's own page, read where it was written: the card never summarises the notes.
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.notesUrl)))
                        }
                    },
                ) {
                    Text("See what changed")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { AppUpdates.updateNow(context) }, enabled = !downloading) {
                Text(if (downloading) "Downloading…" else "Update now")
            }
        },
        dismissButton = {
            TextButton(onClick = { AppUpdates.remindLater() }) { Text("Remind me later") }
        },
    )
}
