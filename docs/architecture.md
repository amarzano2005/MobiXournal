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
| [`audio.md`](audio.md) | audio recording (Opus in a `.mobix`, WAV for a `.xopp`), replay, sidecars linked to strokes | you touch audio annotation features | **Implemented** |
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
| **Step 1** | [`mobix-format.md`](mobix-format.md) | technical design for native `.mobix` container, living domain entities, and `.xopp` bridge | you work on the next-gen file format, serialization, or bridge | **Implemented** |
| **Step 1** | [`mobix-schema-spec.md`](mobix-schema-spec.md) | the `.mobix` container and document envelope (mimetype, manifest, canvas, pages) | you read or write the container, the envelope, or the page/background shape | **Implemented** |
| **Step 1** | [`mobix-elements.md`](mobix-elements.md) | the element schema: strokes, text, images, living entities, preservation rules | you add or change an element type inside a `.mobix` document | **Implemented** (Codecs & Synthesis live; living editors landing in Step 2) |
| **Step 2** | [`ui-modernization.md`](ui-modernization.md) | lightweight UI design system, floating glass capsules, bimanual ergonomics, 1-tap STEM | you modernize the mobile interface or implement floating toolbars | **Partly implemented** (Tokens, theme & layout bands live; Radial HUD & micro-inspectors active in Step 2) |
| **Step 3** | [`desktop-workstation.md`](desktop-workstation.md) | desktop Compose Multiplatform architecture, Skia GPU rendering, and tablet/dial hardware | you work on desktop PC workstation support, Wacom/Huion, or dials | **Planned Spec** |

The statuses above are per **spoke**, not per feature: they say whether a doc describes shipped
behaviour or a specification. Step 1's data model, engines and runtime validations are now fully closed
(archived in `FINISHED.toml` under `step-1-mobix-format-engines`). In `format/mobix/`, the container
(`MobixContainer`), the shared JSON layer (`format/json/`), the manifest and `canvas.json` codecs, the element
codecs for all geometry elements and living families (tables, plots, parametric shapes, circuits), and the
document layer on top (`MobixDocument`: `.xopp` ingested as `.mobix` and read back, [mobix-format.md](mobix-format.md))
are live and tested. Bidirectional compilation via `render/XoppSynthesis.kt` and `render/LivingInk.kt` serves as
the single dispatch across canvas (`PageRenderer`), vector PDF (`PdfVectorPainter`), and SVG (`PageSvgWriter`),
with verified round-trip preservation (`MobixMetaRevival`).

All runtime engines delivered in Step 1 are live and verified:
- **Streaming & incremental I/O**: `PageSource` and `MobixPagedReader` stream large multi-page notebooks via LRU cache, while `MobixIncrementalSaver` performs constant-time delta writes.
- **Infinite canvas**: vertical strip, horizontal strip, and 2D freeform whiteboard modes open, draw, and slice to pages on export ([infinite-canvas.md](infinite-canvas.md)).
- **Synchronized audio-inking**: Opus bundled recording, continuous playhead synchronization, interactive karaoke playback animation, and point-precise audio scrub/tap ([audio-inking-sync.md](audio-inking-sync.md)).
- **Reactive math & plots**: parameter sliders, definite integrals, and analytical summaries with shaded area fills ([math-engine.md](math-engine.md)).

Work is now focused on **Step 2 (`step-2-modern-mobile-ui`)** in `TODO.toml`: modern mobile UI (design system, pen-tip radial HUD, edge-to-edge geometry contracts), living in-place entity editors (plot flyout, table grid, circuits, parametric shapes), 300 ms pen-dwell shape perfection ([shape-perfection-engine.md](shape-perfection-engine.md)), on-device LaTeX/Typst typesetting ([latex-typst-rendering.md](latex-typst-rendering.md)), snap-to-text magnetic PDF highlighting ([pdf-annotation-engine.md](pdf-annotation-engine.md)), and multi-window/DeX document state sync ([multi-window-workspace.md](multi-window-workspace.md)).

### Advanced domain engine specifications

| Spoke | What it owns | Read when | Status |
|---|---|---|---|
| [`infinite-canvas.md`](infinite-canvas.md) | infinite canvas modes (vertical, horizontal, 2D), spatial chunking, auto-pagination | you work on canvas scrolling, pagination boundaries or pageless modes | **Implemented** — vertical, horizontal, and 2D board modes open, render, take ink, and auto-slice on export |
| [`math-engine.md`](math-engine.md) | expression evaluation, numerical calculus (roots, extrema, derivatives), parametric curves | you work on mathematical plotting, formula parsing or calculus features | **Implemented** — AST evaluation, calculus algorithms, shaded area wash, and interactive parameter sliders |
| [`shape-perfection-engine.md`](shape-perfection-engine.md) | hold-to-straighten pen dwell gestures, geometric recognition, parametric handles | you work on shape recognition, gesture dwell detection or handles | **Planned Spec** (Active in Step 2 TODO) |
| [`latex-typst-rendering.md`](latex-typst-rendering.md) | on-device LaTeX and Typst typesetting engines, living formula blocks, offline math rendering | you work on mathematical typography, LaTeX, or Typst features | **Planned Spec** (Active in Step 2 TODO) |
| [`pdf-annotation-engine.md`](pdf-annotation-engine.md) | advanced PDF annotations: snap-to-text highlighter, native ISO PDF/A annotations | you work on PDF text snapping, search indexing or native annotation export | **Planned Spec** (Active in Step 2 TODO) |
| [`audio-inking-sync.md`](audio-inking-sync.md) | continuous audio-inking sync, point-level timestamps, inking karaoke scrubbing | you work on synchronized audio playback or speech-to-stroke features | **Implemented** — bundled Opus/WAV recording, karaoke replay, playhead scrubbing, and audio-tap seeking |
| [`plugin-system.md`](plugin-system.md) | plugin runtime architecture (Lua, WASM, Kotlin), extension APIs, custom exporters | you add extensibility hooks, scripting or third-party integrations | **Planned Spec** |
| [`sync-and-storage.md`](sync-and-storage.md) | conflict-free multi-device synchronization, granular page merges, cloud-agnostic storage | you work on file synchronization (Nextcloud, Syncthing) or conflict resolution | **Planned Spec** |
| [`multi-window-workspace.md`](multi-window-workspace.md) | multi-window workspaces, side-by-side split view, DeX/multi-monitor support, drag and drop | you work on multi-window coordination, split view or drag and drop | **Planned Spec** (Active in Step 2 TODO) |

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
