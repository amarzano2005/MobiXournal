# Math engine: evaluation, plotting and numerical analysis

> Read when implementing mathematical expression parsing, numerical calculus (roots, extrema, derivatives, integrals), parametric curves, polar plots, or interactive formula sliders.
> For the task: [What works, and what is not there](guide-limitations.md), [Placing a plot, table or circuit](guide-stem-tools.md), [The words this guide uses](guide-glossary.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Core math architecture

MobiXournal includes a dedicated mathematical engine that powers both static rendering and interactive
manipulation of mathematical functions. The engine operates entirely in pure Kotlin with no native
C/Fortran dependencies, ensuring seamless portability across Android and Desktop (KMP).

```text
Formula String ("sin(x)*exp(-x)")
       │
       ▼ Lexer & Parser (Dijkstra Shunting-Yard / AST)
Abstract Syntax Tree (Expression Node)
       │
       ├──► Sampler (x, y pairs over the plot's range)
       │          └──► MultiFunctionPlotter (Path generation)
       │
       └──► Numerical Calculus Engine (`FunctionAnalysis`)
                  ├── Derivative (two-point central difference, fixed h = 1e-5)
                  ├── Critical points (uniform samples, then bisection)
                  └── Definite Integral (adaptive Simpson)
```

---

## The mathematical primitives

The parser evaluates real-valued functions $f: \mathbb{R} \to \mathbb{R}$:

| Category | Operators / Functions |
|---|---|
| **Arithmetic** | `+`, `-`, `*`, `/`, `^`, `%` |
| **Constants** | `pi` ($\pi \approx 3.14159$), `e` ($e \approx 2.71828$), `phi` ($\phi \approx 1.61803$) |
| **Trigonometry** | `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2` |
| **Hyperbolic** | `sinh`, `cosh`, `tanh`, `asinh`, `acosh`, `atanh` |
| **Exponential & Log** | `exp`, `ln` ($\log_e$), `log10`, `log2`, `sqrt`, `cbrt` |
| **Special** | `abs`, `sign`, `floor`, `ceil`, `round`, `sinc` |

---

## Numerical calculus algorithms

### 1. Derivative: two points, one fixed step
`FunctionAnalysis.derivative` is a plain central difference at a step that does **not** adapt:
$$f'(x) \approx \frac{f(x + h) - f(x - h)}{2h}, \quad h = 10^{-5}$$

Its truncation error is $O(h^2)$ — about $1.7 \cdot 10^{-11}$ relative for a smooth curve — and the
function is asked only twice per point, which is why it is cheap enough to run on every sample during a
slider drag. Neither side being finite makes the result `NaN` rather than a number: at the top of the
double range the difference of two values around $10^{304}$ leaves a measured relative error of
$2.5 \cdot 10^{-9}$, one order worse than the stencil's own, because the evaluation dominates there.

**There is no second derivative.** The engine never evaluates $f''$ and computes no inflection points:
`CriticalPointType` holds roots, the y-intercept, minima, maxima, intersections and vertical asymptotes,
and nothing else. A minimum or maximum is classified by the sign of $f'$ at the **left end of the bracket
that was bisected**, not by a second derivative.

### 2. Critical points: sample, bracket, bisect
- **Sampling.** `steps` uniform samples (200 by default) across `xMin`..`xMax`. The left end of the range
  is only ever the first bracket's left side, so it is never a candidate on its own; the right end is a
  sample like any other.
- **Roots.** A sign change between adjacent samples is refined by `bisectRoot`: plain bisection, at most 24
  iterations, stopping when $|f| < 10^{-10}$ **or** the bracket is narrower than $10^{-8}$. There is no
  Brent step, no secant iteration and no derivative-based iteration. The bracket test usually wins, so
  $|f|$ at a reported root is a bracket width times the slope — measured $5.7 \cdot 10^{-10}$ for
  $\sin x - 0.3$, not the $10^{-10}$ the value test promises.
- **A sample whose value is already below $10^{-7}$** is reported as a root. That is the only route a
  **tangent** root (one that touches zero without crossing) has: if no sample lands where $|f| < 10^{-7}$,
  it is missed and nothing reports the miss. The root test also needs the *previous* sample finite, so a
  root at the edge of a domain — `sqrt` at 0 — is not reported even when a sample sits on it.
- **Extrema.** The sign change of $f'$ is bisected by the same routine; a sample where $|f'| < 10^{-4}$ is
  taken as stationary. The kind comes from the sign at the bracket's left end.
- **Merging.** Roots and extrema closer than **0.05** in $x$ (and in $y$, when both are finite) collapse
  into one point, so two distinct roots inside one 0.05 window are reported as a single root. Asymptotes
  are appended without this filter.
- **Vertical asymptotes** are **finite↔non-finite transitions between adjacent samples**, reported at the
  midpoint of the pair and never refined: a pole is located to half a sample step, and yields **one point
  per side**. A jump between two values that are both finite is not reported at all, and the edge of a
  domain is reported as an asymptote although it is not one.
- **Intersections** apply the same bracket-and-bisect to $f_1 - f_2$.

### 3. What that costs on curves it cannot resolve
A curve oscillating faster than the sampling is **under-reported, not invented**: `sin(400x)` over
$[-1, 1]$ has 127 roots and a step of $0.01$ sees 37 of them (three samples per period). Each reported root
is genuine — a bracket holding an odd number of roots still bisects to one of them — but the summary is a
partial list, and it does not say it is partial.

| Case | What the engine does | Measured in |
|---|---|---|
| `sin(x)` over $[-\pi, \pi]$ | finds 2 roots (0 and $\pi$) and both extrema | `sin over minus pi to pi …` |
| `(x - 0.5005)^2` | **no** root: no sign change, no sample inside $10^{-7}$ | `a tangent root no sample lands on is missed` |
| `x*(x - 0.03)` | 1 root: the second is inside the 0.05 merge radius | `two roots closer than the merge radius` |
| `1/x` over $[-1, 1]$ | 2 asymptotes at $\pm 0.005$; the integral is refused | `1 over x reports one asymptote …` |
| `abs(x)` at 0 | slope reads **0** (the two sides cancel); the kink is a minimum | `abs at the origin has a zero derivative` |
| `sqrt(x)` at 0 | no derivative, **no root**, and its domain edge reported as an asymptote | `sqrt at its domain edge …` |
| `sin(400x)` | 37 of 127 roots | `a curve oscillating faster than the sampling …` |
| `exp(x)` at 710 | derivative `NaN`, integral refused, overflow marked an asymptote | `an overflow is refused …` |

Every row is a test in
[FunctionAnalysisCharacterizationTest.kt](../mobix-core/src/test/java/com/mobixournal/render/FunctionAnalysisCharacterizationTest.kt),
which records today's numbers so a replacement scheme (`stem-numeric-reliability`) has to change them
deliberately instead of silently.

### 4. Definite integration & area shading
Definite integrals $\int_a^b f(x)\,dx$ are calculated using **Adaptive Simpson's rule**
(`FunctionAnalysis.definiteIntegral`, tolerance $10^{-7}$, recursing to 24 levels), and each curve's value is
reported in the analysis summary the plot already carries. An integrand that is not finite across the bounds
is **refused**, not returned as NaN: `sqrt(x)` over $[-1,1]$ and `1/x` over $[-1,1]$ report
"not finite over the range".

A plot whose `shadeIntegral` is set is drawn with the area filled, compiled **once** by `PlotInk` into one
closed polygon in the frame the plotter fitted, so the canvas, SVG, PDF and the `.xopp` synthesis cannot
disagree about the region a shaded plot stands for. The area is **set in the plot editor**
(`AdvancedFunctionGraphDialog`'s *Shade integral area* switch with its from/to pair, defaulting to the
element's own `xMin`..`xMax`) and written through `PlotElementFactory.applyPlotEdit`, which is what keeps
the wash, the summary's integral and the exported ink moving from one value — and what puts the change
under undo with every other plot edit. A shaded plot's summary reports its integrals **over the shaded
area** and says so in the line above them. The fill is the curve's own colour at the canvas'
translucent-fill alpha (`CanvasChrome.BAND_FILL_ALPHA`), carried on the stroke's `fill` — the desktop's own
way of saying a fill. The bounds are clipped to the element's `xMin`..`xMax`.

Nothing is drawn for an area this build cannot draw: a curve that does not parse, a curve that leaves the
plane inside the bounds (an asymptote is a break, not a region), a single-curve plot whose fitted frame does
not contain the x-axis to shade against, or three or more curves ("the area" is then not one region).

---

## Advanced curve types

In addition to standard explicit Cartesian functions $y = f(x)$, the engine supports:

### 1. Parametric curves $(x(t), y(t))$
Defined over a parameter range $t \in [t_{\min}, t_{\max}]$.
- **Applications**: Lissajous figures ($x = \sin(at), y = \cos(bt)$), cycloids, Bezier trajectories.

### 2. Polar plots $r(\theta)$
Evaluated over an angular sweep $\theta \in [\theta_{\min}, \theta_{\max}]$:
$$x(\theta) = r(\theta) \cos(\theta), \quad y(\theta) = r(\theta) \sin(\theta)$$
- **Applications**: Rose curves ($r = \cos(k\theta)$), Archimedean spirals ($r = a\theta$), cardioids.
- **Placement**: the plot tool places Cartesian curves only; a parametric or polar curve is drawn, exported
  and round-tripped, but has no creation path on the canvas (`MultiFunctionPlotter`).

---

## Interactive parameter sliders (Dynamic reactive math)

Expressions can define named parameters (e.g., $f(x) = a \cdot \sin(b \cdot x + c)$). When a `PlotElement`
carrying named `parameters` is selected on the canvas, the canvas chrome renders interactive slider widgets
anchored directly below the plot frame. Each named parameter receives a dedicated track, label, and draggable thumb.
Dragging a slider dynamically evaluates the expression and numerical calculus (derivatives, critical points, and
summary report) in real-time, refreshing the cached `summaryText`. On pointer release, the adjusted parameter
value commits as a single undoable edit. Dragging a slider is intercepted by the gesture layer so it never draws
accidental ink strokes.

### What a plot keeps on disk

Everything the engine needs to reproduce a plot is persisted with it, so a formula is never
re-entered after a save: the curve `kind` (Cartesian / parametric / polar) with its own range, the
`samples` count, the named `parameters` the sliders move, the display flags
(`showCriticalPoints`, `includeDerivative`) and the integral bounds when the area is shaded. The
engine's *results* (roots, extrema, asymptotes) are written as `summaryText` — a rendering cache,
not the model: recomputed whenever a curve, a range or a parameter changes, and ignored by a reader
that would rather compute. The report is also a **display switch**: turning *Summary* off in the graph
dialog or the live plot inspector drops `summaryText`, so the text under the graph goes with it instead
of lingering as a stale cache. Its layout groups the findings by kind — roots, extrema, intersections,
asymptotes, then the integrals — so a graph with many points stays scannable. The field-by-field schema
is the `PlotElement` in
[mobix-schema-spec.md](mobix-schema-spec.md); what the engine promises about it is this file.
