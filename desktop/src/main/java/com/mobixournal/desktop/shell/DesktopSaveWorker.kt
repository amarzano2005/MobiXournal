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
import com.mobixournal.io.DocumentEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A save that has been **asked for** and not yet written.
 *
 * Everything the write needs is read here, on the command's own thread, and kept: the pages as they are
 * (`snapshot`), the shape the container is written with, the PDF a page sits on, the audio store. The write
 * then touches **none** of it again, which is what lets the canvas keep taking strokes while the bytes go
 * out — the document on screen and the document being written are two different, immutable values.
 *
 * [revision] and [pageCount] are the commit's two questions: did the reader edit at all, and did they change
 * the *list* of pages. Content edits can be left unpublished; a structural one cannot be reconciled.
 */
internal class SaveRequest(val tab: DesktopTab, val target: File, val format: SaveFormat, val completed: () -> Unit) {
    val document = tab.document
    val revision = document.edits
    val snapshot = document.snapshot()
    val shape: DocumentEncoder.SaveShape = document.saveShapeFor(target)
    val pdf: File? = document.pdfSource()
    val audio = document.audioStore
    val readOnly = document.readOnly
    val gateNote: String? = document.gateNote
    val pageCount = snapshot.model.pages.size
}

internal class SaveState {
    var request: SaveRequest? by mutableStateOf(null)

    /**
     * True only while the staged bytes are **put in place** — the move, and the `.mobix` reopen it needs.
     *
     * Separate from [request] because that is the whole transaction: the encoding is seconds on a big
     * notebook and the commit is a rename, and only the second one has to hold the canvas still.
     */
    var committing by mutableStateOf(false)
    var uiBound = false
}

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

/**
 * The **long half**, and the one that used to block the window: encode the snapshot.
 *
 * It runs on IO under a *read* lock, so painting and editing keep their own readers — a save and a stroke
 * no longer exclude each other. Nothing here touches the destination.
 */
internal fun stageSaveRequest(request: SaveRequest): DesktopSaves.Staged = request.document.withReader {
    DesktopSaves.stageSnapshot(
        model = request.snapshot.model,
        shape = request.shape,
        readOnly = request.readOnly,
        gateNote = request.gateNote,
        target = request.target,
        format = request.format,
        pdf = request.pdf,
        audio = request.audio,
    )
}

internal class Saved(
    val document: DesktopDocument?,
    val failure: Throwable?,
    /**
     * The tab keeps the document it had: what reached the file is the snapshot, and the edits made while it
     * was written are still only in memory — so the tab is **dirty**, which is the truth about the file.
     */
    val kept: Boolean = false,
    /** An edit landed after the snapshot: the file does not hold it, so the tab stays dirty. */
    val stillDirty: Boolean = false,
)

/**
 * The **short half**: put the staged bytes in place, and leave the tab honest about what the file holds.
 *
 * Three answers, and the middle one is what makes a save non-blocking:
 *
 *  - nothing was edited while the bytes were written → the file and the document agree: the archive (if
 *    any) is released, the move lands, and the tab is re-opened onto it, clean;
 *  - the reader kept working, and the container holds **no handle** (a `.xopp` is read whole) → the move
 *    lands and the tab keeps the pages it has, dirty, because the file does not hold them yet. Nothing is
 *    thrown away and nothing is re-read;
 *  - the reader kept working on a `.mobix`, or changed the **list** of pages → the file must not be
 *    replaced: a lazy notebook has to release its archive before the move, which would take the pages the
 *    new edits sit in, and a structural edit invalidates the snapshot's indices. The staged file is
 *    dropped and the tab is told to save again, with every page still in memory.
 */
internal fun commitSaveRequest(request: SaveRequest, staged: DesktopSaves.Staged): Saved {
    val original = request.document
    val same = DesktopTabs.keyOf(request.target) == request.tab.key
    val holdsNoHandle = original.container != DesktopContainer.MOBIX
    return try {
        original.withExclusive { synchronized(original) {
            val newer = original.edits != request.revision
            if (same) {
                // Nothing has to be released: a `.xopp` holds no file, and a `.mobix` reads a copy in the
                // cache (DesktopReadCopies), so the file just written is not the one the tab is reading. The
                // move lands and the tab keeps the document it was drawing on — dirty if an edit arrived
                // after the snapshot, because the file does not hold that one yet.
                staged.commit()
                return@withExclusive Saved(null, null, kept = true, stillDirty = newer)
            }
            staged.commit()
            val loaded = DocumentLoader.load(request.target).also { it.audioStore = request.audio }
            original.close()
            staged.close()
            original.pageSource.takeIf { it.absoluteFile != original.file.absoluteFile }?.delete()
            DesktopPdfIndexCache.forgetAll()
            Saved(loaded, null)
        } }
    } catch (failure: Throwable) {
        if (same && original.isClosed && staged != null) {
            val recovered = runCatching { DocumentLoader.adopt(original.file, staged.temp).also { it.audioStore = request.audio } }
            Saved(recovered.getOrNull(), failure)
        } else { staged.close(); Saved(null, failure) }
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
            val staged = runCatching { withContext(Dispatchers.IO) { stageSaveRequest(request) } }
            val result = staged.fold(
                onSuccess = { bytes ->
                    // Only the commit holds the canvas still: the bytes are already written by now.
                    state.saveState.committing = true
                    try { withContext(Dispatchers.IO) { commitSaveRequest(request, bytes) } }
                    finally { state.saveState.committing = false }
                },
                onFailure = { Saved(null, it) },
            )
            val ownsResult = request.tab.document === request.document
            if (ownsResult) {
                when {
                    result.document != null -> {
                        state.noteSaved(request.tab)
                        request.tab.adoptSaved(result.document, result.failure != null)
                    }
                    result.failure == null && result.kept -> {
                        if (!result.stillDirty) state.noteSaved(request.tab)
                        request.tab.notePublished(dirty = result.stillDirty)
                    }
                }
            } else {
                result.document?.close()
            }
            state.saveState.request = null
            state.failure = result.failure?.let(::describeSaveFailure)
            if (result.failure == null && ownsResult) request.completed()
        }
    }
}

/**
 * A save in progress, said **on** the page rather than instead of it: one line in the accent, no scrim and
 * no second surface. The document stays readable and editable while the bytes are written — the window used
 * to swap the whole workspace for a placeholder, which read as a crash and threw every canvas away.
 */
@Composable internal fun BoxScope.SavingBadge() {
    Text(
        text = "Saving...",
        color = DesktopTheme.accent,
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
    )
}

/**
 * Swallow input while the staged bytes are **put in place**. It is bound to `committing`, not to the whole
 * save: the encoding is a read-only view of an immutable snapshot, so a stroke during it is safe, while the
 * commit either releases an archive or replaces the file the tab is reading.
 */
internal fun Modifier.saveInputGate(saving: Boolean): Modifier = onPreviewKeyEvent { saving }.pointerInput(saving) {
    if (saving) awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
