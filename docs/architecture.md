# Architecture — the index

Authoritative home for **how the system works internally**: the `.xopp` format mapping, the
read/write data path, the core components, the repository layout, and the load-bearing design
decisions. This file is the **index**, not the content: each topic below is one spoke, and each
spoke opens with the question it answers — so a change loads one topic instead of the whole
architecture.

> Status: the lossless `.xopp` format core and the Compose/`SurfaceView` editor are implemented,
> including PDF import and export. Record design decisions in the spoke that owns the topic, so
> they aren't re-litigated.

## The spokes

| Spoke | What it owns | Read when |
|---|---|---|
| [`xopp-format.md`](xopp-format.md) | the on-disk schema: containers, MIME type, what the open path accepts, units, colour encoding, the element tree, round-trip hazards | you read or write the file format, or anything that has to survive desktop Xournal++ |
| [`stack.md`](stack.md) | the pinned stack and the reason behind each choice: language, Compose/Material 3, the chrome's two colour roles, the SurfaceView, one width source, no third-party I/O library, PDFBox, SAF, the container build | you wonder why a dependency, framework or chrome colour is the way it is |
| [`codebase.md`](codebase.md) | prior art and the reference clone, the repository layout (every package and file), branding and identity | you need to know what this project is, or where a piece of code lives |
| [`data-path.md`](data-path.md) | open/save, remote staging, the tab/session cache, split view as panes, the in-memory model | you change how a document is opened, saved, staged or shared between panes |
| [`rendering.md`](rendering.md) | the paint pipeline (vsync pacing), page rasterisation, exporting one page as PNG/SVG | a change affects repainting, drawing a page, or exporting a page image |
| [`drawing-tools.md`](drawing-tools.md) | the tools and what they do to the page: shapes and styles, the eraser, layers, hold-to-snap, function plots, vertical space, tap-to-delete | you work on a drawing or editing tool |
| [`pages-and-paper.md`](pages-and-paper.md) | ruling parameters, page templates, the background chooser, bookmarks, the page list | you change a page's paper, its bookmark, or the page list |
| [`selection.md`](selection.md) | the Select/Lasso tools: marquee and lasso, the outline and its handles, move/resize/rotate, align & distribute, the region (background-copy) variant | you change how elements are picked or transformed |
| [`stylus.md`](stylus.md) | the input layer: tool detection and precedence, pen diagnostics, tablets, palm rejection, pressure → width, smoothing, hover, momentum, gestures, what is out of scope | you change anything a pointer does |
| [`colours.md`](colours.md) | the shared colour picker and palette state, and desktop colour parity | you change a colour or the picker |
| [`chrome.md`](chrome.md) | the editor screen's layout, the rail, the top bar, the one menu material, the settings screen and its search | you change chrome layout, a toolbar, a pop-up or a setting |
| [`images.md`](images.md) | image import, painting a pixmap background, resolving and storing its reference | you change image import or a pixmap background |
| [`pdf.md`](pdf.md) | PDF backgrounds and their caches, fonts in generated PDFs, text selection, the Contents outline, relative references, the retired on-screen vector path | you change anything PDF |
| [`text-and-markdown.md`](text-and-markdown.md) | the text/markdown import path, the paginator, the markdown parser and layout, the PDFs those imports are annotated against | you change a text or markdown import |
| [`audio.md`](audio.md) | recording, replay and the `.wav` sidecars a stroke can reference | you touch audio annotation |
| [`search.md`](search.md) | document search, the handwriting index, the two recognition engines | you change search or recognition |
| [`testing.md`](testing.md) | what the unit tests cover, and which rules the build enforces mechanically | you want to know what protects a change, or how to make a rule stick |

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
