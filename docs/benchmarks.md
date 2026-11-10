# Benchmarks: what a number in this corpus has to carry

> Read when you quote a measurement from these docs, re-run one, or compare two runs — and before a timing
> goes into a release note.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md). The mechanism a
measurement describes is its own page's; this page is about the measurement._

## Why a number needs its conditions

A figure with no machine behind it cannot be argued with, reproduced or compared, so it is not evidence —
it is an anecdote that happens to contain digits. The three reports in this repository therefore open with
the block [BenchmarkEnvironment.kt](../mobix-core/src/testFixtures/java/com/mobixournal/format/mobix/BenchmarkEnvironment.kt)
prints, and a number is quoted in a doc only with a link to the report it came from.

That block is deliberately incomplete where the JVM is. A field reading **Not recorded** is a fact about
the run, not a gap to be filled from memory: it means nothing on this host could establish the value, so
the figure beside it is historical rather than comparable.

## What a run records

| Field | Where it comes from |
|---|---|
| CPU | `PROCESSOR_IDENTIFIER` on Windows, `/proc/cpuinfo` on Linux, otherwise **Not recorded** |
| Cores | the JVM's available processors — logical, not physical |
| RAM | the platform MXBean's total memory, in GiB |
| OS | `os.name`, `os.version` and `os.arch` |
| JDK | the VM name and `java.version` this run was given |
| Kotlin | the compiler's own version, from the standard library |
| Gradle | the wrapper properties above the working directory |
| JVM | the arguments this run received, and its actual maximum heap |
| Workload | stated by the benchmark: the document, the page count, the blobs |
| Warm-up | stated by the benchmark: what ran before the first measurement |
| Iterations | stated by the benchmark: how many runs, and what is reported from them |
| Measurement | stated by the benchmark: bytes, milliseconds, entries, pages |
| Date | the day the report was written |

## What is measured, and what is left out

- **Bytes** are the file on disk. They are deterministic for a given workload and writer, so they are the
  one figure here that two machines may compare directly.
- **Milliseconds** are wall clock around a call, measured in the test process. They are not CPU time, they
  include whatever else the machine was doing, and the resolution is one millisecond — a report saying
  `1 ms` means *under about two*, which is why the comparisons that matter are stated as work done
  (entries inflated, pages opened) beside the timer.
- **Nothing measures the UI.** No benchmark here drives a frame loop, a canvas or a scroll, so none of them
  supports a claim about what a reader will feel.
- **Nothing is forked.** Every run shares one JVM with the test harness, so a figure carries the JIT state,
  the heap setting and the GC behaviour that block records.
- **Thermal and background state are not recorded**, because nothing here can observe them.

## How a run is warmed and aggregated

Each benchmark says in its own KDoc what it runs before measuring. Where it warms, it calls both paths once
and discards them, so the first measured run is not the slowest one. No outlier is removed and no mean is
reported — a trimmed sample invents a distribution the run did not have.

The figure reported depends on the sample, and the report says which it is:

- **Fewer than nine measured runs**: the **median**, and nothing else. A tail estimated from five samples is
the fifth sample with a name on it, so it is not quoted.
- **Nine or more**: **p50 and p95**, with the run count beside them. p50 here *is* the median; p95 is the
  ceiling an ordinary session meets, which is the figure a reader feels on a slow page. It is the nearest
  rank of the sorted runs, not an interpolation, so it is always a duration that actually happened.

A single run is reported as such rather than given a median of one, and a run whose conditions differ from
the ones in the metadata block is not averaged with it.

## Running one, and reading its report

```sh
# the .mobix / .xopp size and write-read comparison
./gradlew :app:testDebugUnitTest --tests "com.mobixournal.format.MobixXoppSizeBenchmarkTest"

# the paged open against the eager one
./gradlew :app:testDebugUnitTest --tests "com.mobixournal.format.mobix.MobixPagedOpenBenchmarkTest"

# one edited page of a two-hundred-page notebook
./gradlew :mobix-core:test --tests "com.mobixournal.format.mobix.MobixIncrementalSaveBenchmarkTest"

# the baseline: open, save and streaming across 1 to 1000 pages, with assets and audio
./gradlew :mobix-core:test --tests "com.mobixournal.format.mobix.MobixBaselineBenchmarkTest"

# the container's knobs: compression per content class, the archive boundary, a 1000-page search
./gradlew :mobix-core:test --tests "com.mobixournal.format.mobix.MobixArchivePolicyBenchmarkTest"
```

Each writes its report under the module's `build/reports/` and prints it, so `--info` shows the same text.
The report states the metadata block first, then the rows. Read the block before the rows: a figure from
another OS or JDK is a different measurement that happens to share a name.

## The baseline

The three reports above answer three separate questions on three separate corpora, which is why none of them
is a starting point for an optimisation. [MobixBaselineBenchmarkTest.kt](../mobix-core/src/test/java/com/mobixournal/format/mobix/MobixBaselineBenchmarkTest.kt)
is one: a four-point page count crossed with two ink densities, a save that changes nothing against a save
that changes one page, and a document carrying a bundled picture, a bundled PDF and a recording.

Measured on Linux 6.1.102 (amd64), 8 logical cores, 15.6 GiB RAM, OpenJDK 21.0.13, Kotlin 2.0.20, Gradle
8.14.5, `-Xmx512m` — the block the report prints in full. Open: one cold run then nine warm, p50 and p95 over
the warm runs. Save: one discarded warm-up then five runs.

**Open**, cold versus warm, and how much was actually decoded:

| Pages | Ink | File (B) | Cold to first page (ms) | Warm p50 | Warm p95 | Pages materialised | Peak heap (MiB) |
|---|---|---|---|---|---|---|---|
| 1 | light | 1 079 | 7 | 0 | 1 | 1 | 8 |
| 1 | heavy | 3 628 | 13 | 7 | 7 | 1 | 41 |
| 50 | light | 20 306 | 0 | 0 | 0 | 1 | 9 |
| 50 | heavy | 147 577 | 5 | 5 | 7 | 1 | 100 |
| 200 | light | 79 274 | 0 | 0 | 0 | 1 | 131 |
| 200 | heavy | 588 255 | 2 | 1 | 2 | 1 | 120 |
| 1000 | light | 394 128 | 0 | 0 | 0 | 1 | 105 |
| 1000 | heavy | 2 938 357 | 2 | 2 | 3 | 1 | 252 |

The paged open reads the manifest and materialises **one** page at every size and density, so the time to a
usable first page does not grow with the book: the cold column is the class loading and the archive open, and
its 7–13 ms on the single-page cases is the floor. The heap column is the peak the same run reached, which is
why it still grows with the file — the archive is open, its pages are not decoded.

**Save**, per the saver's own counters (entries rewritten and streamed, bytes each way):

| Pages | Case | p50 (ms) | p95 (ms) | Rewritten | Streamed | Streamed raw bytes | Output (B) |
|---|---|---|---|---|---|---|---|
| 50 | nothing changed | 1 | 2 | 2 | 51 | 17 663 | 19 682 |
| 50 | one page edited | 2 | 2 | 3 | 50 | 17 315 | 19 747 |
| 200 | nothing changed | 4 | 7 | 2 | 201 | 71 301 | 76 249 |
| 200 | one page edited | 5 | 7 | 3 | 200 | 70 953 | 76 317 |
| 1000 | nothing changed | 26 | 140 | 2 | 1001 | 359 342 | 378 305 |
| 1000 | one page edited | 23 | 24 | 3 | 1000 | 358 994 | 378 372 |

Two entries are always rewritten — the manifest and the source record — plus the edited page. Everything else
is streamed, so the work is the output size, not the page count; the 140 ms p95 on the thousand-page no-change
save is one slow run in five, which is exactly why the tail is reported instead of hidden behind the median.

**Carried bytes.** A 50-page document with a 1 MiB picture and a 2 MiB PDF saves in 3 ms with 0 asset
inflations; a 1-minute and a 20-minute recording save in 5 ms and 79 ms with 0 audio inflations, the twenty
minute one streaming 105 842 109 bytes. The save moves payloads, it never decodes them.

Not measured here, on purpose: **UI frame time**, because no benchmark drives a canvas; and a **120-minute
recording**, because a mono 44.1 kHz 16-bit WAV of that length is ~635 MB and this corpus carries no such
file. The twenty-minute case is the evidence that the path streams rather than holds, and no figure is
extrapolated from it.

## The container's own knobs

Three decisions were waiting on numbers rather than arguments — whether the writer should stop
DEFLATE-ing media, where an archive really stops, and whether a derived `index/` would earn its
save-time cost. [MobixArchivePolicyBenchmarkTest.kt](../mobix-core/src/test/java/com/mobixournal/format/mobix/MobixArchivePolicyBenchmarkTest.kt)
answers all three in one run and writes `mobix-compression-policy.txt`, `mobix-archive-boundary.txt` and
`mobix-index-profile.txt` under `mobix-core/build/reports/`. Measured on the host the baseline above
records, `-Xmx512m`.

**Compression**, the same 200-page notebook plus a 64 KiB picture, a 1 MiB photo and a 4 MiB recording:

| Policy | File (B) | vs today | Write (ms) | Cold open (ms) |
|---|---|---|---|---|
| everything DEFLATED (today) | 5 386 782 | — | 175 | 6 |
| page JSON DEFLATED, `assets/` STORED | 5 385 162 | −1 620 | 18 | 6 |
| everything STORED | 5 433 819 | +47 037 | 13 | 5 |

The 5.3 MB of incompressible media hide the JSON, so the same comparison over the compressible half alone:
DEFLATE takes it from 124 699 B to 76 042 B — **1.6×**, not an order of magnitude, because page JSON is
mostly ids and numbers. The conclusion is therefore about CPU, not size: DEFLATE is worth its cost on page
JSON and does nothing for media, where the selective policy saves 1 620 bytes and drops the write from
175 ms to 18 ms. That is one run each, so it justifies a candidate task, not a change to the default.

**The boundary.** The caps, not ZIP, are what stop a document:

| Bound | Value | What happens |
|---|---|---|
| Entries, read | 20 000 (`MobixSafetyCaps.maxEntries`) | 20 000 opens (19 999 payload entries plus the mimetype); 20 001 refused, naming the limit |
| Entries, written | 65 534 (`ClassicZipLimits.entry`) | 65 534 written; the 65 535th throws `ZIP entry limit` |
| One entry / the archive | 256 MiB / 1 GiB | refused while reading, before allocation |
| Any ZIP field | 4 GiB − 1 | throws `ZIP size limit`; no truncated field is published |

A 4 GiB archive was not materialised — only its guard was exercised, and the report says so. The binding
limits sit an order of magnitude below classic ZIP's 4 GiB, so **ZIP64 stays deferred** with a stated
trigger: the first legitimate document needing a single entry or a total past 1 GiB. The caps move first.

**A derived index.** A thousand-page document, 8 000 text boxes, searched through the reader's own surface:
every page is decoded once, the hit is found on page 500, and the first search takes 261 ms against 83 ms
for the second (71 ms when nothing matches). Laying out all 1 000 pages from `pageSize` decodes **none** and
costs under a millisecond. A search index or a page-size index would add a second source of truth and a
save-time cost for a wait nobody perceives, so the answer is **no**.

## When two runs may be compared

Two timings are comparable when the CPU, the JDK, the Gradle and the workload in their blocks agree, and
neither came from an emulator — an emulator's CPU and I/O are its host's, shared with whatever else is
running, so its numbers are a functional check and never a performance claim. Where the blocks disagree,
the only defensible comparison is the deterministic half: bytes on disk, and the counts of work done.

A historical figure whose block says **Not recorded** is quoted as exactly that. It is not updated to look
current, and a fresh run does not silently replace it: the two are different measurements, and the doc says
which one it is quoting.

## Adding a benchmark

A new one is worth adding when a decision rests on the answer — not to decorate a page. It states its
workload, warm-up, iterations and unit; prints the metadata block; writes a report a reader can open; and
asserts a fact about **work done** rather than about a stopwatch, so the test fails when the implementation
regresses even on a slow machine. The existing reports are the pattern to copy, and the baseline above is the
one to measure an optimisation against.
