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
and discards them, so the first measured run is not the slowest one. The reported figure is the **median**
of the measured runs: no mean, no percentiles, and no outlier is removed — with five runs, trimming would
be inventing a distribution the sample cannot support. A single run is reported as such rather than given a
median of one.

## Running one, and reading its report

```sh
# the .mobix / .xopp size and write-read comparison
./gradlew :app:testDebugUnitTest --tests "com.mobixournal.format.MobixXoppSizeBenchmarkTest"

# the paged open against the eager one
./gradlew :app:testDebugUnitTest --tests "com.mobixournal.format.mobix.MobixPagedOpenBenchmarkTest"

# one edited page of a two-hundred-page notebook
./gradlew :mobix-core:test --tests "com.mobixournal.format.mobix.MobixIncrementalSaveBenchmarkTest"
```

Each writes its report under the module's `build/reports/` and prints it, so `--info` shows the same text.
The report states the metadata block first, then the rows. Read the block before the rows: a figure from
another OS or JDK is a different measurement that happens to share a name.

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
regresses even on a slow machine. The three existing reports are the pattern to copy.
