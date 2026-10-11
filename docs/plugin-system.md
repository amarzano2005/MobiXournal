# Plugin system and extensible scripting architecture

> Read when implementing the plugin engine, extension APIs, script runners (Lua, Kotlin, WASM), custom exporters, or handwriting recognition integrations.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: an open, customizable ecosystem

Xournal++ on desktop gained immense community utility through its Lua plugin engine. MobiXournal's
plugin architecture achieves two goals:
1. **Full compatibility** with desktop Xournal++ Lua scripts (allowing existing plugins to run).
2. **Modern cross-platform extensions** for Android and Desktop via WebAssembly (WASM) and Kotlin Scripting.

---

## Plugin runtime architecture

```text
┌─────────────────────────────────────────────────────────────┐
│                    MobiXournal Host Core                    │
│   (Document Model, Selection, Inking Engine, Tool Rail)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ Plugin Host Boundary (IPC / JNI / WASM)
                               ▼
┌──────────────────────────────┴──────────────────────────────┐
│                    Plugin Sandboxed Runtime                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────┐  │
│  │   Lua Engine     │  │   WASM Runtime   │  │ Kotlin KTS│  │
│  │ (LuaJ / Luau)    │  │  (Wasmtime / V8) │  │ (Desktop) │  │
│  └──────────────────┘  └──────────────────┘  └───────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Extension points & capabilities

Plugins declare their hooks in a standard `plugin.json` or `plugin.ini` manifest:

### 1. Document Exporters & Importers
- **LaTeX / Typst**: Export handwritten notes and text blocks directly to formatted typesetting documents.
- **Anki Flashcard Generator**: Automatically scans notes for question/answer tags (e.g. `[Q] ... [A] ...`)
  and generates `.apkg` flashcard decks.
- **Markdown / Pandoc integration**: Converts text and mathematical formulas into structured Markdown.

### 2. Custom Canvas Tools & Figure Generators
- **Specialized Shape Libraries**: Chemistry molecule builders (benzene rings, bonds), algorithmic flowcharts,
  music staves.
- **Formula recognition (MathOCR)**: Hands a bounding box of inking strokes to an on-device OCR engine
  (e.g., Mathpix, MyScript, or an open-source ONNX model) and replaces the ink with a live `PlotElement`
  or formatted LaTeX block.

### 3. Custom UI Action Bar Items
Plugins can register actions that appear in the selection floating toolbar or the editor overflow menu:

```json
{
  "id": "com.mobixournal.plugin.latex-export",
  "name": "LaTeX Exporter",
  "version": "1.0.0",
  "author": "MobiXournal Community",
  "entrypoint": "main.lua",
  "permissions": ["DOCUMENT_READ", "EXPORT_FILE"],
  "actions": [
    {
      "id": "export-latex",
      "label": "Export to LaTeX...",
      "location": "OVERFLOW_MENU",
      "icon": "code"
    }
  ]
}
```

---

## Plugin API contract (Lua example)

To maintain compatibility with desktop Xournal++ plugins:

```lua
function initUi()
  app.registerUi({
    ["menu"] = "Tools",
    ["callback"] = "exportSelectionToAnki"
  })
end

function exportSelectionToAnki()
  local sel = app.getDocumentSelection()
  if not sel then
    app.uiAlert("Seleziona prima una casella di testo o un disegno!")
    return
  end

  local texts = sel.getTextElements()
  -- Process flashcard data and export
  app.uiAlert("Esportate " .. #texts .. " carte per Anki!")
end
```

---

## Security and sandboxing

1. **Storage Isolation**: Plugins can only read/write files within their own sandboxed data directory
   or through explicit user file-picker intents.
2. **Network Access Permission**: Access to network endpoints (for cloud OCR or translation) requires
   explicit user approval during plugin installation.
3. **No Unchecked Native Execution**: Scripts run inside constrained interpreters (Luau, WASM), preventing
   memory corruption or arbitrary binary execution.
