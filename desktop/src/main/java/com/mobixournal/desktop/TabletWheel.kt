package com.mobixournal.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.Locale

/**
 * **The tablet's wheel**, told apart from the mouse's by asking Windows *which device* sent it.
 *
 * The probe measured what the dial actually is on this machine (`docs/desktop-workstation.md`): turning
 * it produces `WM_MOUSEWHEEL` notches — the same message a mouse wheel produces, with the same ±120
 * delta — and nothing else. Windows' `RadialController` route therefore never sees it (there is no wheel
 * *device* class in that API for a driver that emulates a mouse), and `WM_MOUSEWHEEL` alone cannot say
 * which of two wheels turned: routing every wheel to zoom would take scrolling away from the mouse.
 *
 * Raw Input is the one place that difference survives: `WM_INPUT` carries the `RAWINPUTHEADER.hDevice`
 * the report came from, and `GetRawInputDeviceInfoW` turns that handle into the device's interface path
 * (`\\?\HID#TABLETHID&COL01#…` for this tablet's virtual mouse, `\\?\HID#VID_046D…` for the Logitech).
 * A wheel is the dial's when that path is the tablet's — [isTabletDevice] holds the rule, and it is the
 * only heuristic here: a machine with a second *tablet-branded* wheel would be treated as a dial, which
 * is the same thing the reader wants.
 *
 * The registration uses `RIDEV_INPUTSINK`, so the dial works whether or not the window has focus. Raw
 * input is **observational**: it changes no behaviour on its own. The canvas keeps scrolling on Compose's
 * wheel, and suppresses that one scroll when [recentDial] says the notch was the dial's — one notch, one
 * action, as with the pen and the pointer.
 */
internal interface RawInputUser32 : StdCallLibrary {

    fun RegisterRawInputDevices(devices: Pointer, count: Int, size: Int): Boolean

    fun GetRawInputDeviceInfoW(device: Pointer?, command: Int, data: Pointer?, size: IntByReference): Int

    companion object {
        const val WM_INPUT = 0x00FF
        const val RIDI_DEVICENAME = 0x20000007
        const val RIDEV_INPUTSINK = 0x00000100
        const val RIDEV_REMOVE = 0x00000001
        const val HID_USAGE_PAGE_GENERIC: Short = 0x01
        const val HID_USAGE_GENERIC_MOUSE: Short = 0x02

        /** `RAWMOUSE.usButtonFlags` bits for the two wheels. */
        const val RI_MOUSE_WHEEL = 0x0400
        const val RI_MOUSE_HWHEEL = 0x0800

        /** `RIM_TYPEMOUSE`: the only report this reader decodes. */
        const val RIM_TYPEMOUSE = 0

        /** A wheel notch in the units `RAWMOUSE` and `WM_MOUSEWHEEL` both use. */
        const val WHEEL_DELTA = 120
    }
}

/** `RAWINPUTDEVICE`: one usage to listen for, and the window that hears it. */
internal class RawInputDevice : Structure() {
    @JvmField var usUsagePage: Short = 0
    @JvmField var usUsage: Short = 0
    @JvmField var dwFlags: Int = 0
    @JvmField var hwndTarget: Pointer? = null

    override fun getFieldOrder(): List<String> = listOf("usUsagePage", "usUsage", "dwFlags", "hwndTarget")
}

/**
 * `RAWINPUT`, flattened over its union: the header, then the mouse report's fields.
 *
 * The layout is asserted by `TabletWheelTest` against the SDK's sizes (24-byte header, 16-byte device
 * block, 24-byte `RAWMOUSE`), because reading `ulButtons` four bytes off is how a wheel becomes a button.
 */
internal class RawInput(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var dwType: Int = 0
    @JvmField var dwSize: Int = 0
    @JvmField var hDevice: Pointer? = null
    @JvmField var wParam: Long = 0
    @JvmField var usFlags: Short = 0
    @JvmField var ulButtons: Int = 0
    @JvmField var ulRawButtons: Int = 0
    @JvmField var lLastX: Int = 0
    @JvmField var lLastY: Int = 0
    @JvmField var ulExtraInformation: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "dwType", "dwSize", "hDevice", "wParam", "usFlags", "ulButtons", "ulRawButtons",
        "lLastX", "lLastY", "ulExtraInformation",
    )

    companion object {
        /** `sizeof(RAWINPUT)` for a mouse report on x64: a 24-byte header plus `RAWMOUSE`. */
        const val SIZE_BYTES = 48
    }
}

/** Device-path fragments that mean "this is the drawing tablet's own HID stack", not the mouse. */
private val TABLET_DEVICE_HINTS = listOf("tablethid", "huion", "vmulti", "vid_256c")

/**
 * True when a raw-input device interface path belongs to the drawing tablet.
 *
 * The paths come from `GetRawInputDeviceInfoW` and look like
 * `\\?\HID#TABLETHID&COL01#1&4784345&2&0000#{884b96c3-…}` — vendor-neutral, but the tablet's driver
 * names its own collections. A path with no matching fragment is the mouse, and its wheel keeps
 * scrolling.
 */
internal fun isTabletDevice(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val lower = path.lowercase(Locale.ROOT)
    return TABLET_DEVICE_HINTS.any { lower.contains(it) }
}

/**
 * The tablet's wheel, registered on [TabletWheel.open]'s window.
 *
 * [onNotches] is called with the signed notch count of a dial detent group (+1 per notch clockwise) —
 * the caller decides what a notch means. [recentDial] is what keeps one notch from acting twice: the
 * canvas asks it before honouring the wheel Compose also received for the same turn.
 */
internal class TabletWheel private constructor(
    private val library: RawInputUser32,
    private val hwnd: Pointer,
    private val onNotches: (Int) -> Unit,
    private val onEvent: ((String) -> Unit)?,
) : AutoCloseable {

    @Volatile
    private var lastDialNanos = 0L

    /** The last device path seen, so the probe can show what the wheel came from. */
    @Volatile
    var lastDevice: String? = null
        private set

    /** True when the last notch belong to the dial and arrived at most [maxAgeMs] ago. */
    fun recentDial(maxAgeMs: Long = 120): Boolean {
        val at = lastDialNanos
        if (at == 0L) return false
        return (System.nanoTime() - at) / 1_000_000L <= maxAgeMs
    }

    /**
     * Decode one `WM_INPUT`'s `lParam`.
     *
     * Everything that is not a mouse wheel report is ignored, and a wheel from any device that is not the
     * tablet is only **reported** to the probe: the mouse's wheel belongs to Compose, which already
     * scrolls and zooms with it.
     */
    fun handle(lParam: Long) {
        if (lParam == 0L) return
        val address = Pointer.createConstant(lParam)
        // The report's `dwType` is read before the structure is: a keyboard report is shorter than a
        // mouse one, and decoding it as a mouse would read past the buffer Windows lent us.
        if (address.getInt(0) != RawInputUser32.RIM_TYPEMOUSE) return
        if (address.getInt(4) < RawInput.SIZE_BYTES) return
        val raw = RawInput(address)
        raw.read()
        val flags = raw.ulButtons and 0xFFFF
        val wheelBits = flags and (RawInputUser32.RI_MOUSE_WHEEL or RawInputUser32.RI_MOUSE_HWHEEL)
        if (wheelBits == 0) return
        val data = (raw.ulButtons ushr 16).toShort().toInt()
        val notches = data / RawInputUser32.WHEEL_DELTA
        val device = deviceName(raw.hDevice)
        lastDevice = device
        val dial = isTabletDevice(device)
        onEvent?.invoke(
            "raw wheel notches=$notches hw=" + (wheelBits == RawInputUser32.RI_MOUSE_HWHEEL) +
                " dial=$dial device=${device ?: "-"}",
        )
        if (!dial) return
        lastDialNanos = System.nanoTime()
        onNotches(notches)
    }

    private fun deviceName(device: Pointer?): String? {
        if (device == null) return null
        return runCatching {
            val size = IntByReference(0)
            library.GetRawInputDeviceInfoW(device, RawInputUser32.RIDI_DEVICENAME, null, size)
            if (size.value <= 1) return null
            val buffer = Memory(size.value.toLong() * 2L)
            library.GetRawInputDeviceInfoW(device, RawInputUser32.RIDI_DEVICENAME, buffer, size)
            buffer.getWideString(0)
        }.getOrNull()
    }

    /** Stop listening: the registration is removed from the window, and the window keeps its wheel. */
    override fun close() {
        lastDialNanos = 0L
        runCatching {
            val device = RawInputDevice().apply {
                usUsagePage = RawInputUser32.HID_USAGE_PAGE_GENERIC
                usUsage = RawInputUser32.HID_USAGE_GENERIC_MOUSE
                dwFlags = RawInputUser32.RIDEV_REMOVE
            }
            library.RegisterRawInputDevices(device.pointer, 1, device.size())
        }
    }

    companion object {

        /**
         * Register [window] for the mouse's raw reports, or answer null when this host cannot.
         *
         * `RIDEV_INPUTSINK` is deliberate: the dial must work while the page has the keyboard focus, and
         * raw input is per-window — a sink registration delivers to this window regardless.
         */
        fun open(
            window: Window,
            log: (String) -> Unit,
            onNotches: (Int) -> Unit,
            onEvent: ((String) -> Unit)? = null,
        ): TabletWheel? {
            val library = runCatching {
                Native.load("user32", RawInputUser32::class.java) as RawInputUser32
            }.getOrElse {
                log("wheel: user32 unavailable (${it.message})")
                return null
            }
            val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull()
            if (hwnd == null) {
                log("wheel: the window has no handle yet")
                return null
            }
            val device = RawInputDevice().apply {
                usUsagePage = RawInputUser32.HID_USAGE_PAGE_GENERIC
                usUsage = RawInputUser32.HID_USAGE_GENERIC_MOUSE
                dwFlags = RawInputUser32.RIDEV_INPUTSINK
                hwndTarget = hwnd
            }
            val registered = library.RegisterRawInputDevices(device.pointer, 1, device.size())
            if (!registered) {
                log("wheel: RegisterRawInputDevices refused (${Native.getLastError()})")
                return null
            }
            log("wheel: raw input registered for mice")
            return TabletWheel(library, hwnd, onNotches, onEvent)
        }
    }
}
