# Your first notebook, from an empty tab to a file you can reopen

> Read when you have just installed MobiXournal and want a page with ink on it, saved somewhere you will
> find it tomorrow.

_A guide page for Android and Windows: start a notebook, ink it, save it, reopen it. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.0.0** and desktop **1.0.1**.

## Start a notebook

A new tab is an empty notebook. Nothing exists on disk until you save it, so the tab is called
**Untitled** until then — that is a name, not a file.

The format it will be saved in comes from **Settings → New documents**, and picking one here is not a
decision you have to keep: **Save As** can write either format at any point.

## Choose the paper

Paper belongs to the page, not to the notebook. The page-background dialog has a **Paper** section —
**Plain**, **Ruled**, **Dotted** and the rest — and the page-size dialog sets the sheet's dimensions
separately, so a sheet can be A4 with dotted ruling.

## Write, erase, undo

Three interactions carry the first five minutes:

- **Write** with the pen tool. A stylus adds pressure to the stroke; a finger draws without it, so the
  pen is not required to start.
- **Erase** with the eraser, which takes ink off the page rather than painting white over it.
- **Undo** and **Redo** from the tool strip — `Ctrl+Z` and `Ctrl+Shift+Z` on the window. One gesture is one
  undo step, so a long stroke comes back in one piece.

## Save it, and know where it went

**Save As** asks for a name and a format. After the first save, plain **Save** writes back to that same
file in that same format, which is what keeps a `.xopp` a `.xopp` and a `.mobix` a `.mobix`.

The file goes wherever you point the picker: local storage, an SD card, or a folder a sync client already
watches. A save is staged beside the target and then moved into place, so a client that uploads while you
work never picks up half a notebook.

## Open it again

The entry screen lists the notebooks you opened recently; **Open** takes a file from anywhere else. A
notebook that moved since it was listed is a normal case — open it from its new home and the list follows.
Each card has a **×** at its top-right that takes that notebook off the list.

If the app was closed with notebooks still open, they are not reopened at launch: they appear on the entry
screen marked as unsaved work from the previous session, and **Restore last session** — the button under
**New sheet** — brings the whole set back with its edits. **New sheet** answers that offer instead and
starts a clean session, which drops those marked cards.

## Where to go next

- [What works, and what is not there](guide-limitations.md) — whether the tool you need exists on your platform.
- [When something goes wrong](guide-troubleshooting.md) — a refused open, a save that did not happen.
- [The user guide](guide-index.md) — the other task pages, by job.

## How it works

The steps above rest on rules these docs own:

- [pages-and-paper.md](pages-and-paper.md) — paper, templates, page size, and the page list.
- [drawing-tools.md](drawing-tools.md) — what each tool does to the page, the eraser and the layers.
- [data-path.md](data-path.md) — how a document's bytes are read, written and sniffed.
- [mobix-format.md](mobix-format.md) — the two formats and the policy that keeps each document in its own.
