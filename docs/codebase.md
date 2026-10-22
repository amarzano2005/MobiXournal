# The codebase: what it is built on, and where things live

> Read when you need to know what this project is built on, how the repo is laid out, or what the branding/identity rules are.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

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

## Repository layout

Three Gradle modules: `app` (the Android shell), `mobix-core` (platform-free model, codecs, shared
geometry) and `desktop` (the Compose preview, `docs/desktop-workstation.md`). Code is split into small,
single-responsibility files per `AGENTS.md`.

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

mobix-core/                  # shared, platform-free core (kotlin-jvm): no android.*, no Compose
  build.gradle.kts           # jvmToolchain(21), jvmTarget 17, junit; fixtures read from app's test resources
  src/main/java/com/mobixournal/
    format/model/            # Document/Page/Layer/Background/Element, the living entities, ids, EditedPages
    format/xml/              # XmlPullReader, XmlWriter, XmlComment — the dependency-free XML layer
    format/json/             # the JSON value model, parser and writer (public: .mobix, settings, transcripts)
    format/FontDescription.kt # Pango-style font description <-> family + bold/italic (pure)
    format/XoppColor.kt      # #RRGGBBAA <-> ARGB int, named colours
    format/XoppReader.kt     # XML -> Document
    format/XoppWriter.kt     # Document -> XML
    format/Xopp.kt           # gzip open/save + parse/serialize entry points
    format/XoppZip.kt        # ZIP-package open/save (PDF embedded); see the mimetype caveat
    format/SaveFormat.kt     # ORIGINAL (gzip) vs ZIPPED (single-file) — the sticky save choice
    format/FileKind.kt       # content sniffing: ZIP / GZIP / PDF / XML / TEXT / IMAGE / UNKNOWN
    format/mobix/            # the native .mobix engine; the public streaming contract is marked below
        MobixFormat.kt       # the format's fixed entry names and the page/chunk/asset naming scheme (pure)
        MobixContainer.kt    # the ZIP envelope: stored typed mimetype, manifest required, unknown entries kept
        MobixManifest.kt     # manifest.json: version stamps, ids, page/chunk order, canvas mode, defaults
        MobixCanvasFile.kt   # canvas.json: the geometry mode, locked dimensions, chunk grid, print origin
        MobixPagedReader.kt  # public: MobixEntrySource (central-directory access) and the LRU paged reader
        PageSource.kt        # public: the page surface every reader shares — count, id, page, window
        MobixAssets.kt       # public: MobixAssetSource (resolve an `assets/` payload) and MobixAssetSink (name = hash)
        MobixPageCodec.kt    # public: a page <-> JSON, registering the payloads it bundles
        MobixElementCodec.kt # public: one element <-> JSON, dispatching to the per-family codecs
        MobixTableCodec.kt, MobixPlotCodec.kt, MobixShapeCodec.kt, MobixCircuitCodec.kt # internal: the living
                             #   families; MobixJson.kt is the JSON builder they share
        MobixDocument.kt     # the whole-archive read/write (`.xopp` ingest, encode, refusals); chunk half in
                             #   MobixChunkWriting.kt (internal: tile coords, the lazy tile list, encode/read)
        MobixSavePlan.kt     # public: what a full write produces — the entries and the order they are written in
        MobixIncrementalSave.kt # public: repack over a source, rebuilding only the parts that changed (stats)
        MobixPageReuse.kt    # the rule that decides which page/chunk may be copied out instead of rebuilt
        MobixMergeEngine.kt, MobixConflictDetector.kt # public: three-way page merge and its conflicts
        MobixMeta*.kt        # the `.xopp` bridge block: what an export claims and the digest that revives it
        MobixBridgeFile.kt   # xopp-bridge/metadata.json: the .xopp a document came from (creator, version, preview)
    render/BackgroundGrid.kt, BackgroundRuling.kt # ruling offsets/spacings + `<background config=…>`
    render/InkGeometry.kt    # what a stroke IS: alpha/multiply, band, dashes, mode, pressure outline
    render/InkTokens.kt, MeasurementText.kt # the drawing tokens the compilers need (label size, wash) and mm<->pt (pure)
    render/ElementBounds.kt  # pt bounding box of any element + a Bounds value type (pure)
    render/LivingInk.kt      # HOW a living entity is drawn: the one dispatch over the four families' ink
                             #   compilers; Uncompilable.kt is the loss they report instead of dropping
    render/TableInk.kt, PlotInk.kt, ShapeInk.kt, CircuitInk.kt # a family -> its ink: grid rules/cells,
                             #   axes/ticks/curves/summary, outline/fill/labels, symbol placed by its pins; InkPrimitives.kt
                             #   holds the stroke and label primitives all four share (pure)
    render/ShapeBuilder.kt, TriangleKind.kt, TrapezoidKind.kt, FigureGeometry.kt # drag -> vertex list
                             #   (line/arrow(s)/rect/ellipse/axis/table/circuit); triangle/trapezoid variants;
                             #   headed figures, where a head is sized by the pen and not by the shaft (pure)
    render/CircuitShapes.kt, ChemShapes.kt, TriangularLattice.kt # symbols and gates; bonds and the benzene
                             #   ring; the 60° chemistry lattice (pure)
    render/FunctionPlot.kt, AdvancedCurves.kt, FunctionAnalysis.kt, MultiFunctionPlotter.kt, PlotFrame.kt
                             #   f(x) parse/sample/geometry, piecewise + parametric curves, multi-curve plotting
                             #   with feature detection and summaries, and the frame a plot was drawn in (pure)
    render/XoppSynthesis.kt  # THE EXPORT: flatten living entities into ink and write the bridge block (.mobix -> .xopp)
    render/PageSvgWriter.kt  # one page -> a standalone SVG document (vector strokes, shapes, text, ruling)
    render/EditHistory.kt    # generic undo/redo over document snapshots (pure); one per editor, one per tab
  src/test/java/com/mobixournal/  # the model/xml/json/xopp suites and the shared geometry ones
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
    io/                      # storage access that isn't format work
      UriStaging.kt          # stage document bytes to/from a content:// URI (slow remote shares)
      ScratchDir.kt          # unique-per-call file names (staging and both stores), so overlapping writes can't collide
      StoreFiles.kt          # the sweep both stores share: drop unreferenced files, then trim oldest-first to a byte budget
      IncomingDocument.kt    # picks the document URI out of an incoming view/edit intent (pure, testable)
      SaveTarget.kt          # the suffixes and MIME types we write (.xopp and .mobix), and the names to suggest for a save or a PDF export
      PdfStore.kt            # one background-PDF file per open document; never rewritten, content-cache index, byte-budget eviction
      ImageStore.kt          # one never-rewritten copy per pixmap background reference; same liveness sweep and byte budget
      TextImport.kt          # text file -> generated background PDF, cached by content hash
      PdfReference.kt        # how a .xopp names its background PDF: relative <-> absolute paths and SAF document ids
      DocumentIo.kt          # document I/O policy: staging + PDF stores + read (which container?) / encode / merge
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
      DrawingSurfacePlots.kt # the plot insertions: insertMultiPlot's ink and insertPlotElement's living plot (extensions); PlotElementFactory.kt holds the box and the element, Android-free
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
      GuideDrag.kt           # the setsquare/compass/protractor (and ruling/chemistry) guide pose and the finger that moves it
      TextEditController.kt  # placing/editing text boxes, images and LaTeX images from a tap
      ElementEdits.kt        # the document edits behind those placements (pure, tested)
      PageStacker.kt         # lays pages out in rows of N columns, fit to column (pure geometry)
      PageTemplates.kt       # stationery presets: paper style+config per preset, Cornell rules as strokes (pure)
      Snapping.kt            # the page's ruling as a snap lattice (spacing + phase, or an interleaved node mesh for isometric paper); rotation -> 15-degree steps (pure)
      DrawingGuide.kt        # setsquare/compass/protractor overlay geometry + the ruling and chemistry snaps: project a drawn point onto an edge, a ray, the page's ruling or a 60° lattice node (pure)
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
      PdfOutline.kt          # the Contents model: one row per bookmark + the tree flattening (pure)
      PdfOutlineExtractor.kt # reads a PDF's /Outlines bookmark tree via PDFBox
      PdfOutlineCache.kt     # one extracted outline per PDF file, shared across mirrored views
      PdfExporter.kt         # flattens a Document to a PDF (PDFBox; preserves source vector pages)
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
      Selection.kt           # ElementRef + SelectionTester: rect/tap picking, selection bounds (pure)
      SelectionOps.kt        # translate / scale / rotate / restyle / align / distribute selected elements (pure)
      VerticalSpaceOps.kt    # insert / remove vertical space on a page, shifting what's below, and
                             #   snapping the gap to the ruling when snapping is on (pure)
      InputClassifier.kt     # pointer kind + button + active tool + settings -> gesture intent (pure); MultiFingerTapDetector.kt: a still 2/3-finger tap's finger count, FingerTapSettings.kt: the action each tap was given (pure)
      PressureCurve.kt       # pressure -> width multiplier + sensitivity presets (pure)
      Fling.kt               # decelerating two-axis momentum-scroll kinematics (pure)
      VelocityEstimator.kt   # pan release-velocity from a trailing sample window (pure)
      PageOps.kt             # insert / copy / move / delete pages in a page list (pure)
      PageThumbnail.kt       # rasterises one page into a small preview bitmap for the tab overview
    audio/                   # audio-annotated strokes: record, replay, sidecar transfer
      AudioAnnotation.kt     # AudioRef <-> a stroke's fn/ts attrs; document sidecar set; recording names (pure)
      WavWriter.kt           # streaming 16-bit PCM RIFF/WAVE writer, header patched on close (pure)
      AudioRecorder.kt       # AudioRecord -> WavWriter (the .xopp codec); OpusAudioRecorder.kt = Opus/Ogg (.mobix)
      AudioPlayer.kt         # MediaPlayer wrapper: play one clip from an offset
      AudioStore.kt          # app-private recordings dir + SAF tree import/export of sidecars
      AudioSession.kt        # the editor's single audio facade (record / stamp / play / sync)
    ui/                      # Compose Material 3
      EditorScreen.kt        # the editor's assembly: the chrome's bands (header, rail lane, dock) + the canvas that takes the rest, split panes
      EditorUiState.kt       # the screen's remembered chrome state (pen, open dialogs, panes) in one holder + switchToolTo, the one tool-switch rule
      EditorRegions.kt       # the screen's regions: header (Secondary Toolbar tools + the tab row in it), Main Toolbar wiring, one pane's canvas
      EditorOverflowMenu.kt  # the top bar's ☰ menu: its button + panel (quick file actions, PDF row, pen, Settings)
      EditorBackHandler.kt   # back peels off one transient editor state at a time before leaving the app
      PaneState.kt           # per-pane canvas state the chrome mirrors (zoom, page, layer, undo) + the pane count
      SplitLayout.kt         # two panes side by side with a slim draggable grip down the middle
      EditorOverlays.kt      # what layers over the canvas: selection bars + author/save/import dialogs
      ExportGapsDialog.kt    # what a .xopp export could not draw, in the user's terms (living entities it has to cook into ink)
      PenDiagnosticsPanel.kt # the on-canvas pen-diagnostics log: the raw stylus stream, with Copy/Clear
      SideToolbar.kt         # Main Toolbar (rail): the shell + tool-group slots; each pop-up is a Toolbar*.kt below
      EditorTool.kt          # the editor's tool modes + their labels/icons (pure)
      ToolbarColorPopup.kt   # rail slot: the compact colour + tip-size + line-style drop-down
      ToolbarSizePopup.kt    # the three pen-width slots as one compact bar + their long-press resize dialog; ToolbarStylePopup.kt: shared line-style chips
      ToolGlyphs.kt          # custom rail glyphs (hollow rectangle, rhombus, trapezoid, square); Material has no outline variants
      CircuitGlyphs.kt       # custom vector glyphs for circuit components and logic gates
      DrawingGuidePopup.kt   # drawing guide popups (rail + compact) with categorized instruments/lattices & custom glyphs
      ToolbarViewPopups.kt   # rail slots: zoom, page background, audio
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
      PageCounter.kt         # always-visible "page X of Y" badge, in the corner opposite the Main Toolbar, + the zoom badge above it (tap = reset to 100%)
      SettingsScreen.kt      # settings: side menu + sections on a wide screen, pushed index on a narrow one
      SettingsSidebar.kt     # the wide screen's permanent side menu (search field + the four areas)
      SettingsSearch.kt      # which sections answer a query, by title/summary/alias keywords (pure)
      SettingsWidgets.kt     # the shared settings controls (switches, option groups, key fields)
      ShortcutsSection.kt    # the Shortcuts page: the two toggles + one key per tool and per pen colour; FingerGesturesSection.kt: the Finger gestures page (what a still two- and three-finger tap runs)
      AppearanceSection.kt   # the Appearance page: theme mode and wallpaper (dynamic) colours
      BackupSection.kt       # the Backup page: JSON export/import/reset via Storage Access Framework; an import is refused unless it is a compatible backup
      AboutSection.kt        # the About page and the links it sends people to
      AppSettings.kt         # AppSettings model + SettingsStore (SharedPreferences persistence)
      GuidePose.kt           # the remembered drawing-guide pose (page + live guide) and its JSON codec (pure)
      AppSettingsBackup.kt   # JSON serializer/parser for AppSettings: versioned, tolerant reader + a compatibility gate (BackupCheck) for imports (pure)
      theme/                 # XoppTheme (Material You), Color
  src/test/java/com/mobixournal/format/                         # JVM unit tests for the format layer
  src/test/java/com/mobixournal/render/                         # JVM unit tests for layout/grid/LaTeX geometry
  src/test/java/com/mobixournal/audio/                          # JVM unit tests for fn/ts mapping + WAV framing
  src/androidTest/java/com/mobixournal/                         # on-device smoke test (load/draw/save/reopen)
  src/test/java/com/mobixournal/ui/                             # JVM unit tests for the chrome's pure logic (incl. the palette)
```

The **`format/` layer is the heart** and is deliberately free of Android dependencies: the model, the
XML/JSON layers and the `.xopp` codecs live in `mobix-core/`, so the round trip is unit-tested on the
JVM and reused by the desktop shell. `render/` and `ui/` are the Android-facing shell around it.

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
identity of this unofficial app, kept distinct from upstream and from any other build. (The one
exception is the v2 development channel: the `debug` build type adds `.v2` so a test build installs
beside the owner's daily app instead of replacing it — see
[the v2 development channel](tools.md#the-v2-development-channel--the-test-build-never-replaces-the-daily-app).)
