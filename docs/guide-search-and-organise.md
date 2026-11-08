# Finding a word, and rearranging the pages around it

> Read when a notebook has grown long enough to search, or you need to move, prune or bookmark the pages
> in it.

_A guide page for Android and Windows: what a query reaches, and the page furniture that keeps a long document navigable. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.0.0** and desktop **1.0.1**.

## What one search looks through

A query is run over four things at once, page by page: text you typed, the text layer of a PDF
background, the **source** of a formula block — so `\frac{1}{2}` is findable — and handwriting. Results
come back with a snippet and the boxes to paint on the page.

The engine is shared, so the tablet and the window find the same things in the same places. What differs
is how a handwritten word is *read*: ML Kit's digital-ink model on Android, Windows' own recogniser in the
window. Both run offline and per language, and both fall back to a small built-in engine when the
platform one returns nothing — no Play Services, a model still downloading, or the optional Windows
handwriting feature not installed.

That fallback is why the same handwriting can be found on one machine and missed on another. It is a
recogniser difference, not a lost index.

## Why a handwritten word did not match

The rules are deliberately tight, so a short query cannot drift onto a longer word that merely looks
similar:

- the query must equal a reading the recogniser offered;
- a query of three characters or more may match the **start** of a reading;
- a query of four or more may match **inside** it, but the reading may be at most three characters longer;
- a query of five or more may be one edit away, and only when its length is within one of the primary
  reading.

The page's word sequence is matched too, which is how a phrase across two words works. In practice: type
more of the word, and remember the recogniser searches its alternate candidates, not only its first
guess.

## Search never stops the page

Recognition happens ahead of the first query and runs off the drawing path; a long notebook is read page
by page rather than inflated whole. A search over a big document is therefore something you wait for in
the results list, not a stall in the ink.

## Adding, moving and pruning pages

The Pages menu carries a navigator, two quick sizes, *Add page* and *Remove page*, then the page's format
and background with their current answers printed on the row. **Manage pages** is its own section at the
foot on both shells: move a typed range so its first page starts on the page you name, or remove a typed
range — one undo step each.

A range that would empty the document is refused rather than half-honoured, and so is a move that already
starts where you asked. In the window, dragging a thumbnail down the page sidebar reorders as you go, and
the list scrolls when you hold the pointer against an edge, so a page from the top of a scan can be
dropped at the bottom without letting go.

## Bookmarks

A bookmark is a labelled, colour-coded flag on a page, and the point of it is finding a page again in a
document too long to scroll. It is **navigation the app keeps**, not document content: the file stays
byte-identical whether or not a page is flagged, and a flag never becomes an undo step.

The flags are painted on the sheet's top-right corner, and they survive the page being moved or a page
being inserted before them. They belong to this app's record for that document, so they do not travel
with the file to another device.

## Tabs, split view, and what a tab switch costs

Several documents can be open at once, but only the focused one is live — the others hold the snapshot
taken when they were last on screen. Switching tabs therefore starts the incoming document with a clean
undo history, while its content, unsaved edits included, is exactly as you left it.

The open tabs are cached and restored when the app starts again, unsaved edits and all. That is a restart
cache, not a save: the file you named is still the only copy another app ever sees.

**Split view** is two panes, not a second mode. One pane has focus, and the top bar and rail always act on
it; in split view each pane keeps its own tab strip over its own half, and touching anywhere in a strip
focuses that pane. The same document in both halves is a mirror, and it edits as one document rather than
two copies.

## Layers, briefly

A page is drawn from layers, and the sidebar lists them; a living element is added to the top layer. For
most notebooks you never touch the list — it matters when you want to hide a layer's work, or when an
element seems to be somewhere you cannot click.

## How it works

- [search.md](search.md) — the four sources, the match rules and the two recognisers.
- [pages-and-paper.md](pages-and-paper.md) — page ranges, bookmarks, paper and the page list.
- [document-session.md](document-session.md) — tabs, the session cache, and split view.
