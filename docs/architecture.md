# Architecture — the index

Authoritative home for **how the system works internally**: the Android application pipeline, the
shared `mobix-core` module, the `desktop/` preview, the repository layout and the data paths. This file
is the **index**, not the content: each topic below is one spoke, and each spoke opens with the question
it answers — so a change loads one topic instead of the whole architecture.

> **What is installed and what is not.** The Android app is the product: it reads and writes lossless
> `.xopp` and native `.mobix`, with Compose chrome, low-latency `SurfaceView` stylus inking, PDF
> import/export, audio, search and the STEM tools. The `desktop/` module is a working **preview**: it
> opens every container the app writes, paints the pages, tabs, sidebar, zoom and both tool strips, but
> it does not edit, take ink or save. There is no sync: the merge engine is library code with no caller
> ([`mobix-format.md`](mobix-format.md) owns it).

---

## The spokes

| Spoke | What it owns | Read when |
|---|---|---|
| [`codebase.md`](codebase.md) | repository layout (every package and file), branding and identity | you need to know where code lives |
| [`stack.md`](stack.md) | pinned tech stack: Kotlin, Compose M3, SurfaceView, PDFBox, SAF | you wonder why a dependency or framework is chosen |
| [`xopp-format.md`](xopp-format.md) | on-disk `.xopp` schema: containers, gzip XML, elements, round-trip fidelity | you read or write `.xopp` or debug Xournal++ compatibility |
| [`mobix-format.md`](mobix-format.md) | native `.mobix` container, document layer, `.xopp` synthesis bridge, merge engine | you work on the native format, the bridge, or element preservation |
| [`mobix-schema-spec.md`](mobix-schema-spec.md) | the `.mobix` envelope: mimetype, manifest, `canvas.json`, pages | you read or write the container or the page/background shape |
| [`mobix-streaming-api.md`](mobix-streaming-api.md) | the published `.mobix` contract a second shell opens, reads and writes with | you call `mobix-core` from outside the engine |
| [`mobix-elements.md`](mobix-elements.md) | the element schema: strokes, text, images, living entities, preservation rules | you add or change an element type |
| [`data-path.md`](data-path.md) | open/save, remote staging, session cache, in-memory model | you change how documents are opened, saved, or staged |
| [`rendering.md`](rendering.md) | paint pipeline (vsync pacing), page rasterisation, PNG/SVG export | a change affects repainting, drawing or export |
| [`drawing-tools.md`](drawing-tools.md) | drawing and editing tools: shapes, styles, eraser, layers, hold-to-snap | you work on an active drawing or editing tool |
| [`shape-perfection-engine.md`](shape-perfection-engine.md) | the shape recogniser: dwell trigger, tremor filter, recognised classes and their thresholds | you change what a freehand stroke snaps to |
| [`pages-and-paper.md`](pages-and-paper.md) | ruling parameters, page templates, background chooser, bookmarks, page list | you change paper, background, or page management |
| [`selection.md`](selection.md) | Select/Lasso tools: marquee/lasso, handles, transforms, align & distribute | you change how elements are picked or transformed |
| [`stylus.md`](stylus.md) | input layer: tool detection, palm rejection, pressure → width, smoothing | you change stylus pointer handling or touch gestures |
| [`colours.md`](colours.md) | shared colour picker, palette state, desktop colour parity | you change colour selection or palettes |
| [`chrome.md`](chrome.md) | editor screen layout, rail, top bar, menus, settings screen | you change chrome UI or app settings |
| [`ui-modernization.md`](ui-modernization.md) | the design system: tokens, glass material, rail/toolbar geometry, type, motion | you change the look of the chrome, on either shell |
| [`interface-contract.md`](interface-contract.md) | what every **new** UI element must satisfy: material, colour, geometry, glyphs, text, states, motion, keyboard, parity | you add anything to the interface |
| [`infinite-canvas.md`](infinite-canvas.md) | infinite canvas modes (vertical, horizontal, 2D), spatial chunking, auto-pagination | you work on canvas scrolling or pagination boundaries |
| [`math-engine.md`](math-engine.md) | expression evaluation, numerical calculus, parametric curves, plot interaction | you work on mathematical plotting or formula parsing |
| [`audio.md`](audio.md) | audio recording (Opus in a `.mobix`, WAV for a `.xopp`), replay, sidecars | you touch audio annotation features |
| [`audio-inking-sync.md`](audio-inking-sync.md) | continuous audio-inking sync, point-level timestamps, karaoke replay | you work on synchronized audio playback |
| [`images.md`](images.md) | image import, pixmap background, path resolution | you change image handling or background pixmaps |
| [`pdf.md`](pdf.md) | PDF backgrounds, font caches, text selection, bookmarks outline | you change PDF rendering, text selection or export |
| [`text-and-markdown.md`](text-and-markdown.md) | text/markdown import, paginator, layout engine | you change text or markdown import |
| [`search.md`](search.md) | document search, handwriting index, recognition engines | you change search or recognition |
| [`testing.md`](testing.md) | unit test coverage, architectural guards, mechanical rules | you want to know what protects a change |
| [`desktop-workstation.md`](desktop-workstation.md) | the `desktop/` preview: module, window shell, page painting, tool strips, settings | you work on the PC window |
| [`desktop-tablet.md`](desktop-tablet.md) | the desktop pen and dial: Wintab packets, Windows Ink, the ink-latency probe and its measurements | you change how the window reads the tablet |

Not part of the architecture index: [`tools.md`](tools.md) owns the external build/test/release
pipelines, and [`releases/`](releases/) holds the per-version changelogs.

## How this stays honest

The split is kept from silently decaying back into one document by
`app/src/test/java/com/mobixournal/guards/DocsGuardTest.kt`, which fails the build when:

- a doc in `docs/` is not named in `AGENTS.md`'s map (an unreachable spoke),
- a relative link between docs points at a file, or a `#anchor`, that does not exist,
- a doc grows past its **line budget** (400 lines) — the point of a spoke is that a single topic
  can be loaded on its own.

And this index only names what exists: a spoke whose subject is not in the tree is deleted, not labelled
as future work. When you write, put a fact in the spoke that owns its topic, link to the owning spoke
rather than restating it, and if no spoke owns the topic, add one here **and** to `AGENTS.md`'s map.
