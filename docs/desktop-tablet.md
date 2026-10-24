# The desktop tablet: Wintab packets, Windows Ink, and the dial

> Read when you change how the desktop window reads the pen or the dial: the two Windows streams, the packet path that draws a stroke, the ink-latency probe, or the radial controller's COM handler.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). The window that uses these readers is [desktop-workstation.md](desktop-workstation.md)._

## The tablet on Windows: Wintab, Windows Ink, and the dial

Four readers sit beside Compose — `desktop/TabletWintab.kt` (the pen's packet stream), `desktop/TabletInk.kt`
(the same pen through Windows Ink, when a driver offers it), `desktop/TabletWheel.kt` (which wheel turned)
and `desktop/TabletDial.kt` (a radial controller, if one is ever bound) — opened by `DesktopTablet` from
`DesktopApp` and closed with the window. The pen's stream **draws** the stroke (`PenPacketStroke`, below)
while the mouse keeps Compose's own pointer events; what the other readers add is a reading for the event
being handled and the dial's notch as a zoom. `DesktopTablet` asks **Wintab first**, then Windows Ink, and
the status bar names whichever answered: `pen: Wintab`, `pen: Windows Ink` or `pen: mouse`.

**What was measured, not assumed** (`--probe-tablet`, `Main.kt`, writing `tablet-probe.csv`):

- The pen reaches Compose as a **mouse** — `type=Mouse`, `pressure=1.0000`, no tilt — so no amount of
  Compose code can recover the pressure. AWT's pointer model has no pen in it.
- **Wintab is the stream this machine actually has, at 400 packets a second.** `wintab32.dll` in
  `System32` (the driver's own) declares a full set of axes: `x` 0…50800, `y` 0…31750, **pressure
  0…16383** (fourteen bits) and an orientation triple at 0…3600 tenths of a degree. A context opened on a
  real window at **400 packets/s** with `CXO_MESSAGES`, mask `0x15C2`. The earlier "Wintab has no axes at
  all" reading was a **constants bug**, not a hardware fact: `wintab.h` puts `DVC_X` at **12** and
  `DVC_NPRESSURE` at **15**, and the first probe asked for indices 1 and 7. That is also why Xournal++
  draws this pen perfectly while this window did not — it reads this packet stream, not the desktop's
  coalesced mouse moves.
- The pen's real readings are in `WM_POINTER`: `TabletInk` subclasses the window procedure
  (`SetWindowLongPtrW`), reads `POINTER_PEN_INFO` through `GetPointerPenInfo` (pressure 0–1024, rotation
  0–359°, tiltX/tiltY −90…+90) and **passes every message on** with `CallWindowProcW`, so AWT behaves
  exactly as before. A sample counts only while `POINTER_FLAG_INCONTACT` is set, and a contact is "fresh"
  for 120 ms — Windows sends `WM_POINTER` *before* the mouse message it promotes the same contact to, so
  the newest contact is the one that belongs to the pointer event being translated. The two clocks are
  never compared (AWT's event time and `GetTickCount` share no epoch); freshness is
  `System.nanoTime` since the message arrived.
- **No `WM_POINTER` arrived on this machine at all**, with the pen down or hovering: every sample the
  probe wrote is a mouse message (`pointer,Move,…,1.0000,Mouse`), which is the same fact as the first
  bullet seen from the other side — the driver reports the pen as a mouse to the pointer stack, so
  Windows has no pointer stream to send. The Ink reader is therefore **live but idle** here: it installs,
  sees the mouse messages, finds no `PT_PEN`. It is kept as the fallback for a driver that does report the
  pointer stream, and it is what carries the pressure on such a host.
- **The queue is read, not the message.** `WT_PACKET` arrives about **400 times a second**, and the two
  arguments it carries gave this reader no usable serial (decoding them produced **zero** samples, while
  the probe logged 2 122 of those messages): `WTPacketsGet` by the context handle is what Wintab
  documents for a high-rate device, and it cannot lose a packet to a coalesced message. Two strokes on
  the probe then decoded 328 samples with `raw` pressure from **106 to 15 156** of 16 383 and a tilt that
  varied with the hold — a full ramp, which is what `PressureCurve` expects.
- **One window procedure, two readers.** `TabletWintab` opens its context on the same window and Wintab
  delivers packets as `WT_PACKET` (0x7FF0) to whoever subclassed it, so `TabletInk`'s procedure hands
  every message on to a hook instead of a second procedure fighting it for the window. Each packet is
  decoded in the mask's own bit order (status, buttons, x, y, pressure, orientation) into the shared
  `PenContact` — fourteen-bit pressure over the driver's declared range, altitude measured up from the
  glass (900 = upright) into a tilt, azimuth and twist in radians, `TPS_INVERT` for the eraser end — and
  kept for 120 ms, the same freshness rule the Ink reader uses. A lift clears it, so a mouse stroke that
  follows never wears a stale pressure; a hovering pen reports packets with no tip and is not a contact.
  Both readers implement `PenContactStream`, and `PageInkGesture` folds whichever answered into the
  Compose pointer, so a stroke is one stroke and its width is the shared filter's answer either way.
- **Both ends of the stroke are the packets', not the mouse emulation's.** Waiting for a pointer press
  was the bug the owner could feel: Windows' emulation is a *copy* of the contact, so a light touch was
  sometimes dropped before any stroke existed. `PenPacketStroke` takes the tip-down, every sample and the
  lift straight from the packet stream — the same contact, at the tablet's own 400 Hz. The stroke's
  **begin and end are therefore the driver's**: it is born from a tip-down packet and committed by the
  lift packet, and the Compose pointer stands aside for it (`pageGestures`'s `penActive`, so one contact
  is one stroke and never two). The two paths keep **separate drafts** (`PageCanvas` holds the pointer's,
  `PenPacketStrokes` the pen's), because a cancelled mouse gesture clears its own draft: sharing one list
  let the emulation's release wipe the samples of the stroke the tablet was still drawing, which is a
  light, quick contact losing its ink. It only *converts*: a packet becomes an `InkPointer` in the
  canvas's pixels (client coordinates minus the canvas origin) and goes to the same `PageInkGesture`, so
  the shared router still decides what a press means, `PressureCurve` still sizes the width, the eraser
  end still rubs out and the lift still commits one edit. A packet off the sheet starts nothing. A pen
  resting on the glass sends **no** packets at all (the driver reports changes), so silence is not a
  lift: a stroke may go quiet for `PenPacketStroke.STALE_MS` (2 s) and keep drawing, and a silence longer
  than that **finishes** it where it stopped — committed, not thrown away — while a tip-down after the
  silence begins the next stroke instead of stitching onto the old one. `PenPacketStrokeTest` and
  `PenPacketSceneTest` prove it headless: a tip-down ramping to a full press produces one tapered stroke
  with no pointer press anywhere in the test, and with the emulated mouse pressing, moving past the tap
  slop and releasing *around* the same contact the page still gets exactly one stroke, the packets'.
- **One publish per frame, and the latency measured** (`--probe-ink`, `InkProbe.kt`, writing
  `ink-probe.csv`). The driver sends 400 packets a second while the window repaints at the screen's own
  rate; handing every packet to the gesture made 400 snapshot writes and 400 invalidations for one
  frame's ink. `PenPacketStroke` now buffers the moves and `flush()`es them from the canvas's
  `withFrameNanos` ticker — every filtered sample in order, with its own width, so the drawn line is
  identical — while the **tip-down stays immediate**, because it is the sample that starts the stroke
  and tells the mouse path to stand aside; a lift or a silence flushes the tail before it commits, so a
  stroke never loses its last millimetres to the frame it ended on. `PenPacketStrokeTest` pins the batch
  rule beside the existing stroke tests.

  That hand-off crosses two threads — packets are delivered on the **window procedure's thread**, the
  gesture is drawn by the **frame ticker** — and the first two shapes of it both failed on the running
  window, which is why the third is the one in the code:

  1. **Straight to the gesture from the driver's thread** put Compose's snapshot writes on a thread that
     does not paint, and lost samples: a `kotlin.collections.ArrayDeque` drained while another thread
     wrote it hands back `null` (measured: ~350 000 nulls in half a second of two threads hammering one),
     the null passed the compiler's cast at the call site, and the window put a dialog over the page —
     "parameter specified as non-null is null: `PageInkGesture.move`, parameter `pointer`".
  2. **A monitor around the entry points** stopped the nulls and froze the window instead: the driver's
     thread took the lock and then wrote snapshot state, while the frame thread waited for the lock
     *inside* a frame that already held the snapshot's — a lock inversion that stalled on the first stroke.
  3. **The queue** is what ships: `packet()` appends to a `ConcurrentLinkedQueue` and does nothing else —
     no state, no lock, no gesture — while `flush()` on the frame thread drains it and runs the whole
     state machine (smoothing, the router, the draft, the commit). The driver's thread can never wait on
     a frame, and the gesture is touched by exactly one thread. A tip-down is therefore drawn by the
     frame that follows it, together with the samples queued behind it, which is the same frame that
     would have painted them anyway.

  `PenPacketStrokeConcurrencyTest` hammers both threads at once (no `null`, strokes still committed) and
  asserts the shape of the hand-off — `packet` not `synchronized`, the inbox a
  `ConcurrentLinkedQueue` — because a rare race would otherwise be caught only by luck.
  `PenPacketStrokeTest` asserts the same rule from the other side: a packet with no frame behind it draws
  nothing at all.

  The probe paints with the editor's own `PageCanvas` and feeds the real `PenPacketStroke`, recording for
  every sample the packet's time and the `nanoTime` of the frame that first drew it. On a host with no
  tablet the packets are synthetic (400 Hz, the same rate the driver reports), so the numbers below
  measure the canvas, not a driver; on the owner's PC the same window takes the Huion's own stream.
  The first run on the Windows 11 laptop (`i7-12650H`, display refresh the bound this host can measure)
  measured the blank A4 page at **median 12.9 ms / p95 17.9 ms**, but `udiff.xopp`'s dense first page at
  **median 17.7 ms / p95 29.4 ms / max 47.6 ms** — and that gap *was* the felt lag: every frame re-painted
  the whole sheet (paper, picture, every living entity compiled through `LivingInk`, every text box
  measured, all four thousand strokes), which on the owner's PC is a stall the hand reads as the line
  falling behind the nib.

  **The page is now rasterised once** (`PageRaster.kt`) for the viewport in force and **blitted** while a
  stroke is drawn, so a frame costs one image plus the stroke in progress however much ink the document
  holds. The cache is not a second renderer: it runs the same `drawPageContent`, and `PageRasterTest`
  compares cached and direct frames pixel by pixel (and pins the reuse: one rasterisation per still
  viewport, one more per scroll). Measured again on the same host, same synthetic 400 Hz stream: blank A4
  **median 13.5 ms / p95 18.3 ms / max 29.4 ms**, `udiff.xopp` **median 13.6 ms / p95 19.8 ms / max
  29.0 ms** — the dense page now costs the same as the blank one, because what remains is the frame
  interval itself (a 60 Hz display quantises a packet to its next vsync before any paint happens).
  The task's target — one frame at 144 Hz, 6.9 ms — is set for the owner's 144 Hz PC, so it cannot be met
  or disproved on this display; painting the stroke's head at the raw packet position while the filtered
  body trails was not taken, because it would trade a clean line for less than a millisecond while the
  frame interval dominates. Dirty-rect pacing for scrolling and zooming stays
  `desktop-144hz-render-pacing`'s, and the owner's live pass on the Huion is what confirms the 144 Hz
  number.
- **The pen gets a pen's pointer.** Over the page the desktop's arrow is swapped for a ring the size of the
  line the tool will write (`DesktopCursor.kt`): the system cursor becomes an invisible AWT one while a
  drawing tool is active — so every hit test and hover event still works — and `drawToolCursor` paints the
  ring at the pointer, in the window's own tokens ([`DesktopTheme`]`desk` under [`DesktopTheme`]`ink`, so
  it reads on paper, on the black desk and over ink of either colour). Its diameter is the stroke's stored
  width × zoom × density with a 7 px floor, and the **eraser's** ring is its radius doubled
  (`eraserRadiusPt`), not its width — a ring that said 4 pt over a 12 pt stroke would be worse than the
  arrow. A tool that draws nothing keeps the arrow.

  The ring is **this window's own ink**, so it also had to stop being driven by the mouse: Windows'
  emulation is a coalesced *copy* of the contact, which put the ring a frame or two behind the nib. It is
  now placed from the driver's readings (`PenPacketStroke.publishHover`, one update per frame with the
  newest one), for a hovering pen as well as a drawing one. Silence is not absence — a driver reports
  changes, so a pen held still above the glass sends nothing and the ring stays put; the packet that
  says the pen has left the tablet's proximity clears it, and a mouse move takes the ring over once the
  readings stop (`HOVER_STALE_NANOS`).
- **The dial is a wheel, not a pointer.** Turning it sends `WM_MOUSEWHEEL` with `delta=+120` — the mouse
  wheel's own message, its own unit, no modifier, and `GetMessageExtraInfo` empty (`extra=-`), so the
  extra-info signature does not name the device either. The WinRT `RadialController` route therefore has
  nothing to bind to here (`TabletDial` still binds one and adds its **MobiXournal** item, so a real
  radial controller would work; its rotation becomes `DesktopShellState.dialZoom`, `exp(0.002 × degrees)`,
  and its button `dialFit`).
- **Raw Input is where the two wheels part company.** `WM_INPUT` carries the `RAWINPUTHEADER.hDevice` the
  report came from, and `GetRawInputDeviceInfoW` turns that handle into the device's interface path
  (`\\?\HID#TABLETHID&COL01#…` for this tablet's virtual mouse, `\\?\HID#VID_046D…` for the Logitech),
  which is the one difference the message itself loses. `TabletWheel` registers for the mouse's raw
  reports with `RIDEV_INPUTSINK` (so the dial works without focus), decodes `RAWMOUSE` — 24-byte header,
  16-byte device block, 24-byte `RAWMOUSE`, asserted against the SDK's sizes — and calls a notch the
  dial's when its path is the tablet's. Raw input is **observational**: the same notch is still the
  `WM_MOUSEWHEEL` Compose receives, and the canvas asks `DesktopTablet.dialWheelRecently()` whose wheel it
  was. The dial's notch takes the zoom path at the cursor (`wheelViewport(…, forceZoom = …)`) with no
  `Ctrl` held; the mouse's wheel keeps scrolling. One notch, one action, whichever way round the two
  messages arrive. `DialWheelSceneTest` pushes both through a headless `ImageComposeScene`: the dial's
  notch zooms in and leaves the fit, the mouse's scrolls and keeps the zoom.

The calls are **COM by vtable slot**, because a JVM has no C++ headers: the slots and the interop GUIDs
are quoted from the SDK's generated `windows.ui.input.h` and `RadialControllerInterop.h` in the file's
constants. The handler is a JVM-implemented WinRT object, and three measurement runs found what it has to
be — each one is a comment at the slot it fixed:

1. **seven slots**, `IInspectable` + `Invoke`: a four-slot table died with `Invalid memory access` when
   WinRT asked it for `GetIids`;
2. `IMarshal` answered by a **free-threaded marshaler** (`CoCreateFreeThreadedMarshaler`), not by writing
   the interface out;
3. a **whitelist** in `QueryInterface` — `IUnknown`, `IInspectable`, `IAgileObject`, `IMarshal`, and the
   two delegate IIDs the probe logged — with `INoMarshal` and `IdentityUnmarshal` refused. Claiming
   `INoMarshal` (a promise never to be marshaled) produced `CO_E_NOT_SUPPORTED` from both registrations,
   and claiming `IdentityUnmarshal` sent COM into slot 6 of the table, which holds `Invoke`.

The live run registers both events and reports it: `dial: rotation hr=0x00000000, button hr=0x00000000`.
The window says what it has in the status bar — `pen: Wintab`, `pen: Windows Ink` or `pen: mouse`, plus `dial` while a
controller is bound — so "the pen draws at one width" and "the pen is a mouse as far as this window can
tell" can never be confused. With no device, both readers answer null after logging the step that said
so, and the window keeps the wheel, the keyboard and the mouse.
