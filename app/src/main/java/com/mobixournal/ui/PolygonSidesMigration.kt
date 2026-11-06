package com.mobixournal.ui

import android.content.SharedPreferences
import com.mobixournal.render.ShapeBuilder

/** Set once the polygon's old factory count (six) has become the four this build ships. */
private const val KEY_POLYGON_SIDES_MIGRATED = "polygon_sides_migrated_v1"

/** The count the polygon tool shipped with before four; only a stored copy of it is migrated. */
internal const val LEGACY_POLYGON_SIDES = 6

/**
 * The polygon count [stored] names, with the tool's **old factory default** (six) retired to the four a
 * fresh install draws.
 *
 * Every save writes the whole settings object, so an install that never touched the count still carries
 * six in its file — indistinguishable from a deliberate six. The migration is therefore one-shot
 * ([alreadyMigrated]): the old default becomes four once, and a count the reader chooses afterwards is
 * kept, six included.
 */
internal fun migratedPolygonSides(stored: Int, alreadyMigrated: Boolean): Int = when {
    alreadyMigrated -> stored
    stored == LEGACY_POLYGON_SIDES -> ShapeBuilder.DEFAULT_POLYGON_SIDES
    else -> stored
}

/** [migratedPolygonSides] for the settings file, writing the answer and its flag back the first time. */
internal fun readPolygonSides(prefs: SharedPreferences, fallback: Int): Int {
    val stored = prefs.getInt(SettingsStore.KEY_POLYGON_SIDES, fallback)
    val alreadyMigrated = prefs.getBoolean(KEY_POLYGON_SIDES_MIGRATED, false)
    val migrated = migratedPolygonSides(stored, alreadyMigrated)
    if (!alreadyMigrated || migrated != stored) {
        prefs.edit()
            .putBoolean(KEY_POLYGON_SIDES_MIGRATED, true)
            .putInt(SettingsStore.KEY_POLYGON_SIDES, migrated)
            .apply()
    }
    return migrated
}
