# The `.mobix` format specification and dynamic entities

> Read when implementing serialization, parser models, dynamic parametric elements (tables, plots, shapes, circuits), or backward compatibility for `.mobix`.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Document container structure

The `.mobix` format is a ZIP archive conforming to standard PKWARE specifications, typed as
`application/vnd.mobixournal+zip`. The archive layout isolates global metadata, per-page or chunk data,
and binary assets:

```text
document.mobix
├── mimetype                     # Uncompressed ASCII: "application/vnd.mobixournal+zip"
├── manifest.json                # Document envelope & configuration
├── canvas.json                  # Spatial geometry mode & dimensions (see infinite-canvas.md)
├── pages/                       # Paginated mode pages
│   ├── page-0001.json
│   └── page-0002.json
├── chunks/                      # Infinite mode spatial tiles (see infinite-canvas.md)
│   ├── chunk_0_0.json
│   └── chunk_0_1.json
├── assets/                      # Binary deduplicated assets
│   ├── img_<sha256>.webp
│   └── aud_<sha256>.opus
└── xopp-bridge/                 # Interoperability sidecar (see mobix-format.md)
    └── sync.json
```

---

## Manifest schema (`manifest.json`)

```json
{
  "formatVersion": 1,
  "minAppVersion": "2.0.0",
  "documentId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "title": "Analisi Matematica II - Appunti",
  "created": 1775952000000,
  "modified": 1775955600000,
  "canvasMode": "INFINITE_VERTICAL",
  "pageCount": 1,
  "paperTemplate": {
    "kind": "RULED",
    "spacingPt": 24.0,
    "lineColor": "#1A000000",
    "marginPt": 72.0,
    "marginColor": "#33FF0000"
  }
}
```

---

## Dynamic entity model (living elements)

Every entity inherits from `Element` and includes a unique `id` and bounding box `bounds`:

### 1. Dynamic Table (`TableElement`)
A living, re-editable grid structure where cells contain rich multi-line text:

```json
{
  "type": "table",
  "id": "tbl_01",
  "left": 72.0,
  "top": 120.0,
  "colWidths": [120.0, 180.0, 100.0],
  "rowHeights": [28.0, 24.0, 24.0],
  "headerRow": true,
  "borderColor": "#FF212121",
  "borderWidth": 1.0,
  "cells": [
    [
      { "text": "Parametro", "align": "CENTER", "bold": true, "fillColor": "#15000000" },
      { "text": "Formula", "align": "CENTER", "bold": true, "fillColor": "#15000000" },
      { "text": "Valore", "align": "CENTER", "bold": true, "fillColor": "#15000000" }
    ],
    [
      { "text": "Frequenza", "align": "LEFT", "bold": false, "fillColor": null },
      { "text": "f = 1 / T", "align": "CENTER", "bold": false, "fillColor": null },
      { "text": "50 Hz", "align": "RIGHT", "bold": false, "fillColor": null }
    ]
  ]
}
```
**Interactive capabilities**: Tapping a dynamic table displays cell resize handles, column/row insertion
buttons, and cell inline text editing.

### 2. Parametric Function Plot (`PlotElement`)
Stores mathematical expressions, range bounds, and numerical analysis parameters:

```json
{
  "type": "plot",
  "id": "plt_01",
  "bounds": { "left": 100.0, "top": 200.0, "width": 300.0, "height": 180.0 },
  "xMin": -6.28,
  "xMax": 6.28,
  "samples": 300,
  "showCriticalPoints": true,
  "includeDerivative": false,
  "curves": [
    {
      "source": "sin(x) * exp(-0.1 * x)",
      "color": "#FFD32F2F",
      "strokeWidth": 1.5,
      "lineStyle": "SOLID"
    }
  ],
  "axisColor": "#FF37474F",
  "summaryText": "Roots: x = {0.0, 3.14, -3.14}..."
}
```
**Interactive capabilities**: Re-openable via the "Edit Plot" modal; dynamically adjusts upon formula
re-evaluation without re-drawing manually.

### 3. Parametric Geometry Shape (`ParametricShapeElement`)
Geometric constructs retaining mathematical control handles:

```json
{
  "type": "parametricShape",
  "id": "shp_01",
  "shape": "TRIANGLE_SCALENE",
  "vertices": [
    { "x": 100.0, "y": 100.0 },
    { "x": 250.0, "y": 120.0 },
    { "x": 160.0, "y": 240.0 }
  ],
  "strokeColor": "#FF0D47A1",
  "strokeWidth": 2.0,
  "fillColor": "#1A2196F3",
  "showAngles": true,
  "showSideLengths": true
}
```

### 4. Circuit Component (`CircuitElement`)
Active and passive electronics primitives with magnetic connection pins:

```json
{
  "type": "circuit",
  "id": "ckt_res_01",
  "symbol": "RESISTOR_IEEE",
  "value": "10 kΩ",
  "position": { "x": 300.0, "y": 150.0 },
  "rotation": 90.0,
  "pins": [
    { "id": "p1", "relX": -24.0, "relY": 0.0, "connectedTo": "net_node_A" },
    { "id": "p2", "relX": 24.0, "relY": 0.0, "connectedTo": "net_node_B" }
  ]
}
```

### 5. High-Fidelity Inking Stroke (`Stroke`)
```json
{
  "type": "stroke",
  "id": "stk_01",
  "tool": "PEN",
  "color": "#FF1A237E",
  "lineStyle": "SOLID",
  "points": [
    { "x": 102.4, "y": 205.1, "width": 1.2, "pressure": 0.55, "tilt": 14.2, "azimuth": 32.0, "dtMs": 0 },
    { "x": 103.8, "y": 206.8, "width": 1.4, "pressure": 0.62, "tilt": 14.8, "azimuth": 33.1, "dtMs": 6 }
  ]
}
```

---

## Backward and forward compatibility rules

1. **Unknown Entity Preservation**: An older reader that encounters an unknown element type (e.g., a future
   `3dPlotElement`) stores it as a `RawJsonElement`. When the document is saved, the unmodelled element
   is preserved bit-for-bit without data loss.
2. **Schema Versioning**: `formatVersion` is incremented only for breaking structural changes. Minor features
   rely on additive fields with standard defaults.
