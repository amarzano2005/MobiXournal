package com.mobixournal.desktop

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The colour panel's arithmetic, with **no Compose types**: HSV ↔ ARGB, and the `#RRGGBB` field's text.
 *
 * It lives apart from the panel for the reason the rest of the window keeps its rules out of its
 * composables: a hex field's behaviour (which spellings are accepted, what an invalid one does) is a
 * decision a JVM test can hold, while the panel's gradients and sliders are not.
 *
 * The colour is always **opaque** ARGB, like every palette entry ([com.mobixournal.design.PenPalette]):
 * a picker that could hand back a translucent colour would be a picker whose swatch disappears the moment
 * it is stored.
 */
object ColourPicker {

    /** [colour] as the field spells it: `#RRGGBB`, uppercase, always six digits. */
    fun hex(colour: Int): String = "#%06X".format(colour and 0xFFFFFF)

    /**
     * [text] as an opaque ARGB colour, or null when it is not one.
     *
     * Accepted: six hex digits in either case, with the leading `#` optional and the text trimmed — the
     * two spellings a person actually types. Three-digit shorthand and eight-digit alpha are refused
     * rather than guessed at, because guessing is how a picker commits a colour nobody chose.
     */
    fun parse(text: String): Int? {
        val clean = text.trim().removePrefix("#")
        if (clean.length != 6) return null
        if (clean.any { it !in "0123456789abcdefABCDEF" }) return null
        return 0xFF000000.toInt() or clean.toInt(16)
    }

    /** [hue] in degrees, [saturation] and [value] in 0..1, as an opaque ARGB colour. */
    fun hsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val chroma = v * s
        val switched = chroma * (1f - abs((h / 60f) % 2f - 1f))
        val floor = v - chroma
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(chroma, switched, 0f)
            1 -> Triple(switched, chroma, 0f)
            2 -> Triple(0f, chroma, switched)
            3 -> Triple(0f, switched, chroma)
            4 -> Triple(switched, 0f, chroma)
            else -> Triple(chroma, 0f, switched)
        }
        fun channel(bits: Float) = ((bits + floor) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** [colour]'s hue, saturation and value, for the panel's marker and slider. */
    fun hsvOf(colour: Int): Hsv {
        val r = ((colour shr 16) and 0xFF) / 255f
        val g = ((colour shr 8) and 0xFF) / 255f
        val b = (colour and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val rawHue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return Hsv(
            hue = ((rawHue % 360f) + 360f) % 360f,
            saturation = if (max == 0f) 0f else delta / max,
            value = max,
        )
    }
}

/** A colour in HSV, as [ColourPicker.hsvOf] reads it. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/**
 * What the colour panel is editing: an HSV position **and** the text the hex field shows.
 *
 * The two are held together because the field is two-way: dragging a slider rewrites the text, and typing
 * a valid hex moves the markers. An invalid text is remembered as [invalid] while [colour] keeps the last
 * valid colour, so a half-typed `#12` never commits black — the panel shows the message and leaves **Set**
 * out of reach.
 */
data class ColourDraft(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val text: String,
    val invalid: Boolean = false,
) {

    /** The opaque colour this draft currently describes. */
    val colour: Int get() = ColourPicker.hsv(hue, saturation, value)

    fun withHue(hue: Float): ColourDraft = at(hue, saturation, value)

    fun withSaturation(saturation: Float): ColourDraft = at(hue, saturation, value)

    fun withValue(value: Float): ColourDraft = at(hue, saturation, value)

    /**
     * The field's text: a valid hex moves the markers to it, an invalid one is kept on screen and flagged
     * while the colour stays where it was.
     */
    fun withText(typed: String): ColourDraft {
        val parsed = ColourPicker.parse(typed)
        return if (parsed == null) copy(text = typed, invalid = true) else fromColour(parsed)
    }

    private fun at(hue: Float, saturation: Float, value: Float): ColourDraft {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        return ColourDraft(h, s, v, ColourPicker.hex(ColourPicker.hsv(h, s, v)))
    }

    companion object {
        /** The draft of [colour], its text spelled as the field shows it. */
        fun fromColour(colour: Int): ColourDraft {
            val hsv = ColourPicker.hsvOf(colour)
            return ColourDraft(hsv.hue, hsv.saturation, hsv.value, ColourPicker.hex(colour))
        }
    }
}
