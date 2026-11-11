package com.mobixournal

import android.net.Uri
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.SaveFormat
import com.mobixournal.io.pdfNameFor
import com.mobixournal.io.saveNameFor
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.tabs.PendingSave
import com.mobixournal.panes.EditorPane
import java.io.File

/**
 * Handle a URI returned from the system document picker. If the active tab is not yet hydrated, defer
 * saving until it has finished loading onto the canvas.
 */
internal fun MainActivity.handleSaveResult(uri: Uri?) {
    savePickerState = savePickerState.answered(uri?.toString())
    if (uri == null) {
        return
    }
    flushPendingSave()
}

/** Resolve the original picker owner after restoration, never the currently focused tab. */
internal fun MainActivity.flushPendingSave() {
    val uri = pendingSaveUri ?: return
    val request = pendingSaveRequest ?: captureSave() ?: return
    val owner = panes.getOrNull(request.paneIndex)
    val tab = owner?.let { request.tabIn(it.tabs) }
    if (tab != null && (!tab.hydrated || tab.opening)) return
    pendingSaveUri = null
    pendingSaveRequest = null
    if (tab == null) {
        toast("Save cancelled: the original tab is no longer open")
        return
    }
    saveDocument(uri, request)
}

private fun MainActivity.captureSave(format: SaveFormat = saveFormat, name: String = pendingSaveName): PendingSave? =
    tabs.active?.let { PendingSave(panes.indexOf(pane), it.id, format, name) }

/**
 * Write the current document to [uri] in the sticky [saveFormat]. Both formats are single files,
 * so both go through the plain CreateDocument picker:
 * - [SaveFormat.ORIGINAL] — gzip XML, PDF background left linked by path/URI (interchange-safe).
 * - [SaveFormat.ZIPPED] — a ZIP package with the PDF embedded (`domain="attach"`, `bg.pdf`).
 */
internal fun MainActivity.saveDocument(uri: Uri, request: PendingSave? = captureSave()) {
    request ?: return
    val owner = panes.getOrNull(request.paneIndex) ?: return
    snapshotActiveTab(owner)
    val tab = request.tabIn(owner.tabs) ?: return
    if (!tab.hydrated || tab.opening || tab.isReadOnly) return
    val view = owner.surface?.takeIf { owner.tabs.active?.id == tab.id }
    val snapshot = view?.toDocument() ?: tab.document
    val document = if (request.format == SaveFormat.MOBIX) {
        val identified = io.identifyForSave(snapshot, uri)
        view?.adoptIdentity(identified) ?: identified
    } else snapshot
    owner.tabs.updateMatching({ it.id == tab.id }) { it.copy(document = document) }
    val pdf = view?.pdfSourceFile() ?: tab.pdfPath?.let(::File)
    val images = (view?.imageSources() ?: tab.backgrounds?.images ?: tab.images).toMap()
    // The canvas reading the bytes are taken at: `afterSaved` compares it with the canvas it finds, which is
    // how it knows whether a stroke landed after the snapshot.
    val revision = view?.edits ?: -1
    inBackground("Saving ${displayName(uri)}.", {
        val encoded = io.encode(document, pdf, request.format, uri, images, audio::recordingFile)
        try { io.stageOut(encoded.file, uri) } finally { encoded.file.delete() }
        encoded.uncompiled
    }) { result ->
        result.onFailure { toast("Save failed: ${it.message}") }.onSuccess { uncompiled ->
            afterSaved(owner, request, uri, document, revision)
            if (uncompiled.isNotEmpty()) exportGaps.value = uncompiled
        }
    }
}

/** Commit a successful save only to the tab that requested it. */
private fun MainActivity.afterSaved(
    owner: EditorPane,
    request: PendingSave,
    uri: Uri,
    document: com.mobixournal.format.model.Document,
    revision: Int,
) {
    io.persist(uri)
    val title = displayName(uri)
    // A stroke can land while the bytes are being written: the file then holds the snapshot, not the page.
    // The tab says so exactly as the window does — it stays unsaved (the strip's dot) and the session keeps
    // the newer document, because `snapshotActiveTab` below writes the live canvas back into the record.
    val showing = owner.surface?.takeIf { owner.tabs.active?.id == request.tabId }
    val newer = showing != null && showing.edits != revision
    owner.tabs.updateMatching({ it.id == request.tabId }) {
        it.copy(
            title = title,
            uri = uri.toString(),
            format = request.format,
            unsavedEdits = newer,
            savedAt = if (showing != null && !newer) showing.edits else it.savedAt,
        )
    }
    if (owner.tabs.active?.id == request.tabId) {
        owner.saveFormat = request.format
        owner.pendingSaveName = title
        snapshotActiveTab(owner)
    }
    tabsTick.value++
    owner.persist()
    prunePdfCache()
    if (request.format != SaveFormat.MOBIX) {
        audioFolder?.let { audio.exportSidecars(it, document) }
        if (audioFolder == null && documentAudioFiles(document).isNotEmpty()) {
            toast("Choose an audio folder so recordings are saved beside this file")
        }
    }
}

/**
 * Plain Save: write straight back to the file this tab came from when we still hold a write
 * grant on it (the normal case for a document opened from local storage *or* a mounted remote
 * share), and only fall back to asking for a location when we don't.
 */
internal fun MainActivity.saveActiveTab() {
    if (busy.value != null && busy.value?.startsWith("Saving") == true) return
    if (tabs.isEmpty || tabs.active == null || tabs.active?.hydrated == false || tabs.active?.opening == true) return
    if (tabs.active?.isReadOnly == true || surface?.isReadOnly == true) {
        toast("Document is read-only")
        return
    }
    snapshotActiveTab()
    val target = tabs.active?.uri?.let(Uri::parse)?.takeIf(io::isWritable)
    if (target != null) saveDocument(target) else launchSavePicker()
}

/**
 * Ask the system where to write the open document, with the picker that matches the sticky format:
 * the suggested name and the declared type both come from it, so a document that lives as a `.mobix`
 * is proposed as `notes.mobix` and typed as one, instead of being offered to desktop Xournal++ as a
 * file it cannot open.
 *
 * Both pickers write through [saveDocument], which is what makes the format the *document's* sticky
 * choice rather than the picker's: the launcher only decides the name and the type the system sees.
 */
internal fun MainActivity.launchSavePicker(format: SaveFormat = saveFormat, name: String = suggestedSaveName()) {
    snapshotActiveTab()
    pane.persist()
    savePickerState = com.mobixournal.tabs.SavePickerState(captureSave(format, name) ?: return)
    if (format == SaveFormat.MOBIX) saveMobixLauncher.launch(name) else saveLauncher.launch(name)
}

/**
 * The name a Save should propose for the open document: whatever it is called, forced to end in the
 * suffix of the format it is saved in.
 *
 * The name is not always a well-formed one — a document can be opened under a name with no extension
 * at all, or with someone else's (a plain-text file typeset into a document arrives as `notes.txt`)
 * — and asking the picker for that name verbatim writes a file neither the system nor desktop
 * Xournal++ recognises as one.
 */
internal fun MainActivity.suggestedSaveName(): String = saveNameFor(pendingSaveName, saveFormat)

/**
 * The name a PDF export should propose: the open document's own name with `.pdf` in its place, so an
 * export lands beside the document it came from instead of under a generic name.
 */
internal fun MainActivity.suggestedPdfName(): String = pdfNameFor(pendingSaveName)

/** True when the open document references any recording at all. */
internal fun MainActivity.documentHasAudio(view: DrawingSurfaceView): Boolean =
    documentAudioFiles(view.toDocument()).isNotEmpty()

/**
 * Stage a Save As choice without changing the document until its write succeeds. Both formats write one file.
 */
internal fun MainActivity.beginSaveAs(filename: String, format: SaveFormat) {
    launchSavePicker(format, saveNameFor(filename, format))
}

internal fun MainActivity.restorePendingSaveState(savedInstanceState: android.os.Bundle?) {
    pendingSaveUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        savedInstanceState?.getParcelable(STATE_PENDING_SAVE_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        savedInstanceState?.getParcelable(STATE_PENDING_SAVE_URI)
    }
    savedInstanceState?.getString("save.tab")?.let { id ->
        val format = savedInstanceState.getString("save.format")?.let {
            runCatching { com.mobixournal.format.SaveFormat.valueOf(it) }.getOrNull()
        }
        if (format != null) pendingSaveRequest = com.mobixournal.tabs.PendingSave(
            savedInstanceState.getInt("save.pane"), id, format,
            savedInstanceState.getString("save.name").orEmpty(),
        )
    }
    savedInstanceState?.getString(STATE_PENDING_SAVE_NAME)?.let { pendingSaveName = it }
}


internal fun MainActivity.writePendingSaveState(outState: android.os.Bundle) {
        pendingSaveUri?.let { outState.putParcelable(STATE_PENDING_SAVE_URI, it) }
        outState.putString(STATE_PENDING_SAVE_NAME, pendingSaveName)
        pendingSaveRequest?.let {
            outState.putInt("save.pane", it.paneIndex)
            outState.putString("save.tab", it.tabId)
            outState.putString("save.format", it.format.name)
            outState.putString("save.name", it.name)
        }
}

private const val STATE_PENDING_SAVE_URI = "editor.pendingSaveUri"
private const val STATE_PENDING_SAVE_NAME = "editor.pendingSaveName"
