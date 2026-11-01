package com.mobixournal.desktop.ink
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.Bounds
import com.mobixournal.render.HandwrittenWord
import com.mobixournal.render.InkLineEngine
import com.mobixournal.render.InkWordEngine

/**
 * The window's **model**: Windows' own handwriting recognizer, reached through WinRT (`docs/search.md`).
 *
 * The tablet reads ink with ML Kit, which exists only on Android; the window used to fall back to the pure
 * template engine, which reads print unreliably and cursive not at all. Windows ships a neural recognizer
 * of its own — the one behind its handwriting panel, offline, per-language — and this is its binding: the
 * machine's metadata (`Windows.UI.winmd`) gives every vtable slot, [WinRt] gives the calling convention,
 * and [PointSequence] gives the one object WinRT does not hand out: an `IIterable<Point>`.
 *
 * A machine without the optional handwriting feature answers an empty list, and the caller falls back —
 * that is a fact about the installation, not an error.
 *
 * ## What the bridge had to get right (verified live on ANTONIOFISSO-PC)
 *
 * Three ABI facts, each of which the first version got wrong in a way that looked like "the recognizer is
 * asleep" rather than "the call is wrong":
 *
 * 1. **The apartment is the thread's.** The ink objects are not agile, so they are created and used only on
 *    the one thread [worker] owns, entered as STA — and that thread pumps its queue ([WinRt.pump]) while it
 *    waits, because the finish of the work is posted back to it.
 * 2. **`IAsyncOperation<T>` does not carry its own state.** Its vtable is `get_Completed`, `put_Completed`,
 *    `GetResults` and nothing else; the status lives on a **separate** interface, `IAsyncInfo`, which takes
 *    a `QueryInterface`. Reading slot `BASE + 1` on the operation itself called `put_Completed` with a
 *    pointer to an `Int`, which wrote a zero over the status it was about to report: `Started`, forever.
 * 3. **A struct is a pointer, and the iterator starts *on* its first point.** Windows asks `HasCurrent`
 *    before it ever moves, so an iterator positioned "before the first" — the sequence's usual contract —
 *    reads as empty and `CreateStroke` answers `E_INVALIDARG`.
 *
 * With those, `WinInkTest` reads real model output: the Italian recognizer's readings, punctuation
 * candidates and all, on print and on a connected line. A machine without the optional handwriting feature
 * answers an empty list and [WinInkEngine] answers null, so the shared recogniser falls back.
 */
internal object WinInk {

    /** True on Windows: the only OS whose ink objects this binding speaks. */
    val available: Boolean by lazy { System.getProperty("os.name").orEmpty().lowercase().contains("windows") }

    /**
     * How many handwriting recognizers this Windows has installed — **zero** is the state the pure engine
     * is the answer to, because the handwriting feature is an optional language capability.
     */
    fun recognizerCount(): Int {
        if (!available || !WinRt.enter()) return 0
        return runCatching {
            val container = WinRt.activate("Windows.UI.Input.Inking.InkRecognizerContainer") ?: return 0
            try {
                val list = WinRt.callOut(container, GET_RECOGNIZERS) ?: return 0
                try {
                    WinRt.size(list)
                } finally {
                    WinRt.release(list)
                }
            } finally {
                WinRt.release(container)
            }
        }.getOrDefault(0)
    }

    /**
     * The one thread the ink objects live on: an apartment of its own, entered as **STA** and kept for the
     * life of the process. They are documented as not agile, and their completion is delivered through
     * this thread's message queue — which [WinRt.pump] drains while [await] waits.
     */
    private val worker: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "winrt-ink").apply { isDaemon = true }
        }

    /** What Windows reads out of one word's [strokes], best first; empty when there is nothing to ask. */
    fun recognize(strokes: List<List<Pair<Double, Double>>>): List<String> =
        recognizeRegions(strokes).firstOrNull()?.candidates.orEmpty()

    /**
     * Every region the model read out of [strokes], each with the box it covers — the form a **line** is
     * read in: one call, and Windows segments the line into words itself (`docs/search.md`).
     */
    fun recognizeRegions(strokes: List<List<Pair<Double, Double>>>): List<InkRegion> {
        if (!available || strokes.isEmpty()) return emptyList()
        val task = runCatching {
            worker.submit<List<InkRegion>> { if (WinRt.enter()) read(strokes) else emptyList() }
        }.getOrElse { return emptyList() }
        return runCatching {
            task.get(RECOGNITION_TIMEOUT_MS + WAIT_SLACK_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.onFailure { task.cancel(true) }.getOrDefault(emptyList())
    }

    private fun read(strokes: List<List<Pair<Double, Double>>>): List<InkRegion> {
        val container = WinRt.activate("Windows.UI.Input.Inking.InkStrokeContainer") ?: return emptyList()
        val builder = WinRt.activate("Windows.UI.Input.Inking.InkStrokeBuilder") ?: return emptyList()
        val session = session() ?: return emptyList()
        // The point sequences must outlive the strokes they produced: WinRT only borrows them.
        val sequences = mutableListOf<PointSequence>()
        val inkStrokes = mutableListOf<Pointer>()
        try {
            for (stroke in strokes) {
                val points = PointSequence.of(stroke.map { it.first.toFloat() to it.second.toFloat() })
                sequences += points
                val out = com.sun.jna.ptr.PointerByReference()
                WinRt.call(builder, CREATE_STROKE, points.pointer, out)
                val ink = out.value ?: continue
                inkStrokes += ink
                WinRt.call(container, ADD_STROKE, ink)
            }
            val operation = WinRt.callOut(session.recognizers, RECOGNIZE_ASYNC, container, TARGET_ALL)
                ?: return emptyList()
            // `IAsyncOperation<T>` carries only its handler and its result in the ABI; the state is on a
            // **separate** interface, `IAsyncInfo`, which the runtime answers to QueryInterface.
            val info = WinRt.queryInterface(operation, IID_ASYNC_INFO) ?: return emptyList()
            try {
                return if (await(info)) regionsOf(operation) else emptyList()
            } finally {
                WinRt.release(info)
                WinRt.release(operation)
            }
        } finally {
            // The strokes are held for as long as the container is: releasing them earlier left the
            // container pointing at nothing, and recognition then never finished.
            inkStrokes.forEach(WinRt::release)
            WinRt.release(container)
            WinRt.release(builder)
        }
    }

    /**
     * The window's recognizer, activated **once**. Re-activating `InkRecognizerContainer` costs about
     * 55 ms — measured, per call — and that alone was the whole cost of reading a page word by word
     * (`docs/search.md`, *The window's model*).
     */
    private class Session(val recognizers: Pointer, val recognizer: Pointer)

    private var session: Session? = null

    private fun session(): Session? {
        session?.let { return it }
        val recognizers = WinRt.activate("Windows.UI.Input.Inking.InkRecognizerContainer") ?: return null
        val recognizer = firstRecognizer(recognizers)
        if (recognizer == null) {
            WinRt.release(recognizers)
            return null
        }
        WinRt.call(recognizers, SET_DEFAULT_RECOGNIZER, recognizer)
        return Session(recognizers, recognizer).also { session = it }
    }

    /** The recognizer the machine lists first — its own language's, which is what its panel uses. */
    private fun firstRecognizer(container: Pointer): Pointer? {
        val list = WinRt.callOut(container, GET_RECOGNIZERS) ?: return null
        return try {
            if (WinRt.size(list) <= 0) null else WinRt.callOut(list, VECTOR_GET_AT, 0)
        } finally {
            WinRt.release(list)
        }
    }

    /** Waits for the async operation: a failed or timed-out recognition read nothing, never an exception. */
    private fun await(info: Pointer): Boolean {
        val status = com.sun.jna.ptr.IntByReference()
        val deadline = System.currentTimeMillis() + RECOGNITION_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            status.value = ASYNC_STARTED
            // The apartment's queue first: this thread is STA, so the completion is *posted* here.
            if (WinRt.pump()) continue
            if (WinRt.call(info, INFO_STATUS, status) != WinRt.S_OK) return false
            val state = status.value
            if (state != ASYNC_STARTED) return state == ASYNC_COMPLETED
            Thread.sleep(POLL_MS)
        }
        return false
    }

    /**
     * Every region of a finished operation — the model's own word segmentation, each with the box it
     * covers and the readings it could have been. A word's ink gives one; a **line's** gives one per
     * word, which is why the line is the unit worth paying for.
     */
    private fun regionsOf(operation: Pointer): List<InkRegion> {
        val out = com.sun.jna.ptr.PointerByReference()
        if (WinRt.call(operation, OPERATION_RESULTS, out) != WinRt.S_OK) return emptyList()
        val results = out.value ?: return emptyList()
        try {
            return (0 until WinRt.size(results).coerceAtLeast(0)).mapNotNull { index ->
                val result = WinRt.callOut(results, VECTOR_GET_AT, index) ?: return@mapNotNull null
                try {
                    val texts = WinRt.callOut(result, TEXT_CANDIDATES) ?: return@mapNotNull null
                    try {
                        val candidates = (0 until WinRt.size(texts).coerceIn(0, MAX_CANDIDATES))
                            .mapNotNull { WinRt.stringAt(texts, it) }
                        InkRegion(boundsOf(result), candidates)
                    } finally {
                        WinRt.release(texts)
                    }
                } finally {
                    WinRt.release(result)
                }
            }
        } finally {
            WinRt.release(results)
        }
    }

    /** `IInkRecognitionResult.BoundingRect` — a `Rect` of x, y, width, height in the ink's own units. */
    private fun boundsOf(result: Pointer): Bounds {
        val rect = Memory(16)
        rect.clear()
        if (WinRt.call(result, RESULT_BOUNDS, rect) != WinRt.S_OK) return Bounds(0.0, 0.0, 0.0, 0.0)
        val x = rect.getFloat(0).toDouble()
        val y = rect.getFloat(4).toDouble()
        return Bounds(x, y, x + rect.getFloat(8).toDouble(), y + rect.getFloat(12).toDouble())
    }

    /** One word the model read: the box it covers and its readings, best first. */
    internal data class InkRegion(val bounds: Bounds, val candidates: List<String>)

    /** The six slots every WinRT interface carries, then its own — see [WinRt]. */
    private const val BASE = 6

    /** `IInkStrokeBuilder`: BeginStroke, AppendToStroke, EndStroke, CreateStroke, SetDefaultDrawingAttributes. */
    private const val CREATE_STROKE = BASE + 3

    /** `IInkStrokeContainer`: BoundingRect, AddStroke, … */
    private const val ADD_STROKE = BASE + 1

    /** `IInkRecognizerContainer`: SetDefaultRecognizer, RecognizeAsync, GetRecognizers. */
    private const val SET_DEFAULT_RECOGNIZER = BASE
    private const val RECOGNIZE_ASYNC = BASE + 1
    private const val GET_RECOGNIZERS = BASE + 2

    /**
     * The two interfaces an operation answers separately — this is what the bridge first got wrong: the
     * state is **not** on the operation's own vtable, so reading it there called `put_Completed` and wrote
     * a zero back over the status, which then read as `Started` forever.
     */
    private const val IID_ASYNC_INFO = "00000036-0000-0000-c000-000000000046"

    /** `IAsyncInfo`: Id, Status, ErrorCode, Cancel, Close. */
    private const val INFO_STATUS = BASE + 1

    /** `IAsyncOperation<T>`: get_Completed, put_Completed, GetResults — its own three, and no more. */
    private const val OPERATION_RESULTS = BASE + 2

    /** `IVectorView<T>.GetAt`. */
    private const val VECTOR_GET_AT = BASE

    /** `IInkRecognitionResult`: get_BoundingRect, then GetTextCandidates, then GetStrokes. */
    private const val RESULT_BOUNDS = BASE
    private const val TEXT_CANDIDATES = BASE + 1

    private const val TARGET_ALL = 0
    /** `Windows.Foundation.AsyncStatus`: Started, Completed, Canceled, Error — in that order. */
    private const val ASYNC_STARTED = 0
    private const val ASYNC_COMPLETED = 1
    private const val POLL_MS = 2L
    private const val WAIT_SLACK_MS = 1_000L
    private const val RECOGNITION_TIMEOUT_MS = 5_000L

    /** How many of the model's alternative readings are kept: the search's alternate candidates. */
    private const val MAX_CANDIDATES = 5

    /**
     * A `Windows.Foundation.Point` sequence as an `IIterable<Point>`, built by hand.
     *
     * WinRT hands out no activatable collection, and `CreateStroke` takes one, so the object has to be
     * ours: a vtable of callbacks over a block of floats. The struct is passed as a **pointer** — that is
     * the ABI's rule for a struct.
     */
    private class PointSequence private constructor(
        val pointer: Pointer,
        @Suppress("unused") private val alive: List<Callback>,
    ) {

        companion object {

            fun of(points: List<Pair<Float, Float>>): PointSequence {
                val data = Memory((points.size * 8L).coerceAtLeast(8L))
                points.forEachIndexed { index, (x, y) ->
                    data.setFloat(index * 8L, x)
                    data.setFloat(index * 8L + 4, y)
                }
                val state = SequenceState(data, points.size)
                val iterable = state.iterableCallbacks()
                return PointSequence(state.iterableObject(iterable), iterable + state.iteratorCallbacks())
            }
        }
    }

    /** The state the two hand-made objects share: the points, the cursor, and the reference count. */
    private class SequenceState(private val data: Memory, private val count: Int) {

        private var refs = 1

        /**
         * The first point, not "before" it: Windows' own consumer asks `HasCurrent` before it ever moves,
         * and an iterator that answers "no" from the start reads as an **empty** iterable — which is how
         * this binding first failed, with `CreateStroke` answering `E_INVALIDARG` on valid ink.
         */
        private var cursor = 0

        fun iterableCallbacks(): List<Callback> = listOf(
            QueryInterface { self, riid, out ->
                answer(self, out)
            },
            AddRef { ++refs },
            ReleaseCallback { --refs },
            GetIids { _, countOut, idsOut -> answerIids(countOut, idsOut) },
            RuntimeClassName { _, out -> answerNullable(out) },
            TrustLevel { _, out -> answerTrust(out) },
            First { _, out ->
                out?.setPointer(0, iteratorObject())
                WinRt.S_OK
            },
        )

        fun iteratorCallbacks(): List<Callback> = listOf(
            QueryInterface { self, _, out -> answer(self, out) },
            AddRef { ++refs },
            ReleaseCallback { --refs },
            GetIids { _, countOut, idsOut -> answerIids(countOut, idsOut) },
            RuntimeClassName { _, out -> answerNullable(out) },
            TrustLevel { _, out -> answerTrust(out) },
            Current { _, out ->
                write(out, cursor)
                WinRt.S_OK
            },
            HasCurrent { _, out ->
                out?.setInt(0, if (cursor in 0 until count) 1 else 0)
                WinRt.S_OK
            },
            MoveNext { _, out ->
                cursor++
                out?.setInt(0, if (cursor < count) 1 else 0)
                WinRt.S_OK
            },
            GetMany { _, size, items, taken ->
                var moved = 0
                while (moved < size && cursor < count) {
                    write(items?.share(moved * 8L), cursor)
                    cursor++
                    moved++
                }
                taken?.setInt(0, moved)
                WinRt.S_OK
            },
        )

        fun iterableObject(callbacks: List<Callback>): Pointer = objectOf(callbacks)

        private var iterator: Pair<Pointer, List<Callback>>? = null

        private fun iteratorObject(): Pointer {
            val existing = iterator
            if (existing != null) return existing.first
            val callbacks = iteratorCallbacks()
            return objectOf(callbacks).also { iterator = it to callbacks }
        }

        private fun write(out: Pointer?, index: Int) {
            if (out == null || index !in 0 until count) return
            out.write(0, data.getByteArray(index * 8L, 8), 0, 8)
        }

        private fun objectOf(callbacks: List<Callback>): Pointer {
            val vtable = Memory(callbacks.size.toLong() * Native.POINTER_SIZE)
            callbacks.forEachIndexed { slot, callback ->
                vtable.setPointer(slot.toLong() * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(callback))
            }
            val object_ = Memory(2L * Native.POINTER_SIZE)
            object_.setPointer(0, vtable)
            return object_
        }

        private fun answer(self: Pointer, out: Pointer?): Int {
            // The runtime asks for `IIterable<Point>`, whose parameterised IID is not something we compute:
            // this object *is* that interface, and the WinRT-wide interfaces it may also ask for (IUnknown,
            // IInspectable, IAgileObject) either share the same three leading methods or have none.
            out?.setPointer(0, self)
            ++refs
            return WinRt.S_OK
        }

        private fun answerIids(countOut: Pointer?, idsOut: Pointer?): Int {
            countOut?.setInt(0, 0)
            idsOut?.setPointer(0, null)
            return WinRt.S_OK
        }

        private fun answerNullable(out: Pointer?): Int {
            out?.setPointer(0, null)
            return WinRt.S_OK
        }

        private fun answerTrust(out: Pointer?): Int {
            out?.setInt(0, 0)
            return WinRt.S_OK
        }
    }

    // The COM functions, one interface each: JNA builds a trampoline per type, so each shape is its own.
    fun interface QueryInterface : Callback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer?): Int
    }

    fun interface AddRef : Callback {
        fun invoke(self: Pointer): Int
    }

    fun interface ReleaseCallback : Callback {
        fun invoke(self: Pointer): Int
    }

    fun interface GetIids : Callback {
        fun invoke(self: Pointer, count: Pointer?, ids: Pointer?): Int
    }

    fun interface RuntimeClassName : Callback {
        fun invoke(self: Pointer, out: Pointer?): Int
    }

    fun interface TrustLevel : Callback {
        fun invoke(self: Pointer, out: Pointer?): Int
    }

    fun interface First : Callback {
        fun invoke(self: Pointer, out: Pointer?): Int
    }

    fun interface Current : Callback {
        fun invoke(self: Pointer, out: Pointer?): Int
    }

    fun interface HasCurrent : Callback {
        fun invoke(self: Pointer, out: Pointer?): Int
    }

    fun interface MoveNext : Callback {
        fun invoke(self: Pointer, out: Pointer?): Int
    }

    fun interface GetMany : Callback {
        fun invoke(self: Pointer, size: Int, items: Pointer?, taken: Pointer?): Int
    }

    fun interface Completed : Callback {
        fun invoke(self: Pointer, operation: Pointer, status: Int): Int
    }
}

/**
 * The tablet's seam, answered by [WinInk]: one word's strokes in, the model's readings out.
 *
 * It answers **null** when Windows has no recognizer — the handwriting feature not installed, a failed
 * activation — and the shared recogniser then reads the word with the pure engine, exactly as the tablet
 * does while ML Kit is still downloading.
 */
internal object WinInkEngine : InkLineEngine {

    override fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord? {
        val reads = WinInk.recognize(strokes.map { stroke -> stroke.points.map { it.x to it.y } })
        return wordOf(reads, bounds)
    }

    /**
     * A line, read once. Windows segments the line itself and answers a region per word, so each region
     * is given to the segmented word it covers most — by area of overlap, which survives a model that
     * puts its boundary a little to the left of ours. A word no region lands on stays null, and the
     * shared recogniser reads it with the pure engine instead.
     */
    override fun recognizeLine(words: List<List<Stroke>>, bounds: List<Bounds>): List<HandwrittenWord?> {
        val regions = WinInk.recognizeRegions(
            words.flatten().map { stroke -> stroke.points.map { it.x to it.y } },
        )
        if (regions.isEmpty()) return List(words.size) { null }
        val read = arrayOfNulls<HandwrittenWord>(words.size)
        for (region in regions) {
            val index = bounds.indices.maxByOrNull { overlap(region.bounds, bounds[it]) } ?: continue
            if (overlap(region.bounds, bounds[index]) <= 0.0) continue
            val text = region.candidates.firstOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            val existing = read[index]
            read[index] = if (existing == null) {
                wordOf(region.candidates, bounds[index])
            } else {
                existing.copy(
                    text = existing.text + " " + text,
                    candidates = (existing.candidates + region.candidates).distinct().take(5),
                )
            }
        }
        return read.toList()
    }

    private fun wordOf(reads: List<String>, bounds: Bounds): HandwrittenWord? {
        val best = reads.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return HandwrittenWord(
            text = best,
            bounds = bounds,
            confidence = 1.0,
            candidates = reads.distinct().take(5),
        )
    }

    /** How much area two boxes share — zero when they are apart, never negative. */
    private fun overlap(a: Bounds, b: Bounds): Double {
        val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (width <= 0.0 || height <= 0.0) 0.0 else width * height
    }
}
