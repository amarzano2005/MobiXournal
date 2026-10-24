package com.mobixournal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.AudioSession
import com.mobixournal.format.SaveFormat
import com.mobixournal.io.DocumentIo
import com.mobixournal.io.IncomingDocument
import com.mobixournal.io.MOBIX_MIME
import com.mobixournal.io.UriStaging
import com.mobixournal.io.XOPP_MIME
import com.mobixournal.panes.EditorPane
import com.mobixournal.panes.MirrorSync
import com.mobixournal.io.rememberBoardShape
import com.mobixournal.tabs.MobixTabSnapshots
import com.mobixournal.tabs.SnapshotSources
import com.mobixournal.render.BitmapBudget
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.Uncompilable
import com.mobixournal.render.ImportPdfMode
import com.mobixournal.render.KaraokeFrameDriver
import com.mobixournal.render.PdfFonts
import com.mobixournal.render.Placement
import com.mobixournal.render.TextPdfGenerator
import com.mobixournal.render.cancelSpline
import com.mobixournal.render.finishSpline
import com.mobixournal.render.onStylusButtonKey
import com.mobixournal.render.splineInProgress
import com.mobixournal.render.undoLastSplineNode
import com.mobixournal.tabs.TabManager
import com.mobixournal.tabs.TabStore
import com.mobixournal.ui.AppSettings
import com.mobixournal.ui.EditorScreen
import com.mobixournal.ui.SettingsStore
import com.mobixournal.ui.ThemeMode
import com.mobixournal.ui.theme.XoppTheme
import com.mobixournal.ui.theme.isDark
import java.io.File

/**
 * Hosts the editor and bridges the Storage Access Framework to the `.xopp` I/O layer: open a
 * document in place, edit on the [DrawingSurfaceView], save back to the same format. The file on
 * disk is the only source of truth (see `AGENTS.md` non-goals).
 */
class MainActivity : ComponentActivity() {

    /**
     * The two editing panes. Only the first is shown until the user turns on split view, at which
     * point the second gets its own canvas and its own restored tab session (see [EditorPane]).
     */
    internal val panes: List<EditorPane> by lazy {
        TABS_DIRS.map {
            // Each pane's session cache is written in the shape of the document it holds: a `.mobix`
            // tab snapshots into its own container, which is the only shape that can carry the bundled
            // picture or recording its pages name ([MobixTabSnapshots]).
            EditorPane(
                store = TabStore(File(filesDir, it), MobixTabSnapshots(io, audio::recordingFile)),
                // The canvas holds an imported picture or an edited board's grid; the document I/O
                // layer knows what to do with them. Both are asked at snapshot time, for the document
                // that is actually on the canvas.
                sourcesOf = { view ->
                    SnapshotSources(
                        pdf = view.pdfSourceFile(),
                        images = view.imageSources(),
                        rememberBoardShape = { doc -> io.rememberBoardShape(doc, view.canvasGeometry()) },
                    )
                },
            )
        }
    }

    /** Keeps two views of one mirrored document in step across the panes. */
    internal val mirrors: MirrorSync by lazy { MirrorSync(panes) }

    /** Which pane every menu/toolbar action applies to — the one last touched. */
    internal var activePane = mutableStateOf(0)

    /** Whether the editor is showing both panes side by side. */
    internal var splitView = mutableStateOf(false)

    /** The pane in focus. Everything below is written against this one document. */
    internal val pane: EditorPane get() = panes[activePane.value.coerceIn(panes.indices)]

    internal val surface: DrawingSurfaceView? get() = pane.surface

    /** Where a pending image-insert tap landed, kept until the SAF picker returns the image bytes. */
    internal var pendingImagePlacement: Placement? = null

    /** The mode chosen in the Import PDF dialog, kept until the SAF picker returns the PDF. */
    private var pendingImportMode: ImportPdfMode = ImportPdfMode.REPLACE

    /** The file name last chosen in the Save As dialog; reused by plain Save. Per pane. */
    internal var pendingSaveName: String
        get() = pane.pendingSaveName
        set(value) { pane.pendingSaveName = value }

    /** True while open tabs are being restored asynchronously from TabStore. */
    internal var isRestoringTabs: Boolean = false

    /** A save URI waiting for tabs restoration to finish before landing on disk. */
    internal var pendingSaveUri: Uri? = null

    /**
     * The sticky save format. "Save As" sets it; every later plain Save reuses it, so once you save
     * ZIPPED once, Save keeps writing ZIPPED. Opening a document adopts the format it was stored in.
     */
    internal var saveFormat: SaveFormat
        get() = pane.saveFormat
        set(value) { pane.saveFormat = value }

    /** Recording, playback and sidecar transfer for audio-annotated strokes (`fn`/`ts`). */
    internal val audio: AudioSession by lazy { AudioSession(this) }

    /** Persists [AppSettings]; also the home of the nominated audio folder grant. */
    internal val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    /**
     * The folder sidecar `.wav` files are kept in — a persisted `OpenDocumentTree` grant, normally
     * the folder the user's `.xopp` files live in. Null until they nominate one, in which case audio
     * still records and plays but never leaves the app (see [AudioSession]).
     */
    internal var audioFolder: Uri? = null

    /** Bumped whenever recording/playback state changes, so the Compose chrome re-reads it. */
    internal var audioTick = mutableStateOf(0)

    /** Whether the floating frosted-glass audio mini player is visible. */
    internal var miniPlayerVisible = mutableStateOf(false)

    /**
     * The frame loop following the clip that is playing ([KaraokeFrameDriver]), or null when none is: it
     * publishes the playhead onto the surface the recording was started from, and stops when the clip
     * ends or the editor goes away (`MainActivityAudio.detachAudio`).
     */
    internal var karaokeDriver: KaraokeFrameDriver? = null

    /** Active speech-to-text transcription associated with the open document or current recording. */
    internal val activeTranscription = mutableStateOf<com.mobixournal.audio.AudioTranscription?>(null)

    /** The job transcribing the recording that is being made, if one is running ([beginLiveTranscription]). */
    internal var liveTranscription: com.mobixournal.audio.LiveTranscription? = null

    /** The PCM listener feeding [liveTranscription], kept so it can be unhooked by identity. */
    internal var livePcmListener: ((ByteArray, Int, Int) -> Unit)? = null

    /** The encoder producing the standalone `.m4a` while this recording runs ([beginStandaloneExport]). */
    internal var standaloneTap: com.mobixournal.audio.StandaloneAudioTap? = null

    /** Whether speech-to-text deferred transcription is currently running in background. */
    internal val isTranscribing = mutableStateOf(false)

    /** Status message for deferred transcription, displayed inline in the player. */
    internal val transcriptionStatus = mutableStateOf<String?>(null)

    /** The most recent audio recording made or referenced, used for autonomous playback fallback. */
    internal var lastAudioRef: AudioRef? = null

    /** Audio recordings created per-tab during this session, mapped by tab id. */
    internal val tabAudioRefs = mutableMapOf<String, AudioRef>()

    /** The duration in ms of the most recent audio recording played or referenced. */
    internal var lastDurationMs: Int? = null

    /** On-device speech recognition engine active during recording sessions. */
    internal var speechEngine: com.mobixournal.audio.SpeechToTextEngine? = null

    /** The active pane's open documents and which one is showing (see `com.mobixournal.tabs`). */
    internal val tabs: TabManager get() = pane.tabs

    /** Bumped whenever a tab list or selection changes, so the tab strips re-read them. */
    internal var tabsTick = mutableStateOf(0)

    /**
     * What the last `.xopp` write could not draw ([XoppSynthesis]): an element kind this build has no
     * drawing for, a formula that would not evaluate, markup from a newer build with no `.xopp` spelling.
     * The editor shows it once and clears it, and empty is the normal case — a save that lost nothing.
     */
    internal val exportGaps = mutableStateOf<List<Uncompilable>>(emptyList())

    /** Pending consent to open a .mobix document that requires a newer app version than this build. */
    internal val pendingNewerAppConsent = mutableStateOf<PendingConsent?>(null)

    /**
     * The single worker every tab-overview preview is queued on, so a grid of tabs parses and
     * rasterises one document at a time instead of all of them at once (see [previewTabPage]).
     */
    internal val previewWorker: java.util.concurrent.ExecutorService by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor()
    }

    /**
     * All document I/O policy — staging, the background-PDF stores, and the read/encode/merge steps
     * (see [DocumentIo]). The activity keeps only the intent plumbing and the canvas wiring.
     */
    internal val io: DocumentIo by lazy {
        // Plain text defaults to monospace — the sensible face for the logs and source it usually
        // holds; markdown picks its own faces per run style (see MarkdownPdfWriter).
        val fonts = PdfFonts(assets)
        DocumentIo(
            resolver = contentResolver,
            cacheDir = cacheDir,
            filesDir = filesDir,
            // The build's own version is what a `.mobix` records as `minAppVersion`.
            appVersion = BuildConfig.VERSION_NAME,
            textPdf = TextPdfGenerator(fonts::load),
            // A `.mobix` open adopts its bundled recordings into the same store the recorder writes
            // to, under the name the stroke carries ([BundledAudio]).
            audio = audio,
        )
    }

    /** Shared staging helper for URI byte transfers (export, image insert). */
    internal val staging: UriStaging by lazy { UriStaging(contentResolver, File(cacheDir, "staging")) }

    /** What long-running transfer is in flight, or null. Drives the editor's blocking progress note. */
    internal var busy = mutableStateOf<String?>(null)

    /** In-app frosted glass toast notification message, or null. */
    internal val glassToast = mutableStateOf<String?>(null)

    /** Pending callback to start recording after user consents to overwrite existing audio. */
    internal val pendingAudioOverwriteConsent = mutableStateOf<(() -> Unit)?>(null)

    /** Existing audio clip name on the active page/tab prompting for overwrite consent. */
    internal val pendingAudioOverwriteClip = mutableStateOf<String?>(null)

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { insertPickedImage(it) }
        }

    /**
     * The document picker, asking for a **persistable read+write** grant rather than the default
     * one-shot read. That grant is what lets a later plain Save write straight back to the file the
     * document came from — including one on a mounted network share — and lets a restored tab still
     * reach it after a restart.
     */
    private class OpenDocumentForEditing : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
    }

    private val openLauncher =
        registerForActivityResult(OpenDocumentForEditing()) { uri ->
            uri?.let { openDocument(it) }
        }

    internal val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(XOPP_MIME)) { uri ->
            uri?.let { handleSaveResult(it) }
        }

    /**
     * The picker for a save in this app's **own** format. A separate launcher because the type a
     * picker declares is fixed when it is registered, and it is the type that decides both the
     * suggested name the system keeps and how the saved file is typed for other apps — a `.mobix`
     * written as `application/x-xopp` would be offered to desktop Xournal++, which cannot open it.
     */
    internal val saveMobixLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MOBIX_MIME)) { uri ->
            uri?.let { handleSaveResult(it) }
        }

    private val importPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importPdf(it, pendingImportMode) }
        }

    internal val audioFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(::adoptAudioFolder)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && com.mobixournal.audio.AudioRecordingService.isRunning) {
                com.mobixournal.audio.AudioRecordingService.start(this)
            }
        }

    internal val recordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) beginRecording() else toast("Recording needs microphone permission")
        }

    internal fun requestNotificationPermissionIfNecessary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val exportPdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME)) { uri ->
            uri?.let { exportPdf(it) }
        }

    private val exportPagePngLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(PNG_MIME)) { uri ->
            uri?.let { exportPagePng(it) }
        }

    private val exportPageSvgLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(SVG_MIME)) { uri ->
            uri?.let { exportPageSvg(it) }
        }

    /**
     * Hardware-keyboard shortcuts for the spline tool, which is the one tool whose gesture spans
     * several taps: Enter commits the open curve, Backspace drops its last control point, and Escape
     * throws it away. Handled here rather than in the surface so the canvas never has to take
     * keyboard focus away from the app's text fields.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val view = surface
        // A stylus barrel button can arrive as a key event rather than a motion button — Android 14+
        // reports Bluetooth stylus buttons that way — and the canvas only takes focus on touch, so the
        // activity routes it to the surface, which treats it as the held barrel modifier.
        if (view != null && view.onStylusButtonKey(event)) return true
        if (event.action == KeyEvent.ACTION_UP && view != null && view.splineInProgress()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { view.finishSpline(); return true }
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL ->
                    { view.undoLastSplineNode(); return true }
                KeyEvent.KEYCODE_ESCAPE -> { view.cancelSpline(); return true }
            }
        }
        // Let focused Compose components (e.g. text inputs in dialogs/settings) consume keys first.
        if (super.dispatchKeyEvent(event)) return true
        // If unconsumed by Compose, route to the active drawing surface for tool and color shortcuts.
        if (view != null && view.dispatchKeyEvent(event)) return true
        return false
    }

    private fun setupThemeAndWindow(initialSettings: AppSettings) {
        when (initialSettings.themeMode) {
            ThemeMode.LIGHT -> setTheme(R.style.Theme_MobiXournal_Light)
            ThemeMode.DARK -> setTheme(R.style.Theme_MobiXournal_Dark)
            ThemeMode.SYSTEM -> setTheme(R.style.Theme_MobiXournal)
        }
        MobiXournalApp.applyThemeMode(this, initialSettings.themeMode)
        window.setBackgroundDrawableResource(
            if (isDarkTheme(initialSettings)) R.drawable.splash_screen_dark else R.drawable.splash_screen_light,
        )
    }

    /** Whether [settings]' theme choice paints dark right now — `SYSTEM` defers to the OS setting. */
    private fun isDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
        ThemeMode.SYSTEM ->
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /**
     * Ask the picker for a PDF to import, remembering how — the launcher answers on a later turn and
     * reads the mode back then ([pendingImportMode]).
     */
    private fun startPdfImport(mode: ImportPdfMode) {
        pendingImportMode = mode
        importPdfLauncher.launch(arrayOf(PDF_MIME))
    }

    /**
     * Draw the whole screen, under both system bars: the canvas is the document, so a bar-shaped band
     * of window background along any edge would read as part of the page. Both bars are transparent
     * and their contrast scrims are off, so only their contents (clock, gesture handle) remain.
     */
    private fun applyEdgeToEdge(isDark: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { isDark },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun initSubsystems(initialSettings: AppSettings) {
        PDFBoxResourceLoader.init(applicationContext)
        BitmapBudget.configure(applicationContext)
        com.mobixournal.render.MlKitInkEngine.ensureModelDownloaded()
        audioFolder = initialSettings.audioFolderUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        audio.onStateChanged = { runOnUiThread { audioTick.value++ } }
        takeIncoming(intent)
        requestNotificationPermissionIfNecessary()
    }

    private fun confirmAudioOverwriteConsent() {
        val consent = pendingAudioOverwriteConsent.value
        pendingAudioOverwriteClip.value = null
        pendingAudioOverwriteConsent.value = null
        consent?.invoke()
    }

    private fun dismissAudioOverwriteConsent() {
        pendingAudioOverwriteClip.value = null
        pendingAudioOverwriteConsent.value = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val store = settingsStore
        val initialSettings = store.load()
        setupThemeAndWindow(initialSettings)

        super.onCreate(savedInstanceState)
        restoreSplitWorkspace(savedInstanceState)
        applyEdgeToEdge(isDarkTheme(initialSettings))
        initSubsystems(initialSettings)
        // Whatever the last session could not finish (a standalone export, a transcription the user
        // closed the app on) is picked up here — closing after a lecture must never mean losing it.
        resumePendingAudioWork()
        showEditor(initialSettings, store)
    }

    /** The one window: the editor over the settings store, wired to every launcher this activity owns. */
    private fun showEditor(initialSettings: AppSettings, store: SettingsStore) {
        setContent {
            var settings by remember {
                mutableStateOf(initialSettings.also(::applySettingsToLayers))
            }
            XoppTheme(darkTheme = settings.themeMode.isDark(), dynamicColor = settings.dynamicColor) {
                    EditorScreen(
                    onOpen = { openLauncher.launch(arrayOf("*/*")) },
                    onSave = { saveActiveTab() },
                    busy = busy.value,
                    onExit = { finish() },
                    onSaveAs = { name, format -> beginSaveAs(name, format) },
                    currentSaveFormat = { saveFormat },
                    currentSaveName = { suggestedSaveName() },
                    onImportPdf = ::startPdfImport,
                    onExportPdf = { exportPdfLauncher.launch(suggestedPdfName()) },
                    exportGaps = exportGaps.value,
                    onDismissExportGaps = { exportGaps.value = emptyList() },
                    pendingNewerAppConsentName = pendingNewerAppConsent.value?.name,
                    onConfirmNewerAppConsent = {
                        val pending = pendingNewerAppConsent.value
                        pendingNewerAppConsent.value = null
                        pending?.onConfirm?.invoke()
                    },
                    onDismissNewerAppConsent = {
                        val pending = pendingNewerAppConsent.value
                        pendingNewerAppConsent.value = null
                        pending?.onDismiss?.invoke()
                    },
                    pendingAudioOverwriteClip = pendingAudioOverwriteClip.value,
                    onConfirmAudioOverwrite = ::confirmAudioOverwriteConsent,
                    onDismissAudioOverwrite = ::dismissAudioOverwriteConsent,
                    glassToast = glassToast.value,
                    onDismissToast = { glassToast.value = null },
                    onExportPagePng = { exportPagePngLauncher.launch(suggestedPageImageName("png")) },
                    onExportPageSvg = { exportPageSvgLauncher.launch(suggestedPageImageName("svg")) },
                    onPickImage = { placement ->
                        pendingImagePlacement = placement
                        pickImageLauncher.launch(arrayOf("image/*"))
                    },
                    onCanvasModeForDocument = { mode, extentPt -> applyCanvasModeToDocument(mode, extentPt) },
                    onSurfaceCreated = { index, view ->
                        val p = panes[index]
                        p.surface = view
                        view.onReadOnlyBlocked = { toast(getString(R.string.document_read_only)) }
                        // A board has no pages, so the page-shaped gestures are refused on the canvas
                        // itself ([DrawingSurfaceView.onBoardBlocked]): say so rather than letting the
                        // gesture look broken.
                        view.onBoardBlocked = { toast(getString(R.string.infinite_canvas_only_drawing)) }
                        // The board's own shape rides with the document: a chunked document's cells are
                        // not derivable from its pages, so a mirrored pane of an infinite canvas has to
                        // be handed the grid the editing pane holds (`DrawingSurfaceView.canvasGeometry`).
                        view.onDocumentEdited = { doc -> mirrors.propagate(p, doc, view.canvasGeometry()) }
                        // A PDF dropped on the page joins the open document (the drop's own gesture is
                        // "put this here", not "replace everything I have").
                        view.onExternalPdf = { uri -> importPdf(uri, ImportPdfMode.APPEND) }
                        attachAudio(view)
                        // Restore is asynchronous now, so a file handed to us by another app is opened
                        // once the session is back — otherwise it would be shoved aside by the restore.
                        restoreTabs(p) {
                            openIncoming()
                            flushPendingSave()
                        }
                    },
                    settings = settings,
                    onSettingsChange = {
                        if (settings.themeMode != it.themeMode) {
                            MobiXournalApp.applyThemeMode(this@MainActivity, it.themeMode)
                            window.setBackgroundDrawableResource(
                                if (isDarkTheme(it)) R.drawable.splash_screen_dark else R.drawable.splash_screen_light,
                            )
                        }
                        settings = it
                        store.save(it)
                        applySettingsToLayers(it)
                    },
                    audio = audioUiState(),
                    // Page bookmarks are kept per document, keyed by the tab's identity (see
                    // PageBookmarks) — never in the file, which has no page label to hold them.
                    bookmarkKey = panes.getOrNull(activePane.value)?.tabs?.active?.docKey.orEmpty(),
                    tabs = panes.map(::tabsUiState),
                    splitView = splitView.value,
                    onToggleSplitView = ::toggleSplitView,
                    activePane = activePane.value,
                    onActivePane = { activePane.value = it },
                )
            }
        }
    }

    // --- handovers from other apps --------------------------------------------------------------

    /**
     * A document another app handed us that hasn't been opened yet. The intent lands before the
     * canvas exists, so the URI waits here until the surface is up and the restored tabs are back.
     */
    private var pendingIntentUri: Uri? = null

    /**
     * A second handover while we're already running (the app was picked from a share/open sheet
     * without having been killed). Same routing as the cold start; the tab strip grows by one.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeIncoming(intent)
        // Already running, so the surface exists — no need to wait for onSurfaceCreated.
        openIncoming()
    }

    /** Stash the document URI [intent] is handing over, if it is handing one over at all. */
    private fun takeIncoming(intent: Intent?) {
        val stream = @Suppress("DEPRECATION") intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val chosen = IncomingDocument.uriString(
            action = intent?.action,
            data = intent?.data?.toString(),
            stream = stream?.toString(),
        ) ?: return
        pendingIntentUri = Uri.parse(chosen)
    }

    /**
     * Open the handed-over document, once. Called after the surface is live (cold start) or straight
     * away (already running); clearing the field first keeps a re-entrant call from double-opening.
     */
    private fun openIncoming() {
        val uri = pendingIntentUri ?: return
        pendingIntentUri = null
        openDocument(uri)
    }

    /**
     * Run [work] off the UI thread behind a blocking progress note, then hand its result to [done]
     * back on the UI thread. Every document transfer goes through here: a remote share can stall for
     * seconds, and doing that inline would freeze (and eventually kill) the app.
     */
    internal fun <T> inBackground(label: String, work: () -> T, done: (Result<T>) -> Unit) {
        busy.value = label
        Thread {
            val result = runCatching(work)
            runOnUiThread {
                busy.value = null
                done(result)
            }
        }.start()
    }


    /**
     * Bring back the split workspace saved by [onSaveInstanceState]: a workspace that was split when
     * the process was killed comes back split, on the same half. The divider's fraction is still
     * view-only and restarts at 50/50.
     */
    private fun restoreSplitWorkspace(savedInstanceState: Bundle?) {
        splitView.value = savedInstanceState?.getBoolean(STATE_SPLIT_VIEW) == true
        activePane.value = savedInstanceState
            ?.getInt(STATE_ACTIVE_PANE, 0)
            ?.takeIf { splitView.value && it in panes.indices }
            ?: 0
        restorePendingSaveState(savedInstanceState)
    }

    private fun restorePendingSaveState(savedInstanceState: Bundle?) {
        pendingSaveUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState?.getParcelable(STATE_PENDING_SAVE_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            savedInstanceState?.getParcelable(STATE_PENDING_SAVE_URI)
        }
        savedInstanceState?.getString(STATE_PENDING_SAVE_NAME)?.let { pendingSaveName = it }
    }

    /** Keep the split workspace (and the half in focus) across process death. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SPLIT_VIEW, splitView.value)
        outState.putInt(STATE_ACTIVE_PANE, activePane.value)
        pendingSaveUri?.let { outState.putParcelable(STATE_PENDING_SAVE_URI, it) }
        outState.putString(STATE_PENDING_SAVE_NAME, pendingSaveName)
    }

    /** Cache both panes' open tabs on the way to the background — the app may not come back. */
    override fun onPause() {
        super.onPause()
        // The write itself runs on the pane's writer thread; we wait briefly for it here because the
        // process can be killed once we are in the background, and a lost snapshot is lost edits.
        panes.forEach { snapshotActiveTab(it); it.persist() }
        panes.forEach { it.awaitPersist(PERSIST_WAIT_MS) }
    }

    override fun onDestroy() {
        detachAudio()
        audio.release()
        super.onDestroy()
    }

    internal fun toast(msg: String) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            glassToast.value = msg
        } else {
            runOnUiThread { glassToast.value = msg }
        }
    }

    internal companion object {
        // The document MIME lives in io/SaveTarget.kt beside the `.xopp` suffix, since the manifest's
        // intent filters have to agree with it: it is the app's declaration that these files are
        // Xournal++ documents, not untyped blobs. See that constant for why it maps to no extension.
        const val PDF_MIME = "application/pdf"
        const val PNG_MIME = "image/png"
        const val SVG_MIME = "image/svg+xml"

        /**
         * Folders under `filesDir` holding each pane's cached tab session (see [TabStore]), in pane
         * order. The first keeps its historical name so an existing session still restores.
         */
        val TABS_DIRS = listOf("tabs", "tabs-right")

        /** How long `onPause` waits for the queued session write before letting the app go. */
        const val PERSIST_WAIT_MS = 2_000L

        /** Tab label for a document that has never been opened from, or saved to, a file. */
        internal const val UNTITLED = "Untitled"

        /** Saved-instance-state keys for the split workspace (see [onSaveInstanceState]). */
        private const val STATE_SPLIT_VIEW = "editor.splitView"
        private const val STATE_ACTIVE_PANE = "editor.activePane"
        private const val STATE_PENDING_SAVE_URI = "editor.pendingSaveUri"
        private const val STATE_PENDING_SAVE_NAME = "editor.pendingSaveName"
    }
}

/** A pending user consent dialog before proceeding with a document action. */
internal data class PendingConsent(
    val name: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)
