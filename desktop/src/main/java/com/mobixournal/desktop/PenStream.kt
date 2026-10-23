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
