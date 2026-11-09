package com.mobixournal.desktop.settings

import com.mobixournal.design.ProjectLinks
import java.awt.Desktop
import java.net.URI

/**
 * The window's way **out**: the project's own page, opened in the machine's browser.
 *
 * About used to answer with the *other* shell's version number; a reader who wants the Android build wants
 * its releases, so the row opens the repository instead ([ProjectLinks], the one home for the address).
 *
 * The call is guarded on purpose: a headless JVM (every JVM test that composes the settings page) has no
 * browser and `Desktop.isDesktopSupported()` answers false, and a machine whose handler refuses must not
 * take the page down with it — a link that cannot open is a link that does nothing.
 */
internal fun openProjectPage() {
    openPage(ProjectLinks.GITHUB)
}

/**
 * Open [url] in the machine's browser — the update dialog's **what changed** door, and the project page's
 * own. The same guard: a headless JVM has no browser, and a handler that refuses must not take the page down.
 */
internal fun openPage(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
