# Desktop documents: opening, saving and unsaved work

> Read when you change what the `desktop/` window does with a file — how it decides a file's family, what it
> says when an open or a save is refused, how a tab knows it has unsaved edits, and how a crash comes back.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Opening a document, and what a failed open says

`DocumentLoader` picks a file's family by its bytes and its `mimetype` entry, and `describeOpenFailure`
decides what the window says when the open is refused. Three cases, three sentences: an
`UnsupportedOperationException` is the loader's own refusal and keeps its words (`This file looks like a
PDF (pdf), not a MobiXournal document`); an `OutOfMemoryError` is reported as memory, because a page taller than the heap is not a
corrupt notebook; and a **`LinkageError`** is reported as *the window being stale*, not the file being
broken. That last case is real — a run produced `NoClassDefFoundError:
com/mobixournal/desktop/DocumentLoader$openMobix$1`, and the notebook was untouched. It is a fact of how
the preview is launched: `:desktop:run` executes `build/classes`, so a Gradle build during a session
rewrites the class files under the live JVM and any synthetic class the JVM had not loaded yet is gone.
Closing the window and relaunching is the fix; the same file opens in ~40 ms in a fresh process.

A failed open **never takes the document on screen with it**: the report is a card at the top of the
canvas, dismissed with a click or `Esc`, while the page, the tab and the sidebar stay as they were. Its
fill is opaque rather than the floating family's 0.88, because it straddles the desk and the sheet — at
0.88 the part over the page read a tone lighter, as two materials split down the middle.

## Saving, and what a refused save says

A save can be refused before anything is written, and the sentence says which case it is
(`describeSaveFailure`). A **packaged `.xopp` whose background lives inside the package** is the one this
window cannot write faithfully — it has no image or PDF store to bundle one back out of — so it is refused
with the two ways round it (a gzip `.xopp` or a `.mobix`) rather than written with a reference to an entry
it did not produce; the file stays byte-identical, because only a staging file that is never committed existed. An **I/O failure** says so and adds that the document on
disk was left as it was — the staged-then-moved write makes that true. A **`LinkageError`** is the window
being stale, as on an open. `DesktopSaveTest` pins all three, and byte-compares the original to prove a
refused save touched nothing.

A notebook **opened from Explorer** arrives as the command line's tail, and an unquoted path is split at every run of spaces — with the run **squeezed into one**, so `…/09)  21-10-2026/…` reaches `main` as `09) 21-10-2026`. `initialDocument` therefore reads the line Windows actually wrote (`rawCommandLine`, `kernel32!GetCommandLineW`) before it rearranges the arguments, so a double-clicked notebook opens with no dialog at all; `--args` splits its value on spaces too, and only when the line answers nothing are those pieces put back — `launchDocument` searching by name as a last resort, never by guessing.

## Unsaved edits: the dot, the question, and a snapshot

A tab is **dirty** when the document holds commands its file does not, and the window does not answer that
with a flag somebody clears: it compares the history **state id** with the **save point** recorded on open and
on every save (`EditHistory.stateId`). Undoing back to the saved state makes the tab clean again, and a new
edit gets a new id even when the bytes match. The strip draws it as one accent dot after the label — a status
readout, absent when the tab is clean.

Closing a dirty tab (its `×`, `Ctrl+W`) or the window (its X) is a **question**, not a close: *Save*,
*Discard*, *Cancel*, in the floating glass over the page, naming the document at stake. The X goes through
the same path (`requestExit`), so it cannot bypass the question; with several dirty tabs the dialog stays up
and asks about the next one, and the window closes only when none is left. A failed save **keeps the tab open
and dirty** with the reason on screen — the one outcome that cannot lose the work. `Ctrl+S` is unavailable
while the file already holds the document.

While a tab is dirty the window also writes a **crash snapshot**: a self-contained `.mobix` in
`%APPDATA%\MobiXournal\recovery\`, keyed by a digest of the file's path, with a two-line `.origin` sidecar
naming the tab and the file. It is written once per quiet burst of edits (three seconds), not per stroke,
because a snapshot is the whole notebook. A start that finds one opens it as `… (recovered)`, dirty, standing
for the original path: `loadRecovered` keeps the **origin** as the save target and the **snapshot** as the
repack source, so a save rebuilds the pages from the recovered copy instead of copying the file's older ones
back over them, and it is encoded on a background thread — a whole notebook on the UI thread froze the window
in the middle of a pause. Saving, discarding or closing the tab deletes the snapshot; nothing in `recovery/`
is a save.
