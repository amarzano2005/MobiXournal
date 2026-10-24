package com.mobixournal.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.Window
import kotlin.math.PI

/**
 * **Wintab** — the tablet's own packet stream, and the one this machine actually delivers.
 *
 * The measurement (`--probe-tablet`, `docs/desktop-workstation.md`) settled both attempts that came
 * before: Windows sends this pen **no `WM_POINTER` at all** (every contact arrives as a mouse message
 * with `pressure = 1.0`), while `wintab32.dll` — the driver's own library, in `System32` — declares a
 * full set of axes: `x` 0…50800, `y` 0…31750, **pressure 0…16383**, and an orientation triple at 0…3600
 * tenths of a degree. Xournal++ reads the same pen perfectly because it reads *this* stream, at the
 * tablet's own packet rate, rather than the desktop's coalesced mouse moves.
 *
 * The earlier "Wintab has no axes" reading was a constants bug, not a hardware fact: `wintab.h` puts
 * `DVC_X` at **12** and `DVC_NPRESSURE` at **15**, not at 1 and 7. The indices below are quoted from the
 * header (see the constants on [WintabApi]) and the ones the packet decodes are asserted by
 * `TabletWintabTest`.
 *
 * Like `TabletInk`, this is **not a second input path**: a packet is a *reading*, folded into the
 * Compose pointer the same physical contact produced (`PageInkGesture.inkPointer`), so one contact is
 * still one sample and the stroke is still committed once. A context is opened on the window
 * (`WTOpenA`), its packets arrive as `WT_PACKET` messages through the subclassed window procedure
 * `TabletInk` installed, and each one becomes a [PenContact] kept for 120 ms exactly as the Ink reader's
 * is.
 */
internal interface WintabApi : Library {

    fun WTInfoA(category: Int, index: Int, buffer: Pointer?): Int

    fun WTOpenA(hwnd: Pointer?, context: WintabLogContext, enable: Boolean): Pointer?

    fun WTClose(context: Pointer?): Boolean

    fun WTPacket(context: Pointer?, serial: Int, packet: Pointer?): Boolean

    fun WTPacketsGet(context: Pointer?, maximum: Int, packets: Pointer?): Int

    companion object {
        /** Info categories and the device indices this reader asks about (`wintab.h`). */
        const val WTI_INTERFACE = 1
        const val IFC_SPECVERSION = 2
        const val WTI_DEVICES = 100
        const val DVC_NPRESSURE = 15
        const val DVC_ORIENTATION = 17

        /** The default **system** context: coordinates already mapped to the screen. */
        const val WTI_DEFSYSCTX = 4

        /** `CXO_MESSAGES`: deliver `WT_PACKET` to the window that opened the context. */
        const val CXO_MESSAGES = 0x0004

        /** `WT_PACKET`'s message number (`_WT_PACKET(WT_DEFBASE)`, `WT_DEFBASE` = 0x7FF0). */
        const val WT_PACKET = 0x7FF0

        /** Packet masks: what the context reports, in the order the struct decodes them. */
        const val PK_STATUS = 0x0002
        const val PK_BUTTONS = 0x0040
        const val PK_X = 0x0080
        const val PK_Y = 0x0100
        const val PK_NORMAL_PRESSURE = 0x0400
        const val PK_ORIENTATION = 0x1000

        /** Packet status bits used here. */
        const val TPS_INVERT = 0x0010

        /** `TPS_PROXIMITY`: the pen is inside the tablet's sensing range — hovering or drawing. */
        const val TPS_PROXIMITY = 0x0002

        /** The button number a pen's tip reports in `pkButtons`. */
        const val BTN_TIP = 1

        /** `TU_CIRCLE`: orientation angles are in tenths of a degree. */
        const val TU_CIRCLE = 3
    }
}

/** `AXIS`, as `WTInfoA` fills it. */
internal class WintabAxis : Structure() {
    @JvmField var axMin: Int = 0
    @JvmField var axMax: Int = 0
    @JvmField var axUnits: Int = 0
    @JvmField var axResolution: Int = 0

    override fun getFieldOrder(): List<String> = listOf("axMin", "axMax", "axUnits", "axResolution")
}

/**
 * `LOGCONTEXT`, the context description `WTInfoA` fills and `WTOpenA` takes.
 *
 * Its 172-byte size on x64 is asserted by `TabletWintabTest`: a struct that drifted would make
 * `WTOpenA` open a context out of a field it read as garbage, or none at all.
 */
internal class WintabLogContext : Structure() {
    @JvmField var name: ByteArray = ByteArray(40)
    @JvmField var options: Int = 0
    @JvmField var status: Int = 0
    @JvmField var locks: Int = 0
    @JvmField var msgBase: Int = 0
    @JvmField var device: Int = 0
    @JvmField var pktRate: Int = 0
    @JvmField var pktData: Int = 0
    @JvmField var pktMode: Int = 0
    @JvmField var moveMask: Int = 0
    @JvmField var btnDnMask: Int = 0
    @JvmField var btnUpMask: Int = 0
    @JvmField var inOrgX: Int = 0
    @JvmField var inOrgY: Int = 0
    @JvmField var inOrgZ: Int = 0
    @JvmField var inExtX: Int = 0
    @JvmField var inExtY: Int = 0
    @JvmField var inExtZ: Int = 0
    @JvmField var outOrgX: Int = 0
    @JvmField var outOrgY: Int = 0
    @JvmField var outOrgZ: Int = 0
    @JvmField var outExtX: Int = 0
    @JvmField var outExtY: Int = 0
    @JvmField var outExtZ: Int = 0
    @JvmField var sensX: Int = 0
    @JvmField var sensY: Int = 0
    @JvmField var sensZ: Int = 0
    @JvmField var sysMode: Int = 0
    @JvmField var sysOrgX: Int = 0
    @JvmField var sysOrgY: Int = 0
    @JvmField var sysExtX: Int = 0
    @JvmField var sysExtY: Int = 0
    @JvmField var sysSensX: Int = 0
    @JvmField var sysSensY: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "name", "options", "status", "locks", "msgBase", "device", "pktRate",
        "pktData", "pktMode", "moveMask", "btnDnMask", "btnUpMask",
        "inOrgX", "inOrgY", "inOrgZ", "inExtX", "inExtY", "inExtZ",
        "outOrgX", "outOrgY", "outOrgZ", "outExtX", "outExtY", "outExtZ",
        "sensX", "sensY", "sensZ", "sysMode",
        "sysOrgX", "sysOrgY", "sysExtX", "sysExtY", "sysSensX", "sysSensY",
    )

    companion object {
        /** `sizeof(LOGCONTEXT)` as the driver's own header lays it out on x64. */
        const val SIZE_BYTES = 172
    }
}

/**
 * A packet, in the order the mask's bits put its fields: status, buttons, x, y, pressure, orientation.
 *
 * The mask is [WintabApi]'s own (`PK_STATUS | PK_BUTTONS | PK_X | PK_Y | PK_NORMAL_PRESSURE |
 * PK_ORIENTATION`), so the struct is this shell's declaration of what it asked for rather than a guess
 * about the driver's idea of the layout.
 */
internal class WintabPacket(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var status: Int = 0
    @JvmField var buttons: Int = 0
    @JvmField var x: Int = 0
    @JvmField var y: Int = 0
    @JvmField var pressure: Int = 0
    @JvmField var azimuth: Int = 0
    @JvmField var altitude: Int = 0
    @JvmField var twist: Int = 0

    override fun getFieldOrder(): List<String> = listOf(
        "status", "buttons", "x", "y", "pressure", "azimuth", "altitude", "twist",
    )
}

/**
 * The system context's y axis, in Windows screen pixels.
 *
 * Measured on this machine — and then confirmed in the context itself: `WTInfo` reports
 * `sysOrgY = 0, sysExtY = 1440`, i.e. the system coordinate runs **upward from the bottom** of a 1440-pixel
 * desktop, and a contact the pointer put at screen `y = 415` arrived as system `y = 1025` (`1440 − 1025`).
 * A driver that declares a negative extent has already reversed the axis, and its reading is taken as it
 * comes.
 *
 * This is a reading of the driver's own numbers, not a guess: the earlier "whichever reading lands inside
 * the window" heuristic failed exactly where both readings fit inside a tall window.
 */
internal data class ScreenYMapping(private val bottom: Int, val bottomUp: Boolean) {

    /** The Windows screen pixel for a system coordinate [y]. */
    fun of(y: Int): Int = if (bottomUp) bottom - y else y

    fun describe(): String =
        if (bottomUp) "bottom-up from $bottom" else "already top-down (sysExtY is negative)"

    companion object {
        /** From `LOGCONTEXT.sysOrgY`/`sysExtY`. */
        fun of(sysOrgY: Int, sysExtY: Int): ScreenYMapping =
            if (sysExtY >= 0) ScreenYMapping(sysOrgY + sysExtY, bottomUp = true) else ScreenYMapping(sysOrgY, false)
    }
}

/**
 * The window's Wintab reader: one context, the newest packet, and nothing else.
 *
 * [open] answers null on a host without the driver or without a pressure axis, and the window then keeps
 * whichever stream it had — the status bar says which.
 */
internal class TabletWintab private constructor(
    private val api: WintabApi,
    private val user32: InkUser32,
    private val hwnd: Pointer,
    private val context: Pointer,
    /** How the driver's system y maps onto Windows screen pixels. */
    private val screenY: ScreenYMapping,
    /** The driver's full-scale pressure reading, 0 when it declares pressure but no range. */
    private val pressureMax: Int,
    private val log: (String) -> Unit,
    private val onSample: ((PenContact) -> Unit)?,
    private val onEvent: ((String) -> Unit)?,
) : PenContactStream, PenPacketSource, AutoCloseable {

    @Volatile
    private var latest: PenContact? = null

    /** The canvases listening for packets; a stroke is drawn by the one the pen is over. */
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(PenPacket) -> Unit>()

    override fun listen(listener: (PenPacket) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /** Whether the previous packet had the tip down, so a stroke's first packet can be logged. */
    private var wasTip = false

    /**
     * Whether this driver reports `TPS_PROXIMITY` at all.
     *
     * Only then can a packet mean "the pen has left". A driver that leaves the status at zero would
     * otherwise have every lift read as the pen leaving the tablet, which would hide the ring between
     * two hovering moves.
     */
    private var sawProximity = false

    /** Packets decoded this session, for the probe's counter. */
    @Volatile
    var samples: Int = 0
        private set

    /** Batch size per drain — the driver queues packets faster than one message can be handled. */
    private val batch = com.sun.jna.Memory((WintabPacket().size() * MAX_BATCH).toLong())

    /**
     * Drain a `WT_PACKET`: every packet the driver has queued, newest last.
     *
     * `WTPacketsGet` rather than `WTPacket(hCtx, serial, …)` on purpose. The measurement in the probe
     * showed the message arriving **400 times a second**, and the two arguments the driver sends do not
     * carry a serial this reader can use — decoding them produced no sample at all. Reading the queue by
     * the context handle instead is what Wintab's own documentation recommends for a high-rate device,
     * and it cannot lose a packet to a message that was coalesced away.
     *
     * A lift clears the contact, so a mouse stroke that follows can never wear a stale pressure; a
     * hovering pen reports packets with no tip button, and those are not contacts.
     */
    fun drain() {
        val size = WintabPacket().size()
        val read = runCatching { api.WTPacketsGet(context, MAX_BATCH, batch) }.getOrDefault(0)
        if (read <= 0) return
        for (index in 0 until read.coerceAtMost(MAX_BATCH)) {
            val packet = WintabPacket(batch.share(index.toLong() * size))
            packet.read()
            consume(packet)
        }
    }

    private fun consume(packet: WintabPacket) {
        val tip = packet.buttons and 0xFFFF
        val onGlass = tip and WintabApi.BTN_TIP != 0 || packet.pressure > 0
        val inProximity = packet.status and WintabApi.TPS_PROXIMITY != 0
        if (inProximity) sawProximity = true
        // "Near" is the driver's own answer when it has one; a driver that never sets the bit keeps the
        // pen permanently near, which is the reading that cannot hide a ring that should be showing.
        val near = !sawProximity || inProximity
        val sample = PenContact.of(packet, pressureMax, System.nanoTime())
        if (!onGlass) {
            latest = null
            // The lift is a packet too, and it is what ends a stroke: a canvas that drew from these
            // never waits for the mouse message Windows may or may not send for the same contact.
            emit(packet, sample, tip = false, near = near)
            return
        }
        latest = sample
        samples++
        if (samples % 200 == 1) {
            onEvent?.invoke("wintab sample ${sample.describe()} buttons=$tip")
        }
        emit(packet, sample, tip = true, near = near)
        onSample?.invoke(sample)
    }

    /**
     * A packet's screen coordinates as the window's client pixels, or null when it cannot be converted.
     *
     * The y axis is read through [screenY]'s mapping and then `ScreenToClient` applies the window's own
     * position — so the canvas only has to subtract its own corner.
     */
    private fun clientPoint(x: Int, y: Int): InkPoint? {
        val point = InkPoint().apply {
            this.x = x
            this.y = screenY.of(y)
        }
        return if (runCatching { user32.ScreenToClient(hwnd, point) }.getOrDefault(false)) point else null
    }

    /** One packet to every listening canvas, in the window's client pixels. */
    private fun emit(packet: WintabPacket, sample: PenContact, tip: Boolean, near: Boolean) {
        // The context is the system one, so x/y are screen pixels; the canvas needs client pixels, and
        // the y axis's direction is settled by which reading lands inside the window.
        val point = clientPoint(packet.x, packet.y) ?: return
        // One line at each stroke's first packet: the raw reading beside the converted one, which is how
        // a scale or an origin error in the mapping is seen instead of guessed at.
        if (tip && !wasTip) {
            onEvent?.invoke(
                "wintab stroke start screen=${packet.x},${packet.y} client=${point.x},${point.y}",
            )
        }
        wasTip = tip
        if (listeners.isEmpty()) return
        val next = PenPacket(
            x = point.x.toFloat(),
            y = point.y.toFloat(),
            pressure = sample.pressure,
            tiltRad = sample.tiltRad,
            azimuthRad = sample.azimuthRad,
            rotationRad = sample.rotationRad,
            tip = tip,
            near = near,
            eraser = sample.eraser,
            atNanos = sample.atNanos,
        )
        for (listener in listeners) {
            runCatching { listener(next) }
        }
    }

    override fun contact(): PenContact? {
        val contact = latest ?: return null
        return if (contact.fresh(System.nanoTime(), CONTACT_MS)) contact else null
    }

    override fun close() {
        runCatching { api.WTClose(context) }
        latest = null
    }

    companion object {

        /** How old a packet may be and still belong to the pointer event being handled. */
        const val CONTACT_MS = 120L

        /** Packets read per `WT_PACKET` — a 400 Hz stream queues several between two messages. */
        const val MAX_BATCH = 32

        /**
         * Open a context on [window], or answer null with the reason logged.
         *
         * [onPacket] receives every `WT_PACKET`'s serial number from the window procedure; the caller is
         * `DesktopTablet`, which hands it `TabletInk`'s message hook — one window procedure, two readers.
         */
        fun open(
            window: Window,
            log: (String) -> Unit,
            onSample: ((PenContact) -> Unit)? = null,
            onEvent: ((String) -> Unit)? = null,
        ): TabletWintab? {
            val api = runCatching {
                Native.load("wintab32", WintabApi::class.java) as WintabApi
            }.getOrElse {
                log("wintab: not loadable (${it.message})")
                return null
            }
            val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull()
            if (hwnd == null) {
                log("wintab: the window has no handle yet")
                return null
            }
            val pressure = WintabAxis()
            val pressureBytes = api.WTInfoA(WintabApi.WTI_DEVICES, WintabApi.DVC_NPRESSURE, pressure.pointer)
            pressure.read()
            if (pressureBytes <= 0 || pressure.axMax <= 0) {
                log("wintab: the driver declares no pressure axis")
                return null
            }
            val context = WintabLogContext()
            val filled = api.WTInfoA(WintabApi.WTI_DEFSYSCTX, 0, context.pointer)
            if (filled <= 0) {
                log("wintab: no default context")
                return null
            }
            context.read()
            context.options = context.options or WintabApi.CXO_MESSAGES
            context.msgBase = WintabApi.WT_PACKET
            context.pktData = WintabApi.PK_STATUS or WintabApi.PK_BUTTONS or WintabApi.PK_X or
                WintabApi.PK_Y or WintabApi.PK_NORMAL_PRESSURE or WintabApi.PK_ORIENTATION
            context.pktMode = 0
            context.write()
            val handle = api.WTOpenA(hwnd, context, true)
            if (handle == null) {
                log("wintab: WTOpenA refused")
                return null
            }
            val user32 = runCatching {
                Native.load("user32", InkUser32::class.java) as InkUser32
            }.getOrElse {
                log("wintab: user32 unavailable (${it.message})")
                return null
            }
            val screenY = ScreenYMapping.of(context.sysOrgY, context.sysExtY)
            log("wintab: y ${screenY.describe()} (sysOrgY=${context.sysOrgY} sysExtY=${context.sysExtY})")
            log(
                "wintab: context opened, pressure 0..${pressure.axMax}, " +
                    "rate ${context.pktRate}/s, mask 0x${context.pktData.toString(16)}",
            )
            // The window's own geometry, so a coordinate that looks wrong can be attributed: screen
            // pixels, client pixels and the display's scale factor belong together in one reading.
            // Wrapped, because a window that is not on screen yet has no location to report.
            runCatching {
                val scale = window.graphicsConfiguration.defaultTransform.scaleX
                val insets = window.insets
                log(
                    "wintab: window origin ${window.locationOnScreen} scale=$scale " +
                        "insets=${insets.left},${insets.top}",
                )
            }.onFailure { log("wintab: window geometry unavailable (${it.message})") }
            return TabletWintab(api, user32, hwnd, handle, screenY, pressure.axMax, log, onSample, onEvent)
        }
    }
}

/**
 * Reads one packet in the editor's units, the way `PenContact.of(PenPointerInfo)` reads Windows Ink's.
 *
 * Wintab's pressure is `0..axMax` (16383 on this tablet, fourteen bits), its orientation is three
 * angles in **tenths of a degree** with `altitude` measured up from the tablet's surface — 900 for a pen
 * held upright — and `TPS_INVERT` is the eraser end. Azimuth 0 is `away from the user`, the same
 * convention as Windows Ink's, so a stroke drawn under either reader leans the same way.
 */
internal fun PenContact.Companion.of(
    packet: WintabPacket,
    pressureMax: Int,
    atNanos: Long,
): PenContact {
    val pressure = if (pressureMax > 0) (packet.pressure.toFloat() / pressureMax).coerceIn(0f, 1f) else null
    val hasOrientation = packet.azimuth != 0 || packet.altitude != 0 || packet.twist != 0
    // The altitude's own zero is the glass, so a pen lying on it is 90° from vertical.
    val tilt = if (hasOrientation) ((90 - packet.altitude / 10.0) * PI / 180.0).coerceAtLeast(0.0) else null
    val azimuth = if (hasOrientation) {
        ((packet.azimuth / 10.0) * PI / 180.0).mod(2.0 * PI)
    } else {
        null
    }
    return PenContact(
        pressure = pressure,
        tiltRad = tilt?.toFloat(),
        azimuthRad = azimuth?.toFloat(),
        rotationRad = if (hasOrientation) ((packet.twist / 10.0) * PI / 180.0).toFloat() else null,
        eraser = packet.status and WintabApi.TPS_INVERT != 0,
        rawPressure = packet.pressure,
        // The same bits `PenContact.mask` uses for Windows Ink — pressure, rotation, tilt x and y — so
        // one diagnostics log reads both streams.
        mask = 0x01 or (if (hasOrientation) 0x02 or 0x04 or 0x08 else 0),
        atNanos = atNanos,
    )
}
