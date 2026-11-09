package com.mobixournal.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Requires a device codec/looper: compile in the normal loop, execute during an audio device pass. */
@RunWith(AndroidJUnit4::class)
class AudioPlayerAsyncTest {
    private fun fixture(): File = File.createTempFile("async-player-", ".wav",
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir).also { file ->
        WavWriter(file, 44100).use { writer -> val silence = ByteArray(88200); writer.write(silence, silence.size) }
    }

    @Test fun preparedDecoderIsReusedForPlayPauseAndResume() = runBlocking {
        val file = fixture()
        withContext(Dispatchers.Main) {
            val player = AudioPlayer()
            try {
                assertTrue(withTimeout(10000) { player.prepare(file, 200) })
                assertTrue(player.durationMs!! in 900..1100)
                assertTrue(player.playPrepared(file, 200))
                assertTrue(player.currentPositionMs!! >= 150)
                assertTrue(player.pause())
                player.playbackSpeed = 1.5f
                assertFalse(player.isPlaying)
                assertTrue(player.resume())
                player.stop()
                assertNull(player.currentPositionMs)
            } finally { player.release(); file.delete() }
        }
    }

    @Test fun cancellingPreparationCannotStartALateClip() = runBlocking {
        val file = fixture()
        withContext(Dispatchers.Main) {
            val player = AudioPlayer()
            try {
                val request = launch(start = CoroutineStart.UNDISPATCHED) { player.prepare(file, 0) }
                request.cancelAndJoin()
                yield()
                assertFalse(player.isSounding)
                assertNull(player.currentPositionMs)
            } finally { player.release(); file.delete() }
        }
    }
}
