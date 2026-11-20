package com.mobixournal.desktop.settings

import androidx.compose.runtime.Composable
import com.mobixournal.design.ProjectLinks

/**
 * **Feedback**: the window's share of the About area — the two doors to the project's issue tracker, and
 * the one sentence about what a report needs.
 *
 * The doors go to the repository's **issue forms** rather than to a mail address: a report lands where
 * anyone meeting the same problem can search it, and the form asks for the build and the platform in
 * fields instead of in prose. The addresses are `ProjectLinks`' (`mobix-core`), so the tablet's Feedback
 * page opens the same two forms.
 *
 * Every row is the window's **secondary command** — a door is an action, not a value
 * ([`docs/interface-contract.md`](../interface-contract.md)) — and the version a report needs is written
 * on the About page beside it, so the reader does not have to leave the panel to find it.
 */
@Composable
internal fun FeedbackSettings() {
    SettingsGroup("On GitHub") {
        SettingsCommand(
            label = "Report a problem",
            note = "Opens the bug report form: what you did, what happened, and the build in hand.",
            onClick = { openPage(ProjectLinks.newIssue(ProjectLinks.BUG_TEMPLATE)) },
        )
        SettingsCommand(
            label = "Suggest an idea",
            note = "Opens the feature request form: something you want to do that the window cannot do yet.",
            onClick = { openPage(ProjectLinks.newIssue(ProjectLinks.FEATURE_TEMPLATE)) },
        )
        SettingsCommand(
            label = "Open the project on GitHub",
            note = "Source code, release downloads, and every report already filed.",
            onClick = ::openProjectPage,
        )
    }
    SettingsGroup("Before you send it") {
        SettingsNote(
            "A report is easier to act on with the build it came from — the version is on the About page " +
                "beside this one. Say what you did and what happened instead: a problem nobody can repeat " +
                "is a problem nobody can fix.",
        )
    }
}
