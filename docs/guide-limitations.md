# What works, and what is not there

> Read when you are deciding whether MobiXournal does a thing you need, after a page told you a feature is
> partial, or before you trust a notebook to it.

_A guide page for Android and Windows: the status of each capability that is not simply there. The internal map is [architecture.md](architecture.md)._

Describes the Android build **2.0.0** and the desktop build **1.0.1**. The matrix is reviewed when a release
is cut, so a row that is wrong for the version you installed is worth reporting.

## How to read the words

The six words are defined in [writing-docs.md](writing-docs.md). What each one costs you here:

- **Supported** — works on that shell today, with a test or a device run behind it.
- **Partially supported** — works along some path, shell or format, and the row names what is missing.
- **Experimental** — present but not trusted: behind a switch, or never reproduced on real hardware.
- **Planned** — absent, and the work that adds it is queued.
- **Not supported** — absent on purpose, for the reason the row gives.
- **Not verified** — believed to work and unconfirmed; the row says what would confirm it.

A capability that works everywhere is not listed: this page is the exceptions, not the feature list.

## The matrix

| Capability | Android | Windows | The limit, and why | Evidence |
|---|---|---|---|---|
| Handwriting search with no network | **Partially supported** | **Supported** | On a tablet, the first search in a language downloads ML Kit's model; the pure-Kotlin engine still reads when no model is present. The window uses the recognizer Windows already installs. | [search.md](search.md) |
| Parametric and polar plots | **Partially supported** | **Partially supported** | A parametric or polar curve is stored, drawn, exported and re-opened, but the plot tool places Cartesian curves only. | [math-engine.md](math-engine.md) |
| Typst as a formula source | **Partially supported** | **Partially supported** | The source stays Typst through save, export and re-import; this build has no Typst typesetter, so a Typst block is not drawn as typeset mathematics. | [element-authoring.md](element-authoring.md) |
| Chunked, infinite-canvas documents | **Partially supported** | **Partially supported** | One opens, is edited and saves as chunks; the app no longer offers a board canvas, so it cannot create one. | [chunked-documents.md](chunked-documents.md) |
| Sync between devices or machines | **Not supported** | **Not supported** | There is no sync product. A three-way merge reconciles a document that opens beside a conflicting copy, with no interface of its own. | [architecture.md](architecture.md) |
| Stylus tilt and azimuth recorded | **Supported** | **Supported** | Every sample keeps both. The window's Compose pointer has no such axis, so the pen's own tablet stream supplies them. | [stylus.md](stylus.md) |
| Tilt changing stroke width | **Not supported** | **Not supported** | Width follows the Xournal++ pressure filter, so tilting the pen changes nothing on the page. | [stylus.md](stylus.md) |
| MSI signed by a known publisher | — | **Not verified** | The installer is signed only when it is built with a certificate configured; the published file has not been checked. | [tools.md](tools.md) |

## What "Not verified" means on this page

One row carries that word, and it is about a file you download rather than about code: whether the MSI
published with a release is signed by a known publisher. Nothing in the repository can answer that, because
the answer belongs to the artifact. To check it on your machine, right-click the `.msi`, choose
*Properties*, and open *Digital Signatures*; `Get-AuthenticodeSignature` in PowerShell prints the same
answer. If an unsigned installer is not acceptable to you, build one with a certificate — [tools.md](tools.md)
names the four variables it reads.

A row leaves that section only when the check has been run and written down, so its absence is not a verdict.

## Adding a row

A capability earns a row here when it differs by shell, stops part-way, sits behind a switch, or is absent.
The word is one of the six above, the evidence cell links the doc that owns the mechanism, and the limit is
written as a reader meets it rather than as a prognosis. [architecture.md](architecture.md) holds the
procedure for the page itself.

## How it works

Every row links the doc that owns its mechanism, and the reason behind a row lives there too. Two pages
carry the words and the procedure:

- [writing-docs.md](writing-docs.md) — the six words, defined once.
- [architecture.md](architecture.md) — how a page in this layer is added and registered.

The other task pages are in [the user guide](guide-index.md).
