package com.mobixournal.desktop

import com.mobixournal.render.PressureCurve
import java.util.Locale

/**
 * A named pair of the pen's pressure numbers — the window's half of the tablet's `PenPreset`.
 *
 * On the tablet a preset is exactly this too: a *name* for the floor under a light press and the
 * multiplier above it (`docs/stylus.md`). The window keeps its own copy rather than importing the app's,
 * for the reason it keeps its own settings file: `desktop/` consumes `mobix-core` and nothing under
 * `app/`, and these two numbers are the whole of the state — the filter they feed is the shared
 * [PressureCurve] on both shells.
 *
 * A preset is **only a value**: applying one writes the two numbers the Stylus rows and the sliders
 * write, so a preset can never mean anything a hand could not have set by hand.
 */
data class DesktopPenPreset(
    val id: String,
    val name: String,
    val minimumPressure: Float,
    val pressureMultiplier: Float,
) {
    /** The two numbers as one line, the way a row under the preset's name reads them. */
    val values: String
        get() = String.format(Locale.ROOT, "%.2f · %.2f×", minimumPressure, pressureMultiplier)

    companion object {
        /** The factory preset's stable handle — never a name the reader typed, so it cannot collide. */
        const val DEFAULT_ID = "default"

        /** The factory preset's name, which is what the menu's chip shows. */
        const val DEFAULT_NAME = "Default"

        /** The one preset a fresh install has, and the one the list can never be emptied of. */
        fun factoryDefault(): DesktopPenPreset = DesktopPenPreset(
            id = DEFAULT_ID,
            name = DEFAULT_NAME,
            minimumPressure = PressureCurve.MINIMUM_PRESSURE_DEFAULT,
            pressureMultiplier = 1f,
        )

        val DEFAULT_PRESETS: List<DesktopPenPreset> = listOf(factoryDefault())

        /** A stable kebab-case handle for a preset named [name], falling back to `preset`. */
        fun slugId(name: String): String = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
            .ifEmpty { "preset" }

        /** The floor, clamped to the range the Stylus row offers. */
        fun clampMinimum(value: Float): Float =
            value.coerceIn(PressureCurve.MINIMUM_PRESSURE_MIN, PressureCurve.MINIMUM_PRESSURE_MAX)

        /** The multiplier, clamped to the range Xournal++'s own filter offers. */
        fun clampMultiplier(value: Float): Float =
            value.coerceIn(PressureCurve.MULTIPLIER_MIN, PressureCurve.MULTIPLIER_MAX)
    }
}

/** Separates one preset's fields on the stored line. */
private const val FIELD_SEP: Char = ';'

/** Separates the presets on the stored line. */
private const val PRESET_SEP: Char = '|'

/**
 * Serialise [presets] into one line for `DesktopSettings`, in the same plain-text style as the window's
 * other list settings (`interface.shortcuts`, `interface.toolOrder`).
 */
fun encodePenPresets(presets: List<DesktopPenPreset>): String =
    presets.joinToString(PRESET_SEP.toString()) { preset ->
        listOf(
            sanitizeField(preset.id),
            sanitizeField(preset.name),
            preset.minimumPressure.toString(),
            preset.pressureMultiplier.toString(),
        ).joinToString(FIELD_SEP.toString())
    }

/**
 * Parse what [encodePenPresets] wrote, with the window's repair rule: a preset whose line this build
 * cannot make sense of is **dropped and the rest survive**, and a line with nothing usable in it is the
 * factory preset — one unreadable entry costs the reader that entry, not the list.
 */
fun decodePenPresets(raw: String?): List<DesktopPenPreset> {
    if (raw.isNullOrBlank()) return DesktopPenPreset.DEFAULT_PRESETS
    val parsed = raw.split(PRESET_SEP).mapNotNull { entry ->
        val fields = entry.split(FIELD_SEP)
        if (fields.size < 4) return@mapNotNull null
        val id = fields[0].trim().ifEmpty { return@mapNotNull null }
        val name = fields[1].trim().ifEmpty { id }
        val minimum = fields[2].trim().toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        val multiplier = fields[3].trim().toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        DesktopPenPreset(
            id = id,
            name = name,
            minimumPressure = DesktopPenPreset.clampMinimum(minimum),
            pressureMultiplier = DesktopPenPreset.clampMultiplier(multiplier),
        )
    }
    return parsed.ifEmpty { DesktopPenPreset.DEFAULT_PRESETS }
}

/** [presets] with [preset] appended, or replacing the entry that already holds its id. */
fun addOrUpdatePenPreset(
    presets: List<DesktopPenPreset>,
    preset: DesktopPenPreset,
): List<DesktopPenPreset> {
    val at = presets.indexOfFirst { it.id == preset.id }
    if (at < 0) return presets + preset
    return presets.toMutableList().also { it[at] = preset }
}

/** [presets] without the one identified by [id]. The list always keeps at least one. */
fun removePenPreset(presets: List<DesktopPenPreset>, id: String): List<DesktopPenPreset> {
    val left = presets.filterNot { it.id == id }
    return left.ifEmpty { presets }
}

/** [presets] with the entry at [id] renamed; the id is stable, so the selection survives a rename. */
fun renamePenPreset(
    presets: List<DesktopPenPreset>,
    id: String,
    name: String,
): List<DesktopPenPreset> {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return presets
    return presets.map { if (it.id == id) it.copy(name = trimmed) else it }
}

/**
 * A handle for a preset called [name] that no entry in [presets] holds yet.
 *
 * Deterministic rather than time-stamped (`light-hand`, `light-hand-2`, …), because two presets named the
 * same must still be two entries a test can name and a click can tell apart.
 */
fun uniquePenPresetId(presets: List<DesktopPenPreset>, name: String): String {
    val base = DesktopPenPreset.slugId(name)
    if (presets.none { it.id == base }) return base
    var suffix = 2
    while (presets.any { it.id == "$base-$suffix" }) suffix++
    return "$base-$suffix"
}

/** Keep a stored name from carrying the two separators the line is read by. */
private fun sanitizeField(text: String): String =
    text.replace(FIELD_SEP, ' ').replace(PRESET_SEP, ' ')
