# Gestures and pen feel: the wheel, the palm, momentum, multi-finger taps

> Read when you change how a pointer's *motion* is read: wheel scrolling, palm rejection, pressure →
> width, smoothing, hover, momentum, multi-finger taps, telemetry or selection parity.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). What a pointer
**is** — tool detection, the diagnostics panel, graphics tablets, onboarding — is
[stylus.md](stylus.md)'s._

## Mouse wheel

**Mouse wheel.** `ACTION_SCROLL` events from a mouse arrive in `onGenericMotionEvent` and are turned
into a vertical viewport move (`handleWheelScroll` → `ViewportState.scrollBy`, `WHEEL_SCROLL_DP` per
notch). Android reports wheel-down as a *negative* `AXIS_VSCROLL`, so the sign is flipped: wheel down
goes further down the document, the scrollbar's direction rather than a grab-the-paper pan. Zoom is
untouched and the same scroll clamps apply, so a wheel at a bound is left unconsumed
(`MouseWheelInputTest`, on-device — `adb input` can't inject a wheel).

## Palm rejection

**Palm rejection** is the stateful half. The router owns the rule where a contact begins: a pen outranks
a hand that landed first (its gesture is abandoned, not finished), and once a pen owns the gesture a later
finger is dropped. The view keeps the document-shaped half — the active draw/erase gesture is *owned by
a pointer id* (`gesturePointerId`) and only that pointer is sampled, never pointer 0 — so a resting palm
can't perturb the stroke. A pen arriving mid-gesture still runs `onPointerDown` → `abandonInProgress`,
which also resets the router so it holds no stale contact.

## Pressure → width (`PressureCurve`)

**Pressure** feeds width through the pure `PressureCurve`, which is desktop Xournal++'s own mapping
`max(minimumPressure, pressure × pressureMultiplier)` (`PenInputHandler::filterPressure`) with **no
curve and no wide floor of its own** — the two settings are the desktop's own, because deviating would
make the same press store a different width in each app. Pressure sensitivity off returns the full
width, matching a shape (`PressureCurveTest`). There is **no curve on top of it and no second floor**, on
either shell: the preset numbers a phone stores are the numbers the window draws with
(`PressureParityTest`). What each input path does first is `PressureCurve.reading` — its own full scale
divided out (Wintab's `0..axMax`, Windows Ink's `0..1024`, Android's `0..1`) — so an equal reading is an
equal width before the filter sees it.

## Stroke smoothing

**Stroke smoothing** sits between the raw `MotionEvent` samples and those page points, in
`StrokeSmoother.kt`, and is what makes Android handwriting look like the desktop's rather than a
ragged polyline. Two pure pieces:

- `StrokeSmoother` — per-stroke streaming filter over the view-pixel samples, reset in
  `startStrokeFrom` and fed by `extendStrokeFrom`, one call per sample the router passes on. It
  exponentially smooths position (α 0.55) and, harder,
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
of which `adb shell input` can send and a JVM test cannot build — to prove the wiring (the window's
half of the same wiring, the Compose pointer through the router, is the JVM `DesktopPointerSceneTest`): the eraser tip, barrel
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
