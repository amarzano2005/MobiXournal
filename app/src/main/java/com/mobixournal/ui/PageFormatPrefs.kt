package com.mobixournal.ui

import android.content.SharedPreferences
import com.mobixournal.render.PageSizeUnit

/**
 * The **unit the page format is read in**, as the app stores it: `page_size_unit`, a name from
 * [PageSizeUnit].
 *
 * It is a preference of the app's rather than a mood of one dialog because more than one surface prints a
 * measure: the format's own fields, and the Pages menu's quick size, which announces the sheet and its step
 * in the same unit (`PageSizeUnit.measure`). The rule itself is `docs/pages-and-paper.md`'s.
 *
 * Kept out of `AppSettings`' companion because the file's line budget (`SourceStructureTest`) leaves it no
 * room, and the reading and writing of one preference belong together anyway.
 */
internal object PageFormatPrefs {

    /** The preference the unit is written under. */
    const val KEY = "page_size_unit"

    /** The stored unit, or millimetres on a first run or a name this build does not know. */
    fun read(prefs: SharedPreferences): PageSizeUnit =
        PageSizeUnit.fromName(prefs.getString(KEY, null))

    /** [editor] with the unit written — the caller keeps its own chain and `apply()`. */
    fun write(editor: SharedPreferences.Editor, unit: PageSizeUnit): SharedPreferences.Editor =
        editor.putString(KEY, unit.name)
}
