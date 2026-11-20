package com.mobixournal.design

/**
 * The project's **outward address**, in one place, for both shells.
 *
 * The tablet and the window each print their own version in About and each offer the same doors out: the
 * repository where the source, the releases and the issues live, and the two **issue forms** a reader
 * opens to report a problem or suggest an idea. Two literals would be two chances to spell the address
 * differently, so the strings live here — in the module both shells already link — and the tablet's
 * `AboutSection`/`FeedbackSection` and the window's About and Feedback pages read them.
 *
 * A link is not a version: naming the *other* shell's build number in About answered a question nobody
 * asks at that moment, and a reader who wants the other build wants its releases, which is here.
 */
object ProjectLinks {

    /** The repository this app is developed in — source, releases and issue tracker. */
    const val GITHUB = "https://github.com/amarzano2005/MobiXournal"

    /**
     * The **bug report form** in `.github/ISSUE_TEMPLATE`, named by the file GitHub asks for.
     *
     * A form is the door rather than a blank issue box because it asks the reporter for the three facts a
     * fix starts from — the build, the platform, and what led to the problem — instead of leaving them to
     * be guessed from a paragraph.
     */
    const val BUG_TEMPLATE = "bug_report.yml"

    /** The **feature request form**, beside it: what the reader wants to do that the app cannot yet. */
    const val FEATURE_TEMPLATE = "feature_request.yml"

    /**
     * The address that opens a **new issue** from [template] ([BUG_TEMPLATE] or [FEATURE_TEMPLATE]).
     *
     * GitHub reads the `template` query parameter as the form's file name, so the address names a file in
     * `.github/ISSUE_TEMPLATE` rather than a title or a body this module cannot keep in step. A build
     * opened against a branch without that file lands on the plain issue page, which is still the right
     * place to write — a door that cannot open is worse than a door onto a simpler room.
     */
    fun newIssue(template: String): String = "$GITHUB/issues/new?template=$template"
}
