# Document search and handwriting recognition

> Read when you change search, the handwriting index, or the recognition engines.

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

Ink is read **lazily and once**: `PageHandwriting` recognises a page the first time a query reaches it, and
`HandwritingCache` keeps that reading on the tab until the document's edit count moves — a query typed letter
by letter recognises a written page once, and an edited page is read again. There is no upfront index on the
PC (the tablet builds one, see below): a search costs the pages it reads, never the whole notebook.

The field has three doors, all running the same `ShellCommand.FIND`: `Ctrl + F`, the magnifier capsule in the
band beside undo/redo, and the menu's **Search** row.

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

`DrawingSurfaceView` owns the index and one background executor. Every request bumps `indexingGeneration`;
the running task checks its own generation as it walks the pages and stops as soon as a newer request has
made its answer irrelevant, so opening another document or typing in the field cancels the work without a
lock. The surface reports a `SearchStatus` (one-based current hit, and the total) to the chrome, and
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
