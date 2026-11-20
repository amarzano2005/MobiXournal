package com.mobixournal.desktop.settings

/**
 * The build a reader asked never to be offered again, as one line of the window's settings file.
 *
 * The tablet keeps the same string in `mobixournal_updates`, a file of its own (`UpdatePreferences`); the
 * window already has one settings file, so a second one for a single string would be a second place a
 * choice lives. The key is named for the card's own answer, and `docs/updates.md` owns what it changes.
 */
internal const val SKIPPED_UPDATE_KEY = "update.skippedVersion"

/** The skipped build, or null when the reader has skipped none (and on a file an older build wrote). */
internal var DesktopSettings.skippedUpdateVersion: String?
    get() = properties.getProperty(SKIPPED_UPDATE_KEY)?.trim()?.takeIf { it.isNotEmpty() }
    set(value) {
        if (value == null) {
            properties.remove(SKIPPED_UPDATE_KEY)
        } else {
            properties.setProperty(SKIPPED_UPDATE_KEY, value)
        }
        save()
    }
