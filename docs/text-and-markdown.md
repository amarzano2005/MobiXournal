# Text and markdown: import, layout, generated PDFs

> Read when you change a text or markdown import, the paginator, the markdown parser, or the PDF those imports are annotated against.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## `TextPaginator`: one layout, two forms

`TextPaginator` exposes both a **list** form (`wrap`/`paginate`/`layout(String)`) and a **streaming**
one (`wrapLines`/`paginate(Sequence)`/`layout(Sequence)`/`layout(Reader)`) that wraps line-by-line and
yields each page the moment `linesPerPage` lines have accumulated — so memory scales with one page
rather than with the file, and a page can be written out before the input is fully read. The list form
is a thin wrapper over the streaming one, so both give byte-identical layout.

## Generating the text-import PDF (`TextPdfGenerator`)

**Generating the text-import PDF.** `TextPdfGenerator` turns a plain-text file into the PDF a text
import is opened against. It owns only the authoring — layout is entirely `TextPaginator`'s — and
emits **real, selectable text**: a white sheet per page, then one `beginText`/`setFont`/
`newLineAtOffset`/`showText` per laid-out line at `heightPt - baselineFromTop(i)` (PDF's origin is
bottom-left, the paginator's is top-left). Nothing is rasterised and no OCR is involved, so
`PdfTextExtractor` recovers the original characters and text selection works on an imported `.txt`
exactly as on a born-digital PDF. Fonts are **injected** as a
`(PDDocument, PdfFonts.Face) -> PdfFonts.Embedded` loader, keeping the generator free of
`AssetManager` and unit-testable on the JVM; the plain path draws in one `plainFace`, monospace by
default — the sensible choice for logs and source. An empty file still yields one blank sheet to
annotate.

`generate` comes in two forms: one taking the text as a `String`, and a **streaming** one taking the
staged `File`. The streaming form reads the source a line at a time and drains `TextPaginator`'s lazy
page sequence page by page, so neither the file's text nor its wrapped lines are ever held whole —
that is the form `TextImport` uses. Authoring is still **one** `PDDocument`: writing it in bounded
chunks and concatenating them with `PdfMerger` was implemented and measured, and is *worse*, because
PDFBox 2's `legacyMergeDocuments` rebuilds the entire joined document in memory before writing (a
16 MiB source peaked at 405 MiB chunked against 319 MiB unchunked). The finished document is
therefore the memory ceiling, and it grows slowly: 128 MiB of source is ~31k pages at 479 MiB peak.
The text-import cap in `AppSettings` now bounds *time and output size* rather than memory, which is
why it defaults to 64 MiB.

## Authoring the markdown PDF (`render/MarkdownPdfWriter.kt`)

**Authoring the markdown PDF (`render/MarkdownPdfWriter.kt`).** The markdown flavour keeps the same
split — pure layout in `render/markdown/`, PDFBox only here. `TextPdfGenerator.writeMarkdown` derives
a `MarkdownStyle` from the caller's `PageSpec` (page geometry, body size and line-height carry over;
the markdown-only sizes stay at their defaults), runs `MarkdownLayout.layout`, and hands each
`MarkdownPage` back to the writer. Three differences from the plain path:

- **A line is many fragments, not one string.** Each `RunFragment` gets its own
  `beginText`/`setFont`/`newLineAtOffset`/`showText` at `margin + indentPt + fragment.xPt`, because
  neighbouring fragments differ in face. A list `marker` is drawn the same way at its (negative)
  `markerXPt`, so bullets hang in the gutter the indent opened.
- **All fragments on a line share `line.fontSizePt`.** That is the size the composer measured them
  at, including inline code spans — code takes a different *face*, not a different size, inside a
  paragraph. Only a code *block* gets `codeFontSizePt`, and the composer stamps that onto the line.
- **A rule is a filled rect, not a stroke** — `addRect`/`fill` centred on the placed `yPt`, which
  needs no line-width or stroking-colour state and so cannot leak graphics state into later text.

A source that yields no blocks still gets one blank sheet, matching the plain path.
`MarkdownPdfWriterTest` closes the loop end-to-end: markdown in, real PDF out, `PDFTextStripper` back
out again — asserting the words survive, the markup characters do not, and more than one face is
embedded on the page.

## Wiring a text file into the open path (`io/TextImport.kt`)

**Wiring a text file into the open path (`io/TextImport.kt`).** A `.xopp` cannot represent "a text
file" — the only thing that round-trips is a PDF background — so `DocumentIo.read()` short-circuits
`FileKind.TEXT` the same way it does `FileKind.PDF`: typeset the bytes, return
`LoadedFile.Pdf(generated = true)`, and every path downstream (background rasterisation,
`PdfImport.documentFor`, text selection, saving) runs unchanged with no text-specific branch. The
generator is injected into `DocumentIo` (it needs the bundled fonts, and so an `AssetManager`), which
keeps the rest of the class Android-free.

## Markdown routes on the name, not the bytes

**Markdown routes on the name, not the bytes.** A `.md` file is printable UTF-8 like any other text,
so sniffing cannot separate the two: `FileKind.of` still returns `TEXT` for markdown, and the
markdown verdict is a **second, name-level** one — `FileKind.isMarkdownName(name)` matches a `.md` /
`.markdown` suffix (case-insensitively) on the *display* name SAF hands `DocumentIo.read()`. That is
the single place in the open path an extension is consulted, and it is consulted only to pick a
**flavour**, never a format. `TextImport.pdfFor` turns the name into a `render/TextFlavor`
(`PLAIN` · `MARKDOWN`) and rides it through `TextPdfGenerator.generate(…, flavor)` rather than
forking a parallel import class — everything downstream of the generated PDF is identical, so a
second path would buy nothing. The `MARKDOWN` branch in the generator runs the markdown parser,
layout and `MarkdownPdfWriter` in place of `TextPaginator`. Each flavour carries its own `cachePrefix` (`text:` / `markdown:`) so the
same bytes opened as `notes.txt` and as `notes.md` cannot collide in `PdfStore`.

## Parsing and laying out markdown (`render/markdown/`)

**Parsing markdown (`render/markdown/`).** Structure and geometry are split the same way plain text
splits them: `MarkdownParser` turns source into a tree of `MarkdownBlock` and stops there — no
measurement, no wrapping, no pages — exactly as `TextPaginator` is pure geometry with no knowledge of
markup. The parser is dependency-free by policy (no CommonMark library): it is a **line-based
recursive descent** over normalised lines (CRLF→LF, tabs expanded once, so indentation is
countable), with the per-line "what does this start?" rules factored into `MarkdownLine` so each can
be tested against a single string.

Two decisions are load-bearing:

- **Nesting is the tree, not a depth field.** `Quote` holds its child blocks and `ListItem` holds
  its child blocks, so containers *recurse*: a quote's stripped content and a list item's dedented
  content are each re-parsed as a document of their own. A list item containing a code block, or a
  quote containing a list, therefore needs no special case, and no `depth` integer can fall out of
  sync with the structure — layout counts depth as it descends.
- **Inline markup stays raw.** A `Paragraph` or `Heading` carries its source text with `**bold**`
  and `[a](b)` intact; decoding spans into styled runs is a separate pass, which keeps block
  structure testable on its own and stops one parser from doing two jobs.

The dialect is the common core of CommonMark — ATX and setext headings, paragraphs with lazy
continuation, fenced and indented code, ordered and unordered lists with nesting, block quotes,
thematic breaks. Reference links, tables, HTML blocks and footnotes are out of scope and survive
verbatim inside a paragraph rather than being mangled. `MarkdownParserTest` covers each block type
plus the cases a hand-written line parser gets wrong: lazy continuation, loose lists, unclosed
fences, `* * *` beating a bullet marker, CRLF and tabs.

**Inline spans → styled runs.** The deferred second pass is `MarkdownInlineParser`: raw inline
source in, a flat list of `StyledRun` (`text` + `bold`/`italic`/`code`) out — still pure, still
unmeasured. Runs are **flat rather than a tree** because wrapping only ever asks "which font do I
measure the next word in?", so nested `**bold *and* italic**` is just adjacent runs with different
flags. It runs in two small stages: `InlineScanner` walks the source once and resolves everything
decidable locally (backslash escapes, code spans including the double-backtick form and the
symmetric pad-space rule, and link/image labels), emitting `*`/`_` as unresolved delimiter runs
tagged with CommonMark's flanking rules; `InlineEmphasis` then pairs those runs with a
delimiter-stack walk (including the rule of three), stamping styles onto the tokens between each
match. Nesting needs no recursion because a token can be stamped twice, and any delimiter that never
finds a partner prints literally — which is what keeps `2 * 3 * 4` and `snake_case_name` intact.

Two decisions here:

- **Link URLs are dropped; the label renders.** `[label](url)` becomes just `label` (and
  `![alt](url)` just `alt`). The output is a printed PDF page where a URL is neither clickable nor
  wanted mid-sentence.
- **A label is frozen into finished runs before it is spliced in.** Parsing a label through the full
  pipeline (rather than splicing its raw tokens) is what stops a leftover delimiter inside a label
  from pairing with one outside it.

**Styled runs → laid-out lines.** `StyledWrapper` is the markdown counterpart of `TextPaginator`'s
wrapping half: it takes styled runs plus a `(RunStyle, String) -> Float` measurer — one metric per
face (`REGULAR` · `BOLD` · `ITALIC` · `BOLD_ITALIC` · `CODE`, with `code` beating emphasis) — and
returns lines of `RunFragment`s, each a stretch of one face with an x offset and width. Baselines
stay pagination's job, so a fragment carries no Y.

The break rules are deliberately the plain path's: greedy word fill, and a mid-word hard break only
when a word can't fit a line alone. The shared character-level pieces (tab expansion, hard break)
live in `TextWrapping`, which both wrappers call, rather than being copied. What genuinely differs is
that a *word* can span runs — `**bold**tail` is one unbreakable word in two faces — so words are
tokenized as (style, text) segment lists and measured segment by segment, and adjacent same-face text
merges into one fragment, re-measured rather than summed so kerning stays honest. Inline whitespace
collapses to a single separator (markdown's own rule; verbatim spacing belongs to code blocks, which
are never inline-wrapped). Like the parsers it is pure, so `StyledWrapperTest` drives it with
synthetic metrics where bold is deliberately wider than regular and checks that a face change alone
moves a break.

**Blocks → pages.** `MarkdownLayout` is the top of the markdown path (`layout(source, style, measure)`
→ pages), and it runs in two halves for one reason: page breaking wants to see whole blocks.

- `MarkdownComposer` walks the block tree and emits a flat list of `MarkdownGroup`s — one per block,
  each holding the drawable `MarkdownItem`s it produced (`Line` · `Rule` · `Space`). Headings are set
  bold at their level's size, paragraphs at body size, code verbatim in monospace at its own fixed
  size (hard-broken to fit, never re-flowed on words). Nesting is plain recursion at a larger indent,
  so a list inside a quote inside a list needs no depth counter; a list marker (`•` or `3.`) is hung
  on the item's first line, one indent step into the gutter. Gaps between blocks **collapse** — the
  larger of the space below one block and above the next, never their sum.
- `MarkdownLayout.paginate` then walks the groups with a pen offset, giving each line a baseline at
  the foot of its box and each rule the centre of its own. A block longer than a page simply flows
  onto the next; a `Space` at the top of a page disappears, so a page never opens with a gap; and a
  group marked `keepWithNext` (only headings) moves to the next page when it plus the first line of
  whatever follows won't fit, which is what stops an orphaned heading at the page foot.
- Every size lives in `MarkdownStyle`, the markdown-flavoured `PageSpec`: sheet and margins, the six
  heading sizes, heading and block gap ratios, list/quote/code indent steps, code type size, and rule
  height and thickness. Like the rest of the path it is Android- and PDFBox-free, so
  `MarkdownLayoutTest` lays documents out against synthetic monospaced metrics and asserts the breaks
  arithmetically.

Two consequences fall out of the generated PDF living only in the cache:

- **It is cached by content, not copied.** `PdfStore.cached(key) { … }` keys the generated file on a
  SHA-256 of the file's display name followed by its bytes, **streamed** through the digest a buffer
  at a time so a large import never becomes a large `String` (the staged copy's own name is per-open
  scratch and would never hit), recording key → file name in an `index.tsv` sidecar the sweep skips.
  Reopening the same file while the entry survives reuses the PDF instead of typesetting it again.
  `MainActivity.adoptPdf(inStore = true)` then takes the store file **in place** rather than copying
  it, so the cached file *is* the tab's `pdfPath` — which is what keeps liveness pruning from
  sweeping it out from under its own cache entry. A cached entry deliberately **outlives its tab** —
  `prune` keeps every file the index still names — so the cache is bounded by a **byte budget**
  instead: once the folder exceeds `StorageLimits.pdfCacheBytes` (Settings → Storage), `prune`
  evicts the oldest non-live files until it fits, and drops the index entries whose file it deleted,
  so an evicted entry simply regenerates on the next open.
- **It must be saved `ZIPPED`, not `ORIGINAL`.** There is no stable on-disk source to link: the
  cache path would be swept and the document would reopen blank. So the text branch makes the sticky
  save format `ZIPPED`, and `encode` embeds the bytes through the existing `domain="attach"` path.
  `TextImportRoundTripTest` guards the whole journey — open text → annotate → save → reopen — and
  `TextImportTest` the caching contract.
- **It is bounded by a size cap.** Typesetting holds the whole text *and* its whole laid-out page
  list in memory, so an unbounded import of a several-hundred-megabyte log would stall the app and
  fill the cache. `TextImport.pdfFor` therefore checks the staged file's **length before reading a
  byte** against `StorageLimits.textImportBytes` and throws `TextTooLargeException` when it is over;
  `MainActivity`'s existing "Open failed: …" toast shows the message, which names both sizes and
  points at Settings → Storage. Both limits are pushed into `DocumentIo.limits` by
  `MainActivity.applyStorageLimits` on load and on every settings edit, since `DocumentIo` outlives
  any one `AppSettings` value.

Those JVM tests need one build-level accommodation: PDFBox reads its **CMap data** (`Identity-H`,
needed by every `PDType0Font`) out of the AAR's `assets/` through Android's `AssetManager`, which
unit tests don't have. `app/build.gradle.kts` therefore extracts the AAR assets into the unit-test
resources (`extractPdfboxAssets`), where PDFBox's classpath fallback finds them, and sets
`unitTests.isReturnDefaultValues` so FontBox's `android.util.Log` calls no-op instead of throwing.
