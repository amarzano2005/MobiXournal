package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The **Audio page's** choices: which microphone a take comes from and which output a clip sounds through,
 * as the machine's own device names.
 *
 * Its own class rather than five more fields on [DesktopSettingsState], for the same reason the pen presets
 * have one: the page is a subject, and the store's one job is to hand it a file. It also keeps the fallback
 * in one place — a name that is no longer on the machine is dropped to the system default **here**, in the
 * store as well, so a take never fails on a device the window already knows it cannot open.
 *
 * [catalog] is the seam: the real one asks `javax.sound.sampled` for the mixers that support the window's
 * capture and playback lines ([javaSoundDevices]), and a test hands in a list of its own, because a build
 * machine has no sound card.
 */
class DesktopAudioSettings(
    private val store: DesktopSettings,
    private val catalog: () -> AudioDevices = ::javaSoundDevices,
) {

    /** The microphones and the outputs this machine has, as the two rows list them. */
    var devices by mutableStateOf(catalog())
        private set

    /** The microphone the recorder opens, by the platform's name — empty for the system default. */
    var input by mutableStateOf("")
        private set

    /** The output a clip sounds from — empty for the system default. */
    var output by mutableStateOf("")
        private set

    /** The microphone this machine no longer has, if the row dropped one — what the note under it says. */
    var inputFellBackFrom by mutableStateOf<String?>(null)
        private set

    /** The output this machine no longer has, if the row dropped one. */
    var outputFellBackFrom by mutableStateOf<String?>(null)
        private set

    /**
     * Whether each finished take is **also** copied out of the window's store, into [exportFolder] —
     * the tablet's *Save separate audio*, on the page that has a use for it (`audio.saveExternally`).
     */
    var saveExternally by mutableStateOf(false)
        private set

    /** Where those copies go; empty until the reader nominates a folder (`audio.exportFolder`). */
    var exportFolder by mutableStateOf("")
        private set

    init {
        // After the fields above: an `init` block runs where it stands, and settling the rows before their
        // state exists is a write to nothing.
        refresh()
    }

    /**
     * Read the machine's devices again and settle both rows on what exists.
     *
     * The Audio page calls this every time it opens: a headset can be plugged in while the window runs, and
     * a list read once at startup would not show it.
     */
    fun refresh() {
        devices = catalog()
        adoptInput(store.audioInputDevice)
        adoptOutput(store.audioOutputDevice)
        saveExternally = store.saveAudioExternally
        exportFolder = store.audioExportFolder
        adoptExport()
    }

    /** Turn the standalone copy on or off; the take's own engine reads it when one finishes. */
    fun chooseSaveExternally(enabled: Boolean) {
        store.saveAudioExternally = enabled
        saveExternally = enabled
        adoptExport()
    }

    /**
     * Nominate the folder standalone copies go to. A folder the window cannot see is refused in words
     * elsewhere (the picker only returns real ones), so this only records the answer.
     */
    fun chooseExportFolder(folder: File) {
        store.audioExportFolder = folder.absolutePath
        exportFolder = store.audioExportFolder
        adoptExport()
    }

    /** The export half of [DesktopAudioSession]'s state: one folder, and whether a copy is wanted. */
    private fun adoptExport() {
        DesktopAudioSession.saveExternally = saveExternally
        DesktopAudioSession.exportFolder = exportFolder.takeIf { it.isNotBlank() }?.let(::File)
    }

    /** Pick the microphone by the platform's name; empty means the system default. */
    fun chooseInput(name: String) {
        store.audioInputDevice = name
        adoptInput(name)
    }

    /** Pick the output; empty means the system default. */
    fun chooseOutput(name: String) {
        store.audioOutputDevice = name
        adoptOutput(name)
    }

    private fun adoptInput(stored: String) {
        val choice = chooseDevice(stored, devices.inputs)
        input = choice.device.orEmpty()
        inputFellBackFrom = choice.fellBackFrom
        AudioRoute.input = choice.device
        if (choice.fellBackFrom != null) store.audioInputDevice = ""
    }

    private fun adoptOutput(stored: String) {
        val choice = chooseDevice(stored, devices.outputs)
        output = choice.device.orEmpty()
        outputFellBackFrom = choice.fellBackFrom
        AudioRoute.output = choice.device
        if (choice.fellBackFrom != null) store.audioOutputDevice = ""
    }
}
