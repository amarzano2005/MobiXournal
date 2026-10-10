# Architecture

Authoritative home for **how the system works internally**: the `.xopp` format mapping, the
read/write data path, the core components, the repository layout, and the load-bearing design
decisions. `AGENTS.md` points here; the specifics live here.

> Status: the lossless `.xopp` format core and the Compose/`SurfaceView` editor are implemented,
> including PDF import and export. This doc is kept current as the code evolves; record design
> decisions here so they aren't re-litigated.

## Prior art — has someone already done this?

Surveyed 2026-07-30. **Conclusion: no maintained, native, full-fidelity `.xopp` editor for
Android exists.** The gap this project targets is genuinely open, and the community actively
asks for it. Details:

| Project | Read/Write | Tech | Status |
|---------|-----------|------|--------|
| [Xournal++ Mobile](https://gitlab.com/TheOneWithTheBraid/xournalpp_mobile) (mirror: [GitHub](https://github.com/xournalpp/xournalpp_mobile)) | Read **and** write, full `.xopp` | Flutter/Dart | **Archived 2025-08-27**; no real features since ~2021; never stable (author flagged stroke support as poor) |
| [Xournal++ viewer](https://f-droid.org/packages/de.thefeiter.xournalviewer/) | **Read-only** | Android | On F-Droid; view-only, cannot edit or save |
| [Linwood Butterfly](https://xournalpp.github.io/community/other-software/) | *Imports* `.xopp`; native format is its own | Flutter | Actively maintained, but **no lossless round-trip** — a different app that can read our files |
| [Termux + Termux-X11](https://github.com/xournalpp/xournalpp/discussions/5654) | The real desktop app | Linux-in-a-container | Works, pen pressure works, but a compatibility-layer hack, not a native app |

**Decision — build our own, learn from Xournal++ Mobile.** The one true round-trip attempt
(Xournal++ Mobile) reached full-format read/write and was then abandoned; its weak point was
exactly stroke fidelity, which is what our "round-trip safety" principle targets. We build
fresh on native Android (not Flutter), but treat that project as a **format reference**, not a
competitor.

### Reference clone

The archived Xournal++ Mobile source is cloned locally at **`reference/xournalpp_mobile/`**
(git-ignored — see `.gitignore`; not part of our build, kept only for reading code). It is
**EUPL-1.2** licensed — read it for the format mapping, but do not copy code into our
(differently-licensed, TBD) tree without clearing the license implications.

Its data model is the most useful artifact — a clean decomposition that mirrors the `.xopp`
structure:

- `lib/src/XppFile.dart` — top-level document: gzip (via the `archive` package) + XML (via the
  `xml` package) load/save; `XppFile → pages`.
- `lib/src/XppPage.dart`, `lib/src/XppLayer.dart` — page and layer containers.
- `lib/src/XppBackground.dart` — page backgrounds (plain/ruled/graph, PDF).
- `lib/layer_contents/` — the drawable content types: `XppStroke.dart`, `XppText.dart`,
  `XppImage.dart`, `XppTexImage.dart`.

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
    (`LoadHandler::getAbsoluteFilepath`), using an absolute one as-is. See *Relative PDF
    references* below — a relative path is the portable form, so it is what we prefer to write.
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
  is a `.wav` sidecar beside it (see *Audio* below). No rendering meaning.
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

## Stack — pinned 2026-07-30

Native Android, no cross-platform framework (the abandoned reference was Flutter; we go
native for stylus latency and platform fit).

- **Language:** Kotlin, targeting the modern Android SDK.
- **App chrome / UI:** **Jetpack Compose with Material 3** (Material You) for all app chrome —
  app bar, menus, dialogs, the tool palette. Satisfies the Material Design requirement in
  `TODO.toml`.
- **One colour scheme drives every surface, and one chrome colour paints it.** All chrome — app
  bar, rail/toolbar, dialogs, the elevated popovers — takes its colours from
  `MaterialTheme.colorScheme` (`ui/theme/Theme.kt`); no surface hardcodes a colour. The canvas is the
  one exception by construction: it's a `SurfaceView` outside the Compose tree, so
  `ui/theme/ChromeColors.kt` maps the scheme onto its four chrome colours (page backdrop and its
  hairline outline, selection marquee/handles, guide overlay) as ARGB ints and `EditorScreen` pushes
  them in via `DrawingSurfaceView.applyChromeColors`.

  The chrome then splits in two, by job, and each half has one value:

  - **`rememberChromeColor()` — the bars** (`background`): the `Scaffold`'s container under the
    transparent top bar, the tab strip, and both Android system bars (`Theme.kt` sets
    `statusBarColor`/`navigationBarColor` from it). Three dark greys stacked down the screen read as
    three unrelated surfaces, so all of them take this one.
  - **`rememberToolbarColor()` — the implement colour** (`surfaceContainer`): the Main Toolbar rail
    (`SideToolbar.kt`) and the Secondary Toolbar's floating dock (`EditorRegions.kt`) are filled with
    it, and **the canvas backdrop takes the same value**, so the desk around the page stack is literally
    the material the tools are made of rather than a darker void behind them. The docks keep their one
    tonal step over the bars, which is what makes a floating dock read as floating instead of dissolving
    into the bar it hangs from. Rail, dock and surround all read this, so they cannot drift apart.

  A new *bar* surface takes `rememberChromeColor`, a new *tool* surface takes `rememberToolbarColor`;
  neither is a `surfaceContainer*` role spelled out by hand. `pageOutline` (the scheme's
  `outlineVariant` hairline around each sheet, drawn in `DrawingSurfacePaint.drawPageOutline` — **not**
  in `BackgroundRenderer`, which the PDF exporter also calls) is what keeps a near-white page findable
  against a light-themed surround. **Ink, pen palette and page backgrounds are document data, not
  chrome, and are deliberately never themed** — they must round-trip to the file byte-for-byte.
- **Drawing surface:** a custom low-latency **`SurfaceView`** (not Compose `Canvas`) hosted in
  the Compose tree via `AndroidView`. Stylus input comes from raw **`MotionEvent`** with
  `getPressure()` / `getAxisValue(AXIS_PRESSURE)` and historical points
  (`getHistoricalX/Y/Pressure`) so fast strokes keep their samples. This is the load-bearing
  choice for "round-trip safety" — we capture pressure at the same fidelity the format stores.
- **One width source for every tool.** *Decision (2026-08-04):* `DrawingSurfaceView.widthForPressure()`
  is the single place a **tapering** stroke width is derived from the size setting: the highlighter's
  constant band and the pen's per-vertex pressure taper both go through it. *Decision (2026-10-03):*
  the pen's pressure response is desktop Xournal++'s own. A **Pressure sensitivity** switch
  (`AppSettings.pressureEnabled`) turns the taper off entirely, so a pen stroke draws at its set size
  and matches a shape exactly; when on, the width is `max(minimumPressure, pressure × multiplier)`
  (`PressureCurve.penFactor`) — the desktop's `filterPressure` — with `minimumPressure` and
  `pressureMultiplier` carried as the desktop's own two settings. *Decision (2026-10-05):* the whole
  expression lives once, in `PressureCurve.widthPt()`, which `widthForPressure()` calls — so the
  settings' effect on a drawn width is unit-tested (`PressureCurveTest`) instead of being read off a
  call site. The two sliders' **ranges are the desktop's own** (`adjustmentMinimumPressure` 0.01–1,
  `adjustmentPressureMultiplier` 0.5–4, mirrored as `PressureCurve.MINIMUM_PRESSURE_*` /
  `MULTIPLIER_*`): a narrower ceiling is what made the multiplier feel inert. And because half of a
  switched-off filter can only do nothing, both controls are shown **disabled with the reason** while
  `pressureEnabled` is false — desktop Xournal++ greys the same frame out — rather than looking broken. Shapes (line, rectangle, ellipse,
  arrow, double arrow, coordinate axis, spline) have no pressure stream, so they are drawn at the size
  setting **verbatim** (`DrawingSurfaceView.nominalShapeWidthPt()`), exactly as the desktop draws
  them: an app-invented scale would make a shape drawn here a different thickness from one drawn on
  the desktop, and the same file would look different in each app. A shape is therefore as thick as a
  fully-pressed pen and a little thicker than a light stroke — the desktop's own behaviour, which
  switching pressure off removes. A recognised freehand shape keeps the mean width of the stroke it
  replaces. The size itself is per tool family:
  `AppSettings.lastWidth` feeds the pen, `highlighterWidth` the highlighter, and `shapeWidth` the
  line/shape/spline tools — the last kept separate (and defaulting to the same middle pen-width slot
  the pen starts at, so a fresh figure and fresh handwriting come out the same weight) so selecting
  a shape neither inherits a hairline pen nor drags the pen thick. The highlighter is the only tool
  left on the wide slot. The highlighter is the
  one tool whose width is not the raw setting: `DrawingSurfaceDefaults.highlighterWidthFor` snaps it
  to desktop Xournal++'s own highlighter tips (`HIGHLIGHTER_SIZES_PT` = 2.83 / 8.50 / 19.84 pt, its
  1 mm / 3 mm / 7 mm slots from `ToolHandler::initTools`), so a highlighter band is the thickness
  the desktop app would draw however the pen's S/M/L slots happen to be configured. Which *slot* a
  width is written to follows one rule too: `AppSettings.withWidthFor(tool, width)` sends every
  width change — toolbar slider, tool preset — into the live tool's own
  field, so thickening the highlighter or a figure can never become the pen's width. Restoring on a
  switch is the other half of the same rule: `EditorUiState.switchToolTo` writes the outgoing
  tool's style into its slot and reads the incoming one's back out, and
  `DrawingSurfaceView.activateTool` is the wrapper that also pushes colour/width to the canvas and
  persists. *Decision (2026-10-01):* rail slot, keyboard shortcut and barrel button all go
  through that one function. They used to hand-roll their own save/restore, and the paths
  that restored nothing at all (the pen↔eraser shortcut, a highlighter preset
  writing `lastWidth`) left the pen drawing at the figure's or the highlighter's fat width — the
  reported bug. A related trap: the grouped rail slot's picker fired the switch **twice** (once
  from `onSelect`, once from its own `onClick`), and the second save was built on a stale settings
  snapshot, silently dropping the first; `SideToolbar` now activates from the click only.
- **`.xopp` I/O — no third-party format libraries.** Both containers use only the JDK's
  `java.util.zip`: gzip via `GZIPInputStream` / `GZIPOutputStream` (`ORIGINAL`), and the
  ZIP-package via `ZipInputStream` / `ZipOutputStream` (`ZIPPED`, `format/XoppZip.kt`). XML goes
  through Android's built-in streaming `XmlPullParser` (read) and `XmlSerializer` (write).
  Streaming keeps large documents off the heap and gives us exact control over attribute
  preservation (a fidelity requirement above).
- **PDF export — PDFBox (`com.tom-roush:pdfbox-android`).** *Decision (2026-07-31):* the one
  non-framework runtime dependency, taken deliberately. The framework `android.graphics.pdf`
  writer (`PdfDocument`) can only paint onto a canvas, so exporting an imported PDF forced every
  page through a raster bitmap — a no-op import→export bloated files ~10× and discarded vector
  content. PDFBox is the only mature, permissively-licensed (Apache-2.0) library that can import
  an existing PDF page **preserving its vector content** and append a vector overlay; iText's
  AGPL licence ruled it out.  Scope is contained to `PdfExporter`/`PdfVectorPainter`/
  `PdfBackgroundPainter` — the *export* path only. (An experiment that also used PDFBox to draw a
  page **on screen** through its Android `PDFRenderer` was retired in 2026-10-11 — a per-frame
  content-stream replay that made interaction laggy; see the vector-PDF note below.) The `.xopp` I/O
  layer above stays dependency-free, and the display path is the framework `PdfRenderer` alone.
- **File access:** the Storage Access Framework (`ACTION_OPEN_DOCUMENT` /
  `ACTION_CREATE_DOCUMENT`) so a `.xopp` opens/saves in place on the device — the file on disk
  is the only source of truth (per `AGENTS.md` non-goals: no cloud, no custom format).
- **Build:** Gradle (Kotlin DSL) inside a **Docker** container (Podman fallback) per
  `AGENTS.md`. Pipeline details live in `docs/tools.md`; build/run for a human lives in
  `README.md`.

## Data path

The core loop:

```
open (SAF Uri) → UriStaging.stageIn (worker thread) → local staging file
   → FileKind.sniff (gzip / ZIP / PDF / XML / text) → GZIP|ZIP|plain InputStream → XmlPullParser → Document model
     (a raw PDF instead becomes a fresh document via PdfImport.adoptPdf/documentFor)
   → render on SurfaceView (Material 3 chrome around it)
   → stylus edits mutate the model → XmlSerializer → GZIP|ZIP OutputStream (sticky SaveFormat)
   → local staging file → UriStaging.stageOut (worker thread) → save (SAF Uri)
```

### Staging: remote (SSHFS/FTP/cloud) documents

The picker lists files on mounted network shares, and they arrive as ordinary `content://` URIs
from a remote `DocumentsProvider` — the only difference is **latency**. So every document transfer
is staged through a local file by `io/UriStaging.kt` — driven by `io/DocumentIo.kt`, which owns the
whole document-I/O policy (staging, both background-PDF stores, and the sniff/read, encode and merge
steps) so the activity is left with intent plumbing — and run off the UI thread by
`MainActivity.inBackground`, which shows the editor's blocking "Opening…/Saving…" overlay:

- **Read** — the bytes come down once into a staging file; sniff/parse/rasterise then work against
  a local file that can be re-read at will. A failed fetch closes the half-built tab and reports it.
- **Write** — the document is serialised locally *first* and only then pushed out in one pass, so a
  link that drops mid-encode can't leave a truncated `.xopp` on the far end.

Every staging file is allocated by `io/ScratchDir.kt` under a name **no other transfer reuses**, and
deleted by its caller once read. Since transfers run on worker threads, two of them overlap easily;
the fixed `open.tmp` this replaced meant a second, slower download overwrote the first document's
bytes before they were parsed, and both tabs came up holding the same document.

A document's **background PDF** gets its own file, allocated by `io/PdfStore.kt`. Whether it came
out of a ZIP package (`XoppZip.open`), was resolved from a `pdf` background reference
(`DocumentIo.resolvePdfBackground`) or was imported (`DocumentIo.adoptPdf`), the bytes land under a name no
other document uses, and that name is what `OpenTab.pdfPath` records. This is a correctness
requirement, not housekeeping: `PdfPageCache` rasterises through a file descriptor it holds open for
as long as the document is on a canvas, so a shared fixed name (the old `background.pdf`) let the
next document opened — in the other split pane, or another tab — overwrite the bytes underneath a
live renderer, and every page not yet rasterised came back blank. Since the files are never
rewritten, two views of one document (a mirrored tab) can share a path safely. As defence in depth
`PdfPageCache.checkSource` stamps the file's size and mtime when it opens the renderer and re-checks
on every request, re-opening (and dropping the whole cache) if the bytes were replaced anyway, and
closing itself — so the page draws with no background rather than white — if the replacement can't
be opened as a PDF. Unique names would
otherwise accumulate, so `MainActivity.prunePdfCache` sweeps the store against the paths the open
tabs *and* the live surfaces still reference, on every session persist and tab close.

The open picker asks for a **persistable read+write** grant (`OpenDocumentForEditing`), so plain
Save writes back to the tab's own `OpenTab.uri` (`MainActivity.saveActiveTab`) instead of asking for
a location, and a restored tab can still reach its file after a restart. The `CreateDocument` path
takes the same grant after a successful Save As.

Reading and writing are **streaming and symmetric**: the parser builds the model element by
element; the serializer walks the model in document order and re-emits it, carrying through any
preserved-but-unrendered attributes so the file round-trips.

### Tabs and the session cache

Several documents can be open at once, but there is only **one** `DrawingSurfaceView`. A tab switch
is therefore a swap, driven by `MainActivity`:

```
snapshot the surface into the outgoing OpenTab (document, save format, PDF path, page)
  → TabManager.select(index)
  → load the incoming OpenTab into the surface (setPdfSource → load → goToPage)
```

Consequences worth knowing:

- **Undo history is per surface, not per document.** It is cleared by `load`, so a tab switch starts
  the incoming document with a clean history. Content, including unsaved edits, is untouched.
- **Only the active tab's document is live**; every other tab holds the snapshot taken when it was
  last showing. That is also what gets written to disk.
- **The session is cached, not saved.** `TabStore` writes `filesDir/tabs/`: a `session.index` line
  file (`TabIndex`) plus one `<id>.xopp` gzip snapshot per tab, rewritten on every tab change and in
  `onPause`. On launch it is read back, so the app reopens on the same tabs with the same unsaved
  edits. It is a restart cache keyed by tab id — the user's own file (the tab's `uri`) is still the
  only thing the desktop ever sees, and a snapshot never stands in for saving.
- **Nothing is parsed or written on the main thread.** Gzip XML over a whole document takes long
  enough to trip Android's ANR watchdog, so the session is restored **lazily and off-thread**:
  `TabStore.load` reads only the small index and returns every tab as a placeholder
  (`OpenTab.hydrated = false`); `TabStore.hydrate` parses one tab's snapshot, and `MainActivity.show`
  runs it on a worker and paints the canvas when it lands, so a cold start with many tabs never waits
  on more than the index. Writing is the mirror image: `EditorPane.persist` reads the session on the
  caller's thread and queues the save onto a single-threaded writer, with `awaitPersist` in `onPause`
  so a backgrounded app still lands its snapshot. Four rules keep a placeholder from destroying
  content: `TabStore.save` skips unhydrated tabs (their file on disk is the truth); it also skips a
  tab that is still **`OpenTab.opening`** — a slow remote/cloud fetch whose placeholder document is
  on the strip while the canvas still shows the *previous* tab; `snapshotActiveTab` skips a tab whose
  parse is still in flight, for the same reason; and `TabStore.hydrate` leaves a tab **unhydrated**
  when its snapshot exists but cannot be parsed, so the unreadable bytes are never overwritten by the
  blank stand-in. Writing is atomic for the same end — a snapshot lands through a `.tmp` sibling and a
  rename, so a kill mid-write can't leave a truncated file that the next launch would fail to read.
  Handing an unhydrated tab to the other pane copies the snapshot *file* (`TabStore.adopt`) rather
  than parsing it.
- Tabs that were opened from a file keep that `content://` URI, so **Save** after a restart still
  writes back to the same document.
- **The tab overview draws previews from the same snapshots.** The top bar's grid button
  (`ui/TabOverviewPopup.kt`) shows one card per tab, each the page that tab was left on, rasterised by
  `render/PageThumbnail.kt` — `BackgroundRenderer` + `PageRenderer` into a small bitmap, so a preview
  is drawn by the same code as the canvas. `pdf`/`pixmap` backgrounds are drawn as their plain sheet
  only: those images come from asynchronous caches owned by a live surface. Opening the grid
  snapshots the active tab first (`TabsUiState.onOverview`), then requests each preview through
  `MainActivity.previewTabPage`, which hydrates (without writing back into the session) and
  rasterises on **one** shared worker — a thread per tab put every open document in memory at once and
  was an out-of-memory kill on a session of large files.

### Split view: the same thing, twice

Split view is modelled as **panes**, not as a second mode. An `EditorPane` (`panes/EditorPane.kt`)
bundles everything that used to be a per-activity singleton — the canvas, a `TabManager`, the sticky
`SaveFormat`, the pending save name and its own `TabStore` directory. `MainActivity` holds a fixed
list of two of them plus an `activePane` index; `surface`, `tabs`, `saveFormat` and `pendingSaveName`
are now *accessors* onto the pane in focus, which is why the open/save/import/audio code above is
still written against "the" document and needed no changes.

The design decisions worth keeping:

- **One pane has focus; the chrome drives that pane.** A touch anywhere in a pane (observed on the
  pointer-input *initial* pass, so the canvas still receives the event) makes it active. There is no
  second toolbar and no per-pane menu — the single top bar and rail always act on the focused pane.
- **Each pane persists separately.** `TABS_DIRS` gives pane 0 the historical `filesDir/tabs` and pane
  1 `filesDir/tabs-right`, so an existing session still restores and the right-hand pane's documents
  survive both a split-view toggle and a restart. `onPause` snapshots and writes *both*.
- **A pane's Compose mirror is per pane too.** `ui/PaneState.kt` holds the zoom/page/layer/undo state
  a canvas pushes up through its callbacks; `EditorUiState` keeps one per pane and `EditorScreen`
  hands the active one to each region. Each surface's callbacks write into *its own* `PaneState`, so
  a background pane stays current instead of scribbling over the focused one.
- **Turning split view off doesn't destroy the pane.** Its canvas is disposed, so when it comes back
  `restoreTabs` finds a non-empty session and re-loads the showing tab onto the *replacement*
  surface. (Undo history is per surface, so it does not survive that round trip — same rule as a tab
  switch.)
- **A document can be open in both panes as two live views.** Tabs carry an `OpenTab.docKey`; the
  mirror action copies a tab keeping that key, so "same key" means "same document". Every edit
  reaches the other views through `panes/MirrorSync.kt`: `DrawingSurfaceView.doc` is a property whose
  *setter* fires `onDocumentEdited`, so the one place every edit lands is also the one place the
  mirror is notified — no per-operation hooks to keep in sync. `MirrorSync` writes the new document
  into every tab record holding the key (background tabs included, so selecting one is already
  current — all of them take the *same* immutable instance, so this shares one graph rather than
  copying it per tab) and calls `applyMirroredDocument` on any pane showing one. That entry point deliberately
  touches *nothing* but the document — scroll, zoom and columns are left alone, which is what makes
  the two views independent — and clears the receiving surface's undo history, since its snapshots
  predate the other view's edit and undoing to one would discard it. `load`/`applyMirroredDocument`
  write the backing field directly rather than the property, which is what stops an echo loop.
- **The mirror push is coalesced.** Adopting a document costs the receiving pane a full relayout and
  render over every page, and edits arrive as fast as the pen moves, so `MirrorSync.propagate` only
  records the latest document and posts one `flush()` onto the main thread; a burst of edits fans out
  once, with the last one winning. Everything that *reads* the tab records — `snapshotActiveTab`,
  `persistTabs` — calls `mirrors.flush()` first, so the deferral is never observable and no session is
  written that predates a pending edit. `MirrorSyncTest` covers the coalescing (the scheduler is
  injected, so the test runs the pass inline).
- **Which tabs are the same document is shown, not inferred.** `tabs/DocColors.kt` assigns a palette
  colour to every `docKey` open more than once across *both* panes, and `TabStrip` draws it as a dot;
  titles are file names, so they cannot distinguish "open twice" from "two files, one name".
- The split position (`SplitLayout` in `ui/`) is a UI-only fraction: dragged, clamped to leave each
  half at least 15% of the width, and deliberately **not** persisted.

### In-memory document model

The native-Android analogue of the reference's `Xpp*` types — one small Kotlin data
class/module per format element, mirroring the tree in [The `.xopp` format](#the-xopp-format-code-derived--this-is-its-authoritative-home):

Every container and element below also carries an `extraAttrs` map of the attributes we don't
interpret, in source order, so unknown markup round-trips (see the fidelity notes above).

- `Document` — `creator`, `fileversion`, optional title/preview, `List<Page>`.
- `Page` — `width`, `height` (pt), `Background`, `List<Layer>`.
- `Background` — sealed type: `Solid(color, style)`, `Pixmap(domain, filename)`,
  `Pdf(filename, pageNo, domain?)`.
- `Layer` — ordered `List<Element>` (z-order) plus optional `name`.
- `Element` — sealed type: `Stroke`, `Text`, `Image`, `TexImage`, `RawElement`.
  - `Stroke` — `tool`, `color`, `capStyle`, `lineStyle` (plain/dash/dashdot/dot), `fill` (0..255 or
    null), `List<Point>` (each `x, y, width`), plus preserved raw attrs (`ts`, `fn`, unknowns).
  - `Text` — `font`, `size`, `x`, `y`, `color`, `content`.
  - `Image` — bbox `left/top/right/bottom`, decoded bytes.
  - `TexImage` — bbox, `latex`, `color`, the rendered PNG bytes (`data`, nullable), and
    `latexInAttribute` (which of the two on-disk shapes the source used).
  - `RawElement` — an unmodelled layer child kept verbatim: tag name, attributes, raw inner markup.

Colors are stored as Android `0xAARRGGBB` ints; the I/O layer converts to/from the on-disk
`#RRGGBBAA`. Coordinates are stored in pt (document space); the view applies a pan/zoom
transform to screen space.

This list mirrors the Kotlin types in `format/model/` — keep the two in sync when the model changes.

## Repository layout

A standard single-module Gradle (Kotlin DSL) Android project. The app code is split into small,
single-responsibility files per `AGENTS.md`'s style guide.

**The `MainActivity*.kt` family.** An activity can't be split into classes — the framework
constructs exactly one — so it is split into four files instead, one class plus three files of
`internal` extension functions on it. `MainActivity.kt` keeps only what has to live on the class:
the pane/tab/audio fields, the `registerForActivityResult` launchers (which must be registered
before `onCreate`), `onCreate` and the Compose entry point, the intent handovers from other apps,
`inBackground`, and the lifecycle hooks. Each of the other three owns one half-independent concern —
`MainActivityDocuments.kt` (I/O), `MainActivityTabs.kt` (sessions), `MainActivityAudio.kt` (audio) —
and reads as an ordinary module of small functions.

```
settings.gradle.kts, build.gradle.kts, gradle.properties   # Gradle config
gradle/libs.versions.toml                                  # version catalog (all deps/plugins)
gradlew, gradle/wrapper/                                    # Gradle wrapper (pinned 8.9)
Dockerfile, compose.yaml, .dockerignore                    # containerized build image + service
scripts/build.sh                                           # docker/podman build entry point

app/
  build.gradle.kts                                         # module config (SDK levels, Compose, deps)
  src/main/AndroidManifest.xml
  src/main/res/                                            # strings, Material 3 theme (light & dark splash),
                                                           #   adaptive icon & multi-density mipmaps (see "Branding")
  src/main/java/com/mobixournal/
    MainActivity.kt          # hosts the editor; SAF launchers, intent handovers, panes, lifecycle
    MainActivityDocuments.kt # its document I/O half: open/load, PDF & image adoption, save/export
    MainActivityTabs.kt      # its tab/session half: tab strip state, show/switch/close, restore, split view
    MainActivityAudio.kt     # its audio half: canvas wiring, record/playback state, sidecar pull/push
    format/                  # THE CORE — lossless .xopp read/write (pure Kotlin, no device deps)
      model/                 # Document, Page, Layer, Background, Element/Stroke/Text/Image/TexImage
      xml/                   # XmlPullReader, XmlWriter — the dependency-free XML layer
                             #   malformed entities decode to raw text; truncated input throws
                             #   XmlPullReader.TruncatedXmlException instead of hanging/crashing
      FontDescription.kt     # Pango-style font description <-> family + bold/italic (pure)
      XoppColor.kt           # #RRGGBBAA <-> ARGB int, named colours
      XoppReader.kt          # XML -> Document
      XoppWriter.kt          # Document -> XML
      Xopp.kt                  # gzip open/save + parse/serialize entry points
      XoppZip.kt             # ZIP-package open/save (PDF embedded); see the mimetype caveat
      SaveFormat.kt          # ORIGINAL (gzip) vs ZIPPED (single-file) — the sticky save choice
      FileKind.kt            # content sniffing for open: ZIP / GZIP / PDF / XML / TEXT / IMAGE / UNKNOWN
    io/                      # storage access that isn't format work
      UriStaging.kt          # stage document bytes to/from a content:// URI (slow remote shares)
      ScratchDir.kt          # unique-per-call file names (staging and both stores), so overlapping writes can't collide
      StoreFiles.kt          # the sweep both stores share: drop unreferenced files, then trim oldest-first to a byte budget
      IncomingDocument.kt    # picks the document URI out of an incoming view/edit intent (pure, testable)
      SaveTarget.kt          # the .xopp suffix and the application/x-xopp type we write, and the names to suggest for Save and PDF export
      PdfStore.kt            # one background-PDF file per open document; never rewritten, content-cache index, byte-budget eviction
      ImageStore.kt          # one never-rewritten copy per pixmap background reference; same liveness sweep and byte budget
      ImageStore.kt          # never-rewritten copy of every pixmap background picture; same sweep and byte budget
      TextImport.kt          # text file -> generated background PDF, cached by content hash
      PdfReference.kt        # how a .xopp names its background PDF: relative <-> absolute paths and SAF document ids
      DocumentIo.kt          # document I/O policy: staging + PDF stores + read/encode/merge
    panes/                   # split view: one or two editing panes, each with its own tabs
      EditorPane.kt          # one pane: canvas + tab session + save format + its own TabStore
      MirrorSync.kt          # keeps the several views of one document in step (mirrored tabs share the document)
    tabs/                    # several documents open at once, cached across app restarts
      OpenTab.kt             # one open document: id, title, source URI, save format, PDF, page
      TabManager.kt          # the tab list + selection rules (pure; no Android, no canvas)
      TabIndex.kt            # the session index text format (pure encode/decode)
      TabStore.kt            # filesDir/tabs: index + one .xopp snapshot per tab
      DocColors.kt           # the dot colour that marks two tabs as views of the same document (pure)
    render/
      DrawingSurfaceView.kt  # low-latency stylus canvas (MotionEvent pressure): the state block, the
                             # document/zoom/page/layer facades and the View overrides; the gesture and
                             # editing surfaces live in the DrawingSurface*.kt extension files below
      DrawingSurfaceConstants.kt # DrawingSurfaceDefaults: the canvas's shared tuning constants; blankDocument/blankPage
      DrawingSurfacePaint.kt # the surface's render loop, page compositing and chrome overlays (extensions)
      DrawingSurfaceInput.kt # the surface's touch/hover state machine: pointer routing, scroll and gesture end (extensions)
      DrawingSurfaceStrokes.kt # the surface's ink capture: stroke, spline, erase and place gestures (extensions)
      DrawingSurfaceHoldSnap.kt # hold-to-snap: the stillness timer that rewrites a stroke in progress as geometry (extensions)
      DrawingSurfacePenDebug.kt # the surface's pen-diagnostics trace hooks: raw pointer/key events into PenInputLog
      PenInputLog.kt         # the pen-diagnostics log and its Android-free line formatting (pure)
      DrawingSurfaceSelection.kt # the surface's selection: rubber-band start, PDF-text selection, and the
                             # delete/restyle/copy/cut/paste/duplicate edits (extensions)
      InkCache.kt            # off-screen page-ink bitmaps in zoom buckets, so panning blits instead of re-drawing
      StrokeSmoother.kt      # streaming jitter filter for freehand position and pressure (pure)
      CanvasChrome.kt        # the canvas's non-document brushes: selection, band, guide, overview, hover, palette
      ViewportState.kt       # scroll offsets, zoom, and their clamps (pure, tested)
      MomentumDriver.kt      # the fling loop: velocity tracking, release seed, per-frame glide
      PageOverview.kt        # the overview grid's view state: edit mode, selection, clipboard, lift
      PageCommands.kt        # the page/layer edit commands and the two undoable commit pipelines
      SelectionGestureController.kt # the marquee/lasso pick and the move/resize/rotate drags
      VerticalSpaceDrag.kt   # the vertical-space tool's live grab-line drag
      GuideDrag.kt           # the setsquare/compass/protractor pose and the finger that moves it
      TextEditController.kt  # placing/editing text boxes, images and LaTeX images from a tap
      ElementEdits.kt        # the document edits behind those placements (pure, tested)
      PageStacker.kt         # lays pages out in rows of N columns, fit to column (pure geometry)
      BackgroundGrid.kt      # ruling line/dot offsets + the pt spacings themselves (pure geometry)
      BackgroundRuling.kt   # `<background config=…>`: parse/edit/serialize desktop's ruling params (pure)
      PageTemplates.kt       # stationery presets: paper style+config per preset, Cornell rules as strokes (pure)
      Snapping.kt            # shape endpoints -> the ruling; rotation -> 15-degree steps (pure)
      DrawingGuide.kt        # setsquare/compass/protractor overlay geometry: project drawn point onto edge or ray (pure)
      ProtractorRenderer.kt  # renders graduated protractor face, ticks, and angle labels
      BackgroundRenderer.kt  # paints a page background (plain/lined/ruled/graph/dotted/isometric at the page's own spacing, or a PDF page image)
      PageRegionRenderer.kt  # synchronous flattened rectangular page-region copies
      StrokePainter.kt       # paints a stroke's pressure polyline (shared by screen + PDF export)
      PageRenderer.kt        # draws a page's layers/elements at a scale/offset (shared)
      ElementRenderer.kt     # draws text boxes, images, and LaTeX images (real math)
      LatexParser.kt         # LaTeX source -> node tree (pure, no Android deps)
      LatexRenderer.kt       # draws a parsed LaTeX tree to a Canvas (fractions, scripts, roots)
       PdfTileGeometry.kt     # tile grid math for high-zoom PDF rasterisation (pure geometry)
       PdfRasterSource.kt     # the PdfRenderer lifecycle: open/close/reopen on file change (serialised)
       PdfPageCache.kt        # rasterises an imported PDF's pages to bitmaps; orchestrates geometry + source
       BitmapBudget.kt        # the one memory bound every bitmap cache allocates through
       BitmapLruCache.kt      # the LRU bitmap-cache core PdfPageCache and ImageBackgroundCache share
      ImageImport.kt         # builds a one-page Document with an image as its pixmap background
      ImageBackgroundCache.kt # decodes+scales pixmap background pictures (LRU, off the frame)
      PdfBackgroundDomain.kt # the `attach` domain: the PDF travels bundled beside the .xopp
      PixmapBackgroundDomain.kt # re-points pixmap backgrounds at bundled ZIP entries for attach saves
      ImportPdfMode.kt       # how an imported PDF joins the open document (replace vs append-and-merge)
      PdfImport.kt           # builds a Document of pdf-background pages from a PdfPageCache
      PdfMerger.kt           # joins two PDFs end-to-end (PDFBox) so Append has one background PDF
      PdfText.kt             # positioned word model + grouping + range selection (pure, tested)
      PdfTextExtractor.kt    # pulls a PDF's positioned text layer via PDFBox PDFTextStripper
      PdfTextIndexCache.kt   # one extracted text layer per PDF file, shared across mirrored views
      PdfExporter.kt         # flattens a Document to a PDF (PDFBox; preserves source vector pages)
      PageSvgWriter.kt       # one page -> a standalone SVG document (vector strokes, shapes, text, ruling)
      PdfVectorPainter.kt    # draws a page's strokes/text/images as vector overlay onto a PDFBox stream
      PdfBackgroundPainter.kt # draws a fresh (non-PDF) page's background ruling as PDFBox vectors
      PdfPageTransform.kt    # maps .xopp top-left points into PDF bottom-left user space (pure)
      PdfOverlayMatrix.kt    # overlay cm-matrix that aligns annotations on /Rotate 90/180/270 pages (pure)
      TextBlock.kt           # text line-split + baseline geometry (pure)
      TextPaginator.kt       # text-import word-wrap + A4 pagination, injected measurement (pure)
      TextWrapping.kt        # tab expansion + mid-word hard break shared by both wrappers (pure)
      PdfFonts.kt            # embeds the bundled Unicode fonts (DejaVu) into a PDDocument, cached per doc
      TextPdfGenerator.kt    # authors the text-import PDF: selectable embedded text, injected font loader
      TextFlavor.kt          # plain vs markdown typesetting flavour + its PdfStore cache prefix
      MarkdownPdfWriter.kt   # draws laid-out markdown pages: a face per run style, rules as filled rects
      markdown/
        MarkdownBlock.kt     # the block tree a markdown import lays out from (pure data model)
        MarkdownLine.kt      # line-level "what does this line start?" recognisers (pure)
        MarkdownParser.kt    # markdown source -> block tree, line-based recursive descent (pure)
        StyledRun.kt         # a stretch of text with one style combination (bold/italic/code)
        MarkdownInlineParser.kt # raw inline source -> styled runs (pure); scanner + emphasis pass
        InlineScanner.kt     # escapes, code spans and link labels; emits unresolved delimiter runs
        InlineEmphasis.kt    # pairs the delimiter runs into bold/italic (delimiter-stack walk)
        StyledWrapper.kt     # styled runs -> lines of positioned fragments, per-style metrics (pure)
        MarkdownStyle.kt     # every markdown size in one place: page geometry, heading type, gaps, indents
        MarkdownLayoutItem.kt # the drawable pieces of a page (line / rule / space) + placed items and pages
        MarkdownComposer.kt  # block tree -> flat groups of wrapped lines, rules and collapsed gaps (pure)
        MarkdownLayout.kt    # composed groups -> pages: block-aware page breaking, no orphaned headings
      GlyphSanitizer.kt      # maps codepoints a font can't encode onto a substitution glyph (pure)
      StrokeHitTester.kt     # whole-stroke eraser point-to-stroke hit geometry (pure)
      StrokeEraser.kt        # partial eraser: split a stroke into surviving pieces (pure)
      PageEraser.kt          # eraser applied to a page: mode, tip size, hidden-layer skip (pure)
      ShapeBuilder.kt        # line/arrow(s)/rect/ellipse/axis/table/circuit drag -> stroke vertex list (pure)
      TriangleKind.kt        # equilateral/right/isosceles/scalene variants and angle model (pure)
      TrapezoidKind.kt       # isosceles/right/scalene variants and base-angle model (pure)
      CircuitShapes.kt       # passive & active circuits, gates, switching and dimensioning (resistor, capacitor, inductor, ground, switches, junction, transformer, diode, LED, zener, op-amp, BJT NPN/PNP, DC/current sources, AND, OR, NOT, NAND, NOR, XOR, XNOR, buffer, dimension arrow) (pure)
      ShapeRecognizer.kt     # desktop Xournal++'s recognizer ported: polygon fit -> triangle/rectangle/line (pure)
      Inertia.kt             # arc-length moments + the straightness/roundness `det` the fits threshold on (pure)
      RecoSegment.kt         # one fitted straight piece: centre, angle, extent, edge intersections (pure)
      CircleRecognizer.kt    # inertia roundness + radial-residual score -> a rebuilt circle (pure)
      DocumentSearch.kt      # case-insensitive, diacritic-folding, candidate & fuzzy search across typed text, PDF, and ink (pure)
      HandwritingIndex.kt    # per-page index of recognized handwritten words, multi-candidates, and bounding boxes (pure)
      HandwritingRecognizer.kt # stroke clustering into lines/words + neural & fallback offline recognition coordinator
      MlKitInkEngine.kt      # on-device neural digital ink recognition via Google ML Kit with automatic model download
      FallbackInkEngine.kt   # pure-Kotlin offline recognizer with multi-hypothesis character lattice and ligature splitting (pure)
      HandwritingTemplate.kt # normalized canonical templates for Latin alphanumeric characters (pure)
      SplineBuilder.kt       # spline control points -> cubic-Bezier stroke vertex list (pure)
      LayerOps.kt            # add/delete/rename/reorder/merge-down/move-selection layer edits (pure)
      ElementBounds.kt       # pt bounding box of any element + a Bounds value type (pure)
      Selection.kt           # ElementRef + SelectionTester: rect/tap picking, selection bounds (pure)
      SelectionOps.kt        # translate / scale / rotate / restyle / align / distribute selected elements (pure)
      VerticalSpaceOps.kt    # insert / remove vertical space on a page, shifting what's below, and
                             #   snapping the gap to the ruling when snapping is on (pure)
      InputClassifier.kt     # pointer kind + button + active tool + settings -> gesture intent (pure)
      PressureCurve.kt       # pressure -> width multiplier + sensitivity presets (pure)
      Fling.kt               # decelerating two-axis momentum-scroll kinematics (pure)
      VelocityEstimator.kt   # pan release-velocity from a trailing sample window (pure)
      EditHistory.kt         # generic undo/redo over document snapshots (pure)
      PageOps.kt             # insert / copy / move / delete pages in a page list (pure)
      PageThumbnail.kt       # rasterises one page into a small preview bitmap for the tab overview
    audio/                   # audio-annotated strokes: record, replay, sidecar transfer
      AudioAnnotation.kt     # AudioRef <-> a stroke's fn/ts attrs; document sidecar set (pure)
      WavWriter.kt           # streaming 16-bit PCM RIFF/WAVE writer, header patched on close (pure)
      AudioRecorder.kt       # AudioRecord capture thread -> WavWriter; byte-accurate elapsed clock
      AudioPlayer.kt         # MediaPlayer wrapper: play one clip from an offset
      AudioStore.kt          # app-private recordings dir + SAF tree import/export of sidecars
      AudioSession.kt        # the editor's single audio facade (record / stamp / play / sync)
    ui/                      # Compose Material 3
      EditorScreen.kt        # the editor's assembly: scaffold + body layout (rail edge, split panes)
      EditorUiState.kt       # the screen's remembered chrome state (pen, open dialogs, panes) in one holder + switchToolTo, the one tool-switch rule
      EditorRegions.kt       # the screen's regions: top bar (floating in modern UI, optional Secondary Toolbar tools row), ☰ menu, Main Toolbar wiring, one pane's canvas
      EditorBackHandler.kt   # back peels off one transient editor state at a time before leaving the app
      PaneState.kt           # per-pane canvas state the chrome mirrors (zoom, page, layer, undo) + the pane count
      SplitLayout.kt         # two panes side by side with a draggable bar down the middle
      EditorOverlays.kt      # what layers over the canvas: selection bars + author/save/import dialogs
      PenDiagnosticsPanel.kt # the on-canvas pen-diagnostics log: the raw stylus stream, with Copy/Clear
      SideToolbar.kt         # Main Toolbar (rail): the shell + tool-group slots; each pop-up is a Toolbar*.kt below
      EditorTool.kt          # the editor's tool modes + their labels/icons (pure)
      ToolbarColorPopup.kt   # rail slot: the compact colour + tip-size + line-style drop-down
      ToolbarSizePopup.kt    # the three pen-width slots as one compact bar + their long-press resize dialog
      ToolbarStylePopup.kt   # shared line-style chips, and the shape-recognition toggle
      ToolGlyphs.kt          # custom rail glyphs (hollow rectangle, rhombus, trapezoid, square); Material has no outline variants
      CircuitGlyphs.kt       # custom vector glyphs for circuit components and logic gates
      ToolbarViewPopups.kt   # rail slots: zoom, page background, drawing guides, audio
      ToolbarPagesPopup.kt   # rail slot: page navigation/clipboard, overview grid controls, page-size dialog
      ToolbarLayersPopup.kt  # rail slot: the layer manager list + rename dialog
      TopBarItems.kt         # Secondary Toolbar buttons definition (figures & tools), default order, and codecs
      TabStrip.kt            # the horizontal strip of open-tab titles: select, reorder, close, long-press menu
      TabOverviewPopup.kt    # a grid of every open tab shown as the page it was left on
      ColorPalette.kt        # the one colour picker (swatches + custom slot) all three sites use
      ColorPicker.kt         # the arbitrary-colour HSV/hex dialog behind the palette's custom slot
      LatexPalette.kt        # quick-insert STEM symbol palette (calculus, greek, physics, operators) and LaTeX dialog
      ToolPreset.kt          # a named snapshot of the whole tool config (tool/colour/width/style); capture + apply
      ToolPresetList.kt      # save/overwrite, reorder and delete on the saved preset list (pure)
      ToolPresetCodec.kt     # the preset list's one-line SharedPreferences form; forgiving decode (pure)
      ToolGroups.kt          # the rail's tool groups + their persisted per-slot selections (pure)
      RailItems.kt           # the rail's button positions + their persisted order/hidden set (pure)
      ScrollThumb.kt         # right-edge PDF-style scroll thumb: drag to page fast, faint-when-idle, page bubble
      PageCounter.kt         # always-visible "page X of Y" badge (its corner is a setting) + the zoom badge above it (tap = reset to 100%)
      SettingsScreen.kt      # settings: side menu + sections on a wide screen, pushed index on a narrow one
      SettingsSidebar.kt     # the wide screen's permanent side menu (search field + the four areas)
      SettingsSearch.kt      # which sections answer a query, by title/summary/alias keywords (pure)
      SettingsWidgets.kt     # the shared settings controls (switches, option groups, key fields)
      ShortcutsSection.kt    # the Shortcuts page: the two toggles + one key per tool and per pen colour
      AppearanceSection.kt   # the Appearance page: theme mode, dynamic theme, modern interface switch
      BackupSection.kt       # the Backup page: JSON export/import/reset via Storage Access Framework; an import is refused unless it is a compatible backup
      AboutSection.kt        # the About page and the links it sends people to
      AppSettings.kt         # AppSettings model + SettingsStore (SharedPreferences persistence)
      AppSettingsBackup.kt   # JSON serializer/parser for AppSettings: versioned, tolerant reader + a compatibility gate (BackupCheck) for imports (pure)
      theme/                 # XoppTheme (Material You), Color
  src/test/java/com/mobixournal/format/                         # JVM unit tests for the format layer
  src/test/java/com/mobixournal/render/                         # JVM unit tests for layout/grid/LaTeX geometry
  src/test/java/com/mobixournal/audio/                          # JVM unit tests for fn/ts mapping + WAV framing
  src/androidTest/java/com/mobixournal/                         # on-device smoke test (load/draw/save/reopen)
  src/test/java/com/mobixournal/ui/                             # JVM unit tests for the chrome's pure logic (incl. the palette)
```

The **`format/` package is the heart** and is deliberately free of Android dependencies so the
round-trip logic is fully unit-testable on the JVM (see `app/src/test/`). `render/` and `ui/`
are the Android-facing shell around it.

### Branding and identity

This is an **unofficial** project. Its own name — used in this doc, the README, the About page,
`settings.gradle.kts`, and the `creator` attribute the writer stamps into saved files
(`DEFAULT_CREATOR`, `format/model/Document.kt`) — is **MobiXournal**. Desktop
Xournal++ writes `Xournal++ <version>` into `creator`, so the two writers stay tellable apart. The
launcher/task-switcher label (`@string/app_name`) carries the same name, so this app is never
mistaken for the official Xournal++ app.

**Relationship to Xournal++.** The app is a derivative work: it reads and writes desktop Xournal++'s
`.xopp` format, and its palette, shape recogniser and highlighter behaviour are derived from or
matched to upstream. Upstream is GPL-2.0-or-later, so this app keeps that licence (see `LICENSE`) and
credits the Xournal++ authors on the About page — but it is **not affiliated with, endorsed by, or
maintained by** them.

**Relationship to NeXopp.** This codebase is a continuation of **NeXopp**
([github.com/bamonroe/NeXopp](https://github.com/bamonroe/NeXopp)), Brian Monroe's Android `.xopp`
editor — the `format/` read/write layer and XML layer, the Compose editor, the drawing surface and
input pipeline, the tabs/panes/audio plumbing, the SAF open/save paths, the build tooling and the
doc structure all come from there, renamed from NeXopp to MobiXournal.
NeXopp is GPL-2.0-or-later too, so the whole stays under that licence with its author's copyright
intact; `NOTICE` records the inheritance and what changed. Credit is also shown on the About page.

**What actually differs from NeXopp** (measured, not remembered: upstream head compared file by
file after normalising the package name — 287 shared source files, 21 added, 33 removed, 283 of
the shared ones touched; `NOTICE` carries the full list). The headline changes:

- the **radial palette** was deleted outright — 33 files, its renderer/tap/haptics/action/catalog
  code and its settings section all gone;
- its **settings section `Palette` became `Shortcuts`**: one configurable key per tool and per pen
  colour, plus the pen/eraser and hand/pan toggles (`AppSettings.toolShortcutKeys` /
  `colorShortcutKeys`) — NeXopp had no keyboard shortcuts at all;
- the **rail was regrouped** from eight slots to twelve — pen and highlighter separated, Line and
  Rectangle given their own slots, the other figures sharing a Shapes slot, text authoring split
  out of Insert (see `ToolGroups.kt`);
- the **shape recogniser is now a port of desktop Xournal++'s** (`Inertia.kt`, `RecoSegment.kt`,
  `CircleRecognizer.kt`, rewritten `ShapeRecognizer.kt`), replacing a hand-rolled classifier;
- **colours were re-derived from upstream**: desktop Xournal++'s palette hexes, an HSV/hex custom
  slot, pinned by `PaletteColorsTest` (NeXopp shipped six hand-picked swatches); the shipping
  default is now the eight swatches black/red/green/blue/orange/yellow/magenta/white, and the palette is
  user-editable under Settings → Colors;
- the **highlighter** now blends with a separable `BlendMode.MULTIPLY` at 0.47 and snaps to
  desktop's 2.83 / 8.50 / 19.84 pt tips — NeXopp had neither;
- **pen input gained** a vendor-key barrel-button gate (Honor Choice Pencil's key 755),
  single-click collapsing for double-clicking firmware, a live **Pen diagnostics** log, and a
  whole-stroke eraser default;
- **new affordances**: zoom badge, the rail chevron, a default-page-size setting, an Export dialog;
- **a real `.xopp` type** — `application/x-xopp` with intent filters, where NeXopp saved
  `application/octet-stream`;
- **new identity and artwork**: name, `applicationId`/namespace `com.mobixournal`,
  `creator="MobiXournal"` (NeXopp wrote `"NeXopp"`), this project's own sheet-and-pencil icon with
  a monochrome layer, `AGENTS.md` instead of `CLAUDE.md`, and NeXopp/Xournal++ credits in place of
  the Patreon link;
- **the earlier update round that led to this continuation** is carried forward too: the
  **highlighter opens on its own defaults** — the palette's yellow at the widest tip — with a
  colour and width **independent of the pen's** (`DEFAULT_HIGHLIGHTER_COLOR` /
  `DEFAULT_HIGHLIGHTER_WIDTH`, `EditorUiState.highlighterColor` / `highlighterWidth`); large
  Xournal++-authored `.xopp` files no longer lag while scrolling or writing, since a pressure
  stroke is painted as a **single filled outline** built in one pass rather than a `drawLine` per
  segment — and, since 2026-10-03, that outline keeps the stroke's **real per-point width** instead
  of one mean width, so the live taper matches desktop Xournal++ (`StrokePainter.drawPressureLine`);
  a stroke whose points share one width — a shape, a spline — is stroked at that width instead,
  which keeps its sides even and its corners clean (`StrokePainter.drawUniformLine`); and the
  **keyboard shortcuts** (pen/eraser and hand/pan
  toggles, one key per tool and per pen colour) are read from the native `dispatchKeyEvent`
  instead of a Compose `onKeyEvent`, so any character matches, upper or lower case.

The **app icon is this project's own artwork**: a ruled sheet of paper carrying a stylus `X` and a
pencil laid across it, drawn from scratch in `ic_launcher_foreground.xml` on the
`@color/ic_launcher_background` white field, with a matching `ic_launcher_monochrome.xml` for
Android 13+ themed icons. Every element sits inside the adaptive icon's 128/192 safe zone, so no
launcher mask crops it. No upstream logo is used, so the icon belongs to this app while the credit
to Xournal++ stays in the About page and README.

The Kotlin package, the namespace and **`applicationId` are all `com.mobixournal`** — the install
identity of this unofficial app, kept distinct from upstream and from any other build.

## Colour palette (`ColorPalette.kt`, `ColorPicker.kt`)

The one colour picker all three sites share (the pen pop-up, the text-box dialog, the selection
recolour menu), plus the shortcut tables that jump straight to a colour or a tool (see
`ShortcutsSection.kt` and the `toolShortcutKeys` / `colorShortcutKeys` maps in `AppSettings`):

- **`ColorPaletteState`** — the shared colour state: the editable custom slot, persisted in
  `AppSettings`. One instance is threaded to every picker
  (toolbar palette, text-box dialog, selection recolour menu), so a colour redefined anywhere reads
  the *same* custom slot. There is no recently-used list: the palette itself is the list.
  `custom` is **nullable**: the slot is empty on a fresh install (`AppSettings.customColor` is null)
  rather than pre-filled with a colour nobody chose, and an empty slot is stored as an *absent* pref
  key and an explicit `null` in the JSON backup, so "empty" and "black" stay distinguishable.
  `addColor` is the pop-up's append (opaque, de-duplicated, capped at `MAX_PEN_COLORS`).
- **`ColorPaletteRows`** — the one colour picker composable: the user's palette (`penColors`,
  seeded with the eight hexes of `PEN_COLORS` and predefined English names) then the
  editable custom slot (marked with a pencil, long-press to redefine; an empty slot *opens* the
  editor on a tap, since it holds no colour to pick).
  A tap reports the colour through `onPick`; the host decides what it means (set the pen,
  restyle the selection, colour the text). `compact` (the toolbar pop-up) lays the swatches in a
  **single horizontally scrolling row** so the menu's height is fixed however many colours the
  palette holds — a wrapping grid would grow the pop-up with every swatch added — and `onAdd`
  appends the trailing add-colour swatch.
- **Colour parity with desktop.** Beyond the palette, every colour that names a *document* or *paper*
  appearance is taken from desktop Xournal++: the named `.xopp` colours (`format/XoppColor.kt`,
  upstream's `PREDEFINED_COLORS` → `Colors::xopp_royalblue` / `xopp_deepskyblue` / `lime` /
  `xopp_darkorange` / …), the ruling hues (`BackgroundGrid.LINED_RGB` = `xopp_dodgerblue`,
  `GRAPH_RGB`/`DOT_RGB` = `xopp_silver`), and the highlighter tip sizes
  (`DrawingSurfaceDefaults.HIGHLIGHTER_SIZES_PT`). Colours that have **no** desktop counterpart — the
  Material theme chrome, the radial-palette scrim, the tab-duplicate dots, the app-only red ruled
  margin — are deliberately left alone; `DesktopColorParityTest` pins the ones that do.
- **`CustomColorPickerDialog`** — the HSV/hex dialog behind the custom slot: a
  saturation/value square over a hue slider, plus a two-way `#RRGGBB` hex field and a live
  preview. Always opaque; the parent persists the result. Its optional `palette` argument adds an
  **Existing colours** row — the app's own swatches, scrolled horizontally — so a new colour can
  start from one that already exists instead of from a hex the user would have to know; tapping a
  swatch loads it into the editor and nothing is committed until **Set**.

**What the unit tests cover** (this section is the one authoritative inventory — `README.md`
and `docs/tools.md` link here rather than restating it). The `format/` tests exercise the
`.xopp` round-trip: the colour codec, every element type, XML escaping, model reserialization,
a gzip round-trip, the PDF-background on-disk shape, the fixture-driven `FormatDriftTest`
asserting schema coverage, and `XmlEqualityRoundTripTest`, which checks that the XML we emit
still matches the desktop-written source byte-for-byte once normalized. The `render/` tests
cover the pure geometry — page layout, gridlines, page ops, eraser hit-testing, text layout,
LaTeX geometry and undo/redo history — and the `audio/` tests cover fn/ts mapping and WAV
framing. All of it runs on the JVM with no device attached.

**The `udiff.xopp` self-skip rule.** `RealFileRoundTripTest` round-trips a real
desktop-generated `udiff.xopp` end to end, read from the **repo root** (the builder mounts the
project's parent dir, so the file resolves inside the container). The sample is not checked in,
so the test **self-skips when it is absent** — the suite is green with or without it, and
dropping a fresh `udiff.xopp` at the repo root is all it takes to turn the extra coverage on.

**Rendering (`render/`).** Every repaint is **paced to the display**: `DrawingSurfaceView.render()`
never paints inline, it flags a `Choreographer` frame callback that runs the actual `paint()` once
per vsync, collapsing everything requested in between. This matters because a digitiser reports far
faster than the panel refreshes (240 Hz against 120 Hz on the large tablets): painting straight from
the input handler posted several buffers per vsync, and the compositor latching whichever was newest
made the shown position walk back and forth between samples instead of advancing — the flicker seen
when zoomed in on a big screen, where a paint is slow enough to keep several buffers in flight. The
fling loop is the one exception: it is already inside a frame dispatch, so it calls `paint()` directly
rather than deferring a frame — and because it does, `render()` is a **no-op while a fling is in
flight** (and starting a fling cancels any already-queued paint). Otherwise anything that asks for a
repaint mid-glide — most often a PDF tile landing and calling back, which is constant when zoomed in —
would post a *second* buffer for the same vsync and reintroduce the very buffer-walk flicker the
pacing exists to prevent. Each frame locks the surface with **`lockHardwareCanvas()`**, not
`lockCanvas()` (falling back to the software canvas only if the GPU one is unavailable): the software
canvas rasterises and blends every window pixel on the CPU, so frame cost scaled with window *area* —
full-screen page-flicking on a large tablet crawled while the identical gesture in a half-size
split-screen window stayed smooth. On the GPU canvas fill rate is effectively free and the cached page
bitmaps are plain textured blits. The `DrawingSurfaceView` holds the whole [Document] and renders every
page in a single vertical stack, each page scaled to fit the view width via `PageStacker` and
drawn with its background ruling (`BackgroundRenderer`, using the pure `BackgroundGrid` offsets and
colours — lined rules in desktop Xournal++'s `xopp_dodgerblue`, graph/dotted in its `xopp_silver`),
resolved per page through `BackgroundRuling` so a sheet rules at **its own spacing, margin and line
width** rather than at a constant (see *Ruling parameters and stationery* below)
plus all of its layers in z-order. The geometry (page placement, gridlines) is factored into
`PageStacker`/`BackgroundGrid` precisely so it's unit-testable off-device. **One finger draws**
(a new stroke lands on the top layer of the page under the touch) — or **erases** when the
Eraser tool is active, deleting every stroke the eraser disc touches (hit geometry in the pure,
tested `StrokeHitTester`), or **pans** when the Hand tool is active; **two fingers pan** in any
tool. A **zoom** factor multiplies the fit-to-width scale (`PageStacker` takes it as a parameter);
when a page is wider than the view the same pan gesture scrolls horizontally, and narrower pages
are centred in the content band (`PageBox.leftPx`). A **column count** (`PageStacker.stack(columns=)`,
driven by `DrawingSurfaceView.setColumns` and persisted as `AppSettings.pageColumns`) turns that stack
into the **page overview**: pages are chunked into rows of N, each fit to `viewWidth/N` and the row
centred, so 1 is the plain stack and 2-4 a grid of page thumbnails. Because a row now holds several
pages, hit-testing takes both axes — `StackedLayout.pageAt(x, y)` picks the page under a touch and
`nearestPage(x, y)` resolves a probe that lands in a gap (e.g. the viewport centre). Those two hit-tests
also drive the overview's **edit mode**. The grid has two modes, held in
`PageOverview.editMode` (view-only, default off, toggled from the Pages menu via
`setPagesEditMode`): in **view mode** the grid is display/navigation only — a confirmed Hand-tap
`goToPage`s the page it hit and neither selection nor reordering is armed; **edit mode** enables the
page tooling below, and leaving it cancels any lift and clears the selection. In edit mode those
hit-tests drive **drag-to-reorder**: a finger long-press (`ViewConfiguration`'s timeout,
disarmed by touch-slop travel or a second pointer, and never armed for a stylus or at one column)
lifts the page under it, the drag tracks a drop slot with `nearestPage`, and the release commits one
undoable `PageOps.move(pages, from, to)` through `editPages`. The same `pageAt(x, y)` hit-test drives
**multi-select delete**: at more than one column a confirmed Hand-tool tap (the double-tap tracker's
tap, rather than a second gesture) toggles the page under it in `PageOverview.selected`,
and `deleteSelectedPages()` commits one `PageOps.removeAll(pages, indices)` through `editPages`.
`removeAll` refuses a selection covering every page, so the document is never emptied. The same
selection feeds **copy/paste**: `copySelectedPages()` stashes `PageOps.copyOf(pages, indices)` (the
picked pages in ascending order) in the view-only `PageOverview.clipboard`, and `pasteCopiedPages()` commits
one `PageOps.insertAfter(pages, after, clipboard)` through `editPages`, inserting after the
highest-numbered selected page (or `currentPageIndex()` when nothing is picked). Pages are immutable,
so a "copy" is a shared reference — the duplicate carries the same strokes, layers, size and
background object, and `XoppWriter` serialises each page position independently, so the paste
round-trips with no deep copy needed. The selection
is view-only (never written) and is cleared by `editPages` and by dropping back to one column, since
both invalidate page indices; the clipboard is not cleared, so one copy can be pasted repeatedly. Page order *is* list order in
`Document.pages` — `XoppWriter` writes no index — so the reorder round-trips by construction. Zoom keeps the viewport-centre point roughly
fixed, and is clamped to 25%–1000% (`DrawingSurfaceDefaults.MIN_ZOOM`/`MAX_ZOOM`). Strokes and other
elements are re-rendered vectorially at the zoomed scale, so they stay sharp at any level; PDF
backgrounds are re-rasterised per zoomed width up to `BitmapLruCache.MAX_RASTER_WIDTH` (4096 px) and
never above `BitmapLruCache.PAGE_SHARE` of the cache budget for one bitmap (so the visible pages
can't evict one another and flash blank), beyond which the whole-page bitmap is upscaled to bound
memory — asynchronously, so a zoom step shows the previous resolution stretched and sharpens a
moment later rather than stalling the frame. A page with *nothing* cached is the one exception: it
rasterises inline, since an empty background reads as a blank page. Past that whole-page ceiling
the sharpness comes from **tiles**: `PdfPageCache.requestTiles` rasterises only the visible cells of
a `PdfPageCache.TILE_PX` (512 px) grid built at the true on-screen page width, each rendered 1:1 via
a `Matrix` on `PdfRenderer.Page.render`, and `BackgroundRenderer` draws them over the upscaled page
bitmap. So PDF text stays sharp to the 1000 % zoom ceiling while cost stays proportional to the
viewport, not the page; a tile that hasn't rasterised yet simply shows the coarse layer underneath.
When the tiles on hand already cover every visible pixel of the page, `BackgroundRenderer` skips the
coarse whole-page blit entirely, so those pixels aren't rasterised twice. Four things keep the tile
path off the frame budget: `requestTiles` **memoises** its answer per page and rebuilds the list only
when the visible cell block or the cache's contents change (a pan holds the same block for many
frames); it queues the **ring of cells just outside** the viewport, so a pan meets rasterised tiles
at its leading edge rather than the coarse under-layer; a viewport spanning more cells than
`PdfPageCache.MAX_TILES_PER_FRAME` caps how many new cells are *queued* instead of dropping the whole
request, so cells already rasterised still draw sharp; and `nearest` finds a stand-in bitmap through
a per-page sorted width index rather than scanning every entry, since a pinch calls it per visible
page per frame while the cache holds hundreds of tiles. Tiles landing from the worker coalesce into
a single redraw (`DrawingSurfaceView.requestRender`) instead of one full repaint each.
Eviction **pins the visible cells**. `requestTiles` records the viewport's cell block per page (and
refreshes its LRU recency) *before* the memo short-circuit, and `put` spares those keys on its first
eviction pass; the ring is only warmed while the cache is under `PdfPageCache.PREFETCH_HEADROOM` of
budget. Without this, a zoom whose visible tiles plus ring outgrow the budget evicts the very tiles
being drawn, the next frame falls back to the upscaled whole-page bitmap and re-queues them, and the
page flickers between blurry and sharp indefinitely. `DrawingSurfaceView` calls `PdfPageCache.retain`
each frame with the on-screen pages so pins don't accumulate behind a scroll, and a second eviction
pass ignores pins entirely, so a viewport too large to cache still stays memory-bounded.
Ink is culled the same way: `DrawingSurfaceView` hands `PageRenderer.drawElements` the viewport in
page-local pt, and any element whose `ElementBounds` box misses it is never submitted (boxes are
memoised by element identity, so the cull doesn't rescan stroke points each frame). At high zoom a
page spans many screens, where almost every stroke would otherwise cost thousands of canvas calls
Skia only clips away. `PdfExporter` passes no viewport and so draws everything. Above that cull sits
`InkCache`: each visible page's ink is rasterised once into an off-screen bitmap, so a pan or fling
frame is a **blit** rather than a re-submission of every stroke. The raster is keyed by a **zoom
bucket** (widths step by `InkCache.BUCKET_RATIO`, 1.19×), so a pinch only re-rasterises when it
crosses a bucket edge and the zooms in between are a ≤19 % stretch of the bitmap it already has. An
entry is invalidated by page identity (any edit rebuilds the `Page`), by its hidden-layer set, or by
scrolling out of view (`InkCache.retain` keeps only the visible pages). The cache **declines** two
cases and the direct element path takes over: a page whose bucket would exceed its per-entry ceiling
(`BitmapLruCache.PAGE_SHARE` of the shared budget — at deep zoom a page spans many screens and its full
raster would dwarf the screen it feeds, and the viewport cull is the better tool there), and any
gesture that rewrites the page every frame — drag, resize, rotate, erase — where caching would only thrash.
`PdfPageCache` and `ImageBackgroundCache` share their LRU core: both extend **`BitmapLruCache<K>`**,
which owns the access-ordered map, the short-held cache lock, the single background worker, the
insert-and-charge path, and eviction. A subclass supplies only what differs — `produce` (rasterise or
decode, called *without* the cache lock), `index`/`unindex` (its width index behind `nearest`),
`spared` (PdfPageCache's on-screen pinned tiles), and the `onCacheChanged`/`onDiscard` hooks.
`BitmapLruCache.MAX_RASTER_WIDTH` (4096 px), `PAGE_SHARE` (a quarter of the budget per raster) and
`bucket` (64 px width buckets) live there once for all three caches.

**Ruling parameters and stationery (`BackgroundRuling`, `PageTemplates`).** A page's paper is not
just a style: desktop writes the ruling's parameters — spacing, margin, line width, bold lines — into
`<background config=…>`, and reading them is what lets a 7 mm ruled sheet or a millimetre grid look
right in both apps. `BackgroundRuling` is that attribute as an **ordered key/value map** (desktop's
own `BackgroundConfig`, split on `,` and each entry at its last `=`), so unknown parameters — the
colour keys, anything a future desktop adds — are preserved in place exactly like an element's
`extraAttrs`, while the keys we do understand are edited by key and written back with three decimals.
`BackgroundRulings` resolves the numbers a renderer needs (`r1` else the style's default from
`BackgroundGrid`, `m1`, `lw`, `bli`/`blw`), and the three consumers — `BackgroundRenderer` on screen,
`PageSvgWriter` for the SVG export, `PdfBackgroundPainter` for the PDF flatten — all go through it,
so a custom grid can't rule one way on screen and another in the export. `Snapping` resolves the same
numbers, so *Snap to grid* pulls onto the lines the user can actually see. The renderer caches the
parsed ruling against the config string (one entry), because `draw` runs every frame and a fresh parse
per frame was pure churn. Isometric paper is the same story one level up: `isograph`/`isodotted` are
desktop styles, `r1` is the triangle's side, and the mesh is the pure `BackgroundGrid.isometric`
clipped to the sheet — the two ±30° families whose diagonals are what desktop paints. (A desktop page
ruled `isodotted` is drawn as that same mesh rather than as its dotted variant: a cosmetic difference
on a style we never produce, recorded here rather than guessed at.) **Stationery** is the menu over
all of this (`PageTemplates`): millimetre paper, 5 mm graph, 7 mm ruled and 5/10 mm isometric write a
style + `config` pair — real paper the desktop renders too — while **Cornell notes**, which no
Xournal++ version has, is drawn instead: its three rules land as ordinary strokes on a layer named
after the template, so it round-trips like any ink and can be deleted by deleting the layer.

**Vector PDF pages — retired (2026-10-11).** *Decision (2026-10-10), reversed (2026-10-11).* A first
revision drew a `pdf` background's page as **vector geometry** on the drawing thread (PDFBox's
`PDFRenderer.renderPageToGraphics`, replayed every frame) behind an `AppSettings.vectorPdf` toggle,
with a `PdfVectorGuard` to send a page back to raster when its replay overran a time budget. That is
a full content-stream replay **per frame on the UI thread**, and on a born-digital
PDF it made panning, pinching and — above all — drawing and hovering visibly laggy: the frame (and the
input behind it) was spent replaying the page instead of being served. Gating the replay to frames at
rest only moved the stall to every settle. So the whole on-screen vector path was **removed**
(`PdfVectorSource`, `PdfVectorBackground`, `PdfVectorGuard`, `PdfPageClip` and the setting all went),
and a `pdf` background now always goes through the raster cache and its tiles above — exactly like any
other PDF page, and exactly the smooth, cached rendering desktop Xournal++ uses. Nothing is lost in
sharpness: the whole-page bitmap is bucketed and capped, and past that ceiling the **visible tiles are
rasterised at the true on-screen resolution**, so text and line art stay sharp however far you zoom.
`PdfVectorPainter`/`PdfBackgroundPainter` (the *export* path) still emit real vector PDF pages; only
the on-screen replay is gone.
Both bitmap caches allocate through **one** `BitmapBudget` (`BitmapBudget.shared`, sized at startup
from `ActivityManager.memoryClass`), so a PDF-backed document has a single memory bound rather than
two independent guesses. A cache `charge`s each bitmap it rasterises; when the total goes over, the
budget asks its clients to `trim` — the *other* clients first, the one that just allocated last, so
the pixels being drawn this frame survive. `InkCache.trim` gives back off-screen pages first,
`PdfPageCache.trim` its least-recently-used entries (pinned tiles last), and
`ElementRenderer.trim` its least-recently-drawn embedded images (keyed by element *identity*, since
an `ImageElement`'s `equals` compares whole byte arrays; `ElementRenderer.close` recycles them and
leaves the budget when the surface is torn down or a one-shot thumbnail is finished). Trimmed bitmaps are dropped
but never recycled: another thread's trim can hit a bitmap the drawing thread holds for the current
frame. **No lock is held across a charge, in either direction.** `BitmapBudget` never calls a client
back while holding its own lock, and `BitmapLruCache.put` releases the *cache* lock after the insert
and before charging — because a charge reaches into every other client's `trim`, which takes that
client's lock. Holding one cache's lock across it inverts the order between two caches, and any two
live caches over one budget can hit it: a fast scroll in each had the drawing thread inside cache A
waiting on B while A's rasteriser sat inside B waiting on A, hanging the main thread in `doFrame`
until the system killed the app for not responding. (The case that found it was a document mirrored
into both split panes, which then meant two `PdfPageCache`s over one PDF; the panes now share one —
see below — but the lock rule stands on its own, since a pane's PDF and image caches charge the same
budget.)
`BitmapCacheDeadlockTest` (connected suite — the accounting needs real `Bitmap.byteCount`) races two
caches over one budget and fails if they lock up.
**Add/remove page** edit the page list through the pure, tested `PageOps` (a new page is born at the
**default page size** — the settings value `MainActivity.applyDefaultPageSize` pushes into
`DrawingSurfaceDefaults.defaultPageWidth`/`defaultPageHeight`, which the blank-sheet factories every
created sheet comes from — and keeps the paper ruling, but not the background, of the page in view). Each draw, erase, add, or remove snapshots
the whole document into the pure, tested `EditHistory`, so the top-bar **undo/redo** steps one
gesture at a time (snapshots are cheap — immutable pages/layers share structure). The stack is
bounded at `EditHistory.DEFAULT_MAX_DEPTH` (200) steps, dropping the oldest once full; pan and zoom are
view-only and not recorded. Strokes are drawn by the view; text boxes, images, and LaTeX images
are drawn by `ElementRenderer` (text baseline geometry lives in the pure, tested `TextBlock`; image
bytes are decoded once and cached by element identity). A `<teximage>` carries only its LaTeX
source in the model, so it is parsed once (cached by element identity) by the pure `LatexParser`
into a node tree and drawn as **real math** by `LatexRenderer` — fractions (numerator over
denominator with a rule), super/subscripts (smaller and shifted), square roots (radical + vinculum),
and a Unicode table for Greek letters and common operators/relations; the tree is measured at a
reference size then uniformly scaled to fit the element's box. Any parse/draw failure falls back to
the raw source text, so a malformed formula can't crash a frame.

**Hold-to-snap (`DrawingSurfaceHoldSnap.kt`).** The shape recogniser is also reachable *before* lift-off:
resting the stylus on the glass for `HOLD_SNAP_MS` (500 ms) rewrites the stroke in progress as the
recognised geometry, so the user watches the snap happen. Each sample that moves the tip past
`HOLD_SNAP_SLOP_PX` (6 view px, converted to pt through the page's own `scale`) re-arms a
`postDelayed` timer; the timer firing replaces `current` with the recogniser's vertex list and marks
the stroke `holdSnapped`, so the commit that follows keeps it uniform-width like a shape tool's
output (`commitCurrent` ORs that flag into its own `snapped`). It is a **preview, not a lock** — no
state outside the stroke is touched until lift-off, so carrying on drawing simply gives the recogniser
a longer stroke to read, and a wrong guess costs nothing. The timer is cancelled by every move,
commit, cancelled gesture and stylus takeover (`abandonInProgress`/`cancelGesture`), which is why the
gesture plumbing had to know about it at all. It shares the `recognizeShapes` setting — and the pen
and highlighter tool filter — with the on-lift path, so there is one recogniser and one switch.

**Shapes, styles, partial eraser, layers.** The **line and shape tools** (Line/Arrow/Double arrow/Rectangle/Ellipse/Coordinate axis/Spline/Table) turn a
one-finger drag into an ordinary constant-width pen stroke: `ShapeBuilder` (pure, tested) converts the
drag's start/end into a vertex list, previewed live and committed as one undoable stroke, so shapes
round-trip like any stroke. The **triangle tool** supports four geometric variants (`TriangleKind`):
equilateral (factory default, preserved 60° angles), right-angled, isosceles (centered apex), and scalene
(with customizable interior angles A, B, and C constrained to sum to 180° and previewed on a live canvas).
The **trapezoid tool** (right after the rhombus) is the same pattern with three variants
(`TrapezoidKind`): isosceles (factory default — the shorter base centred over the longer one at half
its width), right-angled (the left leg vertical, so two angles are square), and scalene, whose two base
angles are freely customizable — a trapezoid has one pair of parallel sides, so its two base angles fix
its shape, and unequal angles are exactly what makes all four sides differ. `ShapeBuilder.trapezoidOutline()`
is the single layout both the tool and the settings dialog's preview call, so the preview cannot drift
from what a drag produces; the scalene figure is uniformly scaled to stay inside its drag (which
preserves the angles) and centred, the same fit the scalene triangle uses.
The **table tool** creates an \(R \times C\) grid as a single continuous polyline,
with configurable rows and columns, optional relational header row (rendering a double separator line under the first row),
support for live drag-sizing and one-tap centered viewport insertion,
and figure-style stroke (inheriting and adjusting figure width, color, and line style). The **spline tool** is the one shape whose gesture spans several touches,
so it bypasses the single-drag path: `DrawingSurfaceView` accumulates `SplineNode`s (anchor + tangent
handle — a tap sets the anchor, the drag that follows sets the handle) and `SplineBuilder` (pure,
tested) flattens the chain into one vertex list by sampling a cubic Bézier per node pair, with C¹
continuity at every node. The curve previews through the same in-progress-stroke path as everything
else — identical `SplineBuilder` output, so what previews is exactly what commits — with
`drawSplineOverlay` adding the scaffolding the flattened curve can't show: an anchor dot per node,
each node's symmetric tangent arm, and a dashed rubber band to a hovering stylus. It commits on a
double-tap, on `Enter` (routed from `MainActivity.dispatchKeyEvent`, so the canvas never takes
keyboard focus), or when the tool changes; `Backspace`/`undoLastSplineNode` drops the last node and
`Escape` discards the curve. Because a tablet has no keyboard, `onSplineChanged` reports the open
node count out to `PaneState.splineNodes`, which drives the `SplineModeBar` — the on-screen
finish / undo-point / discard bar. The **shape recogniser**
(`ShapeRecognizer`, with `Inertia`, `RecoSegment` and `CircleRecognizer`; all pure and tested) is the
reverse direction, and it is a **direct port of desktop Xournal++'s**
(`src/core/control/shaperecognizer/`): on commit, with the user's **Shape recognition** setting on
and the pen active, the stroke's *raw* samples are classified by the same algorithm and thresholds
the desktop uses, so the same scribble is judged the same way on both. Nothing is simplified first —
`StrokeSimplifier` now runs only on the strokes that weren't recognised, since thinning would hide
the dense detail the fit reads and can round a circle into facets. The method is a piecewise
least-squares **polygon fit** (`findPolygonal` recurses over the point list on `Inertia.det`, the one
arc-length-moment number that measures straightness, then `optimizePolygonal` nudges every break to
its local optimum); three segments within tolerance become a **triangle**, four a **rectangle**
(rotated onto one shared angle, which is what squares a roughly upright one to the page axes), one a
**line**, and only if no polygon fits is the stroke tried as a **circle** — inertia roundness plus a
length-weighted radial residual. Emitted geometry comes from the fitted lines' *intersections*, so
it is cleaner than anything read off the samples. Every tolerance is relative to
`RecoSegment.radius` rather than an absolute pt budget, so the same wobble passes at any size, and a
stroke under 40 pt across (upstream's `strokeRecognizerMinSize` default) is never snapped. It runs
for the **pen and the highlighter** alike — upstream gives both tools `TOOL_CAP_RECOGNIZER` — so a
straight highlighter line snaps to a line too. An
unmatched stroke returns `null`, so handwriting commits untouched. The recognised set is upstream's
— **line, triangle, rectangle, circle** — so an oval, an arrow and an open polyline are deliberately
left as drawn, unlike the hand-rolled recognizer this replaced. The one divergence from upstream's
code is `RecoSegment.calcEdgeIsect` returning `null` for parallel pieces, where the C++ divides by
zero and would write an infinite point into the `.xopp`. **Snapping** (`Snapping`, pure and tested) is
applied at the input edge rather than in the geometry builders: with **Snap to grid** on, the shape
drag's start and end points are rounded onto the page background's ruling before they reach
`ShapeBuilder`, and with **Snap rotation** on, the *swept* angle (not the absolute one) of the rotate
handle is rounded to 15° before it reaches `SelectionOps.rotate` — snapping the swept angle means a
snapped drag returns exactly to the element's original orientation. A **drawing guide** (`DrawingGuide`, pure and tested) is the same idea taken further: a setsquare
(right triangle) or compass (circle) posed in page-local pt, whose `project` pulls any point within
`GRAB_PT` onto its nearest edge and leaves anything further away untouched. `DrawingSurfaceView`
owns the live pose, paints the overlay in `paint()` (it is chrome, not ink, so it stays out of
`InkCache`), and funnels *every* drawn vertex through `guided()` — freehand samples in `point()` and
line/shape endpoints in `startStroke`/`extendStroke`, applied **after** the grid snap so the guide
wins. A finger that lands on the guide's *body* (the triangle's interior, the compass's hub) drives it on
its own pointer id, running alongside the drawing gesture rather than replacing it, which is what
lets the pen rule along a guide the other hand is holding; the edges are deliberately excluded from
the grab test so drawing against one never drags the instrument away with it. Nothing about a guide reaches the document. Which axes snap is a property of
the background style, so the pt spacings live once in `BackgroundGrid` and are shared by
`BackgroundRenderer`, `PdfBackgroundPainter` and `Snapping`. The **setsquare/compass guides**
(`DrawingGuide`, pure and tested) are a third constraint at that same input edge: the surface holds
one live pose pinned to a page, and every drawn vertex — freehand via `point()` and line/shape
endpoints alike — passes through `guided()`, which projects the point onto the guide's nearest edge
when it is within `DrawingGuide.GRAB_PT`. The guide is applied *after* grid snapping, so a placed
guide wins. It is drawn as a canvas overlay outside the ink cache (it is not page content) and is
manipulated by a finger on its own pointer id, deliberately running alongside the drawing gesture
rather than instead of it, so a hand can hold the instrument while the pen rules along it. Nothing
about a guide reaches the document — only the resulting stroke does. A **line style** (`plain`/`dash`/`dashdot`/`dot`) and a **fill** alpha ride
on the stroke the tool draws next; `StrokePainter` paints a dashed/dotted style as a single
constant-width dashed path and floods a fill under the outline, and `PdfVectorPainter` mirrors both for
export (a `setLineDashPattern` stroke and a `fill()` polygon). The **partial eraser** (`StrokeEraser`,
pure, tested) rubs out only the touched part of a stroke and splits it into the surviving pieces (each
inheriting the original's colour/style/fill), alongside the original whole-stroke delete
(`StrokeHitTester`). It hit-tests **segments**, not just vertices, and cuts where a segment crosses the
tip's disc — so a sparse shape stroke (a two-point line, a five-point rectangle) rubs out mid-shaft
just like densely-sampled freehand ink. `PageEraser` (pure, tested) is the page-level driver both modes go through: it
walks the page's layers, **skips hidden ones** (you only rub out ink you can see) and returns `null`
when nothing was touched, so the surface skips the document rebuild and the undo snapshot. The mode is
a view flag on the surface (`eraserMode`), set by which member of the rail's eraser slot is picked —
`EditorTool.ERASER` vs `ERASER_WHOLE`, so the choice is a tool, not a separate menu. The tip size has
no scheme of its own: `DrawingSurfaceView.eraserRadiusPt` derives it from the pen's `baseWidthPt` via
`eraserRadiusPt()` (`ERASER_RADIUS_FACTOR`, floored at `ERASER_RADIUS_MIN_PT`), in **document pt** so
it is zoom-invariant, matching the desktop. **Layer management** (`LayerOps`, pure, tested) adds/
deletes/renames/reorders/merges-down layers and moves a selection between them (all undoable;
`mergeDown` appends the upper layer's elements after the lower one's so z-order survives, keeps the
lower layer's name, and drops the emptied upper layer), while the *active*
layer (where new ink lands) and per-layer *visibility* are view-only editor state on the surface —
visibility just skips a layer in `PageRenderer.drawElements`, so it never touches the file. The UI for
all four lives in the rail's **Tool** (shapes, eraser mode) and **Layers** pop-ups
(`SideToolbar`); line style sits in the **Colour & size** pop-up. Fill has no control at all any
more: nothing sets it, so a stroke drawn in this app is never flooded — while the *format* keeps
`<stroke fill>`, so a document that already carries it still reads, renders and re-saves untouched
(`XoppReader`/`XoppWriter`, `PageRenderer.drawElements` → `StrokePainter.draw(fill = …)`).

**Authoring non-stroke elements.** With the **Text**, **Image**, or **LaTeX** tool active, the
surface is in a *placement* mode (`placeKind`): a one-finger tap (not a drag) raises `onPlace` with
the page-local point, which `EditorOverlays` turns into a keyboard dialog (text/LaTeX) or, for images,
an `onPickImage` callback up to `MainActivity`'s SAF picker. The chosen content is inserted via
`insertText` / `insertTex` / `insertImage` — each a single undoable edit appended to the page's top
layer. Tapping an existing text box reopens it for editing (clearing the content deletes it);
matched by element identity. The view keeps the loaded document intact and only appends/edits, so
every page, layer, and element round-trips through save.

**Audio-annotated strokes (`audio/`).** Xournal++ can record while you write and then replay from
any stroke: the stroke carries `fn` (a `.wav` file name) and `ts` (how far into that recording it was
started). We implement both ends of that.

*Recording.* The **Audio** rail slot toggles capture. `AudioSession.startRecording` names the file
with the desktop's local-time `yyyy-MM-ddTHH-mm-ss.wav` convention and hands it to `AudioRecorder`,
which pumps `AudioRecord` (44.1 kHz mono 16-bit PCM) into a `WavWriter` on its own thread — Android
has no WAV encoder, so `WavWriter` frames the RIFF header itself and patches its two length fields on
close. `RECORD_AUDIO` is requested the first time Record is pressed; everything else in the app works
without it.

The surface's `audioStamp` hook is read **inside `appendStroke`**, which is the single funnel every
committed stroke passes through — so freehand, shapes and splines are all stamped, and the audio
machinery stays out of the drawing hot path. The `ts` it stamps comes from the *bytes written so far*
(`WavWriter.durationMs`), not wall time, so a stroke's offset points at the sample it was really
drawn over even if the capture thread stalls.

*Replay.* The **Play object** tool sets `audioPlayMode`, which short-circuits `beginPointer` before
the gesture classifier — it is a pure query that never edits the document, so it earns no
`GestureIntent`. A tap picks the topmost stroke (`SelectionTester.pickTopmost`), reads its `AudioRef`,
and `AudioPlayer` seeks a `MediaPlayer` to that offset. A tap that misses, or lands on a stroke with
no recording, says so rather than failing silently.

*Sidecars.* A `.xopp` never carries its audio, and SAF grants access to the single document the user
picked — not to its folder — so we can't write a sibling from a `CreateDocument` URI alone. Instead
recordings are captured into an app-private directory (always available, no permission needed), and
the user nominates an **audio folder** once from the Audio pop-up; that persisted `OpenDocumentTree`
grant is the sidecars' home on disk. Opening a document pulls the files it references in; saving (and
stopping a recording) pushes them back out. Without a nominated folder audio still records and plays
for the session — only the hand-off to and from the desktop is missing, and the app says so. `fn` is
reduced to a bare file name before use, so a hand-edited path in a document can't escape that folder.

**Vertical space (`render/VerticalSpaceOps.kt`).** The **Vertical space** tool
(`EditorTool.VERTICAL_SPACE` → the surface's `verticalSpaceMode`, classified as
`GestureIntent.VERTICAL_SPACE`) reflows a page: pointer-down latches the grabbed page and the
page-local Y of the grab line, and each move frame re-applies `VerticalSpaceOps.shiftBelow` to the
**gesture-start snapshot** (the same recompute-from-the-start discipline as a selection move, so a
live drag never drifts or compounds). An element moves when its `ElementBounds.of(...).top` is at or
below the line — desktop's *"items which lie entirely between the cursor position and the end of the
page"*, so the line never tears an element in half — and that decision is taken **once, at grab time**:
a block that slides past the elements above it does not recruit them on the way. **Nothing stops the
block at the line.** The shift is the pointer's travel in either direction, so pulling up carries the
block above the line it was grabbed at (and, pulled far enough, off the top of the sheet — where it
stays, as on the desktop, recoverable by dragging back down). A drag that can't move anything returns
the same page list, which keeps `finishGesture` from recording an empty undo step. With
**Snap to grid** on, `dragShift` snaps the *amount* inserted to the page's ruling (`Snapping.spacingY`
→ `Snapping.snap`, i.e. a whole number of ruled lines), which is desktop 1.1.2's *"Added snapping for
vertical space"*; a plain sheet (spacing 0) leaves the drag continuous. **Every layer of the page
moves together — a deliberate difference from the desktop**, which reflows only the current layer: a
note written on one layer must not be left behind by space opened on another. The whole drag is one
undo step, and because it only rewrites coordinates the result round-trips through save unchanged.

### Document search and handwriting recognition (`DocumentSearch`, `HandwritingRecognizer`, `HandwritingIndex`)

Document search is full-document and multi-layered:
- **Authored text (`TextElement`)**: matches case-insensitively across text box lines, generating highlighted character spans.
- **Background PDF text layer (`PdfTextIndex`)**: extracted words from PDFBox are searched in reading order, highlighting matching word bounding boxes.
- **Handwritten ink strokes (`HandwritingRecognizer`, `HandwritingIndex`)**: non-eraser strokes across all page layers are automatically indexed and searchable. Features a dual-engine architecture:
  - **Google ML Kit Digital Ink Recognition (`MlKitInkEngine`)**: on Android devices, high-accuracy neural recognition trained on live temporal stroke sequences powers whole-word and multi-word recognition for both print and cursive handwriting across 300+ languages (with background model downloading for device locale including `it-IT`). Remote availability checks are throttled to avoid per-word IPC latency, and an on-model-ready listener triggers automatic re-indexing once download completes.
  - **Robust Pure-Kotlin Offline Fallback (`FallbackInkEngine`)**: used when models are downloading or in environments without Play Services/Android runtime. Features safe cursive ligature splitting, loop and stroke-count topology, multi-hypothesis character lattice classification, and letter-over-digit weighting for text notes.
  - **Multi-Candidate Hypotheses & Fuzzy Search (`DocumentSearch`)**: words store alternative recognition candidate strings; search matches across all candidates, performs diacritic and accent folding (`Normalizer` NFD), supports multi-word continuous phrases, and permits fuzzy Levenshtein distance matches ($\le 1$ for short queries, $\le 2$ for longer queries) to guarantee robust matching even with human handwriting variations.
  - **Non-blocking Background Indexing (`DrawingSurfaceView`, `DocumentSearch`)**: indexing never blocks the caller thread or UI. Typed and PDF text matches return instantly in $< 0.1\,\text{ms}$, while handwriting recognition runs exclusively on a background worker thread (`handwritingExecutor`) pinned to `THREAD_PRIORITY_BACKGROUND` with cooperative cancellation (`indexingGeneration`). Normal writing, erasing, live selection dragging, and scrolling run with zero indexing overhead.
  - **$O(N)$ Centroid Clustering & Non-Text Stroke Filtering (`HandwritingRecognizer`)**: strokes precompute bounds once in a `BoundedStroke` record; `LineCluster` aggregates running centroid and average height, guaranteeing stable horizontal text lines without vertical cascade collapse. Non-text elements (geometric frames, diagrams, and full-width page dividers) are filtered out prior to recognition.
- **Match highlights & navigation**: on the canvas, matches are painted as rounded pill rectangles with subtle padding (`drawRoundRect` in `DrawingSurfacePaint.kt`). The currently focused hit receives a prominent amber outline, and `jumpToSearchHit()` smoothly centers the viewport on each result.
- **Search UI (`SearchControls`, `SearchIndexingDialog`)**: integrated into the top bar as a Material 3 pill container with auto-focusing input, dynamic match counter badge (`1/3` or `0/0`), keyboard IME Search action, instant clear button, and stepper chevron navigation. When opening search on an unindexed document with handwritten ink, an AI processing dialog (`SearchIndexingDialog`) informs the user with live per-page progress while strokes are indexed; once complete, keystroke search runs completely in real time with instant highlight updates and zero lockups.

**Selecting objects (`render/`).** The **Select** tools (`EditorTool.SELECT`,
`EditorTool.LASSO_SELECT`, `EditorTool.TEXT_SELECT`, and `EditorTool.BG_SELECT`) share the rail's
Select group, but their gestures stay separate. Object selection (`selectMode`) mirrors desktop
Xournal++: a one-finger **drag** draws a rubber-band marquee and selects every element **wholly
enclosed** by it (desktop's rectangle-select semantics); a one-finger **tap** picks the single topmost
element under the point. Selection is **per page** —
anchored to the page the gesture started on — and elements are addressed by position, not identity, via
`ElementRef(layerIndex, elementIndex)`: a move rewrites the element objects but never reorders them, so
the refs stay valid across a live drag. The picking is pure and JVM-tested — **`ElementBounds` is the
single owner of "what rectangle does this element occupy"**: `ElementBounds.of` gives each element's pt
bounding box (strokes grown by half-width, images/teximages are their box, text a rough content-extent
metric) and `ElementBounds.TAP_PAD` is the one hit-test margin. Every consumer routes through it —
`SelectionTester` (picking), `PageRenderer` (viewport cull), `VerticalSpaceOps` (the grab line) and
`ElementEdits.hitsText` (tap-to-edit) — so selection handles, culling and vertical-space insertion agree
by construction rather than by coincidence; nothing re-derives a box locally. `SelectionTester` does
rect-containment / topmost
tap / union-bounds, and `SelectionOps` translates or deletes the addressed elements on a page list
(returning a new list; immutable pages/layers share structure so a snapshot stays cheap). Dragging inside
the selection outline translates the elements live (recomputing from the gesture-start document each
frame so there's no drift) and commits as **one undoable edit**. Dragging into the top or bottom
edge band starts a **drag auto-scroll** (`updateDragAutoScroll` + the `autoScrollCallback` frame
loop): the page scrolls one step per frame and the move is re-applied at the finger's last
position (`SelectionGestureController.moveSelectTo`), so the element rides the scrolling sheet even
under a held finger; a floating **Cut / Copy / Duplicate / Recolour / Width / Delete** bar
(`SelectionActionBar` in `ui/EditorActionBars.kt`, placed by `ui/SelectionActionAnchor.kt`) acts on the
selection (all undoable). That bar **rides with the
selection** rather than sitting at the bottom edge: the view publishes the selection's box in its own
view px (`DrawingSurfaceView.selectionScreenRect` / `onSelectionRectChanged`, reported from the render
pass next to `reportScroll`) through `PaneState.selectionRect`, and `EditorPaneView` — whose canvas
`Box` *is* the surface's coordinate space — hangs the bar just **below** the box (above it only when
the canvas ends below the selection, clamped inside the canvas either way). It carries **no Done
button**: tapping off the selection clears it, and that same tap starts the next stroke. Its controls
are 32dp squares rather than Material's 48dp (`SelectionBarButton`), which halves the bar's footprint,
and it is held at zero alpha until it has been measured so it fades in where it belongs instead of
flickering there. Pure placement geometry lives in `ui/SelectionActionAnchor.kt`'s
`selectionBarOffset`, pinned by `SelectionActionAnchorTest`. The
other mode bars (paste/region, table, spline, PDF-text selection) stay on the bottom edge. The dashed outline and marquee are drawn by the view over the page stack. Two-finger pan still
works in Select mode (it abandons the in-progress selection gesture).

The outline itself is drawn **exactly on the elements' ink box** (`SELECT_PAD_PX` = 0): a padded box
claims more than the user picked, and the handles' hit radii are what make it grabbable, not an
inflated rectangle. Beyond move/delete, the outline carries **eight resize handles**: the four
**corners** resize proportionally (a uniform scale about the opposite corner, `SelectionOps.scale`,
both factors being the pointer's distance ratio), and the four **edge midpoints** stretch **one axis
only** (`ResizeAxis.X` / `ResizeAxis.Y`, the pure `axisScaleFactor` measuring travel from the opposite
edge's midpoint, applied by `SelectionOps.scaleXY`) — the out-of-proportion resize, so a drawing can be
widened without being made taller. A per-axis stretch scales stroke widths (and text sizes) by the
**geometric mean** `sqrt(sx·sy)`: of the two axes neither is the right one for a scalar, and the mean
keeps the ink in proportion to the box it fills. Their grab radius is capped at half the selection's
on-screen size (`min(HANDLE_HIT_PX, min(w, h) / 2)`), because a fixed 30 px radius swallows a small
selection whole — every touch inside it lands within 30 px of a corner, turning an intended *move*
into a resize. The cap keeps the corner zones from overlapping in the middle so a small element
moves when dragged, while the handles stay grabbable right on the corners. The outline also carries
— for an all-stroke selection only — a **right-edge rotate knob**
(`SelectionOps.rotate`, which bakes the angle into stroke vertices). A **lasso** marquee
(`lassoMode`) selects everything wholly inside a traced polygon (`SelectionTester.inPolygon`),
alongside the rectangle. Lasso containment tests a **stroke's own points**, not its bounding box —
a diagonal or curved stroke inside the loop has box corners outside it, so a box test would drop
exactly the strokes the user traced around; rectangular elements (image, TeX, text) still test
their four corners. Both containment tests are **tolerant by `ElementBounds.TAP_PAD`** — the same
margin a tap pick uses: a rect-select box is grown by the pad, and a lasso point counts as inside
when it is within the pad of a polygon edge, so a hairline stroke or an empty text box traced
closely isn't dropped for landing a hair outside. Both containment tests also sweep **only the
active layer** (`onlyLayer`, resolved from `resolvedActiveLayer` by `SelectionGestureController`),
matching desktop Xournal++'s current-layer selection so a marquee never grabs ink the user isn't
editing; with no layer chosen yet the sweep falls back to the whole page. (A single **tap** pick is
still page-wide — `pickTopmost` is unchanged.) The traced path is stored in **page-local pt**
(`SelectionGestureController.lassoPoly`), converted as each sample arrives rather than on release,
and the overlay converts it back to view px with the *current* scroll each frame — so scrolling or
zooming mid-trace can't drift the shaded region away from the polygon that is tested, and the
overlay's closing segment is the same last→first wrap `inPolygon` assumes. **Cut / copy / paste / duplicate** run through a view-held element clipboard
(`SelectionOps.elementsAt` + `addToTopLayer`, which reports the pasted refs so the copies are
selected); paste lands on the visible page, **centred on the current viewport** (`SelectionOps.boundsOf`
+ the target box's `toPt`) rather than back at the copied-from spot. The bar's **Paste** first switches the tool to
`EditorTool.SELECT` unless a select tool is already active: only `SELECT`/`LASSO_SELECT` route
touches to `SelectionGestureController`, so pasting under `BG_SELECT` would otherwise draw the copies
as selected while they stayed undraggable — and the next touch would start a new marquee and clear
them. Dropping a move over a **different page** re-homes the
elements onto that page (`SelectionOps.moveToPage`, mapping through both pages' pt frames). The
floating action bar also **recolours / re-widths** the selection (`SelectionOps.restyle`).

**Align & distribute (`SelectionOps.align` / `distribute`).** With two or more elements selected the
same bar offers the six alignments — left/centre/right and top/middle/bottom — plus horizontal and
vertical distribution. Alignment translates every selected element but the **anchor**: the outermost
one on that axis, so the group lines up on its own extent and nothing moves further than it has to
(aligning left moves everything right of the leftmost element onto it, not all of them across the
page). Distribution keeps the two end elements and respaces the rest evenly between them, sorting by
centre so a straggler doesn't jump the queue. Both are pure page-list rewrites recorded as **one**
undo step, and both are no-ops — with no history entry — when the selection is too small to align
(two) or distribute (three). The scope and round-trip reasoning for what rotate/resize can touch lives in
[Stylus & selection roadmap](#stylus--selection-roadmap).

The background-copy variant (`backgroundSelectMode`) is intentionally not a selection transform. A
drag records only a rectangular marquee, clips it to the page, synchronously resolves the page's
background bitmap (`PdfPageCache.render` or `ImageBackgroundCache.render`), then
`PageRegionRenderer.render` composites the page background and all layers into a capped PNG. That PNG
is wrapped as one `ImageElement` in the same view-held clipboard the normal Select tool uses, so
Paste can drop the flattened region like any other copied image. There is no lasso path for this
tool; the copied result is one flat bitmap rather than editable elements.

The marquee **survives the release**: `commitBackgroundSelect` keeps it as `backgroundRegion` (page
index + `Bounds` in page-local pt, so it stays put under scroll and zoom — `drawBackgroundRegion`
maps it back through the `PageBox`) and reports it through `onBackgroundRegionChanged`, which drives
the **Copy** and **Cut** buttons in `SelectModeBar`. The release itself **never writes the clipboard**
— only an explicit Copy/Cut does — so marking out a region can't silently discard what the user had
copied. Copy runs `captureBackgroundRegion`, and can be pressed again after the clipboard has moved
on; **Cut** captures and then deletes, as **one undoable edit**, the elements `SelectionTester.inRect` finds
wholly inside the region on the page's **active layer** (the same containment and active-layer rule
the rectangle marquee selects by, so a cut never takes ink the user can't currently edit). The page
background is a page attribute rather than part of the region, so it is copied but never erased. The
region is view state only — never recorded in history — and clears on a new marquee drag, on a tool
change (the `backgroundSelectMode` setter), on Back, and on `load`.

**PDF (`render/`).** A `<background type="pdf">` page shows its PDF page as the background image:
`PdfPageCache` wraps the framework `PdfRenderer` (dependency-free, serialised — `PdfRenderer` is
not thread-safe) and `BackgroundRenderer` draws the rasterised page.

**One cache and one text index per PDF file, not per view.** Callers take a cache through
`PdfPageCache.shared(file)`, which keys live instances by absolute path and refcounts them; `close()`
releases one claim and only the last holder shuts the renderer down (`DrawingSurfaceView.setPdfSource`
owns exactly one claim, so being handed the instance it already holds gives the surplus one back).
A directly constructed `PdfPageCache` is unshared and closes on the first `close()`. The extracted
text layer is shared the same way through `PdfTextIndexCache` (soft-referenced by path, so an unused
index is reclaimable, and `forget` drops it when the bytes behind a path are rewritten by an import or
a merge). Without this, mirroring a PDF-backed document into both panes opened a second `PdfRenderer`
over the same file, rasterised every page twice against one shared bitmap budget, and extracted and
retained a second copy of the whole word index. The cache is keyed by page,
target-width bucket and (for tiles) grid cell, **LRU** under a heap-proportional byte budget (`PdfPageCache.budget`, a quarter
of the heap clamped to 24–192 MB — a page's cost varies ~64× between zoom levels, so counting pages
budgets nothing). Rasterisation never happens on the drawing frame: `request` returns whatever
resolution is already cached for that page (the nearest width, upscaled, or nothing) and queues the
exact size on a single worker thread, which fires `onPageReady` so the view redraws sharp. The view
also `prefetch`es one page either side of the viewport, so scrolling a long document meets a filled
cache. Evicted bitmaps are *not* recycled — the drawing thread may still hold one for the frame in
flight; the GC reclaims them. Past the whole-page raster ceiling `requestTiles` supplies
viewport-sized tiles rendered at the true on-screen scale (see the zoom paragraph above), so text
keeps sharpening as you zoom. A `.xopp` whose PDF isn't present falls back to a
plain sheet. **Import PDF** (`PdfImport`, invoked from `MainActivity`) copies the picked PDF into
app cache and builds pages from it (`PdfImport.pagesFor`) — one page per PDF page, sized from the PDF,
with the `filename`+`domain` on the first of them only and `pageno` thereafter (the desktop on-disk
convention). An `ImportPdfMode` chosen up front (the `ImportPdfDialog`) decides where they land:
`REPLACE` makes them the whole `Document` (`PdfImport.documentFor` → `load`), `APPEND` adds them after
the open document's pages as one undoable edit (`PageOps.appendPages` → `DrawingSurfaceView.appendPages`,
which also drops a lone untouched blank sheet so it isn't stranded in front of the PDF). Because the
`filename`/`domain` convention — and `XoppZip`'s single embedded `bg.pdf` — allow exactly **one** PDF
per document, `APPEND` onto a document that already has a PDF background does not add a second
reference: `MainActivity.appendMergedPdf` **merges** the two PDFs into one via `DocumentIo.merge`. `PdfMerger.join`
concatenates the current background PDF and the incoming one with PDFBox's `PDFMergerUtility` (source
pages imported verbatim, so they rasterise and re-export unchanged); the joined file is written to
`filesDir` — not the cache, so the link a plain `Save` records survives — under a name allocated by
that pane's joined-PDF `PdfStore`, so a merge never writes the file it is reading. The
new pages are sized from the incoming PDF with `PdfImport.pagesFor(reference = null, pageNoOffset =
<existing PDF page count>)`, and `DrawingSurfaceView.appendPdfPages` then re-points the document's one
reference at the joined file (`documentWithPdfReference`) **and** appends the pages in a **single
undoable edit** — the two halves must move together, since the appended `pageno` values index the
joined document. Existing pages keep their `pageno`, being at the front of the join. `Save`
(`ORIGINAL`) links the joined PDF by path; `Save As` (`ZIPPED`) embeds it as `bg.pdf` through the
usual `documentWithPdfDomain` rewrite, which is the easy case. **Export
PDF** (`PdfExporter`) flattens the document back out with **PDFBox** (`com.tom-roush:pdfbox-android`,
the one non-framework runtime dependency — see the note below): a `pdf`-backed page whose source PDF
is available (`PdfPageCache.source`, the cached import) is **imported verbatim so its original vector
content is preserved** (`PDDocument.importPage`), and the annotations are appended over it as a
**vector overlay** (`PdfVectorPainter`, an `APPEND`-mode content stream); every other page becomes a
fresh sheet whose background ruling is drawn as vectors (`PdfBackgroundPainter`) with the same
overlay. A `pixmap`-backed page additionally gets its picture embedded over that sheet —
`PdfExporter` decodes it synchronously through `ImageBackgroundCache.render` at 2× the page's point
size (~150 dpi) and draws it to the page box, so an image-backed document flattens to what the
editor shows; a picture that no longer decodes leaves the bare sheet. `PdfVectorPainter` mirrors the on-screen `StrokePainter`/`ElementRenderer` geometry at scale
1 — the `.xopp` unit == the PDF unit (1/72") — flipping y into PDF's bottom-left space via
`PdfPageTransform`; pen strokes taper per segment, the highlighter is one constant-width translucent
path, `.xopp` text elements use the base-14 fonts (see **Fonts in generated PDFs** below), and images
embed losslessly. **Nothing is rasterised** except
bitmaps that were already raster (user images and `pixmap` backgrounds), so a no-op import→export round-trips a PDF at ~its original size
and fidelity instead of bloating ~10× from a raster flatten. **Rotated source pages** (`/Rotate`
90/180/270) are handled: since the on-screen renderer already applies `/Rotate`, annotations are
authored in the page's *visual* space, so `PdfExporter` pre-multiplies the overlay content stream by
a `PdfOverlayMatrix` (a pure, unit-tested `cm` matrix — the inverse of the display rotation, with the
crop-box origin folded in) that maps visual coordinates into the page's unrotated content space; the
viewer's `/Rotate` then cancels back to the drawn position, so strokes, text, and images all land
correctly. For `/Rotate 0` the matrix is just the crop-origin shift.

**One page as an image (`PageSvgWriter`, `DrawingSurfaceView.writePagePng`/`writePageSvg`).** The
overflow menu's two page-export entries write the **active page alone** through a
`MainActivityDocuments` staging file, exactly like the PDF export does (so a share/cloud target can
ever see a half-written file). PNG is the page rendered at 2× its point size through the same
`PageRenderer`/`BackgroundRenderer` pair the canvas uses; SVG is generated by `PageSvgWriter`, which
walks the page's elements and emits one `<path>` per stroke (`d=`, the element's own width/colour) with
the ruling, text boxes and images alongside, and the whole document wrapped in a viewBox of the page's
pt size. It is deliberately **not** a screenshot: no Android type is involved, so the writer stays
pure and unit-testable, and the output scales without the raster's pixel ceiling.

**Fonts in generated PDFs.** The PDF **base-14** fonts (`PDType1Font.HELVETICA` and friends) only
encode WinAnsi, so `PdfVectorPainter` drops any codepoint outside `0x20..0xFF`. That is acceptable
for `.xopp` `<text>` elements — desktop Xournal++ owns their font description and the element is
preserved in the file regardless — but **not** for **text import**, where the source is arbitrary
UTF-8 and the glyphs only exist in the generated PDF. So the text-import path draws with fonts
**bundled as app assets** and embedded per document by `PdfFonts`
(`PDType0Font.load(doc, stream, subset = true)`): PDFBox subsets them into the output, so a CJK or
Cyrillic import renders identically everywhere without bloating the file with a whole face.

The bundled family is **DejaVu Sans** — regular, **bold**, oblique and bold-oblique, the four faces
markdown emphasis needs — and **DejaVu Sans Mono** (monospace, which also sets markdown code), chosen
for broad Unicode coverage at a modest ~3 MB and a permissive licence — Bitstream Vera + Arev, with the
DejaVu changes in the public domain, compatible with the Apache-2.0/OFL-only rule that already ruled
out iText. They live in `app/src/main/assets/fonts/` with the full licence text beside them as
`LICENSE.txt`. Codepoints even DejaVu lacks are **substituted, never fatal**: `GlyphSanitizer` (pure,
unit-tested; encodability injected as a predicate) maps each unencodable codepoint to U+FFFD — or `?`,
or a space — memoising per codepoint, and iterates by codepoint so an astral character becomes one
substitute rather than two surrogate halves. `PdfFonts.Embedded.measurer` is also what feeds
`TextPaginator`'s injected measurement, so wrapping is measured against the exact font that draws.
Markdown needs the same guarantee across *five* faces at once, so `MarkdownPdfWriter` resolves every
`RunStyle` to an `Embedded` up front and exposes that map both as the layout's `SizedMeasurer` and as
the draw-time font lookup — one table, so a line can never be wider on the page than the width it was
wrapped to.

`TextPaginator` exposes both a **list** form (`wrap`/`paginate`/`layout(String)`) and a **streaming**
one (`wrapLines`/`paginate(Sequence)`/`layout(Sequence)`/`layout(Reader)`) that wraps line-by-line and
yields each page the moment `linesPerPage` lines have accumulated — so memory scales with one page
rather than with the file, and a page can be written out before the input is fully read. The list form
is a thin wrapper over the streaming one, so both give byte-identical layout.

**Generating the text-import PDF.** `TextPdfGenerator` turns a plain-text file into the PDF a text
import is opened against. It owns only the authoring — layout is entirely `TextPaginator`'s — and
emits **real, selectable text**: a white sheet per page, then one `beginText`/`setFont`/
`newLineAtOffset`/`showText` per laid-out line at `heightPt - baselineFromTop(i)` (PDF's origin is
bottom-left, the paginator's is top-left). Nothing is rasterised and no OCR is involved, so
`PdfTextExtractor` recovers the original characters and text selection works on an imported `.txt`
exactly as on a born-digital PDF. Fonts are **injected** as a
`(PDDocument, PdfFonts.Face) -> PdfFonts.Embedded` loader, keeping the generator free of
`AssetManager` and unit-testable on the JVM; the plain path draws in one `plainFace`, monospace by
default — the sensible choice for logs and source. An empty file still yields one blank sheet to
annotate.

`generate` comes in two forms: one taking the text as a `String`, and a **streaming** one taking the
staged `File`. The streaming form reads the source a line at a time and drains `TextPaginator`'s lazy
page sequence page by page, so neither the file's text nor its wrapped lines are ever held whole —
that is the form `TextImport` uses. Authoring is still **one** `PDDocument`: writing it in bounded
chunks and concatenating them with `PdfMerger` was implemented and measured, and is *worse*, because
PDFBox 2's `legacyMergeDocuments` rebuilds the entire joined document in memory before writing (a
16 MiB source peaked at 405 MiB chunked against 319 MiB unchunked). The finished document is
therefore the memory ceiling, and it grows slowly: 128 MiB of source is ~31k pages at 479 MiB peak.
The text-import cap in `AppSettings` now bounds *time and output size* rather than memory, which is
why it defaults to 64 MiB.

**Authoring the markdown PDF (`render/MarkdownPdfWriter.kt`).** The markdown flavour keeps the same
split — pure layout in `render/markdown/`, PDFBox only here. `TextPdfGenerator.writeMarkdown` derives
a `MarkdownStyle` from the caller's `PageSpec` (page geometry, body size and line-height carry over;
the markdown-only sizes stay at their defaults), runs `MarkdownLayout.layout`, and hands each
`MarkdownPage` back to the writer. Three differences from the plain path:

- **A line is many fragments, not one string.** Each `RunFragment` gets its own
  `beginText`/`setFont`/`newLineAtOffset`/`showText` at `margin + indentPt + fragment.xPt`, because
  neighbouring fragments differ in face. A list `marker` is drawn the same way at its (negative)
  `markerXPt`, so bullets hang in the gutter the indent opened.
- **All fragments on a line share `line.fontSizePt`.** That is the size the composer measured them
  at, including inline code spans — code takes a different *face*, not a different size, inside a
  paragraph. Only a code *block* gets `codeFontSizePt`, and the composer stamps that onto the line.
- **A rule is a filled rect, not a stroke** — `addRect`/`fill` centred on the placed `yPt`, which
  needs no line-width or stroking-colour state and so cannot leak graphics state into later text.

A source that yields no blocks still gets one blank sheet, matching the plain path.
`MarkdownPdfWriterTest` closes the loop end-to-end: markdown in, real PDF out, `PDFTextStripper` back
out again — asserting the words survive, the markup characters do not, and more than one face is
embedded on the page.

**Wiring an image into the open path (`render/ImageImport.kt`).** The `.xopp` format has exactly one
home for a picture behind a page — `<background type="pixmap">` — so an opened PNG/JPEG/WebP becomes a
**single page** carrying `Background.Pixmap(domain="absolute", filename=<source content:// URI>)` and
one empty layer. The page is sized straight from the image's pixels at the 72-dpi baseline `.xopp`
user space already uses (one pixel → one point), which keeps its aspect ratio without inventing a
physical size the file never stated. The document links the picture by location — a pixmap background
is a reference, not embedded bytes — but a copy is taken into the image store all the same
(`DocumentIo.adoptImage`), so the page keeps rendering once the staging copy is swept and the picture
can be bundled into a ZIP save. `ImageImport.pixelSize` reads the dimensions bounds-only
(`inJustDecodeBounds`), so a phone-camera photo never has to be decoded in full just to size a page;
a file that doesn't decode leaves the canvas untouched and toasts instead.

**Painting a pixmap background (`render/ImageBackgroundCache.kt`).** The picture itself is decoded by
the raster counterpart of `PdfPageCache`, charged to the same shared `BitmapBudget` so an image-backed
document and a PDF-backed one compete for one bound rather than two. Entries are keyed by reference
and 64 px target-width bucket and evicted **LRU**; a picture is one image per page rather than a
rasterisable page count, so there is no tiling and no prefetch ring. `request` never decodes on the
drawing thread — a miss returns the nearest cached width (or nothing) and queues the exact size on a
worker, which announces it through `onImageReady` and gets a redraw, exactly like
`PdfPageCache.onPageReady`. Decoding is two-pass: bounds first, then `inSampleSize` chosen so the
decode never lands *below* the target width (`ImageBackgroundCache.sampleSize`), then an exact scale —
so a 12-megapixel photo shown at tablet width is never materialised in full. A reference whose bytes
won't open or decode is retired, so a frame doesn't re-queue it forever.
`DrawingSurfaceView.pageBitmapFor` is the one place both caches meet: a `pdf` page asks `PdfPageCache`
and a `pixmap` page asks `ImageBackgroundCache`, and either answer rides the same `pageImage` slot
into `BackgroundRenderer.draw`.

**Resolving and storing a pixmap reference (`io/ImageStore.kt`, `render/PixmapBackgroundDomain.kt`).**
A `pixmap` `filename` comes in every shape a `pdf` one does, and desktop Xournal++ resolves both
through the same `getAbsoluteFilepath`, so `DocumentIo` resolves them through one `openReference`
helper: a `content://` URI, an absolute path, a relative path beside the `.xopp`, or
`domain="attach"`'s `<name>.xopp.<filename>` sibling. Whatever it can reach is **copied into
`ImageStore`** (cache dir `images/`, one never-rewritten file per copy, swept by `DocumentIo.prune`
against the pictures the live canvases decode from and additionally held to
`StorageLimits.imageCacheBytes` oldest-first, as `PdfStore` is — live copies are never evicted, so
the cap is a backstop for strays rather than the primary bound), and the copies come back on `LoadedFile.Doc` as
an `images: reference → File` map — never as a rewrite of the document. That side table is the whole
design point: the document keeps the reference it was read with, so a plain Save round-trips it
unchanged, while `DrawingSurfaceView.setImageSources` hands the map to `ImageBackgroundCache`, which
prefers the local copy and only falls back to opening the reference itself. A reference nothing could
resolve leaves that page blank and toasts (`LoadedFile.Doc.missingImage`).

Saving mirrors the PDF rules. `SaveFormat.ORIGINAL` relativises each pixmap reference against the
document's own folder where it lives there (`portablePixmapReferences`, the pixmap twin of the PDF
path) and otherwise leaves it alone. `SaveFormat.ZIPPED` bundles: `documentWithPixmapAttachments`
re-points every pixmap background at `domain="attach"`, `filename="bg-<n>.<ext>"` and hands
`XoppZip.save` the entry → file map to embed, with the extension sniffed from the picture's own bytes
(`extensionFor`) because desktop picks its image loader by suffix and a `content://` URI has none. A
background whose picture can't be reached keeps its original reference rather than becoming an attach
name with no entry behind it. `XoppZip.open` extracts every non-bookkeeping, non-`bg.pdf` entry into
the image store and returns them as `Loaded.images`, keyed by entry name — which is exactly what the
attached background's `filename` says — so a package reopens self-contained.

**Round-trip status.** `PixmapBackgroundRoundTripTest` locks in both halves: a linked reference
(absolute path, relative path, or `content://` URI) is written verbatim and the picture's bytes never
enter the file, and a bundled one survives the whole `documentWithPixmapAttachments` →
`XoppZip.save` → `XoppZip.open` path with its entry bytes and the annotations over it intact —
numbering one entry per image-backed page and leaving an unreachable picture's reference alone.

**Wiring a text file into the open path (`io/TextImport.kt`).** A `.xopp` cannot represent "a text
file" — the only thing that round-trips is a PDF background — so `DocumentIo.read()` short-circuits
`FileKind.TEXT` the same way it does `FileKind.PDF`: typeset the bytes, return
`LoadedFile.Pdf(generated = true)`, and every path downstream (background rasterisation,
`PdfImport.documentFor`, text selection, saving) runs unchanged with no text-specific branch. The
generator is injected into `DocumentIo` (it needs the bundled fonts, and so an `AssetManager`), which
keeps the rest of the class Android-free.

**Markdown routes on the name, not the bytes.** A `.md` file is printable UTF-8 like any other text,
so sniffing cannot separate the two: `FileKind.of` still returns `TEXT` for markdown, and the
markdown verdict is a **second, name-level** one — `FileKind.isMarkdownName(name)` matches a `.md` /
`.markdown` suffix (case-insensitively) on the *display* name SAF hands `DocumentIo.read()`. That is
the single place in the open path an extension is consulted, and it is consulted only to pick a
**flavour**, never a format. `TextImport.pdfFor` turns the name into a `render/TextFlavor`
(`PLAIN` · `MARKDOWN`) and rides it through `TextPdfGenerator.generate(…, flavor)` rather than
forking a parallel import class — everything downstream of the generated PDF is identical, so a
second path would buy nothing. The `MARKDOWN` branch in the generator runs the markdown parser,
layout and `MarkdownPdfWriter` in place of `TextPaginator`. Each flavour carries its own `cachePrefix` (`text:` / `markdown:`) so the
same bytes opened as `notes.txt` and as `notes.md` cannot collide in `PdfStore`.

**Parsing markdown (`render/markdown/`).** Structure and geometry are split the same way plain text
splits them: `MarkdownParser` turns source into a tree of `MarkdownBlock` and stops there — no
measurement, no wrapping, no pages — exactly as `TextPaginator` is pure geometry with no knowledge of
markup. The parser is dependency-free by policy (no CommonMark library): it is a **line-based
recursive descent** over normalised lines (CRLF→LF, tabs expanded once, so indentation is
countable), with the per-line "what does this start?" rules factored into `MarkdownLine` so each can
be tested against a single string.

Two decisions are load-bearing:

- **Nesting is the tree, not a depth field.** `Quote` holds its child blocks and `ListItem` holds
  its child blocks, so containers *recurse*: a quote's stripped content and a list item's dedented
  content are each re-parsed as a document of their own. A list item containing a code block, or a
  quote containing a list, therefore needs no special case, and no `depth` integer can fall out of
  sync with the structure — layout counts depth as it descends.
- **Inline markup stays raw.** A `Paragraph` or `Heading` carries its source text with `**bold**`
  and `[a](b)` intact; decoding spans into styled runs is a separate pass, which keeps block
  structure testable on its own and stops one parser from doing two jobs.

The dialect is the common core of CommonMark — ATX and setext headings, paragraphs with lazy
continuation, fenced and indented code, ordered and unordered lists with nesting, block quotes,
thematic breaks. Reference links, tables, HTML blocks and footnotes are out of scope and survive
verbatim inside a paragraph rather than being mangled. `MarkdownParserTest` covers each block type
plus the cases a hand-written line parser gets wrong: lazy continuation, loose lists, unclosed
fences, `* * *` beating a bullet marker, CRLF and tabs.

**Inline spans → styled runs.** The deferred second pass is `MarkdownInlineParser`: raw inline
source in, a flat list of `StyledRun` (`text` + `bold`/`italic`/`code`) out — still pure, still
unmeasured. Runs are **flat rather than a tree** because wrapping only ever asks "which font do I
measure the next word in?", so nested `**bold *and* italic**` is just adjacent runs with different
flags. It runs in two small stages: `InlineScanner` walks the source once and resolves everything
decidable locally (backslash escapes, code spans including the double-backtick form and the
symmetric pad-space rule, and link/image labels), emitting `*`/`_` as unresolved delimiter runs
tagged with CommonMark's flanking rules; `InlineEmphasis` then pairs those runs with a
delimiter-stack walk (including the rule of three), stamping styles onto the tokens between each
match. Nesting needs no recursion because a token can be stamped twice, and any delimiter that never
finds a partner prints literally — which is what keeps `2 * 3 * 4` and `snake_case_name` intact.

Two decisions here:

- **Link URLs are dropped; the label renders.** `[label](url)` becomes just `label` (and
  `![alt](url)` just `alt`). The output is a printed PDF page where a URL is neither clickable nor
  wanted mid-sentence.
- **A label is frozen into finished runs before it is spliced in.** Parsing a label through the full
  pipeline (rather than splicing its raw tokens) is what stops a leftover delimiter inside a label
  from pairing with one outside it.

**Styled runs → laid-out lines.** `StyledWrapper` is the markdown counterpart of `TextPaginator`'s
wrapping half: it takes styled runs plus a `(RunStyle, String) -> Float` measurer — one metric per
face (`REGULAR` · `BOLD` · `ITALIC` · `BOLD_ITALIC` · `CODE`, with `code` beating emphasis) — and
returns lines of `RunFragment`s, each a stretch of one face with an x offset and width. Baselines
stay pagination's job, so a fragment carries no Y.

The break rules are deliberately the plain path's: greedy word fill, and a mid-word hard break only
when a word can't fit a line alone. The shared character-level pieces (tab expansion, hard break)
live in `TextWrapping`, which both wrappers call, rather than being copied. What genuinely differs is
that a *word* can span runs — `**bold**tail` is one unbreakable word in two faces — so words are
tokenized as (style, text) segment lists and measured segment by segment, and adjacent same-face text
merges into one fragment, re-measured rather than summed so kerning stays honest. Inline whitespace
collapses to a single separator (markdown's own rule; verbatim spacing belongs to code blocks, which
are never inline-wrapped). Like the parsers it is pure, so `StyledWrapperTest` drives it with
synthetic metrics where bold is deliberately wider than regular and checks that a face change alone
moves a break.

**Blocks → pages.** `MarkdownLayout` is the top of the markdown path (`layout(source, style, measure)`
→ pages), and it runs in two halves for one reason: page breaking wants to see whole blocks.

- `MarkdownComposer` walks the block tree and emits a flat list of `MarkdownGroup`s — one per block,
  each holding the drawable `MarkdownItem`s it produced (`Line` · `Rule` · `Space`). Headings are set
  bold at their level's size, paragraphs at body size, code verbatim in monospace at its own fixed
  size (hard-broken to fit, never re-flowed on words). Nesting is plain recursion at a larger indent,
  so a list inside a quote inside a list needs no depth counter; a list marker (`•` or `3.`) is hung
  on the item's first line, one indent step into the gutter. Gaps between blocks **collapse** — the
  larger of the space below one block and above the next, never their sum.
- `MarkdownLayout.paginate` then walks the groups with a pen offset, giving each line a baseline at
  the foot of its box and each rule the centre of its own. A block longer than a page simply flows
  onto the next; a `Space` at the top of a page disappears, so a page never opens with a gap; and a
  group marked `keepWithNext` (only headings) moves to the next page when it plus the first line of
  whatever follows won't fit, which is what stops an orphaned heading at the page foot.
- Every size lives in `MarkdownStyle`, the markdown-flavoured `PageSpec`: sheet and margins, the six
  heading sizes, heading and block gap ratios, list/quote/code indent steps, code type size, and rule
  height and thickness. Like the rest of the path it is Android- and PDFBox-free, so
  `MarkdownLayoutTest` lays documents out against synthetic monospaced metrics and asserts the breaks
  arithmetically.

Two consequences fall out of the generated PDF living only in the cache:

- **It is cached by content, not copied.** `PdfStore.cached(key) { … }` keys the generated file on a
  SHA-256 of the file's display name followed by its bytes, **streamed** through the digest a buffer
  at a time so a large import never becomes a large `String` (the staged copy's own name is per-open
  scratch and would never hit), recording key → file name in an `index.tsv` sidecar the sweep skips.
  Reopening the same file while the entry survives reuses the PDF instead of typesetting it again.
  `MainActivity.adoptPdf(inStore = true)` then takes the store file **in place** rather than copying
  it, so the cached file *is* the tab's `pdfPath` — which is what keeps liveness pruning from
  sweeping it out from under its own cache entry. A cached entry deliberately **outlives its tab** —
  `prune` keeps every file the index still names — so the cache is bounded by a **byte budget**
  instead: once the folder exceeds `StorageLimits.pdfCacheBytes` (Settings → Storage), `prune`
  evicts the oldest non-live files until it fits, and drops the index entries whose file it deleted,
  so an evicted entry simply regenerates on the next open.
- **It must be saved `ZIPPED`, not `ORIGINAL`.** There is no stable on-disk source to link: the
  cache path would be swept and the document would reopen blank. So the text branch makes the sticky
  save format `ZIPPED`, and `encode` embeds the bytes through the existing `domain="attach"` path.
  `TextImportRoundTripTest` guards the whole journey — open text → annotate → save → reopen — and
  `TextImportTest` the caching contract.
- **It is bounded by a size cap.** Typesetting holds the whole text *and* its whole laid-out page
  list in memory, so an unbounded import of a several-hundred-megabyte log would stall the app and
  fill the cache. `TextImport.pdfFor` therefore checks the staged file's **length before reading a
  byte** against `StorageLimits.textImportBytes` and throws `TextTooLargeException` when it is over;
  `MainActivity`'s existing "Open failed: …" toast shows the message, which names both sizes and
  points at Settings → Storage. Both limits are pushed into `DocumentIo.limits` by
  `MainActivity.applyStorageLimits` on load and on every settings edit, since `DocumentIo` outlives
  any one `AppSettings` value.

Those JVM tests need one build-level accommodation: PDFBox reads its **CMap data** (`Identity-H`,
needed by every `PDType0Font`) out of the AAR's `assets/` through Android's `AssetManager`, which
unit tests don't have. `app/build.gradle.kts` therefore extracts the AAR assets into the unit-test
resources (`extractPdfboxAssets`), where PDFBox's classpath fallback finds them, and sets
`unitTests.isReturnDefaultValues` so FontBox's `android.util.Log` calls no-op instead of throwing.

**PDF text selection.** An imported PDF's **text layer** is extracted on import (off the UI thread)
by `PdfTextExtractor` — a `PDFTextStripper` subclass that turns each positioned glyph into a
`CharBox` and groups them into `PdfWord`s (`PdfWordGrouper`, breaking on whitespace, wide gaps, and
line changes) — into a `PdfTextIndex` threaded to the surface via `setPdfTextIndex` (mirroring
`setPdfSource`). This reuses the **same PDFBox dependency** as export, so **no OCR engine** is needed
for born-digital PDFs; a scanned image-only page yields no words (`hasAnyText` false), and OCR for
that case is a tracked follow-up. Boxes are page-local top-left points (the `.xopp` frame), so they
map to the screen through a `PageBox` exactly like strokes. The **Select text (PDF)** tool
(`EditorTool.TEXT_SELECT` → `ActiveTool.TEXT_SELECT` → `GestureIntent.SELECT_TEXT`) drags to select
an inclusive reading-order word range: `beginTextSelect`/`textSelectMove` resolve pointer positions
to word indices (`PdfTextIndex.anchorWord`), the range is highlighted (`drawTextSelection`), and the
`TextSelectionBar`'s Copy puts the text on the Android system clipboard (`copyTextSelection`). The
selection is a **view-only** overlay derived from the PDF — it isn't part of the `.xopp` document, so
it doesn't affect round-trip (matching how desktop selects a PDF background's text).

**Chrome (`ui/`).** `EditorScreen` is the one editor screen: a top bar (`EditorTopBar`) with undo/redo
icon buttons, a compact quick **Export PDF** button and a one-tap **Save** button after it (Save
outermost, i.e. under the thumb), and a
**☰ overflow menu** (`DropdownMenu`) holding Open, Import PDF, Export PDF, Save, Save As,
Pen diagnostics, and Settings; a **Main Toolbar `SideToolbar`** (rail dockable to the left or right
edge — `ToolbarPosition` offers only those two, so the rail always runs as a vertical column and
never fights the Secondary Toolbar for the top edge)
with core drawing tool slots and pop-up panels; a permanent **Secondary Toolbar** (`TopBarToolsRow`) for geometric
figures and tools embedded in the top bar; and the canvas filling the rest.
The element selection's action bar floats beside the selection itself (see [Selecting objects](#selecting-objects-render)).

The rail also carries the **Colour & size slot** (`ColorSizeRailSlot`, `ui/ColorSizeRailSlot.kt`) — the
one rail slot that is not a button opening a menu: its main surface *is* the control. It is a vertical
stack of `AppSettings.FAVORITE_COUNT` (3) favourite-colour dots, one tap away, with a **chevron** beside
them onto the full **Colour & size** pop-up (`ColorSizePopup`: palette, tip sizes, line style). That
chevron is where the *separate* Colour & size rail position went — the two slots were one errand, and
retiring `color` (`PANEL_RAIL_ITEMS` no longer names it, and a saved order naming it is dropped) gave
the rail a position back while the richer pop-up stayed one tap away. `favoriteToolFor` decides whose
terna the dots draw — the highlighter's while the highlighter
is live, the pen's otherwise (a non-inking tool such as the eraser offers the pen's rather than an empty
strip) — so the pen and the highlighter keep **separate** favourite colours, exactly as they already keep
separate colour fields, while the slot never holds two ternas. The slot measures exactly **one slot**
(`LocalRailSlotSize`), so it cannot cost the rail the tools it shows at a glance: the dots take 52% of
its width as a column of three cells, each `slot / 3` tall with the dot inset 2dp inside it — the cell
is the *touch target* while the feedback drawn on it is clipped to `DotCellShape`, a **true circle
centred in the cell** (not `CircleShape`, which would inscribe an oval in a cell wider than it is tall),
so a tap's ripple and a hovering stylus' state layer light up the dot rather than a grey box around it —
and a
centred **square** chevron button of 40% of the slot sits beside them across a 4dp gap — a square
button rather than a full-height strip, because a strip as tall as the slot and only as wide as a
glyph reads as empty space with an arrow in it and puts a target where the hand expects none. A tap goes through `pickFavoriteColor`, which writes the colour into
the **owning tool's** slot (`AppSettings.lastColor` for the pen, `highlighterColor` for the highlighter —
the same write the Colour & size pop-up makes) and applies it to the active canvas stroke directly without switching
the active tool away (so drawing tools like line, rectangle, or eraser keep their tool without reverting to the pen). A long-press opens the palette over that dot and rewrites it
(`assignFavorite`). `sanitizeFavorites` pads a short, long or corrupt pref to exactly three opaque,
distinct colours — the row is a fixed three, so "three" is a shape the slot depends on, not a
preference — and `AppSettingsBackup` carries both lists verbatim. The factory sets are
`DEFAULT_PEN_FAVORITES` = **black, red, green** and `DEFAULT_HIGHLIGHTER_FAVORITES` = **yellow, green,
blue**. Both ternas are edited side by side under **Settings → Drawing → Colors**
(`ColorsSection.FavoritesEditor`), where seeing them together *is* the point; the rail draws one at a
time.

**The rail never cuts a slot — and never shrinks one.** `ToolbarShell` (`ui/SideToolbar.kt`) measures
the height it is given (`BoxWithConstraints`) and hands the visible slot count to `railContentScale`
(`ui/RailSizing.kt`), a pure function: it counts the slots that fit **whole** at their own size (`m`
slots take `m·slot + (m−1)·spacing`) and spreads exactly that many over the space, so the slot after
them starts at the rail's bottom edge instead of being cut by it. The scale it returns is therefore
never below 1 — a rail button is never drawn smaller than the size it was designed at, which is what
keeps the rail usable at a glance, and what an all-slots-visible rail wastes most of its length on —
and only a few percent above it, capped at `RAIL_MAX_SCALE` (1.08) so the slot stays within the rail's
inner width. The leftover strip below the last whole slot is spent on slightly larger buttons rather
than on a sliver of the next one. Slots the height cannot hold are a **scroll away**, and a rail that
fits entirely is left at scale 1. The shell publishes the scale as the slot size
(`LocalRailSlotSize`, read by every slot — tool buttons, pop-up buttons, the one-tap colour slot; the
zoom slot's percentage label reads the factor itself via `LocalRailSlotScale`, since its content is
text and not a dp size) and applies it to the gaps but **not** to the column's padding, so the
whole-number fit stays exact.

Chrome styling is one design, with no switch: the Material 3 floating dock.
- The Main Toolbar renders as a floating dock surface (`SideToolbarModernWidth` = 56dp) with rounded corners (`20.dp`), tonal elevation (`3.dp`), shadow elevation (`4.dp`), subtle border, and squircle tool buttons (`12.dp`) at the rail's own whole-slot sizing (44dp slots, grown by a few percent when that hides a sliver of the next one); the Secondary Toolbar in the top bar renders as an adaptive floating dock surface (`40.dp` height) enclosing the visible figure tools with rounded corners (`20.dp`), tonal and shadow elevation, dynamically adapting its width to the number of visible figures, and not drawn at all while none are visible; the **active tool indicator** stands outside that dock as a button of its own (`StandaloneToolIndicator`), which on a left-docked rail is the top bar's leading slot — the corner between the rail's column and the dock, and the slot whose fixed width (`TOP_BAR_INDICATOR_WIDTH`) leaves the dock starting on the document tab strip's own line — and trails the dock when the rail is docked right (`standaloneIndicatorSlot`, pinned by `IndicatorSlotTest`), so the run always reads rail → indicator → figures; it is `TOP_BAR_DOCK_HEIGHT` (40dp) tall with `TOP_BAR_DOCK_CORNER` (`20.dp`) on all four sides plus the dock's tonal and shadow elevation and hairline border, its face is shared with the old cap (`ToolIndicatorFace`) and shows the selected tool, colour and stroke width with no thickness bar beside the size letter, tapping it opens the Colour & size pop-up, and its grey is the `surfaceContainerHighest` role — a surface step, never the accent `primaryContainer` a picked tool button wears; the style it replaced (the indicator as the dock's own **end cap**, grey flush to the dock's rounded border, `ActiveToolIndicator`) is kept behind the `INDICATOR_AS_DOCK_CAP` constant as a one-flip restore path; the top bar uses 48dp height with a grouped undo/redo pill container (`18.dp`), a compact quick Export PDF button followed by the accent-tonal Save button (so Save sits outermost), and active document title chip; tabs use 38dp height with pill chips (`14.dp`); page counter and zoom badges use frosted rounded pills (`14.dp`); floating action bars use rounded capsules.
Each rail button owns its own `DropdownMenu`, so the pop-up
is anchored to that button (opening to the right of the rail) rather than filling the screen. The
rail's head is **one slot per tool group** (`ToolGroups.kt`): `TOOL_GROUPS` partitions every
`EditorTool` into named groups (pen · highlighter · eraser · line · rectangle · shape · pan · select ·
insert · vspace · play). **Line** and **Rectangle** are one-tool groups of their own; every other
figure shares the **shape** group, faced by default with the ellipse (the circle). `ToolGroupButton`
renders each as a single button faced with that group's current tool — a tap activates that tool, and
tapping the slot **again** once it is the live tool opens a `DropdownMenu` over the group's members (a
long-press opens it too, but is no longer required). `ToolGroupPicker` (`ToolbarPopup.kt`, shared with
the modern top bar's compact slots) lays those members out as **rows of compact icon-and-label
buttons** rather than one full-width row each, with related tools sharing a row: a group declares its
grouping in `ToolGroup.pickerLayout` and `pickerRows()` resolves it against the members actually shown
(so the diode family, the two transistors and the two sources sit together, each inverted logic gate
beside its twin, and any member a layout doesn't name still wraps three to a row rather than going
missing; the user-ordered Shapes slot declares no grouping at all, so its order is preserved). The two
figures that ship in several geometric **kinds** rather than a group — the triangle and the trapezoid,
whose secondary-toolbar buttons carry a variant submenu — get the same row treatment from
`ToolVariantPicker` (`ToolbarPopup.kt`), which lays the kinds out through the pure `variantRows()`
two to a row: the live kind is tinted, and the kind that has a dialog behind it (the scalene, whose
angles are configurable) keeps its pencil so picking it and opening that dialog stays one gesture.
Every button is a radio item and the slot's live member is tinted in the primary container colour — the same
"this one is on" language as the rail — so the picker reads as one setting with several choices. A tool slot whose group has **more than one member** wears a small **chevron in its bottom-right
corner** (`BoxScope.MenuChevron`, `ToolbarPopup.kt`, drawn by `ToolGroupButton`), so a slot that can
be re-faced reads at a glance differently from one that just activates a single tool; the panel
pop-ups (`ToolbarPopupButton`) deliberately do **not** wear it — they each open a panel, not a
submenu of alternatives. The **insert** group carries one member — **Image**, the file/gallery
picker — while text authoring lives in its own sibling **text** group (Text, LaTeX), so the rail
keeps pictures and typed content in separate slots. A pick writes
`AppSettings.toolGroupSelections` (a `groupId → EditorTool` map, encoded as `group:TOOL` pairs in
one pref, so slots survive a restart) and activates the tool in the same gesture. `selected()` and
`decodeToolGroupSelections()` both drop non-member entries, so a stale pref degrades to the group's
first tool rather than facing a slot at a tool that has since moved. `startingTool()` resolves the
opening tool as `defaultTool`'s **group selection**, so the rail's face and the live tool agree on
launch. The **Figures** list in settings includes all geometric figures (`FIGURE_TOOLS` in `ToolGroups.kt`, including simple line and rectangle): `orderedShapeTools()` and
`visibleShapeTools()` read `AppSettings.shapeOrder` / `shapeHidden` (tool names, encoded like the rail
ids) and the **Figures** settings section edits them with the same drag/toggle list the Toolbar
section uses (`ReorderableRowList`), keeping visibility and order in sync with the secondary top bar (`visibleTopBarItems` filters items hidden in either `topBarHidden` or `shapeHidden`). The same section owns the **default figure size**
(`AppSettings.defaultShapeSlot`, an index into `penWidths`). That default exists because a figure is
drawn at its set width with no pressure while the pen thins with pressure, so with pressure on a
figure reads thicker than a light pen stroke — the slot is the knob that closes the gap without
leaving the desktop's width semantics. The line **style** (Solid/Dashed/Dash-dot/Dotted) lives
only in the Colour & size pop-up — the separate **Style** rail slot was
removed — so a dashed pen is one tap away as in the desktop's pen options. The
pop-up itself is deliberately compact: one **horizontally scrolling** row of swatches
(`ColorPaletteRows(compact = true)`, ending in the add-colour swatch that appends to `penColors` and
selects the new colour — the row scrolls so the menu's height never changes with the palette's size),
the three tip sizes as one bar of small boxes (`WidthSlotBar`) and line style as one row of chips
(`LineStyleChips`). **Which positions the rail shows, and in what order**, is data too
(`RailItems.kt`):
`RAIL_ITEMS` names every position — every tool group plus the Colour/Shape-recognition/
Guides/Layers/Zoom/Background/Pages/Audio panels — in an explicit factory order
(`FACTORY_RAIL_ORDER`): the drawing tools reached for most first (pen, highlighter, colour & size,
eraser), then the rest of the tools and panels, with the rarer controls (shape recognition,
guides) trailing the rail. `SideToolbar` renders `visibleRailItems(railOrder, railHidden)`,
dispatching each id to its group button or panel. The Toolbar settings section edits those two prefs
(`AppSettings.railOrder`, a comma-separated id list, and `railHidden`, the same encoding for the
switched-off ids); a row is reordered by **long-press drag** (`detectDragGesturesAfterLongPress`),
which steps `moveRailItem()` one place each time the finger crosses a row height, so the list
reshuffles live. The in-flight order is held in the section's own `draggedOrder` state because
several steps can land inside one frame, before `onChange` has recomposed `settings`. `orderedRailItems()` **appends** any id the saved order omits in factory order, so
a position added in a later release still appears for an existing install, and `decodeRailIds()`
drops ids that no longer exist. The tools are UI-level
`EditorTool`s (Hand is view-only pan and Text/Image/LaTeX are placement modes, none a document tool,
so `EditorScreen.applyTool` maps them to the surface's `handMode` / `placeKind` and maps the three
drawing tools to the document `Tool`). The **Pages** pop-up is a page navigator: `Page N / M` with
◀ / ▶ to jump to the previous/next page (`goToPage` scrolls the stack; the surface reports the page
under the viewport centre via `onCurrentPageChanged`), plus Add / Remove page. A **right-edge scroll
thumb** (`ScrollThumb.kt`, overlaid on the canvas in a `Box` sibling of the `AndroidView`) gives
PDF-style fast paging: the surface reports its vertical scroll geometry via
`onScrollChanged(scrollY, totalHeightPx, viewportPx)` (all content px, already zoom-scaled), the thumb
sizes/positions itself from that ratio and **dragging it** drives `DrawingSurfaceView.scrollToY`. The
touch target is only the thumb *band* (a small region tracking the scroll position), not the full
right edge, so a stylus can still draw over the page's right margin everywhere but the thumb; the
thumb sits faint when idle, brightens after a scroll, and is brightest while dragged, showing a
page-number bubble beside it. A rounded grip "peninsula" bulges out of the thumb's centre (purely
visual — the whole band already catches touches) so there's an obvious finger-sized target to grab. It is a pure navigation affordance — no `.xopp` state, so nothing
round-trips. Choosing Settings
from the ☰ menu swaps in `SettingsScreen`, which is shaped like a tablet's system settings and has **two
shapes** for one set of areas. On a screen at least `SETTINGS_TWO_PANE_MIN_WIDTH` (600dp) wide the four
`SettingsArea`s — **Input**, **Drawing**, **Interface**, **App & data** — are a **permanent side menu**
(`SettingsSidebar.kt`: its own search field and one row per area) with the selected area's
`SettingsSection`s in the pane beside it, and an opened section replaces that pane (the title bar and the
back arrow return to the list). Narrower than that the side menu is dropped for the pushed flow — index,
then area, then section — because a permanent list on a phone would leave the controls a strip to live
in. Back pops whichever shape is on screen one step at a time (section → search results → area → out of
settings), so the system back button mirrors the title-bar arrow either way.

**Search** runs over the sections from both shapes (`SettingsSearch.kt`, pure): a query matches a
section's title, its summary, or its `keywords` aliases — the everyday words for the controls a user
might type instead of the app's label ("momentum", "wallpaper", "cache", "left-handed"). The aliases
are what makes search *find* rather than filter titles, so `SettingsSearchTest` pins a handful of those
queries to their sections and requires every section to carry aliases and answer to its own title. A
result names the area it lives in (`areaOf`), so a hit found by an alias still says where the setting
belongs; opening one goes straight to the section. `SettingsAreaTest` pins the structure the whole
screen rests on: the areas cover every section exactly once (an unlisted section would be unreachable)
and no area owns a single child (a one-child area is a tap that explains nothing). Section bodies live
in their own `*Section.kt` files (`StylusSection.kt`, `EditorSection.kt`, …, `ShortcutsSection.kt`). The **Shortcuts** section
owns the whole keyboard-shortcut table: `AppSettings.penEraserToggleKey` / `handToggleKey` (the two
toggles) plus two maps — `toolShortcutKeys` (one key per `EditorTool`) and `colorShortcutKeys` (one
key per palette swatch). They are persisted by `encodeToolShortcuts` / `decodeToolShortcuts`
and `encodeColorShortcuts` / `decodeColorShortcuts` (the same `key:value` comma-list style as the
other codecs, dropping unknown tools/colours and blank keys), migrating the old per-colour keys
(`black_pen_key` etc.) once. `EditorRegions.onKeyPressed` resolves a character against those maps —
toggles first, then colour, then tool — so the table is data, not code. The factory pen palette (`PEN_COLORS` — desktop Xournal++'s default palette, hex for hex; see `PaletteColorsTest`) and `PEN_WIDTH_LABELS`
lives beside its pop-up (`ToolbarColorPopup.kt` / `ToolbarSizePopup.kt`); the **live** palette is `AppSettings.penColors`, seeded with `PEN_COLORS` and edited under **Settings → Drawing → Colors**
(`ColorsSection.kt`: add / redefine / delete, capped at `MAX_PEN_COLORS`, opaque and
de-duplicated by `sanitized()`, round-tripped by `encodePenColors` / `decodePenColors` which falls
back to the factory list rather than blanking the pickers). The user's own colours are part of the
palette, not guests of it: neither `sanitized()` nor `decodePenColors` prunes a colour for being
absent from `PREDEFINED_COLOR_NAMES` (that map is display names only), so a swatch added here comes
back on the next launch and travels in — and returns from — the JSON backup. Deleting a colour drops its
`colorShortcutKeys` entry with it, and the last swatch can't be deleted. The user-configurable pen widths and the editable custom colour are
persisted in `AppSettings`/`SettingsStore`, and the arbitrary-colour HSV/hex picker is in
`ColorPicker.kt`. Every place a colour is chosen — the pen's rail button, the text-box dialog, the
selection recolour menu — renders the **one** `ColorPaletteRows` component (`ColorPalette.kt`):
the user's swatches, the editable custom slot (long-press → `CustomColorEditor`),
reading and writing one `ColorPaletteState` over `AppSettings` (its `colors` is `penColors`, so an
edit under Settings shows up everywhere at once). The swatches wrap (`FlowRow`) so the
custom slot survives a narrow dialog, and the HSV editor is hoisted *outside* the menu that opened it
so dismissing that menu doesn't take the dialog with it. `AppSettings` also remembers the pen you
left off with — `lastColor`/`lastWidth`, re-pushed onto a freshly created surface by `EditorScreen`;
`withColorUsed(color)` is now just that write, since the recently-used list was removed along with
its pref key. The **Select** tool adds a rail entry and a floating action bar; its
mechanics are in [Selecting objects](#selecting-objects-render) above.

## Relative PDF references {#relative-pdf-references}

A background reference is only useful if it still resolves on the machine that opens the file next.
An absolute Linux path means nothing on Android, and a `content://` URI means nothing anywhere but
the device that issued it — so **a path relative to the `.xopp` is the portable form, and the
default this app writes whenever it can.** Desktop Xournal++ has no `relative` domain: a relative
path is carried under `domain="absolute"` and resolved against the document's own folder
(`LoadHandler::getAbsoluteFilepath`), so what we write is exactly what desktop already reads.

The string logic lives in `io/PdfReference.kt` — pure and unit-tested (`PdfReferenceTest`), with no
Android types, so the same helpers serve both filesystem paths and SAF **document ids**
(`primary:Docs/notes.xopp`), which are `/`-joined paths behind a volume root.

**Reading** (`DocumentIo.resolvePdfBackground`, which now takes the source URI of the `.xopp` being
opened) handles four shapes, in order:

| Reference | Resolved as |
|---|---|
| `content://…` | opened directly (what this app records for a picked PDF) |
| `/abs/path.pdf` | opened as a file, if it exists on this device |
| `bg.pdf`, `scans/bg.pdf`, `../bg.pdf` | **relative** to the `.xopp`'s own folder |
| `domain="attach"` on a non-zip document | the `<name>.xopp.<filename>` sibling |

The last two need a folder, which `openSibling` derives from the source URI: a `file://` URI
relativises on the filesystem, a `content://` one on its SAF document id via
`DocumentsContract.buildDocumentUriUsingTree`/`buildDocumentUri`. Providers with **opaque** ids
(Downloads' `msf:1234`) have no path to relativise, so resolution returns null and the pages come up
blank with the existing "Background PDF not found" note — the reference itself is still written back
untouched on save, so nothing is silently dropped.

**Writing** (`DocumentIo.portableReference`, applied on every `ORIGINAL` save) rewrites the
reference to be relative to the save destination whenever the PDF sits in the same folder — matching
SAF document-id volumes, or two filesystem paths. It is a heuristic, not a preference: a resolvable
relative path is strictly better than a `content://` URI no desktop can read, and there is nothing
for the user to get wrong. Three cases are deliberately left alone: an **already relative**
reference (so a desktop-authored document round-trips byte-identically), an **attach** reference
(the ZIP path owns that), and anything that **won't relativise** (different folder, different
volume, opaque id) — those keep their absolute reference rather than becoming a broken relative one.

**Round-trip status.** The parse/serialize halves are locked in by fixtures in
`PdfBackgroundRoundTripTest` (a relative path under `domain="absolute"`, and an attach reference on
a non-zip document) and the derivation by `PdfReferenceTest`.

The **desktop check** was run against **Xournal++ 1.3.6** on Linux, headlessly, via its export CLI
(`xournalpp FILE.xopp -p out.pdf`, which renders the resolved background into the exported PDF — a
blank page means the reference didn't resolve). Results:

| Fixture | Desktop 1.3.6 |
|---|---|
| `domain="absolute"` + bare sibling filename (`bhm_prior.pdf`) | ✅ background renders |
| The multi-page shape this app writes — first page carries `filename`+`domain`, later pages only `pageno` | ✅ all 3 pages render the right PDF page, strokes on top |
| `domain="attach"` on a non-zip document, `<name>.xopp.bg.pdf` sibling | ✅ background renders |
| Unresolvable reference (`nope.pdf`) | ⚠️ desktop reports *"The background file … could not be found"* and aborts the export (exit 2) |

So the reference forms we read and write are exactly what desktop resolves, and the inheritance
shape (`filename` only on the first page) is accepted.

**An unresolvable reference survives a desktop save.** Verified interactively on the same 1.3.6
build (X11/openbox GUI): opening a `.xopp` whose background PDF is missing pops a *"Missing PDF
background file"* dialog offering **Select another PDF** / **Remove PDF Background** / **Cancel**.
Dismissing it with *Cancel* leaves the reference in place — the page renders as a grey sheet reading
"PDF background missing", strokes still draw on it, and after editing and saving, the `<background>`
element comes back **byte-identical**, filename and `domain` untouched. Confirmed for both shapes:
a bare relative sibling name (`missing-scan.pdf`) and a dead absolute path
(`/nonexistent/dir/gone.pdf`). Desktop only rewrites or drops the reference if the user explicitly
picks one of the other two buttons, so this app's "write the reference back untouched when it can't
be resolved" behaviour matches desktop exactly and a missing PDF never silently loses its link.

## Stylus & selection roadmap

The app is **stylus-first**. `DrawingSurfaceView.onTouchEvent` routes every pointer-down through the
pure `InputClassifier`, so the pen hardware — not the on-screen toolbar — decides what a gesture does,
matching desktop Xournal++. This section is the design home for the input layer and for finishing the
selection tool to desktop parity; work items are journaled in `TODO.toml` (via the `todo` skill).

**Stylus input — implemented.** Android reports the source of every pointer via
`MotionEvent.getToolType(pointerIndex)` (`TOOL_TYPE_STYLUS` / `_ERASER` / `_FINGER`) and stylus
side-buttons via `getButtonState()`. The view maps those onto the
device-independent `PointerKind` and calls the pure, JVM-tested **`InputClassifier`** (`PointerKind` +
barrel-pressed + `ActiveTool` + `InputSettings` → `GestureIntent`), keeping the decision logic off the
Android surface so it's unit-testable (`InputClassifierTest`). Precedence, "pen hardware wins over the
toolbar":

1. **Eraser tip.** A `TOOL_TYPE_ERASER` pointer (the flipped-over tip) erases whatever the tool.
2. **Barrel button.** The button held on a pen invokes a configurable action while
   held — default **erase**, or **select** / **none** (`BarrelAction`), whatever the on-screen tool.
   Which stream reports it depends on the pen and the platform, so detection is deliberately broad
   and latched (pure, JVM-tested **`BarrelButtonState`**, see `BarrelButtonStateTest`):
   `BUTTON_STYLUS_PRIMARY`/`_SECONDARY` on any pointer event; the barrel-button *key* events a
   Bluetooth pen sends on Android 14+ (`KEYCODE_STYLUS_BUTTON_PRIMARY`/`_SECONDARY`, routed from
   `MainActivity.dispatchKeyEvent` because the canvas only takes focus on touch); and a plain
   secondary click from a pen the tablet presents as a mouse (`PointerKind.UNKNOWN`, whose *primary*
   button is its tip and so can never be the barrel — the barrel therefore applies to
   `PointerKind.UNKNOWN` pointers too, or the setting would be dead on exactly the third-party pens
   whose owners report it as broken). A pen whose firmware picks its *own* key code is covered as
   well, and this is the case that mattered in practice: the **Honor Choice Pencil** presents as a
   Bluetooth **keyboard** (`src=0x101:KEYBOARD`) and sends its side button as key code **755**, a code
   Android does not define. `BarrelButtonState.isBarrelKey` therefore accepts, besides the documented
   codes, (a) a key code Android cannot **name** — computed by `isVendorKeycode`
   (`DrawingSurfaceInput.kt`) through `KeyEvent.keyCodeToString`, which answers `KEYCODE_<NAME>` for
   everything the platform defines and the bare number for everything it does not, so an unnamed code
   can only be firmware's own and never a real keyboard key — and (b) a key from a stylus-sourced
   device (`SOURCE_STYLUS_BIT`, shared by `SOURCE_STYLUS` and `SOURCE_BLUETOOTH_STYLUS`) carrying no
   printable character. Every character-producing key, and every named key from a keyboard, is left
   alone. Latching matters because a pen's press edge
   often lands on a hover event and is **not** repeated on the following `ACTION_DOWN` — reading only
   that event would classify the stroke as ink and holding the button would do nothing — while any
   event carrying the real bits overwrites the latch, which therefore can never stick in erase mode.
   A key-sourced press needs one rule beyond that: a pen whose button travels over Bluetooth does not
   mention it in the motion stream at all, so *every* event of the stroke carries no buttons and the
   usual "the motion stream says released" self-correction would drop the button the instant the tip
   landed. `BarrelButtonState.onMotion` therefore ignores a motion event that reports nothing while
   the latch came from a **key**, and that latch is only cleared by the key going up (or by `reset`,
   when the pen leaves hover range); a motion event that *does* report the button bit takes ownership
   back, so the latch can still never stick.
   The held state is re-read *during* a gesture as well: `switchStrokeToErase` turns the rest of an
   in-progress stroke into an erase, matching desktop Xournal++.
   A pen can also do its own gesture recognition: the Honor Choice Pencil swallows a single tap and
   sends **one** key event for a whole physical double-click, so waiting for a second press edge would
   mean the button never does anything. A press that arrives as an **unnamed vendor key** is therefore
   the whole gesture on its own, and `VendorClickGate` (pure, time-injected, `VendorClickGateTest`)
   collapses the edges of one gesture — one or two, depending on the firmware — into a **single**
   action, because two would cancel out a toggle and read as a dead button. Its window is Android's own
   double-tap timeout (300 ms): two whole double-clicks cannot fit inside it, while the two taps of one
   physical double-click always do, so it is safe on either kind of firmware and no real press is
   swallowed. Auto-repeat key presses are filtered before that. A firmware click is also exempt from
   the two rules a *held* barrel follows, both of which would have turned a flaky button into a dead
   one: it does not need the latch to **change** (a pen that never sends the key up would otherwise
   silence every later press for good), and it is not dropped while the pen is on the glass — the
   in-progress stroke is finished with `endGesture()` first, then the action runs, so the click a user
   makes right after erasing lands. The action it runs is recorded in the diagnostics log
   (`CLICK -> toggle eraser`, `CLICK swallowed (same gesture)`), so the panel shows what the app did
   with the press and not only what arrived.
   **`TOGGLE_ERASER` switches to the eraser the rail's slot is showing** (`preferredEraser`,
   `ToolGroups.kt`): whole-stroke by default, partial if that is what the user picked from the slot's
   menu — shared with the pen↔eraser keyboard shortcut so both toggles land on the same eraser.
   A *double-click* of the same button is a separate, button-only gesture: `BarrelClickDetector`
   (pure, time-injected, `BarrelClickDetectorTest`) recognises two press edges within 350 ms from
   the hover/generic event stream — never while the tip is down, so it can't interrupt a stroke —
   and it only ever sees *edges*: both streams a pen can report its button on pass through the one
   latch first, so a single press reported twice (a motion bit *and* a key event) is one edge, not a
   pair,
   and runs the configured `BarrelDoubleAction` (undo/redo on the surface; the tool and full-page
   toggles are handed to `EditorScreen` through `onBarrelDoubleClick`).
3. **Finger-draw gate.** With the Settings **"finger draws"** toggle off, a finger **always pans** and
   actuates no tool at all — pen, highlighter, eraser, text, selection and placement all become
   stylus-only. Palm-safe writing on non-stylus devices. It is **off by default** on a first launch
   (`AppSettings.fingerDraws = false`) so a resting hand never inks the page; the stored preference
   keeps whatever the user last chose.
4. Otherwise the on-screen tool's default intent.

**Pen diagnostics — measuring instead of guessing.** Because `PointerKind.UNKNOWN` and the key-code
paths above are claims about hardware the app cannot see, the input layer carries its own instrument
— and it is what settled the Honor pen rather than a guess: **☰ menu → Pen diagnostics** floats a live log over the canvas
(`ui/PenDiagnosticsPanel.kt`), fed by the pure, JVM-tested **`PenInputLog`** (`PenInputLogTest`) whose
lines are formatted by **`PenEventText`** — tool types, actions, button bits, the **raw pressure**
the tablet reports for the pointer, key codes with their names, and the input-device list with each
device's source flags (`DrawingSurfacePenDebug.kt` records the devices when the panel opens). Pressure
is part of the de-duplication key, so a press that never moves still shows up.

The same panel answers the other "it does nothing" question — *did my setting reach the pen at all* —
with `tracePenParameters()`: `applySettings` (and switching the panel on) logs one `PEN sens/mult/min/
base -> @p/w` line holding the surface's own pressure filter and the width it makes of a half press. A
slider moved in Settings either prints its new value there or never reached the drawing surface, which
turns a guess into a reading.

Every diagnostic figure is formatted in `Locale.ROOT` (not the device locale), so a pressure reads
`0.42` on every phone and the lines stay comparable with a pen's spec sheet. Every hook (`tracePenMotion` in the three motion handlers,
`tracePenKey` in `dispatchKeyEvent`) is a read-only observer placed *before* the routing, so what the
panel shows is what the app really receives, not what the routing made of it; identical consecutive
states are de-duplicated to a key so a long hover is one line, and snapshots are coalesced into one
UI pass (`postPenLogFlush`) so a 100 Hz pen cannot drive 100 recompositions a second. The panel lives
on the canvas rather than in Settings because a settings page layered over it would swallow the very
events being diagnosed — and the one question it exists to answer (does this pen's button reach the
app at all, and as what?) can only be answered where the pen is. Its state is
`EditorUiState.penDiagnostics` (deliberately not persisted — it is a debugging aid, not a
preference), its lines land in `PaneState.penDebugLines`, and back closes it like any other transient
overlay.

**Graphics tablets and ExpressKeys.** External USB or Bluetooth drawing tablets (Huion, Wacom, XP-Pen,
Gaomon) send their physical button presses as standard Android `KeyEvent`s. The input pipeline supports
them through two complementary paths:
1. **Direct 1-click detection:** in **Settings → Shortcuts**, `KeyDetectionDialog` listens via
   `Modifier.onPreviewKeyEvent` for the next `ACTION_DOWN` event, extracting the character code via
   `keyStringFromKeyEvent` (supporting `unicodeChar`, `displayLabel`, space, digits, numpad and letters) and assigning it
   immediately to any tool or colour shortcut. Manual text input remains available side by side.
2. **Runtime routing:** `MainActivity.dispatchKeyEvent` forwards unconsumed key events to
   `DrawingSurfaceView.dispatchKeyEvent`, which routes them to `onKeyPressed` in `EditorRegions.kt`, matching
   against `toolShortcutKeys` and `colorShortcutKeys`. `DrawingSurfaceView.onTouchEvent` also requests focus
   on touch down so the canvas maintains key focus.
3. **Fidelity and pressure harmonization:** graphics tablets with pressure-sensitive pens write with
   dynamic line widths governed by `PressureCurve`. Since geometric figures are drawn at a fixed nominal
   width, users can harmonize them by setting `AppSettings.defaultShapeSlot` under **Settings → Figures**
   (choosing a narrower slot like `S` to match handwriting pressure) or turning pressure sensitivity off
   in **Settings → Stylus** to ensure byte-identical uniform widths across both freehand and shapes.
4. **Touch & palm isolation:** with `AppSettings.fingerDraws = false`, tablets equipped with capacitive
   multi-touch reserve touch strictly for navigation (pan/zoom), preventing stray marks from a resting palm.

**First-launch onboarding.** Fresh installs display `OnboardingDialog.kt` over `EditorScreen`, controlled
by `AppSettings.hasSeenOnboarding`. Its four steps are stylus pressure and barrel-button configuration,
harmonizing figure sizes with handwriting pressure, graphics-tablet ExpressKeys, and **handedness**
(`OnboardingHandedness.kt`: right-handed — the default, Main Toolbar on the left — or left-handed, on the
right). The last step edits `AppSettings.toolbarPosition` live through `onToolbarPosition`, so the rail is
seen moving behind the dialog rather than described.

**Mouse wheel.** `ACTION_SCROLL` events from a mouse arrive in `onGenericMotionEvent` and are turned
into a vertical viewport move (`handleWheelScroll` → `ViewportState.scrollBy`, `WHEEL_SCROLL_DP` per
notch). Android reports wheel-down as a *negative* `AXIS_VSCROLL`, so the sign is flipped: wheel down
goes further down the document, the scrollbar's direction rather than a grab-the-paper pan. Zoom is
untouched and the same scroll clamps apply, so a wheel at a bound is left unconsumed
(`MouseWheelInputTest`, on-device — `adb input` can't inject a wheel).

**Palm rejection** is the stateful half, handled in the view around the classifier: the active
draw/erase gesture is *owned by a pointer id* (`gesturePointerId`) and only that pointer is sampled
(`addSamples` reads `pointerIndex`, never pointer 0), so a resting palm — a different pointer — can't
perturb the stroke. A stylus/eraser pointer arriving mid-gesture **takes over** any gesture a finger
started (`onPointerDown` → `abandonInProgress`), and once a stylus owns the stroke (`stylusOwner`)
extra finger/palm pointers are ignored rather than treated as a second-finger pan.

**Pressure** feeds width through the pure `PressureCurve`, which is desktop Xournal++'s own mapping
`max(minimumPressure, pressure × pressureMultiplier)` (`PenInputHandler::filterPressure`) with **no
curve and no wide floor of its own** — the two settings are the desktop's, defaulting to its own
`minimumPressure = 0.05` and `pressureMultiplier = 1.0`. Deviating there (a gamma curve, a 0.25 floor)
would make the same physical press store a different width than the desktop and a stroke would look
different in each app, which is exactly what this deliberately does not do. Pressure sensitivity
switched off returns the full width, matching a shape (`PressureCurveTest`).

**Stroke smoothing** sits between the raw `MotionEvent` samples and those page points, in
`StrokeSmoother.kt`, and is what makes Android handwriting look like the desktop's rather than a
ragged polyline. Two pure pieces:

- `StrokeSmoother` — per-stroke streaming filter over the view-pixel samples, reset in
  `startStroke` and fed by `addSamples`. It exponentially smooths position (α 0.55) and, harder,
  pressure (α 0.3), then **decimates** samples that moved less than `minStepPx` with less than 0.02
  pressure change. A decimated sample still advances the filter, but the decimation distance is
  measured from the last **emitted** point, so a run of sub-threshold steps that adds up to a real
  move still lands a vertex. The newest sample of every batch is `force`d through so the drawn line
  always reaches the pen. `minStepPx` is **scale-aware** (`StrokePrecision.stepPxFor(pxPerPt)`,
  passed to `reset` per stroke): the *tighter* of a 1.6 view-px noise floor and a 0.8 pt
  document-space ceiling. A fixed pixel radius meant a view pixel bought more page at low zoom, so
  a 4-column overview silently discarded ~4× the real pen movement — the "corners and jumps" bug.
- `StrokeSimplifier` — Ramer–Douglas–Peucker pass run once in `commitCurrent` over the finished
  freehand points, dropping vertices within its tolerance of their neighbours' chord. Shape-tool
  output is exact geometry and is exempt. Fewer vertices = smaller `.xopp` and fewer `drawLine`
  calls per redraw. The tolerance is **scale-aware**: `toleranceFor(pxPerPt, precision)` divides
  `TOLERANCE_PX` (0.35 view px) by the page's real pixels-per-point, so the detail thrown away
  stays sub-pixel *on screen* at every magnification — a fixed page-point budget is ~3 view px at
  8 px/pt and facets curves into visible straight segments. `pxPerPt` must be `PageBox.scale`
  (fit-to-width × user zoom), **not** the user zoom alone: on a large tablet fit-to-width is
  already 2–4 px/pt, so keying off the zoom leaves the budget that many times too coarse at 100%
  and below — the faceting bug this replaced. Below 1 px/pt that division runs the other way, so
  the budget is clamped at `TOLERANCE_PT` (0.35 pt): zooming out only ever *tightens* the budget.
- `StrokePrecision` — the user-facing **Stroke precision** setting (Economy / Balanced / High /
  Maximum). Its `factor` multiplies *both* budgets — the simplifier tolerance and the smoother's
  `minStepPx` — so one control moves the whole fidelity-vs-size trade. Both budgets also depend on
  the page's `PageBox.scale`, so `DrawingSurfaceView` computes them per stroke (`startStroke` and
  `commitCurrent`) rather than pinning them when the setting changes.

Both are covered by `StrokeSmootherTest`. **Hover** (`ACTION_HOVER_MOVE` from a
stylus, via `onHoverEvent`) draws a preview ring where the tip will land. All of these are settings in
`AppSettings`, persisted by `SettingsStore` (SharedPreferences) and pushed live onto the surface by
`EditorScreen.applySettings`; the on-device `StylusInputTest` drives synthetic tool-typed
`MotionEvent`s to prove the wiring (eraser tip, barrel erase, barrel double-click, finger-draw gate, palm rejection, and
that the same page-space gesture keeps its detail at 100% and zoomed out).
`AppSettings` also carries the **default tool** (`DEFAULT_TOOL_CHOICES` — pen/highlighter/eraser/hand),
which seeds `EditorScreen`'s active-tool state so a document opens in the user's chosen mode.

**Momentum scrolling.** A pan feeds each focus sample to the pure `VelocityEstimator`; on release the
view runs the release velocity (content-space, opposite the finger, clamped to the platform max)
through `Momentum.seed` and seeds the pure `Fling` with the result, then drives a `Choreographer` frame
loop that decays the velocity exponentially, scrolls `scrollY`/`scrollX` by each frame's step (clamped
by `maxScrollY()`/`maxScrollX()`), and re-`render()`s. It stops when the speed drops below a threshold
or both axes pin to a bound; a fresh touch, cancel, or detach halts it at once. `Momentum.seed` sets
the seed magnitude to `strength · REFERENCE_SPEED_PX · curve.factor(speed / REFERENCE_SPEED_PX)`, where
the `MomentumCurve` (`LINEAR`/`QUADRATIC`/`CUBIC`/`EXPONENTIAL`, user-selectable, default `QUADRATIC`)
shapes how hard a fast flick is rewarded. Every curve is pinned to `factor(0)=0` and `factor(1)=1`, so
a reference-speed flick coasts at its own speed on any curve and only the fall-off below / take-off
above the reference changes — a tiny flick barely drifts while a fast swipe flies many pages (the wide
dynamic range a plain linear scale lacked). **Only a one-finger pan flings:** a two-finger release
resets the estimator when the first finger lifts (to dodge the focus-point jump), so its
near-motionless single-finger tail carries no momentum — the intended feel. Both `Fling` and
`VelocityEstimator` are Android-free (we roll our own estimator because `VelocityTracker` returns
nothing for the synthetic events used in tests), so the kinematics are unit-tested on the JVM
(`FlingTest`, `VelocityEstimatorTest`) and stay frame-rate independent. Because `render()` re-emits
`onScrollChanged`, the scroll thumb tracks the glide live. A `panSensitivity` gain (the
`PanSensitivity` object in `Fling.kt`, default `1.0`) multiplies each pan delta before it hits
`scrollX`/`scrollY` — 1 tracks the finger one-to-one, `<1` pans slower, `>1` faster, `0` freezes the
document — and the same factor scales the seeded release velocity so the fling coasts at the pan's
visual rate.

**Multi-touch gesture shortcuts: tap with two fingers to undo, three to redo.** The tap tracker
already used for the double-tap gesture keeps a per-gesture high-water mark of concurrent pointers
(`multiTapMaxPointers`), so a gesture that never drew, panned or zoomed can be classified by **how
many** fingers it held: two → `undo()`, three → `redo()` on the release that takes the last finger
off the canvas. The count has to survive the lift order (`ACTION_POINTER_UP` while one finger stays
down), and the whole gesture is disqualified the moment a pointer moves past the slop
(`multiTapMoved`) or the pinch/scroll paths take it. The two actions are the surface's own
`undo()`/`redo()`, so the history, the toolbar buttons and the barrel-button shortcuts stay one
implementation. Both gestures hang off the one **`AppSettings.multiFingerShortcuts`** switch (on by
default, round-tripped by the settings backup and offered in **Settings → Shortcuts → Gestures**),
because a finger tap that silently rewrites the document is exactly the kind of thing a user must be
able to turn off.

**Out of scope: tilt / orientation.** The `.xopp` format stores only per-vertex width — it has no
place for stylus **tilt / orientation** (`AXIS_TILT` / `AXIS_ORIENTATION`), so tilt-driven width
can't round-trip through the file and is **out of scope** per the project's scope rule (we only
build features the `.xopp` format can represent — see `AGENTS.md`). None of the input layer changes
the file format — it's all input-layer behaviour, so it lives entirely in `render/`/`ui/` without
touching `format/`.

**Selection — desktop parity (shipped).** The tool now covers rectangle **and** lasso select
(separate members of the rail's select slot — `EditorTool.SELECT` / `LASSO_SELECT` — which
`applyTool` turns into the surface's `selectMode`/`lassoMode` pair),
tap-pick, move (including **across pages**), on-canvas **resize** (uniform, corner handles) and
**rotate** (top knob), **cut / copy / paste / duplicate**, and **recolour / re-width**. Every
transform is a pure `SelectionOps` op (`scale`/`rotate`/`restyle`/`moveToPage`/`addToTopLayer`
alongside `translate`/`delete`) and lasso containment is `SelectionTester.inPolygon`, all
JVM-tested. **Rotate is stroke-only by the scope rule:** a stroke bakes rotation into its vertex
coordinates and round-trips, but text/images have no rotation attribute and axis-aligned boxes, so
`rotate` leaves them untouched and the view shows the rotate knob only for an all-stroke selection.
Non-uniform resize is likewise avoided (a text box's font size is a single scalar), so resize is a
uniform scale that keeps every element representable.
