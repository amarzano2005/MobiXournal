package com.mobixournal.ui

import com.mobixournal.format.model.LineStyle

/**
 * Encoding of the saved [ToolPreset] list to and from the single string [SettingsStore] keeps in
 * `SharedPreferences`, in the same plain-text style as the other list encoders.
 *
 * The wire form is `|`-separated presets, each of them six `;`-separated fields:
 *
 * ```
 * fine-black;Fine black;PEN;-16777216;0.85;PLAIN|marker;Marker;HIGHLIGHTER;-256;8.0;DASHED
 * ```
 *
 * Decoding is deliberately forgiving so a list written by an older or newer build still loads: a
 * preset with too few fields, an unknown tool/line-style name or an unparsable number is **dropped**
 * and the rest survive. One unreadable preset costs the user that preset, not the whole list. A list
 * written by a build that still carried fill's two trailing fields decodes all the same — the extra
 * fields are ignored rather than rejected.
 */

/** Separates one preset's fields. */
private const val FIELD_SEP: Char = ';'

/** Separates presets. */
private const val PRESET_SEP: Char = '|'

/** Serialise [presets] into the one-line form [decodeToolPresets] reads back. */
fun encodeToolPresets(presets: List<ToolPreset>): String =
    presets.joinToString(PRESET_SEP.toString()) { p ->
        listOf(
            sanitize(p.id),
            sanitize(p.name),
            p.tool.name,
            p.colorArgb.toString(),
            p.widthPt.toString(),
            p.lineStyle.name,
        ).joinToString(FIELD_SEP.toString())
    }

/** Parse what [encodeToolPresets] wrote, skipping any preset this build can't make sense of. */
fun decodeToolPresets(raw: String?): List<ToolPreset> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(PRESET_SEP).mapNotNull(::decodePreset)
}

/** One preset, or `null` when a field is missing, unknown to this build, or unparsable. */
private fun decodePreset(raw: String): ToolPreset? {
    val f = raw.split(FIELD_SEP)
    if (f.size < 6) return null
    val id = f[0].trim().ifEmpty { return null }
    val tool = enumOrNull<EditorTool>(f[2].trim()) ?: return null
    val color = f[3].trim().toIntOrNull() ?: return null
    // Coerced rather than rejected: a width from a build with different bounds is still a width.
    val width = f[4].trim().toFloatOrNull()?.coerceIn(PEN_WIDTH_MIN, PEN_WIDTH_MAX) ?: return null
    val style = enumOrNull<LineStyle>(f[5].trim()) ?: return null
    return ToolPreset(
        id = id,
        name = f[1].trim().ifEmpty { id },
        tool = tool,
        colorArgb = color,
        widthPt = width,
        lineStyle = style,
    )
}

/** Strip the separators out of a free-text field so it can't break the wire form. */
private fun sanitize(text: String): String = text.replace(FIELD_SEP, ' ').replace(PRESET_SEP, ' ')

/** An enum constant by name, or `null` if this build no longer has it. */
private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
    runCatching { enumValueOf<E>(name) }.getOrNull()
