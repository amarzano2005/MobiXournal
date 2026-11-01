package com.mobixournal.desktop.settings

import com.mobixournal.desktop.sheet.RingContents
import java.util.Properties

/**
 * The ring's three answers in the settings file, as one value ([DesktopSettings.ring]).
 *
 * A store of its own rather than three properties on [DesktopSettings] for the reason the pen presets
 * have one: the window's settings file is a list of subjects, and a subject that grows a third key should
 * not make the file that holds every other subject longer.
 *
 * The keys are `ring.swatches`, `ring.widths` and `ring.tools`, and a key the file does not carry is the
 * factory answer — so a fresh install's ring holds everything without a single stored line.
 */
internal class RingSettingsStore(
    private val properties: Properties,
    private val save: () -> Unit,
) {

    /** What the ring holds — read from the three keys, written in one go by the Settings page. */
    var contents: RingContents
        get() = RingContents(
            swatches = flag(KEY_SWATCHES, true),
            widths = flag(KEY_WIDTHS, true),
            tools = RingContents.toolsOf(properties.getProperty(KEY_TOOLS)),
        )
        set(value) {
            properties.setProperty(KEY_SWATCHES, value.swatches.toString())
            properties.setProperty(KEY_WIDTHS, value.widths.toString())
            properties.setProperty(KEY_TOOLS, RingContents.encodeTools(value.tools))
            save()
        }

    /** A stored switch, or [fallback] when the key is absent or was hand-edited into something else. */
    private fun flag(key: String, fallback: Boolean): Boolean =
        properties.getProperty(key)?.trim()?.toBooleanStrictOrNull() ?: fallback

    companion object {

        /** Whether the palette's swatches are on the ring. */
        const val KEY_SWATCHES = "ring.swatches"

        /** Whether the pen's three widths are on the ring. */
        const val KEY_WIDTHS = "ring.widths"

        /** The writing tools the ring offers, by [com.mobixournal.desktop.tools.DesktopTool] name. */
        const val KEY_TOOLS = "ring.tools"
    }
}
