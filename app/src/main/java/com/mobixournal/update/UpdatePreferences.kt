package com.mobixournal.update

import android.content.Context

/**
 * The one thing the update check remembers between launches: the build the reader asked it to stop
 * offering (`docs/updates.md`, *The popup and its states*).
 *
 * It has its own preferences file rather than a row in `SettingsStore`, the way the bookmark and recents
 * stores do: a skip is about the app, not the notebook editor's settings, and the launch check reads it
 * before any settings surface exists. The stored value is a **version string**, so a newer release — a
 * different string — is offered again without anything having to clear the entry.
 */
internal class UpdatePreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mobixournal_updates", Context.MODE_PRIVATE)

    /** The version the reader skipped, or null when they have skipped none. */
    fun skippedVersion(): String? = prefs.getString(KEY_SKIPPED, null)

    /** Remember [version] as skipped; **Skip this version** is the only writer. */
    fun skip(version: String) {
        prefs.edit().putString(KEY_SKIPPED, version).apply()
    }

    private companion object {
        const val KEY_SKIPPED = "skippedVersion"
    }
}
