# Stylus input: what a pointer does

> Read when you change the input layer: tool detection, the barrel button, diagnostics, graphics
> tablets and first-launch onboarding.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). How a pointer's
**motion** is read — wheel, palm rejection, pressure → width, smoothing, momentum, multi-finger taps,
telemetry, selection parity — is [gestures.md](gestures.md)'s._

## Stylus & selection

The app is **stylus-first**. `DrawingSurfaceView.onTouchEvent` routes every pointer-down through the
pure `InputClassifier`, so the pen hardware — not the on-screen toolbar — decides what a gesture does,
matching desktop Xournal++. This section is the design home for the input layer; the selection tool's
parity with desktop Xournal++ is [gestures.md](gestures.md)'s.

## One pointer layer, two shells

The input layer's **decisions** are platform-free and live in `mobix-core/render/`: `InputClassifier`
(what a contact begins), `PressureCurve` (how hard it draws), `PageEraser` / `StrokeEraser` /
`StrokeHitTester` (what an erase cuts), and **`InkPointerRouter`**, which turns a stream of
`InkPointer` samples into the events an editor consumes: stroke start/sample/end, a tap, an erase hit,
a two-finger pan and a pinch. Each shell keeps only its **adapter**, and both are live: the tablet's is
`render/DrawingSurfaceRouter.kt` — a `MotionEvent` → `InkPointer`, one per historical sample as well as
one per event — with `MotionEventPointer.kt` reading the platform vocabulary, and the window's is
`desktop/PageCanvasInput.kt` (`PageInkGesture`). What each platform actually fills is *measured*, not
assumed, so the router treats every field as optional: Android fills the tool type, pressure (per
historical sample too), tilt/azimuth and the barrel; the window's Compose pointer fills the tool type,
pressure and its buttons but **no tilt or azimuth** (Compose 1.7.3 has no such axis), so it folds in the
pen's own reading for the same contact — `TabletWintab.kt`'s packets, `TabletInk.kt`'s samples otherwise.

What it decides: a press is held until it crosses the tap slop, so a **tap does not draw** (the tablet's
"a tap leaves a dot" is one flag, `startOnDown`, and the tablet sets it); a **second finger** ends the
stroke it interrupts and starts a pan; once a **pen** owns a gesture, a later finger is a resting palm
and is ignored; an **eraser** contact is reported as a hit, and the split at that hit is `PageEraser`'s.
The Android surface keeps the document-shaped half — page box, guide snapping, `StrokeSmoother`,
hold-to-snap, the commit and its undo step — and the entry points the router does **not** own, each
listed in `DrawingSurfaceRouter.kt`'s header: barrel and vendor keys, the barrel pressed mid-stroke
(`switchStrokeToErase`), page drag, guide drag, spline taps, table/plot double-taps, audio play mode,
vertical space, background select and the marquee.

## Stylus input — implemented

**Stylus input — implemented.** Android reports the source of every pointer via
`MotionEvent.getToolType(pointerIndex)` (`TOOL_TYPE_STYLUS` / `_ERASER` / `_FINGER`) and stylus
side-buttons via `getButtonState()`. The view maps those onto the
device-independent `PointerKind` and calls the pure, JVM-tested **`InputClassifier`** (`PointerKind` +
barrel-pressed + `ActiveTool` + `InputSettings` → `GestureIntent`), keeping the decision logic off the
Android surface so it's unit-testable (`InputClassifierTest`). Precedence, "pen hardware wins over the
toolbar":

1. **Eraser tip.** A `TOOL_TYPE_ERASER` pointer (the flipped-over tip) erases whatever the tool.
2. **Barrel button.** The button held on a pen invokes a configurable action while
   held — default **erase**, **select**, the pen-tip **HUD** or **none** (`BarrelAction`), whatever the
   on-screen tool.
   Which stream reports it depends on the pen and the platform, so detection is deliberately broad
   and latched (pure, JVM-tested **`BarrelButtonState`**, see `BarrelButtonStateTest`):
   `BUTTON_STYLUS_PRIMARY`/`_SECONDARY` on any pointer event; the barrel-button *key* events a
   Bluetooth pen sends on Android 14+ (`KEYCODE_STYLUS_BUTTON_PRIMARY`/`_SECONDARY`, routed from
   `MainActivity.dispatchKeyEvent` because the canvas only takes focus on touch); and a plain
   secondary click from a pen the tablet presents as a mouse (`PointerKind.UNKNOWN`, whose *primary*
   button is its tip and so can never be the barrel — the barrel therefore applies to
   `PointerKind.UNKNOWN` pointers too, or the setting would be dead on exactly the third-party pens
   whose owners report it as broken). A pen whose firmware picks its *own* key code is covered as
   well, and this is the case that mattered in practice: the **Honor Choice Pencil** presents as a
   Bluetooth **keyboard** (`src=0x101:KEYBOARD`) and sends its side button as key code **755**, a code
   Android does not define. `BarrelButtonState.isBarrelKey` therefore accepts, besides the documented
   codes, (a) a key code Android cannot **name** — computed by `isVendorKeycode`
   (`DrawingSurfaceInput.kt`) through `KeyEvent.keyCodeToString`, which answers `KEYCODE_<NAME>` for
   everything the platform defines and the bare number for everything it does not, so an unnamed code
   can only be firmware's own and never a real keyboard key — and (b) a key from a stylus-sourced
   device (`SOURCE_STYLUS_BIT`, shared by `SOURCE_STYLUS` and `SOURCE_BLUETOOTH_STYLUS`) carrying no
   printable character. Every character-producing key, and every named key from a keyboard, is left
   alone. Latching matters because a pen's press edge
   often lands on a hover event and is **not** repeated on the following `ACTION_DOWN` — reading only
   that event would classify the stroke as ink and holding the button would do nothing — while any
   event carrying the real bits overwrites the latch, which therefore can never stick in erase mode.
   A key-sourced press needs one rule beyond that: a pen whose button travels over Bluetooth does not
   mention it in the motion stream at all, so *every* event of the stroke carries no buttons and the
   usual "the motion stream says released" self-correction would drop the button the instant the tip
   landed. `BarrelButtonState.onMotion` therefore ignores a motion event that reports nothing while
   the latch came from a **key**, and that latch is only cleared by the key going up (or by `reset`,
   when the pen leaves hover range); a motion event that *does* report the button bit takes ownership
   back, so the latch can still never stick.
   The held state is re-read *during* a gesture as well: `switchStrokeToErase` turns the rest of an
   in-progress stroke into an erase, matching desktop Xournal++.
   A pen can also do its own gesture recognition: the Honor Choice Pencil swallows a single tap and
   sends **one** key event for a whole physical double-click, so waiting for a second press edge would
   mean the button never does anything. A press that arrives as an **unnamed vendor key** is therefore
   the whole gesture on its own, and `VendorClickGate` (pure, time-injected, `VendorClickGateTest`)
   collapses the edges of one gesture — one or two, depending on the firmware — into a **single**
   action, because two would cancel out a toggle and read as a dead button. Its window is Android's own
   double-tap timeout (300 ms): two whole double-clicks cannot fit inside it, while the two taps of one
   physical double-click always do, so it is safe on either kind of firmware and no real press is
   swallowed. Auto-repeat key presses are filtered before that. A firmware click is also exempt from
   the two rules a *held* barrel follows, both of which would have turned a flaky button into a dead
   one: it does not need the latch to **change** (a pen that never sends the key up would otherwise
   silence every later press for good), and it is not dropped while the pen is on the glass — the
   in-progress stroke is finished with `endGesture()` first, then the action runs, so the click a user
   makes right after erasing lands. The action it runs is recorded in the diagnostics log
   (`CLICK -> toggle eraser`, `CLICK swallowed (same gesture)`), so the panel shows what the app did
   with the press and not only what arrived.
   **`HUD`** turns the same button into the pen-tip ring (`docs/ui-modernization.md`, *The pen-tip
   HUD*): a press with the tip off the glass opens it at the nib, the hover moves the pick, and the
   key-up (or the motion-stream release) commits the slot under the pen or cancels. It is a hover
   gesture, so a tip that lands while the ring is open closes it and writes nothing
   (`DrawingSurfaceHud.kt`).
   **`TOGGLE_ERASER` switches to the eraser the rail's slot is showing** (`preferredEraser`,
   `ToolGroups.kt`): whole-stroke by default, partial if that is what the user picked from the slot's
   menu — shared with the pen↔eraser keyboard shortcut so both toggles land on the same eraser.
   A *double-click* of the same button is a separate, button-only gesture: `BarrelClickDetector`
   (pure, time-injected, `BarrelClickDetectorTest`) recognises two press edges within 350 ms from
   the hover/generic event stream — never while the tip is down, so it can't interrupt a stroke —
   and it only ever sees *edges*: both streams a pen can report its button on pass through the one
   latch first, so a single press reported twice (a motion bit *and* a key event) is one edge, not a
   and runs the configured `QuickAction` (undo/redo on the surface; the tool and full-page
   toggles go to `EditorScreen` through `onQuickAction`; `HUD` opens the ring — its second summon,
   for a pen that keeps the held button on erasing).
3. **Finger-draw gate.** With the Settings **"finger draws"** toggle off, a finger **always pans** and
   actuates no tool at all — pen, highlighter, eraser, text, selection and placement all become
   stylus-only. Palm-safe writing on non-stylus devices. It is **off by default** on a first launch
   (`AppSettings.fingerDraws = false`) so a resting hand never inks the page; the stored preference
   keeps whatever the user last chose.
4. Otherwise the on-screen tool's default intent.

## Pen diagnostics — measuring instead of guessing

**Pen diagnostics — measuring instead of guessing.** Because `PointerKind.UNKNOWN` and the key-code
paths above are claims about hardware the app cannot see, the input layer carries its own instrument
— and it is what settled the Honor pen rather than a guess: **☰ menu → Pen diagnostics** floats a live log over the canvas
(`ui/PenDiagnosticsPanel.kt`), fed by the pure, JVM-tested **`PenInputLog`** (`PenInputLogTest`) whose
lines are formatted by **`PenEventText`** — tool types, actions, button bits, the **raw pressure**
the tablet reports for the pointer, the **tilt and azimuth axes shown in degrees** (a spec sheet quotes
degrees; the document stores the platform's radians), key codes with their names, and the input-device
list with each device's source flags (`DrawingSurfacePenDebug.kt` records the devices when the panel
opens). Pressure and both axes are part of the de-duplication key, so a press that never moves — or a
pen tilted without moving — still shows up.

An axis the tablet does not declare reads `t=-` rather than `t=0.00deg`, because the platform reports
`0` for an axis it lacks and a pen held upright reports `0` too: `StrokeTelemetry` keeps both facts
apart (and asks `InputDevice.getMotionRange` *before* the value, since `getAxisValue` throws for an
axis the device does not have). The same rule decides what a stroke stores, so what the panel shows is
what a `.mobix` would carry — the panel is where the question "does this pen report tilt at all?" is
answered, by the pen in your hand rather than by a guess.

The same panel answers the other "it does nothing" question — *did my setting reach the pen at all* —
with `tracePenParameters()`: `applySettings` (and switching the panel on) logs one `PEN sens/mult/min/
base -> @p/w` line holding the surface's own pressure filter and the width it makes of a half press. A
slider moved in Settings either prints its new value there or never reached the drawing surface, which
turns a guess into a reading.

Every diagnostic figure is formatted in `Locale.ROOT` (not the device locale), so a pressure reads
`0.42` on every phone and the lines stay comparable with a pen's spec sheet. Every hook (`tracePenMotion` in the three motion handlers,
`tracePenKey` in `dispatchKeyEvent`) is a read-only observer placed *before* the routing, so what the
panel shows is what the app really receives, not what the routing made of it; identical consecutive
states are de-duplicated to a key so a long hover is one line, and snapshots are coalesced into one
UI pass (`postPenLogFlush`) so a 100 Hz pen cannot drive 100 recompositions a second. The panel lives
on the canvas rather than in Settings because a settings page layered over it would swallow the very
events being diagnosed — and the one question it exists to answer (does this pen's button reach the
app at all, and as what?) can only be answered where the pen is. Its state is
`EditorUiState.penDiagnostics` (deliberately not persisted — it is a debugging aid, not a
preference), its lines land in `PaneState.penDebugLines`, and back closes it like any other transient
overlay.

## Graphics tablets and ExpressKeys

**Graphics tablets and ExpressKeys.** External USB or Bluetooth tablets (Huion, Wacom, XP-Pen, Gaomon)
send their physical buttons as standard Android `KeyEvent`s, through two paths: **direct detection** —
**Settings → Shortcuts**' `KeyDetectionDialog` listens via `Modifier.onPreviewKeyEvent` for the next
`ACTION_DOWN`, extracts the character with `keyStringFromKeyEvent` (`unicodeChar`, `displayLabel`,
space, digits, numpad, letters) and assigns it to any tool or colour shortcut, manual text input beside
it; and **runtime routing** — `MainActivity.dispatchKeyEvent` forwards unconsumed keys to
`DrawingSurfaceView.dispatchKeyEvent`, which matches them against `toolShortcutKeys` /
`colorShortcutKeys` in `EditorRegions.kt` (the canvas requests focus on touch down to keep it). Two
consequences: a pressure pen and a shape drawn at the fixed nominal width are harmonised with
`AppSettings.defaultShapeSlot` (**Settings → Figures**) or by switching pressure sensitivity off
(**Settings → Stylus**), and with `AppSettings.fingerDraws = false` a tablet's capacitive multi-touch is
navigation only, so a resting palm marks nothing.

## A figure is resized, not thickened

**A figure is resized, not thickened.** The shape tools lay a figure down as one constant-width stroke,
and the width it is laid at is the pen slot the figure tool uses (`AppSettings.shapeWidth`, point 3
above). **Resizing** that figure afterwards is a different act: dragging a selection handle stretches the
*geometry* and leaves every vertex's width alone (`SelectionOps.affineXY(..., scaleWidth = false)`), so an
arrow made twice as long is twice as long and exactly as thin, and a figure shrunk to annotate a detail
does not collapse into a smear. Lengthening an arrow therefore never fattens it **or enlarges its
head**: the V heads of arrows, the axes of the coordinate diagram and every other figure keep the size
they were drawn with while the shaft lengthens.

**A head is sized by the pen, not by the shaft.** `FigureGeometry` — the one home of the three headed
figures (arrow, double arrow, coordinate diagram) — gives every head `6 × strokeWidth` pt, floored at
10 pt and capped at 30 pt, with the single further clamp that a head may never take more than 45% of
its own shaft. Two things follow that the previous fraction-of-the-shaft rule got wrong: arrows of
different lengths drawn with one pen now wear the *same* head (a short arrow is no longer a dart), and
both axes of a coordinate diagram wear one equal head instead of each sizing its own from its own
length — a wide, flat drag used to come out with a big x head above a tiny y head. The V opens at 48°.

**Resizing re-emits the figure, it does not stretch it.** `FigureRedraw` reads a committed stroke back
into its anchors — tail and tip, or the origin and the two axis tips — plus the head length and opening
angle, then rebuilds the whole figure through `FigureGeometry` at the transformed anchors. So a resize
by 1.0 returns the stroke bit for bit, repeated resizes cannot accumulate drift, and a one-axis stretch
turns the head with the shaft without flattening it. The read is deliberately tolerant — the two barbs
may differ by up to a tenth, and the tip counts as repeated within a thousandth of a point — because
the committed stroke is not always the builder's own (the endpoint snap moves the stroke's last vertex,
which on an arrow is a barb), and a head that stops being recognised is a head that starts growing
again.

The one case where the width *does* follow the scale is **re-homing** ink onto another page's frame
(`affineXY`'s default `scaleWidth = true`): that is a change of units, not a resize, and a stroke that
kept its pt width while its page changed size would change thickness on screen. Text boxes keep scaling
their font size in both cases — letters are the figure there, not a stroke.

## First-launch onboarding

**First-launch onboarding.** Fresh installs display `OnboardingDialog.kt` over `EditorScreen`, controlled
by `AppSettings.hasSeenOnboarding`. Its five steps are stylus pressure and barrel-button configuration,
harmonizing figure sizes with handwriting pressure, graphics-tablet ExpressKeys, **handedness**, and the
format a new document is written in (`.mobix` or `.xopp`).

The handedness step (`OnboardingHandedness.kt`) asks the question the rail is really about — *which hand
writes* — rather than which edge to dock to: two cards, a radio set, each carrying a miniature of the
editor with the rail drawn on the edge that hand implies (right hand → **left** edge, the default; left
hand → the **right** edge), so the answer is a hand rather than a coin flip. The last two steps edit the
settings they ask about — `AppSettings.toolbarPosition` and `AppSettings.newDocumentFormat`, through
`onToolbarPosition` / `onDocumentFormat` — and because the editor is composed behind the dialog, the rail
is *seen* moving as a card is tapped rather than described.

Gestures, palm rejection, pressure → width, smoothing, hover, momentum, multi-finger taps, telemetry and
selection parity are [gestures.md](gestures.md)'s.
