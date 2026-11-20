package com.mobixournal.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ProjectLinks

/**
 * **About**: what window this is, the check that says whether it is current, the doors to the project, and
 * the credits it owes — **one page**, not two.
 *
 * It used to be About beside a Feedback page, which made a reader choose between *what build do I have* and
 * *where do I say something about it* when they are the same question. Now the page is grouped by what a
 * command is about: the **build** (its version, the check, the details a report asks for), then the
 * **project** (the two issue forms and the repository), then the credits — the tablet's three steps, minus
 * its hero card (`AboutSection.kt`).
 *
 * Every command **answers where it stands**: the check's result and the copy's confirmation are lines under
 * their own button, not sentences only a hover can read (`docs/desktop-settings.md` owns the grammar, and
 * `docs/updates.md` the update channel).
 *
 * The doors go to **issue forms** (`.github/ISSUE_TEMPLATE`, addressed through `ProjectLinks`) rather than
 * to a blank issue box: the form asks for the build and the platform in fields, and the report lands where
 * anyone meeting the same problem can search it. Nothing is sent from here — a command opens the machine's
 * browser and the reader writes the report signed in as themselves.
 */
@Composable
internal fun AboutSettings() {
    // The copy's confirmation is a line of the page, like the update check's answer: a reader who just
    // copied the build needs to see that it happened, and the panel has no toast of its own on this page.
    var copied by remember { mutableStateOf(false) }
    SettingsGroup("MobiXournal Desktop") {
        SettingsReadout("Version", DESKTOP_VERSION)
        SettingsReadout("Reads and writes", ".xopp · .mobix")
        // The check and its answer, in the same place: the note is the panel's static help, and the line
        // under the row is what the last check found — the version in hand, the newer one, or the failure.
        SettingsCommand(
            label = "Check for updates",
            note = "Asks GitHub whether a newer window is published.",
            spacing = 6.dp,
            onClick = { DesktopUpdates.offerOrCheck() },
        )
        DesktopUpdates.answerLine()?.let { answer -> SettingsAnswer(answer) }
        // The build's own facts, beside its version: this command is about the window, not about the
        // project, and its confirmation is a line rather than a note a reader has to hover to find.
        SettingsCommand(
            label = "Copy app details",
            note = "Version, Windows build and formats — the facts a report asks for.",
            spacing = 6.dp,
            onClick = { copied = copyAppDetailsToClipboard() },
        )
        if (copied) SettingsAnswer("Copied to the clipboard — paste it into your report.")
    }
    SettingsGroup("Tell the project", summary = "Each command opens the project's own page in your browser.") {
        SettingsCommand(
            label = "Report a problem",
            note = "Opens the bug report form: what you did, what happened, and the build in hand.",
            spacing = 6.dp,
        ) { openPage(ProjectLinks.newIssue(ProjectLinks.BUG_TEMPLATE)) }
        SettingsCommand(
            label = "Suggest an idea",
            note = "Opens the feature request form: something you want to do that the window cannot do yet.",
            spacing = 6.dp,
        ) { openPage(ProjectLinks.newIssue(ProjectLinks.FEATURE_TEMPLATE)) }
        SettingsCommand(
            label = "Open the project on GitHub",
            note = "Source code, release downloads, and every report already filed.",
            spacing = 6.dp,
        ) { openProjectPage() }
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
