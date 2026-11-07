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

The Compose pan never holds a press-time copy of the offset: each event's travel is applied to the viewport
**as it is now**, because the packets may have moved the same sheet between two of its events. Measuring from
the press instead put the page back where the gesture began — a pan that bounced on every lift rather than
scrolling — so the rule is one writer at a time and always the live offset (`DesktopHandPanTest`).

And the hand is the **slot's** pan, not the emulated mouse's button state: this pen can reach Compose as a
contact with no button bit set, so the Hand slot (or a held Space) pans on any press that is not the right
button — the pen having no other way to scroll is the whole point of the slot (`DesktopHandPanTest`).

The wheel hands the view back when its glide settles: a notch's eased scroll that kept writing its target
on every frame after it had arrived pinned the sheet where the wheel left it, so the hand moved nothing
after the first scroll of a session — a wheel and a hand are two writers of one offset, and the one with a
finished gesture must stop (`DesktopHandPanTest`).

It also pans **further than the pen travels** (`PAN_GAIN`, 1.6): a stylus is moved by the wrist across a
tablet's working area, not by the arm across a desk, so a matched pan reads as a page that lags a hand which
is plainly moving. The gain is a feel constant, not a measurement, and it is the pen's alone — a mouse drag
and a wheel notch keep the distance their device reports. A held **Space** takes the packets from the
stroke path, so a pen in hand never inks the sheet it is scrolling.

Settings ▸ **Scrolling** describes scrolling, not the wheel, so it governs the hand as well. *Scroll
distance* is a distance (`64 dp` is the default and reads as 1:1, `128` as twice the travel): the pointer
drag scales by it, and the pen's packets scale their own `PAN_GAIN` by it — bounded to a quarter and two and
a half, so the slider's ends stay usable rather than making the page uncontrollable. *Smooth scrolling* and
*Scroll fluidity* are the **release**: the hand's flick keeps travelling for a twelfth of a second of its
own speed (smoothed over the last events, the pen measuring its 400 Hz readings), eased with the same decay
a notch glides with and to the same target-based rule — no lead step, because a hand that has just let go
does not need to be answered. Switched off, the sheet stops where the hand left it. Two glides still cannot
fight: each one lets go the moment another writer moves the offset.

## The window's share of the machine

A stroke's smoothness is not the drawing code's property — it is **how soon the thread that receives the
packets is allowed to run**, and on a PC with other processes busy Windows is free to run them first. The
symptom is the one the owner reports (and sees in Xournal++ too, which asks for nothing): the ink lags the
hand and comes out jagged, while the same stroke is perfect on an idle machine. `WindowsScheduling` asks
once, from `main` before the window opens, on four independent levers:

- `timeBeginPeriod(1)` — the system timer resolves to 1 ms instead of ~15.6, so every wait this process
  makes (the packet-staleness timer, the edge-scroll ticker, the message pump) wakes when it meant to;
- `ABOVE_NORMAL_PRIORITY_CLASS` — every thread of the process outranks the normal-priority processes
  competing with it, and still yields to a foreground-critical task elsewhere. Deliberately **not**
  `HIGH_PRIORITY_CLASS`: an editor must not be able to starve the machine;
- **EcoQoS off** — `SetProcessInformation(ProcessPowerThrottling)` with the execution-speed bit cleared, or
  Windows 11 may demote the process's execution speed while another window is in front;
- **the event thread itself**, `THREAD_PRIORITY_HIGHEST` plus an MMCSS *Audio* registration
  (`AvSetMmThreadCharacteristicsW`) — both are per-thread calls, so they are made from inside the AWT event
  thread, which is where a page reflows and a frame paints.

The call is best-effort and reports what it managed (`WindowsScheduling.Report`), because a policy that
refuses is not an error the reader should see; `WindowsSchedulingTest` asserts each lever **lands** on a
Windows machine, which is also what catches a misdeclared JNA signature instead of a silent no-op. There is
no UI for any of it and no setting: the ask is what the input needs, not a preference.

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
