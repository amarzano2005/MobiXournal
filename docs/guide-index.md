# The user guide

> Read when you have just installed MobiXournal, or something did not go the way you expected, and you
> want the page for that job rather than the architecture.

_A guide page for Android and Windows: a router to the task pages. The internal map is [architecture.md](architecture.md)._

Each row is a job, not a topic, so the page you land on opens by telling you what it is for. The pages
quote the app's own words and link to the doc that owns each rule; nothing here describes how the program
is built.

Installing, building and running from source are [README.md](../README.md)'s; this table is for using the
app, not for building it.

| If you want to… | Open |
|---|---|
| write a first notebook, save it, and open it again tomorrow | [Your first notebook](guide-first-notebook.md) |
| understand, change or hand over the format a notebook is saved in | [Which format your notebook is in](guide-formats-and-conversion.md) |
| bring your `.xopp` notebooks over from Xournal++, or keep working in both | [Coming from Xournal++](guide-xournalpp-migration.md) |
| get the pen to behave: width, palm rejection, the barrel button, a shape that snapped | [How the pen behaves](guide-inking-and-gestures.md) |
| put a plot, table, circuit or formula on the page and edit it afterwards | [Placing a plot, table or circuit](guide-stem-tools.md) |
| write on an imported PDF, or record while you write and play it back | [Annotating a PDF, and recording](guide-pdf-and-audio.md) |
| find a word in a long notebook, or move, bookmark and prune its pages | [Finding a word, and rearranging pages](guide-search-and-organise.md) |
| find a setting, learn a key, or reset the pen's pressure curve | [Where a setting lives](guide-settings-and-shortcuts.md) |
| get a short answer about licence, accounts, files or versions before reading a task page | [The questions that are not tasks](guide-faq.md) |
| decode a word the guide uses, or find what Xournal++ calls the same thing | [The words this guide uses](guide-glossary.md) |
| put MobiXournal on an Android tablet or phone, and find the notebooks afterwards | [Installing on Android](guide-install-android.md) |
| install the Windows window from the MSI and get it running | [Installing on Windows 11](guide-install-windows.md) |
| get past an open that was refused or a save that did not happen, and recover a notebook after a crash | [When something goes wrong](guide-troubleshooting.md) |
| check whether something you need works at all, and how far | [What works, and what is not there](guide-limitations.md) |

## Adding a page

A task page is a file named `docs/guide-<job>.md` — the layer lives in the name, not in a folder
([architecture.md](architecture.md), *The tree stays flat*). One page, one job: if you cannot write the
job as a line in the table above, the page is a reference page instead.

The smallest page that passes every rule:

```markdown
# Adding a page to your notebook

> Read when you have written a page and want another one, and you want to know where it lands.

_A guide page for Android and Windows: appending, duplicating and reordering the pages of a notebook.
The internal map is [architecture.md](architecture.md)._

The **+** button at the end of the page strip appends a page after the one you are on...

## How it works

[pages-and-paper.md](pages-and-paper.md) owns the page model, the paper parameters and the page list.
```

Five steps, in this order:

1. **Name the job, not the subsystem.** `guide-search-and-organise.md` says what a reader wants;
   `guide-pages.md` would say what the code contains, and the reference layer already covers that.
2. **Open like every other page.** An `# H1` naming the job, then a `> Read when …` line, then the
   em-dash line that names the platform and points at the internal map — copy it from a neighbour and
   change the clause.
3. **Write the task, then stop.** Numbered steps with the control the reader can see, and the failure they
   might meet; quote the app's own words. Mechanism belongs in the owner, not here.
4. **Close on the owner.** A last `## How it works` section with one to three links to the spokes that own
   the how. This is what keeps the page a pointer rather than a second home.
5. **Register it twice.** A row in the table above (job first, link second), and a `For the task` line in
   the reader block of each spoke you linked, naming this page back.

`DocsGuardTest` fails the build when a page has no task line, no `## How it works` section, no owner link,
no index row, or when a spoke you leaned on does not name you back; it also resolves every relative link
and `#anchor` in the page. What it cannot see is a malformed heading section — an unterminated code span,
a heading swallowed into a paragraph — which is how this page itself once lost its closing section, so read
the rendered page before you commit.

## If you are here to change the app

The pages above are written for the person using it. The way in for the person changing it is
[the developer guide](../docs/developer-guide.md), and [CONTRIBUTING.md](../CONTRIBUTING.md) is
where a report or a patch starts. The internal map is [architecture.md](architecture.md).

## How it works

[writing-docs.md](writing-docs.md) owns the standard this page applies — the reader model, the six status
words, the seven phases and the Definition of Done — and [architecture.md](architecture.md) owns the index
that makes a new page reachable.
