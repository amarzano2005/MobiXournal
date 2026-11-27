package com.mobixournal

import android.net.Uri
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.SaveFormat
import com.mobixournal.io.SaveActivity
import com.mobixournal.io.pdfNameFor
import com.mobixournal.io.saveNameFor
import com.mobixournal.portability.PortableCopy
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.tabs.PendingSave
import com.mobixournal.panes.EditorPane
import java.io.File

/**
 * The internal marker for a write in flight. It is **not** copy: nothing draws it, because the Save
 * control's own arc already says the document is being written (`SaveActivity`, `ui/SaveControl.kt`). It
 * stands in [MainActivity.busy] only so the guards that read "is something being written?" keep working —
 * the back press stays refused for the length of a save, and auto save does not start a second write.
 */
internal const val SAVING_MARKER = "Saving"

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
/**
 * Write the document to [uri].
 *
 * [quiet] is auto save's door: same snapshot, same encoder, same worker — the one difference is that a
 * refusal a reader-pressed save can toast is not said for a write nobody asked for
 * (`docs/ui-modernization.md`).
 */
internal fun MainActivity.saveDocument(uri: Uri, request: PendingSave? = captureSave(), quiet: Boolean = false) {
    request ?: return
    // A portable copy is a hand-off file, never written in place: a Save As answered with a `.pmobix`
    // name is refused here rather than turning the export into a notebook someone keeps editing.
    if (PortableCopy.isPortableCopy(displayName(uri))) {
        if (!quiet) toast("This is a portable copy, made to hand on: save a working .mobix instead.")
        return
    }
    val owner = panes.getOrNull(request.paneIndex) ?: return
    snapshotActiveTab(owner)
    val tab = request.tabIn(owner.tabs) ?: return
    if (!tab.hydrated || tab.opening || tab.isReadOnly) return
    val view = owner.surface?.takeIf { owner.tabs.active?.id == tab.id }
    val snapshot = view?.toDocument() ?: tab.document
    val pdf = view?.pdfSourceFile() ?: tab.pdfPath?.let(::File)
    val embedPdf = view?.embedPdf ?: tab.embedPdf
    val effectivePdf = if (request.format == SaveFormat.MOBIX && !embedPdf) null else pdf
    val images = (view?.imageSources() ?: tab.backgrounds?.images ?: tab.images).toMap()
    val exportAudioFolder = if (request.format != SaveFormat.MOBIX) audioFolder else null
    // The canvas reading the bytes are taken at: `afterSaved` compares it with the canvas it finds, which is
    // how it knows whether a stroke landed after the snapshot.
    val revision = view?.edits ?: -1
    val audioRevision = finalizedAudioVersion
    // The top bar's Save control turns while this runs, whether or not it was asked for: the arc is the
    // only thing a save draws, and [busy] carries a marker for the guards rather than text for the page
    // (`SaveActivity`, `docs/data-path.md`).
    SaveActivity.begin()
    inBackground(SAVING_MARKER, {
        val rawIdentified = if (request.format == SaveFormat.MOBIX) io.identifyForSave(snapshot, uri) else snapshot
        val identified = if (request.format == SaveFormat.MOBIX && !embedPdf) {
            com.mobixournal.io.DocumentEncoder.stripBundledPdfAssets(rawIdentified)
        } else {
            rawIdentified
        }
        // The take being recorded right now is not bundleable — its file is still growing under the
        // content hash the entry would be named by ([AudioSession.bundleableRecording]).
        val encoded = io.encode(identified, effectivePdf, request.format, uri, images, audio::bundleableRecording)
        // Prove the bytes before a SAF copy can replace the document; a refusal keeps the original.
        if (request.format == SaveFormat.MOBIX) {
            com.mobixournal.format.mobix.MobixPublishValidation.validate(encoded.file, BuildConfig.VERSION_NAME)
        }
        val verified = try { io.stageOut(encoded.file, uri) } finally { encoded.file.delete() }
        exportAudioFolder?.let { audio.exportSidecars(it, identified) }
        Triple(identified, encoded.uncompiled, verified)
    }) { result ->
        SaveActivity.end()
        result.onFailure { toast("Save failed: ${it.message}") }.onSuccess { (identified, uncompiled, verified) ->
            afterSaved(owner, request, uri, snapshot, identified, revision, audioRevision)
            if (uncompiled.isNotEmpty()) exportGaps.value = uncompiled
            if (!verified) {
                toast("The file was written but could not be read back: the save may be incomplete.")
            }
        }
    }
}

/** Commit a successful save only to the tab that requested it. */
private fun MainActivity.afterSaved(
    owner: EditorPane,
    request: PendingSave,
    uri: Uri,
    snapshot: com.mobixournal.format.model.Document,
    identified: com.mobixournal.format.model.Document,
    revision: Int,
    audioRevision: Int,
) {
    io.persist(uri)
    val title = displayName(uri)
    // A stroke or finalized audio take can land while the bytes are being written: the file then holds
    // the snapshot, not the live state. The tab stays unsaved (the strip's dot) and keeps the newer state.
    val showing = owner.surface?.takeIf { owner.tabs.active?.id == request.tabId }
    val newer = (showing != null && showing.edits != revision) || finalizedAudioVersion != audioRevision
    if (request.format == SaveFormat.MOBIX && identified !== snapshot && showing != null && showing.edits == revision) {
        showing.adoptIdentity(identified)
    }
    owner.tabs.updateMatching({ it.id == request.tabId }) {
        it.copy(
            title = title,
            uri = uri.toString(),
            format = request.format,
            document = if (!newer) identified else it.document,
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
    // A plain `.xopp` names its background PDF by path, and the sweep below keeps only what a live tab
    // still refers to: the file this save wrote into the document is pinned first, so closing the tab
    // does not take the background out from under the document on disk.
    if (request.format == SaveFormat.ORIGINAL) io.retainLinkedPdf(identified)
    prunePdfCache()
    if (request.format != SaveFormat.MOBIX && audioFolder == null && documentAudioFiles(identified).isNotEmpty()) {
        toast("Choose an audio folder so recordings are saved beside this file")
    }
}

/**
 * Plain Save: write straight back to the file this tab came from when we still hold a write
 * grant on it (the normal case for a document opened from local storage *or* a mounted remote
 * share), and only fall back to asking for a location when we don't.
 */
internal fun MainActivity.saveActiveTab() {
    if (SaveActivity.saving) return
    if (busy.value != null && busy.value?.startsWith("Saving") == true) return
    if (tabs.isEmpty || tabs.active == null || tabs.active?.hydrated == false || tabs.active?.opening == true) return
    if (tabs.active?.isReadOnly == true || surface?.isReadOnly == true) {
        toast("Document is read-only")
        return
    }
    // The opened file may be a portable copy: Save must not write the export back, so it opens the
    // Save As picker on a working `.mobix` name — the same rule the window follows.
    val name = tabs.active?.uri?.let(Uri::parse)?.let { displayName(it) }.orEmpty()
    if (PortableCopy.isPortableCopy(name)) {
        toast("This is a portable copy: choose where to keep working as a .mobix")
        launchSavePicker(SaveFormat.MOBIX, saveNameFor(pendingSaveName, SaveFormat.MOBIX))
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
