# The words this guide uses, and what they mean

> Read when a page used a word you are expected to know — living element, rail, sidecar, chunked — or when
> you want to know what Xournal++ calls the same thing.

_A guide page for Android and Windows: the vocabulary, with the page that owns each term. The internal map is [architecture.md](architecture.md)._

This page is true for Android **2.2.0** and desktop **1.2.0**. A term's owner is where its behaviour is
described; nothing here is more than a definition.

## Document and formats

| Term | What it is | What else it is called | Owner |
|---|---|---|---|
| Living element | An object a notebook keeps as data, not as a picture of itself, so it can be edited again | Xournal++ sees only the ink it was flattened into | [element-authoring.md](element-authoring.md) |
| Sticky format | The rule that a document saves in the format it was opened in, until Save As says otherwise | — | [mobix-format.md](mobix-format.md) |
| `.mobix` | This app's own container: living elements, full stylus telemetry, audio inside the file | — | [mobix-format.md](mobix-format.md) |
| `.xopp` | Xournal++'s file, read and written without loss so a round trip returns the same bytes | the interchange format | [xopp-bridge.md](xopp-bridge.md) |
| Sidecar | A file that sits beside the document instead of inside it — audio a `.xopp` references, a PDF background | — | [xopp-bridge.md](xopp-bridge.md) |
| Chunked document | A notebook stored as spatial chunks rather than a page stack; it opens and saves, but this app cannot create one | a board | [chunked-documents.md](chunked-documents.md) |
| Snapshot | The copy the app writes so it can reopen your tabs after a restart; it is a cache, never a save | autosave (wrongly) | [document-session.md](document-session.md) |

## Canvas and chrome

| Term | What it is | What else it is called | Owner |
|---|---|---|---|
| Sheet | The page's own area inside the canvas, with its paper and its edges | the page | [pages-and-paper.md](pages-and-paper.md) |
| Rail | The strip of writing tools down one side of the editor | the toolbar | [chrome.md](chrome.md) |
| Layer | A stack inside a page, so one run of work can be hidden over another | the same word in Xournal++ | [drawing-tools.md](drawing-tools.md) |
| Marquee | The rubber-band selection a drag leaves behind | lasso | [selection.md](selection.md) |
| Ring | The menu that opens where the pen is, holding the actions you reach for | — | [chrome-settings.md](chrome-settings.md) |
| Bookmark | A labelled, colour-coded flag on a page, kept by the app rather than written into the file | — | [pages-and-paper.md](pages-and-paper.md) |
| Playhead | Where a recording currently is, and therefore which strokes are dimmed | — | [audio.md](audio.md) |
| Vertical space | Room inserted at a point on the page, pushing what follows it down | — | [element-authoring.md](element-authoring.md) |

## Pen and input

| Term | What it is | What else it is called | Owner |
|---|---|---|---|
| Pressure filter | The mapping from how hard you press to how wide the stroke is | Xournal++'s own filter | [stylus.md](stylus.md) |
| Palm rejection | The rule that a pen outranks a hand that landed first | — | [gestures.md](gestures.md) |
| Hold-to-snap | Resting the tip long enough that the shape recogniser replaces the stroke before you lift | shape recognition | [drawing-tools.md](drawing-tools.md) |
| Barrel button | The side button on a pen, running one action you choose | — | [stylus.md](stylus.md) |
| Gesture | One press to one lift, however many samples it wrote — which is also one undo step | a stroke (loosely) | [gestures.md](gestures.md) |

## STEM objects

| Term | What it is | What else it is called | Owner |
|---|---|---|---|
| Plot | A graph kept as its expression, with a domain, a derivative and a shaded integral | function plot | [math-engine.md](math-engine.md) |
| Slider | A control under a selected plot that moves one named parameter | — | [math-engine.md](math-engine.md) |
| Table | A grid kept as cells and a header, editable after it is placed | data table | [mobix-elements.md](mobix-elements.md) |
| Circuit | A component with the pins it was made with, so wires meet where the pins are | — | [mobix-elements.md](mobix-elements.md) |
| Formula block | LaTeX that is typeset on the page, with its source kept | the LaTeX tool | [element-authoring.md](element-authoring.md) |

## Engineering terms (for developers)

These are the words the code and the architecture docs use; a reader of the guide can skip the group.

| Term | What it is | What else it is called | Owner |
|---|---|---|---|
| Telemetry | What every sample records: position, pressure, tilt, azimuth and the time since the last sample | — | [stylus.md](stylus.md) |
| Digest claim | The hash recorded beside the ink a living element was flattened into | the metadata block | [xopp-bridge.md](xopp-bridge.md) |
| Revival | Restoring objects on import, only while that ink still matches the record | — | [xopp-bridge.md](xopp-bridge.md) |
| Sniffing | Deciding a file's format from its first bytes rather than from its name | — | [data-path.md](data-path.md) |
| Three-way merge | Reconciling a document opened beside a conflicting copy, with no interface of its own | — | [document-session.md](document-session.md) |

## How it works

Each row above points at the page that owns the behaviour; the map of all of them is
[architecture.md](architecture.md), and the status of anything partial is
[what works, and what is not there](guide-limitations.md).
