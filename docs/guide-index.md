# The user guide

> Read when you have just installed MobiXournal, or something did not go the way you expected, and you
> want the page for that job rather than the architecture.

_A guide page for Android and Windows: a router to the task pages. The internal map is [architecture.md](architecture.md)._

Each row is a job, not a topic, so the page you land on opens by telling you what it is for. The pages
quote the app's own words and link to the doc that owns each rule; nothing here describes how the program
is built.

| If you want to… | Open |
|---|---|
| write a first notebook, save it, and open it again tomorrow | [Your first notebook](guide-first-notebook.md) |
| understand, change or hand over the format a notebook is saved in | [Which format your notebook is in](guide-formats-and-conversion.md) |
| bring your `.xopp` notebooks over from Xournal++, or keep working in both | [Coming from Xournal++](guide-xournalpp-migration.md) |
| get the pen to behave: width, palm rejection, the barrel button, a shape that snapped | [How the pen behaves](guide-inking-and-gestures.md) |
| put a plot, table, circuit or formula on the page and edit it afterwards | [Placing a plot, table or circuit](guide-stem-tools.md) |
| write on an imported PDF, or record while you write and play it back | [Annotating a PDF, and recording](guide-pdf-and-audio.md) |
| find a word in a long notebook, or move, bookmark and prune its pages | [Finding a word, and rearranging pages](guide-search-and-organise.md) |
| put MobiXournal on an Android tablet or phone, and find the notebooks afterwards | [Installing on Android](guide-install-android.md) |
| install the Windows window from the MSI, or run it without installing anything | [Installing on Windows 11](guide-install-windows.md) |
| get past an open that was refused or a save that did not happen, and recover a notebook after a crash | [When something goes wrong](guide-troubleshooting.md) |
| check whether something you need works at all, and how far | [What works, and what is not there](guide-limitations.md) |

## Adding a page

A new task page is a new `docs/guide-*.md` file with a `# Title` naming the job, a `> Read when …` line, a
task line, a closing `## How it works` that links the owning spoke, and a row in the table above. `DocsGuardTest` fails the build when
one of those is missing.
