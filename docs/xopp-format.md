# The `.xopp` format: the on-disk schema

> Read when you touch the file format: reading, writing, or anything that must round-trip through desktop Xournal++. This is the authoritative home of the mapping.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## The `.xopp` format (code-derived — this is its authoritative home)

A `.xopp` file is a **gzip-compressed, UTF-8, XML document**. Uncompress with gzip and you get
a plain XML tree rooted at `<xournal>`. This section is the authoritative schema our
reader/writer must implement. It is derived from three sources: the real `udiff.xopp` sample in
the repo root (`xournalpp 1.1.1+dev`, `fileversion="4"`), the archived Xournal++ Mobile
reference clone, and desktop Xournal++'s writer. Where the sample and the reference disagree,
**the desktop file wins** — round-trip safety is measured against desktop Xournal++.

#### Two containers, one XML: gzip vs ZIP-package (`SaveFormat`)

The same `<xournal>` XML is written in one of two containers, chosen in the "Save As" dialog and
then made **sticky** — every later plain Save reuses the last-picked format (owned by
`format/SaveFormat.kt`, wired in `MainActivity`; opening a document adopts the format it was
stored in, classified by `format/FileKind.kt` — see below).

#### The `.xopp` file type on Android (`XOPP_MIME`)

A `.xopp` is a **typed document** here, not a generic blob: the app declares `application/x-xopp`
(`io/SaveTarget.kt`) and registers itself for it in the manifest, and every document it writes is
created with that type. The type is not invented — it is the one the desktop app registers for
`*.xopp` (its `.desktop` `MimeType=` line and its shared-mime-info definition), so both
installations agree on what a `.xopp` is.

Two properties of that constant matter, and both are easy to break by accident:

- **It must stay a type the platform maps to no extension.** SAF appends an extension whenever it
  can map the MIME to one, and `application/gzip` made it save `document.xopp.gz` — a name desktop
  Xournal++ will not open. Nothing maps `.xopp`, so the name we ask for is the name we get.
- **The manifest has to agree with it.** The intent filters mirror how a file can arrive:
  `content://` and `file://` by `application/x-xopp`, plus a `file://` filter matching the `.xopp`
  extension for the documents that arrive *untyped* (one from desktop Xournal++, a download, a copy
  on a share — the system only knows what `.xopp` means because this app declares it). Content URIs
  cannot be matched by extension on Android, so a provider that types a `.xopp` as
  `application/octet-stream` (the external-storage provider derives the type from the extension, and
  does not know this one) will not offer the app; the in-app **Open** picker instead asks for `*/*`
  and lets the content sniffer decide, which is why Open never depends on the type at all.

The save and export pickers ask for a **name the user recognises**, not a generic placeholder:
`io/SaveTarget.kt`'s `xoppNameFor` / `pdfNameFor` are the one "swap the extension" rule behind both,
so a document always lands as a `.xopp` (even one opened as `notes.txt`), and a PDF export lands as
`notes.pdf` beside the `notes.xopp` it came from, falling back to `Untitled.*` for a document that
has no name of its own.

#### What the open path accepts (`format/FileKind.kt`)

Open never trusts the file name: even though a `.xopp` does have a registered type now (see above),
a provider need not report it, so the picker is unfiltered (`*/*`) and SAF hands back `content://`
URIs with no reliable suffix. `FileKind.sniff()` reads
the first `MAGIC_BYTES` (512) off the buffered stream and rewinds it, so the same stream goes on to
the loader its verdict picks. The sample is far larger than any magic number because plain text has
no signature at all — it is recognised by the whole sample decoding as printable UTF-8:

| Magic | `FileKind` | Loaded as | Sticky `SaveFormat` |
|---|---|---|---|
| `PK` | `ZIP` | ZIP-package `.xopp` (`XoppZip.open`) — the PDF travels inside | `ZIPPED` |
| `1f 8b` | `GZIP` | gzip `.xopp` (`Xopp.open`), PDF background relinked by path/URI | `ORIGINAL` |
| `%PDF-` | `PDF` | fresh annotatable document, one page per PDF page (`PdfImport.documentFor`) | `ORIGINAL` |
| `<?xml` / `<xournal` | `XML` | uncompressed Xournal++ XML (`Xopp.parseXml`); saved back compressed | `ORIGINAL` |
| `89 PNG 0d 0a 1a 0a`, `ff d8 ff`, `RIFF` + `WEBP` at 8 | `IMAGE` | PNG / JPEG / WebP, one page the size of the image with it as the page's pixmap background (`render/ImageImport.kt`) | `ORIGINAL` |
| none, but the sample decodes as printable UTF-8 (tab/CR/LF allowed, leading BOM skipped) | `TEXT` | plain text (`.txt`, `.md`), typeset into a generated PDF-backed document (`io/TextImport.kt`) | `ZIPPED` |
| anything else (empty or binary) | `UNKNOWN` | rejected with an "Open failed" toast | — |

- **`ORIGINAL`** — the legacy gzip `.xopp` (`format/Xopp.kt`, JDK `GZIPOutputStream`). A PDF
  background stays **linked by location** (`domain="absolute"`, its path/URI). The
  interchange-safe default desktop Xournal++ also writes.
- **`ZIPPED`** — a self-contained ZIP-package `.xopp` (`format/XoppZip.kt`) with the PDF
  **embedded inside** the archive. Entries: `mimetype`, `META-INF/version`
  (`current=<fileversion>\nmin=1`), `content.xml` (the same XML, plain — *not* gzipped), and the
  PDF as `bg.pdf` (referenced by `domain="attach"`, `filename="bg.pdf"` — an in-archive entry
  name, not a sibling path). Because the PDF travels inside the one file, a ZIPPED document
  reopens **in this app** with its background intact (no sibling to resolve).
  - **Intentional mimetype deviation (targeting release Xournal++ on Arch Linux).** The
    spec-correct mimetype is `application/xournal++`, but the *released* Xournal++ 1.3.5 (the
    Arch build the owner runs) has an **inverted** mimetype check in `LoadHandler`
    (`if (!strcmp(mimetype, "application/xournal++")) → "Mimetype wrong"`), so it *rejects* a
    correctly-labelled archive. We therefore deliberately write a non-canonical mimetype
    (`XoppZip.MIMETYPE = "application/x-xopp-zip"`), which its one-value check accepts. This is a
    known, temporary hack — flip `XoppZip.MIMETYPE` back to the canonical string once upstream
    fixes the check. Xournal++ enforces neither entry order nor per-entry compression.

### Units and coordinate system

- **All geometry is in points (pt), 1 pt = 1/72 inch.** Page size, stroke coordinates and
  widths, text position and size, and image/teximage bounding boxes are all pt.
- **Origin is top-left**, x increases right, y increases down. Values are written as decimals
  with 8 fractional digits (e.g. `162.27585752`), but any valid decimal parses.

### Color encoding

- On-disk form is `#RRGGBBAA` — 8 hex digits, **alpha last** (e.g. `#000000ff` opaque black,
  `#ffffffff` opaque white). Our writer emits this form.
- On **read** be lenient: also accept 6-digit `#RRGGBB` (implicit `ff` alpha) and the desktop
  named-color keywords (`black, blue, red, green, gray`/`grey`, `lightblue, lightgreen,
  magenta, orange, yellow, white`). Internally we store ARGB; only the byte order differs
  from Android's `0xAARRGGBB`, so convert on the boundary.

### Element tree

```
<xournal creator="…" fileversion="4">
  <title>…</title>                     (preserved when custom; a fixed banner otherwise)
  <preview>…base64 PNG…</preview>       (optional thumbnail; regenerated on write)
  <page width height>
    <background type style color … />
    <layer>
      <stroke …>…coords…</stroke>
      <text …>…text…</text>
      <image …>…base64…</image>
      <teximage …>…latex…</teximage>
      <anything-else …>…</…>            (kept verbatim as RawElement; see below)
    </layer>                            (1+ layers per page)
  </page>                              (1+ pages per document)
</xournal>
```

**`<xournal>`** — root. Attributes: `creator` (writer id string), `fileversion` (`"4"` for
current desktop). Children: optional `<title>`, optional `<preview>`, then 1+ `<page>`.

**`<title>`** — decorative text child; desktop writes a fixed banner string. Read into
`Document.title` and written back verbatim, so a document with a custom title keeps it. The
standard banner reads back as `null` (i.e. "no title of its own"), which keeps
model → XML → model an identity for documents we author.

**`<preview>`** — inner text is a base64-encoded PNG thumbnail of page 1. Optional; we
regenerate it on write (or omit it — desktop tolerates its absence).

**`<page>`** — attributes `width`, `height` (pt). Contains exactly one `<background>` then 1+
`<layer>`.

**`<background>`** — empty element, attributes depend on `type`:
- `type="solid"`: `color` (hex or named), `style` ∈ `plain | lined | ruled | graph | dotted |
  isograph | isodotted` — these are desktop's own style strings, the map being
  `PageTypeHandler::getPageTypeFormatForString` (`isograph`/`isodotted` are its isometric papers), and
  `style` is otherwise a free name that desktop resolves against `pagetemplates.ini`. **`config`** is
  an optional comma-separated `key=value` list of ruling parameters (`BackgroundConfig.cpp` splits on
  `,` and each entry at its **last** `=`): `r1` spacing in pt (line spacing for lined/ruled, square
  size for graph/dotted, **triangle side** for the isometric styles), `m1` margin, `lw` line width,
  `bli`/`blw` bold-line interval and width, and colour keys (`f1`/`f2`/`af1`/`af2`) we preserve
  without interpreting. This app models the whole attribute (`BackgroundRuling`) and writes only the
  keys it changes, so a page's own spacing survives a round trip.
- `type="pixmap"`: `domain` ∈ `absolute | attach | clone`, `filename` (image path/URI).
- `type="pdf"`: `filename` (PDF path/URI), `pageno` (**1-based** PDF page index, matching desktop
  Xournal++'s `SaveHandler`; converted to/from the 0-based `Background.Pdf.pageNo` used internally
  to index Android's `PdfRenderer` — see `XoppReader`/`XoppWriter`); `domain` on the first pdf
  background of the doc. The `domain` follows from the chosen `SaveFormat` (see the container
  section above), applied by `documentWithPdfDomain` in `render/PdfBackgroundDomain.kt`:
  - `domain="absolute"` — `filename` is the PDF's path/URI; the .xopp links to it in place (what
    the gzip `ORIGINAL` format writes). There is **no `relative` domain**: desktop carries a
    relative path under this same domain and resolves it against the .xopp's own folder
    (`LoadHandler::getAbsoluteFilepath`), using an absolute one as-is. See [Relative PDF references](pdf.md#relative-pdf-references) — a relative path is the portable form, so it is what we prefer to write.
  - `domain="attach"` — the PDF is bundled *inside* the `ZIPPED` container as the `bg.pdf` archive
    entry; `filename` is that in-archive name (`bg.pdf`), which desktop resolves via
    `readZipAttachment`. Self-contained and portable, and reopens with its background intact in this
    app (the PDF travels in the same file). *(The earlier gzip-plus-sibling attach — a
    `<xoppname>.bg.pdf` file written next to the .xopp — was replaced by this embedded form.)*
  - `domain="clone"` is **pixmap-only** in desktop Xournal++ (it reuses an earlier background image
    by id) and is never written for a PDF background, so the Save As dialog does not offer it.

**`<layer>`** — optional `name` (desktop's `<layer name="Layer 1">`; preserved on round-trip, null
when omitted). Children in document order: any mix of `<stroke>`, `<text>`, `<image>`, `<teximage>`.
**Document order is z-order** and must be preserved on round-trip. A child whose tag we don't
model (a vendor or future element) is captured verbatim — attributes plus raw inner markup — as
`format/model/Element.kt`'s `RawElement` and re-emitted untouched in its original position, so it
is never silently dropped by a save. It has no geometry, so it is invisible to hit tests,
selection ops and PDF export — every hit test filters on `ElementBounds.isHitTestable(...)` first
(false for a `RawElement` or an empty stroke) rather than trusting the empty box `ElementBounds.of`
returns, which would otherwise read as a real box at the page origin. Layer *visibility* is **not** a
format attribute — it's a view-only editor state (a hidden layer still round-trips with its content).

**`<stroke>`** — the core drawable. Attributes:
- `tool` ∈ `pen | highlighter | eraser`. The highlighter renders distinctly from the pen: a
  broad, **constant-width** band (its width snapped to the desktop's own highlighter tips, pressure-
  independent → a single `width` value) drawn as **one path in multiply blend at desktop Xournal++'s
  `OPACITY_HIGHLIGHTER` (0.47, `StrokePainter.HIGHLIGHTER_RENDER_ALPHA = 0x78`)** — `StrokePainter.drawBand`.
  Multiply is what makes it read as a marker (paper stays bright, ink darkens) instead of a plain
  alpha-over wash, and drawing the whole band as one path keeps the alpha from beading at
  self-overlaps. It uses `android.graphics.BlendMode.MULTIPLY` (API 29+, Skia's separable multiply);
  `PorterDuffXfermode(MULTIPLY)` is **not** equivalent — the Porter-Duff variant multiplies the alpha
  too and punches a translucent hole in the page, so the band composites against the window and goes
  muddy. On API 26–28, where `BlendMode` is unavailable, the band falls back to a plain alpha-over at
  the same 0.47 (`StrokePainter.highlighterMultiply`). On disk the colour alpha is **`0x7f`** (`XoppColor.HIGHLIGHTER_ALPHA`, the byte
  upstream's `SaveHandler` writes), even though the renderer paints at 0.47 regardless — as the
  desktop does. PDF export uses the same multiply blend (`PdfVectorPainter.alphaState`).
  Eraser strokes exist in the format but desktop rarely persists them.
- `color` — hex/named as above.
- `width` — **space-separated list of doubles (pt)**. The **first value is the nominal stroke
  width**; the remaining values (if present) are the **per-vertex pressure widths**. A single
  value means constant width. On read, if fewer widths than vertices, reuse the first for all.
- `capStyle` — `round` | `butt` | `square` (line cap; desktop attribute, default `round`).
- `style` — line pattern ∈ `plain | dash | dashdot | dot` (default `plain`, omitted when plain).
  Dashed/dotted strokes render as one constant-width path with a width-proportional dash pattern
  (`StrokePainter.dashIntervalsPt`, shared by screen and PDF export).
- `fill` — fill alpha `0..255` painted inside the closed stroke (shapes/highlighter fill), or absent
  for no fill.
- `ts`, `fn` — audio-recording offset (**milliseconds**) and sidecar **file name** for pen-replay;
  `ts="0" fn=""` when unused. Read/written as a pair by `audio/AudioAnnotation.kt`; any stroke we
  don't stamp keeps whatever the file had, verbatim. The named file is **not** in the `.xopp` — it
  is a `.wav` sidecar beside it (see [`audio.md`](audio.md)). No rendering meaning.
- **Inner text**: a flat space-separated coordinate list `x0 y0 x1 y1 …` (pt). Vertex *i* is
  `(text[2i], text[2i+1])` and its width is `width[i+1]` (or `width[0]` if constant).

**`<text>`** — attributes `font` (a Pango-style font **description**: family plus optional
`Bold`/`Italic` tokens, e.g. `Sans Bold Italic` — parsed/composed by `format/FontDescription.kt`),
`size` (pt), `x`, `y` (pt, top-left anchor), `color`. **Inner text** is the string, XML-escaped
(`&amp; &lt; &gt;`). The description has no underline token, so underline is not representable.

**`<image>`** — attributes `left`, `top`, `right`, `bottom` (pt bounding box). **Inner text**
is base64-encoded raw image bytes (PNG/JPEG as stored). Desktop line-wraps that body, so both
`<image>` and `<teximage>` decode with the MIME decoder (whitespace-tolerant); an undecodable
body yields an empty image rather than failing the whole file.

**`<teximage>`** — a LaTeX-rendered image. Attributes `text` (LaTeX source), `color`, and
`left/top/right/bottom` bounding box (pt). **Inner text** is the base64-encoded PNG desktop
rendered from that source; we keep those bytes verbatim (`TexImage.data`) so they survive a
round-trip. Older files without a `text` attribute put the LaTeX source in the body instead;
`TexImageElement.latexInAttribute` records which of the two shapes the file used so we re-emit the
one it was authored in rather than converting it.

**A comment inside `<xournal>`** — after the last `<page>`, before the closing tag — is not part of the
document: the reader skips comments (`XmlPullReader`) and desktop Xournal++ ignores them. MobiXournal
writes exactly one, the `.mobix` **bridge block** (`Xopp.BRIDGE_COMMENT`, tag `mobix:meta`), whose
payload is escaped so the comment can hold no `--`, and reads it back into `Document.bridge`; what it
means and how far it may be trusted is [mobix-format.md](mobix-format.md)'s to say. A comment this app
did not write is **not preserved** on save — it is not document content and no round-trip contract
covers it — and the bridge block is the one that is.

### Fidelity notes / round-trip hazards

- **Preserve unknown attributes — on every element, not just strokes.** Desktop emits attributes
  we don't render (`ts`/`fn` on strokes *and* text boxes, custom ruling configuration on a solid
  background, future ones anywhere); each of `<page>`, `<background>`, `<layer>`, `<stroke>`,
  `<text>`, `<image>` and `<teximage>` carries an `extraAttrs` map in source order and re-emits it
  ahead of the attributes we do model.
- **Escape control characters in attribute values.** A conforming XML parser normalises a literal
  newline/CR/tab inside an attribute to a space, so `XmlWriter` writes `&#10;`, `&#13;` and `&#9;`
  — without that, a preserved value containing one would not survive a trip through desktop.
- **Preserve layer child order** exactly (it is z-order).
- **Alpha matters** for highlighter — don't force `ff`.
- The Xournal++ Mobile reference is a *simplified* writer (always alpha `ff`, fixed title,
  skips erasers, pressure-as-width-list); use it for element names, not as the fidelity bar.

- [x] **Drift test in place.** `RealFileRoundTripTest` re-serializes `udiff.xopp`, and
      `FormatDriftTest` does the same over a committed fixture set
      (`app/src/test/resources/fixtures/`, each validated to load in desktop Xournal++ 1.3.5)
      while asserting the fixtures collectively cover this schema surface — all background
      styles, multi-page, layers, every element type, pressure vs. uniform width, highlighter
      alpha. A parser/writer change that would corrupt a real file, or drop a documented
      feature's coverage, fails the build (per `AGENTS.md`'s code-derived-fact rule).
      `FormatDriftTest` covers only `Background.Solid` styles; the `pdf` background on-disk
      round-trip (filename+domain on page 1, `pageno`-only on later pages) is locked in
      separately by `PdfBackgroundRoundTripTest`, the `pixmap` background (linked references under
      `domain="absolute"` and bundled ones under `attach`, through both save paths) by
      `PixmapBackgroundRoundTripTest`, and the stroke `style`/`fill` attributes plus the
      `<layer name>` attribute by `StyleFillLayerNameRoundTripTest`.
- [x] **XML-equality drift test.** `XmlEqualityRoundTripTest` compares the *emitted XML* against
      each fixture's source XML (normalized for formatting, attribute order, number precision and
      the `<title>` boilerplate — see the test's doc), and asserts the writer is a fixed point.
      The model-equality tests above can't see a difference that both the reader and writer agree
      on; this one can, and it is what caught the dropped `<teximage>` PNG body.
