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

    /** Copy one tab's reading — its clip, where that clip is now, and whether it sounds. */
    fun publish(sound: DesktopTabAudio, positionMs: Int) {
        publish(sound.clipName, positionMs, sound.playing)
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
 * The karaoke state for [tab]'s page, driven while **that tab's** clip sounds.
 *
 * Keyed on the tab so a tab switch (or a page change, which is remembered with it in [PageCanvas]) starts
 * from "nothing is on": a state left behind would dim another page's strokes for a recording it does not
 * own — and the reading is [DesktopAudioSession.of]'s, so a clip that belongs to a different document is
 * never published here at all.
 */
@Composable
internal fun rememberKaraoke(audio: DesktopAudioSession, tab: DesktopTab, page: Int): DesktopKaraoke {
    val karaoke = remember(tab, page) { DesktopKaraoke() }
    val sound = audio.of(tab)
    LaunchedEffect(karaoke, sound.clipName, sound.playing) {
        if (sound.clipName == null) {
            karaoke.clear()
            return@LaunchedEffect
        }
        // One publish per frame while it sounds; the loop ends on the frame where the playhead stopped,
        // which is also the pause's single publish. The stop is read from the **player**, not from the
        // 100 ms pill poll: a frame loop that outlived the sound kept repainting a page it could no
        // longer take from its recorded picture.
        while (true) {
            karaoke.publish(sound, audio.clipPositionMs(tab) ?: sound.positionMs)
            if (!audio.sounding(tab)) break
            withFrameNanos { }
        }
    }
    // A scrub moves a *held* clip: the player is not playing, so no frame loop is running, and this is
    // the write that walks the page to the position the pointer asked for.
    LaunchedEffect(karaoke, sound.clipName, sound.playing, sound.positionMs) {
        if (!sound.playing && sound.clipName != null) {
            karaoke.publish(sound, audio.clipPositionMs(tab) ?: sound.positionMs)
        }
    }
    return karaoke
}
