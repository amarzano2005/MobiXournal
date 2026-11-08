# Annotating a PDF, and recording while you write

> Read when you are about to import a PDF to write on, or record a lecture and want the recording tied to
> the stroke it was written over.

_A guide page for Android and Windows: the two workflows that bring outside material onto the page. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.0.0** and desktop **1.0.1**.

## A PDF becomes the page's background

**Import PDF** reads the file first, then asks two things: which pages (a range you type, not a
checkbox per page) and where they go — replacing the document, or appended after the pages already open.
Appending is one undo step, and a single untouched blank sheet in front of the PDF is dropped rather than
left stranded.

A document holds one PDF. Import a second one onto a document that already has one and the two are merged
into a single file, so every page's PDF page number still points at the right sheet; the merge and the new
pages land together, which is what makes undoing it put the document back as it was.

## Copying text out of the page

A born-digital PDF's text layer is extracted when you import it, so **Select text (PDF)** can drag a
reading-order range and **Copy** puts it on the clipboard — `Shift+S` and `Ctrl+C` in the window. The
selection is a view over the PDF, not something written into your notebook: it is never saved, and
`Esc`, a click off the text or a page change clears it.

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

The **Contents** glyph at the foot of the page list opens the PDF's outline — indent, page numbers, the
bookmark tree the file already had. Tapping goes by the PDF's own page number rather than by a count, so
even a merged PDF lands on the right sheet; an entry with no destination inherits the page of the nearest
chapter below it, and an entry that points nowhere is listed but not tappable.

Navigation only: the notebook format has nowhere to keep a bookmark tree, so the outline is read from the
PDF and your file is untouched by it.

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
- [audio-inking-sync.md](audio-inking-sync.md) — replay modes, the scrubber, and the export rules.
- [images.md](images.md) — how a picture reaches the page, and what the reference stores.
- [text-and-markdown.md](text-and-markdown.md) — the paginator, the generated PDF and the two routes in.
