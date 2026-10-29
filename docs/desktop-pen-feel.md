# The pen's feel in the window: its active area, the hand's pan, and the measurement

> Read when you change what the pen *feels* like in the desktop window: the Wintab active area, the
> pan's gain, or the on-screen latency measurement against Xournal++.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). The packets, the
Ink reader and the dial are [desktop-tablet.md](desktop-tablet.md)'s; the window that uses them is
[desktop-workstation.md](desktop-workstation.md)._

## The active area: a context of the window's own

The driver's **system context** (`WTI_DEFSYSCTX`) is the surface every other program sees, and its
coordinates arrive as screen pixels, which is why the canvas converts them with `ScreenToClient`. Settings'
*Tablet* area needs the other kind: `TabletWintab.deviceContext` narrows the context's `lcInOrg`/`lcInExt` to
the chosen rectangle of the device axes (`DVC_X`/`DVC_Y`, 0…50800 × 0…31750 here) and points `lcOut` at the
window's client area with a **negative y extent**, so packets then arrive as client pixels and the canvas
does not convert them again (`systemSpace` in the reader). The one field that has to be *cleared* is
`CXO_SYSTEM`: the default system context sets it, and a screen-mapped context ignores `lcIn`/`lcOut`
entirely — the area would be written and never read.

Every failure is the fallback and not a dead pen: no loadable library, no declared axis, no client area yet
or a refused `WTOpenA` all keep the system context, and the log says which step said so. The rectangle's own
arithmetic is `TabletAreaMap`, pure and covered by `TabletAreaTest`; the percentages are of the **surface**,
which is what makes the aspect lock one comparison (`docs/desktop-settings.md`).

## The hand on the pen

A pan built on Compose's pointer crawled, because Windows' copy of this pen is coalesced: the driver sends
400 readings a second and the mouse emulation delivers a handful. So the **hand** pans from the same packets
the stroke does (`PenPacketPan`): a tip-down opens the pan and records where it landed, every later reading
adds its travel — the packet delta over the display's density — and the whole batch is **one** viewport
write per frame instead of one per reading (`PenPacketPanTest` pins the distance, the single write and the
lift's own travel).

It also pans **further than the pen travels** (`PAN_GAIN`, 1.6): a stylus is moved by the wrist across a
tablet's working area, not by the arm across a desk, so a matched pan reads as a page that lags a hand which
is plainly moving. The gain is a feel constant, not a measurement, and it is the pen's alone — a mouse drag
and a wheel notch keep the distance their device reports. A held **Space** takes the packets from the
stroke path, so a pen in hand never inks the sheet it is scrolling.

## Against Xournal++: one measurement, both programs

The probe above times the window's own path (packet → painted). What a hand feels is one step further
out — pointer moved, ink **on the screen** — and that is the only number the same yardstick can take
from two programs, because Xournal++ has no probe to instrument. `scripts/desktop-latency.ps1` drives
short strokes with synthetic pointer messages and polls a small box around each target for the first
dark pixel, so its answer carries the app's processing, its frame, the compositor's present and a
fraction of a millisecond of probing. Both programs were measured through that one loop, on this PC
(3440 × 1440 at 120 Hz), on a blank A4 page, with the pen slot clicked first in each:

| pointer → visible ink (n = 40 each) | median | p95 | mean | min | max |
|---|---|---|---|---|---|
| **MobiXournal** (packaged `.exe`) | **16.9 ms** | **31.5 ms** | 16.9 ms | 4.2 ms | 34.9 ms |
| **Xournal++ 1.3.8** | **16.4 ms** | **40.5 ms** | 19.7 ms | 11.6 ms | 40.5 ms |

Read literally: at the median the two land in the same place, **two 8.33 ms frames** — cursor move,
one frame to paint, one to present — so this metric cannot separate them. The difference is in the
**tail and the floor**: MobiXournal reaches a single frame at its best (4.2 ms) where Xournal++'s
floor is 11.6 ms, and its worst case is 34.9 ms against 40.5 ms. The mouse is a *proxy*: the tablet
cannot be automated, so these numbers say nothing about the Gaomon WH851's own path, whose packet → painted
figure is the 1.48 ms median above and which is measured at a different boundary (before the present).
