package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * **Which take a save may bundle.** The bug this pins is the one a reader saw as *Save failed: DIGEST_MISMATCH*:
 * a take is named by the hash of its bytes, so the file being recorded cannot be bundled — the name is computed
 * before the recorder has finished growing the file, and the next save is where its content becomes final.
 *
 * The other half matters just as much: an older recording, and a take that is merely paused, are finished files
 * and must keep being bundled, or every save would quietly strip the audio it already carried.
 */
class AudioBundleRuleTest {

    private val file = File("/audio/2026-11-16T21-40-00.ogg")

    @Test
    fun `the take being recorded is not bundled`() {
        assertNull(AudioBundleRule.bundleable("now.ogg", current = "now.ogg", recording = true, file = file))
    }

    @Test
    fun `an older recording is bundled while another one runs`() {
        assertEquals(file, AudioBundleRule.bundleable("old.ogg", current = "now.ogg", recording = true, file = file))
    }

    @Test
    fun `a finished take is bundled`() {
        assertEquals(file, AudioBundleRule.bundleable("now.ogg", current = "now.ogg", recording = false, file = file))
    }

    @Test
    fun `a paused take is still the one being written, so it is left out too`() {
        assertNull(AudioBundleRule.bundleable("now.ogg", current = "now.ogg", recording = true, file = file))
    }

    @Test
    fun `a recording the store does not hold bundles nothing`() {
        assertNull(AudioBundleRule.bundleable("gone.ogg", current = "now.ogg", recording = true, file = null))
    }
}
