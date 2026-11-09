package com.mobixournal.desktop.ink

import com.mobixournal.collections.IdentityLruCache
import com.mobixournal.collections.PersistentVector
import com.mobixournal.format.model.*

/** Converts only newly appended input samples; immutable point roots can safely reach a frame cache. */
internal object DraftStrokeCache {
    private class State(val tool: Tool, val width: Float) {
        var points = PersistentVector.empty<StrokePoint>()
        var first: InkSample? = null
        var last: InkSample? = null
    }
    private val states = IdentityLruCache<Any, State>(32, 200_000) { it.points.size.toLong() }
    @Synchronized fun stroke(identity: Any, samples: List<InkSample>, tool: Tool, colour: Int,
        width: Float, style: LineStyle): Stroke? {
        if (samples.size < 2) return null
        var state = states[identity]
        if (state == null || state.tool != tool || state.width != width || state.points.size > samples.size ||
            state.first !== samples.first() || (state.points.isNotEmpty() && state.last !== samples[state.points.lastIndex])) {
            state = State(tool, width)
            state.first = samples.first()
        }
        val count = samples.size
        val start = samples.first().pointer.timeMs
        for (i in state.points.size until count) {
            val sample = samples[i]
            state.points = state.points.appended(StrokePoint(sample.x, sample.y,
                if (tool == Tool.HIGHLIGHTER) width.toDouble() else width * sample.factor.toDouble(),
                sample.pointer.pressure, sample.pointer.tiltRad, sample.pointer.azimuthRad,
                (sample.pointer.timeMs - start).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()))
        }
        state.last = samples[count - 1]
        states.put(identity, state)
        return Stroke(tool, colour, "round", state.points, tool == Tool.HIGHLIGHTER, style)
    }
}
