# Math engine: evaluation, plotting and numerical analysis

> Read when implementing mathematical expression parsing, numerical calculus (roots, extrema, derivatives, integrals), parametric curves, polar plots, or interactive formula sliders.

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
       ├──► Vectorized Evaluator (double[] x -> double[] y)
       │          └──► MultiFunctionPlotter (Path generation)
       │
       └──► Numerical Calculus Engine
                  ├── Root Finder (Brent's method / Newton-Raphson)
                  ├── Extrema & Inflections (f'(x) = 0, f''(x) = 0)
                  └── Definite Integral (Adaptive Gauss-Kronrod / Simpson)
```

---

## Supported mathematical primitives

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

### 1. Adaptive derivative computation
Derivatives are computed using a 5-point central difference stencil with automatic step size $h$:
$$f'(x) \approx \frac{-f(x + 2h) + 8f(x + h) - 8f(x - h) + f(x - 2h)}{12h}, \quad h = \sqrt[3]{\epsilon_{\text{mach}}} \cdot \max(|x|, 1.0)$$

Second derivatives $f''(x)$ are evaluated using:
$$f''(x) \approx \frac{-f(x + 2h) + 16f(x + h) - 30f(x) + 16f(x - h) - f(x - 2h)}{12h^2}$$

### 2. Critical point detection (Extrema & roots)
- **Roots ($f(x) = 0$)**: Sign-change brackets $[x_k, x_{k+1}]$ are identified across sampled points and
  refined via **Brent's method** to tolerance $10^{-7}$.
- **Local Extrema ($f'(x) = 0$)**: Local minima and maxima are located by detecting sign changes in $f'(x)$
  and categorized via the sign of $f''(x)$ (positive $\implies$ minimum, negative $\implies$ maximum).
- **Inflection Points ($f''(x) = 0$)**: Sign changes of $f''(x)$ indicate concavity transitions.

### 3. Definite integration & area shading
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
engine's *results* (roots, extrema, inflections) are written as `summaryText` — a rendering cache,
not the model: recomputed whenever a curve, a range or a parameter changes, and ignored by a reader
that would rather compute. The field-by-field schema is the `PlotElement` in
[mobix-schema-spec.md](mobix-schema-spec.md); what the engine promises about it is this file.
