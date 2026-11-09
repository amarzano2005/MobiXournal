package com.mobixournal.audio

internal class AudioWorkState {
    @Volatile private var owner: String? = null
    var request: kotlinx.coroutines.Job? = null
    var seek: kotlinx.coroutines.Job? = null
    var sidecars: kotlinx.coroutines.Job? = null
    val sidecarLock = kotlinx.coroutines.sync.Mutex()
    fun cancelPlayback() { request?.cancel(); seek?.cancel() }
    fun cancel() { cancelPlayback(); sidecars?.cancel() }
    fun activate(next: String?) { if (owner != next) { cancelPlayback(); owner = next } }
    fun cancelOwner(previous: String?) { if (owner == previous) cancelPlayback() }
    fun owns(tab: String?) = owner == tab
}
