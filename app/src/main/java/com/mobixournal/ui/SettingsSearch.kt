package com.mobixournal.ui

/**
 * The settings search: which [SettingsSection]s answer what the user typed.
 *
 * Search matches the section's own words — its title, its one-line summary, and its [SettingsSection.keywords]
 * aliases — because the alternative (crawling the composed pages for a label) would mean a full Compose
 * pass per keystroke, and because the aliases are where the words a user actually types live: nobody
 * names "Momentum" when looking for the setting that stops the page drifting, they type "scroll".
 * The alias lists are also what makes the search *find* things rather than merely filter titles, so
 * [SettingsSearchTest] pins a handful of everyday queries to their sections.
 *
 * Pure and free of Compose, so what the search returns is unit-testable without a device.
 */

/** The sections matching [query], in settings order; an empty or blank query matches nothing. */
fun settingsSearchResults(query: String): List<SettingsSection> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return SettingsSection.values().filter { it.matches(needle) }
}

/** The area a section lives under, or null if no area claims it (a structural bug, see SettingsAreaTest). */
fun areaOf(section: SettingsSection): SettingsArea? =
    SettingsArea.values().firstOrNull { section in it.sections }

/** True when the already-trimmed, already-lower-case [needle] is part of this section's words. */
private fun SettingsSection.matches(needle: String): Boolean =
    title.lowercase().contains(needle) ||
        summary.lowercase().contains(needle) ||
        keywords.lowercase().contains(needle)
