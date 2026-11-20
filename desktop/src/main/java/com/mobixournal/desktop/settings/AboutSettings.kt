package com.mobixournal.desktop.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Text
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
        // The build's two actions side by side, with their answers below on the same label column:
        // checking for updates and copying diagnostics are sibling build commands.
        SettingsCommandRow(
            SettingsCommandAction(
                label = "Check for updates",
                note = "Asks GitHub whether a newer window is published.",
                onClick = { DesktopUpdates.offerOrCheck() },
            ),
            SettingsCommandAction(
                label = "Copy app details",
                note = "Version, Windows build and formats — the facts a report asks for.",
                onClick = { copied = copyAppDetailsToClipboard() },
            ),
        )
        val updateAnswer = DesktopUpdates.answerLine()
        AnimatedVisibility(
            visible = updateAnswer != null,
            enter = expandVertically(tween(140)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(100)) + fadeOut(tween(100)),
        ) {
            updateAnswer?.let { SettingsAnswer(it) }
        }
        AnimatedVisibility(
            visible = copied,
            enter = expandVertically(tween(140)) + fadeIn(tween(140)),
            exit = shrinkVertically(tween(100)) + fadeOut(tween(100)),
        ) {
            SettingsAnswer("Copied to the clipboard — paste it into your report.")
        }
    }
    SettingsGroup("Tell the project", summary = "Each command opens the project's own page in your browser.") {
        SettingsCommandRow(
            SettingsCommandAction(
                label = "Report a problem",
                note = "Opens the bug report form: what you did, what happened, and the build in hand.",
                onClick = { openPage(ProjectLinks.newIssue(ProjectLinks.BUG_TEMPLATE)) },
            ),
            SettingsCommandAction(
                label = "Suggest an idea",
                note = "Opens the feature request form: something you want to do that the window cannot do yet.",
                onClick = { openPage(ProjectLinks.newIssue(ProjectLinks.FEATURE_TEMPLATE)) },
            ),
            SettingsCommandAction(
                label = "Open the project on GitHub",
                note = "Source code, release downloads, and every report already filed.",
                onClick = { openProjectPage() },
            ),
        )
    }
    SettingsGroup("Credits & License") {
        SettingsRow(
            label = "Lineage",
            note = "Continues NeXopp by Brian Monroe (bamonroe). Original portions copyright Brian Monroe.",
        ) {
            Text("NeXopp", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        }
        SettingsRow(
            label = "Interoperability",
            note = "Reads and writes the .xopp format. Unofficial; not affiliated with or endorsed by Xournal++ authors.",
        ) {
            Text("Xournal++", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        }
        SettingsRow(
            label = "License",
            note = "Free software provided without warranty.",
        ) {
            Text("GNU GPL v2 or later", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        }
    }
}
