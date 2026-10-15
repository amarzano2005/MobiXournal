# MobiXournal Importer Plugin for Desktop Xournal++

A companion Lua plugin for [Xournal++](https://github.com/xournalpp/xournalpp) on Linux, Windows, and macOS that enables opening and importing native **MobiXournal (`.mobix`)** documents directly into desktop Xournal++.

---

## What It Does

- **Container Extraction**: Unpacks `.mobix` ZIP containers into a local staging directory.
- **Envelope & Manifest Parsing**: Reads `manifest.json` and page schemas.
- **XML Synthesis**: Generates a valid temporary `.xopp`/`.xoj` document preserving strokes, text elements, and layer hierarchies.
- **Audio Companion Sync**: A stroke's `audioRef` carries two names — `filename`, the sidecar the stroke replayed from, and `assetId`, the entry the recording is bundled in — and the importer emits the sidecar name with its `ts` timestamp, so desktop Xournal++ looks for a companion file beside the document. The extension is never rewritten: a `.mobix` bundles a recording in the container it was recorded in, which for this app's own recorder is `.wav` (`assets/aud_<sha256>.wav`, `docs/audio-inking-sync.md`). A recording an older writer bundled as Opus needs the app to re-export it (Save As to `.xopp` writes the companion sidecar), because the plugin cannot transcode.
- **Desktop Launch**: Loads the synthesized document seamlessly via Xournal++'s `app.openFile` API.

---

## Installation

Copy the `mobix-importer` directory into your Xournal++ plugins folder:

- **Linux**: `~/.config/xournalpp/plugins/mobix-importer`
- **Windows**: `%LOCALAPPDATA%\xournalpp\plugins\mobix-importer` (or `%USERPROFILE%\.xournalpp\plugins\mobix-importer`)
- **macOS**: `~/Library/Application Support/xournalpp/plugins/mobix-importer`

Then, restart Xournal++, navigate to **Plugin → Plugin Manager**, and ensure **MobiXournal Importer** is enabled.

---

## Usage

1. Open Xournal++.
2. Select **Plugin → Import .mobix Document...** (or press <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>M</kbd>).
3. Select your `.mobix` document.
4. The document opens immediately in Xournal++ ready for reading, handwriting, or exporting.
