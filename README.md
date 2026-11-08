<div align="center">
  <img src="docs/assets/logo.png" alt="MobiXournal" width="128" height="128" />

  # MobiXournal

  **An independent, unofficial notebook and technical-drafting app for Android tablets and Windows 11, built for a pen: it keeps what you place on the page editable, and stores it in a document format of its own.**

  [![Android CI](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml/badge.svg)](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml)
  [![License: GPL v2+](https://img.shields.io/badge/License-GPL%20v2%2B-blue.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%2F%20Windows%2011-3DDC84?logo=android&logoColor=white)](docs/guide-index.md)

  <br />
  <img src="docs/assets/preview.jpg" alt="The MobiXournal editor on an Android tablet" width="820" />

  *The tablet editor: one notebook open, the tool strips floating over the page.*

  <br />
  <img src="docs/assets/preview-desktop.png" alt="The MobiXournal window on Windows 11" width="820" />

  *The Windows window: the same notebook, with tabs and a page sidebar.*
</div>

---

## Get it running

| Your platform | Download, and what you need first |
|---|---|
| **Android 8.0 or newer** | [The APK](docs/guide-install-android.md) — one file to sideload, then the first launch and where notebooks live. Nothing to sign up for. |
| **Windows 11** | [The MSI](docs/guide-install-windows.md) — the installer, what it adds to your PC, and the folder build for a portable copy. |

Both builds work offline and leave your notebooks in your own storage. Every version is on
[GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases), and
[the guide](docs/guide-index.md) starts with the first notebook.

## Two formats, one document

A notebook saves as **`.mobix`**, a single self-contained file that keeps its plots, tables, circuits,
formulas, recorded audio and pen samples editable — reopening it reopens the work, not a picture of it.

It also reads and writes Xournal++'s **`.xopp`** without loss, which is what lets one file be edited in
either app: a `.xopp` you open saves back as a `.xopp`, and **Save As** converts when you ask it to.

One conversion the Windows window **refuses** rather than write badly: a **packaged** `.xopp` whose page
background is stored inside the file is left exactly as it is, because rewriting it would drop the
background. [How conversion behaves, case by case](docs/guide-formats-and-conversion.md).

## What it is good at

- **Pen first.** Pressure-tapered ink, palm rejection, a hover preview, and a highlighter that leaves the text beneath it readable.
- **Shapes from ink.** Hold the pen still at the end of a stroke and freehand lines, circles and arrows snap clean.
- **Objects that stay editable.** Function plots, tables, circuits and logic gates, chemistry bonds, dimension arrows, LaTeX formulas.
- **PDFs and recorded notes.** Annotate a PDF, record while you write, then tap a stroke to replay that moment.
- **Two shells, one document.** The tablet and the Windows window open the same files, with the same tools and the same paper.

## Documentation

| If you want | Read |
|---|---|
| to use the app, task by task | [the guide](docs/guide-index.md) |
| to know what works on which platform | [what works, and what does not](docs/guide-limitations.md) |
| the next action after a failure | [troubleshooting](docs/guide-troubleshooting.md) |
| which version shipped, and when | [the release index](docs/releases/index.md) |
| how the system is built | [the index of owners](docs/architecture.md) |
| the rules a change to these pages follows | [the writing standard](docs/writing-docs.md) |

## Build from source

`scripts/build.sh` where a Linux host carries `/data/android`, `scripts/host-build.sh` anywhere else. The
suites, the guards, the emulator loop, cutting a release and the documentation rules are
[the developer guide](docs/developer-guide.md)'s; a report or a patch starts at
[CONTRIBUTING.md](CONTRIBUTING.md).

## Licence and credits

**GPL-2.0-or-later** — [LICENSE](LICENSE), with third-party notices in [NOTICE](NOTICE). MobiXournal
continues Brian Monroe's [NeXopp](https://github.com/bamonroe/NeXopp) and shares format compatibility with
[Xournal++](https://github.com/xournalpp/xournalpp). It is an independent project, not affiliated with or
endorsed by the Xournal++ authors.
