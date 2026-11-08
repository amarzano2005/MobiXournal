<div align="center">
  <img src="docs/assets/logo.png" alt="MobiXournal Logo" width="140" height="140" />

  # MobiXournal
  **A stylus-first note-taking & STEM drafting suite for Android and Windows — native `.mobix` documents, lossless `.xopp` interchange, full graphics-tablet support**

  [![Android CI](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml/badge.svg)](https://github.com/amarzano2005/MobiXournal/actions/workflows/build.yml)
  [![License: GPL v2+](https://img.shields.io/badge/License-GPL%20v2%2B-blue.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%2F%20Windows%2011-3DDC84?logo=android&logoColor=white)](docs/desktop-workstation.md)
  [![Format](https://img.shields.io/badge/Format-.mobix%20(native)%20%2F%20.xopp%20(lossless)-orange)](docs/xopp-bridge.md)
  [![Stylus & Tablet](https://img.shields.io/badge/Stylus-S--Pen%20%7C%20Active%20Pen%20%7C%20Tablet-blueviolet)]()

**Documents live in two formats that work side by side:**
- **`.mobix` (Native format)**: Stores re-editable graphs, tables, circuits, shapes, and formulas, compressed Opus audio bundled inside the file, full stylus telemetry, and fast page-by-page streaming.
- **`.xopp` (Xournal++ interchange)**: Reads and writes standard [Xournal++](https://github.com/xournalpp/xournalpp) files losslessly. Living `.mobix` elements export as standard vector strokes with embedded metadata, reviving as editable objects when reopened in MobiXournal unless modified externally.

  <br />
  <img src="docs/assets/preview.jpg" alt="MobiXournal Mobile - UI Preview" width="850" />
  
  *MobiXournal Mobile*

  
  <br />
  <br />
  <img src="docs/assets/preview-desktop.png" alt="MobiXournal Desktop - UI Preview" width="850" />
  
  *MobiXournal Desktop*

  
</div>

---

## ▶️ Try It

| Platform | How to get started |
|---|---|
| **Android (8.0+)** | Download `app-release.apk` from [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases), or build it locally with `scripts/release-apk.sh` (or `.\gradlew.bat assembleDebug` for a debug APK). |
| **Windows 11 Desktop** | Install `MobiXournal-<version>.msi` from [GitHub Releases](https://github.com/amarzano2005/MobiXournal/releases) — the installer offers, on by default, to open `.mobix` and `.xopp` files with it and to put an icon on the desktop, and is signed when a certificate is configured — or build it with `scripts/desktop-package.ps1` (JDK 21 and WiX 3.14, no Android SDK). Or build the standalone folder with `.\gradlew.bat :desktop:createDistributable` and run `MobiXournal.exe` inside `desktop/build/compose/binaries/main/app/MobiXournal/`. |

---

## ✨ Key Features

### 📂 Workspace, Documents & Formats
- **Sticky Format Policy**: Every opened file saves back in its own format (`.mobix` or `.xopp`), while **Save As** converts in either direction — the Windows window refuses the one case it cannot write faithfully (a packaged `.xopp` with an embedded background) and leaves the file untouched.
- **Multi-Tab & Split View**: Work across multiple tabs or side-by-side split panes; open a live mirror of the same notebook to view a reference page while writing on another.
- **Universal Import & Export**: Annotate PDFs (replace or append specific page ranges), import Markdown/plain text and images (`PNG`, `JPEG`, `WebP`), and export vector PDFs, per-page SVGs, or PNGs.
- **Safe Cloud & Local Saves**: Atomic staged writes protect cloud shares (Google Drive, Nextcloud, OneDrive, WebDAV), while incremental `.mobix` saves update only modified pages in milliseconds.

### 🖊️ Stylus, Graphics Tablets & Inking
- **Stylus-First Inking**: Capacitive palm rejection, hover cursor preview, pressure-tapered pen strokes, and multiply-blended highlighters that preserve underlying text.
- **Pressure Calibration & Presets**: Fine-tune minimum pressure floor (`0.01–1.00`) and multiplier (`0.5–4.0×`) with named presets and live input diagnostics.
- **Shape Recognition & Hold to Snap**: Pause the pen tip for 300 ms at the end of a stroke (or enable continuous shape recognition) on Android or Windows to snap freehand ink into clean lines, rectangles, triangles, circles, ellipses, arcs, or arrows.
- **Pen-Tip Ring & Barrel Actions**: Trigger the radial ring menu at the nib via barrel button or shortcut (`Alt + B` on PC) to switch tools, colors, and stroke widths without reaching for the toolbar.
- **Graphics Tablet Integration**: Plug-and-play USB/Bluetooth tablets on Android with 1-click ExpressKey mapping, plus dedicated Wintab and Windows Ink pen streams on Windows 11.

### 🔬 STEM & Technical Drafting
- **Living 2D Function Plotter**: Plot Cartesian $y = f(x)$ and parametric $\{x(t), y(t)\}$ curves with live calculus analysis, shaded definite integrals, and interactive on-canvas parameter sliders.
- **Electronic Circuits & IEEE Logic Gates**: Place and edit passive/active components (resistors, capacitors, inductors, diodes, LEDs, BJTs, op-amps, sources) and digital logic gates with magnetic pin snapping.
- **Chemistry & Dimensioning**: Draw single, double, or triple chemical bonds, aromatic benzene rings, and a 60°/120° hexagonal bond lattice, or drag auto-measured millimeter dimension arrows.
- **Relational Tables & Math Blocks**: Author resizable data tables with optional relational headers and in-place cell editing, plus re-editable LaTeX formulas typeset as vectors and Typst sources kept intact through save and export.
- **Virtual Drafting Instruments**: Rule exact lines and arcs against an on-canvas setsquare, compass, or protractor, or snap directly to lined, graph, dotted, and isometric paper meshes.

### 🎙️ Audio Sync, PDF & Full-Document Search
- **Synchronized Audio Notes**: Record audio while taking notes; tap any stroke with **Play Object** (or scrub the waveform player at $0.8\times\text{–}2.0\times$ speed) to jump to the exact moment it was written.
- **Self-Contained & Exportable Audio**: `.mobix` bundles compressed Opus audio (~15 MB/hour) directly inside the notebook, with optional background recording and automatic `.m4a` export on Android.
- **Smart PDF Annotation**: Select and copy real PDF text (`Shift + S` on PC), use the magnetic highlighter to snap cleanly along text lines, browse the PDF Table of Contents, and reorder, duplicate, or copy/paste pages across documents.
- **Handwriting & Full-Text Search**: Search typed text, math source, PDF text, and handwritten ink simultaneously — powered by Google ML Kit on Android, Windows Ink recognition on PC, and an offline Kotlin fallback.
- **Tailored Navigation & Unified Settings**: Documents open at 100 %, the sheet's width filling the window as on the tablet; zoom step and smooth wheel scrolling are customizable on PC (momentum curves on Android), and the settings search finds any option or shortcut.

---

## 🛠️ Build & Requirements

- **Containerized Linux (`/data/android`)**: Uses Docker (with Podman fallback) so no local JDK or Android SDK is required.
- **Windows 11 / macOS / Linux host**: Requires **JDK 21** (17–24 supported by Gradle) and the **Android SDK** configured in `local.properties` (building `:desktop` requires only the JDK).

```sh
# Containerized build (Linux with /data/android): unit tests + debug APK
scripts/build.sh

# Host fallback (auto-discovers JDK 21 + Android SDK): unit tests + debug APK
scripts/host-build.sh

# Direct Gradle commands (Windows PowerShell with JAVA_HOME set to JDK 21)
.\gradlew.bat :app:testDebugUnitTest :mobix-core:test :desktop:test
.\gradlew.bat assembleDebug
.\gradlew.bat :desktop:createDistributable
```

For emulator testing (`scripts/connected-test.sh`) and cutting releases (`scripts/todo.sh release --version <version> --bump-gradle`), see [`docs/tools.md`](docs/tools.md).

---

## 📚 Documentation Map & Project Structure

MobiXournal uses a strict hub-and-spoke documentation model where each fact has a single authoritative home:

| Topic | Authoritative Document |
|---|---|
| **Development Workflow & Conventions** | [`AGENTS.md`](AGENTS.md) |
| **Writing or Reviewing a Doc** | [`docs/writing-docs.md`](docs/writing-docs.md) |
| **System Architecture Index (all spokes)** | [`docs/architecture.md`](docs/architecture.md) |
| **UI & Design Contract** | [`docs/interface-contract.md`](docs/interface-contract.md) |
| **Native `.mobix` Format & `.xopp` Bridge** | [`docs/mobix-format.md`](docs/mobix-format.md), [`docs/xopp-bridge.md`](docs/xopp-bridge.md) |
| **Windows 11 Desktop Workstation** | [`docs/desktop-workstation.md`](docs/desktop-workstation.md), [`docs/desktop-settings.md`](docs/desktop-settings.md) |
| **Build, Emulator & Release Pipelines** | [`docs/tools.md`](docs/tools.md), [`docs/testing.md`](docs/testing.md) |
| **Task Queue & Completed Archive** | [`TODO.toml`](TODO.toml), [`FINISHED.toml`](FINISHED.toml), [`docs/releases/`](docs/releases/) |

The repository is split into three Gradle modules (detailed in [`docs/repo-layout.md`](docs/repo-layout.md)):
- **`mobix-core/`**: Pure JVM module containing the `.mobix` and `.xopp` codecs, living element synthesis/revival, math/plot engine, shape recognizer, and shared rendering geometry.
- **`app/`**: Android tablet & phone editor (`SurfaceView` low-latency inking pipeline, Jetpack Compose Material 3 UI, SAF storage, and ML Kit handwriting search).
- **`desktop/`**: Windows 11 Compose Multiplatform (Skiko) desktop editor sharing `mobix-core` and the frosted-glass design system.

---

## 📄 License

> **Credits & License**:
> - **Xournal++**: Format compatibility, color palette, and shape recognition match upstream [Xournal++](https://github.com/xournalpp/xournalpp) (GPL-2.0-or-later).
> - **NeXopp**: MobiXournal continues and expands Brian Monroe's [NeXopp](https://github.com/bamonroe/NeXopp) (`bamonroe`).
> - Independent and unofficial project distributed under the **GPL-2.0-or-later** license. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
