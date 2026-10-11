# Auto-shape perfection and parametric geometry handles

> Read when implementing pen dwell gestures, geometric shape recognition (lines, arrows, circles, polygons), morphing animations, or interactive vertex control handles.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: effortless geometric drafting

Drawing diagrams, geometric proofs, flowcharts, and circuit schematics by hand often results in uneven,
wobbly lines. While traditional toolbar shape tools require interrupting thought to switch tools, the
**Hold-to-Straighten (Auto-Shape Perfection)** engine operates unobtrusively during normal freehand pen drawing.

---

## Dwell detection gesture pipeline

```text
Stylus Down ──► Freehand Drawing ──► Stylus Dwells (Still for 300 ms)
                                             │
                                             ▼ Geometric Feature Extraction
                                      Candidate Classifier
                                             │
                      ┌──────────────────────┼──────────────────────┐
                      ▼                      ▼                      ▼
                 Line / Arrow         Circle / Ellipse      Polygon / Polyline
                      │                      │                      │
                      └──────────────────────┼──────────────────────┘
                                             │
                                             ▼ Spring Morph Animation (60-120 FPS)
                                   Pristine Vector Geometry
                                             │
                                   On-Screen Vertex Handles
```

### 1. Dwell threshold & jitter filtering
- **Dwell Timer**: If the stylus tip travels $< 3.0\text{ pt}$ for $\ge 300\text{ ms}$ before touch-up,
  the gesture triggers.
- **Micro-jitter cancelation**: Stylus hover tremors and hand pulses are filtered out using an exponential
  moving average window.

### 2. Multi-class shape classifier
The classifier analyzes curvature profile, aspect ratio, end-point closure, and vertex angular changes:

| Target Shape | Classification Invariants |
|---|---|
| **Straight Line** | Low total curvature, linear regression correlation $R^2 > 0.96$. |
| **Arrow** | Straight trunk ending in a sharp V-shaped acute reversal at the head. |
| **Circle** | Endpoint distance to start $< 10\%$ perimeter; radial variance from center $\sigma_r / \bar{r} < 0.08$. |
| **Ellipse** | Bounded closed loop fitting an algebraic conic section with eccentricity $e \in [0, 1)$. |
| **Rectangle / Square**| 4 detected corners with internal angles within $\pm 10^\circ$ of $90^\circ$. |
| **Triangle** | 3 prominent corner vertices; interior angle sum $\approx 180^\circ$. |
| **Arc / Spline** | Smooth, monotonic curvature without corner discontinuities. |

---

## Morphing feedback animation

To provide immediate visual delight and clarity:
1. When recognized, the wobbly freehand path does not snap abruptly.
2. A lightweight spring animation ($120\text{ ms}$, damped harmonic motion) interpolates the points of the
   wobbly path toward the idealized mathematical vector geometry.
3. A subtle haptic pulse (on supported Android pens and trackpads) confirms the transformation.

---

## Living control handles (`ParametricShapeElement`)

Instead of baking the perfected shape into an unmodifiable raw `Stroke`, the engine generates a
`ParametricShapeElement` (defined in [mobix-schema-spec.md](mobix-schema-spec.md)):

```text
Triangle Handle UI:
         V1 (Apex)
         ●  [α = 60°]
        / \
       /   \
      /     \
     ●───────●
    V2       V3
[β = 60°]  [γ = 60°]
```

- **Interactive Vertex Dragging**: Dragging vertex $V_1$ dynamically deforms the triangle while updating
  side lengths and angle readout badges in real time.
- **Axis Snapping**: Vertices snap to $0^\circ, 30^\circ, 45^\circ, 90^\circ$ angles relative to page rulings.
- **Conversion on Save**: Stored natively in `.mobix`; synthesized to regular lines when exported to `.xopp`
  via the bridge in [native-format-and-desktop.md](native-format-and-desktop.md).
