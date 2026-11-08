# Document search and handwriting recognition

> Read when you change search, the handwriting index, or the recognition engines.
> For the task: [What works, and what is not there](guide-limitations.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## What a query searches

`DocumentSearch.find` walks the pages through `PageSource`, so a long `.mobix` notebook is read page by
page instead of being inflated whole. A match comes back as a `SearchHit` — a page index and the page-local
boxes to paint — and the three sources are consulted in one pass over a page:

- **Typed text** (`TextElement`): each line of the box is matched case-insensitively, and the hit's box is
  derived from the element's size with the same rough metrics element hit-testing uses.
- **Background PDF text** (`PdfTextIndex`): the page's words are joined into one string in reading order, so
  a query may span two words; the boxes returned are the words the match overlaps.
- **A math block's source** (`MathBlockElement`): the formula as it was typed, so `\frac{1}{2}` is
  findable; the block's own box is the hit, once per occurrence.
- **Handwritten ink**: the page's `HandwritingIndex` words, matched by the rules below.

The engine is `mobix-core`'s, and both shells call it: the tablet's field and the PC window match the same
things at the same places — **handwriting included**. What is platform-specific is only the *reading* of a
word (`InkWordEngine`), not the segmentation around it: the tablet plugs ML Kit's digital-ink model in, and
the window plugs in **Windows' own recognizer** (`WinInk.kt`, the one behind its handwriting panel, offline
and per-language) — so cursive written on the PC is found there too. A hit carries a `snippet` — the matched
line, words or formula, cut to a row — beside its boxes, which is what a results list prints.

## The window's field, results and highlight

`Ctrl + F` opens the desktop band's field (`HeaderSearch.kt`) and `Esc` closes it; `Enter` steps to the next
match and `Shift + Enter` to the previous, both wrapping. The answer is kept on the tab as `DesktopSearch`
(query, hits, current) and walked by `SearchRunner` on `Dispatchers.Default` once per settled query, so
repainting the page never re-walks the document. The sidebar lists the matches (`SearchSidebar.kt`, filled
only while a query is up) and the current one is painted over the page as chrome, at the PDF text
selection's translucency.

Two things are the window's own. Pages are read through the **tab's document** (`TabPages`), so the pages
being edited are the pages searched, unsaved strokes included. And the PDF half is keyed by each page's
**own** `Background.Pdf.pageNo`: the window reorders pages, so document page *n* is not PDF page *n*, and a
single index passed by position would highlight the wrong words.

Ink is read **lazily, a line at a time**: `PageHandwriting` segments a page when a query reaches it and reads
only the lines it has no reading for, keyed by the line's own ink (`InkLine.key`) — so a query typed letter by
letter reads a written page once, and an edit re-reads the **line it touched** while the rest of the page stays
read. `HandwritingCache` keeps that reader on the tab while the document is the same one, and `InkReading`
warms the page in view about 300 ms after a lift or a page turn. There is still no upfront index on the PC (the
tablet builds one, see below): a search costs the pages it reads, never the whole notebook.

The field stands on the band always, and two doors run the same `ShellCommand.FIND`: `Ctrl + F`, which hands
it the keyboard, and the menu's **Search** row.

Stepping through the matches is one pair of commands behind four doors: `Enter` and `Shift + Enter` in the
field, and the field's own two chevrons (up = earlier in the document, down = later) for a mouse. Each step
does two things, not one: it goes to the match's **page** and then moves the sheet **to the word**
(`Viewport.showing`) — a word twenty lines down a long page used to be off the bottom of the window, so
"next match" pointed at something the reader could not see. The viewport moves only when the box is not
wholly on screen, so stepping along one line does not make the sheet twitch, and a box taller than the
window is shown from its top. A click on a result row runs the same pair.

While the walk has not answered the query, the field's counter, the status bar's count and the results
heading all read **Searching…** instead of `0 / 0` — the count that means *this query found nothing*, which
used to be shown while ink was still being recognised. The rule is one property, `DesktopTab.isSearchPending`:
it compares the field's trimmed text with the query the answer on hand belongs to, because an answer carries
its own query and a flag raised at the start of the walk and lowered at the end can be left raised by the
cancellation a keystroke causes. The two chevrons and `Enter` refuse to step while it is true: the hits on
hand are the previous query's, and a step would take the reader to a word they are not searching for.

## How a handwritten word matches

A word carries its **primary** reading plus any **alternate candidates** the engine offered. Once the
query and the candidate are folded (NFD, combining marks stripped) and lowercased, a word matches when:

- a candidate equals the query;
- a candidate starts with a query of three characters or more;
- a candidate contains a query of four characters or more, and is at most three characters longer;
- or, for a query of five characters or more whose length is within one of the primary reading's, the
  Levenshtein distance is at most **1** — deliberately tight, so a short query cannot drift onto a longer
  word that merely resembles it.

A query also matches across the page's word sequence as a phrase, which is what makes two words work. Each
of those rules is a case in `DocumentSearchTest`, including the near miss that must not match.

## The index and its two engines

`HandwritingIndex.build` recognises the pages ahead of the first query — from a `Document` or, one page at a
time, from a `PageSource` — and keeps the **words**, not the strokes, because a word is what search and its
highlight boxes need. `HandwritingRecognizer` does the work, in `mobix-core`:

- strokes are filtered first — a minimum number of points and extent — so a tap, a full-width divider or a
  diagram's arrow is never read as a letter;
- bounds are computed once per stroke (`BoundedStroke`) and grouped into lines by a running centroid and
  average height (`LineCluster`), which keeps two close lines from collapsing into one;
- a line is split into words at its horizontal gaps;
- a word goes to the shell's engine (`InkEngines.platform` — ML Kit on the tablet, Windows' recognizer in
  the window, each set at startup) and to the pure-Kotlin `FallbackInkEngine` when that engine returns
  nothing: no Play Services, a model still downloading, the optional Windows handwriting feature not
  installed, or a JVM test without either runtime.

### The window's model, through WinRT

`WinInk.kt` reaches `Windows.UI.Input.Inking` with raw COM calls (`WinRt.kt`, JNA): the machine's metadata
gives each vtable slot, and the one object WinRT does not hand out — an `IIterable<Point>` for
`CreateStroke` — is built by hand. Three ABI facts cost a day between them and are worth knowing before
touching it: the ink objects are not agile, so they live on one thread entered as **STA** that pumps its
message queue while it waits; `IAsyncOperation<T>` carries only its handler and its result, so the status is
read through a `QueryInterface` for **`IAsyncInfo`** (reading the operation's own slot called `put_Completed`
and wrote a zero over the status, which then read as `Started` forever); and an iterator must stand *on* its
first point, because Windows asks `HasCurrent` before it ever moves.

`WinInkTest` is the live probe (it skips, rather than fails, where the handwriting feature is absent): it
asserts the model answers on print and on a connected line, and that the shared seam hands a word back.

A reading that looks wrong in a real notebook is measured, not guessed: `MobiXournal.exe --probe-handwriting
"<file>"` (`HandwritingProbe.kt`) opens nothing on screen and prints, page by page, the stroke count, the
median stroke height, the model's words with their boxes and the pure engine's beside them — so "the model
is not plugged in", "the model reads it as this" and "the ink never reached either reader" are three
different answers instead of one complaint. It runs from the packaged executable on purpose: that runtime is
where the bridge can fail while every JVM test passes.

## Indexing never blocks the canvas

`CanvasSearch` (`CanvasSearch.kt`) owns the query, its hits, the index and one background executor — the
canvas hands it only the document, the PDF text layer, a repaint and a reveal. Every request bumps its
generation; the running task checks its own generation as it walks the pages and stops as soon as a newer
request has made its answer irrelevant, so opening another document or typing in the field cancels the work
without a lock (`CanvasSearchTest` drives that with direct executors and a held job). The surface reports a `SearchStatus` (one-based current hit, and the total) to the chrome, and
`SearchControls` in `ui/EditorRegions.kt` draws the field, the count and the stepper;
`SearchIndexingDialog`, beside it, is what a document with handwritten ink shows while its first index is
built.

## What the tests pin

`DocumentSearchTest` covers the matching rules over a synthetic document: typed text, a PDF phrase across
two words, handwriting with and without an index, alternate candidates, the fuzzy bound, folded accents, a
multi-word phrase, and the shorter word that shares a suffix and must stay unmatched.
`HandwritingRecognizerTest` covers the recogniser itself: single characters, words grouped by gap, separate
lines, an index built from a document, a cursive connection, cancellation, the minimum-stroke filter, and a
timed 50-word indexing run whose one assertion is that the index came out with text in it.
`HandwritingEngineTest` (core) pins the seam: a `PageSource` is walked page by page, cancellation answers no
index, and `recognizeWord` asks `InkEngines.platform` unless a caller names an engine. On the window,
`DesktopSearchTest` finds a handwritten word through a fake engine, recognises a page once for two queries,
and throws the reading away when the document is edited.

The shared module owns the segmentation and the fallback engine; only the model-backed reading stays in
`app/` with ML Kit.

## A local model, measured before it is chosen

*Decision (2026-11-06).* The question was whether to read ink with a line-level model that travels with
the app, the way xournal-qt does on Windows (a 9.1 MB int8 CRNN beside ONNX Runtime), instead of asking
the platform. It is two questions, because the shells' engines differ: the **window** reads with Windows'
own recognizer (`WinInk.kt`), the **tablet** with ML Kit (`AndroidInkEngine.kt`) — and ML Kit is
Android-only, so on the PC a local model is the *only* route to that kind of reading, not a faster route
to one already there. Both were measured; the latency numbers below are the PC's.

### What one line costs

An int8 CRNN over 64-pixel-high lines, ONNX Runtime 1.31.1, CPU, four threads, on the owner's PC. It is
the closest model at hand, used as a ruler and **not** as a candidate: its weights are non-commercial.

| line width | median inference |
|---|---|
| 384 px | 6.1 ms |
| 512 px | 8.0 ms |
| 1024 px | 16.3 ms |
| 2048 px | 31.9 ms |

Session load is 28 ms and the cost is linear in width: **6–32 ms per line** on this machine. The window's
baseline is its Windows bridge — about 55 ms just to activate, 17 ms to answer a word, a 419-word page
read word by word taking 34 s, which is why `InkLineEngine` exists. A local model is therefore 2–10×
faster per line here, and it keeps working on a machine where the optional Windows handwriting feature is
absent and `WinInkTest` skips. ML Kit's published ~100 ms for a line is the tablet's order of magnitude.

### What a runtime and its weights add

On the **PC** size is not the obstacle. The packaged window is **147 MB**; ONNX Runtime's `win-x64`
library is **12.4 MB** (the `onnxruntime` jar is 139 MB because it carries every platform plus debug
symbols — we would ship the one DLL). LiteRT is not an option there: it is Android and Linux only. A
local reader on the PC therefore costs about +12 MB plus its weights, under a tenth of the image.

On the **tablet** the runtime is what costs. Measured from the artifacts themselves (`unzip -l` of each
AAR), uncompressed as they land in an APK:

| dependency | arm64-v8a native library |
|---|---|
| Google LiteRT 2.1.0, `libLiteRt.so` (AAR 7.6 MB) | 5.1 MB |
| ONNX Runtime 1.22.0, `libonnxruntime.so` (AAR 28.5 MB) | 18.2 MB |
| ML Kit digital ink 19.0.0, `libdigitalink.so` — already shipped | 6.9 MB |

The debug APK is 66.8 MB, 29.1 MB of it four copies of `libdigitalink.so` (x86, x86_64, arm64-v8a,
armeabi-v7a). A tablet is arm64-v8a, so an ABI split alone would free 22.2 MB — more than LiteRT plus a
13 MB model would add, and ONNX Runtime is ruled out on this axis (3.6× LiteRT's native library for the
same job). ML Kit's weights cost the APK nothing: about 20 MB **per language**, downloaded at runtime.

### What the quality would be

Neither shell publishes a CER for its own reader, so the published figures of the permissive candidates
are the only comparable ones — and they are not better than what the platforms already read:

- **PP-OCRv5 mobile recognition** (Apache-2.0, 17 MB LiteRT) is a general text-line model with
  handwriting *coverage*; its own handwritten-English line accuracy is 0.4944 — a coin toss per line.
- **TrOCR small** reaches IAM CER 4.22, but only as an int8 encoder/decoder pair of about 64 MB in ONNX,
  decoded autoregressively once per line; the checkpoint's weight licence is undocumented (the repository
  carries no licence field) although the code is MIT.
- **The small permissive CTC checkpoints** that do fit (Kraken PP-OCRv6 tiny: 2.84 MB, CER 8.71 / WER
  30.00) are 2–4× worse than a modern recogniser and ship as safetensors only, so a conversion toolchain
  comes with them; the 13 MB sibling (CER 5.43) lists IAM in its training data — the same non-commercial
  taint that keeps xournal-qt's own model out of a commercial build.

So no small, permissively licensed, line-level checkpoint measured here is worth shipping. And the
repository is GPL-2.0: weights offered for non-commercial use only — xournal-qt's own model claims WER
0.58 %, and its licence forbids exactly that distribution — are an added restriction we cannot pass on,
on either shell. A model that did fit would have to be trained for it.

### The decision

**Read on the stroke lift, not on the first query.** That is what makes recognition feel immediate, and it
costs no bytes:

1. **The window reads on the lift** (*wired 2026-11-06*). `InkLineEngine` was already the Windows bridge's
   call; `PageInkGesture` now warms the page about 300 ms after a lift and `SearchRunner` warms the page in
   view when one is opened or turned, and the answer is kept per line in the tab's `HandwritingCache` — a
   line is read once, and an edit costs the line it changed.
2. **The tablet needs the call first.** ML Kit is asked once per word across up to six language models;
   it recognises an `Ink` of many strokes, so a line is one call and one model.

**Then each shell keeps the engine it has.** The window keeps Windows' recognizer as its default — free,
offline, already installed — and the tablet keeps ML Kit. A local model is added behind `InkWordEngine`
only where the platform reader is missing or too slow, and on the PC that is a measured proposition:
+12.4 MB of runtime for 6–32 ms a line. What is missing is not the runtime but the **weights**, so the
seam waits for a model we can licence or train rather than for the plumbing.

### What it costs while you write

The question that decides whether this is usable is not the per-line figure but what the running app
pays. Measured on the same model, ONNX Runtime, this PC:

| threads | 512 px line | 1024 px line |
|---|---|---|
| 1 | 23.3 ms | 45.7 ms |
| 2 | 13.0 ms | 26.0 ms |
| 4 | 8.0 ms | 15.9 ms |

Session load is 20 ms and the session holds about 16 MB. Three consequences for how it is wired:

- **One or two threads, never four.** Latency halves from one to two and then the paint thread pays for it.
  Two threads at 13–26 ms a line is the setting that cannot be felt.
- **Recognise the line that changed, not the page.** A 30-line page re-read per stroke is close to a second
  of CPU per stroke; one line is 13–26 ms. The tab's `HandwritingCache` throws the whole page away when the
  edit count moves, so a per-line reading has to sit in front of it rather than inside it.
- **Bucket the line width.** ONNX Runtime's CPU arena grows with the shapes it sees (+27 MB over 30 varying
  widths in this run); a few fixed widths, or the arena off, keep it flat. Loaded lazily on the first
  recognition, none of this delays the window opening.
