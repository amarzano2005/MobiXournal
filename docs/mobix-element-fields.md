# The element fields, family by family

> Read when you need the exact field, type, unit or default of a `stroke`, `text`, `image`, `texImage`, `table`, `plot`, `parametricShape`, `circuit` or `mathBlock`, or when you need to know which half of an element's `bounds` a reader may trust.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns the **tables**. The rules that make an element survive a round trip — the `type`
discriminator, identity, the retention channel, the vocabulary refusal — are
[mobix-elements.md](mobix-elements.md)'s and are not restated here. Units follow the document's: points,
`#RRGGBBAA` colours, UTC epoch milliseconds; an angle is **degrees** except a stylus `tilt` and
`azimuth`, which are **radians** because the app stores the platform's own numbers.

## What every family shares

| Field | Type | Required | Meaning |
|---|---|---|---|
| `type` | string | **yes** | the discriminator; no `type`, no element |
| `id` | string | no | identity; a reader mints one when absent, and never re-mints |
| `attrs` | object of strings | no | the `.xopp` attributes the bridge must not lose |
| `bounds` | object | per family | `left`, `top`, `right`, `bottom`; **fact** or **derived** as the table says |

A **known key** this build did not read is carried in the element's `unknown` channel and written back
after the known fields; a **value** inside a closed set that this build has no name for is refused, and
the refusal keeps the element whole as a `RawElement` — the page still opens. `bounds` is a cache for a
reader that only wants to cull or hit-test, except where the row below calls it a fact: there it is the
placement, and nothing else in the element states it.

## `stroke`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `tool` | `PEN` \| `HIGHLIGHTER` \| `ERASER` | — | **none**: absent is refused, never drawn as a pen |
| `color` | `#RRGGBBAA` | — | opaque black |
| `lineStyle` | `PLAIN` \| `DASHED` \| `DASH_DOT` \| `DOTTED` | — | `PLAIN` |
| `lineWidth` | number | pt | **presence is the fact**: present means a constant-width stroke, absent means one width per point |
| `capStyle` | string | — | the renderer's own; the `.xopp` spelling (`round`) is kept verbatim |
| `fill` | integer `0`–`255` | alpha | absent: an open stroke with no interior |
| `audioRef` | object `{ assetId, filename, startMs }` | — | absent: no recording; read into `attrs`' `fn`/`ts` ([mobix-schema-spec.md](mobix-schema-spec.md)) |
| `points[]` | array | — | absent reads as no points, which draws nothing |
| `points[].x`, `.y` | number | pt | **required** on a point |
| `points[].width` | number | pt | `1.0`; the pressure-resolved width actually drawn |
| `points[].pressure` | number `0`–`1` | — | absent when the tablet reports none |
| `points[].tilt` | number | radians | absent when the axis does not exist; `0` is a pen held upright |
| `points[].azimuth` | number | radians | the pen's in-plane angle |
| `points[].dtMs` | integer | ms | `0` at the first point, then elapsed since it |

`bounds` is **derived** — the polyline widened by half the ink at each end — so a stale box is
recomputed rather than believed. The example is
[mobix-elements.md](mobix-elements.md) → *Stroke*.

## `text`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `font` | string | — | `""`; a Pango description kept verbatim, never re-derived |
| `size` | number | pt | `0.0` |
| `x`, `y` | number | pt | `0.0`; the text's **anchor**, not a box's corner |
| `color` | `#RRGGBBAA` | — | opaque black |
| `content` | string | — | `""` |

A text element **has no `bounds`**: its extent depends on the font, and only the renderer knows it. Its
example is [mobix-elements.md](mobix-elements.md) → *Text*.

## `image` and `texImage`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `bounds` | object, all four edges | pt | **required**: an image with no box cannot be placed, and the read fails as a corrupt document |
| `asset` | string, an entry under `assets/` | — | required for `image`; for `texImage` a document may carry the LaTeX alone |
| `latex` | string | — | `""` (`texImage` only) |
| `color` | `#RRGGBBAA` | — | opaque black (`texImage` only) |
| `latexInAttribute` | boolean | — | `true`: the `.xopp` shape whose source sits in the attribute rather than the body |

`bounds` is a **fact** here: the box *is* the placement, so it is read and trusted, and an `asset` name
that resolves to nothing is a corrupt document rather than an empty picture. Examples:
[mobix-elements.md](mobix-elements.md) → *Image and rendered LaTeX*.

## `table`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `bounds` | object | pt | **required**; `left`/`top` are the grid's origin (**fact**), `right`/`bottom` are **derived** from the grid |
| `colWidths` | array of numbers | pt | **required**: the authority for the columns |
| `rowHeights` | array of numbers | pt | **required**: the authority for the rows |
| `cells` | array of rows, each an array of cells | — | **required**; a short row reads as empty cells, not a corrupt table |
| `headerRow` | boolean | — | `false` |
| `borderColor` | `#RRGGBBAA` | — | `#212121ff` |
| `borderWidth` | number | pt | `1.0` |
| `cells[][] .text` | string | — | `""` |
| `cells[][] .align` | `LEFT` \| `CENTER` \| `RIGHT` | — | `CENTER`; an unknown name refuses the table |
| `cells[][] .bold` | boolean | — | `false` |
| `cells[][] .fillColor` | `#RRGGBBAA` or `null` | — | `null`; written explicitly, never omitted |

Example: [mobix-living-entities.md](mobix-living-entities.md) → *Table*.

## `plot`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `bounds` | object | pt | **required**, and a **fact**: the frame |
| `xMin`, `xMax` | number | the plot's own | `-10.0`, `10.0` |
| `samples` | integer | — | `300` |
| `showCriticalPoints` | boolean | — | `false` |
| `includeDerivative` | boolean | — | `false` |
| `shadeIntegral` | object `{ from, to }` | the plot's own | absent: no wash; each edge `0.0` |
| `curves[]` | array | — | absent reads as no curves |
| `curves[].kind` | `CARTESIAN` \| `PARAMETRIC` \| `POLAR` | — | **required**; an unknown kind refuses the plot |
| `curves[].source` | string | — | **required**: the expression, in the parser's own variable |
| `curves[].sourceY` | string | — | absent outside `PARAMETRIC` |
| `curves[].color` | `#RRGGBBAA` | — | opaque black; each curve carries its own |
| `curves[].strokeWidth` | number | pt | `1.0` |
| `curves[].lineStyle` | the stroke's four names | — | `PLAIN` |
| `curves[].tMin`, `.tMax` | number | the plot's own | absent outside `PARAMETRIC` |
| `curves[].thetaMin`, `.thetaMax` | number | radians | absent outside `POLAR` |
| `parameters` | object, name → number | — | absent reads as no sliders; a non-number refuses the plot |
| `axisColor` | `#RRGGBBAA` | — | `#37474fff` |
| `summaryText` | string | — | absent; a **cache** of what was computed, recomputed on any change |
| `infoSide` | `BELOW` \| `ABOVE` \| `RIGHT` \| `LEFT` | — | `BELOW`; an unknown side refuses the plot |

Example: [mobix-living-entities.md](mobix-living-entities.md) → *Function plot*.

## `parametricShape`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `shape` | string | — | **required**, but an **open key set**: a kind from a newer library is still the user's figure |
| `bounds` | object | pt | **derived** from the vertices, which are the authored geometry |
| `vertices[]` | array of `{ x, y }`, numbers in pt | pt | **required and non-empty**: no vertices, no figure |
| `strokeColor` | `#RRGGBBAA` | — | opaque black |
| `strokeWidth` | number | pt | `1.0` |
| `fillColor` | `#RRGGBBAA` | — | absent: an unfilled outline |
| `showAngles` | boolean | — | `false` |
| `showSideLengths` | boolean | — | `false` |

Example: [mobix-living-entities.md](mobix-living-entities.md) → *Parametric shape*.

## `circuit`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `symbol` | string | — | **required**, and an **open key set** like a shape's `shape` |
| `value` | string | — | absent: a symbol with no designator |
| `labelSide` | `above` \| `below` \| `left` \| `right`, read case-insensitively | — | `above` |
| `labelBelow` | boolean | — | the older spelling of the same choice, read and no longer written |
| `position` | object `{ x, y }`, numbers in pt | pt | **required**: the anchor |
| `rotation` | number | degrees | `0.0` |
| `strokeColor` | `#RRGGBBAA` | — | opaque black; the user's ink, not the library's |
| `strokeWidth` | number | pt | `1.0` |
| `pins[]` | array | — | absent: a symbol with no magnetic anchors |
| `pins[].id` | string | — | **required** on a pin |
| `pins[].relX`, `.relY` | number | pt | `0.0`; relative to `position` and **before** `rotation` |
| `pins[].connectedTo` | string | — | absent: a floating pin |

A component has **no `bounds`**: its outline belongs to the symbol library. Example:
[mobix-living-entities.md](mobix-living-entities.md) → *Circuit component*.

## `mathBlock`

| Field | Type | Unit | Default when absent |
|---|---|---|---|
| `source` | string | — | **required**: the formula, and the only authority for what is drawn |
| `flavor` | `LATEX` \| `TYPST` | — | `LATEX`; an unknown flavor refuses the block |
| `color` | `#RRGGBBAA` | — | opaque black |
| `bounds` | object | pt | **required**: where the typeset formula sits |

No rendered bytes are stored — the drawing is a function of `source`, so a reader that can typeset draws
it and one that cannot still shows its box and keeps it. A semantically valid example:

```json
{ "type": "mathBlock", "id": "mth_01",
  "source": "\\frac{\\pi}{2}", "color": "#1f6febff",
  "bounds": { "left": 120.0, "top": 240.0, "right": 200.0, "bottom": 268.0 } }
```

`flavor` is omitted for LaTeX because that is what a desktop `<teximage>` carries; a Typst source is
written explicitly so a reader never guesses the language. A `texImage` is the other spelling of the same
idea — a *picture* of a formula rather than a formula — and a document may carry both.
