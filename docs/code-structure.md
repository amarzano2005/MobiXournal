# Code structure: one file, one topic; one package, one subject

> Read when you add a file, a package or a subsystem — this doc says where new code belongs and what the
> build refuses, so a change lands in the same shape as the tree around it.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

The tree is not organised by accident, and it is not organised by taste: the rules below are **enforced by
`PackageStructureTest`** (in `app/src/test/java/com/mobixournal/guards/`), so a change that breaks them fails
the JVM suite rather than being caught in review. The sizes are enforced by `SourceStructureTest`
([testing.md](testing.md) owns both).

## The size rules (mechanical, everywhere)

- **A file is a topic, not a chapter.** Past **700 lines** it is a mono-file and its own test refuses it.
  Splitting a listed file is always the rewarded move; the five recorded exceptions today are all in the
  Android app (`ui/EditorRegions.kt`, `render/DrawingSurfaceView.kt`, `render/CircuitShapes.kt`,
  `ui/AppSettings.kt`, `ui/EditorDialogs.kt`) and may only shrink.
- **A function is a step, not a story.** Past **100 lines** it is extracted, and `SourceStructureTest` says so.
- **No generic buckets.** A file called `Utils`, `Helpers`, `Common`, `Misc`, `Stuff` or `Manager` is refused:
  a bucket is where a topic goes to be forgotten. Name the subject.

## The window's packages (`desktop/`)

The window used to be **200 files in one flat package**. It is now seven, by subject — and this list is the
closed part of the rule: a file that is not in one of them fails the build, and so does a package that is not
in this table.

| Package | Owns |
|---|---|
| `com.mobixournal.desktop` | the **entry points only**: `Main.kt`, `DesktopApp.kt` |
| `…desktop.sheet` | the sheet's pixels, pointer gestures and their overlays: `Page*` painting, `Selection*`, `Guide*`/`DesktopGuide*`, `Living*`, `Circuit*`, `Sheet*`, `Canvas*`, `Viewport` |
| `…desktop.ink` | the **devices** and the pen's preview: `Tablet*` (Wintab, dial, wheel, area), `PenPacket*`, `WinInk`/`WinRt`, `DesktopInk*`, pressure, smoothing, the probe, the frame meter |
| `…desktop.io` | **files in and out**: `DocumentLoader`, save and recovery, PDF (import, merge, export, text), audio (record, play, store), file pickers, PNG/SVG export, the clipboard and the drop target |
| `…desktop.settings` | the **Settings window** and the stores behind it: `DesktopSettings*`, rows/sections/surface/select, shortcuts, the theme and its glyphs, pen presets |
| `…desktop.tools` | the **controls** the hand works with: tool strips and groups, the pickers for figures and circuits, colour flyouts, inspector, panel buttons, the glass material, text and math authoring |
| `…desktop.shell` | the **window around the sheet**: shell state/views, tabs and title bar, menu, workspace, status, the sidebar and page panels, search, header and audio pill |

**The root package is closed.** `Main.kt` and `DesktopApp.kt` are the only files it may hold (a recorded list,
shrink-only) — every other file states its subject by choosing a package, which is the whole point: a new
a `Desktop…` file dropped beside `Main.kt` is exactly how the flat 200-file package grew.

**No package may become the next flat one.** A topic package holds at most **50 files**; past that it is two
subjects and is split (the doc and the guard gain the new row in the same commit). Today: `sheet` 40,
`tools` 35, `shell` 34, `io` 43, `ink` 27, `settings` 19.

## The tablet's canvas (`app/`)

The other half of the same story. `DrawingSurfaceView` is one object whose state used to be reached by
extension functions in **20 files** (`DrawingSurface*.kt`, ~5 000 lines): input, selection, painting, the
page's services, hover/barrel, the karaoke painter, and so on. Splitting those into objects that *own* their
state is the fix, and it is **under way**: a cluster becomes a class in `Canvas<Subject>.kt` beside the canvas
— `CanvasDiagnostics.kt` (the pen log, its coalesced push, the frame watchdog) and `CanvasHoldSnap.kt` (the
dwell timer, its anchor, the figure it found) so far — constructed by the view, holding its own state, taking
its inputs as lambdas so it is testable with no `View`. A **new** state cluster and the code that moves it
belong in such an object, never in another `DrawingSurface*.kt` extension. The pile is now **18 files**, and
it is only allowed to shrink — each extraction lowers the ceiling in `PackageStructureTest`:

- the number of `DrawingSurface*.kt` files may not exceed **20** (a new one fails the build),
- each of them keeps the `DrawingSurface<Facet>` name, so the file name still answers which facet it is,
- and the file budget above applies to each of them.

The same applies to the app's packages, which were already split (`audio`, `io`, `panes`, `render`, `tabs`,
`ui`): a file lands in the package whose subject it is.

## Package ↔ directory, in every module

For `app`, `mobix-core` and `desktop`, a file's `package` must match its directory. It is what Kotlin needs
anyway; the guard catches the stray file that a scripted move or a copy-paste leaves behind, which is how a
tree slowly stops meaning anything.

## Adding a topic

1. **Does an existing package own it?** Read the table. Most new code is a file in one of them.
2. **If not**, the new package is a deliberate change: add its row to this doc **and** to the guard in the same
   commit, keep it under the 50-file cap, and give it a subject name — never a bucket.
3. **A new subsystem that both shells need** is not a package here: it belongs in `mobix-core`, where the
   arithmetic is shared and tested once ([codebase.md](codebase.md) owns that rule).
