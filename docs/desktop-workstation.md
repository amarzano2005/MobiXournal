# Desktop workstation: the `desktop/` window

> Read when you change the desktop Compose Multiplatform window: its shell and chrome, its tool strips, how
> it opens a document, or how it paints a page.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What the window is

A **`desktop` Gradle module** (Compose Multiplatform 1.7 on Skiko) that opens the app's own documents and
paints their pages in a native window. Every word the window shows is **English**, like the tablet app's UI
— [`ui-modernization.md`](ui-modernization.md) owns that rule. It is the feasibility proof this spoke was
written against: the document model and the codecs are platform-free, so a PC window can show the same
page the Android editor shows. Windows 11 is the only platform it is built and run on.

What it does today:
- Opens **every container the app writes** — gzip `.xopp`, self-contained ZIP `.xopp` and native
  `.mobix` — from the toolbar or a path argument, lists its pages, and zooms 25–200 %. The container is
  chosen by the file's **bytes** (`FileKind` plus a ZIP's `mimetype` entry), never by the extension, so a
  renamed notebook still opens as a notebook.
- **Navigates like a workstation, not like a phone**: a header carrying one tab per open document (each
  tab keeps its own page, zoom and fit, so going back to a tab lands where you left it), a collapsible
  240 dp sidebar of page thumbnails and the current page's layers, the page alone on the true-black desk,
  and a 24 dp status bar with the page counter, the effective zoom, the fit mode and the container. The
  chrome is the app's **own glass** (the shared tokens, `DesktopTheme`) over a `#000000` desk: a 40 dp
  header carrying 16 dp tab capsules, 1.5 dp monoline glyphs, Inter with tabular figures, one cast shadow
  under each band, and one under the sheet — `docs/ui-modernization.md` owns the material and
  `DesktopThemeTest` pins it.
  Wheel scrolls, `Ctrl` + wheel zooms **at the cursor**, middle-drag or `Space` + drag pans, and every one
  of those actions also has a key (`docs/ui-modernization.md` owns the table). A tab opens in **`AUTO`
  fit**, which asks what the page *is*: a sheet (A4 is 1.41 high) is shown whole, and a page saved as a
  long strip — the tablet writes one 850 × 2976 pt page for a lecture that fills the sheet — is fitted to
  its **width**, because showing that whole page lands at 27 %, where a 12 pt hand is three pixels tall and
  two thirds of the window is black desk. The status bar names the fit actually in use; `Ctrl + 0` still
  means the whole sheet, however small that makes it, and scrolling never abandons the fit (a width-fitted
  page must stay fitted while you read down it). Thumbnails come from the
  same `PageView` that paints the page — a preview cannot disagree with what it previews — and are cached
  once per page per tab. The sidebar's layer list carries an **eye per layer** (`LayerEye`, monoline like
  the rest of the chrome): switching one off skips that layer in the page painter *and* in the thumbnail,
  and the layer stays listed in the muted ink — switched off, not gone. That is **view** state, per tab and
  per page (`DesktopTab.hiddenOn` / `toggleLayer`): nothing is written to the document, and a second press
  brings the layer back. A hidden layer's thumbnail is painted fresh instead of taking the page cache, so a
  preview can never show a layer the sheet beside it is missing.
- Shows a `.mobix` **page by page**: the list is lazy and pages are inflated only as they scroll into
  view, so a notebook opens without reading it. A pressed-for stroke count is therefore not shown for
  those documents — the header says nothing about a count instead of showing a wrong one.
- Paints solid/ruled/graph/dotted/**isometric** paper (`config`'s `r1`, else `BackgroundGrid`'s defaults;
  an isometric sheet strokes the segments `BackgroundGrid.isometric` returns — the same mesh the tablet
  and the SVG/PDF exports draw — so a ±30° sheet is ruled by shared geometry and not a second grid),
  pen strokes with their pressure taper, highlighter bands with multiply, dashed/dotted line styles, and
  text boxes.
- **Paints living entities** — tables, function plots, parametric shapes and circuit components — by
  running the same compiler the Android canvas and every export use (`LivingInk`, now platform-free in
  `mobix-core`), then drawing the ink it produces. A table on the PC is the table on the tablet, not a
  second renderer's opinion of it; an entity this build has no drawing for is reported, not drawn blank.
- **Paints page pictures**: a pixmap background from the file the page names, and an annotated **PDF
  page** rasterised with PDFBox (about 144 dpi) and placed under the ink, so a highlighter still
  multiplies into it. A `.mobix`'s bundled bytes are preferred over the original reference, which is
  what keeps a shared notebook looking right after the picture's file has moved away.
- **Shows both tool placements**: a **52 dp rail capsule in a 64 dp lane** beside the page (the
  default, the tablet's own geometry and air) and a **48 dp ribbon capsule in a 56 dp band** under the
  header, switched from the header's **Tool placement** control (Rail / Ribbon / Both) and remembered in
  the window's settings file, so the choice survives a restart. The lane is **air, not a second sheet of
  glass**: painting it too made 0.85 over 0.85, and the rail read #161618. The strip is a **capsule** with
  its own 6 dp lift and the family's rim, so its fill is the tablet's #131314 (+1 rounding), and each
  header control is the same object at 36 dp. Both placements draw the same fifteen tools, in the order,
  glyphs and shortcuts of [`ui-modernization.md`](ui-modernization.md) §3, with the picked slot a 10 dp
  squircle in the steel `#38383E` and a name-and-hotkey pill on hover. A slot only sets the tool
  selection — nothing draws with it yet.
- **One material and one icon set, not a lookalike**: every band (header, status bar, sidebar) takes the
  band lift instead of a bare fill, every surface that floats takes the drawn rim, and
  both keep the **shared fill with no gradient of the window's own** — a sheen over the bands made the
  same charcoal read a shade lighter on the PC. Glyphs are drawn in the chrome's ink (white, the tablet's
  `#FFFFFF`), with the picked slot in the steel and its glyph in the accent. The fifteen tool glyphs are
  no longer the window's own art: they come from `ToolGlyphCatalogue` in `mobix-core` — plain geometry on
  the 24-unit grid — which the tablet turns into an `ImageVector` and this shell draws into a
  `DrawScope`, so the PC's pen and the tablet's pen are the same strokes. `ChromeMaterialRenderTest`
  proves the lift, the flat fill and the rim as pixels, and `ToolGlyphCatalogueTest` pins the geometry
  both shells read.
- Consumes **`mobix-core`**, the platform-free module that holds the document model, the XML/JSON
  layers, the `.xopp` and `.mobix` codecs, and the shared paper/ink geometry — one codec, one ruling and
  one ink rule book rather than a fork.
- The `.mobix` engine lives in `mobix-core` too, behind a published streaming contract
  ([`mobix-streaming-api.md`](mobix-streaming-api.md)): the paged reader, the asset sources, the page
  and element codecs, the incremental save and the merge are callable from this module, and
  `DesktopMobixOpenTest` opens a 40-page notebook through them without the Android app on the
  classpath. The window opens those documents through the same reader.

### Opening, and what a failed open says

`DocumentLoader` picks a file's family by its bytes and its `mimetype` entry, and `describeOpenFailure`
decides what the window says when the open is refused. Three cases, three sentences: an
`UnsupportedOperationException` is the loader's own refusal and keeps its words (`Formato non
supportato`); an `OutOfMemoryError` is reported as memory, because a page taller than the heap is not a
corrupt notebook; and a **`LinkageError`** is reported as *the window being stale*, not the file being
broken. That last case is real — a run produced `NoClassDefFoundError:
com/mobixournal/desktop/DocumentLoader$openMobix$1`, and the notebook was untouched. It is a fact of how
the preview is launched: `:desktop:run` executes `build/classes`, so a Gradle build during a session
rewrites the class files under the live JVM and any synthetic class the JVM had not loaded yet is gone.
Closing the window and relaunching is the fix; the same file opens in ~40 ms in a fresh process.

A failed open **never takes the document on screen with it**: the report is a card at the top of the
canvas, dismissed with a click or `Esc`, while the page, the tab and the sidebar stay exactly as they
were. The card's fill is opaque rather than the floating family's frosted 0.88, because it straddles the
edge where the desk meets the sheet — at 0.88 the part over the white page came out a tone lighter than
the part over the black desk, and the panel read as two materials split down its middle.

`--args` splits its value on spaces, so a notebook kept under `Il mio Drive/…` arrives as several
arguments and `File(args[0])` does not exist: `initialDocument` puts the pieces back together when the
first one is not itself a file, so a path with spaces works from the command line as it does from the
picker.

### What the window does not do

- No editing and no saving — the window is read-only.
- No sketch/PDF/text import, no **images as elements** (only as page backgrounds), no editing of a
  living entity — the ink is drawn, not made editable.
- No right-side property inspector, no search, and no page reordering or insertion. The tool strips
  pick a tool and stop there: there is no inking, no erasing and no selection.
- No tablet or dial input, no dirty-rect GPU pacing, no installer. `run` is the only entry point;
  `nativeDistributions` is configured but not wired into a release job.

### Verification, runnable on any JVM host (no emulator)

`./gradlew :desktop:test` loads `udiff.xopp`
(6 pages, ~4 000 strokes) through `DocumentLoader`, opens a ZIP `.xopp` and a 40-page `.mobix` (including
both **misnamed** — `.mobix` content in a `.xopp` file and vice versa), refuses a text file and a ZIP with
no document inside by name, renders page 1 of each headlessly via `ImageComposeScene`, and renders a
table, a parametric shape, a bundled pixmap and a PDF background — asserting the pixels, not just that
the call returned (`LivingAndBackgroundTest`). `DesktopShellTest` renders the tab strip **before and
after** a document is opened and asserts the frames differ (the regression that caught a strip which
never redrew), renders the sidebar, and checks the keyboard's fit and zoom arithmetic. `ToolStripsTest`
renders the rail and the ribbon on their own (every slot must have drawn its glyph, and the picked one
must wear the active slot), then renders the whole window in Rail, Ribbon and Both and reads the paper
out of the pixels: the page starts after the rail's lane and below the ribbon's band, in every
placement — the mechanical form of "a band never lies on the page". `OpenFailureTest`
pins what a failed open says in each of its three cases and how a space-split command line is put back
together. Every render is
written under `desktop/build/desktop-render/` so it can be looked at — including
`glass-parity.png`, the same panel in the tablet's material and in this window's, written by
`GlassParityRenderTest`, and `glass-edge.png` / `glass-over-page.png` / `glass-show-through.png`, which
`GlassEdgeTest` samples: the rim must be brighter at the top than at the bottom, or it is a border and not
the family's edge, and a band of ink under the panel must still read through it, or the fill is a slab.
`GlassMotionTest` drives the arrival through a scene's frame clock (`render(nanoTime)`) and reads the
pixels at successive instants: at t=0 nothing is painted, 40 ms later the panel is faint and still inside
its 82 % box, by 700 ms it is the glass at full size — and gating it with `GlassPresence` keeps it
composed through the 230 ms exit hold before releasing it. `PageLayersAndPaperTest` reads pixels too:
`isometric-paper.png` is an isometric sheet, whose line midpoints must fall on the segments
`BackgroundGrid.isometric` returns, and `hidden-layer.png` is a two-layer page with one layer switched
off — the band that is gone is the check.

## Why the core is shared

`mobix-core` is the platform-free module: the document model, the XML/JSON layers, the `.xopp` and
`.mobix` codecs, the paper/ink geometry and the export compilers. The rule its header states is the one
that keeps the two shells honest: **nothing in the core may import `android.*` or Compose.** A file that
meets it moves as a whole; a file that meets it *almost* is cut along the seam rather than copied, so
there is never a second renderer with its own opinion of the drawing.

Measured on 2026-10-22 against the Android editing path:

| Type | Lines | Android imports | Verdict |
|---|---|---|---|
| `render/XoppSynthesis.kt` | 179 | none — only `format.mobix` and `format.model` | **moved** — the export compiles in `mobix-core` |
| `render/PageSvgWriter.kt` | 229 | none — model and `java.util.Base64` | **moved** — the SVG export compiles in `mobix-core` |
| `render/EditHistory.kt` | 69 | none, no imports at all | **moved** — lives in `mobix-core/src/main/java/com/mobixournal/render/` |
| `render/PdfVectorPainter.kt` | 226 | `BitmapFactory`, plus `com.tom_roush.pdfbox` (the Android PDFBox port) | **split** — the geometry is `PdfPagePlan` in `mobix-core`, and this is its Android emitter |
| `io/DocumentIo.kt` | 666 | `ContentResolver`, `Uri`, `OpenableColumns` — and `resolver` is used in exactly one place, `displayName` | a `File`-based core layer plus the existing SAF shell |
| `render/ElementEdits.kt` | 152 | `BitmapFactory`, for `inJustDecodeBounds` only | movable behind an injected image-size function |
| `render/DrawingSurfaceInput.kt` | 680 | `MotionEvent`, `KeyEvent`, `SurfaceView`, `ViewConfiguration` | stays Android; the desktop reimplements the pointer layer over Compose |
| `render/DrawingSurfaceStrokes.kt` | 679 | `MotionEvent` | stays Android; the stroke/erase geometry is what could move |
| `render/DrawingSurfaceSelection.kt` | 341 | `MotionEvent`, `ClipData`, `ClipboardManager`, `Context` | stays Android; the transform geometry is what could move |

## Decisions

Three decisions taken on 2026-10-21, recorded in [`ui-modernization.md`](ui-modernization.md) (the owning
spoke for desktop layout and materials):

- The chrome is the app's **own glass on a true-black desk**, not a platform material: Mica/Vibrancy would
  tint the window with the user's wallpaper and stop it matching the tablet.
- **Both tool placements ship** — vertical rail by default, horizontal ribbon as the alternative —
  switched in the window's own settings file, with the same value written by the header's control.
- The tab/view model is Compose **snapshot state**, so the chrome is redrawn by the state it reads instead
  of by a second copy kept in sync by hand.
