package com.mobixournal.desktop.shell

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.io.*
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.format.SaveFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

internal class SaveRequest(val tab: DesktopTab, val target: File, val format: SaveFormat, val completed: () -> Unit) {
    val document = tab.document
    val revision = document.edits
}
internal class SaveState { var request: SaveRequest? by mutableStateOf(null); var uiBound = false }
internal val DesktopShellState.isSaving: Boolean get() = saveState.request != null

internal fun DesktopShellState.requestSave(target: File? = null, format: SaveFormat? = null,
    completed: () -> Unit = {}, origin: DesktopTab? = active) {
    if (isSaving) return
    val tab = origin ?: return
    liveDrag?.invoke()
    liveDrag = null
    val file = target ?: tab.file
    val chosen = format ?: if (target == null) DesktopSaves.formatOf(tab.document.container)
        else DesktopSaves.formatFor(file.name, tab.document.container)
    saveState.request = SaveRequest(tab, file, chosen, completed)
}

internal class Saved(val document: DesktopDocument?, val failure: Throwable?)
internal fun writeSaveRequest(request: SaveRequest): Saved {
    val original = request.document
    var staged: DesktopSaves.Staged? = null
    val same = DesktopTabs.keyOf(request.target) == request.tab.key
    return try {
        staged = original.withReader { DesktopSaves.stage(original, request.target, request.format) }
        original.withExclusive { synchronized(original) {
            check(original.edits == request.revision) { "The document changed while saving. Save again." }
            if (same) original.close()
            staged.commit()
            val loaded = DocumentLoader.load(request.target).also { it.audioStore = original.audioStore }
            if (!same) original.close()
            staged.close()
            original.pageSource.takeIf { it.absoluteFile != original.file.absoluteFile }?.delete()
            DesktopPdfIndexCache.forgetAll()
            Saved(loaded, null)
        } }
    } catch (failure: Throwable) {
        if (same && original.isClosed && staged != null) {
            val recovered = runCatching { DocumentLoader.adopt(original.file, staged.temp).also { it.audioStore = original.audioStore } }
            Saved(recovered.getOrNull(), failure)
        } else { staged?.close(); Saved(null, failure) }
    }
}

/** Encoding and source-drain/replace/reopen run on IO; Compose state changes only after returning. */
@Composable internal fun SaveWorker(state: DesktopShellState) {
    DisposableEffect(state) { state.saveState.uiBound = true; onDispose { state.saveState.uiBound = false } }
    val request = state.saveState.request
    LaunchedEffect(request) {
        request ?: return@LaunchedEffect
        // Let the editing canvas leave composition before any archive can be closed.
        withFrameNanos { }
        withContext(NonCancellable) {
            val result = withContext(Dispatchers.IO) { writeSaveRequest(request) }
            val ownsResult = request.tab.document === request.document
            if (result.failure == null && ownsResult) state.noteSaved(request.tab)
            result.document?.let {
                if (ownsResult) request.tab.adoptSaved(it, result.failure != null) else it.close()
            }
            state.saveState.request = null
            state.failure = result.failure?.let(::describeSaveFailure)
            if (result.failure == null && ownsResult) request.completed()
        }
    }
}

/**
 * A save in progress, said **on** the page rather than instead of it: one line in the accent, no scrim and
 * no second surface. The document stays readable while the bytes are written — the window used to swap the
 * whole workspace for a placeholder, which read as a crash and threw every canvas away for the length of
 * the write.
 */
@Composable internal fun BoxScope.SavingBadge() {
    Text(
        text = "Saving...",
        color = DesktopTheme.accent,
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
    )
}
internal fun Modifier.saveInputGate(saving: Boolean): Modifier = onPreviewKeyEvent { saving }.pointerInput(saving) {
    if (saving) awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
