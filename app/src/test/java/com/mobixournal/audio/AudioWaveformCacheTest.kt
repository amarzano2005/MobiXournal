package com.mobixournal.audio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioWaveformCacheTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cacheReusesACompletedFileAndInvalidatesSameLengthReplacement() {
        val file = temp.newFile("clip.ogg").apply { writeBytes(ByteArray(1024) { 16 }) }
        val first = AudioWaveformExtractor.extract(file)
        assertSame(first, AudioWaveformExtractor.extract(file))
        val stamp = file.lastModified()
        file.writeBytes(ByteArray(1024) { 100 }); assertTrue(file.setLastModified(stamp + 5000))
        assertNotEquals(first, AudioWaveformExtractor.extract(file))
    }

    @Test fun cacheEvictsOldFilesInsteadOfGrowingWithEveryRecording() {
        val firstFile = temp.newFile("old.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val first = AudioWaveformExtractor.extract(firstFile)
        repeat(20) { n -> AudioWaveformExtractor.extract(temp.newFile("$n.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }) }
        assertNotSame(first, AudioWaveformExtractor.extract(firstFile))
    }

    @Test fun cancelledExtractionDoesNotPublishAPartialCachedWaveform() {
        val file = temp.newFile("cancel.ogg").apply { writeBytes(ByteArray(200000) { 90 }) }
        try { AudioWaveformExtractor.extract(file, checkCancelled = { throw java.util.concurrent.CancellationException() }); fail() }
        catch (_: java.util.concurrent.CancellationException) { }
        assertTrue(AudioWaveformExtractor.extract(file).all { it > .5f })
    }
}
