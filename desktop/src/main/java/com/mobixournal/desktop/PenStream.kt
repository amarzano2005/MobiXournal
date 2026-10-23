package com.mobixournal.desktop

/**
 * The window's **pen stream**: the newest reading of the pen that is on the glass right now.
 *
 * Two readers implement it, because Windows offers this tablet two different things
 * (`docs/desktop-workstation.md`): `TabletWintab`, the driver's own packet stream and the one that
 * carries pressure on this machine, and `TabletInk`, Windows Ink's `WM_POINTER` samples, which is the
 * fallback for a pen whose driver reports the pointer stream. Both answer `null` when they have no
 * fresh contact, which is exactly the mouse path: the canvas keeps the Compose pointer and draws at the
 * tool's width.
 *
 * The canvas never asks *which* stream answered — `PageInkGesture` folds one [PenContact] into the
 * Compose pointer the same physical contact produced, so a stroke is one stroke whatever the source.
 */
internal interface PenContactStream {

    /** The contact on the glass now, or null when the pen is up (or the reading has gone stale). */
    fun contact(): PenContact?
}

/**
 * One of the tablet's own packets, in the window's pixels — a **stroke sample**, not a reading about one.
 *
 * This is the stream a stroke can be drawn from (`docs/desktop-workstation.md`): the pen's tip, its
 * position and its pressure, delivered by the driver whether or not Windows' mouse emulation bothered to
 * turn the same contact into a pointer event. [x] and [y] are **client** pixels of the window the context
 * was opened on, which is the space a canvas resolves against its own origin.
 */
internal data class PenPacket(
    val x: Float,
    val y: Float,
    /** 0…1, or null when the device declares no pressure axis. */
    val pressure: Float?,
    val tiltRad: Float?,
    val azimuthRad: Float?,
    val rotationRad: Float?,
    /** True while the pen's tip is on the glass. */
    val tip: Boolean,
    /** True when the eraser end is the end on the glass. */
    val eraser: Boolean,
    val atNanos: Long,
)

/**
 * A device that can hand its packets to a canvas, instead of being polled for the newest contact.
 *
 * [listen] answers the function that stops listening: a canvas subscribes while it is composed, so a
 * second page can never receive the first one's strokes.
 */
internal interface PenPacketSource {
    fun listen(listener: (PenPacket) -> Unit): () -> Unit
}
