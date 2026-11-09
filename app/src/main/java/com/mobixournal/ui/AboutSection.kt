package com.mobixournal.ui

import android.content.Intent
import android.net.Uri
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
 * About: which build this is (the version name), who made it, the licence it ships under, and
 * the credits owed — both to NeXopp, the codebase this continues, and to Xournal++, the format's
 * author it is built for. Every row that points outward opens the link in the system browser.
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
        // The **door out**, where a row naming the *other* shell's version used to stand: a reader who wants
        // the Windows build wants its releases, and the repository is where both shells' work lives.
        LinkRow(
            "MobiXournal on GitHub",
            AboutLinks.SOURCE,
            open,
            note = "Source code, release downloads, and issue tracker.",
        )
    }

    SettingsGroup("Credits") {
        SettingsNote(
            "Continues NeXopp by Brian Monroe (bamonroe), licensed under GNU GPL v2 or later. " +
                "Original portions copyright Brian Monroe.",
        )
        LinkRow("NeXopp on GitHub", AboutLinks.NEXOPP, open)
        SettingsDivider()
        SettingsNote(
            "Interoperable with Xournal++, which defines the .xopp format. " +
                "Unofficial; not affiliated with or endorsed by Xournal++ authors.",
        )
        LinkRow("Xournal++ desktop app", AboutLinks.XOURNALPP, open)
    }

    SettingsGroup("License") {
        SettingsNote("Free software licensed under GNU General Public License v2 or later. Provided without warranty.")
        LinkRow("Read the GPL v2 License", AboutLinks.LICENSE, open)
    }

}

/** The sentence the bottom notification repeats when the check finds nothing newer. */
private fun latestSentence(): String =
    "MobiXournal ${BuildConfig.VERSION_NAME} is the latest version."

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
 * A tappable line that hands its URL to the system browser, with [note] as its own subtitle.
 *
 * The note is inside the row rather than a [SettingsNote] after it: a separate block carries a cell's
 * air above and below, so the sentence that explains the link sat a group's gap away from it and read
 * as belonging to whatever came next.
 */
@Composable
private fun LinkRow(label: String, url: String, open: (String) -> Unit, note: String? = null) {
    val reach = LocalSettingsCardInset.current
    Column(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable { open(url) }
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
