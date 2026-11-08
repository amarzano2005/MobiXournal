package com.mobixournal.format.mobix

import java.io.File
import java.lang.management.ManagementFactory
import java.time.LocalDate
import java.util.Locale

/**
 * The conditions a benchmark number was measured under, printed as the block every report opens with.
 *
 * A timing without its machine is a number a reader cannot use: it cannot be reproduced, compared or
 * argued with. This is the shared half of the three benchmarks in this repository
 * (`MobixXoppSizeBenchmarkTest`, `MobixPagedOpenBenchmarkTest`, `MobixIncrementalSaveBenchmarkTest`),
 * so the three reports state their conditions the same way instead of each growing its own header.
 *
 * What it can read, it reads; what it cannot, it says so as [NOT_RECORDED] rather than guessing. The
 * honest gaps are the point: a JVM exposes no CPU model portably, and the Gradle and Kotlin versions
 * are read from the wrapper and the compiler this run used. A field that says *Not recorded* means
 * exactly that — the number beside it is historical and not comparable. The rules a reader applies to
 * these values live in `docs/benchmarks.md`.
 *
 * It lives in `testFixtures` rather than in three copies because both modules that carry benchmarks
 * can see it: `mobix-core`'s own tests and the app's suite, which already depends on this fixture set.
 */
object BenchmarkEnvironment {

    /** The literal a field carries when this run cannot establish it. Never a guess, never blank. */
    const val NOT_RECORDED = "Not recorded"

    /**
     * The metadata block for one report, as a Markdown table.
     *
     * The four parameters are the parts only the benchmark knows: what it weighs, how it warms up,
     * how many times it measures and in what unit. Everything else describes the machine and is read.
     */
    fun table(
        workload: String,
        warmUp: String,
        iterations: String,
        unit: String,
    ): String {
        val rows = listOf(
            "CPU" to cpuModel(),
            "Cores" to "${Runtime.getRuntime().availableProcessors()} logical",
            "RAM" to totalMemory(),
            "OS" to listOfNotNull(
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch")?.let { "($it)" },
            ).joinToString(" "),
            "JDK" to listOfNotNull(
                System.getProperty("java.vm.name"),
                System.getProperty("java.version"),
            ).joinToString(" "),
            "Kotlin" to KotlinVersion.CURRENT.toString(),
            "Gradle" to gradleVersion(),
            "JVM" to jvmOptions(),
            "Workload" to workload,
            "Warm-up" to warmUp,
            "Iterations" to iterations,
            "Measurement" to unit,
            "Date" to LocalDate.now().toString(),
        )
        return buildString {
            appendLine("| Field | Value |")
            appendLine("|---|---|")
            for ((field, value) in rows) appendLine("| $field | $value |")
        }
    }

    /**
     * The processor's model name, where the platform lets one be read without a subprocess: the
     * `PROCESSOR_IDENTIFIER` Windows sets, or `/proc/cpuinfo` on Linux. Neither is part of the JVM's
     * API, so a host that answers neither is recorded as such instead of being described generically.
     */
    private fun cpuModel(): String {
        System.getenv("PROCESSOR_IDENTIFIER")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val cpuinfo = File("/proc/cpuinfo")
        if (cpuinfo.isFile) {
            val name = cpuinfo.useLines { lines ->
                lines.firstOrNull { it.startsWith("model name") }?.substringAfter(':')?.trim()
            }
            if (!name.isNullOrEmpty()) return name
        }
        return "$NOT_RECORDED (no portable processor identifier on this platform)"
    }

    /** The machine's physical memory, through the platform MXBean when the JVM provides it. */
    private fun totalMemory(): String {
        val bytes = try {
            (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
                ?.totalMemorySize
        } catch (error: LinkageError) {
            null
        }
        if (bytes == null || bytes <= 0) return NOT_RECORDED
        // `Locale.ROOT`: a report is read by a person in another locale, and `31,8 GiB` reads as two
        // values anywhere that expects a decimal point.
        return "%.1f GiB".format(Locale.ROOT, bytes / 1024.0 / 1024.0 / 1024.0)
    }

    /**
     * The Gradle that ran this build, read from the wrapper properties above the working directory.
     *
     * Gradle does not expose its own version to a test, and hard-coding it in a report is how a
     * document goes stale; the wrapper file is the same authority the build uses.
     */
    private fun gradleVersion(): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val properties = File(directory, "gradle/wrapper/gradle-wrapper.properties")
            if (properties.isFile) {
                val url = properties.readLines()
                    .firstOrNull { it.startsWith("distributionUrl") }
                    ?: return NOT_RECORDED
                Regex("""gradle-([0-9][^-]*)-\w+\.zip""")
                    .find(url)?.groupValues?.get(1)?.let { return it }
                return NOT_RECORDED
            }
            directory = directory.parentFile
        }
        return NOT_RECORDED
    }

    /**
     * The runtime settings that could move a measurement, plus the heap this run actually has.
     *
     * Only `-X` arguments are kept. Gradle hands the test JVM a long `-Djava.library.path` and its own
     * worker paths, which describe the harness rather than the run and turn one table cell into a
     * paragraph; the heap is stated separately because it is the one that changes what a large-document
     * benchmark costs.
     */
    private fun jvmOptions(): String {
        val arguments = ManagementFactory.getRuntimeMXBean().inputArguments.filter { it.startsWith("-X") }
        val heap = "max heap ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MiB"
        return if (arguments.isEmpty()) "defaults; $heap" else "${arguments.joinToString(" ")}; $heap"
    }
}
