# Advanced PDF annotation engine: snap-to-text, indexing, and ISO PDF/A export

> Read when implementing smart highlighter text snapping, background PDF full-text indexing, or exporting native ISO PDF annotation dictionaries.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Beyond rasterized backgrounds

While [pdf.md](pdf.md) documents the low-latency raster tile cache for PDF backgrounds, academic and
professional workflows require intelligent, bidirectional interaction with the underlying document text.

This engine introduces two advanced capabilities:
1. **Snap-to-Text Highlighter**: Automatically straightens and constrains freehand highlighter strokes to text lines.
2. **Native ISO PDF/A Annotation Export**: Produces interoperable annotations compatible with Adobe Acrobat and Okular.

---

## 1. Snap-to-text highlighter ("Magnetic highlighter")

When highlighting with a pen or stylus over born-digital PDF pages, freehand strokes tend to look wavy
and misaligned. The snap-to-text algorithm aligns the highlighter geometry to the underlying glyph boxes:

```text
User Freehand Drag:  (~~~~ wavy stroke ~~~~)
Glyph Line Boxes:    [La] [matrice] [jacobiana]
                              │
                              ▼ Spatial Raycast & Bounding Box Fusion
Snaped Highlight:    ┌─────────────────────────┐
                     │ La matrice jacobiana    │  (Perfect rectangular translucent fill)
                     └─────────────────────────┘
```

### Snapping algorithm
1. **Line Detection**: During an active `HIGHLIGHTER` drag, the stroke coordinates are tested against
   the page's glyph layout extracted from the PDF text layer.
2. **Glyph Clustering**: Adjacent characters overlapping the stroke raycast are clustered into line runs.
3. **Geometric Snapping**: The stroke path points are replaced by a uniform, flat rectangular bounding band
   aligned with the text baseline, ascenders, and descenders.
4. **Tolerance Gate**: If the user draws vertically or crosses across multiple lines at a sharp angle,
   snapping releases into freehand mode to allow margins and diagrams to be highlighted unconstrained.

---

## 2. Inverted full-text index (`PdfTextIndex`)

Large PDF textbooks (300–800 pages) can stall search interfaces if text is extracted linearly on each query.

- **Background Indexing Worker**: On first document open, a low-priority coroutine parses the PDF text
  streams and constructs a tokenized inverted index:
  $$\text{token} \longrightarrow \left[ (\text{page}, x_0, y_0, x_1, y_1), \dots \right]$$
- **Instant Search**: Typing in the search bar matches tokens in $< 2\text{ ms}$, immediately highlighting
  all occurrences across the document thumbnail strip.
- **Index Cache**: Stored in `.mobix/assets/pdf_index.bin` (see [mobix-schema-spec.md](mobix-schema-spec.md))
  so subsequent opens require zero extraction overhead.

---

## 3. Native ISO PDF/A annotation export

Standard Xournal++ exports burn all annotations into the PDF content stream, making them permanent and
un-editable in third-party readers.

MobiXournal provides an optional **Native PDF Annotations** export mode:
- **Text Highlights**: Written as `/Highlight` annotation dictionaries with `/QuadPoints` referencing the text.
- **Handwritten Ink**: Written as `/Ink` vector paths.
- **Sticky Notes & Audio**: Written as `/Text` or `/Sound` annotation popups.
- **Compatibility**: The exported PDF remains fully editable, commentable, and searchable in Adobe Acrobat,
  Apple Preview, Okular, and web PDF viewers.
