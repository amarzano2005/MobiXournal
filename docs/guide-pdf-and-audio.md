# Annotating a PDF, and recording while you write

> Read when you are about to import a PDF to write on, or record a lecture and want the recording tied to
> the stroke it was written over.

_A guide page for Android and Windows: the two workflows that bring outside material onto the page. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.4.0** and desktop **1.4.1**.

## A PDF becomes the page's background

**Import PDF** reads the file first, then asks two things: which pages and where they go. The card reads
top-down — the file's name and its page count, then the pages, then the destination — and the button under
it names the answer you picked instead of a bare *Import*, so you never have to remember which of the two
is selected.

**The pages are a selection you type, not a checkbox per page.** One field takes the reader's own grammar:
`2,4-6,9` for a chapter with holes in it, and `3` or `3-5` when it is one stretch. Under the field the card
answers back with the count — *4 of 108 pages* — so the number being decided on is on screen before the
button is pressed. A page named twice is imported once, and the pages always arrive in the file's own order
whatever order they were typed in, because that is how the document will hold them. A letter, a page past
the end or a run typed backwards is refused with a sentence (*Pages are numbered 1 to 108*), never rounded
to something else, and the confirm button greys out meanwhile. On the tablet the tool that selects a
passage later is the Select slot's third entry, as under [Copying text out of the page](#copying-text-out-of-the-page).

On the tablet the second answer is **Replace this document's pages**: the PDF becomes this document and the
pages already open are gone. In the window it is **Open in a new tab**, because that is what its command
does — a window with tabs has no reason to throw the open document away, so *replace* there means the PDF
arrives as a document of its own and what you had open is untouched. **Add to this document** reads the
same on both and is the default, because it keeps the work. Appending is one undo step, and a single
untouched blank sheet in front of the PDF is dropped rather than left stranded.

**The card fits the space it is given.** It asks for at most 420 dp of width and takes less when the window
or the pane is narrower, and only its title and its two buttons stay put — the file name, the page count,
the range, the error and both answers scroll inside the height that is left. So a short window, a
split-screen pane or the on-screen keyboard takes room from the body, never from the buttons: at 150 % text
in a pane roughly 370 dp wide, with the keyboard up, the fields, the *Pages are numbered 1 to 3* error and
Cancel are all reachable by scrolling, and the confirm button greys out rather than importing a range that
cannot be used. Under a narrow card or a large text scale the footer stacks, instead of Material squeezing
the two buttons onto unrelated lines. Both shells share those size rules, so the window and the tablet
answer a cramped place the same way.

**The card works without a finger.** `Tab` and `Shift+Tab` walk it top-down — the pages field, the two
answers, Cancel, then the confirm button — and `Space` takes whichever answer holds focus, so the pages and
the destination can be set from a keyboard alone, in the window and on a tablet with one attached. `Esc` in
the window and `Back` on the tablet close the card without importing anything. The focused answer draws a
ring around itself, so where the next `Space` will land is visible and not guessed. The destination you
picked stays picked while you go back and change a page number, so the two answers are not re-decided every
time the field is touched.

**A screen reader hears one answer at a time.** Each of the two rows is a single radio choice carrying its
name and its note as one announcement — *Add to this document. Continues after the pages already open, as
one step you can undo.* — followed by whether it is the chosen one, instead of a radio button announced on
top of two unrelated lines of text. Double-tapping a row with TalkBack selects it. A range the file cannot
use marks the field red, reads *Pages are numbered 1 to 3* as the field's error rather than as a stray
label, and the confirm button greys out, so nothing is entered that could not be imported.

**The pages are also pictures you can look at.** Under the field both cards show a grid of the file's pages,
numbered, each with its preview: tap one to pick it, tap it again to drop it — the field above rewrites
itself to match (`2,4-6,9`), and typing in the field lights the same previews up, because the selection is one
answer seen twice. **Select all** and **Clear** write that answer the short way. Only the previews on screen
are drawn, a handful are kept, so an eight-hundred-page scan scrolls as a grid rather than loading as a file;
the pictures and the PDF they came from are released the moment the card is answered or cancelled. Each
preview is a checkbox to a screen reader — *Page 3, selected* — so `Tab` walks into the grid and `Space` picks
the page under focus, exactly as a finger does, and the focused cell draws a ring.

A document holds one PDF. Import a second one onto a document that already has one and the two are merged
into a single file, so every page's PDF page number still points at the right sheet; the merge and the new
pages land together, which is what makes undoing it put the document back as it was.

## Getting a PDF out, a page at a time

**Export PDF** — the header's red PDF glyph, or the menu's row — asks *which pages* before the system asks
where. Three answers: **All pages**, which is what the card opens on because that is what an export always
was; **This page**, the one in view; and **Pages…**, typed in the same grammar the import card teaches
(`2,4-6,9`). Under them the card repeats the count it is about to write — *2 pages of 108* — and a page past
the end is refused with the sentence the import card uses rather than rounded to something else.

A notebook of **one page** is asked nothing, on either shell: the card would only offer three ways to say
"the only page there is", so the export goes straight to the question of where the file should go.

The file keeps the **notebook's** order, never the order the pages were typed in: exporting pages 2 and 4 of
a reordered notebook gives a two-page PDF whose first page is the sheet that reads first on screen. What each
page already is survives the subset — an imported page stays its source PDF's own vectors and text, the ink
sits on top as page content, and the ruling, the pictures and the layers are the ones the page shows.
**Cancel** writes nothing and leaves the notebook exactly as it was.

The exported file carries the **Contents** too: its bookmark pane lists the index you edited — renamed and
reordered rows included, in the order they read — and each row opens the sheet its page was written as, so a
bookmark follows its page through a subset export. A chapter whose own page you left out stays in the list as
a heading for the sections that did make it; one with nothing exported under it is left out rather than
pointing at a sheet you did not pick.

## Copying text out of the page

A born-digital PDF's text layer is extracted when you import it, so a passage can be selected and **Copy**
puts it on the clipboard. There is no mode to pick first: **hold a finger still on a word** and the range
starts under it with whatever tool the pen is on — drag to extend it, let go for the Copy/Highlight/Underline
bar, and a finger that moves instead still pans or pinches as before. In the window a **double click** on a
word does the same with any ink tool in hand; `Shift+S` is the keyboard and screen-reader entry, and `Ctrl+C`
copies. The selection is a view over the PDF, not something written into your notebook: it is never saved, and
`Esc`, a tap or click off the text, a drawn stroke or a page change clears it.

**The passage looks like selected text, not a row of tiles.** The tint covers each line whole, the spaces
between its words included, and it stops at a column gap instead of running across it. As you drag, only the
edge of the tint moves — nothing fades in, scales or slides as one block — and the words stay readable
underneath. It is still the text the PDF carries, not a reading of a picture: a scan selects nothing here.

**You can adjust a passage instead of selecting it again.** A range keeps two dots, one at its first word and one at
its last: drag either dot to add or drop words, in reading order, and if you drag a dot past the other end the
selection simply turns around — the dot stays under your finger and nothing jumps. The dots are the same size at
any zoom, so an end is as easy to take at 30 % as at 300 %. In the window, `Tab` reaches a dot and the arrow keys
move that end one word at a time, and the same two moves sit among the dot's screen-reader actions; on the tablet
the bar stays out of the way until you let go, exactly as it does while you are still dragging out a range.

**The page says what it can do here.** A hold that lands on no words brings a small line over the page, so
the gesture never fails silently: *Reading this PDF's text…* while the file is still being read, *No text on
this page* for a scan — a picture of text, which this app ships no OCR for — and *Could not read this PDF's
text* with a **Try again** beside it when the read broke, since the same file often reads on the second
attempt. A page with words says nothing: the hold simply works. The line belongs to its own pane, so a second
pane showing another document never explains itself on the first.

**Highlight marks the words in place.** Beside Copy the bar carries **Highlight**: the passage you selected
becomes highlighter ink on the page — one band per line, in the highlighter's own colour, on the layer you are
writing on — and the whole selection is **one undo step**, so undo takes back every line at once and redo puts
them back. It is ordinary highlighter ink, not a PDF annotation object: it saves, reopens in `.mobix` and
`.xopp`, and reaches an exported PDF the way any drawn highlight does. A document opened read-only keeps its
Copy and has no Highlight.

**Underline draws the passage instead.** **Underline** is Highlight's sibling and works on the same selection:
each line of the passage gets one thin, level pen rule just under its words — one rule per line, and two columns
on one line get two, never a stroke across the gutter — drawn in your **pen's** colour and width, on the layer
you are writing on. The whole passage is still **one undo step**, so undo takes back every line at once and redo
puts them back. It is ordinary pen ink, not a PDF annotation: it saves and reopens in `.mobix` and `.xopp`, and
an exported PDF carries it exactly like a line you drew by hand. A document opened read-only keeps its Copy and
takes no Underline either — its two writing words stay in the bar, dimmed, and a screen reader is told why.
The bar carries all three verbs whatever tool the pen is holding.

**Copy rides with the words.** The bar does not sit at the bottom of the screen, and it does not follow your
finger: it appears when you let go of the drag, beside the line you last touched — below it, above it when the
page ends there — so the tint that shows what is selected is never hidden by the buttons that act on it, and
nothing chases the pointer while you are still choosing words. It carries two words, each a
compact pill on a full-size tap target, so it stays small over the page. Near an edge, or with the
text enlarged or a split-screen pane, the bar is measured and pulled inside the pane instead of hanging
over its edge, and `Tab` puts a ring around Copy or Highlight when a keyboard is attached. There is no
Deselect word: a tap on the page or `Esc` lets the selection go, and a screen reader finds **Deselect**
among the bar's actions. The words
copied are the PDF's own, exactly the ones under the tint: Copy leaves your document untouched, and Highlight
marks exactly those words.

A scanned page is a picture of text, not text. It has no words to select, and this app ships no OCR for
it — the limitation is tracked, not hidden.

## The highlighter that finds the line

Highlight across PDF text with a drag that is clearly horizontal and the app snaps each line it crosses
into one constant-width band as tall as the glyphs plus a little air, previewed while you drag and
committed at lift-off. It is still ordinary highlighter ink afterwards, so it survives a save and a
conversion, and one gesture is still one undo step.

The snap is a convenience, never a correction you did not ask for. A margin mark, a steep stroke or a drag
over no text stays exactly as drawn.

## Following the PDF's own contents

On Android, tap the **bookmark ribbon** in the **Page list** capsule in the top bar. The **Bookmarks** tab
shows the PDF's outline beside the document; **Pages** switches to page previews in the same panel.
On a tablet, choosing a bookmark leaves the list open for the next jump. In a compact window, choosing a
page closes the panel; the ribbon opens it again with the filter and folded chapters retained.
The Android panel has been exercised on the Pixel_Tablet emulator at tablet and phone sizes in both themes;
[the implementation notes](pdf.md#pdf-contents-the-outline) record the test and its fixture. On large notebooks the panel can show **Reading bookmarks…** while destinations load. A failed read offers **Try again**; you can close the panel at any time.

Tapping goes by the PDF's own page number rather than by a count, so
even a merged PDF lands on the right sheet; an entry with no destination inherits the page of the nearest
chapter below it, and an entry that points nowhere is listed but not tappable.

**The list reads as a tree, not as lines of text.** A chapter stands in the heading face and its sections
sit smaller and lighter under it, each level indented a step further — but the indent stops before the
title does, so an eight-deep bookmark in a split-screen pane or a narrow sidebar keeps its whole name
instead of running off the edge. The page number every entry leads to keeps a column of its own, wide
enough for four digits and reserved even on a row that leads nowhere, which is what keeps the numbers
lined up under each other however long the title beside them is. At 150 % text the rows grow sideways not
one pixel: they get taller.

**A long book folds, and a word finds it.** Android starts branches closed; opening one keeps your choice while you browse or filter. Each chapter with sections under it wears a chevron: close it and
its sections leave the list, open it and they come back — one chapter at a time, even when two of them share
a title. In the window the Contents are a view of their own: the **Pages / Bookmarks** switch at the top of
the sidebar, and the index gets the sidebar's whole height instead of a folded section under the thumbnails. The **Filter** box finds a section by name; the chapters above the hit stay in the list as the path
to it, quieter than the hit itself, and the row naming the page you are reading wears the accent. Clearing
the box puts the tree back exactly as you left it: filtering never folds or unfolds anything on its own.

**The index is yours to edit.** The **+** button above Android's list (**Add bookmark**) names a row on the page you are on. A row
that belongs to the notebook wears a **⋯** button that opens a small menu with **Rename** and **Delete** —
drag the handle on Android, or the row on Windows, to reorder a chapter with its subsections under the same parent. The list scrolls when held near an edge. Opening the menu or picking Rename writes nothing, and Delete only *asks* first, above the list, saying
the sections under it move up and the page stays in the notebook: a bookmark is a signpost, never the page.
Rows read out of the PDF cannot be edited and show the empty slot instead: the first bookmark you add copies
them into the notebook first, so the list you were reading stays where it is, in one undo step. The name is
typed where the bookmark is: **Rename** turns that row into a field and opens the Android keyboard automatically; **Add bookmark** opens a draft row above
its neighbours showing the page it captured, **Enter** saves, and the cross (or Escape on the window) leaves
the tree exactly as it was; a blank name cannot be saved and a refusal is printed under the field. Every add,
rename, reorder and removal is one undo step. Adding shows the draft immediately and reveals the saved bookmark without leaving the list; a filter is cleared so the new title stays visible.

**An import writes the tree into the notebook.** A PDF's bookmarks are copied into the notebook's own Contents
when it is imported — one undo step together with its pages — so they can be renamed or deleted and they survive
a save. The index starts directly with the PDF's chapters, without an automatic bookmark named after the file.
The import card's **Add a bookmark for this file** option is off by default. Turn it on only when you want
one marker grouping that file's chapters. Appending another PDF normally adds its chapters at the top level.
Saved notebooks keep their existing bookmarks. To remove an old filename marker, use **Delete** on that row;
its chapters remain and move up a level. Opening the notebook never re-reads or rebuilds an authored index.

## Text and Markdown, laid out into pages

A plain text or Markdown file can come in the same way, and it arrives as **pages generated from it** — the
file is laid out and paginated into a PDF you write on, so what you annotate against is the finished
layout. Markdown is recognised by the file's name rather than by what is inside it, so an unusual
extension will be read as plain text.

## A picture as a page background

A picture imports as a page's background the same way a PDF page does, and the format decides what happens
to the file: a `.xopp` references it beside the document, a `.mobix` can carry it inside. Once imported it
is the sheet you write on rather than an object you can drag around.

## Recording while you write

The **Audio** slot toggles capture, and the document's format picks the recorder. A `.mobix` records Opus
in an Ogg file — small, about fifteen megabytes an hour. A `.xopp` records the WAV the desktop plays. A
device that cannot encode Opus falls back to WAV rather than refusing to record, and the file's own name
says which container it is.

The microphone permission is asked the first time you press record; everything else works without it.
Every stroke committed while recording is stamped with the recording's file name and how far into it you
were — measured in the audio actually written, so a stall in capture cannot quietly shift the mapping.

## Playing it back from the page

**Play object** is a query, not an edit: tap a stroke and the recording seeks to the point nearest where
you tapped, so tapping the fifth word starts there rather than at the start of the stroke.

While it plays, writing after the playhead is dimmed and the stroke being written shows as a growing
prefix with a glowing nib at the pen. **Pause** holds the page where it stopped; **Stop** lets the page
paint itself again. The audio pop-up carries a scrubber over the clip, so dragging fills and unfills the
page. With finger drawing off, the pen alone picks strokes and a finger still pans — the page stays
scrollable under a resting hand while you listen.

## Getting the audio out, and what a `.xopp` gets

Audio does not go into an exported PDF: the page is its ink and nothing else.

A `.xopp` carries the stroke's reference and the recording is written out beside the document. Nothing is
transcoded, so a compressed recording exported inside a `.xopp` travels as the Ogg file it is, and the
editor says so — Xournal++ plays WAV only. A `.xopp` authored in this app records WAV from the start, so
it never meets that case.

## How it works

- [pdf.md](pdf.md) — backgrounds, import modes, the text index, the highlighter and the outline.
- [audio.md](audio.md) — capture, the stamp, the playhead, and the platform half.
- [audio.md](audio.md) — replay modes, the scrubber, and the export rules.
- [images.md](images.md) — how a picture reaches the page, and what the reference stores.
- [text-and-markdown.md](text-and-markdown.md) — the paginator, the generated PDF and the two routes in.
