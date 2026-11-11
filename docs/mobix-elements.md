# The `.mobix` element schema: the rules and the geometry elements

> Read when implementing or changing the rules that make an element survive a round trip, or one of the four geometry elements a `.xopp` also has. The living entities — table, plot, shape, circuit, math — are [mobix-living-entities.md](mobix-living-entities.md).
> For the task: [Placing a plot, table or circuit](guide-stem-tools.md), [The words this guide uses](guide-glossary.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This file owns **what a geometry element is** on disk, and the four rules every element obeys. The
field-by-field tables of all nine families — types, units, defaults, and which `bounds` are stored facts
rather than derived caches — are [mobix-element-fields.md](mobix-element-fields.md)'s. The container, the
manifest and the page envelope are [mobix-schema-spec.md](mobix-schema-spec.md); why the format exists at
all is [mobix-format.md](mobix-format.md); the living families are
[mobix-living-entities.md](mobix-living-entities.md). The code is `format/mobix/MobixElementCodec.kt` —
the element envelope and the four `.xopp`-shaped elements.

## The four rules every element obeys

1. **A `type` discriminator decides everything.** A reader that knows the type models the element; one
   that does not **preserves it verbatim** as a `RawElement`, so a `.mobix` written by a newer app — a
   `3dPlotElement` nobody has written yet — survives an open-and-save byte-for-byte in value.
2. **An `id`, minted once and never re-minted.** Identity is what the merge keys on
   ([mobix-format.md](mobix-format.md) → *Merging two copies is library code*): an edit keeps it, a copy's
   duplicate gets a new one. A reader that finds none mints it (`ElementId.mint`), which is how a `.xopp`
   import — a format with no ids — gets its elements identified once. The spelling is `stk_3f9c1a7b2e40`.
3. **`bounds` is a cache, written but never trusted.** A writer includes it when the element's own
   geometry determines it (a stroke's extents, an image's box); a reader **re-derives** rather than
   believing it, so a stale value in a file cannot become the document's truth. It exists so a reader
   that only wants to cull or hit-test does not have to parse the element.
4. **What we cannot interpret is kept.** `attrs` carries the attributes the `.xopp` bridge must not
   lose (`ts`, `fn`, anything a newer desktop writes), a **field** the codec did not read is carried in
   the element's `unknown` channel (below), and an **element that only exists in `.xopp`** is marked with
   `"syntax": "xml"` so it is written back as the XML element it came from.

### A known type keeps the fields it did not read

An unknown *field* inside a known element type is the layer's one remaining loss, and it has a decided
answer rather than an open question: **keep it**. The element gains an `unknown` channel holding exactly
the JSON fields its codec did not consume, and the encoder writes the known fields first and that channel
after them, in the order it was read. A name the codec *does* own is never taken from the channel — the
known key wins, and the channel carries only skipped keys — so one fact cannot be written twice and a
new field never forces a `formatVersion` bump.

The rule is about **semantics, not bytes**: an element the writer modified comes back with its unknown
fields intact and its known fields as the edit left them; byte identity is promised only for an element
nothing touched. `attrs` stays the `.xopp` attribute channel, because a second copy of the schema inside
it would be ambiguous to both readers.

The channel is implemented for **every family the format models**, at the level the key was written:

| Family | Where its keys are kept |
|---|---|
| `stroke`, `text` | the element, a stroke's `points[]` entries, and a stroke's `bounds` are derived and therefore not carried |
| `image`, `texImage` | the element, and its `bounds` (that box *is* the placement, so it is read) |
| `table` | the element, every `cells[][]` entry |
| `plot` | the element, every `curves[]` entry, `shadeIntegral` |
| `shape`, `circuit`, `math` | the element, and `vertices[]` / `pins[]` where the family has them |
| any background | the background object, and — when its `type` is unknown — the whole object, re-emitted as it arrived |

The codecs that own each object are the ones that carry its keys (`MobixElementCodec`, `MobixTableCodec`,
`MobixPlotCodec`, `MobixShapeCodec`, `MobixCircuitCodec`, `MobixMathCodec`, `MobixPageCodec`), so a new
sub-object added to a family has one place to teach. `MobixJson.unknownFields`/`putUnknown` are the two
shared helpers, and the known-key set is what keeps a codec's own field from being shadowed.

The invariants — open then save, edit *another* page, edit the same element, copy, merge — are
`MobixUnknownFieldInvariantsTest`'s, and the corpus measures the same promise per construct
(`08-unknown-top-level-field`, `09-unknown-nested-fields`, `11-unknown-vocabulary-values`,
`12-unknown-background-type`).

### Vocabulary the schema uses

Values are written as the **names the app uses**, not as desktop XML spellings, and each is checked on
read: a value this build does not know is **refused** rather than silently defaulted, because a stroke
whose tool we misread is a rendering bug we want to hear about. "Refused" means the *element* is not
modelled — it goes back to the preserved channel whole (`RawElement`, its own JSON intact) and the
**document still opens**; every family behaves this way, geometry and living alike, so one element from
a newer build never costs the user the rest of the page.

| Field | Values |
|---|---|
| `tool` | `PEN`, `HIGHLIGHTER`, `ERASER` |
| `lineStyle` | `PLAIN`, `DASHED`, `DASH_DOT`, `DOTTED` |
| colours | `#RRGGBBAA` — lowercase hex, the same order `.xopp` uses (`XoppColor`), **not** the `0xAARRGGBB` the int is held in |
| `align` | `LEFT`, `CENTER`, `RIGHT` — fresh cells are **centred by default** (and a file cell with no `align` reads centred too); use `LEFT` explicitly when a cell reads as prose |

---

## Geometry elements

These four mirror the `.xopp` model one-to-one, so the bridge is a field rename rather than a
translation.

### Stroke

```json
{
  "type": "stroke",
  "id": "stk_01",
  "tool": "PEN",
  "color": "#1a237eff",
  "lineStyle": "PLAIN",
  "capStyle": "round",
  "bounds": { "left": 101.7, "top": 204.4, "right": 104.5, "bottom": 207.5 },
  "points": [
    { "x": 102.4, "y": 205.1, "width": 1.2, "pressure": 0.55, "tilt": 0.248, "azimuth": 0.558, "dtMs": 0 },
    { "x": 103.8, "y": 206.8, "width": 1.4, "pressure": 0.62, "tilt": 0.258, "azimuth": 0.578, "dtMs": 6 }
  ]
}
```

- **`lineWidth` is present only for a constant-width stroke, and absent for a pressure stroke** whose
  widths are per point. That is `.xopp`'s own rule — a `width` *attribute* means the constant form, no
  attribute means one width per vertex — kept because it is the fact the bridge has to re-emit.
- `points[].width` is the **pressure-resolved** width actually drawn, so the document renders the same
  without re-running the pressure filter; `.pressure`, `.tilt`, `.azimuth` and `.dtMs` are the raw
  telemetry and are absent (not zero) for a `.xopp` import, which has none of them.
- **Units and presence.** `.tilt` is radians from the screen normal and `.azimuth` is radians of the
  pen's in-plane angle, exactly as `MotionEvent.AXIS_TILT` and `AXIS_ORIENTATION` report them — the app
  stores the platform's own numbers rather than converting — and `.dtMs` is milliseconds from the
  stroke's first point. A `tilt` of `0` means a pen held upright; a field is meant to be absent only
  when the tablet declares no such axis, and today a genuine zero and a missing axis are both written
  as absent (`store-a-genuine-zero-tilt-instead-of-dropping-it`). None of the three is a width: the
  width is `points[].width`, and telemetry never feeds it ([stylus.md](stylus.md)).
- `fill` is the desktop alpha (`0`–`255`) of a closed stroke's interior; absent when unfilled.

### Text

```json
{ "type": "text", "id": "txt_01",
  "font": "Sans Bold", "size": 12.0, "x": 72.0, "y": 90.0,
  "color": "#000000ff", "content": "E = mc^2" }
```

`font` is the Pango-style description the `.xopp` stores (`format/FontDescription.kt`) kept verbatim,
so a round trip never re-derives bold/italic from a family name. A text element has **no `bounds`**:
its extent depends on the font, which only the renderer knows, and a made-up box would be worse than
none.

**A font name is a rendering hint, not a payload.** Nothing in a `.mobix` carries font bytes, so a
document is complete without the face it names — which is why the portability report emits no entry for
a font ([mobix-assets.md](mobix-assets.md)) instead of calling every page of text incomplete. A reader
resolves a description in this order:

1. the named family, in the weight and slant the description carries;
2. the platform's default family, in the same weight and slant.

Text set in a face another machine lacks therefore **reflows**: the words and their styles arrive, the
author's line breaks do not. Bundling faces is the alternative this build refuses for now — it would
need a new `AssetKind`, a schema entry and, for each face, a licence — and a rule this file can honour
today beats one that waits on three decisions.

### Image and rendered LaTeX

```json
{ "type": "image", "id": "img_01",
  "bounds": { "left": 40.0, "top": 40.0, "right": 240.0, "bottom": 200.0 },
  "asset": "assets/img_a1b2c3d4.webp" }

{ "type": "texImage", "id": "tex_01",
  "bounds": { "left": 100.0, "top": 300.0, "right": 280.0, "bottom": 330.0 },
  "latex": "\\int_0^1 x^2 dx", "color": "#000000ff",
  "asset": "assets/img_e5f6g7h8.png" }
```

`asset` names an entry under `assets/` — **never an inline base64 blob** — and the naming, digest and
missing-payload rules are [mobix-assets.md](mobix-assets.md)'s. What belongs to the element is which of
its fields may carry a payload: required on `image`, because a picture with no bytes is not a picture.

- `texImage.asset` is the **rendered PNG** the `.xopp` carried, and is optional: a document may carry
  only the LaTeX. `latexInAttribute` records which of `.xopp`'s two on-disk shapes
  the source used, so a legacy file is not silently rewritten into the modern one.

### What an unknown or `.xopp`-only element looks like

```json
{ "type": "image2", "syntax": "xml", "attrs": { "width": "12" }, "body": "<x/>" }
```

An element that came from a `.xopp` and has no native shape carries `"syntax": "xml"`: its tag, its
attributes and its markup are wrapped, not translated, and it exports back as itself. Without the
marker it would come back indistinguishable from a native element whose type happens to be the same
word — and would then be skipped by the next `.xopp` export, which is exactly the round-trip loss this
channel exists to prevent.

---

The living entities — table, plot, parametric shape, circuit, math block — are
[mobix-living-entities.md](mobix-living-entities.md)'s.
