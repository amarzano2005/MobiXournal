# Architecture — the index

Authoritative home for **how the system works internally**: the Android application pipeline, the
shared `mobix-core` module, the `desktop/` window, the repository layout and the data paths. This file
is the **index**, not the content: each topic below is one spoke, and each spoke opens with the question
it answers — so a change loads one topic instead of the whole architecture.

> **What is installed and what is not.** Two shells, one core, one document format. The **Android
> editor** and the **`desktop/` PC window** both consume `mobix-core` and both open, paint, edit, save
> and export the same documents — ink, figures, living elements, text and the selection — so a codec, a
> ruling or an ink rule is never forked for the PC ([`desktop-workstation.md`](desktop-workstation.md),
> [`desktop-documents.md`](desktop-documents.md)). A document lives in the native `.mobix`;
> `.xopp` is the interchange format and the compatibility contract is [`xopp-bridge.md`](xopp-bridge.md)'s.
> There is no sync: the merge engine is library code with no caller ([`mobix-format.md`](mobix-format.md)
> owns it).

---

## The spokes

| Spoke | What it owns | Read when |
|---|---|---|
| [`repo-layout.md`](repo-layout.md) | the repository map: every module, package and file | you need to know where code lives |
| [`codebase.md`](codebase.md) | what it is built on, the shared-core rule, branding and identity | you ask what the project is or what must not cross into `mobix-core` |
| [`stack.md`](stack.md) | pinned tech stack: Kotlin, Compose M3, SurfaceView, PDFBox, SAF | you wonder why a dependency or framework is chosen |
| [`xopp-format.md`](xopp-format.md) | on-disk `.xopp` schema: containers, gzip XML, elements, round-trip fidelity | you read or write `.xopp` or debug Xournal++ compatibility |
| [`xopp-bridge.md`](xopp-bridge.md) | the `.xopp` compatibility contract, and the bridge that keeps it: synthesis, revival, the metadata block | you change how a document leaves as `.xopp` or comes back with its elements intact |
| [`mobix-format.md`](mobix-format.md) | native `.mobix`: why it exists, what it adds, where it sits in the app, engine work | you work on the native format or the streaming engine |
| [`mobix-schema-spec.md`](mobix-schema-spec.md) | the `.mobix` envelope: mimetype, manifest, `canvas.json`, pages | you read or write the container or the page/background shape |
| [`mobix-streaming-api.md`](mobix-streaming-api.md) | the published `.mobix` contract a second shell opens, reads and writes with | you call `mobix-core` from outside the engine |
| [`mobix-elements.md`](mobix-elements.md) | the element schema's four rules and the geometry elements: stroke, text, image, LaTeX | you add or change a geometry element |
| [`mobix-living-entities.md`](mobix-living-entities.md) | the living entities: table, plot, parametric shape, circuit, math block | you add or change a re-editable element |
| [`data-path.md`](data-path.md) | opening and saving: the container verdict, the encoder, the in-memory model | you change how a document's bytes are read, written or staged |
| [`document-session.md`](document-session.md) | the open document: remote staging, the tab session cache, split view | you change what holds a document while it is open |
| [`rendering.md`](rendering.md) | paint pipeline (vsync pacing), page rasterisation, PNG/SVG export | a change affects repainting, drawing or export |
| [`drawing-tools.md`](drawing-tools.md) | the stroke tools: shapes, styles, partial eraser, layers, hold-to-snap, tapping to delete, the plotter | you work on an active drawing or editing tool |
| [`element-authoring.md`](element-authoring.md) | authoring and placing non-stroke elements (text, LaTeX, image, living), and vertical space | you create, place or resize something that is not a stroke |
| [`shape-perfection-engine.md`](shape-perfection-engine.md) | the shape recogniser: dwell trigger, tremor filter, recognised classes and their thresholds | you change what a freehand stroke snaps to |
| [`pages-and-paper.md`](pages-and-paper.md) | ruling parameters, page templates, background chooser, bookmarks, page list | you change paper, background, or page management |
| [`selection.md`](selection.md) | Select/Lasso tools: marquee/lasso, handles, transforms, align & distribute | you change how elements are picked or transformed |
| [`stylus.md`](stylus.md) | the pointer as input: tool detection, the barrel button, pen diagnostics, graphics tablets, first-launch onboarding | you change what a stylus or pen *is* to the app |
| [`gestures.md`](gestures.md) | pointer motion: wheel, palm rejection, pressure → width, smoothing, hover, momentum, multi-finger taps, telemetry | you change how a pointer's movement is read |
| [`colours.md`](colours.md) | shared colour picker, palette state, desktop colour parity | you change colour selection or palettes |
| [`chrome.md`](chrome.md) | the editor screen, the rail's Colour & size slot and its sizing, the Material 3 dock | you change the editor's chrome or the rail |
| [`chrome-frame.md`](chrome-frame.md) | the bands around the page and the saved toolbar order | you change where the chrome sits |
| [`chrome-settings.md`](chrome-settings.md) | the phone's Settings shell: its areas, sidebar and search | you change how Settings is presented on the tablet |
| [`ui-modernization.md`](ui-modernization.md) | the design system: tokens, glass material, true-black depth, type, motion | you change the look of the chrome, on either shell |
| [`desktop-ui-layout.md`](desktop-ui-layout.md) | the PC window's layout: panes, strip placement, universal shortcuts | you change the window's arrangement |
| [`interface-contract.md`](interface-contract.md) | what every **new** UI element must satisfy: material, colour, geometry, glyphs, text, states, motion, keyboard, parity | you add anything to the interface |
| [`infinite-canvas.md`](infinite-canvas.md) | the file's geometry modes (vertical, horizontal, 2D) and how a chunked document exports as pages | you work on chunk geometry or export slicing |
| [`chunked-documents.md`](chunked-documents.md) | how a chunked document is stored, opened, written and queried as spatial chunks | you change chunk files, `canvas.json` or chunked saving |
| [`math-engine.md`](math-engine.md) | expression evaluation, numerical calculus, parametric curves, plot interaction | you work on mathematical plotting or formula parsing |
| [`audio.md`](audio.md) | audio recording (Opus in a `.mobix`, WAV for a `.xopp`), replay, sidecars | you touch audio annotation features |
| [`audio-inking-sync.md`](audio-inking-sync.md) | continuous audio-inking sync, point-level timestamps, karaoke replay | you work on synchronized audio playback |
| [`images.md`](images.md) | image import, pixmap background, path resolution | you change image handling or background pixmaps |
| [`pdf.md`](pdf.md) | PDF backgrounds, font caches, text selection, bookmarks outline | you change PDF rendering, text selection or export |
| [`text-and-markdown.md`](text-and-markdown.md) | text/markdown import, paginator, layout engine | you change text or markdown import |
| [`search.md`](search.md) | document search, handwriting index, recognition engines | you change search or recognition |
| [`testing.md`](testing.md) | unit test coverage, architectural guards, mechanical rules | you want to know what protects a change |
| [`desktop-workstation.md`](desktop-workstation.md) | the `desktop/` window: module, shell, page painting and the tool strips | you work on the PC window |
| [`desktop-documents.md`](desktop-documents.md) | what the window does with a file: opening and its refusals, saving and its refusals, the dirty dot, the crash snapshot | you change how a document is opened, saved or recovered on the PC |
| [`desktop-editing.md`](desktop-editing.md) | the window's editing surface: figures, vertical space, placing a living element, the selection | you change what a drag on the page does |
| [`desktop-testing.md`](desktop-testing.md) | what the `desktop/` suites render and assert, and how to run them | you add or change a desktop test |
| [`desktop-settings.md`](desktop-settings.md) | the window's Settings surface: the index, the row rule, and what each section answers | you add or change a desktop setting |
| [`desktop-tablet.md`](desktop-tablet.md) | the pen's two Windows streams: Wintab packets, Windows Ink, the dial | you change how the window reads the tablet |
| [`desktop-pen-feel.md`](desktop-pen-feel.md) | the pen's active area, the hand's pan, the measurement against Xournal++ | you change how the pen feels in the window |

Not part of the architecture index: [`tools.md`](tools.md) owns the external build/test/release
pipelines, [`tools-emulator.md`](tools-emulator.md) the emulator and device harness, and
[`releases/`](releases/) holds the per-version changelogs.

## How this stays honest

A fact has **one home**. Every other document links to it. The split is kept from decaying back into a
few fat documents — and from drifting into statements that are no longer true — by
`app/src/test/java/com/mobixournal/guards/DocsGuardTest.kt`, which fails the build when:

- **a spoke is not in the index above** — a doc nobody links is a doc nobody reads — or a row links to a
  file, or a `#anchor`, that does not exist (renaming a heading is a decision, not an accident);
- **a spoke passes 300 lines** — the point of a spoke is that one topic loads on its own, and 300 leaves
  room for the next sentence instead of forcing a rewrite of the whole file first;
- **a spoke does not open like the rest** — an `# H1`, then a `> Read when …` line saying who should open
  it;
- **two spokes claim one title or one section heading** — the same topic described twice is two homes for
  one fact, and one of them will go stale;
- **a sentence is pasted into two spokes** — a moved paragraph leaves a pointer, not a copy;
- **a doc names a code file that does not exist** — a rename, a deletion or a moved file shows up here
  rather than in a reader's wasted afternoon (host-only paths and elided `...` paths are exempt);
- **a doc names another doc that does not exist** — a reference to `docs/…md` anywhere in the docs, the two
  front doors or `TODO.toml` has to land on a spoke, because a scope that tells its reader to open a file
  that was split away sends them nowhere (`FINISHED.toml` is exempt: it records the past);
- **a doc names a task id** that neither `TODO.toml` nor `FINISHED.toml` holds;
- **a front door outgrows its job** — `README.md` and `AGENTS.md` carry a recorded line budget and a cap on
  one paragraph of running prose, because they sit outside `docs/` and had no rule at all: a front door
  *points at* its spokes, and a paragraph nobody can finish belongs in the one that owns it;
- **a file at the repository root is an orphan** — the root is the first thing a reader sees, so everything
  there is either infrastructure or named by a doc, a test or a script.

### Adding a spoke

A new topic is a new file, and this is the whole procedure:

1. Create `docs/<topic>.md` with an `# H1` naming the topic, a `> Read when …` line, and only that topic.
   Move prose here from the doc that used to hold it — do not restate it in both.
2. Leave a pointer where the prose was — one sentence ending in *is that spoke's*, with the file linked.
3. Add one row above: the file, what it owns, and when to read it. Keep the link relative.
4. If the topic is a whole new area (not a split of an existing one), add it to `AGENTS.md`'s map too,
   and say in `docs/testing.md` what now protects it.
5. Run `DocsGuardTest` — it will tell you if the index, an anchor, the budget or a duplicated heading is
   wrong before a reader has to.

And this index only names what exists: a spoke whose subject is not in the tree is deleted, not labelled
as future work.
