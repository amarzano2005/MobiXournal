# The questions that are not tasks

> Read when you want the short answer first — a licence, an account, a file's fate — before you open a
> page that walks you through the doing of it.

_A guide page for Android and Windows: twelve answers and a pointer each. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.1.0** and desktop **1.1.0**. No answer here is the only home of its
fact; each one points at the page that owns it.

## Can I open the notebook in Xournal++ after saving here?

Yes, if it is saved as a `.xopp` — that is the interchange format and the reason it exists. Saving as
`.mobix` gives Xournal++ nothing to open, which is the trade for keeping living elements alive.

*More:* [Coming from Xournal++](guide-xournalpp-migration.md).

## Can I sync two devices, or merge two copies of a notebook?

There is no sync product: nothing in this app talks to a server about your notebooks. What does exist is a
three-way merge that reconciles a document you open beside a conflicting copy, with no interface of its own
— useful when a file-sync client has made two versions.

*More:* [What works, and what is not there](guide-limitations.md).

## Does it need an account, or the internet?

No account, and nothing is uploaded. The app works offline; the one network moment is a tablet's first
handwriting search in a language, which may need to fetch a recognition model.

*More:* [Finding a word](guide-search-and-organise.md).

## Does it work with a mouse?

In the window, yes — a mouse draws, selects and pans, and a pen behaves like a better mouse. On a tablet a
finger draws too, without pressure, so the stroke has one width; the pen adds pressure and the barrel
button.

*More:* [How the pen behaves](guide-inking-and-gestures.md).

## How do I report a problem?

Open [When something goes wrong](guide-troubleshooting.md) first: it lists the failures the app can report
and what to collect for a report. A report that quotes the app's own message is worth ten that describe a
feeling.

## Is it affiliated with Xournal++?

No. MobiXournal is independent and unofficial, not affiliated with or endorsed by the Xournal++ authors. It
continues Brian Monroe's NeXopp under its own name, and reads the Xournal++ format as an act of
interoperability rather than of shared ownership.

*More:* [codebase.md](codebase.md).

## Is it free, and under what licence?

Free and open source under **GPL-2.0-or-later**, the same licence as the project it grew out of; the
repository's `LICENSE` and `NOTICE` are the authority.

*More:* [codebase.md](codebase.md).

## Is there an iOS or macOS version?

No. The app ships for Android tablets and phones and for Windows; there is no Apple build, and no promise of
one.

*More:* [the user guide](guide-index.md).

## What is a "living" element?

An object the file keeps as data rather than as a picture of itself — a plot, a table, a shape, a circuit, a
formula — so that selecting it later offers its own fields again. Export it to Xournal++ and it becomes
ordinary ink there, because that format has nowhere to keep the object.

*More:* [Placing a plot, table or circuit](guide-stem-tools.md).

## What is the difference between the two formats?

`.xopp` is Xournal++'s file, read and written without loss. `.mobix` is this app's own container, which can
hold what a `.xopp` cannot. A document keeps the format it was opened in until you use Save As.

*More:* [Which format your notebook is in](guide-formats-and-conversion.md).

## Where are my files?

Wherever you pointed **Save As**. The app also keeps a restart cache of the tabs you had open, but that is
not a file you can copy: the notebook you named is the only copy another app ever sees.

*More:* [Your first notebook](guide-first-notebook.md).

## Why are there two version numbers?

The tablet app and the Windows window are built and released separately, so each carries its own version;
a release note names the shell it belongs to. This guide states both at the top of every page for that
reason.

*More:* [the releases, newest first](releases/index.md).

## How it works

The only fact here with a technical owner is the licence and the lineage:
[codebase.md](codebase.md) states both, and the status of every capability is
[what works, and what is not there](guide-limitations.md).
