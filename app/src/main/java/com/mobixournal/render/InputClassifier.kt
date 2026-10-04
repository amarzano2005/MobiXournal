package com.mobixournal.render

/**
 * Physical source of a pointer — a device-independent mirror of `MotionEvent.getToolType`, so the
 * classifier below stays a pure JVM value function (the view maps the Android constants onto these).
 */
enum class PointerKind {
    /** A finger / touch contact (`TOOL_TYPE_FINGER`), or a resting palm. */
    FINGER,

    /** The pen tip of a stylus (`TOOL_TYPE_STYLUS`). */
    STYLUS,

    /** The "eraser" end of a stylus flipped over (`TOOL_TYPE_ERASER`). */
    ERASER_TIP,

    /** A mouse / trackpad / anything else — treated as a drawing pointer, never as a palm. */
    UNKNOWN,
}

/**
 * The user's currently selected on-screen tool, collapsed from the rail's `EditorTool` (Hand and the
 * authoring tools reduce to [HAND] / [PLACE] here — the classifier only cares about intent, not which
 * placement kind).
 */
enum class ActiveTool {
    PEN,
    HIGHLIGHTER,
    ERASER,
    SELECT,
    BACKGROUND_SELECT,
    TEXT_SELECT,
    HAND,
    PLACE,

    /** Drag a horizontal line to insert/remove vertical space on a page. */
    VERTICAL_SPACE;

    /** The three tools whose primary gesture is laying ink down (subject to the finger-draw gate). */
    fun isDrawing(): Boolean = this == PEN || this == HIGHLIGHTER || this == ERASER

    /** What a plain pointer of this tool does, absent any stylus override or finger-draw gate. */
    fun defaultIntent(): GestureIntent = when (this) {
        PEN, HIGHLIGHTER -> GestureIntent.DRAW
        ERASER -> GestureIntent.ERASE
        SELECT -> GestureIntent.SELECT
        BACKGROUND_SELECT -> GestureIntent.BACKGROUND_SELECT
        TEXT_SELECT -> GestureIntent.SELECT_TEXT
        HAND -> GestureIntent.PAN
        PLACE -> GestureIntent.PLACE
        VERTICAL_SPACE -> GestureIntent.VERTICAL_SPACE
    }
}

/** What the stylus barrel (primary side-button) does when held during a stroke — a user setting. */
enum class BarrelAction {
    /** Ignore the button; the on-screen tool applies as normal. */
    NONE,

    /** Erase while held, regardless of the on-screen tool (the default, matching desktop). */
    ERASE,

    /** Rubber-band select while held. */
    SELECT,
}

/**
 * What a *rapid double-click* of the stylus barrel button invokes — a user setting distinct from the
 * held [BarrelAction]. The two never collide: the held action is decided at pointer-down (the tip is
 * on the glass), while a double-click is a button-only gesture recognised by [BarrelClickDetector].
 */
enum class BarrelDoubleAction {
    /** Ignore double-clicks entirely. */
    NONE,

    /** Undo the last edit (the default — the most-wanted eyes-free action). */
    UNDO,

    /** Redo the last undone edit. */
    REDO,

    /** Flip between the eraser and the previous drawing tool. */
    TOGGLE_ERASER,

    /** Flip between the Select tool and the previous drawing tool. */
    TOGGLE_SELECT,

    /** Show/hide the chrome (full-page view). */
    TOGGLE_FULL_PAGE,
}

/**
 * Recognises a double *click* of the barrel button from the stream of press edges. Pure and
 * time-injected (callers pass `MotionEvent.eventTime`) so it is fully unit-testable on the JVM.
 *
 * A click pair counts only when the second press lands within [windowMs] of the first; once it
 * fires, the state resets so a third press starts a fresh pair rather than triggering again.
 */
/**
 * Recognises a double *click* of the barrel button from the stream of press edges. Pure and
 * time-injected (callers pass `MotionEvent.eventTime`) so it is fully unit-testable on the JVM.
 *
 * A click pair counts only when the second press lands within [windowMs] of the first; once it
 * fires, the state resets so a third press starts a fresh pair rather than triggering again.
 *
 * **Only edges reach this class.** Both streams a pen can report its button on — motion button bits
 * and barrel-button key events — are funnelled through the one `BarrelButtonState` latch first, so a
 * single physical press that arrives as *both* is fed here once, not twice (which would read as a
 * double-click). See [BarrelButtonState].
 */
class BarrelClickDetector(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var lastPressMs = Long.MIN_VALUE

    /** Feed one button-press edge at [timeMs]; returns true when it completes a double-click. */
    fun press(timeMs: Long): Boolean {
        val doubled = lastPressMs != Long.MIN_VALUE && timeMs - lastPressMs in 0..windowMs
        lastPressMs = if (doubled) Long.MIN_VALUE else timeMs
        return doubled
    }

    /** Forget any pending first click (e.g. the stylus left hover range). */
    fun reset() { lastPressMs = Long.MIN_VALUE }

    companion object {
        /** Matches Android's own multi-tap window closely enough to feel native. */
        const val DEFAULT_WINDOW_MS = 350L
    }
}

/**
 * Collapses the press edges a pen's **firmware-reported click** produces into exactly one action.
 *
 * Some pens do their own gesture recognition: the Honor Choice Pencil swallows a single tap of its
 * side button and sends *one* key event for a whole physical double-click. Waiting for a second press
 * edge — what [BarrelClickDetector] does — would mean waiting forever, and nothing would ever happen;
 * which is exactly how such a pen behaves before this gate exists. So a click of a vendor-key button
 * **is** the gesture, and this class makes sure it fires once:
 *
 *  - A firmware that reports one press per double-click fires once, as it should.
 *  - A firmware that reports *two* presses per double-click (one per tap) still fires once — two
 *    actions would cancel each other out for a toggle, which reads as "the button does nothing".
 *
 * Pure and time-injected (callers pass `KeyEvent.eventTime`), like [BarrelClickDetector], so the
 * behaviour is unit-testable on the JVM; auto-repeat presses are filtered out by the caller before
 * they ever get here.
 */
class VendorClickGate(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var lastMs = Long.MIN_VALUE

    /** Feed one press edge at [timeMs]; returns true when it should run the action. */
    fun fires(timeMs: Long): Boolean {
        if (lastMs != Long.MIN_VALUE && timeMs - lastMs < windowMs) return false
        lastMs = timeMs
        return true
    }

    /** Forget the last click (e.g. the stylus left hover range). */
    fun reset() { lastMs = Long.MIN_VALUE }

    companion object {
        /**
         * Android's own double-tap timeout, which is exactly the question this gate asks: are these
         * two edges one gesture or two? A shorter window would let a firmware that reports one edge
         * per *tap* fire twice — a toggle would flip straight back and look dead — and a longer one
         * would swallow a real second gesture, which is what makes such a button feel like it misses
         * presses. Two whole double-clicks simply cannot fit in 300 ms, so the window is safe on both
         * kinds of firmware.
         */
        const val DEFAULT_WINDOW_MS = 300L
    }
}

/** The gesture a pointer-down should begin. */
enum class GestureIntent {
    DRAW,
    ERASE,
    PAN,
    SELECT,
    BACKGROUND_SELECT,
    SELECT_TEXT,
    PLACE,
    VERTICAL_SPACE,
    IGNORE,
}

/** Input-layer preferences the classifier consults (owned by the app's settings). */
data class InputSettings(
    /** When false, finger pointers only pan/zoom — they actuate no tool at all (stylus-only mode). */
    val fingerDraws: Boolean = true,
    /** What the stylus primary barrel-button invokes while held. */
    val barrelAction: BarrelAction = BarrelAction.ERASE,
    /** What a rapid double-click of that same button invokes. */
    val barrelDoubleAction: BarrelDoubleAction = BarrelDoubleAction.UNDO,
)

/**
 * Pure decision function: given a pointer's physical [PointerKind], whether the stylus barrel is
 * held, the on-screen [ActiveTool], and the user's [InputSettings], return the [GestureIntent] the
 * pointer should begin. Kept free of Android types so it is fully unit-testable on the JVM (see
 * `InputClassifierTest`); the stateful parts of stylus handling (palm rejection while a stylus is
 * already down, hover) live in `DrawingSurfaceView`, which routes every pointer-down through here.
 *
 * Precedence, matching desktop Xournal++'s "the pen hardware wins over the toolbar":
 *  1. The flipped-over **eraser tip** always erases, whatever the tool.
 *  2. A held **barrel button** applies its configured action (erase/select), whatever the tool.
 *  3. With **finger-draw off**, a finger only ever pans — it actuates no tool at all (pen, eraser,
 *     highlighter, text, selection, placement…), so a palm can't ink and only the stylus works.
 *  4. Otherwise the on-screen tool's [ActiveTool.defaultIntent].
 */
object InputClassifier {

    fun classify(
        kind: PointerKind,
        barrelPressed: Boolean,
        activeTool: ActiveTool,
        settings: InputSettings,
    ): GestureIntent {
        if (kind == PointerKind.ERASER_TIP) return GestureIntent.ERASE

        // The barrel applies to a pointer the tablet promoted to a stylus **and** to one it only
        // presents as a mouse ([PointerKind.UNKNOWN]) — the latter is how many third-party pens
        // (Honor/Huawei M-Pencils among them) arrive on a tablet that has no vendor pen service, and
        // it is exactly the case where "the button does nothing" was reported. For an UNKNOWN
        // pointer the latch only ever sets on a non-primary bit (see [BarrelButtonState]), so a
        // tip-down with no button still draws normally; a real mouse's right-click erases, which is
        // the same sensible behaviour.
        if (barrelPressed && (kind == PointerKind.STYLUS || kind == PointerKind.UNKNOWN)) {
            when (settings.barrelAction) {
                BarrelAction.ERASE -> return GestureIntent.ERASE
                BarrelAction.SELECT -> return GestureIntent.SELECT
                BarrelAction.NONE -> Unit // fall through to the on-screen tool
            }
        }

        if (kind == PointerKind.FINGER && !settings.fingerDraws) {
            return GestureIntent.PAN
        }

        return activeTool.defaultIntent()
    }
}
