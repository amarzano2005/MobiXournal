# Scientific typesetting: on-device LaTeX and Typst engine

> Read when implementing formula rendering, on-device typography engines (KaTeX, Typst), interactive math editing, or vector math font rasterization.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: eliminating external LaTeX toolchains

Legacy desktop Xournal++ requires a multi-gigabyte external TeX Live distribution installed on the host OS
to compile formulas, which is impossible on mobile Android devices and cumbersome on desktop. When rendered,
Xournal++ converts formulas into static pixmaps or opaque PDF image objects that cannot be edited easily.

MobiXournal integrates a **self-contained, on-device scientific typesetting engine** operating in milliseconds
without external network or compiler dependencies.

---

## Dual engine: KaTeX and Typst

```text
User Input: LaTeX ("\int_0^\infty e^{-x^2} dx") or Typst ("integral_0^oo e^(-x^2) dif x")
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│             Embedded Typesetting Core                  │
│   ├── KaTeX Engine (Offline JS/WASM or native port)    │
│   └── Typst Compiler (Embedded Rust/WASM via KMP)      │
└────────────────────────┬───────────────────────────────┘
                         │ Vector Geometry (Glyphs & Bézier Outlines)
                         ▼
┌────────────────────────────────────────────────────────┐
│                  MathBlockElement                      │
│   - Living Source Code: preserved in .mobix container  │
│   - Skia Vector Path: hardware-rendered at any zoom    │
│   - Double-tap: reopens editing modal with live preview│
└────────────────────────────────────────────────────────┘
```

### 1. Embedded KaTeX engine
- **Instant compilation**: Compiles standard mathematical LaTeX in $< 15\text{ ms}$.
- **Zero setup**: Bundles high-quality OpenType Computer Modern / Latin Modern Math fonts.
- **Full offline support**: Operates completely disconnected from the internet.

### 2. Modern Typst compiler
- **Next-generation syntax**: Concise, intuitive markup (e.g. `$sum_(i=1)^n i = (n(n+1))/2$`).
- **Blazing speed**: Incremental compilation in $< 5\text{ ms}$, enabling live as-you-type preview.
- **Embedded via WASM/KMP**: Runs inside an isolated, lightweight WebAssembly runtime on Android and Desktop.

---

## The living math block (`MathBlockElement`)

In `.mobix` (see [mobix-schema-spec.md](mobix-schema-spec.md)), formulas are first-class interactive entities:

```json
{
  "type": "mathBlock",
  "id": "math_01",
  "engine": "TYPST",
  "source": "integral_(-oo)^(+oo) e^(-x^2) dif x = sqrt(pi)",
  "bounds": { "left": 140.0, "top": 220.0, "scale": 1.2 },
  "color": "#FF212121",
  "fontSizePt": 16.0
}
```

### Interactive capabilities
- **Double-tap to edit**: Double-tapping the rendered formula with the stylus reopens the formula editor
  with the cursor positioned in the source text.
- **Resolution-independent vector scaling**: Formulas render as crisp Skia vector paths rather than blurry
  pixel bitmaps, maintaining razor-sharp rendering on 4K/retina displays and at $800\%$ zoom.

---

## Exporting to `.xopp` and PDF

- **To `.xopp`**: The export bridge converts the vector paths into standard Xournal++ `TexImage` elements
  while embedding the raw LaTeX/Typst source code in a metadata comment tag for lossless round-tripping.
- **To PDF**: Emits native vector glyphs with embedded font outlines, ensuring text selectable formulas in
  standard PDF readers.
