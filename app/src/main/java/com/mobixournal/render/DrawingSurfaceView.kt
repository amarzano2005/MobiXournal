package com.mobixournal.render

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import androidx.annotation.VisibleForTesting
import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.audioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.XoppColor
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.ui.keyStringFromKeyEvent
import kotlin.math.hypot

/** What a canvas tap places when a placement tool is active (see [DrawingSurfaceView.placeKind]). */
enum class PlaceKind { TEXT, IMAGE, TEX }

/** One row of the layer panel: the layer's model index (bottom-up), label, visibility, active flag. */
data class LayerInfo(val index: Int, val label: String, val visible: Boolean, val active: Boolean)

/**
 * Where a placement tap landed: the page and its page-local pt coordinates. [existing] carries the
 * text box the tap hit (so the editor opens it for editing — prefilling its content, font, size and
 * colour — instead of creating a new one); null means "create new".
 */
data class Placement(val pageIndex: Int, val xPt: Double, val yPt: Double, val existing: TextElement? = null)

/**
 * The low-latency stylus canvas. Holds the whole [Document] and renders every page top-to-bottom,
 * each with its background ruling and all its layers, scaled to fit the view width (see
 * [PageStacker] / [BackgroundRenderer]). One finger draws; two fingers scroll the page stack.
 * Strokes are drawn here; text boxes, images, and LaTeX images are drawn by [ElementRenderer].
 * Pressure is captured at the fidelity `.xopp` stores — this is where round-trip safety starts
 * (see `docs/architecture.md`).
 *
 * This file is deliberately the *state* of the surface — the property block, the document/zoom/page/
 * layer facades and the thin `View` overrides. The behaviour is split across sibling extension
 * files: `DrawingSurfacePaint.kt` (render loop and compositing), `DrawingSurfaceInput.kt` (touch
 * state machine), `DrawingSurfaceStrokes.kt` (ink capture), `DrawingSurfaceSelection.kt` (selection
 * and its edits) and `DrawingSurfaceConstants.kt` (tuning values).
 */
class DrawingSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    /** The working document; edits append strokes to it and [toDocument] returns it verbatim. */
    private var docValue: Document = Document(pages = listOf(blankPage()))

    /**
     * The working document. Assigning it is how every edit lands, so the setter is also the one
     * place a mirrored view can be told the document moved on — see [onDocumentEdited]. Loading a
     * document ([load], [applyMirroredDocument]) writes [docValue] directly instead: that is a view
     * catching up, not an edit, and echoing it back would loop.
     */
    internal var doc: Document
        get() = docValue
        set(value) {
            if (value === docValue) return
            docValue = value
            indexingGeneration++
            handwritingIndex = null
            indexingDoc = null
            if (searchQuery.isNotEmpty() && !isLiveGestureActive) {
                rebuildSearch()
            }
            onDocumentEdited?.invoke(value)
        }
    internal var layout: StackedLayout = StackedLayout(emptyList(), 0f, 0f)
    /** The scroll/zoom offsets and their clamps ([ViewportState] owns the numbers and the maths). */
    internal val viewport = ViewportState()
    @VisibleForTesting
    internal var scrollY: Float
        get() = viewport.scrollY
        set(value) { viewport.scrollY = value }
    internal var scrollX: Float
        get() = viewport.scrollX
        set(value) { viewport.scrollX = value }
    internal val zoom: Float get() = viewport.zoom
    /** Pages shown side by side: 1 is the single-page stack, 2+ the page-overview grid. */
    internal var columns = 1

    /** Rasteriser for the PDF that backs this document's `pdf` pages (set on import), or null. */
    internal var pdfSource: PdfPageCache? = null

    /**
     * Decoder for the pictures behind `pixmap` pages. Unlike [pdfSource] this isn't handed in on
     * import: a pixmap background is *linked by name*, so the view can resolve one whenever a page
     * carries it — an opened image, or a `.xopp` that references one. Built on first use so a
     * document with no pixmap page never starts a decode thread.
     */
    internal val imageSource: ImageBackgroundCache by lazy {
        ImageBackgroundCache(::openBackgroundImage).apply { onImageReady = { requestRender() } }
    }

    /** Local copies of the `pixmap` pictures, by document reference (see [setImageSources]). */
    internal var imageBackgrounds: Map<String, java.io.File> = emptyMap()

    /** In-progress stroke (page-local pt space) and the page it belongs to. */
    internal var current: ArrayList<StrokePoint>? = null
    /** In-progress multi-stroke shape (e.g. capacitor with an open gap). */
    internal var currentStrokes: List<List<StrokePoint>>? = null
    internal var currentPage = 0
    /** While drawing a shape ([shapeKind] set), the drag anchor in page-local pt and the live flag. */
    internal var shaping = false
    internal var shapeStartX = 0.0
    internal var shapeStartY = 0.0
    /**
     * The width a shape/spline draws at, in pt: the tool's width setting, fixed at the gesture's
     * first touch and held constant for the whole shape. Shapes ignore pressure — desktop Xournal++
     * draws them at the nominal width — so this never tapers; see [nominalShapeWidthPt].
     */
    internal var shapeWidthPt = 1.5
    /** The spline tool's control points so far (page-local pt); non-empty means one is being laid down. */
    internal val splineNodes = ArrayList<SplineNode>()
    /** True between the down and up of a tap that is placing/curving the newest spline node. */
    internal var splineDragging = false
    /** Where the pointer went down on the current spline node, so a drag can be read as its tangent. */
    internal var splineAnchorX = 0.0
    internal var splineAnchorY = 0.0
    /** The previous spline tap's time/position, to recognise the double-tap that finishes the curve. */
    internal var splineTapTime = 0L
    internal var splineTapX = 0f
    internal var splineTapY = 0f
    /** Pointer id owning the current draw/erase gesture, so a resting palm can't perturb it. */
    internal var gesturePointerId = -1
    /** True while a stylus/eraser tip owns the current draw/erase gesture (drives palm rejection). */
    internal var stylusOwner = false
    // Hover preview position (view px) and whether a stylus is currently hovering.
    internal var hovering = false
    internal var hoverX = 0f
    internal var hoverY = 0f
    /** Tool type of the hovering pointer — an inverted pen's eraser tip previews the rubber, not the nib. */
    internal var hoverKind = PointerKind.UNKNOWN
    // Last eraser contact point (view px), so the tip outline follows the rub — finger touches included.
    internal var eraseX = 0f
    internal var eraseY = 0f
    internal var scrolling = false

    /**
     * Whether a two-finger tap undoes and a three-finger tap redoes (the "Shortcuts" setting). The
     * detector in `DrawingSurfaceInput.kt` reads it; off, a multi-finger tap is just a no-op pan.
     */
    internal var multiFingerShortcuts = true

    // Multi-finger tap detection state, owned by the touch state machine in `DrawingSurfaceInput.kt`.
    /** A candidate two/three-finger tap is in progress (still still and within the time window). */
    internal var multiTapCandidate = false
    /** Set once the fingers have travelled past the touch slop, which disqualifies the tap. */
    internal var multiTapMoved = false
    /** Most pointers seen during the candidate gesture (2 = undo, 3 = redo). */
    internal var multiTapMaxPointers = 0
    internal var multiTapStartTime = 0L
    internal var multiTapStartX = 0f
    internal var multiTapStartY = 0f
    /** Touch slop in px, resolved lazily from this view's configuration. */
    internal var multiTapSlop = 0f

    // Hold-to-snap state (see `DrawingSurfaceHoldSnap.kt`): a timer that turns an in-progress
    // freehand stroke into clean geometry when the stylus rests, and the anchor that arms it.
    internal var holdSnapRunnable: Runnable? = null
    internal var holdAnchorX = 0.0
    internal var holdAnchorY = 0.0
    /** True once the in-progress stroke was snapped by a hold, so the commit keeps the clean shape. */
    internal var holdSnapped = false
    internal var erasing = false
    internal var placing = false
    internal var placeDownX = 0f
    internal var placeDownY = 0f
    internal var lastFocusY = 0f
    internal var lastFocusX = 0f
    /** The two-finger span (mean pointer distance from the focus, view px) at the last pan frame,
     * so a change in span drives a proportional pinch-zoom. 0 means "re-baseline on the next frame". */
    internal var lastSpan = 0f

    // Momentum scrolling: a released pan keeps gliding, decelerating, until it stalls or hits a bound.
    // The whole loop — velocity tracking, the release seed, and the per-frame glide — is [MomentumDriver]'s.
    internal val choreographer = Choreographer.getInstance()
    internal val momentum = MomentumDriver(
        context = context,
        choreographer = choreographer,
        canScroll = { viewport.canScroll() },
        scrollBy = { dx, dy -> scrollViewportBy(dx, dy) },
        // Already inside a frame dispatch: paint now rather than deferring to the next vsync. Nothing
        // can have posted [paintCallback] since the glide started ([render] no-ops while flinging), so
        // this is the only buffer posted for this vsync.
        paintFrame = { paintPosted = false; paint() },
        cancelQueuedPaint = {
            if (paintPosted) {
                choreographer.removeFrameCallback(paintCallback)
                paintPosted = false
            }
        },
    )

    /** Scales the release velocity fed into a fling; 1 = as-flung, 0 disables momentum. Driven by the
     * momentum-strength setting (see [Momentum]). */
    var flingStrength: Float
        get() = momentum.strength
        set(value) { momentum.strength = value }
    /** The velocity→coast response shape for momentum (see [MomentumCurve]); driven by the setting. */
    var momentumCurve: MomentumCurve
        get() = momentum.curve
        set(value) { momentum.curve = value }
    /** Scales how far the document moves per unit of pan travel; 1 = one-to-one, 0 freezes it under a
     * pan, >1 pans faster than the finger. Driven by the panning-sensitivity setting (see
     * [PanSensitivity]). Also scales the released velocity so a fling glides at the same visual rate. */
    var panSensitivity = PanSensitivity.NORMAL

    /** Set between a [render] request and the vsync that services it — see [render]. */
    internal var paintPosted = false
    internal val paintCallback = Choreographer.FrameCallback { paint() }

    // Drag auto-scroll: while a selection is dragged into the top/bottom edge band the page keeps
    // scrolling and the element travels with it (desktop behaviour). Direction is -1 up, +1 down, 0
    // off; the repeat is driven by [autoScrollCallback] so it scrolls even under a held finger.
    internal var dragAutoScrollDir = 0
    internal var dragLastX = 0f
    internal var dragLastY = 0f
    internal var autoScrollPosted = false
    internal val autoScrollCallback = Choreographer.FrameCallback { autoScrollFrame() }

    // Hand-tool double-tap: a centre double-tap toggles full-page view, a left/right-edge double-tap
    // pages back/forward. Detected manually (single-finger tap = down→up without exceeding tap slop).
    internal val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    internal val doubleTapSlopPx = ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
    /** The current single touch's down time/position, and whether it has moved past tap slop (→ a pan). */
    internal var handTapDownTime = 0L
    internal var handTapDownX = 0f
    internal var handTapDownY = 0f
    internal var handTapCandidate = false
    internal var handTapMoved = false
    /** The previous confirmed tap's down time/position, to match the next tap against for a double-tap. */
    internal var handFirstTapTime = 0L
    internal var handFirstTapX = 0f
    internal var handFirstTapY = 0f

    // Page-overview drag-to-reorder: in the multi-column grid a finger long-press lifts a page and the
    // drag drops it at another slot. Armed on touch-down, fired by [pageDragArm] after the long-press
    // timeout; see [startPageDrag]. The state it moves through lives in [overview].
    internal val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
    internal val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    internal val pageDragArm = Runnable { startPageDrag() }

    /** The page-overview grid's view state: edit mode, selection, clipboard, lift (see [PageOverview]). */
    internal val overview = PageOverview(
        onSelectionChanged = { onPageSelectionChanged?.invoke(it) },
        onClipboardChanged = { onPageClipboardChanged?.invoke(it) },
        invalidate = { render() },
    )

    /** Places and edits text boxes, images and LaTeX images (see [TextEditController]). */
    internal val textEdits = TextEditController(
        document = { doc },
        activeLayerOf = { resolvedActiveLayer(it) },
        commit = { commitElementEdit(it) },
    )
    /** Which page index was last reported to [onCurrentPageChanged], to suppress duplicate calls. */
    private var lastReportedPage = -1
    /** Last (scrollY, totalHeightPx, viewportPx) reported to [onScrollChanged], to suppress duplicate calls. */
    private var lastScrollReport = Triple(-1f, -1f, -1f)
    /** Last selection box reported to [onSelectionRectChanged], to suppress duplicate calls. */
    private var lastSelectionRect: android.graphics.RectF? = null

    /** Undo/redo snapshots of the whole [Document] (cheap: immutable pages/layers share structure). */
    internal val history = EditHistory<Document>()
    /** The document as it was when the current gesture began, so one gesture is one undo step. */
    internal var gestureStartDoc: Document? = null

    /** The page/layer edit commands and the two commit pipelines behind them (see [PageCommands]). */
    private val pages = PageCommands(
        document = { doc },
        commit = { edited ->
            val before = doc
            doc = edited
            history.record(before)
            notifyHistory()
        },
        visiblePage = { visiblePageIndex() },
        currentPage = { currentPageIndex() },
        overview = overview,
        resetLayerVisibility = { hiddenLayers.clear() },
        clearActiveLayer = { activeLayerIndex = -1 },
        refresh = {
            relayout()
            // A page's height (or the page count) may have changed, so keep the viewport in range.
            scrollY = scrollY.coerceIn(0f, maxScrollY())
            render()
        },
        onPageCountChanged = { onPageCountChanged?.invoke(it) },
        onLayersChanged = { onLayersChanged?.invoke() },
    )

    /** Notified with (canUndo, canRedo) whenever the history changes, so the chrome can enable buttons. */
    var onHistoryChanged: ((Boolean, Boolean) -> Unit)? = null
    /** Fired when a keyboard key is pressed; return true to consume the event. */
    var onKeyPressed: ((String) -> Boolean)? = null
    /**
     * Notified with the new document after every edit made on this canvas, so another view of the
     * same document (the split's other pane) can be handed it — see [applyMirroredDocument].
     */
    var onDocumentEdited: ((Document) -> Unit)? = null
    /** Notified with the current zoom factor whenever it changes, so the chrome can show the level. */
    var onZoomChanged: ((Float) -> Unit)? = null
    /** Notified with the page-overview column count whenever it changes. */
    var onColumnsChanged: ((Int) -> Unit)? = null
    /** Notified with the page count whenever it changes (load, add, remove). */
    var onPageCountChanged: ((Int) -> Unit)? = null
    /** Notified with the index of the page nearest the viewport centre whenever it changes. */
    var onCurrentPageChanged: ((Int) -> Unit)? = null
    /** Notified with how many overview pages are selected whenever the selection changes. */
    var onPageSelectionChanged: ((Int) -> Unit)? = null
    /** Notified with how many pages are on the page clipboard whenever a copy changes it. */
    var onPageClipboardChanged: ((Int) -> Unit)? = null
    /** Notified with (scrollY, totalHeightPx, viewportPx) whenever the vertical scroll or content extent changes — drives the right-edge scroll thumb. */
    var onScrollChanged: ((Float, Float, Float) -> Unit)? = null
    /** Notified when a placement tap lands, so the editor can prompt for content / pick an image. */
    var onPlace: ((PlaceKind, Placement) -> Unit)? = null
    /** Notified when the Hand tool receives a centre double-tap, so the editor can toggle full-page (chrome-hidden) view. */
    var onToggleFullPage: (() -> Unit)? = null
    /**
     * Notified with the configured [BarrelDoubleAction] when the stylus barrel button is
     * double-clicked. Undo/redo are applied here on the surface; the tool/chrome flips need the
     * editor's state, so they are handed out through this callback.
     */
    var onBarrelDoubleClick: ((BarrelDoubleAction) -> Unit)? = null

    var tool: Tool = Tool.PEN
    var colorArgb: Int = AndroidColor.BLACK
    var baseWidthPt: Float = 1.5f
    /** Input-layer settings (finger-draw / barrel action) consulted by [InputClassifier]; from Settings. */
    var inputSettings: InputSettings = InputSettings()
    /**
     * Whether the pen's width follows pressure at all (desktop Xournal++'s "Pressure sensitivity").
     * Off draws every stroke at [baseWidthPt], so a pen stroke and a shape match exactly.
     */
    var pressureEnabled: Boolean = true
    /**
     * Raw pressure is scaled by this before the floor, then times the tool's width — desktop's
     * "pressure multiplier". 1 leaves the digitiser's value unchanged; above 1 thickens past the size.
     */
    var pressureMultiplier: Float = 1f
    /**
     * The floor under the filtered pressure (desktop Xournal++'s "minimum pressure"): a zero-pressure
     * sample still draws a light line at this fraction of the tool's width.
     */
    var minimumPressure: Float = PressureCurve.MINIMUM_PRESSURE_DEFAULT
    /** Jitter filter for the in-progress freehand stroke; reset at the start of each stroke. */
    internal val smoother = StrokeSmoother()
    /** How much digitiser detail freehand strokes keep (see [StrokePrecision]); from Settings. */
    // The decimation radius depends on the page's px/pt as well as this setting, so it is computed
    // per stroke in [startStroke] rather than pinned here.
    var strokePrecision: StrokePrecision = StrokePrecision.DEFAULT
    /** When true, a hovering stylus shows a preview dot (from `ACTION_HOVER_MOVE`). */
    var showHover: Boolean = true

    /** When true, the open radial palette buzzes as the flick crosses slots and on commit. */
    var paletteHaptics: Boolean = true
    /** When true, picking a slot closes the palette instead of leaving it open for more picks. */
    var paletteCloseOnSelect: Boolean = false
    /** When true, one finger pans the canvas (the Hand tool) instead of drawing/erasing. */
    var handMode: Boolean = false
    /** When non-null, a one-finger tap places an element of this kind instead of drawing. */
    var placeKind: PlaceKind? = null
    /** When true, one finger rubber-band-selects (or moves an existing selection) instead of drawing. */
    var selectMode: Boolean = false
        set(value) {
            field = value
            if (!value) clearSelection()
        }

    /** When true, a one-finger rectangle copies the flattened page region into the image clipboard. */
    var backgroundSelectMode: Boolean = false
        set(value) {
            field = value
            if (!value) { backgroundSelecting = false; clearBackgroundRegion() }
        }

    /** When true, a drag inserts/removes vertical space on a page instead of drawing (see [VerticalSpaceOps]). */
    var verticalSpaceMode: Boolean = false

    /** When true, a one-finger tap replays the tapped stroke's recording instead of drawing. */
    var audioPlayMode: Boolean = false

    /**
     * Consulted the instant a stroke is committed: returns the recording position to stamp onto it
     * (`fn`/`ts`), or null when nothing is recording. Reading it at commit rather than at the start
     * of the gesture is deliberate — it keeps the whole audio machinery out of the drawing hot path.
     */
    var audioStamp: (() -> AudioRef?)? = null

    /** Notified when an [audioPlayMode] tap lands, with the tapped stroke's link (null if it has none). */
    var onAudioTap: ((AudioRef?) -> Unit)? = null

    /** When true, the Select tool's marquee is a free-form lasso instead of a rectangle. */
    var lassoMode: Boolean = false

    /**
     * When non-null, a one-finger drag draws this geometric shape instead of a freehand stroke.
     * [ShapeKind.SPLINE] is the exception: it is laid down tap-by-tap, so switching away from it
     * commits whatever curve is still open rather than silently dropping it.
     */
    var shapeKind: ShapeKind? = null
        set(value) {
            if (field == ShapeKind.SPLINE && value != ShapeKind.SPLINE) finishSpline()
            field = value
        }

    /** Number of rows for [ShapeKind.TABLE] drawing and insertion. */
    var tableRows: Int = 3

    /** Number of columns for [ShapeKind.TABLE] drawing and insertion. */
    var tableCols: Int = 3

    /** When true, [ShapeKind.TABLE] draws a double line under the first row as a relational header. */
    var tableHeader: Boolean = false

    /** Geometric variant for [ShapeKind.TRIANGLE]: equilateral (default), right, isosceles, scalene. */
    var triangleKind: TriangleKind = TriangleKind.EQUILATERAL

    /** For [TriangleKind.SCALENE]: interior angle A in degrees. */
    var scaleneAngleA: Double = 40.0

    /** For [TriangleKind.SCALENE]: interior angle B in degrees. */
    var scaleneAngleB: Double = 60.0

    /** For [TriangleKind.SCALENE]: interior angle C in degrees. */
    var scaleneAngleC: Double = 80.0

    /** Geometric variant for [ShapeKind.TRAPEZOID]: isosceles (default), right-angled, scalene. */
    var trapezoidKind: TrapezoidKind = TrapezoidKind.ISOSCELES

    /** For [TrapezoidKind.SCALENE]: left base angle in degrees (measured from the longer base). */
    var trapezoidAngleA: Double = 75.0

    /** For [TrapezoidKind.SCALENE]: right base angle in degrees (measured from the longer base). */
    var trapezoidAngleB: Double = 60.0
    /**
     * When true, a finished freehand pen stroke that clearly means a primitive (line, arrow, circle,
     * rectangle, triangle, polyline) is snapped to clean geometry — see [ShapeRecognizer].
     */
    var recognizeShapes: Boolean = true

    /** When true, a shape tool's start/end points snap to the page background's ruling ([Snapping]). */
    var snapToGrid: Boolean = false

    /** When true, dragging the selection's rotate handle steps in [Snapping.ROTATION_STEP_DEG]. */
    var snapRotation: Boolean = false

    /**
     * The on-canvas setsquare/compass overlay, or null when none is placed. It is an input aid only
     * — it constrains drawn vertices (see [guided]) and is never written to the document.
     */
    val guide: DrawingGuide? get() = guideDrag.pose

    /** Notified whenever the user moves or re-poses the guide, so the pose can be remembered. */
    var onGuideChanged: ((DrawingGuide?) -> Unit)? = null

    /** Line pattern applied to strokes and shapes this tool draws (dashed/dotted); default solid. */
    var currentLineStyle: LineStyle = LineStyle.PLAIN
    /** How the eraser removes ink: [EraserMode.STANDARD] rubs out touched segments; [EraserMode.WHOLE_STROKE] deletes whole strokes. */
    var eraserMode: EraserMode = EraserMode.STANDARD
    /**
     * The eraser tip size follows the pen's: it is derived from [baseWidthPt] via [eraserRadiusPt],
     * so the rail's Size popup sizes the pen and the rubber together (there is no separate scheme).
     * The radius is in document pt, so the tip covers the same ink at any zoom.
     */
    val eraserRadiusPt: Double get() = eraserRadiusPt(baseWidthPt)

    /** Layer new ink lands on for the visible page; -1 = the top layer. Resolved/clamped per page. */
    var activeLayerIndex: Int = -1
        private set
    /** `(pageIndex, layerIndex)` keys hidden in the editor only — view state, never persisted. */
    internal val hiddenLayers = HashSet<Long>()
    /** Notified when the layer set / active layer / visibility changes so the chrome can refresh. */
    var onLayersChanged: (() -> Unit)? = null

    /** Notified whenever the selection appears or clears, so the chrome can show contextual actions. */
    var onSelectionChanged: ((Boolean) -> Unit)? = null

    /**
     * Notified whenever the selection's on-screen box changes — it appears, clears, is moved, resized
     * or rotated, or the view scrolls/zooms under it. Box is in this view's own px, so the chrome can
     * anchor the contextual action bar to the selection (see [EditorPaneView][com.mobixournal.ui.EditorPaneView]).
     */
    var onSelectionRectChanged: ((android.graphics.RectF?) -> Unit)? = null

    /** Notified when the copy/cut clipboard gains or loses content (drives the Paste affordance). */
    var onClipboardChanged: ((Boolean) -> Unit)? = null

    /**
     * Notified with the open spline's control-point count (0 = no spline open), so the chrome can
     * offer on-screen Finish/Undo-point/Cancel actions. A tablet has no Enter key, so without this
     * the double-tap is the only way to close a curve.
     */
    var onSplineChanged: ((Int) -> Unit)? = null

    /**
     * The select/transform gestures — the rubber-band that picks elements and the move/resize/rotate
     * drags of what is picked. It owns the selection and the transform snapshot; see
     * [SelectionGestureController].
     */
    internal val gestures = SelectionGestureController(
        document = { doc },
        setDocument = { doc = it },
        layout = { layout },
        viewport = viewport,
        activeLayerOf = { resolvedActiveLayer(it) },
        lassoMode = { lassoMode },
        snapRotation = { snapRotation },
        beginGesture = { gestureStartDoc = it },
        refresh = { relayout(); render() },
        render = { render() },
        onSelectionChanged = { onSelectionChanged?.invoke(it); reportSelectionRect() },
    )

    /** The current selection (a page index + the refs of its selected elements), or null. */
    internal var selection: ActiveSelection?
        get() = gestures.selection
        set(value) { gestures.selection = value }

    /** Copied/cut elements, ready to paste onto the visible page. */
    internal var clipboard: List<Element> = emptyList()

    // Live background-copy marquee (view px). Unlike Select, this is rectangle-only and never
    // selects elements; release copies a flattened bitmap region into [clipboard].
    internal var backgroundSelecting = false
    internal var backgroundSelectPage = -1
    internal var backgroundSelectX0 = 0f
    internal var backgroundSelectY0 = 0f
    internal var backgroundSelectX1 = 0f
    internal var backgroundSelectY1 = 0f

    // The marquee that survives the release, kept in page-local pt so it stays put while scrolling
    // and zooming. It is what the Copy/Cut actions act on; -1 / null means there is no region.
    internal var backgroundRegionPage = -1
    internal var backgroundRegion: Bounds? = null

    /** Notified when a background-select region appears or clears (drives Copy/Cut in the bar). */
    var onBackgroundRegionChanged: ((Boolean) -> Unit)? = null

    /** When true, a one-finger/stylus drag selects text over an imported PDF's extracted text layer. */
    var textSelectMode: Boolean = false
        set(value) {
            field = value
            if (!value) clearTextSelection()
        }

    /** The imported PDF's positioned text layer, or null when no PDF (or no text) is loaded. */
    internal var pdfTextIndex: PdfTextIndex? = null

    /** Notified when a PDF-text selection appears or clears, so the chrome can offer Copy. */
    var onTextSelectionChanged: ((Boolean) -> Unit)? = null

    // Live/committed PDF-text selection: a page and an inclusive reading-order word range.
    internal var textSelecting = false
    internal var textSelPage = -1
    internal var textSelAnchor = -1
    internal var textSelFocus = -1

    /** Notified when search count/current changes so the top bar can show navigation state. */
    var onSearchChanged: ((SearchStatus) -> Unit)? = null

    /** Notified when a fresh document is loaded into the canvas. */
    var onDocumentLoaded: (() -> Unit)? = null

    /** Notified when handwriting AI indexing begins or ends (true = indexing, false = idle), with optional progress text. */
    var onSearchIndexingChanged: ((Boolean, String?) -> Unit)? = null

    internal var searchQuery = ""
    internal var searchHits: List<SearchHit> = emptyList()
    internal var currentSearchHit = -1
    internal var handwritingIndex: HandwritingIndex? = null
    private var indexingDoc: Document? = null
    @Volatile private var isIndexingActive = false
    @Volatile private var indexingGeneration = 0
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val handwritingExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}
            r.run()
        }, "handwriting-index").apply { isDaemon = true }
    }

    /** True during live continuous transforms/moves where intermediate search rebuilding would thrash. */
    internal val isLiveGestureActive: Boolean
        get() = gestures.moving || gestures.resizing || gestures.rotating || vspace.active

    // Live vertical-space drag: the grabbed page, the grab line (page pt) and its view-px Y for the
    // guide overlay. Like a selection move, each frame recomputes from the gesture-start snapshot.
    internal val vspace = VerticalSpaceDrag(
        document = { doc },
        setDocument = { doc = it },
        layout = { layout },
        viewport = viewport,
        beginGesture = { gestureStartDoc = it },
        // Snap to grid on → the gap opened is a whole number of ruled lines (desktop behaviour).
        // Read live, so toggling the setting mid-session applies to the next drag.
        snapSpacingPt = { page -> if (snapToGrid) Snapping.spacingY(page.background) else 0.0 },
        refresh = { relayout(); render() },
    )

    /** The setsquare/compass overlay and the finger that poses it — see [GuideDrag]. */
    internal val guideDrag = GuideDrag(
        layout = { layout },
        viewport = viewport,
        snapRotation = { snapRotation },
        render = { render() },
        onGuideChanged = { onGuideChanged?.invoke(it) },
    )

    /** A guide asked for before the first layout, held until there's a viewport to centre it on. */
    private var pendingGuide: GuideKind? = null

    internal val strokePainter = StrokePainter()
    internal val elementRenderer = ElementRenderer()

    /** Off-screen ink rasters, so a pan/fling frame blits pages instead of re-submitting strokes. */
    internal val inkCache = InkCache(columns = { columns })

    /** Set while a coalesced redraw is queued — see [requestRender]. */
    internal val renderPosted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Every non-document brush this canvas paints with — see [CanvasChrome]. */
    internal val chrome = CanvasChrome()

    /**
     * Repaints the canvas chrome from the app's Material 3 colour scheme. The `SurfaceView` sits
     * outside the Compose tree, so the colours are pushed in from the hosting composable; see
     * `com.mobixournal.ui.theme.CanvasChromeColors`.
     */
    fun applyChromeColors(backdrop: Int, pageOutline: Int, selection: Int, guide: Int) {
        chrome.applyColors(backdrop, pageOutline, selection, guide)
        requestRender()
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        MlKitInkEngine.addOnModelReadyListener {
            mainHandler.post {
                handwritingIndex = null
                indexingDoc = null
                if (searchQuery.isNotEmpty()) {
                    triggerBackgroundIndexing(doc)
                }
            }
        }
    }

    /** Replace the canvas contents with [doc] (all pages, layers, and unmodelled elements). */
    fun load(doc: Document) {
        cancelIndexing()
        this.docValue = if (doc.pages.isEmpty()) doc.copy(pages = listOf(blankPage())) else doc
        scrollY = 0f
        scrollX = 0f
        selection = null
        clearBackgroundRegion()
        onSelectionChanged?.invoke(false)
        hiddenLayers.clear()
        activeLayerIndex = -1
        history.clear()
        notifyHistory()
        onPageCountChanged?.invoke(this.doc.pages.size)
        lastReportedPage = -1
        searchHits = emptyList()
        currentSearchHit = -1
        notifySearchChanged()
        handwritingIndex = null
        indexingDoc = null
        if (searchQuery.isNotEmpty()) {
            triggerBackgroundIndexing(this.docValue)
        }
        relayout()
        render()
        onLayersChanged?.invoke()
        onDocumentLoaded?.invoke()
    }

    /**
     * Adopt [incoming] as the document *without* disturbing this view: the scroll position, zoom and
     * column count are left exactly as they are, so a mirrored pane can sit on a different page of
     * the same document while it updates under you.
     *
     * The undo history is dropped, because it holds snapshots taken before the other view's edit and
     * undoing to one would silently throw that edit away. Undo therefore lives in the pane doing the
     * editing, and follows the user as they switch panes.
     */
    fun applyMirroredDocument(incoming: Document) {
        if (incoming === docValue) return
        cancelIndexing()
        docValue = if (incoming.pages.isEmpty()) incoming.copy(pages = listOf(blankPage())) else incoming
        current = null
        currentStrokes = null
        gestureStartDoc = null
        selection = null
        onSelectionChanged?.invoke(false)
        history.clear()
        notifyHistory()
        onPageCountChanged?.invoke(docValue.pages.size)
        searchHits = emptyList()
        currentSearchHit = -1
        notifySearchChanged()
        handwritingIndex = null
        indexingDoc = null
        if (searchQuery.isNotEmpty()) {
            triggerBackgroundIndexing(docValue)
        }
        relayout()
        render()
        onLayersChanged?.invoke()
        onDocumentLoaded?.invoke()
    }

    /** The current working document — every page, layer, and preserved element, ready to save. */
    fun toDocument(): Document = doc

    /**
     * Supply the PDF whose pages back this document's `pdf` backgrounds (set on import), or null to
     * clear it (opening a plain `.xopp`). Closes any previously-held rasteriser.
     */
    fun setPdfSource(source: PdfPageCache?) {
        // A view holds exactly one claim on its cache ([PdfPageCache.shared] is refcounted), so being
        // handed the one it already has means giving the surplus claim straight back.
        if (source === pdfSource) {
            if (source != null && source.isShared) source.close()
            return
        }
        pdfSource?.onPageReady = null
        pdfSource?.close()
        pdfSource = source
        // Rasterisation runs on a worker; redraw once a sharper page lands. A pan can land a dozen
        // tiles in one frame's time, so the redraws are coalesced into a single pass.
        source?.onPageReady = { requestRender() }
    }

    /**
     * Supply the local copy of each `pixmap` background's picture, keyed by the reference the
     * document names it under (see `io.DocumentIo`). The document's own references are left as they
     * were read so a save round-trips them unchanged, so this side table is how a reference that
     * only the loader could resolve — an archive entry, a sibling beside the `.xopp` — becomes
     * bytes the decoder can open. References not in the map fall back to being opened directly.
     */
    fun setImageSources(sources: Map<String, java.io.File>) {
        imageBackgrounds = sources
        imageSource.clear()
        requestRender()
    }

    /** The local copies backing this document's `pixmap` backgrounds, by document reference. */
    fun imageSources(): Map<String, java.io.File> = imageBackgrounds

    /** Supply the imported PDF's extracted text layer for the text-select tool, or null to clear it. */
    fun setPdfTextIndex(index: PdfTextIndex?) {
        pdfTextIndex = index
        clearTextSelection()
        rebuildSearch()
        requestRender()
    }

    /** True when the PDF has a usable text layer, so the chrome can enable the text-select tool. */
    fun hasPdfText(): Boolean = pdfTextIndex?.hasAnyText == true

    /** Search authored text and background-PDF text, repainting highlights as the query changes. */
    fun setSearchQuery(query: String): SearchStatus {
        searchQuery = query
        rebuildSearch()
        render()
        return searchStatus()
    }

    /** True when handwriting index is already computed and ready for immediate search. */
    fun isHandwritingIndexReady(): Boolean =
        handwritingIndex != null || DocumentSearch.totalStrokeCount(doc) == 0

    /** Cancels any in-flight background handwriting indexing. */
    fun cancelIndexing() {
        indexingGeneration++
        isIndexingActive = false
        onSearchIndexingChanged?.invoke(false, null)
    }

    /**
     * Ensures handwriting index is fully built before querying.
     * If not already built, runs indexing in background, posting progress and calling [onComplete] on finish.
     */
    fun ensureHandwritingIndex(onComplete: (() -> Unit)? = null) {
        val currentDoc = doc
        if (handwritingIndex != null || DocumentSearch.totalStrokeCount(currentDoc) == 0) {
            if (handwritingIndex == null) handwritingIndex = HandwritingIndex(emptyList())
            onSearchIndexingChanged?.invoke(false, null)
            onComplete?.invoke()
            return
        }

        indexingDoc = currentDoc
        isIndexingActive = true
        val taskGen = ++indexingGeneration
        onSearchIndexingChanged?.invoke(true, "Starting text recognition...")

        handwritingExecutor.execute {
            try {
                val totalPages = currentDoc.pages.size
                val index = HandwritingIndex.build(
                    doc = currentDoc,
                    onProgress = { curPage, total ->
                        if (indexingGeneration == taskGen) {
                            mainHandler.post {
                                if (indexingGeneration == taskGen) {
                                    val progressMsg = if (totalPages > 1) {
                                        "Processing page $curPage of $total..."
                                    } else {
                                        "Processing text..."
                                    }
                                    onSearchIndexingChanged?.invoke(true, progressMsg)
                                }
                            }
                        }
                    },
                    isCancelled = { indexingGeneration != taskGen },
                )
                mainHandler.post {
                    if (indexingGeneration == taskGen) {
                        handwritingIndex = index
                        isIndexingActive = false
                        onSearchIndexingChanged?.invoke(false, null)
                        if (searchQuery.isNotEmpty()) {
                            rebuildSearch()
                            render()
                        }
                        onComplete?.invoke()
                    } else {
                        isIndexingActive = false
                        onSearchIndexingChanged?.invoke(false, null)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    isIndexingActive = false
                    onSearchIndexingChanged?.invoke(false, null)
                }
            }
        }
    }

    fun nextSearchHit(): SearchStatus {
        if (searchHits.isEmpty() && searchQuery.isNotEmpty() && handwritingIndex == null && !isIndexingActive) {
            ensureHandwritingIndex()
        }
        if (searchHits.isEmpty()) return searchStatus()
        currentSearchHit = (currentSearchHit + 1).floorMod(searchHits.size)
        jumpToSearchHit()
        return notifySearchChanged()
    }

    fun previousSearchHit(): SearchStatus {
        if (searchHits.isEmpty() && searchQuery.isNotEmpty() && handwritingIndex == null && !isIndexingActive) {
            ensureHandwritingIndex()
        }
        if (searchHits.isEmpty()) return searchStatus()
        currentSearchHit = (currentSearchHit - 1).floorMod(searchHits.size)
        jumpToSearchHit()
        return notifySearchChanged()
    }

    fun clearSearch(): SearchStatus {
        searchQuery = ""
        searchHits = emptyList()
        currentSearchHit = -1
        render()
        return notifySearchChanged()
    }

    fun searchStatus(): SearchStatus =
        SearchStatus(current = if (currentSearchHit >= 0) currentSearchHit + 1 else 0, total = searchHits.size)

    internal fun rebuildSearch() {
        val currentDoc = doc
        if (searchQuery.isEmpty()) {
            searchHits = emptyList()
            currentSearchHit = -1
            notifySearchChanged()
            return
        }
        if (handwritingIndex == null && !isIndexingActive) {
            triggerBackgroundIndexing(currentDoc)
        }
        searchHits = DocumentSearch.find(doc, pdfTextIndex, searchQuery, handwritingIndex)
        currentSearchHit = when {
            searchHits.isEmpty() -> -1
            currentSearchHit < 0 -> 0
            else -> currentSearchHit.coerceAtMost(searchHits.lastIndex)
        }
        notifySearchChanged()
    }

    private fun triggerBackgroundIndexing(targetDoc: Document) {
        if (indexingDoc === targetDoc && (handwritingIndex != null || isIndexingActive)) return
        indexingDoc = targetDoc
        isIndexingActive = true
        val taskGen = ++indexingGeneration
        handwritingExecutor.execute {
            try {
                val index = HandwritingIndex.build(targetDoc, isCancelled = { indexingGeneration != taskGen })
                mainHandler.post {
                    if (indexingGeneration == taskGen) {
                        handwritingIndex = index
                        isIndexingActive = false
                        if (searchQuery.isNotEmpty()) {
                            rebuildSearch()
                            render()
                        }
                    } else {
                        isIndexingActive = false
                    }
                }
            } catch (_: Exception) {
                isIndexingActive = false
            }
        }
    }

    private fun notifySearchChanged(): SearchStatus =
        searchStatus().also { onSearchChanged?.invoke(it) }

    private fun jumpToSearchHit() {
        val hit = searchHits.getOrNull(currentSearchHit) ?: return
        val box = layout.boxes.getOrNull(hit.pageIndex) ?: return
        val bounds = hit.boxes.firstOrNull() ?: return
        val centerX = box.toViewX((bounds.left + bounds.right) / 2, 0f)
        val centerY = box.toViewY((bounds.top + bounds.bottom) / 2, 0f)
        scrollX = (centerX - width / 2f).coerceIn(0f, maxScrollX())
        scrollY = (centerY - height / 2f).coerceIn(0f, maxScrollY())
        render()
    }

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

    /** The on-disk PDF backing this document's `pdf` backgrounds, or null when there is none. */
    fun pdfSourceFile(): java.io.File? = pdfSource?.source

    /** How many pages the backing PDF has — where an appended PDF's pages start once merged in. */
    fun pdfSourcePageCount(): Int = pdfSource?.pageCount ?: 0

    /** Flatten the current document (backgrounds, PDF pages, and all annotations) to a PDF. */
    fun exportPdf(out: java.io.OutputStream) = PdfExporter(pdfSource, imageSource).export(doc, out)

    /** Flatten one page (sheet, ruling, PDF/image background and every annotation) to a PNG. */
    fun exportPagePng(pageIndex: Int, out: java.io.OutputStream): Boolean = writePagePng(pageIndex, out)

    /** Serialise one page to a scalable SVG vector document. */
    fun exportPageSvg(pageIndex: Int, out: java.io.OutputStream): Boolean = writePageSvg(pageIndex, out)

    // --- undo / redo ---------------------------------------------------------------------------

    /** Revert the most recent edit (stroke or erase gesture). No-op when there's nothing to undo. */
    fun undo() {
        doc = history.undo(doc) ?: return
        afterHistoryMove()
    }

    /** Re-apply the most recently undone edit. No-op when there's nothing to redo. */
    fun redo() {
        doc = history.redo(doc) ?: return
        afterHistoryMove()
    }

    private fun afterHistoryMove() {
        current = null
        relayout()
        render()
        notifyHistory()
    }

    internal fun notifyHistory() {
        onHistoryChanged?.invoke(history.canUndo, history.canRedo)
    }

    // --- page overview -------------------------------------------------------------------------

    /**
     * Show [n] pages side by side (1 = the plain single-page stack, 2+ = the page overview grid).
     * The current page is kept in view, so zooming out to the grid and back doesn't lose your place.
     */
    fun setColumns(n: Int) {
        val next = n.coerceIn(1, PageStacker.COLUMN_CHOICES.last())
        if (next == columns) return
        val at = currentPageIndex()
        cancelPageDrag() // leaving (or reshaping) the grid abandons any lift in flight
        if (next == 1) clearPageSelection() // no grid, no way to see or extend a selection
        columns = next
        relayout()
        goToPage(at)
        onColumnsChanged?.invoke(columns)
        render()
    }

    fun columns(): Int = columns

    // --- zoom ----------------------------------------------------------------------------------

    fun zoomIn() = setZoom(zoom * DrawingSurfaceDefaults.ZOOM_STEP)
    fun zoomOut() = setZoom(zoom / DrawingSurfaceDefaults.ZOOM_STEP)
    fun resetZoom() = setZoom(1f)

    /** Set the zoom factor (clamped), keeping the point at the viewport centre roughly fixed. */
    private fun setZoom(target: Float) {
        if (!viewport.zoomTo(target, ::relayout)) return
        render()
        onZoomChanged?.invoke(zoom)
    }

    /**
     * Multiply the zoom by [factor] (clamped) while keeping the content point under the viewport
     * pixel ([focusVx], [focusVy]) fixed — the anchor for pinch-zoom. Unlike [setZoom] this does not
     * render; the pan frame that drives it renders once at the end. No-op if the clamp bites.
     */
    internal fun zoomAbout(focusVx: Float, focusVy: Float, factor: Float) {
        if (viewport.zoomAbout(focusVx, focusVy, factor, ::relayout)) onZoomChanged?.invoke(zoom)
    }

    // --- pages ---------------------------------------------------------------------------------

    // Every page command is [PageCommands]'; the view only forwards, so the public canvas API stays
    // one place while the edit itself (and its undo step) has a single testable home.

    /** Insert a blank page after the page in view. */
    fun addPage() = pages.addPage()

    /** Insert a blank page before the page in view. */
    fun addPageBefore() = pages.addPageBefore()

    /** Duplicate the page in view (content included) straight after itself. */
    fun duplicatePage() = pages.duplicatePage()

    /** Scroll to the page after the one in view; no-op at the end of the document. */
    fun goToNextPage() = goToPage(currentPageIndex() + 1)

    /** Scroll to the page before the one in view; no-op at the start of the document. */
    fun goToPreviousPage() = goToPage(currentPageIndex() - 1)

    /** Append [newPages] after the document's existing pages as one undoable edit (PDF import). */
    fun appendPages(newPages: List<Page>) = pages.appendPages(newPages)

    /** Append [newPages] and re-point the document's PDF reference at [reference] in one edit. */
    fun appendPdfPages(newPages: List<Page>, reference: String) = pages.appendPdfPages(newPages, reference)

    /** True when any page is backed by an imported PDF — i.e. the document already has a PDF source. */
    fun hasPdfBackground(): Boolean = doc.pages.any { it.background is Background.Pdf }

    /** Delete the page currently in view. No-op when only one page remains. */
    fun removePage() = pages.removePage()

    // --- page-overview selection -----------------------------------------------------------------
    // In the multi-column grid a Hand-tool finger tap picks pages out; the picked set is what
    // [deleteSelectedPages] removes in one undoable edit. Purely view state — nothing is written to
    // the `.xopp` until a delete commits.

    /**
     * Turn overview editing on or off. Leaving edit mode abandons any lift in flight and clears the
     * selection, so coming back to the grid to read never has stale selection chrome on it.
     */
    fun setPagesEditMode(on: Boolean) {
        if (overview.setEditMode(on)) cancelPageDrag()
    }

    fun pagesEditMode(): Boolean = overview.editMode

    /** How many overview pages are currently selected. */
    fun selectedPageCount(): Int = overview.selected.size

    /** Drop the overview selection (e.g. on leaving the grid, or after a delete). */
    fun clearPageSelection() = overview.clearSelection()

    /** Delete every selected page as one undoable edit and clear the selection. */
    fun deleteSelectedPages() = pages.deleteSelectedPages()

    /** Put the selected pages on the page clipboard, in document order (no document edit yet). */
    fun copySelectedPages() = pages.copySelectedPages()

    /** How many pages are on the page clipboard (0 when nothing has been copied). */
    fun copiedPageCount(): Int = overview.clipboard.size

    /** Paste the clipboard pages as one undoable edit and scroll to the first pasted page. */
    fun pasteCopiedPages() {
        pages.pasteCopiedPages()?.let { goToPage(it) }
    }

    /**
     * The visible page's background style (plain/lined/ruled/graph/dotted), or null when the page
     * isn't a solid sheet (PDF/pixmap) and so has no selectable paper style.
     */
    fun visiblePageBackgroundStyle(): String? =
        (doc.pages.getOrNull(visiblePageIndex())?.background as? Background.Solid)?.style

    /**
     * Set the visible page's paper [style] (plain/lined/ruled/graph/dotted) as one undoable edit.
     * No-op on PDF/pixmap pages, whose background isn't a solid sheet.
     */
    fun setPageBackgroundStyle(style: String) = pages.setPageBackgroundStyle(style)

    /**
     * The visible page's ruling parameters (desktop's `<background config=…>`), or null when the page
     * has none (or isn't a solid sheet). See [BackgroundRuling].
     */
    fun visiblePageBackgroundConfig(): String? =
        (doc.pages.getOrNull(visiblePageIndex())?.background as? Background.Solid)?.config

    /** Set the visible page's ruling parameters as one undoable edit (null restores the defaults). */
    fun setPageBackgroundConfig(config: String?) = pages.setPageBackgroundConfig(config)

    /** Apply a **stationery** preset to the visible page as one undoable edit ([PageTemplates]). */
    fun applyStationery(kind: Stationery) = pages.applyStationery(kind)

    /** The visible page's size in points (width to height), or null when the document has no pages. */
    fun visiblePageSize(): Pair<Double, Double>? =
        doc.pages.getOrNull(visiblePageIndex())?.let { it.width to it.height }

    /**
     * Set the visible page's size to [widthPt] × [heightPt] points as one undoable edit; both are
     * clamped to a sane range. The stacked layout re-fits every page to the view width, so this
     * changes the page's on-screen aspect ratio (and the dimensions written to the `.xopp`).
     */
    fun setPageSize(widthPt: Double, heightPt: Double) = pages.setPageSize(widthPt, heightPt)

    /** Index of the page nearest the viewport centre — the one add/remove act on. */
    internal fun currentPageIndex(): Int =
        layout.nearestPage(scrollX + width / 2f, scrollY + height / 2f)?.index ?: doc.pages.lastIndex.coerceAtLeast(0)

    // --- layers --------------------------------------------------------------------------------
    // The panel acts on the page nearest the viewport centre ([visiblePageIndex]); layer indices are
    // bottom-up (0 = bottom z-order, last = top), matching the model. Visibility and the active layer
    // are view-only editor state; add/delete/rename/reorder/move mutate the document (undoable).

    private fun layerKey(pi: Int, li: Int): Long = (pi.toLong() shl 32) or (li.toLong() and 0xFFFFFFFFL)
    internal fun isLayerHidden(pi: Int, li: Int): Boolean = layerKey(pi, li) in hiddenLayers

    /** The visible page's layers, bottom-up, as UI-facing rows (label / visible / active). */
    fun visibleLayers(): List<LayerInfo> {
        val pi = visiblePageIndex()
        val page = doc.pages.getOrNull(pi) ?: return emptyList()
        val active = resolvedActiveLayer(page)
        return page.layers.indices.map { li ->
            LayerInfo(li, LayerOps.label(page, li), !isLayerHidden(pi, li), li == active)
        }
    }

    /** Add a fresh empty layer above the top and make it active. */
    fun addLayer() = pages.addLayer()

    /** Delete layer [index] (never the last remaining layer). */
    fun deleteLayer(index: Int) = pages.deleteLayer(index)

    /** Merge layer [index] into the layer below it (never the bottom layer). */
    fun mergeLayerDown(index: Int) = pages.mergeLayerDown(index)

    /** Rename layer [index] ([name] blank clears the custom name). */
    fun renameLayer(index: Int, name: String) = pages.renameLayer(index, name)

    /** Reorder layer [from] to position [to] (changes z-order). */
    fun moveLayer(from: Int, to: Int) = pages.moveLayer(from, to)

    /** Make layer [index] the one new ink lands on (view state; no document change). */
    fun setActiveLayer(index: Int) {
        activeLayerIndex = index
        onLayersChanged?.invoke()
    }

    /** Show/hide layer [index] in the editor only (view state; content still round-trips). */
    fun setLayerHidden(index: Int, hidden: Boolean) {
        val key = layerKey(visiblePageIndex(), index)
        if (hidden) hiddenLayers += key else hiddenLayers -= key
        render()
        onLayersChanged?.invoke()
    }

    /** Move the current selection onto layer [index] (undoable), keeping it selected there. */
    fun moveSelectionToLayer(index: Int) {
        val sel = selection ?: return
        if (sel.pageIndex != visiblePageIndex()) return
        pages.editVisiblePage(resetViewState = false, op = { page ->
            val (newPage, refs) = LayerOps.moveElementsToLayer(page, sel.refs, index)
            selection = if (refs.isEmpty()) null else ActiveSelection(sel.pageIndex, refs)
            newPage
        }, after = { onSelectionChanged?.invoke(selection != null) })
    }

    /** Scroll so page [index]'s top aligns with the viewport top (used by the page navigator). */
    fun goToPage(index: Int) {
        val box = layout.boxes.getOrNull(index) ?: return
        scrollY = box.topPx.coerceIn(0f, maxScrollY())
        render()
    }

    /** Set the vertical scroll offset to [y] px from the top, clamped (driven by the right-edge scroll thumb). */
    fun scrollToY(y: Float) {
        viewport.scrollToY(y)
        render()
    }

    /** Emit [onCurrentPageChanged] if the page under the viewport centre changed since last time. */
    internal fun reportCurrentPage() {
        if (doc.pages.isEmpty()) return
        val idx = currentPageIndex()
        if (idx != lastReportedPage) {
            lastReportedPage = idx
            onCurrentPageChanged?.invoke(idx)
            onLayersChanged?.invoke() // the panel tracks the visible page's layers
        }
    }

    /** Emit [onScrollChanged] if the scroll offset or content extent changed since last time. */
    internal fun reportScroll() {
        val t = Triple(scrollY, layout.totalHeightPx, height.toFloat())
        if (t != lastScrollReport) {
            lastScrollReport = t
            onScrollChanged?.invoke(t.first, t.second, t.third)
        }
    }

    /**
     * The active selection's bounding box in view px, padded like the drawn marquee, or null when
     * nothing is selected or its page isn't laid out. The same box [drawSelectionBox] outlines, so the
     * chrome can hang actions directly off what the user sees.
     */
    fun selectionScreenRect(): android.graphics.RectF? {
        val sel = selection ?: return null
        val box = layout.boxes.getOrNull(sel.pageIndex) ?: return null
        val page = doc.pages.getOrNull(sel.pageIndex) ?: return null
        val b = SelectionTester.boundsOf(page, sel.refs) ?: return null
        val pad = DrawingSurfaceDefaults.SELECT_PAD_PX
        return android.graphics.RectF(
            (b.left * box.scale + box.leftPx - scrollX).toFloat() - pad,
            (b.top * box.scale + box.topPx - scrollY).toFloat() - pad,
            (b.right * box.scale + box.leftPx - scrollX).toFloat() + pad,
            (b.bottom * box.scale + box.topPx - scrollY).toFloat() + pad,
        )
    }

    /** Emit [onSelectionRectChanged] if the selection box moved since the last report. */
    internal fun reportSelectionRect() {
        val rect = selectionScreenRect()
        if (rect != lastSelectionRect) {
            lastSelectionRect = rect
            onSelectionRectChanged?.invoke(rect)
        }
    }

    // --- authoring: place text boxes, images, and LaTeX images by tapping ------------------------
    // The round trip lives in [TextEditController]; the view only forwards the editor's answers and
    // commits the document it hands back.

    /** Create a text box (or edit the one a tap hit) at the placement; blank content deletes it. */
    fun insertText(p: Placement, content: String, font: String, sizePt: Double, colorArgb: Int) =
        textEdits.insertText(p, content, font, sizePt, colorArgb)

    /** Place a LaTeX image at the placement, sized to a default box (resizable later). */
    fun insertTex(p: Placement, latex: String, colorArgb: Int) = textEdits.insertTex(p, latex, colorArgb)

    /** Place an encoded image (PNG/JPEG bytes) at the placement, scaled to fit a default extent. */
    fun insertImage(p: Placement, data: ByteArray) = textEdits.insertImage(p, data)

    /** Discard a pending text-edit target (the editor's dialog was dismissed without saving). */
    fun cancelTextEdit() = textEdits.cancel()

    /** Adopt [next] as one undoable edit — how [textEdits] commits an element it placed or changed. */
    private fun commitElementEdit(next: Document) {
        val before = doc
        doc = next
        history.record(before)
        notifyHistory()
        relayout()
        render()
    }

    // --- selection & vertical space -------------------------------------------------------------
    // Selection itself (rubber-band, PDF text, and the clipboard edits) lives in
    // `DrawingSurfaceSelection.kt`; only the vertical-space drag stays here.

    /** Down with the vertical-space tool: clear the other modes and hand off to [VerticalSpaceDrag]. */
    internal fun beginVerticalSpace(event: MotionEvent) {
        scrolling = false; erasing = false; placing = false; current = null; currentStrokes = null
        val page = vspace.begin(event.x, event.y) ?: return
        currentPage = page
        gesturePointerId = event.getPointerId(0)
        render()
    }

    /**
     * The page nearest the viewport centre — the target for a paste, and what a tab records so
     * re-selecting it lands back where you left off (see `com.mobixournal.tabs`).
     */
    fun visiblePageIndex(): Int =
        layout.nearestPage(scrollX + width / 2f, scrollY + height / 2f)?.index ?: currentPage

    /** [x] pt pulled onto [box]'s background ruling when the snap-to-grid setting is on. */
    internal fun snapX(box: PageBox, x: Double): Double =
        if (snapToGrid) Snapping.snap(x, Snapping.spacingX(box.page.background)) else x

    /** [y] pt pulled onto [box]'s background ruling when the snap-to-grid setting is on. */
    internal fun snapY(box: PageBox, y: Double): Double =
        if (snapToGrid) Snapping.snap(y, Snapping.spacingY(box.page.background)) else y

    // --- the setsquare / compass guide: place it, drag it, and rule drawn points against it -------

    /**
     * Put a fresh guide of [kind] at the middle of the visible page (or clear it for
     * [GuideKind.NONE]). Placing at the viewport centre rather than the page centre is what makes
     * the guide land under the user's hand when they turn it on while zoomed in.
     */
    fun placeGuide(kind: GuideKind) {
        val index = visiblePageIndex()
        val box = layout.boxes.getOrNull(index)
        // Asked for before the first layout (a guide restored from settings): hold it until
        // surfaceChanged, so it lands under the user's eye instead of at the page's own middle.
        if (kind != GuideKind.NONE && (box == null || width <= 0 || height <= 0)) {
            pendingGuide = kind
        }
        // Before the first layout there is no viewport to centre on, so fall back to the page's own
        // middle — that way a guide restored at launch still lands somewhere sensible.
        val laidOut = box != null && width > 0 && height > 0
        val cx = when {
            laidOut -> box!!.toPtX(width / 2f, scrollX)
            box != null -> box.page.width / 2
            else -> 0.0
        }
        val cy = when {
            laidOut -> box!!.toPtY(height / 2f, scrollY)
            box != null -> box.page.height / 2
            else -> 0.0
        }
        guideDrag.page = index
        guideDrag.pose = kind.place(cx, cy)
        onGuideChanged?.invoke(guide)
        render()
    }

    /** Restore a remembered [pose] onto [page] without disturbing the viewport (used on load). */
    fun restoreGuide(pose: DrawingGuide?, page: Int = 0) {
        guideDrag.pose = pose
        guideDrag.page = page
        render()
    }

    /**
     * ([x], [y]) page pt pulled onto the guide's nearest edge when one is placed on [box]'s page and
     * the point is within reach. Every drawn vertex — freehand and shape-tool alike — goes through
     * here, which is what makes the guide behave like a physical straightedge held against the page.
     */
    internal fun guided(box: PageBox, x: Double, y: Double): Pair<Double, Double> =
        guideDrag.project(box.index, x, y)

    // --- touch: the pen draws (or erases), fingers pan; input is routed through InputClassifier ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !hasFocus()) {
            requestFocus()
        }
        return handleTouch(event) ?: super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // The diagnostics panel watches every key, not only the barrel's: what a pen's button really
        // arrives as is exactly what the panel is for.
        tracePenKey(event)
        // A stylus barrel button may be reported as a key event (Android 14+ Bluetooth pens); catch it
        // here as well as at the activity, so it still works wherever the event is delivered first.
        if (onStylusButtonKey(event)) return true
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val char = keyStringFromKeyEvent(event)
            if (char != null) {
                onKeyPressed?.let { callback ->
                    if (callback(char)) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Latch the pan's release velocity as the fling seed — see [MomentumDriver.captureRelease]. */
    internal fun captureReleaseVelocity(event: MotionEvent) =
        momentum.captureRelease(event.eventTime, focusX(event, skip = -1), focusY(event, skip = -1))

    // --- page-overview drag-to-reorder -----------------------------------------------------------
    // Only in the multi-column grid, and only for a finger: a long-press lifts the page under it, the
    // drag tracks a drop slot, and the release commits one undoable [PageOps.move]. The pen is never a
    // candidate, so drawing on a grid page is untouched.

    /** Arm the long-press that lifts a page, for a single finger down on the overview grid. */
    internal fun armPageDrag(event: MotionEvent) {
        cancelPageDrag()
        if (columns <= 1 || !overview.editMode || event.pointerCount != 1) return
        if (pointerKindOf(event, 0) != PointerKind.FINGER) return
        overview.arm(event.x, event.y)
        postDelayed(pageDragArm, longPressMs)
    }

    /** A touch that travels past slop before the timeout is a pan, not a page lift. */
    internal fun trackPageDragArm(event: MotionEvent) {
        if (!overview.armed) return
        if (hypot(event.x - overview.armDownX, event.y - overview.armDownY) > touchSlopPx) cancelPageDrag()
    }

    /** The long-press fired: lift the page under the finger and take the gesture away from panning. */
    private fun startPageDrag() {
        overview.disarm()
        if (columns <= 1 || doc.pages.size < 2) return
        val box = layout.pageAt(scrollX + overview.armDownX, scrollY + overview.armDownY) ?: return
        momentum.stop()
        scrolling = false
        overview.lift(box.index)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        render()
    }

    /** Track the finger to the page it is hovering over — that slot is where the drop lands. */
    internal fun pageDragMove(event: MotionEvent) {
        val target = layout.nearestPage(scrollX + event.x, scrollY + event.y) ?: return
        if (overview.moveDropTo(target.index)) render()
    }

    /** Commit the drop as one undoable reorder and follow the page to its new home. */
    internal fun finishPageDrag() {
        val move = overview.endDrag()
        if (move == null) { render(); return }
        val (from, to) = move
        pages.movePage(from, to)
        goToPage(to)
    }

    /** Disarm the pending long-press and drop any in-flight lift without reordering. */
    internal fun cancelPageDrag() {
        if (overview.armed) removeCallbacks(pageDragArm)
        overview.disarm()
        val wasDragging = overview.dragging
        overview.endDrag()
        if (wasDragging) render()
    }

    // --- barrel double-click ---------------------------------------------------------------------
    // Recognised only while the tip is *off* the glass (hover / button-only events): with the tip
    // down the button is the held modifier ([BarrelAction]), and firing an undo mid-stroke would be
    // exactly wrong. Edges are derived from `buttonState` rather than ACTION_BUTTON_PRESS so devices
    // that never send the button actions still work.

    internal val barrelClicks = BarrelClickDetector()

    /**
     * Keeps a pen button that its firmware reports as a *click* down to one action per gesture — see
     * [VendorClickGate]. Such a pen sends one press for a whole physical double-click, so the double
     * action runs off a single edge.
     */
    internal val vendorClicks = VendorClickGate()

    /**
     * The barrel button's held state, latched from whichever stream reports it — motion button bits,
     * or a stylus button key event (see [BarrelButtonState]). Read at pointer-down and while a stroke
     * is in progress, so a pen whose press edge lands on a hover event still erases.
     */
    internal val barrelButton = BarrelButtonState()

    // --- pen diagnostics -------------------------------------------------------------------------
    // A live log of the raw input stream, shown on the canvas itself (see `DrawingSurfacePenDebug`
    // and [PenInputLog]). Off unless the user switches it on from the top-bar menu.

    /** Whether the pen-event log is recording. */
    internal var penDebugEnabled = false

    /** Receives the log's snapshots while the panel is on — the editor's overlay renders them. */
    internal var onPenDebug: ((List<String>) -> Unit)? = null

    /** The buffered diagnostic lines. */
    internal val penLog = PenInputLog()

    private var penLogFlushPosted = false

    /**
     * Push the log to [onPenDebug] once per UI pass, coalescing a burst of events into one snapshot:
     * a pen moving across the glass emits far faster than anything can re-render.
     */
    private val penLogFlush = Runnable {
        penLogFlushPosted = false
        onPenDebug?.invoke(penLog.snapshot())
    }

    /** Schedule the coalesced snapshot push ([penLogFlush]). */
    internal fun postPenLogFlush() {
        if (penLogFlushPosted) return
        penLogFlushPosted = true
        // Before the first layout (or while detached) `post` never runs; deliver straight away instead
        // of leaving the panel stuck empty.
        if (!post(penLogFlush)) {
            penLogFlushPosted = false
            onPenDebug?.invoke(penLog.snapshot())
        }
    }

    /** Button-only events (press/release with the tip off the glass) arrive here, not in touch. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        handleGenericMotion(event) ?: super.onGenericMotionEvent(event)

    /** A hovering stylus (tip not yet down) drives a preview dot so the user can see where it'll land. */
    override fun onHoverEvent(event: MotionEvent): Boolean =
        handleHover(event) ?: super.onHoverEvent(event)

    internal fun maxScrollY(): Float = viewport.maxScrollY()
    internal fun maxScrollX(): Float = viewport.maxScrollX()

    /** Scroll the viewport by one glide step, clamped; false when it didn't move (pinned at a bound). */
    internal fun scrollViewportBy(dx: Float, dy: Float): Boolean = viewport.scrollBy(dx, dy)

    // --- surface + rendering -------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) = render()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        relayout()
        // A guide restored from settings before the first layout had no viewport to centre on; now
        // there is one, so place it for real rather than leaving it wherever the fallback guessed.
        pendingGuide?.let { pendingGuide = null; placeGuide(it) }
        render()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) = momentum.stop()

    override fun onDetachedFromWindow() {
        indexingGeneration++
        momentum.stop()
        choreographer.removeFrameCallback(paintCallback)
        paintPosted = false
        inkCache.clear()
        elementRenderer.close()
        super.onDetachedFromWindow()
    }

    /** False during gestures that rewrite the page model each frame, where caching would thrash. */
    internal val inkCacheUsable: Boolean
        get() = !gestures.transforming && !erasing


}
