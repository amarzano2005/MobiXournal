package com.mobixournal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobixournal.BuildConfig
import com.mobixournal.design.ProjectLinks
import com.mobixournal.update.AppUpdates
import com.mobixournal.update.UpdatePhase

/** Where the About page sends people; the repository's address has one home, `ProjectLinks` (mobix-core). */
object AboutLinks {
    /** This app's own source repository (the owner's) — read from the shared home both shells use. */
    const val SOURCE = ProjectLinks.GITHUB
    /** The licence this app ships under — GPL-2.0-or-later, inherited from Xournal++. */
    const val LICENSE = "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
    /** The desktop project this app is built for and based on. */
    const val XOURNALPP = "https://github.com/xournalpp/xournalpp"
    /** The Android `.xopp` editor this codebase continues. */
    const val NEXOPP = "https://github.com/bamonroe/NeXopp"
}

/**
 * About: which build this is (the version name), what it owes, and the build details a report needs.
 *
 * It is a page of its own inside the **About** area now, beside **Feedback**: the two questions a reader
 * arrives with here are *which build am I on* and *where do I say something about it*, and the outward
 * doors — the bug form, the feature form, the repository — belong to the second one.
 *
 * The version row **is** the update check (the number is the one row the command answers about), and
 * *Copy app details* puts the version, the Android release and the device on the clipboard: that trio is
 * what a report is asked for, and retyping it from a settings page is the step a bug report loses.
 */
@Composable
fun AboutSection() {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // The version line **is** the update check: the number is the one row the command answers about, so a
    // tap on it asks GitHub and the sentence under it is the state — there is no separate "Updates" group
    // to find. "Already the latest" also raises a notification at the bottom, because an answer printed
    // only inside the card is an answer the reader may never see (`docs/updates.md`).
    var askedHere by remember { mutableStateOf(false) }
    LaunchedEffect(AppUpdates.phase) {
        if (askedHere && AppUpdates.phase == UpdatePhase.UP_TO_DATE) {
            Toast.makeText(context, latestSentence(), Toast.LENGTH_SHORT).show()
        }
        if (askedHere && AppUpdates.phase != UpdatePhase.CHECKING) askedHere = false
    }
    SettingsGroup("MobiXournal") {
        VersionRow(BuildConfig.VERSION_NAME, AppUpdates.statusLine()) {
            askedHere = true
            AppUpdates.offerOrCheck(context)
        }
        SettingsDivider()
        // The build's three facts, one tap to the clipboard: what a report needs (`FeedbackSection`).
        LinkRow(
            "Copy app details",
            note = "Version, Android release and device — paste them into a report.",
        ) { copyAppDetails(context) }
    }

    SettingsGroup("Credits") {
        SettingsNote(
            "Continues NeXopp by Brian Monroe (bamonroe), licensed under GNU GPL v2 or later. " +
                "Original portions copyright Brian Monroe.",
        )
        LinkRow("NeXopp on GitHub") { open(AboutLinks.NEXOPP) }
        SettingsDivider()
        SettingsNote(
            "Interoperable with Xournal++, which defines the .xopp format. " +
                "Unofficial; not affiliated with or endorsed by Xournal++ authors.",
        )
        LinkRow("Xournal++ desktop app") { open(AboutLinks.XOURNALPP) }
    }

    SettingsGroup("License") {
        SettingsNote("Free software licensed under GNU General Public License v2 or later. Provided without warranty.")
        LinkRow("Read the GPL v2 License") { open(AboutLinks.LICENSE) }
    }

}

/** The sentence the bottom notification repeats when the check finds nothing newer. */
private fun latestSentence(): String =
    "MobiXournal ${BuildConfig.VERSION_NAME} is the latest version."

/** The three facts a report asks for, one per line — what *Copy app details* puts on the clipboard. */
private fun appDetails(): String = buildString {
    append("MobiXournal ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n")
    append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
    append("${Build.MANUFACTURER} ${Build.MODEL}")
}

/**
 * Hand [appDetails] to the clipboard, with a word about where they go.
 *
 * A device without a clipboard service is told so rather than failing quietly: the row is a command, and
 * a command that does nothing when pressed reads as a broken page.
 */
private fun copyAppDetails(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "This device has no clipboard to copy to.", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("MobiXournal app details", appDetails()))
    Toast.makeText(context, "App details copied.", Toast.LENGTH_SHORT).show()
}

/**
 * The build's number, and **the button** that asks GitHub whether it is still current.
 *
 * The value is drawn in the accent because the whole line is the command: the reader asked where *Check for
 * updates* had gone, and the honest place for it is the row that names the version it answers about. The
 * sentence underneath is the state ([com.mobixournal.update.AppUpdates.statusLine]) — "Tap to check for
 * updates", then "Checking…", then the answer, which stays readable after a tap.
 */
@Composable
private fun VersionRow(version: String, status: String, onClick: () -> Unit) {
    val reach = LocalSettingsCardInset.current
    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = reach, vertical = settingsCellInset(reach)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Version", style = MaterialTheme.typography.bodyLarge)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            version,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A tappable line, with [note] as its own subtitle: the shape both outward links (**About**) and the
 * clipboard command wear, so one page's rows read as one list.
 *
 * The note is inside the row rather than a [SettingsNote] after it: a separate block carries a cell's
 * air above and below, so the sentence that explains the row sat a group's gap away from it and read
 * as belonging to whatever came next.
 */
@Composable
internal fun LinkRow(label: String, note: String? = null, onClick: () -> Unit) {
    val reach = LocalSettingsCardInset.current
    Column(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = reach, vertical = settingsCellInset(reach)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
