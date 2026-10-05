package com.mobixournal.render

import java.util.Locale

/**
 * A small ring buffer of already-formatted pen-input lines — the model behind the on-canvas
 * **pen diagnostics** panel.
 *
 * The panel exists because a stylus can reach an app down several unrelated paths (motion button
 * bits, barrel-button key codes, a vendor pen the tablet only presents as a mouse) and which one a
 * given pen takes is not observable from the outside: a pen whose side button does nothing looks the
 * same whether the app mis-reads the event or the tablet never delivers one. Showing the raw stream
 * on the glass answers that question directly, from the device itself.
 *
 * Android-free — plain strings and ints — so the buffer is unit-testable on the JVM
 * (see `PenInputLogTest`); the mapping from `MotionEvent`/`KeyEvent` to the arguments of
 * [PenEventText] happens in `DrawingSurfacePenDebug.kt`.
 */
internal class PenInputLog(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lines = ArrayDeque<String>()
    private var lastKey: String? = null

    /** How many lines are in the buffer right now. */
    val size: Int get() = lines.size

    /**
     * Append [line], dropping it when a line with the same [key] went in last — a pen moving across
     * the glass emits events far faster than anyone can read them, and every one of a long hover is
     * identical for our purposes. Pass no key for a line that must always land. Returns true when
     * the line was kept.
     */
    fun record(line: String, key: String? = null): Boolean {
        if (key != null && key == lastKey) return false
        lastKey = key
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
        return true
    }

    /** The buffered lines, oldest first. */
    fun snapshot(): List<String> = lines.toList()

    /** Drop everything — the user pressing Clear, or the panel being switched on afresh. */
    fun clear() {
        lines.clear()
        lastKey = null
    }

    companion object {
        /** How many lines the panel keeps; it shows as many as a phone-sized canvas can display. */
        const val DEFAULT_CAPACITY = 40
    }
}

/**
 * Turns the raw numbers of a pointer or key event into one readable diagnostic line.
 *
 * Every Android constant it names is mirrored locally, so the whole thing stays a pure function of
 * ints and strings: the flags are a fixed part of the platform ABI, and mirroring them here is what
 * lets the formatting be unit-tested without a device (`BarrelButtonState` does the same).
 */
internal object PenEventText {

    // MotionEvent actions — the values the panel needs to name.
    private const val ACTION_DOWN = 0
    private const val ACTION_UP = 1
    private const val ACTION_MOVE = 2
    private const val ACTION_CANCEL = 3
    private const val ACTION_OUTSIDE = 4
    private const val ACTION_POINTER_DOWN = 5
    private const val ACTION_POINTER_UP = 6
    private const val ACTION_HOVER_MOVE = 7
    private const val ACTION_SCROLL = 8
    private const val ACTION_HOVER_ENTER = 9
    private const val ACTION_HOVER_EXIT = 10
    private const val ACTION_BUTTON_PRESS = 11
    private const val ACTION_BUTTON_RELEASE = 12

    // MotionEvent tool types.
    private const val TOOL_UNKNOWN = 0
    private const val TOOL_FINGER = 1
    private const val TOOL_STYLUS = 2
    private const val TOOL_ERASER = 3
    private const val TOOL_MOUSE = 4

    /** Names for the styling hardware's long device names, so a line stays on one row. */
    private const val DEVICE_MAX = 22

    /**
     * `DOWN tool=stylus btn=0x20:STYLUS_PRIMARY p=0.42 dev=HONOR Choice Pencil`.
     *
     * [pressure] is the digitiser's raw reading for the pointer, when the event carries one — the
     * number the pen's width is actually computed from, so a stool pigeon that never varies, or one
     * that never moves off 1.0, is visible right here instead of only in how a stroke looks.
     */
    fun motionLine(
        actionMasked: Int,
        toolType: Int,
        buttonState: Int,
        pointerCount: Int,
        deviceName: String?,
        pressure: Float? = null,
    ): String = buildString {
        append(actionName(actionMasked))
        append(" tool=").append(toolName(toolType))
        append(" btn=").append(buttons(buttonState))
        if (pressure != null) append(" p=").append(pressure(pressure))
        if (pointerCount > 1) append(" pointers=").append(pointerCount)
        appendDevice(this, deviceName)
    }

    /**
     * The dedupe key for [motionLine]'s arguments: identical consecutive states are one line. The
     * pressure is part of it — a stream that only ever changes pressure (a pen pressed harder without
     * moving) would otherwise be collapsed, which is exactly the evidence the panel is opened for.
     */
    fun motionKey(
        actionMasked: Int,
        toolType: Int,
        buttonState: Int,
        pointerCount: Int,
        deviceName: String?,
        pressure: Float? = null,
    ): String = "$actionMasked/$toolType/$buttonState/$pointerCount/$deviceName/" +
        (pressure?.let(::pressure) ?: "-")

    /**
     * `PEN sens=on mult=1.50 min=0.05 base=1.50pt -> @p=0.50 w=0.75pt` — the live pressure filter and
     * what it makes of a half press.
     *
     * Logged whenever the settings are pushed onto the canvas, which is what makes it an answer rather
     * than a restatement: a slider moved in Settings must show up here as the new multiplier, or the
     * value never reached the drawing surface at all.
     */
    fun penParametersLine(
        enabled: Boolean,
        multiplier: Float,
        minimum: Float,
        baseWidthPt: Float,
        samplePressure: Float,
        sampleWidthPt: Double,
    ): String = buildString {
        append("PEN sens=").append(if (enabled) "on" else "off")
        append(" mult=").append(times(multiplier))
        append(" min=").append(fraction(minimum))
        append(" base=").append(pts(baseWidthPt))
        append(" -> @p=").append(fraction(samplePressure))
        append(" w=").append(pts(sampleWidthPt))
    }

    /** `KEY down KEYCODE_STYLUS_BUTTON_PRIMARY(308) src=0x4002:STYLUS|POINTER`. */
    fun keyLine(down: Boolean, keyCode: Int, source: Int, deviceName: String?): String = buildString {
        append("KEY ").append(if (down) "down" else "up").append(' ')
        append(keyName(keyCode))
        append(" src=").append(hex(source)).append(':').append(sourceNames(source))
        appendDevice(this, deviceName)
    }

    /** `KEYCODE_STYLUS_BUTTON_PRIMARY(308)` — the names the barrel button is known by. */
    fun keyName(keyCode: Int): String = when (keyCode) {
        BarrelButtonState.KEYCODE_STYLUS_BUTTON_PRIMARY -> "KEYCODE_STYLUS_BUTTON_PRIMARY(308)"
        BarrelButtonState.KEYCODE_STYLUS_BUTTON_SECONDARY -> "KEYCODE_STYLUS_BUTTON_SECONDARY(309)"
        BarrelButtonState.KEYCODE_BUTTON_STYLUS_PRIMARY -> "KEYCODE_BUTTON_STYLUS_PRIMARY(317)"
        BarrelButtonState.KEYCODE_BUTTON_STYLUS_SECONDARY -> "KEYCODE_BUTTON_STYLUS_SECONDARY(318)"
        in 188..203 -> "KEYCODE_BUTTON_${keyCode - 187}($keyCode)"
        66 -> "KEYCODE_ENTER(66)"
        4 -> "KEYCODE_BACK(4)"
        82 -> "KEYCODE_MENU(82)"
        19 -> "KEYCODE_DPAD_UP(19)"
        20 -> "KEYCODE_DPAD_DOWN(20)"
        21 -> "KEYCODE_DPAD_LEFT(21)"
        22 -> "KEYCODE_DPAD_RIGHT(22)"
        else -> "key($keyCode)"
    }

    /** `dev #3 "HONOR Choice Pencil" src=0x4002:STYLUS|POINTER` — one line per input device. */
    fun deviceLine(id: Int, name: String?, sources: Int): String =
        "dev #$id \"${trim(name ?: "?")}\" src=${hex(sources)}:${sourceNames(sources)}"

    /** `DOWN`, `HOVER_MOVE`, … — an unnamed action keeps its number so nothing is lost. */
    fun actionName(actionMasked: Int): String = when (actionMasked) {
        ACTION_DOWN -> "DOWN"
        ACTION_UP -> "UP"
        ACTION_MOVE -> "MOVE"
        ACTION_CANCEL -> "CANCEL"
        ACTION_OUTSIDE -> "OUTSIDE"
        ACTION_POINTER_DOWN -> "PTR_DOWN"
        ACTION_POINTER_UP -> "PTR_UP"
        ACTION_HOVER_MOVE -> "HOVER_MOVE"
        ACTION_SCROLL -> "SCROLL"
        ACTION_HOVER_ENTER -> "HOVER_ENTER"
        ACTION_HOVER_EXIT -> "HOVER_EXIT"
        ACTION_BUTTON_PRESS -> "BUTTON_PRESS"
        ACTION_BUTTON_RELEASE -> "BUTTON_RELEASE"
        else -> "action($actionMasked)"
    }

    /** `stylus`, `mouse`, … — what the tablet says the pointer *is*, which decides every path. */
    fun toolName(toolType: Int): String = when (toolType) {
        TOOL_FINGER -> "finger"
        TOOL_STYLUS -> "stylus"
        TOOL_ERASER -> "eraser"
        TOOL_MOUSE -> "mouse"
        TOOL_UNKNOWN -> "unknown"
        else -> "tool($toolType)"
    }

    /** `0x22:SECONDARY|STYLUS_PRIMARY`, or `-` when no button is down. */
    fun buttons(buttonState: Int): String {
        if (buttonState == 0) return "-"
        val names = buildList {
            if (buttonState and BarrelButtonState.BUTTON_PRIMARY != 0) add("PRIMARY")
            if (buttonState and BarrelButtonState.BUTTON_SECONDARY != 0) add("SECONDARY")
            if (buttonState and BarrelButtonState.BUTTON_STYLUS_PRIMARY != 0) add("STYLUS_PRIMARY")
            if (buttonState and BarrelButtonState.BUTTON_STYLUS_SECONDARY != 0) add("STYLUS_SECONDARY")
        }
        val known = names.joinToString("|")
        return hex(buttonState) + ":" + if (known.isEmpty()) "unknown" else known
    }

    /** The `InputDevice` source bits that decide which stream a key event came from. */
    fun sourceNames(sources: Int): String {
        val names = buildList {
            if (sources and BarrelButtonState.SOURCE_STYLUS_BIT != 0) add("STYLUS")
            if (sources and SOURCE_BLUETOOTH_STYLUS_BIT != 0) add("BT_STYLUS")
            if (sources and SOURCE_MOUSE_BIT != 0) add("MOUSE")
            if (sources and SOURCE_TOUCHSCREEN_BIT != 0) add("TOUCHSCREEN")
            if (sources and SOURCE_KEYBOARD_BIT != 0) add("KEYBOARD")
        }
        return if (names.isEmpty()) "other" else names.joinToString("|")
    }

    private fun appendDevice(builder: StringBuilder, deviceName: String?) {
        if (deviceName.isNullOrBlank()) return
        builder.append(" dev=").append(trim(deviceName))
    }

    // Formatted in the ROOT locale, not the device's: these are numbers to compare against a pen's
    // spec sheet, so they must not come back as "0,42" on a phone set to a comma locale — and the
    // formatting stays testable outside any locale.

    /** `0.42` — two decimals, the precision the pressure is worth reading at. */
    private fun pressure(value: Float): String = "%.2f".format(Locale.ROOT, value)

    /** `1.50x` — a multiplier or a pressure fraction. */
    private fun times(value: Float): String = "%.2fx".format(Locale.ROOT, value)

    /** `0.05` — a bare fraction (the minimum-pressure floor, a pressure). */
    private fun fraction(value: Float): String = "%.2f".format(Locale.ROOT, value)

    /** `1.50pt` — a width in page points. */
    private fun pts(value: Number): String = "%.2fpt".format(Locale.ROOT, value.toDouble())

    private fun trim(name: String): String =
        if (name.length <= DEVICE_MAX) name else name.take(DEVICE_MAX - 1) + "…"

    private fun hex(value: Int): String = "0x" + value.toString(16)

    // Source bits, as `InputDevice` defines them.
    private const val SOURCE_MOUSE_BIT = 0x00002000
    private const val SOURCE_TOUCHSCREEN_BIT = 0x00001000
    private const val SOURCE_KEYBOARD_BIT = 0x00000100
    private const val SOURCE_BLUETOOTH_STYLUS_BIT = 0x00008000
}
