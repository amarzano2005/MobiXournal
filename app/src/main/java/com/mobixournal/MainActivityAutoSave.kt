package com.mobixournal

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import com.mobixournal.io.AutoSavePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * The tablet's **Auto save**: a document that has a file of its own is written by itself once the strokes have
 * been quiet for [AutoSavePolicy.QUIET_MS].
 *
 * The watch is one job for the whole activity, not one per document: it looks at the canvas the focused pane is
 * showing and at the tab that canvas belongs to, so switching tabs is not a thing it has to be told about. It
 * reads the setting on every pass instead of being started and stopped by it, which is why a switch flipped in
 * Settings takes effect without the shell wiring anything.
 *
 * Every gate is [AutoSavePolicy]'s: the setting, an edited document, a writable file, not read-only, nothing
 * else already writing. What it adds is only the **quiet period** — measured by watching the canvas's own edit
 * count rather than a clock, so an edit that lands between two passes restarts the wait without anyone having
 * to record a timestamp on the drawing path.
 */
private const val TICK_MS = 1_000L

/**
 * Starts the watch for [activity] — once per activity, so a recreated window gets its own job and a dead one's
 * coroutine is already cancelled with its scope.
 *
 * It is deliberately started by the **first edit** (`markShowingTabEdited`) rather than at launch: a watch with
 * nothing to write would tick for as long as the app is open on the entry screen, and a document cannot need
 * saving before something has been written on it.
 */
internal object AutoSaveWatch {

    /** Which activity already holds a watch, held weakly: the job must not keep a closed window alive. */
    private var owner: WeakReference<MainActivity>? = null

    fun start(activity: MainActivity) {
        if (owner?.get() === activity) return
        owner = WeakReference(activity)
        activity.startAutoSaveLoop()
    }
}

/** The loop itself: measure quiet, then ask [AutoSavePolicy] and write. */
internal fun MainActivity.startAutoSaveLoop() {
    lifecycleScope.launch {
        var lastEdits = -1
        var quietMs = 0L
        while (isActive) {
            delay(TICK_MS)
            val surface = pane.surface ?: continue
            // The canvas's own count: an edit — a stroke, an erase, an undo, a page command — changes it, and
            // the count not changing is what "quiet" means here.
            val edits = surface.edits
            if (edits == lastEdits) {
                quietMs += TICK_MS
            } else {
                lastEdits = edits
                quietMs = 0
            }
            if (quietMs < AutoSavePolicy.QUIET_MS) continue
            val tab = pane.tabs.active ?: continue
            val target = tab.uri?.let(Uri::parse)?.takeIf { io.isWritable(it) }
            if (target == null) continue
            val due = AutoSavePolicy.due(
                enabled = settingsState.value.autoSave,
                edited = tab.unsavedEdits,
                readOnly = tab.isReadOnly || surface.isReadOnly,
                hasTarget = true,
                busy = busy.value != null,
            )
            if (!due) continue
            quietMs = 0
            // The same path plain Save takes with a file already chosen — never the picker: the app may not
            // name a notebook.
            saveDocument(target)
        }
    }
}
