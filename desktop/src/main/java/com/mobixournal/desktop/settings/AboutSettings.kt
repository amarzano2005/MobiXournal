package com.mobixournal.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mobixournal.design.ProjectLinks

/**
 * **About**: what window this is, the check that says whether it is current, the doors to the project, and
 * the credits it owes — **one page**, not two.
 *
 * It used to be About beside a Feedback page, which made a reader choose between *what build do I have* and
 * *where do I say something about it* when they are the same question. Now the page carries the version and
 * the copy row first, then the two doors to the repository's issue forms, then the credits: the tablet's
 * page has the same three steps, minus its hero card (`AboutSection.kt`).
 *
 * The doors go to **issue forms** (`.github/ISSUE_TEMPLATE`, addressed through `ProjectLinks`) rather than
 * to a blank issue box: the form asks for the build and the platform in fields, and the report lands where
 * anyone meeting the same problem can search it. Nothing is sent from here — a command opens the machine's
 * browser and the reader writes the report signed in as themselves.
 */
@Composable
internal fun AboutSettings() {
    // The copy's answer stays on the row: a reader who just copied the build needs to see that it happened,
    // and the panel has no toast of its own on this page.
    var copied by remember { mutableStateOf(false) }
    SettingsGroup("MobiXournal Desktop") {
        SettingsReadout("Version", DESKTOP_VERSION)
        SettingsReadout("Reads and writes", ".xopp · .mobix")
        // The check and its answer, in the same place: the note is the panel's static help, and the line
        // under the row is what the last check found — the version in hand, the newer one, or the failure.
        // Nothing floats over the page for this (`docs/updates.md` owns the channel and both installers).
        SettingsCommand(
            label = "Check for updates",
            note = "Asks GitHub whether a newer window is published.",
            onClick = { DesktopUpdates.offerOrCheck() },
        )
        DesktopUpdates.answerLine()?.let { answer -> SettingsNote(answer) }
    }
    SettingsGroup("Tell the project") {
        SettingsCommand(
            label = "Report a problem",
            note = "Opens the bug report form: what you did, what happened, and the build in hand.",
        ) { openPage(ProjectLinks.newIssue(ProjectLinks.BUG_TEMPLATE)) }
        SettingsCommand(
            label = "Suggest an idea",
            note = "Opens the feature request form: something you want to do that the window cannot do yet.",
        ) { openPage(ProjectLinks.newIssue(ProjectLinks.FEATURE_TEMPLATE)) }
        // The facts a report asks for belong **under the doors they are pasted into**, as on the tablet.
        SettingsCommand(
            label = "Copy app details",
            note = if (copied) {
                "Copied to the clipboard — paste it into your report."
            } else {
                "Version, Windows build and formats — the facts a report asks for."
            },
            onClick = { copied = copyAppDetailsToClipboard() },
        )
        SettingsCommand(
            label = "Open the project on GitHub",
            note = "Source code, release downloads, and every report already filed.",
            onClick = ::openProjectPage,
        )
    }
    SettingsGroup("Credits & License") {
        SettingsNote(
            "Continues NeXopp by Brian Monroe (bamonroe), licensed under GNU GPL v2 or later. " +
                "Original portions copyright Brian Monroe.",
        )
        SettingsNote(
            "Interoperable with Xournal++, which defines the .xopp format. " +
                "Unofficial; not affiliated with or endorsed by Xournal++ authors.",
        )
        SettingsNote("Free software licensed under GNU General Public License v2 or later. Provided without warranty.")
    }
}
