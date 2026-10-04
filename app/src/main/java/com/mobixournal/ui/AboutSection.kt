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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobixournal.BuildConfig

/** Where the About page sends people; kept in one place so a link is never spelled out twice. */
object AboutLinks {
    /** This app's own source repository (the owner's). */
    const val SOURCE = "https://github.com/amarzano2005/MobiXournal"
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

    Text("MobiXournal", style = MaterialTheme.typography.titleLarge)
    Text(
        "An unofficial, stylus-first Android reader and editor for Xournal++ (.xopp) documents.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )

    InfoRow("Version", BuildConfig.VERSION_NAME)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))

    Text("Credits", style = MaterialTheme.typography.bodyLarge)
    Text(
        "MobiXournal continues NeXopp — the Android .xopp editor written by Brian Monroe " +
            "(bamonroe). The document layer, the editor, the build tooling and the documentation " +
            "structure all come from that project, which is licensed under the GNU General Public " +
            "License, version 2 or later; this build renames, rebrands and extends it. NeXopp's " +
            "author holds the copyright in the portions inherited from there.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    LinkRow("NeXopp — github.com/bamonroe/NeXopp", AboutLinks.NEXOPP, open)
    Text(
        "NeXopp, by Brian Monroe (bamonroe) — the upstream project this app is built on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    Text(
        "This app is built on and for Xournal++, the open-source handwriting app for Linux and " +
            "the .xopp format it defines. The format handling, colour palette, shape recogniser " +
            "and highlighter behaviour are derived from or matched to the Xournal++ project, whose " +
            "authors hold the copyright in that work. Xournal++ is licensed under the GNU General " +
            "Public License, version 2 or later.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        "This is an independent, unofficial project: it is not affiliated with, endorsed by, or " +
            "maintained by the Xournal++ authors.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    LinkRow("Xournal++, the desktop app", AboutLinks.XOURNALPP, open)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))

    Text("Licence", style = MaterialTheme.typography.bodyLarge)
    Text(
        "MobiXournal is free software under the GNU General Public License, " +
            "version 2 or later — the same licence as the work it derives from. You may use, study, " +
            "share and modify it; if you distribute a modified version, it must stay free under the " +
            "same terms and ship its source. It comes with no warranty. The full text is in the " +
            "LICENSE file in the source.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    LinkRow("Read the GPL v2", AboutLinks.LICENSE, open)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))

    Text("Source", style = MaterialTheme.typography.bodyLarge)
    Text(
        "Developed in the open. Bug reports and patches are welcome.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    LinkRow("github.com/amarzano2005/MobiXournal", AboutLinks.SOURCE, open)
}

/** A read-only "label … value" line, for facts about the build. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** A tappable line that hands its URL to the system browser. */
@Composable
private fun LinkRow(label: String, url: String, open: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { open(url) }.padding(vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
