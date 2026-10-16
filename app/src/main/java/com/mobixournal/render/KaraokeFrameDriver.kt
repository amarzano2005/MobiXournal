package com.mobixournal.render

import android.view.Choreographer

/**
 * **A recording playing with the writing following it**: the frame loop that keeps a page painted at the
 * audio playhead until the clip stops (`docs/audio-inking-sync.md`).
 *
 * It has to own its frames rather than ask for a repaint per tick. [DrawingSurfaceView.render] no-ops
 * while a fling is running, so a repaint squeezed into one would be dropped exactly when the user is
 * scrolling — the loop that keeps the karaoke frame alive cannot be the surface's own. Each frame reads
 * the clip the session is on ([KaraokeFrames.Clip]), publishes the state the canvas paints from through
 * [publish], and posts the next frame only while a clip is still there.
 *
 * Where it **stops** matters as much as where it runs: a clip finishing, a stop, and the editor going
 * away all publish `null`, so the page paints itself normally again instead of freezing at the last
 * frame's dimming. Completion is not a case of its own — the session clears the clip it was on and this
 * loop ends with it on the next frame. A **pause** is the one stop that does not clear anything: the
 * loop ends, the state stays published at the held position, and [publishOnce] moves it as the scrubber
 * drags (`docs/audio-inking-sync.md`, mode 2).
 */
internal class KaraokeFrameDriver(
    private val choreographer: Choreographer,
    /** The clip the session is on, read once per frame. */
    private val clip: () -> KaraokeFrames.Clip,
    /** Put one frame's state on the surface — null when the page should paint itself again. */
    private val publish: (KaraokeStrokeAnimator.State?) -> Unit,
) {

    private var following = false
    private val frameCallback = Choreographer.FrameCallback { onFrame() }

    /** Start following the clip that just began. A second call while following is a no-op. */
    fun follow() {
        if (following) return
        following = true
        choreographer.postFrameCallback(frameCallback)
    }

    /**
     * Publish where the clip is **once**, without following it: what a held playhead needs — a pause, and
     * every step of a scrub — since nothing is moving there and a frame per vsync would repaint a page
     * that has not changed.
     */
    fun publishOnce() {
        publish(KaraokeFrames.stateOf(clip()))
    }

    /** Stop following and hand the page back its own painting. */
    fun stop() {
        if (following) {
            following = false
            choreographer.removeFrameCallback(frameCallback)
        }
        publish(null)
    }

    private fun onFrame() {
        if (!following) return
        val clip = clip()
        val state = KaraokeFrames.stateOf(clip)
        publish(state)
        // The loop lives exactly as long as the clip is *sounding*: a pause leaves the state published
        // (the page keeps showing the writing where it stopped) and the frames stopped.
        if (state == null || !clip.isPlaying) {
            following = false
            return
        }
        choreographer.postFrameCallback(frameCallback)
    }
}

/**
 * The reading one frame publishes — pure, so the mapping is unit-tested with no device.
 *
 * A clip that is sounding has a name and a playhead, and publishes both; anything else publishes
 * **nothing**: a stopped or finished clip, and a session that cannot say which recording it is on. None
 * of those means "paint at zero" — they mean "paint normally", which is why the answer is null rather
 * than a state at position nothing.
 */
internal object KaraokeFrames {

    /** The clip as the session sees it — null readings when nothing is on, sounded or held. */
    data class Clip(val recording: String?, val playheadMs: Int?, val isPlaying: Boolean)

    fun stateOf(clip: Clip): KaraokeStrokeAnimator.State? {
        val file = clip.recording ?: return null
        val ms = clip.playheadMs ?: return null
        return KaraokeStrokeAnimator.State(audioFile = file, playheadMs = ms, isPlaying = clip.isPlaying)
    }
}
