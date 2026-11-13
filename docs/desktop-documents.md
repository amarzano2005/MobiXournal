# Desktop documents: opening, saving and unsaved work

> Read when you change what the `desktop/` window does with a file — how it decides a file's family, what it
> says when an open or a save is refused, how a tab knows it has unsaved edits, and how a crash comes back.
> For the task: [Installing on Windows 11](guide-install-windows.md), [When something goes wrong](guide-troubleshooting.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Opening a document, and what a failed open says

`DocumentLoader` picks a file's family by its bytes and its `mimetype` entry, and `describeOpenFailure`
decides what the window says when the open is refused. Three cases, three sentences: an
`UnsupportedOperationException` is the loader's own refusal and keeps its words (`This file looks like a
PDF (pdf), not a MobiXournal document`); an `OutOfMemoryError` is reported as memory, because a page taller than the heap is not a
corrupt notebook; and a **`LinkageError`** is reported as *the window being stale*, not the file being
broken. That last case is real — a run produced `NoClassDefFoundError:
com/mobixournal/desktop/io/DocumentLoader$openMobix$1`, and the notebook was untouched. It is a fact of how
the preview is launched: `:desktop:run` executes `build/classes`, so a Gradle build during a session
rewrites the class files under the live JVM and any synthetic class the JVM had not loaded yet is gone.
Closing the window and relaunching is the fix; the same file opens in ~40 ms in a fresh process.

A failed open **never takes the document on screen with it**: the report is a card at the top of the
canvas, dismissed with a click or `Esc`, while the page, the tab and the sidebar stay as they were. Its
fill is opaque rather than the floating family's 0.88, because it straddles the desk and the sheet — at
0.88 the part over the page read a tone lighter, as two materials split down the middle.

A **read-only** open is a tab, not a card: `DocumentLoader.openMobix` computes the gate's verdict once
(`MobixDocument.verdict(DesktopSaves.APP_VERSION)`) and the document carries it, so the band's `read-only`
reading with its whole sentence under the pointer, the canvas that takes no press and the Save and Save As
commands that answer *unavailable* are all that one answer. That works because every edit in the window
funnels through one method (`DesktopDocument.editPage`) and every write through one (`DesktopSaves.stage`),
instead of an affordance being disabled at a time. A document whose **optional** capability this build
does not implement reads `kept as written` and stays editable, because the elements it cannot model are
preserved verbatim rather than dropped. `DesktopReadOnlyGateTest` pins both: a required capability leaves
the pages readable and answers no to an edit and to a save, an optional one still takes edits, and a file
this build wrote shows no note at all.

## Saving, and what a refused save says

A save can be refused before anything is written, and the sentence says which case it is
(`describeSaveFailure`). A **packaged `.xopp` whose background lives inside the package** is the one this
window cannot write faithfully — it has no image or PDF store to bundle one back out of — so it is refused
with the two ways round it (a gzip `.xopp` or a `.mobix`) rather than written with a reference to an entry
it did not produce; the file stays byte-identical, because only a staging file that is never committed existed. An **I/O failure** says so and adds that the document on
disk was left as it was — the staged-then-moved write makes that true. A **`LinkageError`** is the window
being stale, as on an open. `DesktopSaveTest` pins all three, and byte-compares the original to prove a
refused save touched nothing.

**A save that fails while replacing costs nothing.** The write stages the whole document beside the target
and only then releases the archive and moves it into place; when that move fails (a locked file, a path the
OS refuses, a share that went away) the old bytes are still on disk and the **staged file holds exactly what
the author has**. The tab therefore reads on from the staged copy — every page, edits included — keeps
`file` on the destination the user knows so Save tries it again and Save As is the way out, and stays
**dirty**: the file does not hold those pages, and a clean dot would hide that until the window closed. Only
the undo steps go, because their snapshots name pages of the archive just released. The staging name is a
fixed short prefix (`mobix-<digits>.writing`) rather than the document's own name repeated: a save into a
deep folder — `…\28-10-2026\Materiale\Le caratteristiche statiche…, il CMOS.mobix` — was reaching Windows'
260-character limit on the copy of a name that existed only to make the temp file recognisable.

A notebook **opened from Explorer** arrives as the command line's tail, and an unquoted path is split at every run of spaces — with the run **squeezed into one**, so `…/09)  21-10-2026/…` reaches `main` as `09) 21-10-2026`. `initialDocument` therefore reads the line Windows actually wrote (`rawCommandLine`, `kernel32!GetCommandLineW`) before it rearranges the arguments, so a double-clicked notebook opens with no dialog at all; `--args` splits its value on spaces too, and only when the line answers nothing are those pieces put back — `launchDocument` searching by name as a last resort, never by guessing.

A document opened from a `.pmobix` is a portable copy: Save opens Save As on the working name
([data-path.md](data-path.md)), and this window never replaces the copy itself.

## Unsaved edits: the dot, the question, and a snapshot

A tab is **dirty** when the document holds commands its file does not, and the window does not answer that
with a flag somebody clears: it compares the history **state id** with the **save point** recorded on open and
on every save (`EditHistory.stateId`). Undoing back to the saved state makes the tab clean again, and a new
edit gets a new id even when the bytes match. A save that lands over the tab's **own** file therefore only
moves the save point: the steps stay, so `Ctrl+Z` keeps working across an autosave, and only a save that
replaces the document itself (a Save As, or a failed write adopting its own staged file) starts a new history,
because those steps name an archive that was closed. Switching tabs moves only which tab is active, so
each keeps the steps it recorded: `Ctrl+Tab` and the strip cost nothing. The strip draws it as one accent dot after the label — a status
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
with a full baseline followed by native deltas as described below. A start that finds one **offers** it on
the entry screen — a badged card, and *Restore last session* under *New sheet* — instead of adopting it
behind the reader's back (`DesktopEntryCards.kt`). Restoring it, or opening that file at all, lands on
`… (recovered)`, dirty, standing for the original path: `loadRecovered` keeps the **origin** as the save
target and the **snapshot** as the repack source, so a save rebuilds the pages from the recovered copy
instead of copying the file's older ones back over them, and it is encoded on a background thread — a whole
notebook on the UI thread froze the window in the middle of a pause. The card's **×** and *New sheet* drop
the offer with the snapshot; saving, discarding or closing the tab deletes it too. Nothing in `recovery/`
is a save.

Save As to the current path uses the same stage/close/replace/reopen sequence as an ordinary Save,
with the explicitly chosen format. Its reloaded container and save point then become the tab's baseline
(`DesktopSaveTest`). Quiet recovery captures immutable requests on the UI thread before encoding on IO;
each origin has a generation. Save or Discard invalidates that generation, so an older writer cannot
republish a snapshot after it was removed (`DesktopLifecycleTest`).

Recovery encoding is serialized per store. A superseded generation is rejected before staging and at
output chunks, as well as before replacement, so obsolete requests stop consuming a whole encode.
Native recovery keeps an independent full baseline plus immutable ZIP entry deltas. Each record carries
the current manifest/order, changed pages and new assets; normal updates leave the baseline untouched.
Before reopening, or after 16 records / a bounded byte threshold, compaction publishes a standard
`.mobix` checkpoint before deleting older records. A checkpoint watermark prevents replay after an
interrupted cleanup. The watermark is a private ZIP comment; record metadata is outside the document
entry namespace, so even colliding unknown entry names survive. Unpublished `.writing` files are ignored. Documents without native identity use
full checkpoints. Final user-file saves still have the ZIP/provider copy cost described in
[mobix-format.md](mobix-format.md).

## Desktop save transaction

Toolbar, keyboard, Save As and close-confirm saves use `SaveWorker`. The editor is temporarily
unavailable while IO stages the bytes, drains document reader leases, replaces the destination and
reopens it. A desk status uses existing theme tokens and English copy. Tab adoption and history/save
point changes happen on the UI thread. A revision change aborts replacement and asks for another save.
Search, export and recovery hold read leases; archive closure holds exclusive ownership. Failed
same-path replacement retains the staged document as a dirty retry source. Synchronous APIs remain
available to headless callers and tests.
