# When something goes wrong: opens, saves and recovery

> Read when an open or a save did not go the way you expected, the window came back after a crash, or a
> message named something you did not recognise.

_A guide page for Android and Windows: what to do when an open or a save is refused. The internal map is [architecture.md](architecture.md)._

Both shells decide what a file is by reading its first bytes, not by its name, and the messages on this
page are the Windows window's, quoted exactly as they appear. The causes are the same on a tablet: the
tablet simply reports them with less ceremony.

## A file that will not open

### "This file is not on this PC"

Nothing is at that path. The usual causes are a notebook that was moved or renamed after a shortcut was
made, a cloud-storage folder that is not available offline yet, and a launch that named the file with a
path Windows split at a space. Check the file in Explorer first; if it lives in Google Drive or OneDrive,
open the folder once so the file is really on disk, then open the notebook again.

### "Unsupported format"

The file opened, but its bytes are not a document this build reads. The second line says which family it
recognised — a PDF, a ZIP with nothing document-shaped inside, plain text, or an image. Open it with the
application that owns it, or import it into a notebook rather than opening it as one.

### "Not enough memory"

The page is larger than the memory the window could give it. It is a size problem, not a corrupt file: the
notebook is untouched. Close other notebooks (each tab holds pages), then reopen this one.

### "The preview has fallen behind"

This one is not about your file at all. It appears when the window was already running while the program
was rebuilt: the code underneath it changed, and a class it had not loaded yet disappeared. Close the
window, relaunch it, and open the same file — it opens normally in a fresh process. A release build never
does this.

### "Could not open the document"

The catch-all. The second line carries the technical reason; if it names a class and a detail you do not
recognise, that is the sentence to quote when you report the problem.

## A save that does not happen

The window writes the whole document beside the target first and only then moves it into place. That is
why a failed save is safe to retry: **the file on disk is either the old bytes or the new ones, never
half of each.**

### "This document cannot be saved there"

One case is refused on purpose: a packaged `.xopp` whose page background lives **inside** the package.
The window has no image or PDF store to bundle that background back out of, so it refuses rather than
writing a reference it cannot honour — and the file stays exactly as it was. Two ways through: save the
notebook as a `.mobix`, or save it as a `.xopp` whose background is a file beside it rather than inside
the package.

### "Could not write the file"

The document was written but could not be moved into place: the file is open in another program, the
folder is read-only, or the network share went away. Nothing is lost. The window keeps your pages and
stays marked as unsaved, so **Save** retries the same destination and **Save As** writes them somewhere
that works. The second line says which of those it was — "access denied" and "used by another process"
are different problems with different fixes.

## A notebook that came back after a crash

While a tab has unsaved edits, the window writes a copy of the notebook to a recovery folder, once per
quiet burst of edits rather than once per stroke:

```
%APPDATA%\MobiXournal\recovery\
```

A start that finds one opens it as a tab named `… (recovered)`, marked as unsaved. It stands for the
original file: **Save** writes to the original path, rebuilding its pages from the recovered copy rather
than from the older bytes on disk. Saving, discarding or closing the tab deletes the snapshot; nothing in
that folder is ever a save of its own. If you want to keep a recovered version without touching the
original, use **Save As**.

## When the message is not on this page

Note four things before you report it: the platform (Android or Windows), the version shown in the
window title or the app's About page, what you did just before, and the message word for word. The
mechanism behind each failure is in [desktop-documents.md](desktop-documents.md); the shared rules for
reading and writing a document are in [data-path.md](data-path.md).

## How it works

Each message above comes from a path the architecture docs own:

- [desktop-documents.md](desktop-documents.md) — the open path, and the failures it can report.
- [data-path.md](data-path.md) — how bytes are read, written and moved into place.
- [xopp-bridge.md](xopp-bridge.md) — what a file keeps when it comes back with its elements.

The other task pages are in [the user guide](guide-index.md).
