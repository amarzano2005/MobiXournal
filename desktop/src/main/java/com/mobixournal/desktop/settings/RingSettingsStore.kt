package com.mobixournal.desktop.settings

import com.mobixournal.desktop.sheet.RingContents
import java.util.Properties

/**
 * The ring's faces in the settings file, as one value ([DesktopSettings.ring]).
 *
 * One key, `ring.slots`, and its **absence is the automatic ring**: a fresh install carries the palette, the
 * three widths and the three tools without a single stored line, and could not be told apart from a line
 * that spelled them out. The absence also means "follow the palette", which is what a reader who never
 * edited the ring should get when they add a colour to the strip.
 */
internal class RingSettingsStore(
    private val properties: Properties,
    private val save: () -> Unit,
) {

    /** What the ring holds — the reader's own faces, or [RingContents.Auto] when nothing is stored. */
    var contents: RingContents
        get() = RingContents.decode(properties.getProperty(KEY_SLOTS))
            ?.let(RingContents::Fixed)
            ?: RingContents.Auto
        set(value) {
            when (value) {
                // The automatic ring is the *absence* of the key, so a reset is a removal and a build that
                // later changes what "automatic" means is what such a file gets.
                is RingContents.Auto -> properties.remove(KEY_SLOTS)
                is RingContents.Fixed -> properties.setProperty(KEY_SLOTS, RingContents.encode(value.faces))
            }
            save()
        }

    companion object {

        /** The ring's own positions, in order: `c:…` a colour, `w:…` a width, `t:…` a tool. */
        const val KEY_SLOTS = "ring.slots"
    }
}
