# Shape perfection engine — when a stroke snaps, and to what

> Read when you change the dwell trigger, the tremor filter, a recognised class or one of its thresholds.

**The question this answers:** a freehand stroke has just been drawn (or the stylus is resting on the
glass) — is it a primitive, which one, and by what test? This spoke owns the shape recogniser: the
trigger, the sample filter, the classes and the thresholds. The *tools* — which tool recognises, the
Settings switch, the hold-to-snap gesture and its timer — are owned by
[`drawing-tools.md`](drawing-tools.md).

Everything here is live: the recogniser runs on lift-off in the Android editor and on the 300 ms
dwell, on the pen and the highlighter alike.

## The trigger

| | |
|---|---|
| Hold before snapping | `SHAPE_DWELL_MS` = **300 ms** of stillness |
| "Still" means | the tip travels **under 3 pt** (page space) between samples |
| Shortest stroke ever snapped | **40 pt** across the stroke's bounding diagonal |
| Switch | Settings → Drawing → **Shape recognition** (`recognizeShapes`), one switch for both triggers |

The timer is re-armed by every movement past the slop, so a hand that keeps writing never snaps
mid-letter; 300 ms is above the stalls inside a word and below the pause a person makes when they
mean "make this a shape". A hold is a **preview**: nothing is committed until lift-off, and drawing on
gives the recogniser a longer stroke.

## The tremor filter

`StrokeSmoothing` runs before the fit on a copy of the samples: an exponential moving average
(α = 0.5) whose displacement is clamped to 1.5 pt.

- The **average** is what makes the thresholds meaningful on real input: a stylus reports every few
  milliseconds and a resting hand trembles, and the straightness measure below reads that tremor as
  curvature — a drawn line can otherwise sit just above the line threshold and be left as drawn.
- The **clamp** is what keeps corners. An unclamped average lags every turn, so a triangle's vertex
  drifts back along the incoming edge and the corner detector stops seeing a corner.
- The **first and last** samples are never filtered: the stroke must start and end where the tip did,
  and the line test measures the tip's stray from the fitted line.

Only the recogniser sees the filtered copy. What commits is either the geometry a fit *emits* or the
raw samples untouched.

## The pipeline

Each stage rejects rather than guesses; a stroke that matches nothing commits as drawn.

| Order | Class | File | Accepts when |
|---|---|---|---|
| 1 | **line** | `ShapeRecognizer` | one straight segment, `Inertia.det` < 0.015 (a piece of a polygon is allowed 0.045); a near-horizontal or vertical fit is snapped flat, a diagonal keeps its extent unless the tip strayed more than √15 pt from the fitted line |
| 2 | **triangle** and **rectangle / square** | `ShapeRecognizer` | a ring of three or four fitted sides **that also agrees with the ink** (see below); a rectangle's corners must be within 15° of square with gaps within 0.20, and a tilt under 5° is dropped to the page axes, which is what squares an upright hand rectangle |
| 3 | **triangle** / **rectangle** again, from the corner fallback | `PolygonCorners` | the piecewise fit did not produce a ring that agrees: Ramer–Douglas–Peucker finds the corners and the same rebuild and agreement test run on them |
| 4 | **arrow** | `ArrowRecognizer` | a straight shaft ≥ 60 pt with ≤ 10% bow, two barbs 10–55% of its length (within 2.5× of each other) not past the point, and the stroke returning to the point between them |
| 5 | **circle** | `CircleRecognizer` | roundness `det` > 0.95 and a length-weighted radial residual < 0.10 × the radius |
| 6 | **ellipse** | `EllipseRecognizer` | **closed** (ends within 30% of the bounding diagonal), axes 1.25–6× apart, mean rim residual ≤ 0.10 and no single sample above 0.45 |
| 7 | **arc** | `ArcRecognizer` | **open**, chord ≥ 60 pt, sweep 100–300°, mean distance from the fitted circle ≤ 6% of its radius, radial spread ≤ 35% |

The polygon fit is desktop Xournal++'s own (`findPolygonal` recursion on `Inertia.det`, then
`optimizePolygonal` nudging each break to its local optimum), and the emitted geometry comes from the
fitted lines' **intersections**, so it is cleaner than anything read off the samples. Every tolerance
is relative to the stroke's own size (`RecoSegment.radius`), never an absolute pt budget, so the same
wobble passes on a small circle and a page-sized one.

## The agreement test, and why a rectangle used to become a triangle

Upstream reads the **topology** of the fit as proof: three segments that close are a triangle. That is
the single worst thing the port did on a tablet. The splitter guesses an even split first and grows one
straight piece out of it, so a squat rectangle drawn in one stroke — the wide, shallow box most people
make — can run out of search budget one corner short. Its three fitted sides still close, through the
fourth side's chord, and the recogniser emitted a **triangle**: it snapped, and it was wrong.

So a rebuilt polygon is now measured against the ink that produced it before it is emitted
(`ShapeRecognizer.agreement`), against three tests:

| Test | Threshold | Why |
|---|---|---|
| mean distance from the samples to the polygon | ≤ 3% of the diagonal | the polygon is where the ink is |
| 90th-percentile distance | ≤ 6% of the diagonal | a hand-drawn corner usually overshoots, and a few samples must not veto the fit — a *missing side* moves far more than a tenth of them |
| every corner ≥ 12°, every side ≥ 5% of the diagonal | — | a sliver is not a figure |

Both classes are asked, and the **closer** one wins rather than the first one in a cascade. When the
fit agrees with neither, `PolygonCorners` steps in: RDP over the closed ring (epsilon 2% of the
diagonal) finds the corners the piecewise fit missed, every vertex nearly collinear with its
neighbours is merged away (the seam — the stroke's own start point, which sits mid-side whenever the
user began drawing mid-side), and the same line fit, intersection, rebuild and agreement test run on
that ring. RDP assumes nothing about where the sides are, so the four corners of a squat rectangle and
the three of a wide triangle come out at any sample density — a fast square drawn with three samples a
side is recognised now, where before it was left as ink.

What the fallback cannot do is invent a figure: a circle has no corners to cut, and a polygon that
hugs a scribble fails the agreement test. It only offers a corner set the first detector missed.

`Inertia.det` — a normalised second-moment ratio that falls to 0 for a perfect line — is deliberately
kept instead of a regression R²: it is the number the desktop thresholds, so the same stroke is judged
the same way in both apps. The curve classes add their own measures (radial residual for the circle
and ellipse, least squares for the arc) because a moment ratio alone cannot separate an oval from a
circle.

The **arrow** is the one class measured on the raw samples: its point is a corner where three legs
meet, the filter's clamp rounds it slightly, and a rounded point is no longer the sample furthest from
the tail — the whole reading would come off the wrong vertex.

## Deliberate rejections

- A stroke under 40 pt is a tick or a dot, never a shape.
- A closed loop is never an arc and an open sweep is never an ellipse — closure is a class boundary,
  not a hint.
- An **oval is not a circle**: `CircleRecognizer` refuses non-round strokes on purpose rather than
  fitting the closest circle.
- A letter-sized `C` (chord under 60 pt) stays ink. A *deliberately* large `C` will snap to an arc:
  geometrically it is one, and the class is worth more than that edge case.
- A check mark is not an arrow — it has one barb, not two.
- Handwriting, squiggles and blobs match nothing and are kept exactly as drawn.

## Provider / format boundary

A recognised shape is an ordinary constant-width `Stroke` — no new element, nothing to preserve, and
it round-trips through `.xopp` and `.mobix` like any ink. One code divergence from upstream exists:
`RecoSegment.calcEdgeIsect` returns `null` for parallel pieces where the C++ divides by zero and would
write an infinite point into the `.xopp`.

## Status

The recogniser itself lives in **`mobix-core`** (`render/ShapeRecognizer.kt` and its companions), not in the
app module: it was always pure, and both shells run the same dwell. The rule they share is
`ShapeDwell` (the 300 ms, the 3 pt slop, and the figure a stroke is offered to); what differs is the
clock. The tablet posts a `postDelayed` from `DrawingSurfaceHoldSnap`; the window has no such thing, so
`PageInkGesture.dwellCheck` times the rest itself — from the last **move**, with the slop deciding
(`System.nanoTime`), because a Wintab driver keeps reporting the same position while the pen rests on
the glass and a clock stamped on every sample never aged at all. It is called from the canvas's silent
timer, from the packet path's flush, and once before a lift.

- **Live**: dwell (300 ms) and lift-off recognition, all seven classes, the tremor filter, the switch,
  the pen/highlighter filter, the polygon agreement test and the corner fallback, and the step that
  keeps the result: a recognised **triangle or rectangle** drawn with the pen is committed as a
  living `ParametricShapeElement` whose corners are dragged on the page (see *Living figures* in
  [drawing-tools.md](drawing-tools.md)). In the window the dwell is live too: the figure replaces the
  preview on the sheet while the pen rests, a stroke that grows gets its own ink back, and the lift
  writes it — the polygon as a living shape, a line, arrow or arc as the cleaned stroke.
- **Not built**: the kinds whose angles are authored rather than recognised — a scaled-angle triangle
  or trapezoid re-fitting its vertices on every drag. Those arrive with the shape-library kinds that
  carry an angle set; a circle or an ellipse has no corners to drag and stays ink, as does every other
  recognised class.
