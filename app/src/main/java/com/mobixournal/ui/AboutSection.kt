package com.mobixournal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mobixournal.BuildConfig
import com.mobixournal.R
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.ProjectLinks
import com.mobixournal.update.AppUpdates
import com.mobixournal.update.UpdatePhase
import kotlinx.coroutines.delay

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
 * About: **one page, not two.** The build in hand, the two doors to the project, and the credits owed —
 * an identity card rather than a list of settings.
 *
 * The page is the whole area: there is no *About* row opening an *About* page, and no separate Feedback
 * entry to find, because the two questions a reader arrives with here — *which build is this* and *where
 * do I say something about it* — are asked at the same moment. Its shape is deliberately not the other
 * pages': a hero that names the app and the version and carries the update check beside a compact GitHub
 * door, a pair of action cards aimed at GitHub's issue forms with *Copy app details* under them, and one
 * card holding the credits and the licence behind two disclosures.
 *
 * The doors go to the repository's **issue forms** (`ProjectLinks`, `.github/ISSUE_TEMPLATE`) rather than
 * to a blank issue box: the form asks for the build and the platform in fields, and the answer lands
 * where anyone meeting the same problem can search it. Nothing is sent from here — a card opens the
 * browser and the reader writes the report signed in as themselves.
 */
@Composable
fun AboutSection() {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // The version line **is** the update check: the number is the one row the command answers about, so a
    // tap on the hero asks GitHub and the sentence under it is the state — there is no separate "Updates"
    // group to find. "Already the latest" also raises a notification at the bottom, because an answer
    // printed only inside the card is an answer the reader may never see (`docs/updates.md`).
    var askedHere by remember { mutableStateOf(false) }
    LaunchedEffect(AppUpdates.phase) {
        if (askedHere && AppUpdates.phase == UpdatePhase.UP_TO_DATE) {
            Toast.makeText(context, latestSentence(), Toast.LENGTH_SHORT).show()
        }
        if (askedHere && AppUpdates.phase != UpdatePhase.CHECKING) askedHere = false
    }

    // The clipboard answer stays on the row for a moment: a toast over a settings page is easy to miss, and
    // the reader's next move is to paste these three lines somewhere.
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    BuildIdentity(
        version = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
        status = AppUpdates.statusLine(),
        platform = platformLine(),
        checking = AppUpdates.phase == UpdatePhase.CHECKING,
        onCheck = {
            askedHere = true
            AppUpdates.offerOrCheck(context)
        },
        onOpenProject = { open(AboutLinks.SOURCE) },
    )

    Spacer(Modifier.height(SettingsGroupGap))
    SettingsGroupTitle("Tell the project")
    ProjectDoors(open)
    // The facts a report asks for sit **under the doors they are pasted into**, not in a card of their
    // own: the reader copies them to write one of the two reports above, and the button says what it did
    // on the press itself — `Copied` for a moment, then back to the command.
    OutlinedButton(
        onClick = { copied = copyAppDetails(context) },
        modifier = Modifier.settingsActionPill(top = 8.dp, bottom = 0.dp),
    ) {
        Icon(ChromeGlyph.COPY.imageVector(), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (copied) "Copied" else "Copy app details")
    }

    Spacer(Modifier.height(SettingsGroupGap))
    SettingsGroup("Credits & License") {
        // The credits and the licence are long, and neither is what a reader came for: a disclosure keeps
        // them one tap away instead of spending half the page on text that is read once.
        DisclosureRow("Credits") {
            SettingsNote(
                "Continues NeXopp by Brian Monroe (bamonroe), licensed under GNU GPL v2 or later. " +
                    "Original portions copyright Brian Monroe.",
            )
            LinkRow("NeXopp on GitHub") { open(AboutLinks.NEXOPP) }
            SettingsNote(
                "Interoperable with Xournal++, which defines the .xopp format. " +
                    "Unofficial; not affiliated with or endorsed by Xournal++ authors.",
            )
            LinkRow("Xournal++ desktop app") { open(AboutLinks.XOURNALPP) }
        }
        SettingsDivider()
        DisclosureRow("License") {
            SettingsNote(
                "Free software licensed under GNU General Public License v2 or later. " +
                    "Provided without warranty.",
            )
            LinkRow("Read the GPL v2 License") { open(AboutLinks.LICENSE) }
        }
    }
}

/** The sentence the bottom notification repeats when the check finds nothing newer. */
private fun latestSentence(): String =
    "MobiXournal ${BuildConfig.VERSION_NAME} is the latest version."

/** The device and release a report is asked about, on one line under the build number. */
private fun platformLine(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    return "$maker ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
}

/** The three facts a report asks for, one per line — what *Copy app details* puts on the clipboard. */
private fun appDetails(): String = buildString {
    append("MobiXournal ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n")
    append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
    append("${Build.MANUFACTURER} ${Build.MODEL}")
}

/**
 * Hand [appDetails] to the clipboard and say whether it worked.
 *
 * A device without a clipboard service is told so rather than failing quietly: the row is a command, and a
 * command that does nothing when pressed reads as a broken page.
 */
private fun copyAppDetails(context: Context): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "This device has no clipboard to copy to.", Toast.LENGTH_SHORT).show()
        return false
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("MobiXournal app details", appDetails()))
    Toast.makeText(context, "App details copied.", Toast.LENGTH_SHORT).show()
    return true
}

/**
 * The two **doors**, side by side: the page's main business after the hero.
 *
 * Cards rather than rows because on this page the doors are the point, so they are the largest targets
 * after the hero; the shared [IntrinsicSize.Min] height keeps the pair reading as one choice instead of a
 * list whose second item is shorter than the first.
 */
@Composable
private fun ProjectDoors(open: (String) -> Unit) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DoorCard(
            icon = ChromeGlyph.DESCRIPTION.imageVector(),
            title = "Report a problem",
            note = "Opens the bug form: what you did, what happened, and the build in hand.",
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { open(ProjectLinks.newIssue(ProjectLinks.BUG_TEMPLATE)) }
        DoorCard(
            icon = ChromeGlyph.ADD.imageVector(),
            title = "Suggest an idea",
            note = "Opens the feature form: something you want to do that the app cannot do yet.",
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { open(ProjectLinks.newIssue(ProjectLinks.FEATURE_TEMPLATE)) }
    }
}

/**
 * The page's **hero**: the app's own mark, its name, the build in hand, the machine it is on — and the
 * **Check for updates** button, in the same card as the number it asks about.
 *
 * The card used to be the command, with the build number as its hit area. That was invisible: a reader had
 * to guess the number was tappable, and a tap meant for reading started a network check. The sentence under
 * the version still reports the state ([com.mobixournal.update.AppUpdates.statusLine]), the button says what
 * the sentence is about, and while a check runs the button is disabled and reads *Checking…* rather than
 * looking pressable and doing nothing.
 */
@Composable
private fun BuildIdentity(
    version: String,
    status: String,
    platform: String,
    checking: Boolean,
    onCheck: () -> Unit,
    onOpenProject: () -> Unit,
) {
    val reach = LocalSettingsCardInset.current
    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = settingsCellInset(reach))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("MobiXournal", style = MaterialTheme.typography.titleMedium)
                    Text(
                        version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        platform,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.settingsActionPill(top = 12.dp, bottom = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCheck,
                    enabled = !checking,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(if (checking) "Checking…" else "Check for updates")
                }
                // The **compact door out**, beside the check its releases answer: one glyph, one tap to the
                // repository (its source, its downloads and its issue list), with the name in the icon's
                // own label so a screen reader still says what the button is.
                OutlinedButton(
                    onClick = onOpenProject,
                    modifier = Modifier.size(width = 56.dp, height = 48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        ChromeGlyph.GITHUB.imageVector(),
                        contentDescription = "Open the project on GitHub",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * One **door** on the page: a glyph, its name and one sentence saying where it goes.
 *
 * A card rather than a row because the doors are this page's main business; the glyph is the shared
 * chrome catalogue's, so the picture is the same on the window's page. The **card itself** is the control
 * ([SettingsCard]'s `onClick`), not a row inside it: a reader aims at the whole shape, and the card is the
 * one node whose clip is its own corner — the two doors are stretched to a shared height, so a row inside
 * them could light the sides but never the bottom.
 */
@Composable
private fun DoorCard(
    icon: ImageVector,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val reach = LocalSettingsCardInset.current
    SettingsCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = settingsCellInset(reach)),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * A row that **opens in place**: a name and a chevron that turns when the content under it is shown.
 *
 * The content is the caller's, drawn on the card's own column so notes and links keep the insets they wear
 * elsewhere; the row itself is full-bleed, like every other cell, so its press reaches the card's edge.
 */
@Composable
private fun DisclosureRow(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reach = LocalSettingsCardInset.current
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fullBleedRow(reach)
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = reach, vertical = settingsCellInset(reach)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * A tappable line, with [note] as its own subtitle: the shape the outward links wear, so one card's rows
 * read as one list.
 *
 * The note is inside the row rather than a [SettingsNote] after it: a separate block carries a cell's air
 * above and below, so the sentence that explains the row sat a group's gap away from it and read as
 * belonging to whatever came next.
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
