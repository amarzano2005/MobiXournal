<div align="center">

<img src="docs/assets/logo.png" alt="The MobiXournal logo: a stylus resting on an open notebook" width="128" height="128" />

# MobiXournal

### Think. Write. Explore.

**A handwriting-first scientific workspace.**

*A scientific workspace for learning, creating and exploring ideas.*

A stylus, live mathematics and the documents you work from, on one page — for Android tablets and Windows 11.

[![Android and desktop CI](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml/badge.svg)](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml)
[![Licence: GPL-2.0-or-later](https://img.shields.io/badge/Licence-GPL--2.0--or--later-blue.svg)](LICENSE)

[**Install on Android**](docs/guide-install-android.md) · [**Install on Windows 11**](docs/guide-install-windows.md) · [**User guide**](docs/guide-index.md) · [**Releases**](https://github.com/amarzano2005/MobiXournal/releases)

<img src="docs/assets/preview.jpg" alt="The MobiXournal editor on an Android tablet: a notebook open on ruled paper, handwriting in ink, and the floating tool strips over the page" width="880" />

*One page holding handwriting, figures and a document — the editor on an Android tablet.*

</div>

---

## The idea behind the workspace

A notebook app treats the page as a picture and a drafting tool treats it as a canvas. MobiXournal treats it as
working paper: what you place stays an object you can reopen and change.

It is built for people who think in handwriting and then need mathematics, diagrams and source documents beside
it — a student on a problem set, an engineer sketching a circuit, a researcher annotating a paper. Files stay
yours, in the folder you choose, with no account between.

## Four pillars of the workspace

### Think and write

- **Ink that answers the pen.** Width follows the pressure the tablet reports, through the same filter desktop Xournal++ uses: a thin fast stroke and a bold pressed one are one gesture.
- **The pen outranks the toolbar.** A palm that landed first is abandoned when the stylus arrives, a flipped eraser erases, and the barrel button runs the action you pick.
- **Shapes from ink.** Rest the tip for a third of a second and a line, circle or arrow snaps clean; a closed triangle or rectangle stays a shape with movable corners.
- **Paper belongs to the page.** Plain, ruled, dotted, graph or isometric ruling, with the sheet's size chosen separately.

### Mathematics and science

- **Functions you can feel.** A plot keeps its formula live: its named parameters get sliders, and dragging one re-evaluates curve, derivative and summary.
- **Analysis in the same place.** Critical points, inflections, a shaded definite integral and a written summary belong to the plot, not to a separate calculator.
- **Objects, not pictures.** A table keeps a header row and real cells a typo edits rather than repaints; circuits wire together at a part's pins.
- **Formulas and guides.** LaTeX takes a live typeset preview, and the drafting guides snap a 15° protractor, a 60° lattice and the page's grid.

### Documents and research

- **Write on the source.** A PDF becomes the page's background — by range, replacing or appended — and its text stays selectable and copyable.
- **A highlighter that finds the line.** Drag across PDF text and each line snaps into a constant-width band; a margin mark stays exactly as drawn.
- **Follow the document's contents.** A PDF's outline opens as a navigable tree by its own page numbers, even after two files are merged.
- **Record while you write.** Every stroke is stamped with its place in the recording, and tapping it later starts playback at that moment.
- **Search everything at once.** Typed text, a PDF's text layer, a formula's source and your handwriting come back together, offline, on both shells.

### One workspace, two shells

- **The same file in both.** The tablet editor and the Windows window open the same notebooks with the same tools and paper, through one core.
- **The window is a workstation.** Tabs that keep their own page and zoom, a sidebar of thumbnails and layers, and export to PDF, PNG or SVG.
- **The tablet is the paper.** Split view, the Ring at the pen, palm rejection, and pages sized for a hand.
- **Nothing converts behind your back.** A document keeps the format it was opened in; nothing needs an account or a connection.

## Screens from the real app

<div align="center">
  <img src="docs/assets/preview-plot.png" alt="The Android editor with a sin(x) plot on a ruled page" width="880" />

  *A function placed on the page, still an editable object.*

  <img src="docs/assets/preview-pdf.png" alt="A PDF page in the Android editor with its title highlighted" width="880" />

  *An imported PDF, highlighted line by line.*

  <img src="docs/assets/preview-desktop.png" alt="The MobiXournal window on Windows 11 showing handwritten derivations" width="640" />

  *Handwritten derivations in the Windows 11 window.*
</div>

## Platforms, and what each one needs

| Platform | What you need, and what you get |
|---|---|
| **Android 8.0 or newer** | The release APK from [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases). A stylus makes it a notebook; a finger does the rest. Current build **2.0.0**. |
| **Windows 11, 64-bit** | A per-user installer, with no Java to install first and a folder build that needs no install. Current build **1.0.1**. |

The shells number separately — Android **2.0.0**, Windows **1.0.1** — so the release you run is the one whose note
describes it; a notebook is not tied to either. The installer is **unsigned**: Windows reports an unknown publisher
and SmartScreen may ask first, left unsigned on purpose rather than given a false identity.

## The two file formats

| Format | What it is for |
|---|---|
| **`.mobix`** | MobiXournal's own container: one self-contained file that keeps plots, tables, circuits, formulas, audio and full stylus telemetry editable, and opens a long notebook page by page. |
| **`.xopp`** | The interchange format desktop Xournal++ reads and writes. A notebook goes both ways without loss, so one file can be worked on in either app. |

Opening never converts anything: a `.xopp` opened on the tablet still saves as a `.xopp`, and **Save As** is what
changes the format. The Windows window **refuses** one **packaged** `.xopp` whose background lives inside it,
leaving the file untouched. Details: [the native format](docs/mobix-format.md), [the `.xopp`
contract](docs/xopp-bridge.md), [which format your notebook is in](docs/guide-formats-and-conversion.md).

## Installing, and the first notebook

**Android**

1. Download `app-release.apk` from [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases) and open it.
2. Allow installs from the app you downloaded it with; the permission can be turned off afterwards.
3. Notebooks are files you pick with Android's own dialog.

**Windows 11**

1. Download `MobiXournal-<version>.msi` from [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases) and run it.
2. Confirm the SmartScreen prompt if one appears, and keep or decline the associations and shortcut.
3. Open a notebook from the header, from Explorer, or by dragging a file onto the window.

A new tab is an empty notebook until you save it: **Save As** for a name and a format, then **Save**
writes back in that format. The full first run is [your first notebook](docs/guide-first-notebook.md).

## Where the work stands today

Most of it is settled and in daily use: ink, tables, circuits, Cartesian plots with live sliders,
LaTeX, PDF annotation, audio replayed from a stroke, and search across handwriting.

Some of it stops part-way: the plot tool places Cartesian curves only, so a parametric or polar curve opens and
round-trips but cannot be created here; a Typst source is kept but not typeset; tablet handwriting search needs
its model downloaded first.

Absent: no sync between devices, tilting the pen does not change stroke width, and the chemistry palette is
gone. What differs by shell is graded in [what works, and what is not there](docs/guide-limitations.md).

## Documentation, by question

| If you want to | Read |
|---|---|
| use the app, task by task | [the user guide](docs/guide-index.md) |
| know what works on which platform | [what works, and what is not there](docs/guide-limitations.md) |
| get past a failure, or recover a notebook | [when something goes wrong](docs/guide-troubleshooting.md) |
| know which version shipped, and what it changed | [the release index](docs/releases/index.md) |
| understand how the system is built | [the index of owners](docs/architecture.md) |
| see how a timing was measured | [the benchmark method](docs/benchmarks.md) |
| follow the rules these pages obey | [the writing standard](docs/writing-docs.md) and [governance](docs/doc-governance.md) |

## Building from source, and getting involved

`scripts/build.sh` with `/data/android`, `scripts/host-build.sh` anywhere else; suites, guards and releases are
[the developer guide](docs/developer-guide.md)'s. A report or patch starts at [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence, credit and lineage

**GPL-2.0-or-later** — [LICENSE](LICENSE), with third-party notices in [NOTICE](NOTICE). MobiXournal continues
Brian Monroe's [NeXopp](https://github.com/bamonroe/NeXopp) and shares format compatibility with
[Xournal++](https://github.com/xournalpp/xournalpp). It is an independent project, not affiliated with or
endorsed by the Xournal++ authors.
