package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.mobixournal.render.KaraokeStrokeAnimator

/**
 * The window's **karaoke replay**: which recording is sounding, where in it, and therefore which strokes
 * have been written yet.
 *
 * The rules are [KaraokeStrokeAnimator]'s — the tablet's object, moved to `mobix-core` so both shells read
 * the same answer — and this class is only the window's clock: it asks the player for the position **once
 * per frame while sound is coming out**, and once when it is held. Publishing per vsync is what makes the
 * writing keep up with the voice on a 144 Hz display; publishing a held position once is what the
 * tablet's `KaraokeFrameDriver` does, because a paused page draws the same pixels however many frames are
 * asked for.
 *
 * A null [state] means nothing is on — the page is painted as finished. That is *not* the same as
 * `isPlaying = false`, which is a held playhead whose page still shows the writing up to it.
 */
internal class DesktopKaraoke {

    var state by mutableStateOf<KaraokeStrokeAnimator.State?>(null)
        private set

    /** Copy the player's own reading: the clip it holds, where it is, and whether it sounds. */
    fun publish(audio: DesktopAudioSession) {
        publish(audio.clipName, audio.player.positionMs() ?: audio.positionMs, audio.playing)
    }

    /** The same write for a reader with the three numbers already in hand — a test, or a caller's clock. */
    fun publish(clipName: String?, positionMs: Int, playing: Boolean) {
        state = if (clipName == null) {
            null
        } else {
            KaraokeStrokeAnimator.State(
                audioFile = clipName,
                playheadMs = positionMs,
                isPlaying = playing,
            )
        }
    }

    /** Nothing is on any more: the page is painted as finished, from its recorded picture again. */
    fun clear() {
        state = null
    }
}

/**
 * The karaoke state for [tab]'s page, driven while the clip [audio] holds sounds.
 *
 * Keyed on the tab so a tab switch (or a page change, which is remembered with it in [PageCanvas]) starts
 * from "nothing is on": a state left behind would dim another page's strokes for a recording it does not
 * own.
 */
@Composable
internal fun rememberKaraoke(audio: DesktopAudioSession, tab: DesktopTab, page: Int): DesktopKaraoke {
    val karaoke = remember(tab, page) { DesktopKaraoke() }
    LaunchedEffect(karaoke, audio.clipName, audio.playing) {
        if (audio.clipName == null) {
            karaoke.clear()
            return@LaunchedEffect
        }
        // One publish per frame while it sounds; the loop ends on the frame where the playhead stopped,
        // which is also the pause's single publish.
        while (true) {
            karaoke.publish(audio)
            if (!audio.playing) break
            withFrameNanos { }
        }
    }
    // A scrub moves a *held* clip: the player is not playing, so no frame loop is running, and this is
    // the write that walks the page to the position the pointer asked for.
    LaunchedEffect(karaoke, audio.clipName, audio.playing, audio.positionMs) {
        if (!audio.playing && audio.clipName != null) karaoke.publish(audio)
    }
    return karaoke
}
