# Which format your notebook is in, and what Save As does

> Read when you are about to save a notebook, convert one, or hand a file to someone who works in
> Xournal++.

_A guide page for Android and Windows: the two formats, the one conversion that is refused, and what a conversion costs. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.0.0** and desktop **1.0.1**.

## The rule, in one paragraph

A document remembers its own format. A `.xopp` you open saves back as a `.xopp`; a `.mobix` saves back as
a `.mobix`; a new notebook takes whichever format **Settings → New documents** names. **Save As** is the
only way to change that, and opening a file never converts it — the extension you picked up is the
extension you keep until you say otherwise.

## `.xopp`: the file Xournal++ reads

`.xopp` is the interchange format. A notebook from desktop Xournal++ opens in either shell without a
conversion step, and goes back to Xournal++ the same way. Leave a file alone and the round trip gives the
same bytes back; the [contract page](xopp-bridge.md) lists what "same" covers.

## `.mobix`: the format that keeps things editable

`.mobix` is the native format, and it is the reason a table, plot, circuit or formula stays an object you
can re-open and edit rather than ink that happens to look like one. It also carries what a `.xopp` has no
field for — full stylus telemetry, audio recorded inside the notebook, and a paged layout that opens a
large notebook without reading all of it. The container is [mobix-format.md](mobix-format.md)'s to
describe.

## What Save As does to the content

**Save As** asks for a name and a format, and converts in either direction. Converting to `.xopp` turns
each living element into the strokes and text boxes Xournal++ understands, and records where each one
landed plus a digest of that ink. Come back to the file and the elements are restored — unless the ink was
edited in the meantime, in which case the edit wins and arrives as ordinary ink. That is the whole of the
revival rule; the details are [xopp-bridge.md](xopp-bridge.md)'s.

Converting to `.mobix` keeps everything the format can hold, including the living elements a `.xopp` could
only flatten.

## The one conversion that is refused

The desktop window refuses one case on purpose: a **packaged `.xopp` whose page background lives inside
the package**. It has no image or PDF store to write that background back out of, so it stops rather than
writing a reference it cannot honour, and the file on disk is left exactly as it was. Two ways through:

- save the notebook as a `.mobix`, which can carry the background inside the file; or
- save it as a `.xopp` whose background is a file beside it rather than inside the package.

The refusal is the safe outcome, not a failure: refusing leaves a working file, and a half-rewritten
package would not.

## Taking the page somewhere else

Saving a notebook is not the only way out of it. **Export PDF** writes the pages as a PDF from either
shell, keeping the source PDF's own vector content where it is still available and laying your ink over
it. The window's menu also exports the pages as **PNG** or **SVG** into a folder you pick.

Audio is the exception in both directions: a recording stays beside the document and never enters an
exported PDF.

## Nothing converts behind your back

Opening is read-only with respect to format. A `.xopp` opened on the tablet is still a `.xopp` when the
tablet saves it; nothing renames your file, inserts an extension, or rewrites the bytes into the other
container as a side effect of looking at it.

## How it works

The rules above are these docs' to state:

- [mobix-format.md](mobix-format.md) — the container, and which format a document is written in.
- [xopp-bridge.md](xopp-bridge.md) — synthesis out, revival in, and the metadata block between them.
- [data-path.md](data-path.md) — how a file's bytes are sniffed, read and written.
- [desktop-ui-layout.md](desktop-ui-layout.md) — the window's menu, its export commands and their chips.

The other task pages are in [the user guide](guide-index.md).
