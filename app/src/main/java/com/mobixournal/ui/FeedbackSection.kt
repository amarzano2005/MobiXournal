package com.mobixournal.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mobixournal.design.ProjectLinks

/**
 * Feedback: the two doors to the project's issue tracker, and the one sentence about what a report needs.
 *
 * The doors go to the repository's **issue forms** rather than to a mail address, because a report that
 * lands in the tracker can be read by anyone who meets the same problem, is searchable before it is
 * written, and asks for the build and the platform in fields instead of in prose. Both addresses come from
 * [ProjectLinks], the one home of the project's outward address, so the window's page opens the same two
 * forms.
 *
 * The note under them is the page's other half: a report is acted on because of the steps and the build,
 * and the build is already one tap away on **About** (*Copy app details*). Nothing here sends anything by
 * itself — a row opens the browser, and the reader sends the report from GitHub, signed in as themselves.
 */
@Composable
fun FeedbackSection() {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    SettingsGroup("On GitHub") {
        LinkRow(
            "Report a problem",
            note = "Opens the bug report form: what you did, what happened, and the build in hand.",
        ) { open(ProjectLinks.newIssue(ProjectLinks.BUG_TEMPLATE)) }
        SettingsDivider()
        LinkRow(
            "Suggest an idea",
            note = "Opens the feature request form: something you want to do that the app cannot do yet.",
        ) { open(ProjectLinks.newIssue(ProjectLinks.FEATURE_TEMPLATE)) }
        SettingsDivider()
        LinkRow(
            "Open the project on GitHub",
            note = "Source code, release downloads, and every report already filed.",
        ) { open(AboutLinks.SOURCE) }
    }

    SettingsGroup("Before you send it") {
        SettingsNote(
            "A report is easier to act on with the build it came from: Copy app details on About puts " +
                "the version, the Android release and the device on the clipboard in one tap. Say what you " +
                "did and what happened instead — a problem nobody can repeat is a problem nobody can fix.",
        )
    }
}
