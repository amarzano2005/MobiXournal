# Shape perfection engine — when a stroke snaps, and to what

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

Each stage rejects rather than guesses, strictest first; the first match wins, and a stroke that
matches nothing commits as drawn.

| Order | Class | File | Accepts when |
|---|---|---|---|
| 1 | **triangle** | `ShapeRecognizer` | the last three fitted segments close into a ring; vertex gaps within 0.3 × the pieces' radii |
| 2 | **rectangle / square** | `ShapeRecognizer` | four segments, every corner within 15° of square, gaps within 0.20; a tilt under 5° is dropped to the page axes, which is what squares an upright hand rectangle |
| 3 | **line** | `ShapeRecognizer` | one straight segment, `Inertia.det` < 0.015 (a piece of a polygon is allowed 0.045); a near-horizontal or vertical fit is snapped flat, a diagonal keeps its extent unless the tip strayed more than √15 pt from the fitted line |
| 4 | **arrow** | `ArrowRecognizer` | a straight shaft ≥ 60 pt with ≤ 10% bow, two barbs 10–55% of its length (within 2.5× of each other) not past the point, and the stroke returning to the point between them |
| 5 | **circle** | `CircleRecognizer` | roundness `det` > 0.95 and a length-weighted radial residual < 0.10 × the radius |
| 6 | **ellipse** | `EllipseRecognizer` | **closed** (ends within 30% of the bounding diagonal), axes 1.25–6× apart, mean rim residual ≤ 0.10 and no single sample above 0.45 |
| 7 | **arc** | `ArcRecognizer` | **open**, chord ≥ 60 pt, sweep 100–300°, mean distance from the fitted circle ≤ 6% of its radius, radial spread ≤ 35% |

The polygon fit is desktop Xournal++'s own (`findPolygonal` recursion on `Inertia.det`, then
`optimizePolygonal` nudging each break to its local optimum), and the emitted geometry comes from the
fitted lines' **intersections**, so it is cleaner than anything read off the samples. Every tolerance
is relative to the stroke's own size (`RecoSegment.radius`), never an absolute pt budget, so the same
wobble passes on a small circle and a page-sized one.

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

- **Live**: dwell (300 ms) and lift-off recognition, all seven classes, the tremor filter, the switch,
  the pen/highlighter filter.
- **Not built**: a recognised shape is still *ink*. Turning it into a living
  `ParametricShapeElement` with draggable vertex handles is the engine's next child
  (`living-parametric-shape-with-interactive-vertex-handles`).
