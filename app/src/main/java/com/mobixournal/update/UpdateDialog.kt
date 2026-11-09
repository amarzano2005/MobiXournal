package com.mobixournal.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mobixournal.BuildConfig
import kotlin.math.roundToInt

/**
 * The tablet's **update available** popup: the newer version, the door to what changed, and the two
 * answers the reader has — [UpdatePhase.AVAILABLE] offers the install, and Remind me later keeps the offer
 * on the About page without asking again this launch (`docs/updates.md`).
 *
 * It is a Material dialog like every other question in the app (`docs/ui-modernization.md` owns the
 * material), and the copy says what is happening: a failing download names the failure instead of leaving
 * the button pressed and silent.
 */
@Composable
fun UpdateAvailableDialog() {
    if (!AppUpdates.dialog) return
    val release = AppUpdates.release ?: return
    val context = LocalContext.current
    val downloading = AppUpdates.phase == UpdatePhase.DOWNLOADING
    AlertDialog(
        onDismissRequest = { AppUpdates.remindLater() },
        title = { Text("Update available") },
        text = {
            Column {
                Text("MobiXournal ${release.version} is available. This build is ${BuildConfig.VERSION_NAME}.")
                val status = AppUpdates.message
                if (downloading) {
                    Text("Downloading ${(AppUpdates.progress * 100).roundToInt()}%")
                } else if (status != null) {
                    Text(status)
                }
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
