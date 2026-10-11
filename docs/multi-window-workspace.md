# Multi-window workspace: split-view and cross-app drag and drop

> Read when implementing multi-window management (Android split-screen / DeX, Desktop multi-monitor), document side-by-side comparison, or cross-window / cross-app drag and drop.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Motivation: workstation productivity on tablets and PCs

Modern student and research workflows require comparing textbook materials against working notes. Rather
than forcing users to switch tabs continually, MobiXournal's workspace architecture supports:
1. **Side-by-side split view** across two independent documents or two distant sections of the same document.
2. **True multi-window support** (Android freeform windows, Samsung DeX, and Desktop multi-monitor setups).
3. **Fluid cross-window drag and drop** of living elements, formulas, tables, and inking clippings.

---

## Workspace window topology

```text
┌─────────────────────────────────┐   ┌─────────────────────────────────┐
│     Window A (Lecture PDF)      │   │   Window B (MobiXournal Notes)  │
│                                 │   │                                 │
│   ┌─────────────────────────┐   │   │   ┌─────────────────────────┐   │
│   │ [Selected Formula Block]│═══╪═══╪══>│ [Pasted Dynamic Plot]   │   │
│   └─────────────────────────┘   │   │   └─────────────────────────┘   │
│                Drag & Drop Payload (MIME: application/vnd.mobixournal.element)
└─────────────────────────────────┘   └─────────────────────────────────┘
```

### 1. Document sharing model between windows
When two windows or panes (see [data-path.md](data-path.md)) display the **same document**:
- Both windows observe a single shared `DocumentState` model.
- Committing a stroke or modifying a `TableElement` in Window A immediately issues an invalidated dirty-rect
  event to Window B, keeping the views in lockstep.
- Undo/redo stacks are scoped per document, avoiding race conditions between different viewport editors.

---

## Cross-window and cross-app drag and drop

MobiXournal leverages standard OS drag-and-drop protocols (Android `View.startDragAndDrop` and Java/AWT
`Transferable` on Desktop) to provide seamless interoperability:

### Multi-flavored clipboard and drag payloads

When the user selects elements (via Lasso or Marquee, see [selection.md](selection.md)) and drags them:

```text
Drag Payload (ClipData / Transferable)
├── Flavor 1: "application/vnd.mobixournal.element"  (Full fidelity living JSON entity for MobiXournal)
├── Flavor 2: "image/svg+xml"                        (Crisp vector format for LibreOffice, Illustrator)
├── Flavor 3: "image/png"                            (High-DPI 300 DPI raster for web, Discord, email)
└── Flavor 4: "text/plain"                           (Extracted text or LaTeX/Typst formula string)
```

### Destination behaviors
1. **Drop into another MobiXournal window**: The target window inspects Flavor 1, reconstructing the living
   `TableElement`, `PlotElement`, or vector `Stroke` exactly as authored, complete with all interactive handles.
2. **Drop into external apps** (e.g. Word, LibreOffice, Anki): The destination selects Flavor 2 (SVG) or
   Flavor 3 (PNG), seamlessly inserting a clean, anti-aliased image of the handwritten note or formula.

---

## Ingestion drag and drop

Dragging external content into the MobiXournal canvas:
- **Images (PNG, JPEG, WebP)**: Dropped onto the active page as a new `ImageElement` centered at the drop point.
- **PDF Documents**: Dropped into the canvas prompts whether to insert as a multi-page background or attach as
  an annotated document page.
- **LaTeX / Typst text snippets**: Recognized and converted automatically into a live `MathBlockElement`
  (see [latex-typst-rendering.md](latex-typst-rendering.md)).
- **CSV / TSV text files**: Instantly parsed and converted into an editable `TableElement`
  (see [mobix-schema-spec.md](mobix-schema-spec.md)).
