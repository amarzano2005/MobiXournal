# Architecture — the index

Authoritative home for **how the system works internally**: the active Android application
pipeline, repository layout, data paths, and the technical specifications for future evolution.
This file is the **index**, not the content: each topic below is one spoke, and each spoke opens
with the question it answers — so a change loads one topic instead of the whole architecture.

> **Status & Implementation State:**
> - **Active Production System**: The Android app is a fully functional companion reading and writing
>   lossless `.xopp` files, with Jetpack Compose Material 3 chrome, low-latency `SurfaceView` stylus
>   inking, and PDF import/export.
> - **Roadmap & Evolution Specifications**: Technical design specifications for the upcoming native
>   `.mobix` format, modernized bimanual mobile UI, and desktop workstation app are documented in dedicated
>   spokes to guide development without blurring the current codebase reality.

---

## Active core architecture (Currently implemented)

The following spokes describe the **active Android codebase** (`com.mobixournal.*`) as implemented today:

| Spoke | What it owns | Read when | Status |
|---|---|---|---|
| [`codebase.md`](codebase.md) | repository layout (every package and file), branding and identity | you need to know where code lives | **Implemented** |
| [`stack.md`](stack.md) | pinned tech stack: Kotlin, Compose M3, SurfaceView, PDFBox, SAF | you wonder why a dependency or framework is chosen | **Implemented** |
| [`xopp-format.md`](xopp-format.md) | on-disk `.xopp` schema: containers, gzip XML, elements, round-trip fidelity | you read or write `.xopp` or debug Xournal++ compatibility | **Implemented** |
| [`data-path.md`](data-path.md) | open/save, remote staging, session cache, in-memory model | you change how documents are opened, saved, or staged | **Implemented** |
| [`rendering.md`](rendering.md) | paint pipeline (vsync pacing), page rasterisation, PNG/SVG export | a change affects repainting, drawing or export | **Implemented** |
| [`drawing-tools.md`](drawing-tools.md) | drawing and editing tools: shapes, styles, eraser, layers, hold-to-snap | you work on an active drawing or editing tool | **Implemented** |
| [`pages-and-paper.md`](pages-and-paper.md) | ruling parameters, page templates, background chooser, bookmarks, page list | you change paper, background, or page management | **Implemented** |
| [`selection.md`](selection.md) | Select/Lasso tools: marquee/lasso, handles, transforms, align & distribute | you change how elements are picked or transformed | **Implemented** |
| [`stylus.md`](stylus.md) | input layer: tool detection, palm rejection, pressure → width, smoothing | you change stylus pointer handling or touch gestures | **Implemented** |
| [`colours.md`](colours.md) | shared colour picker, palette state, desktop colour parity | you change colour selection or palettes | **Implemented** |
| [`chrome.md`](chrome.md) | editor screen layout, rail, top bar, menus, settings screen | you change chrome UI or app settings | **Implemented** |
| [`images.md`](images.md) | image import, pixmap background, path resolution | you change image handling or background pixmaps | **Implemented** |
| [`pdf.md`](pdf.md) | PDF backgrounds, font caches, text selection, bookmarks outline | you change PDF rendering, text selection or export | **Implemented** |
| [`text-and-markdown.md`](text-and-markdown.md) | text/markdown import, paginator, layout engine | you change text or markdown import | **Implemented** |
| [`audio.md`](audio.md) | audio recording, replay, `.wav` sidecars linked to strokes | you touch audio annotation features | **Implemented** |
| [`search.md`](search.md) | document search, handwriting index, recognition engines | you change search or recognition | **Implemented** |
| [`testing.md`](testing.md) | unit test coverage, architectural guards, mechanical rules | you want to know what protects a change | **Implemented** |

---

## Future evolution & Next-Gen specifications (Roadmap)

The following spokes define the **technical architecture for future milestones**.
The high-level evolution is coordinated in [`future-roadmap.md`](future-roadmap.md) along three primary steps:

### The 3 strategic pillars

| Step | Spoke | What it owns | Read when | Status |
|---|---|---|---|---|
| **Master** | [`future-roadmap.md`](future-roadmap.md) | strategic 3-step architecture roadmap coordinating data, mobile, and desktop | you plan or review the next-generation architecture hierarchy | **Roadmap** |
| **Step 1** | [`mobix-format.md`](mobix-format.md) | technical design for native `.mobix` container, living domain entities, and `.xopp` bridge | you work on the next-gen file format, serialization, or bridge | **Partly implemented** |
| **Step 1** | [`mobix-schema-spec.md`](mobix-schema-spec.md) | the `.mobix` container and document envelope (mimetype, manifest, canvas, pages) | you read or write the container, the envelope, or the page/background shape | **Partly implemented** |
| **Step 1** | [`mobix-elements.md`](mobix-elements.md) | the element schema: strokes, text, images, living entities, preservation rules | you add or change an element type inside a `.mobix` document | **Partly implemented** |
| **Step 2** | [`ui-modernization.md`](ui-modernization.md) | lightweight UI design system, floating glass capsules, bimanual ergonomics, 1-tap STEM | you modernize the mobile interface or implement floating toolbars | **Planned Spec** |
| **Step 3** | [`desktop-workstation.md`](desktop-workstation.md) | desktop Compose Multiplatform architecture, Skia GPU rendering, and tablet/dial hardware | you work on desktop PC workstation support, Wacom/Huion, or dials | **Planned Spec** |

The statuses above are per **spoke**, not per feature: they say whether a doc describes shipped
behaviour or a specification. Step 1 is the one with code behind it so far: in `format/mobix/`, the
container (`MobixContainer`), the shared JSON layer (`format/json/`), the manifest and `canvas.json`
codecs, the element codecs for every element the format can hold — the four `.xopp`-shaped ones in
`MobixElementCodec`, the four living families (tables, plots, parametric shapes, circuits) in their own
codecs — and the document layer on top (`MobixDocument`: a `.xopp` ingested as a `.mobix` document and
read back, [mobix-format.md](mobix-format.md)) exist and are tested, and so does the other half of the
compiler: `render/XoppSynthesis.kt` flattens a document holding living entities into ink the ordinary
`.xopp` writer emits, one compiler per family. The app-level open/save path has since landed — the type
is registered, a `.mobix` is dispatched by its own `mimetype` and saved back in its own format
([data-path.md](data-path.md)) — and the export already carries the verified `mobix:meta` block, which is
what lets an exported `.xopp` come back as living entities ([mobix-format.md](mobix-format.md)). That whole
list has since closed: a `.mobix` whose `minAppVersion` is above ours offers a **read-only open**
(`MobixManifest.requiresAppNewerThan` is asked by `DocumentIo`, and `MainActivityDocuments` opens the tab
read-only rather than risk newer data), and the export needs no entry point of its own because every
`.xopp` write already goes through the synthesis. The living entities **are drawn**: `render/LivingInk.kt`
is the single dispatch the canvas rasteriser (`PageRenderer`), the PDF painter (`PdfVectorPainter`) and the
SVG writer (`PageSvgWriter`) all compile an entity through, so a `.mobix` holding a table shows the table
on screen and in an export, and the plot is editable in place ([mobix-elements.md](mobix-elements.md) owns
that one-family-at-a-time sequence). What Step 1 still owes is the *Step 1 remainder* epic in `TODO.toml`:
the infinite canvas ([infinite-canvas.md](infinite-canvas.md)), continuous audio-inking sync
([audio-inking-sync.md](audio-inking-sync.md)), the math engine's parameter sliders
([math-engine.md](math-engine.md)) and the desktop Lua companion plugin ([mobix-format.md](mobix-format.md)).

### Advanced domain engine specifications

| Spoke | What it owns | Read when | Status |
|---|---|---|---|
| [`infinite-canvas.md`](infinite-canvas.md) | infinite canvas modes (vertical, horizontal, 2D), spatial chunking, auto-pagination | you work on canvas scrolling, pagination boundaries or pageless modes | **Planned Spec** |
| [`math-engine.md`](math-engine.md) | expression evaluation, numerical calculus (roots, extrema, derivatives), parametric curves | you work on mathematical plotting, formula parsing or calculus features | **Planned Spec** |
| [`shape-perfection-engine.md`](shape-perfection-engine.md) | hold-to-straighten pen dwell gestures, geometric recognition, parametric handles | you work on shape recognition, gesture dwell detection or handles | **Planned Spec** |
| [`latex-typst-rendering.md`](latex-typst-rendering.md) | on-device LaTeX and Typst typesetting engines, living formula blocks, offline math rendering | you work on mathematical typography, LaTeX, or Typst features | **Planned Spec** |
| [`pdf-annotation-engine.md`](pdf-annotation-engine.md) | advanced PDF annotations: snap-to-text highlighter, native ISO PDF/A annotations | you work on PDF text snapping, search indexing or native annotation export | **Planned Spec** |
| [`audio-inking-sync.md`](audio-inking-sync.md) | continuous audio-inking sync, point-level timestamps, inking karaoke scrubbing | you work on synchronized audio playback or speech-to-stroke features | **Planned Spec** |
| [`plugin-system.md`](plugin-system.md) | plugin runtime architecture (Lua, WASM, Kotlin), extension APIs, custom exporters | you add extensibility hooks, scripting or third-party integrations | **Planned Spec** |
| [`sync-and-storage.md`](sync-and-storage.md) | conflict-free multi-device synchronization, granular page merges, cloud-agnostic storage | you work on file synchronization (Nextcloud, Syncthing) or conflict resolution | **Planned Spec** |
| [`multi-window-workspace.md`](multi-window-workspace.md) | multi-window workspaces, side-by-side split view, DeX/multi-monitor support, drag and drop | you work on multi-window coordination, split view or drag and drop | **Planned Spec** |

---

Not part of the architecture index: [`tools.md`](tools.md) owns the external build/test/release
pipelines, and [`releases/`](releases/) holds the per-version changelogs.

## How this stays honest

The split is kept from silently decaying back into one document by
`app/src/test/java/com/mobixournal/guards/DocsGuardTest.kt`, which fails the build when:

- a doc in `docs/` is not named in `AGENTS.md`'s map (an unreachable spoke),
- a relative link between docs points at a file, or a `#anchor`, that does not exist,
- a doc grows past its **line budget** (400 lines) — the point of a spoke is that a single topic
  can be loaded on its own.

So when you write: put a fact in the spoke that owns its topic, link to the owning spoke rather
than restating it, and if no spoke owns the topic, add one here **and** to `AGENTS.md`'s map.
