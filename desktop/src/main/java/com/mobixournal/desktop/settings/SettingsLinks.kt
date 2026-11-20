package com.mobixournal.desktop.settings

import com.mobixournal.design.ProjectLinks
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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

/**
 * **Copy app details**: the build and the Windows version behind it, on the clipboard for a report.
 *
 * The tablet's row carries three lines (build, Android release, device); this one carries the build and the
 * machine's own version word, because that is what a desktop report is asked for. The same guard as the
 * links: a headless JVM — every test that composes the page — has no clipboard, and a clipboard that
 * refuses must not take the page down with it, so the answer is `false` rather than an exception.
 */
internal fun copyAppDetailsToClipboard(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return false
    val text = buildString {
        append("MobiXournal Desktop $DESKTOP_VERSION\n")
        append("${System.getProperty("os.name")} ${System.getProperty("os.version")}\n")
        append("Reads and writes .xopp · .mobix")
    }
    return runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }.getOrDefault(false)
}
