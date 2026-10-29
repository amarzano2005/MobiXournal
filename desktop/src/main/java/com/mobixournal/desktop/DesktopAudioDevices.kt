package com.mobixournal.desktop

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

/**
 * The microphones and the outputs this machine has, as names a person can pick in the Audio section.
 *
 * `javax.sound.sampled` calls both a **mixer**: a device is a mixer that supports the line the window
 * wants, so the list is the mixers that answer for a 44.1 kHz mono 16-bit target (a microphone) or source
 * (a speaker). A machine with no sound card answers an empty list rather than throwing — a build machine
 * has neither, and the window must still open.
 *
 * The names are the platform's own (`Headset Microphone (USB Audio)`), which is what the store keeps and
 * what the rows show. They are **not** stable identities: a headset unplugged and plugged back into another
 * port arrives under another name, which is exactly why a name that no longer exists has to fall back
 * rather than fail to open ([chooseDevice]).
 */
data class AudioDevices(
    val inputs: List<String> = emptyList(),
    val outputs: List<String> = emptyList(),
)

/** What this machine has, read once per look — the Audio page asks again every time it opens. */
internal fun javaSoundDevices(): AudioDevices = AudioDevices(
    inputs = mixersSupporting(TargetDataLine::class.java),
    outputs = mixersSupporting(SourceDataLine::class.java),
)

/** The names of the mixers that support [kind] at the window's own capture/playback format. */
internal fun mixersSupporting(kind: Class<*>): List<String> = runCatching {
    val format = AudioFormat(PROBE_RATE.toFloat(), 16, 1, true, false)
    val info = DataLine.Info(kind, format)
    AudioSystem.getMixerInfo()
        .filter { spec -> runCatching { AudioSystem.getMixer(spec).isLineSupported(info) }.getOrDefault(false) }
        .mapNotNull { spec -> spec.name?.takeIf { it.isNotBlank() } }
        .distinct()
}.getOrDefault(emptyList())

/**
 * What a stored device name means now: the device itself, the system default, or **the default because the
 * device is gone** — with the name it fell back from, so the row can say so.
 *
 * A blank stored name is the system default and is not a fallback: that is the first run, and the window
 * picking the platform's default is what it was told to do. A name this machine does not have is a
 * disappeared device: the recorder or the player would fail to open it, so it is dropped to the default
 * here, once, instead of failing later in a take or a playback.
 */
internal fun chooseDevice(stored: String?, available: List<String>): AudioDeviceChoice {
    val wanted = stored?.trim()?.takeIf { it.isNotEmpty() } ?: return AudioDeviceChoice(null, null)
    return if (wanted in available) AudioDeviceChoice(wanted, null) else AudioDeviceChoice(null, wanted)
}

/** A device and, when it was dropped, the name it was dropped from. */
internal data class AudioDeviceChoice(
    /** The name to open, or null for the platform's own default. */
    val device: String?,
    /** The stored name that is no longer on this machine, or null when the choice stands. */
    val fellBackFrom: String?,
)

/**
 * The device the recorder and the player open, in force for the whole window — the seam the Audio section
 * writes and the two engines read, beside `PressureFeel` for the same reason.
 *
 * Null is the system default. The store keeps the names; this object is what the engines consult, so a
 * choice takes effect on the next take or clip without either engine knowing about settings.
 */
internal object AudioRoute {
    @Volatile
    var input: String? = null

    @Volatile
    var output: String? = null
}

/**
 * The mixer a name opens, or null when the name is blank or gone — the platform's default, which is the
 * fallback the settings rows promised. Resolved at open time as well as at pick time, because a device can
 * disappear while the window is running.
 */
internal fun mixerNamed(name: String?): Mixer? {
    val wanted = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        AudioSystem.getMixerInfo()
            .firstOrNull { spec -> spec.name == wanted }
            ?.let { spec -> AudioSystem.getMixer(spec) }
    }.getOrNull()
}

/** The line [info] describes, from the named mixer when it is there and from the platform's default when not. */
internal fun openLine(name: String?, info: DataLine.Info): Line {
    val mixer = mixerNamed(name)
    return if (mixer == null) AudioSystem.getLine(info) else mixer.getLine(info)
}

/** The rate the device lists probe with: the one both recording and playback already use. */
private const val PROBE_RATE = 44_100
