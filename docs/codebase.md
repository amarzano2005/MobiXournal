# The codebase: what it is built on, and who built it first

> Read when you need to know what this project is built on, what prior art exists, or what the
> branding and identity rules are. **Where code lives** is [repo-layout.md](repo-layout.md)'s.
>
> _One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

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

The package-by-package map is [repo-layout.md](repo-layout.md)'s.

## The shared core: no `android.*` in `mobix-core`

`mobix-core` is the platform-free module: the document model, the XML/JSON layers, the `.xopp` and
`.mobix` codecs, the paper/ink geometry and the export compilers. The rule its header states is the one
that keeps the two shells honest: **nothing in the core may import `android.*` or Compose.** A file that
meets it moves as a whole; a file that meets it *almost* is cut along the seam rather than copied, so
there is never a second renderer with its own opinion of the drawing.

Measured on 2026-10-22 against the Android editing path (line counts re-checked 2026-10-23):

| Type | Lines | Android imports | Verdict |
|---|---|---|---|
| `render/XoppSynthesis.kt` | 179 | none — only `format.mobix` and `format.model` | **moved** — the export compiles in `mobix-core` |
| `render/PageSvgWriter.kt` | 229 | none — model and `java.util.Base64` | **moved** — the SVG export compiles in `mobix-core` |
| `render/EditHistory.kt` | 69 | none, no imports at all | **moved** — lives in `mobix-core/src/main/java/com/mobixournal/render/` |
| `io/DocumentIo.kt` | 666 | `ContentResolver`, `Uri`, `OpenableColumns` — and `resolver` is used in exactly one place, `displayName` | **split** — the write is `io/DocumentEncoder` in `mobix-core`, and the SAF shell keeps staging, the stores and the `content://` target |
| `render/ElementEdits.kt` | 152 | `BitmapFactory`, for `inJustDecodeBounds` only | movable behind an injected image-size function |
| `render/DrawingSurfaceInput.kt` | 581 | `MotionEvent`, `KeyEvent`, `SurfaceView`, `ViewConfiguration` | stays Android as the adapter, and it is **live**: it feeds `InkPointerRouter`, so the decisions are the core's (`docs/stylus.md`) and the Compose pointer is the second adapter |
| `render/DrawingSurfaceStrokes.kt` | 683 | `MotionEvent` | stays Android; its erase geometry moved (`PageEraser`/`StrokeEraser` are in the core) and its stroke now arrives as router events — `startStrokeFrom`, `extendStrokeFrom`, `eraseAtPointer` — so only the document-shaped half is left |
| `render/DrawingSurfaceSelection.kt` | 341 | `MotionEvent`, `ClipData`, `ClipboardManager`, `Context` | stays Android; the transform geometry is what could move |

## Branding and identity

This is an **unofficial** project. Its own name — used in this doc, the README, the About page,
`settings.gradle.kts`, and the `creator` attribute the writer stamps into saved files
(`DEFAULT_CREATOR`, `format/model/Document.kt`) — is **MobiXournal**. Desktop
Xournal++ writes `Xournal++ <version>` into `creator`, so the two writers stay tellable apart. The
launcher/task-switcher label (`@string/app_name`) carries the same name, so this app is never
mistaken for the official Xournal++ app.

**What this is, and where `.xopp` sits in it.** MobiXournal is a note-taking and technical-drafting
suite with **two shells** — the Android editor and the Windows window — over one shared core,
`mobix-core`. Its documents live in its **own native format, `.mobix`**, which is where what an XML
drawing cannot hold is kept: living tables, plots, circuits and formulas, full stylus telemetry, and
paged streaming. `.xopp` is the **interchange** format, not the store: a document is read from it and
written to it without loss in both directions, and that is a **permanent guarantee** of the product
rather than a step it is migrating through ([`xopp-bridge.md`](xopp-bridge.md) states it as a
contract, with the tests that hold it and what "lossless" means where the XML has no words for a
recipe). Xournal++ is where the project came from and what it must stay compatible with; it is not
what a MobiXournal document is made of.

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
  `CircleRecognizer.kt`, rewritten `ShapeRecognizer.kt`), replacing a hand-rolled classifier; the recogniser now lives in `mobix-core/src/main/java/com/mobixournal/render/`
  (`ShapeRecognizer`, `CircleRecognizer`, `EllipseRecognizer`, `ArcRecognizer`, `ArrowRecognizer`, `PolygonCorners`,
  `RecoSegment`, `Inertia`, `StrokeSmoothing`), because the desktop window runs the same dwell.
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
[the debug build's own app](tools.md#the-debug-build-is-its-own-app--a-test-build-never-replaces-the-daily-app).)
