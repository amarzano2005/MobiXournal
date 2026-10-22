# Stylus input: what a pointer does

> Read when you change the input layer: tool detection, palm rejection, pressure, smoothing, hover, scrolling, gestures, or selection parity.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Stylus & selection

The app is **stylus-first**. `DrawingSurfaceView.onTouchEvent` routes every pointer-down through the
pure `InputClassifier`, so the pen hardware — not the on-screen toolbar — decides what a gesture does,
matching desktop Xournal++. This section is the design home for the input layer and for the selection
tool's parity with desktop Xournal++.

## One pointer layer, two shells

The input layer's **decisions** are platform-free and live in `mobix-core/render/`: `InputClassifier`
(what a contact begins), `PressureCurve` (how hard it draws), `PageEraser` / `StrokeEraser` /
`StrokeHitTester` (what an erase cuts), and — new — **`InkPointerRouter`**, which turns a stream of
`InkPointer` samples into the events an editor consumes: stroke start/sample/end, a tap, an erase hit,
a two-finger pan and a pinch. A shell keeps only what is its own: `MotionEventPointer.kt` maps a
`MotionEvent`'s tool type to `PointerKind` and the surface keeps its state machine. The desktop's
Compose pointer becomes the **second adapter** in `desktop-pressure-inking-through-the-pointer-router`,
and which fields Compose Desktop actually fills on Windows (pressure, tilt) is *measured* by
`desktop-tablet-and-dial-input` rather than assumed — the router therefore treats them as optional.

What it decides: a press is held until it crosses the tap slop, so a **tap does not draw** (the tablet's
"a tap leaves a dot" is one flag, `startOnDown`); a **second finger** ends the stroke it interrupts and
starts a pan; once a **pen** owns a gesture, a later finger is a resting palm and is ignored; an
**eraser** contact is reported as a hit, and the split at that hit is `PageEraser`'s, so a partial erase
has one implementation. **Status: library only** — `DrawingSurfaceInput` still runs its own state machine
over these same decisions, and moving it onto the router is the open task
`wire-the-android-pointer-path-through-the-router`.

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

**Graphics tablets and ExpressKeys.** External USB or Bluetooth drawing tablets (Huion, Wacom, XP-Pen,
Gaomon) send their physical button presses as standard Android `KeyEvent`s. The input pipeline supports
them through two complementary paths:
1. **Direct 1-click detection:** in **Settings → Shortcuts**, `KeyDetectionDialog` listens via
   `Modifier.onPreviewKeyEvent` for the next `ACTION_DOWN` event, extracting the character code via
   `keyStringFromKeyEvent` (supporting `unicodeChar`, `displayLabel`, space, digits, numpad and letters) and assigning it
   immediately to any tool or colour shortcut. Manual text input remains available side by side.
2. **Runtime routing:** `MainActivity.dispatchKeyEvent` forwards unconsumed key events to
   `DrawingSurfaceView.dispatchKeyEvent`, which routes them to `onKeyPressed` in `EditorRegions.kt`, matching
   against `toolShortcutKeys` and `colorShortcutKeys`. `DrawingSurfaceView.onTouchEvent` also requests focus
   on touch down so the canvas maintains key focus.
3. **Fidelity and pressure harmonization:** graphics tablets with pressure-sensitive pens write with
   dynamic line widths governed by `PressureCurve`. Since geometric figures are drawn at a fixed nominal
   width, users can harmonize them by setting `AppSettings.defaultShapeSlot` under **Settings → Figures**
   (choosing a narrower slot like `S` to match handwriting pressure) or turning pressure sensitivity off
   in **Settings → Stylus** to ensure byte-identical uniform widths across both freehand and shapes.
4. **Touch & palm isolation:** with `AppSettings.fingerDraws = false`, tablets equipped with capacitive
   multi-touch reserve touch strictly for navigation (pan/zoom), preventing stray marks from a resting palm.

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

## Mouse wheel

**Mouse wheel.** `ACTION_SCROLL` events from a mouse arrive in `onGenericMotionEvent` and are turned
into a vertical viewport move (`handleWheelScroll` → `ViewportState.scrollBy`, `WHEEL_SCROLL_DP` per
notch). Android reports wheel-down as a *negative* `AXIS_VSCROLL`, so the sign is flipped: wheel down
goes further down the document, the scrollbar's direction rather than a grab-the-paper pan. Zoom is
untouched and the same scroll clamps apply, so a wheel at a bound is left unconsumed
(`MouseWheelInputTest`, on-device — `adb input` can't inject a wheel).

## Palm rejection

**Palm rejection** is the stateful half, handled in the view around the classifier: the active
draw/erase gesture is *owned by a pointer id* (`gesturePointerId`) and only that pointer is sampled
(`addSamples` reads `pointerIndex`, never pointer 0), so a resting palm — a different pointer — can't
perturb the stroke. A stylus/eraser pointer arriving mid-gesture **takes over** any gesture a finger
started (`onPointerDown` → `abandonInProgress`), and once a stylus owns the stroke (`stylusOwner`)
extra finger/palm pointers are ignored rather than treated as a second-finger pan.

## Pressure → width (`PressureCurve`)

**Pressure** feeds width through the pure `PressureCurve`, which is desktop Xournal++'s own mapping
`max(minimumPressure, pressure × pressureMultiplier)` (`PenInputHandler::filterPressure`) with **no
curve and no wide floor of its own** — the two settings are the desktop's, defaulting to its own
`minimumPressure = 0.05` and `pressureMultiplier = 1.0`. Deviating there (a gamma curve, a 0.25 floor)
would make the same physical press store a different width than the desktop and a stroke would look
different in each app, which is exactly what this deliberately does not do. Pressure sensitivity
switched off returns the full width, matching a shape (`PressureCurveTest`).

## Stroke smoothing

**Stroke smoothing** sits between the raw `MotionEvent` samples and those page points, in
`StrokeSmoother.kt`, and is what makes Android handwriting look like the desktop's rather than a
ragged polyline. Two pure pieces:

- `StrokeSmoother` — per-stroke streaming filter over the view-pixel samples, reset in
  `startStroke` and fed by `addSamples`. It exponentially smooths position (α 0.55) and, harder,
  pressure (α 0.3), then **decimates** samples that moved less than `minStepPx` with less than 0.02
  pressure change. A decimated sample still advances the filter, but the decimation distance is
  measured from the last **emitted** point, so a run of sub-threshold steps that adds up to a real
  move still lands a vertex. The newest sample of every batch is `force`d through so the drawn line
  always reaches the pen. `minStepPx` is **scale-aware** (`StrokePrecision.stepPxFor(pxPerPt)`,
  passed to `reset` per stroke): the *tighter* of a 1.6 view-px noise floor and a 0.8 pt
  document-space ceiling. A fixed pixel radius meant a view pixel bought more page at low zoom, so
  a 4-column overview silently discarded ~4× the real pen movement — the "corners and jumps" bug.
- `StrokeSimplifier` — Ramer–Douglas–Peucker pass run once in `commitCurrent` over the finished
  freehand points, dropping vertices within its tolerance of their neighbours' chord. Shape-tool
  output is exact geometry and is exempt. Fewer vertices = smaller `.xopp` and fewer `drawLine`
  calls per redraw. The tolerance is **scale-aware**: `toleranceFor(pxPerPt, precision)` divides
  `TOLERANCE_PX` (0.35 view px) by the page's real pixels-per-point, so the detail thrown away
  stays sub-pixel *on screen* at every magnification — a fixed page-point budget is ~3 view px at
  8 px/pt and facets curves into visible straight segments. `pxPerPt` must be `PageBox.scale`
  (fit-to-width × user zoom), **not** the user zoom alone: on a large tablet fit-to-width is
  already 2–4 px/pt, so keying off the zoom leaves the budget that many times too coarse at 100%
  and below — the faceting bug this replaced. Below 1 px/pt that division runs the other way, so
  the budget is clamped at `TOLERANCE_PT` (0.35 pt): zooming out only ever *tightens* the budget.
- `StrokePrecision` — the user-facing **Stroke precision** setting (Economy / Balanced / High /
  Maximum). Its `factor` multiplies *both* budgets — the simplifier tolerance and the smoother's
  `minStepPx` — so one control moves the whole fidelity-vs-size trade. Both budgets also depend on
  the page's `PageBox.scale`, so `DrawingSurfaceView` computes them per stroke (`startStroke` and
  `commitCurrent`) rather than pinning them when the setting changes.

## Hover, and the on-device stylus tests

Both are covered by `StrokeSmootherTest`. **Hover** (`ACTION_HOVER_MOVE` from a
stylus, via `onHoverEvent`) draws a preview ring where the tip will land. All of these are settings in
`AppSettings`, persisted by `SettingsStore` (SharedPreferences) and pushed live onto the surface by
`EditorScreen.applySettings`; the on-device `StylusInputTest` drives synthetic tool-typed
`MotionEvent`s — carrying a tool type, a button state, a per-sample **pressure** and a **hover**, none
of which `adb shell input` can send and a JVM test cannot build — to prove the wiring: the eraser tip, barrel
erase (motion button *and* the vendor key a Bluetooth pen sends), barrel double-click, the
finger-draw gate, palm rejection, the pressure stream (a rising press stores rising vertex widths,
and **Pressure sensitivity** off stores one width), the **guide hook** (a stylus stroke 8pt off
a setsquare's edge is stored *on* the edge and keeps its offset once the guide is cleared; a ruling
guide pulls every vertex onto the page's own ruling, and clearing it gives the vertex back), the
**select/band gestures** (a pen in Select mode marquees and lasso-traces without laying down ink of
its own, and only what the band encloses is picked), the **hover preview ring** (a hovering tip moves
and clears it, an inverted pen previews the rubber, the setting suppresses it), and **hold-to-snap**
(landing arms the hold timer, a sample past the slop re-arms it while a resting tip leaves it alone,
lift-off drops it, and the armed callback rewrites a held stroke into one clean segment *before* the
lift — a preview the user can keep drawing on). It runs
on any attached device with `scripts/host-build.sh connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=com.mobixournal.StylusInputTest` (or through
`scripts/connected-test.sh` in the container), and it is the only place these paths are exercised:
the pure halves are unit-tested in `InputClassifierTest`, `PressureCurveTest` and `DrawingGuideTest`.
The stroke's vertices are its **motion** samples — the lift-off adds none — and `StrokeSmoother`
low-passes the pressure, so a stored width lags the reading rather than equalling it.
`AppSettings` also carries the **default tool** (`DEFAULT_TOOL_CHOICES` — pen/highlighter/eraser/hand),
which seeds `EditorScreen`'s active-tool state so a document opens in the user's chosen mode.

## Momentum scrolling

**Momentum scrolling.** A pan feeds each focus sample to the pure `VelocityEstimator`; on release the
view runs the release velocity (content-space, opposite the finger, clamped to the platform max)
through `Momentum.seed` and seeds the pure `Fling` with the result, then drives a `Choreographer` frame
loop that decays the velocity exponentially, scrolls `scrollY`/`scrollX` by each frame's step (clamped
by `maxScrollY()`/`maxScrollX()`), and re-`render()`s. It stops when the speed drops below a threshold
or both axes pin to a bound; a fresh touch, cancel, or detach halts it at once. `Momentum.seed` sets
the seed magnitude to `strength · REFERENCE_SPEED_PX · curve.factor(speed / REFERENCE_SPEED_PX)`, where
the `MomentumCurve` (`LINEAR`/`QUADRATIC`/`CUBIC`/`EXPONENTIAL`, user-selectable, default `QUADRATIC`)
shapes how hard a fast flick is rewarded. Every curve is pinned to `factor(0)=0` and `factor(1)=1`, so
a reference-speed flick coasts at its own speed on any curve and only the fall-off below / take-off
above the reference changes — a tiny flick barely drifts while a fast swipe flies many pages (the wide
dynamic range a plain linear scale lacked). **Only a one-finger pan flings:** a two-finger release
resets the estimator when the first finger lifts (to dodge the focus-point jump), so its
near-motionless single-finger tail carries no momentum — the intended feel. Both `Fling` and
`VelocityEstimator` are Android-free (we roll our own estimator because `VelocityTracker` returns
nothing for the synthetic events used in tests), so the kinematics are unit-tested on the JVM
(`FlingTest`, `VelocityEstimatorTest`) and stay frame-rate independent. Because `render()` re-emits
`onScrollChanged`, the scroll thumb tracks the glide live. A `panSensitivity` gain (the
`PanSensitivity` object in `Fling.kt`, default `1.0`) multiplies each pan delta before it hits
`scrollX`/`scrollY` — 1 tracks the finger one-to-one, `<1` pans slower, `>1` faster, `0` freezes the
document — and the same factor scales the seeded release velocity so the fling coasts at the pan's
visual rate.

## Two- and three-finger tap gestures

**Finger gestures: a still two-finger tap and a still three-finger tap, each running the action the
user picked** — undo and redo by default. The tap tracker already used for the double-tap gesture
keeps a per-gesture high-water mark of concurrent pointers (`multiTapMaxPointers`), so a gesture
that never drew, panned or zoomed is classified by **how many** fingers it held: `FingerTap`
(`TWO_FINGER`, `THREE_FINGER`) on the release that takes the last finger off the canvas. The count
has to survive the lift order (`ACTION_POINTER_UP` while one finger stays down), and the whole
gesture is disqualified the moment a pointer moves past the slop (`multiTapMoved`) or the
pinch/scroll paths take it — so a pan or a pinch never fires a tap.

**Detection and meaning are separate.** The detector stops at the finger count; the action comes from
`FingerTapSettings.twoFingerTapAction` / `threeFingerTapAction` (the same `QuickAction` vocabulary the
barrel's double-click uses, minus `HUD` — the ring needs a nib and a release, so the tap pickers filter
it out), resolved by the pure `fingerTapQuickAction()` and run by the surface's one
`runQuickAction()`. Undo and redo are the surface's own, so history, toolbar buttons and the barrel
shortcut stay one implementation; the tool/chrome toggles go to the editor through `onQuickAction`.
Both choices live in **Settings → Input → Finger gestures**, are round-tripped by the settings backup,
and are saved as `twoFingerTapAction` / `threeFingerTapAction`. With both set to `NONE` the detector is
disarmed entirely, because a finger tap that silently rewrites the document is exactly the kind of
thing a user must be able to turn off. An install from before this choice carried one
`multiFingerShortcuts` switch: its **off** loads as both taps `NONE`, its on (or absence) as the
defaults, and a save clears the retired key.

## Telemetry: stored, never drawn

**Telemetry: stored, never drawn.** `.xopp` stores only per-vertex width, so nothing about tilt or
orientation round-trips through it — but a `.mobix` carries a stroke's raw `tilt`, `azimuth` and
per-point `dtMs` ([mobix-elements.md](mobix-elements.md) owns the element schema), and this paragraph
is the **written decision** about what they are for:

- **The width rule is the desktop pressure filter, exactly as above.** Telemetry never feeds the
  drawn width and never feeds the smoother: `StrokeSmoother` stays a function of position and
  pressure, so the same hand movement draws the same stroke whether or not a recording, a tilt, or a
  timestamp was captured. A tilt- or speed-driven width is **out**, and stays out until a later written
  decision moves it — the storage of a value is not a promise to draw with it.
- **`dtMs` exists for replay, not for drawing.** It is what
  [audio-inking-sync.md](audio-inking-sync.md) replays the writing against, point by point.
- **Barrel roll is not capturable here.** Android's `MotionEvent` exposes tilt and orientation and no
  roll axis, so the platform cannot supply it and no field is captured or stored; a `.mobix` from
  another writer that carried one would simply be an uninterpreted field.
- The input layer therefore writes telemetry (a `format/` concern, on the `.mobix` path only) while
  everything it decides about *drawing* stays in `render/`/`ui/`. A `.xopp` write drops the three
  fields, which is stated in [mobix-format.md](mobix-format.md).

## Selection — desktop parity (shipped)

**Selection — desktop parity (shipped).** The tool now covers rectangle **and** lasso select
(separate members of the rail's select slot — `EditorTool.SELECT` / `LASSO_SELECT` — which
`applyTool` turns into the surface's `selectMode`/`lassoMode` pair),
tap-pick, move (including **across pages**), on-canvas **resize** (uniform, corner handles) and
**rotate** (top knob), **cut / copy / paste / duplicate**, and **recolour / re-width**. Every
transform is a pure `SelectionOps` op (`scale`/`rotate`/`restyle`/`moveToPage`/`addToTopLayer`
alongside `translate`/`delete`) and lasso containment is `SelectionTester.inPolygon`, all
JVM-tested. **Rotate is stroke-only by the scope rule:** a stroke bakes rotation into its vertex
coordinates and round-trips, but text/images have no rotation attribute and axis-aligned boxes, so
`rotate` leaves them untouched and the view shows the rotate knob only for an all-stroke selection.
Non-uniform resize is likewise avoided (a text box's font size is a single scalar), so resize is a
uniform scale that keeps every element representable.
