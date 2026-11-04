package com.mobixournal.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobixournal.BuildConfig
import com.mobixournal.design.ProjectLinks

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

    SettingsGroup("MobiXournal") {
        InfoRow("Version", BuildConfig.VERSION_NAME)
        // The **door out**, where a row naming the *other* shell's version used to stand: a reader who wants
        // the Windows build wants its releases, and the repository is where both shells' work lives.
        LinkRow("MobiXournal on GitHub", AboutLinks.SOURCE, open)
        SettingsNote("Developed in the open; bug reports and patches are welcome.")
    }

    SettingsGroup("Credits") {
        SettingsNote(
            "MobiXournal continues NeXopp, Brian Monroe's (bamonroe) Android .xopp editor, licensed " +
                "under the GNU GPL v2 or later. Its author holds the copyright in the parts inherited.",
        )
        LinkRow("NeXopp (GitHub)", AboutLinks.NEXOPP, open)
        SettingsDivider()
        SettingsNote(
            "Built on and for Xournal++, which defines the .xopp format; the format handling, palette, " +
                "shape recogniser and highlighter follow that project, whose authors hold the copyright.",
        )
        SettingsNote("Unofficial: not affiliated with or endorsed by the Xournal++ authors.")
        LinkRow("Xournal++ desktop app", AboutLinks.XOURNALPP, open)
    }

    SettingsGroup("Licence") {
        SettingsNote(
            "Free software under the GNU GPL, version 2 or later. Use, study, share and modify it; a " +
                "modified version you distribute stays free under the same terms and ships its source. " +
                "No warranty.",
        )
        LinkRow("Read the GPL v2 Licence", AboutLinks.LICENSE, open)
    }

}

/** A read-only "label … value" line, for facts about the build. */
@Composable
private fun InfoRow(label: String, value: String) {
    // A **block**, not a row: the card keeps no vertical air of its own (so its rows can wear its
    // corners), so a line that opens a group carries the air that keeps it off the top edge.
    Row(modifier = Modifier.fillMaxWidth().settingsBlockInset()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** A tappable line that hands its URL to the system browser. */
@Composable
private fun LinkRow(label: String, url: String, open: (String) -> Unit) {
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
    }
}
