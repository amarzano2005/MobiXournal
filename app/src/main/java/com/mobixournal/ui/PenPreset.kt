package com.mobixournal.ui

import com.mobixournal.render.PressureCurve

/**
 * A named preset for the pen's pressure parameters: sensitivity (minimum pressure floor)
 * and pressure multiplier.
 */
data class PenPreset(
    val id: String,
    val name: String,
    val minimumPressure: Float,
    val pressureMultiplier: Float,
) {
    companion object {
        /** The factory default pen presets offered on first launch. */
        val DEFAULT_PRESETS = listOf(
            PenPreset(
                id = "default",
                name = "Default",
                minimumPressure = 0.05f,
                pressureMultiplier = 1.0f,
            ),
            PenPreset(
                id = "light-touch",
                name = "Light touch",
                minimumPressure = 0.08f,
                pressureMultiplier = 1.5f,
            ),
            PenPreset(
                id = "firm-hand",
                name = "Firm hand",
                minimumPressure = 0.02f,
                pressureMultiplier = 0.75f,
            ),
            PenPreset(
                id = "high-sensitivity",
                name = "High sensitivity",
                minimumPressure = 0.12f,
                pressureMultiplier = 2.0f,
            ),
        )

        /** Generate a stable slug id from a preset name. */
        fun slugId(name: String): String =
            name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("").trim('-').replace(Regex("-+"), "-")
                .ifEmpty { "preset" }
    }
}

/** Separates one preset's fields. */
private const val FIELD_SEP: Char = ';'

/** Separates presets. */
private const val PRESET_SEP: Char = '|'

/** Serialise [presets] into a single string for SharedPreferences. */
fun encodePenPresets(presets: List<PenPreset>): String =
    presets.joinToString(PRESET_SEP.toString()) { p ->
        listOf(
            sanitizeField(p.id),
            sanitizeField(p.name),
            p.minimumPressure.toString(),
            p.pressureMultiplier.toString(),
        ).joinToString(FIELD_SEP.toString())
    }

/** Parse what [encodePenPresets] wrote, falling back to [fallback] when nothing usable is stored. */
fun decodePenPresets(raw: String?, fallback: List<PenPreset> = PenPreset.DEFAULT_PRESETS): List<PenPreset> {
    if (raw.isNullOrBlank()) return fallback
    val parsed = raw.split(PRESET_SEP).mapNotNull { entry ->
        val f = entry.split(FIELD_SEP)
        if (f.size < 4) return@mapNotNull null
        val id = f[0].trim().ifEmpty { return@mapNotNull null }
        val name = f[1].trim().ifEmpty { id }
        val minP = f[2].trim().toFloatOrNull()?.coerceIn(PressureCurve.MINIMUM_PRESSURE_MIN, 1f)
            ?: return@mapNotNull null
        val mult = f[3].trim().toFloatOrNull()?.coerceIn(
            AppSettings.PRESSURE_MULTIPLIER_MIN,
            AppSettings.PRESSURE_MULTIPLIER_MAX,
        ) ?: return@mapNotNull null
        PenPreset(id = id, name = name, minimumPressure = minP, pressureMultiplier = mult)
    }
    return if (parsed.isEmpty()) fallback else parsed
}

/** [presets] with the entry at [id] renamed (its id is stable, so references survive a rename). */
fun renamePenPreset(presets: List<PenPreset>, id: String, newName: String): List<PenPreset> =
    presets.map { if (it.id == id) it.copy(name = newName) else it }

/** [presets] with [preset] added, or replacing an existing preset with the same id. */
fun addOrUpdatePenPreset(presets: List<PenPreset>, preset: PenPreset): List<PenPreset> {
    val at = presets.indexOfFirst { it.id == preset.id }
    if (at < 0) return presets + preset
    return presets.toMutableList().also { it[at] = preset }
}

/** [presets] without the one identified by [id]. Always keeps at least one preset. */
fun removePenPreset(presets: List<PenPreset>, id: String): List<PenPreset> {
    val filtered = presets.filterNot { it.id == id }
    return if (filtered.isEmpty()) presets else filtered
}

private fun sanitizeField(text: String): String =
    text.replace(FIELD_SEP, ' ').replace(PRESET_SEP, ' ')
