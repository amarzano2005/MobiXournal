package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Audio page's two choices: they survive a restart, and a device that is gone becomes the system default
 * **with a sentence** rather than a take that fails to open.
 *
 * The catalogue is a function the page reads (`DesktopAudioSettings`'s `catalog`), so the fallback is proved
 * with two made-up devices on a machine that has no sound card at all — the same seam the recorder's own
 * line uses.
 */
class DesktopAudioSettingsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val devices = AudioDevices(
        inputs = listOf("Headset Microphone (USB Audio)", "Laptop Microphone"),
        outputs = listOf("Speakers (Realtek)", "Headset Earphone (USB Audio)"),
    )

    private fun page(file: File = File(temp.root, "audio.properties")) =
        DesktopAudioSettings(DesktopSettings(file)) { devices }

    @Test
    fun `both devices survive a restart`() {
        val file = File(temp.root, "restart.properties")
        val audio = page(file)
        audio.chooseInput("Laptop Microphone")
        audio.chooseOutput("Headset Earphone (USB Audio)")

        val reopened = DesktopSettings(file)
        assertEquals("Laptop Microphone", reopened.audioInputDevice)
        assertEquals("Headset Earphone (USB Audio)", reopened.audioOutputDevice)

        // And the window that opens over that file has them in force for the engines, not only on screen.
        val second = page(file)
        assertEquals("Laptop Microphone", second.input)
        assertEquals("Headset Earphone (USB Audio)", second.output)
        assertEquals("Laptop Microphone", AudioRoute.input)
        assertEquals("Headset Earphone (USB Audio)", AudioRoute.output)
        audio.chooseInput("")
        audio.chooseOutput("")
    }

    @Test
    fun `the window's settings state builds the page and puts the route in force`() {
        val file = File(temp.root, "state.properties")
        DesktopSettings(file).audioInputDevice = "Laptop Microphone"
        // No catalog is injected here: the window's own state reads the machine, which has no microphone in
        // a build machine, so the stored name is dropped rather than opened.
        val state = DesktopSettingsState(DesktopSettings(file))
        assertEquals("", state.audio.input)
        assertEquals("Laptop Microphone", state.audio.inputFellBackFrom)
        assertEquals(null, AudioRoute.input)
    }

    @Test
    fun `the system default is the absence of the key, not an empty name`() {
        val file = File(temp.root, "default.properties")
        val settings = DesktopSettings(file)
        settings.audioInputDevice = "Laptop Microphone"
        settings.audioInputDevice = ""
        assertEquals("", settings.audioInputDevice)
        assertNull("the name is not kept", DesktopSettings(file).snapshot()[DesktopSettings.KEY_AUDIO_INPUT])
    }

    @Test
    fun `a device that has gone falls back to the default and says so`() {
        val file = File(temp.root, "vanished.properties")
        DesktopSettings(file).apply {
            audioInputDevice = "Bluetooth Headset (gone)"
            audioOutputDevice = "Dock Speakers (gone)"
        }

        val audio = page(file)
        assertEquals("the microphone drops to the default", "", audio.input)
        assertEquals("and the row keeps the name it dropped", "Bluetooth Headset (gone)", audio.inputFellBackFrom)
        assertEquals("", audio.output)
        assertEquals("Dock Speakers (gone)", audio.outputFellBackFrom)
        assertEquals("the recorder gets the platform's default", null, AudioRoute.input)
        assertEquals("and so does the player", null, AudioRoute.output)
        assertNull(
            "the store stopped naming a device nothing can open",
            DesktopSettings(file).snapshot()[DesktopSettings.KEY_AUDIO_INPUT],
        )
    }

    @Test
    fun `a device that appears while the window runs is offered`() {
        val file = File(temp.root, "plugged-in.properties")
        var catalog = AudioDevices(inputs = emptyList())
        val audio = DesktopAudioSettings(DesktopSettings(file)) { catalog }
        assertEquals("nothing to pick yet", emptyList<String>(), audio.devices.inputs)

        catalog = AudioDevices(inputs = listOf("Headset Microphone (USB Audio)"))
        audio.refresh()
        assertEquals(listOf("Headset Microphone (USB Audio)"), audio.devices.inputs)
        audio.chooseInput("Headset Microphone (USB Audio)")
        assertEquals("Headset Microphone (USB Audio)", AudioRoute.input)
        audio.chooseInput("")
    }

    @Test
    fun `a stored name resolves to itself, the default, or the fallback`() {
        val available = devices.inputs
        assertEquals(AudioDeviceChoice("Laptop Microphone", null), chooseDevice("Laptop Microphone", available))
        assertEquals("blank is the default", AudioDeviceChoice(null, null), chooseDevice("", available))
        assertEquals("an absent name too", AudioDeviceChoice(null, null), chooseDevice(null, available))
        assertEquals(
            "a vanished device is the default, with its name kept for the row",
            AudioDeviceChoice(null, "Studio Mic"),
            chooseDevice("Studio Mic", available),
        )
    }
}
