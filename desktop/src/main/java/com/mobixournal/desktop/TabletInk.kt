package com.mobixournal.desktop

import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * **Windows Ink** — the pen's own stream, read from `WM_POINTER`.
 *
 * `desktop-tablet-pressure-spike` measured what the window was getting before this file existed
 * (`docs/desktop-workstation.md`): Compose reported the Huion as a **mouse** with `pressure = 1.0` and
 * no tilt, because AWT's pointer model has no pen in it, and the Wintab context the first probe tried
 * opened without a single device axis. The pen's real readings are in Win32's pointer messages, which
 * Windows sends to the window *beside* the mouse messages it also sends: `WM_POINTERDOWN` /
 * `WM_POINTERUPDATE` / `WM_POINTERUP`, a pointer id in `wParam`, and `GetPointerPenInfo`'s
 * `POINTER_PEN_INFO` (pressure 0–1024, rotation 0–359°, tiltX/tiltY −90…+90) behind it.
 *
 * The window procedure is **subclassed**, not replaced: `SetWindowLongPtrW(GWLP_WNDPROC, …)` puts this
 * class in front of AWT's own procedure and every message — the pointer ones included — is passed on
 * through `CallWindowProcW`, so the window keeps behaving exactly as it did and this reader only
 * watches. Nothing here is on the ink path: [contact] is a *reading* that `PageCanvasInput` folds into
 * the Compose pointer it already receives, which is what keeps one stroke from being delivered twice
 * (a pointer event is still one sample; the pen's pressure replaces the `1.0` a mouse reports).
 *
 * A device that declares no pressure (or no tilt) reports **null** for it, the same rule
 * `docs/stylus.md` states for the tablet: the pen is then drawn at the tool's width, which is what a
 * mouse does, instead of being drawn at width zero.
 */
internal interface InkUser32 : StdCallLibrary {

    fun GetPointerType(pointerId: Int, type: IntByReference): Boolean

    fun GetPointerPenInfo(pointerId: Int, info: PenPointerInfo): Boolean

    fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Pointer): Pointer

    fun CallWindowProcW(previous: Pointer, hwnd: Pointer, message: Int, wParam: Long, lParam: Long): Pointer

    /** The extra information the message carries — how a driver marks *which* device sent a wheel. */
    fun GetMessageExtraInfo(): Pointer?

    companion object {
        /** `GWLP_WNDPROC`: the index [SetWindowLongPtrW] takes to swap a window's procedure. */
        const val GWLP_WNDPROC = -4

        const val WM_POINTERUPDATE = 0x0245
        const val WM_POINTERDOWN = 0x0246
        const val WM_POINTERUP = 0x0247

        /** The legacy wheel messages: what a dial would send if Windows treated it as a mouse wheel. */
        const val WM_MOUSEWHEEL = 0x020A
        const val WM_MOUSEHWHEEL = 0x020E

        /** `PT_PEN` — the only pointer type this reader is about (mouse is 4, touch is 2). */
        const val PT_PEN = 3

        /** `POINTER_FLAG_INCONTACT`: the tip is on the glass, not hovering above it. */
        const val POINTER_FLAG_INCONTACT = 0x00000004

        /** `PEN_FLAG_ERASER`: the pen is turned round and its eraser end is touching. */
        const val PEN_FLAG_ERASER = 0x00000004

        const val PEN_MASK_PRESSURE = 0x00000001
        const val PEN_MASK_ROTATION = 0x00000002
        const val PEN_MASK_TILT_X = 0x00000004
        const val PEN_MASK_TILT_Y = 0x00000008

        /** The reading a full press reports: `POINTER_PEN_INFO.pressure` is 0–1024. */
        const val PRESSURE_MAX = 1024f
    }
}

/**
 * The window procedure this reader installs: `LRESULT WndProc(HWND, UINT, WPARAM, LPARAM)`.
 *
 * `WPARAM`/`LPARAM` are the platform's pointer-sized integers and are bound as `Long` — a `Pointer`
 * parameter would promise JNA a dereferenceable address, while a window parameter is a value (the
 * pointer id sits in its low sixteen bits).
 */
internal fun interface InkWindowProcedure : StdCallLibrary.StdCallCallback {
    fun callback(hwnd: Pointer, message: Int, wParam: Long, lParam: Long): Pointer?
}

/** `POINT`, as the pointer structures embed it. */
internal class InkPoint : Structure() {
    @JvmField var x: Int = 0
    @JvmField var y: Int = 0

    override fun getFieldOrder(): List<String> = listOf("x", "y")
}

/**
 * `POINTER_INFO`, the header every pointer structure starts with.
 *
 * The field order is the SDK's, and [PenPointerInfo]'s 120-byte size on x64 is asserted by
 * `TabletInkTest`: a `Structure` whose layout drifted would read a pressure out of the wrong four
 * bytes and call it a pen.
 */
internal class InkPointerInfo : Structure() {
    @JvmField var pointerType: Int = 0
    @JvmField var pointerId: Int = 0
    @JvmField var frameId: Int = 0
    @JvmField var pointerFlags: Int = 0
    @JvmField var sourceDevice: Pointer? = null
    @JvmField var hwndTarget: Pointer? = null
    @JvmField var ptPixelLocation: InkPoint = InkPoint()
    @JvmField var ptHimetricLocation: InkPoint = InkPoint()
    @JvmField var ptPixelLocationRaw: InkPoint = InkPoint()
    @JvmField var ptHimetricLocationRaw: InkPoint = InkPoint()
    @JvmField var dwTime: Int = 0
    @JvmField var historyCount: Int = 0
    @JvmField var inputState: Int = 0
    @JvmField var dwKeyStates: Int = 0
    @JvmField var performanceCount: Long = 0
    @JvmField var buttonChangeType: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "pointerType", "pointerId", "frameId", "pointerFlags", "sourceDevice", "hwndTarget",
        "ptPixelLocation", "ptHimetricLocation", "ptPixelLocationRaw", "ptHimetricLocationRaw",
        "dwTime", "historyCount", "inputState", "dwKeyStates", "performanceCount", "buttonChangeType",
    )
}

/** `POINTER_PEN_INFO`: [InkPointerInfo] plus the pen's own fields. */
internal class PenPointerInfo : Structure() {
    @JvmField var pointerInfo: InkPointerInfo = InkPointerInfo()
    @JvmField var penFlags: Int = 0
    @JvmField var penMask: Int = 0
    @JvmField var pressure: Int = 0
    @JvmField var rotation: Int = 0
    @JvmField var tiltX: Int = 0
    @JvmField var tiltY: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "pointerInfo", "penFlags", "penMask", "pressure", "rotation", "tiltX", "tiltY",
    )
}

/**
 * One pen sample in the editor's units, or the honest absence of one.
 *
 * [pressure] is `null` when the device's `penMask` declares no pressure — not `0`, which would draw a
 * stroke of no width; [tiltRad] and [azimuthRad] are `null` when the pen reports no tilt, and
 * [azimuthRad] is then undefined too, since it is the direction the tilt points in. [atNanos] is when
 * **this process** received the sample (`System.nanoTime`), never the driver's own tick count: AWT's
 * event time and `GetTickCount` share no epoch, so the two are never compared.
 */
internal data class PenContact(
    val pressure: Float?,
    /** Angle from vertical, radians, 0 for a pen held upright. */
    val tiltRad: Float?,
    /** The direction the pen leans toward, radians clockwise from "away from the user". */
    val azimuthRad: Float?,
    /** The pen's twist about its own axis, radians. */
    val rotationRad: Float?,
    /** True when the sample came from the eraser end (`PEN_FLAG_ERASER`). */
    val eraser: Boolean,
    /** The device's own pressure reading, kept for the diagnostics log. */
    val rawPressure: Int,
    /** Which optional fields the device declared — `penMask`, for the diagnostics log. */
    val mask: Int,
    val atNanos: Long,
) {

    /** True when this sample was received at most [maxAgeMs] ago. */
    fun fresh(nowNanos: Long, maxAgeMs: Long): Boolean {
        val ageMs = (nowNanos - atNanos) / 1_000_000L
        return ageMs in 0..maxAgeMs
    }

    /** One line for the probe's CSV: every reading, and which ones the device actually declares. */
    fun describe(): String = String.format(
        Locale.ROOT,
        "ink p=%s raw=%d mask=0x%02X tilt=%s az=%s rot=%s eraser=%b",
        pressure?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "-",
        rawPressure,
        mask,
        tiltRad?.let { degrees(it) } ?: "-",
        azimuthRad?.let { degrees(it) } ?: "-",
        rotationRad?.let { degrees(it) } ?: "-",
        eraser,
    )

    companion object {

        /** Reads [info] into the editor's units; [atNanos] is when the message arrived. */
        fun of(info: PenPointerInfo, atNanos: Long): PenContact {
            val mask = info.penMask
            val pressure = if (mask and InkUser32.PEN_MASK_PRESSURE != 0) {
                (info.pressure / InkUser32.PRESSURE_MAX).coerceIn(0f, 1f)
            } else {
                null
            }
            // Both tilt axes or neither: one axis alone does not say which way the pen leans.
            val hasTilt = mask and InkUser32.PEN_MASK_TILT_X != 0 && mask and InkUser32.PEN_MASK_TILT_Y != 0
            return PenContact(
                pressure = pressure,
                tiltRad = if (hasTilt) {
                    (hypot(info.tiltX.toDouble(), info.tiltY.toDouble()) * PI / 180.0).toFloat()
                } else {
                    null
                },
                azimuthRad = if (hasTilt) {
                    val clockwise = atan2(info.tiltX.toDouble(), info.tiltY.toDouble())
                    val wrapped = (clockwise + 2.0 * PI) % (2.0 * PI)
                    wrapped.toFloat()
                } else {
                    null
                },
                rotationRad = if (mask and InkUser32.PEN_MASK_ROTATION != 0) {
                    (info.rotation * PI / 180.0).toFloat()
                } else {
                    null
                },
                eraser = info.penFlags and InkUser32.PEN_FLAG_ERASER != 0,
                rawPressure = info.pressure,
                mask = mask,
                atNanos = atNanos,
            )
        }

        private fun degrees(radians: Float): String =
            String.format(Locale.ROOT, "%.1f", radians * 180.0 / PI)
    }
}

/**
 * A window's Windows Ink reader: the subclassed procedure, the latest contact, and nothing else.
 *
 * Opened once per window (`TabletInk.open`), closed with the window. When there is no Windows Ink to
 * talk to — a pen driver in mouse mode, a non-Windows host, a window whose handle is not ready — [open]
 * answers `null` and the window keeps the mouse path it always had, which is the documented fallback
 * (`desktop-tablet-absent-fallback`).
 */
internal class TabletInk private constructor(
    private val user32: InkUser32,
    private val hwnd: Pointer,
    /** An observer of every contact, for the diagnostics probe; the editor passes null. */
    private val onSample: ((PenContact) -> Unit)?,
    /**
     * An observer of every message this reader looks at — each `WM_POINTER` with its pointer type and
     * flags, each wheel notch — for the diagnostics probe. The editor passes null: a message with nothing
     * to say about the pen is not the window's business.
     */
    private val onEvent: ((String) -> Unit)? = null,
    /** Receives the `lParam` of every `WM_INPUT`, which is where a raw wheel report lives. */
    private val onRawInput: ((Long) -> Unit)? = null,
    /**
     * Receives every other message this procedure sees, as `(message, wParam, lParam)`.
     *
     * One window procedure, two readers: `TabletWintab` opens its context on the same window, and
     * Wintab delivers packets as `WT_PACKET` messages (0x7FF0) to whoever subclassed it. A reader that
     * installed a second procedure would be fighting this one for the window.
     */
    private val onWindowMessage: ((Int, Long, Long) -> Unit)? = null,
) : InkWindowProcedure, PenContactStream, AutoCloseable {

    /** The procedure this one was installed in front of. */
    private var previous: Pointer? = null

    /** The last sample whose tip was on the glass, cleared the moment the pen lifts. */
    @Volatile
    private var latest: PenContact? = null

    /** The pointer type of the last `WM_POINTER` message — the probe logs it to prove a *pen* arrived. */
    @Volatile
    var pointerType: Int = 0
        private set

    /** Contacts read this session, for the probe's counter. */
    @Volatile
    var samples: Int = 0
        private set

    override fun callback(hwnd: Pointer, message: Int, wParam: Long, lParam: Long): Pointer? {
        // A reader that throws inside a native callback would take the window procedure with it, so the
        // observation is fenced and the message is always passed on.
        runCatching { observe(message, wParam, lParam) }
        val previousProcedure = previous ?: return null
        return user32.CallWindowProcW(previousProcedure, hwnd, message, wParam, lParam)
    }

    private fun observe(message: Int, wParam: Long, lParam: Long) {
        runCatching { onWindowMessage?.invoke(message, wParam, lParam) }
        if (message == RawInputUser32.WM_INPUT) {
            // The raw report names the device; `TabletWheel` is the only thing that needs it.
            runCatching { onRawInput?.invoke(lParam) }
            return
        }
        if (message == InkUser32.WM_MOUSEWHEEL || message == InkUser32.WM_MOUSEHWHEEL) {
            // The high word of `wParam` is the signed notch count; a dial that Windows passes as a wheel
            // would arrive exactly here, which is why the probe records it.
            val delta = (wParam ushr 16).toShort().toInt()
            val extra = runCatching { user32.GetMessageExtraInfo()?.let { Pointer.nativeValue(it) } }.getOrNull()
            val signature = extra?.let { String.format(Locale.ROOT, "0x%X", it) } ?: "-"
            onEvent?.invoke(
                "wheel msg=0x${message.toString(16)} delta=$delta extra=$signature " +
                    "keys=0x${(wParam and 0xFFFF).toString(16)} pos=0x${lParam.toString(16)}",
            )
            return
        }
        if (message != InkUser32.WM_POINTERDOWN &&
            message != InkUser32.WM_POINTERUPDATE &&
            message != InkUser32.WM_POINTERUP
        ) {
            return
        }
        // GET_POINTERID_WPARAM: the pointer id is the low word of the window parameter.
        val pointerId = (wParam and 0xFFFF).toInt()
        val kind = IntByReference()
        if (!user32.GetPointerType(pointerId, kind)) return
        pointerType = kind.value
        if (kind.value != InkUser32.PT_PEN) {
            onEvent?.invoke("pointer msg=0x${message.toString(16)} type=${kind.value} (not a pen)")
            return
        }
        if (message == InkUser32.WM_POINTERUP) {
            latest = null
            return
        }
        val info = PenPointerInfo()
        if (!user32.GetPointerPenInfo(pointerId, info)) return
        if (info.pointerInfo.pointerFlags and InkUser32.POINTER_FLAG_INCONTACT == 0) return
        val sample = PenContact.of(info, System.nanoTime())
        latest = sample
        samples++
        onSample?.invoke(sample)
    }

    /**
     * The pen's reading if it is **on the glass right now**, else null.
     *
     * The pointer event AWT delivers follows the `WM_POINTER` message that produced it, so "the newest
     * contact, if it is younger than [maxAgeMs]" is the sample that belongs to the pointer event being
     * handled. [maxAgeMs] is generous enough for a slow frame and short enough that a lifted pen never
     * colours a mouse stroke.
     */
    override fun contact(): PenContact? = contactWithin(CONTACT_MS)

    /** The contact, if it is younger than [maxAgeMs] — the freshness rule, on its own. */
    fun contactWithin(maxAgeMs: Long): PenContact? {
        val contact = latest ?: return null
        return if (contact.fresh(System.nanoTime(), maxAgeMs)) contact else null
    }

    override fun close() {
        previous?.let { user32.SetWindowLongPtrW(hwnd, InkUser32.GWLP_WNDPROC, it) }
        previous = null
        latest = null
    }

    companion object {

        /** How old a contact may be and still belong to the pointer event being handled. */
        const val CONTACT_MS = 120L

        /**
         * Install the reader on [window], or answer null when this host has no Windows Ink for it.
         *
         * [log] receives the one sentence that says which case it was — the probe writes it into its
         * CSV, and the packaged window leaves it on the console. Never an exception to the user.
         */
        fun open(
            window: Window,
            log: (String) -> Unit,
            onSample: ((PenContact) -> Unit)? = null,
            onEvent: ((String) -> Unit)? = null,
            onRawInput: ((Long) -> Unit)? = null,
            onWindowMessage: ((Int, Long, Long) -> Unit)? = null,
        ): TabletInk? {
            val user32 = runCatching {
                Native.load("user32", InkUser32::class.java) as InkUser32
            }.getOrElse {
                log("ink: user32 unavailable (${it.message})")
                return null
            }
            val handle = runCatching { Native.getWindowPointer(window) }.getOrNull()
            if (handle == null) {
                log("ink: the window has no handle yet")
                return null
            }
            val ink = TabletInk(user32, handle, onSample, onEvent, onRawInput, onWindowMessage)
            val installed = user32.SetWindowLongPtrW(
                handle,
                InkUser32.GWLP_WNDPROC,
                CallbackReference.getFunctionPointer(ink),
            )
            if (installed == null && Native.getLastError() != 0) {
                log("ink: SetWindowLongPtrW refused (${Native.getLastError()})")
                return null
            }
            ink.previous = installed
            log("ink: window procedure installed")
            return ink
        }
    }
}
