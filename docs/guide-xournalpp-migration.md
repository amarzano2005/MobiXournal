# Coming from Xournal++, what opens, what stays, and what changes

> Read when your notebooks are `.xopp` files from desktop Xournal++ and you want to know what happens to
> them here before you trust one to the app.

_A guide page for Android and Windows: the Xournal++ relationship, in both directions. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.0.0** and desktop **1.0.1**.

## This is not Xournal++

MobiXournal is an independent, unofficial app. It is not affiliated with or endorsed by the Xournal++
authors, and it continues Brian Monroe's NeXopp under its own name. It reads and writes the Xournal++
format because your files should not be a lock-in, not because the two projects are the same one —
[codebase.md](codebase.md) owns the lineage and the branding rules.

## Your `.xopp` files open as they are

There is no import step. A `.xopp` opened on the tablet or in the window is displayed and edited as it
stands, and it saves back as a `.xopp` — the format is sticky per document, so this is not a decision you
have to remember each time.

Audio sidecars, PDF backgrounds and ruling that the file references come along with it. A `.mobix` can
hold a page's background inside the file instead; that difference is what makes one conversion refusable,
and [which format your notebook is in](guide-formats-and-conversion.md) explains when.

## The round trip, in both directions

Leave a file untouched and it comes back byte for byte. Change it here and the change is written as a
`.xopp` Xournal++ can read. The interesting case is the living elements — a table, plot, circuit or
formula — which have no `.xopp` equivalent: they leave as ordinary strokes and text, with a metadata block
recording where each one landed and a digest of that ink.

Open the file here again and the objects return, as long as that ink still hashes to what was recorded. If
you edited the file in Xournal++ in between, the digest no longer matches, the claim is dropped, and the
edit arrives as plain ink. That is deliberate: your edit is more recent than the app's memory of an
object, so the edit wins. The block and its two hazards belong to
[xopp-bridge.md](xopp-bridge.md), and the file's own schema to [xopp-format.md](xopp-format.md).

## Working in both apps, day to day

Keep a notebook in `.xopp` and both apps can open it: this one does not convert it behind your back, and
Xournal++ keeps reading what it always read. Nothing stops you working that way forever.

Reach for **Save As → `.mobix`** when you want the things a `.xopp` cannot hold — living elements kept as
objects, recorded audio inside the notebook, the paged container. Once a notebook is a `.mobix`, Xournal++
cannot open it; convert back with **Save As** when it has to go the other way.

## What deliberately differs

- **Drawn stroke width follows desktop Xournal++'s pressure filter.** The same pen moving at the same
  pressure should not produce two different lines, even across the two apps.
- **The app creates page stacks, not an unbounded board.** A chunked document from elsewhere opens, edits
  and saves, but this app does not offer the board canvas that would create a new one.
- **Handwriting search uses the platform's recogniser** rather than a model shipped by the app, which is
  why an offline first search can ask for a language download on a tablet.

## How it works

The Xournal++ side of this page is one contract, stated in three places:

- [xopp-bridge.md](xopp-bridge.md) — the bridge in both directions and what a round trip guarantees.
- [xopp-format.md](xopp-format.md) — the file format itself, as this app reads and writes it.
- [codebase.md](codebase.md) — lineage, branding, and the licence the two projects share.

The other task pages are in [the user guide](guide-index.md).
