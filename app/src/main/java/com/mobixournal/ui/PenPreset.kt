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
        const val FACTORY_DEFAULT_MIN_PRESSURE = 0.05f
        const val FACTORY_DEFAULT_MULTIPLIER = 1.0f

        /** The factory default pen presets offered on first launch. */
        val DEFAULT_PRESETS = listOf(
            PenPreset(
                id = "default",
                name = "Default",
                minimumPressure = FACTORY_DEFAULT_MIN_PRESSURE,
                pressureMultiplier = FACTORY_DEFAULT_MULTIPLIER,
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

/** Predefined presets that were removed and should be ignored if present in storage. */
private val RETIRED_DEFAULT_PRESET_IDS = setOf("light-touch", "firm-hand", "high-sensitivity")

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
        if (id in RETIRED_DEFAULT_PRESET_IDS) return@mapNotNull null
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

/** [presets] with the entry at [index] moved by [delta] positions; out-of-range moves are no-ops. */
fun movePenPreset(presets: List<PenPreset>, index: Int, delta: Int): List<PenPreset> {
    val to = index + delta
    if (index !in presets.indices || to !in presets.indices) return presets
    return presets.toMutableList().also { it.add(to, it.removeAt(index)) }
}

private fun sanitizeField(text: String): String =
    text.replace(FIELD_SEP, ' ').replace(PRESET_SEP, ' ')

