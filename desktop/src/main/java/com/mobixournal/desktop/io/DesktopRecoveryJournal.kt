package com.mobixournal.desktop.io

import com.mobixournal.format.mobix.*
import java.io.*
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Private recovery log. Metadata lives outside the document entry namespace. */
internal class DesktopRecoveryJournal(private val baseline: File) {
    private fun records() = baseline.parentFile.listFiles()?.filter {
        it.name.startsWith(baseline.name + ".delta-") && it.name.endsWith(".zip")
    }.orEmpty().sortedBy { it.name }
    private fun sequence(file: File) = file.name.substringAfterLast(".delta-").removeSuffix(".zip").toLongOrNull() ?: 0L
    fun nextSequence(): Long = maxOf(System.currentTimeMillis(), RecoveryZipMarker.read(baseline) + 1,
        (records().maxOfOrNull(::sequence) ?: 0L) + 1)
    fun mark(file: File, sequence: Long) = RecoveryZipMarker.write(file, sequence)

    private class Record(file: File) : MobixEntrySource {
        private val zip = ZipFile(file)
        val order: List<String>
        override val entryNames: Set<String>
        init {
            try {
                order = DataInputStream(zip.getInputStream(requireNotNull(zip.getEntry(ORDER)))).use { input ->
                    val count = input.readInt(); require(count in 0..1_000_000)
                    List(count) { input.readUTF() }
                }
                entryNames = zip.entries().asSequence().filter { it.name.startsWith(DATA) }.map {
                    String(Base64.getUrlDecoder().decode(it.name.removePrefix(DATA)), Charsets.UTF_8)
                }.toSet()
                require(MobixFormat.MANIFEST_ENTRY in entryNames)
            } catch (failure: Throwable) { zip.close(); throw failure }
        }
        override fun openEntry(name: String): InputStream? = zip.getEntry(encoded(name))?.let(zip::getInputStream)
        override fun close() = zip.close()
    }
    private class Layered(val sources: List<MobixEntrySource>, val order: List<String>?, val position: Long) : MobixEntrySource {
        override val entryNames = sources.flatMap { it.entryNames }.toSet()
        override fun openEntry(name: String): InputStream? =
            sources.asReversed().firstOrNull { name in it.entryNames }?.openEntry(name)
        override fun close() { sources.asReversed().forEach { it.close() } }
    }
    fun layered(): MobixEntrySource {
        val sources = ArrayList<MobixEntrySource>()
        try {
            sources += MobixEntrySource.fromFile(baseline)
            var position = RecoveryZipMarker.read(baseline)
            var order: List<String>? = null
            for (file in records().filter { sequence(it) > position }) {
                val record = runCatching { Record(file) }.getOrNull() ?: break
                sources += record; order = record.order; position = sequence(file)
            }
            return Layered(sources, order, position)
        } catch (failure: Throwable) { sources.forEach { it.close() }; throw failure }
    }

    fun stage(request: DesktopRecovery.Request, sequence: Long): Pair<File, File>? = layered().use { source ->
                val previous = MobixManifest.read(source)
                if (previous == null || previous.documentId != request.model.documentId || previous.canvasMode != request.shape.mode) null else {
                    val reuse = MobixPageReuse.of(request.model.pages, source, request.shape.mode, previous)
                    val carried = source.entryNames.filter { it.startsWith("assets/") }.associateWith { name ->
                        MobixPayload.ofSource({ source.openEntry(name) })
                    }
                    val now = System.currentTimeMillis()
                    val model = MobixDocument.of(request.model, DesktopSaves.APP_VERSION, now,
                        canvasMode = request.shape.mode, reuse = reuse, payloads = carried,
                        chunkCoords = request.shape.chunkCoords, canvas = request.shape.canvas, previousManifest = previous)
                    val plan = model.encode(now, backgroundPayloadsOf(request.document.pdfSource(), emptyMap()),
                        { request.audio.file(it)?.let(MobixPayload::of) }, reuse)
                    stage(plan, source, sequence)
                }
            }

    fun stage(plan: MobixSavePlan, source: MobixEntrySource, sequence: Long): Pair<File, File> {
        val dirty = MobixIncrementalSave.dirtyEntries(plan.entries, MobixManifest.read(source), source)
        val target = File(baseline.parentFile, baseline.name + ".delta-" + sequence.toString().padStart(20, '0') + ".zip")
        val temp = File.createTempFile("delta-", ".writing", baseline.parentFile)
        try {
            ZipOutputStream(temp.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(ORDER))
                val data = DataOutputStream(zip)
                data.writeInt(plan.order.size); plan.order.forEach(data::writeUTF)
                zip.closeEntry()
                for ((name, payload) in dirty) {
                    zip.putNextEntry(ZipEntry(encoded(name)))
                    payload.open().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return temp to target
        } catch (failure: Throwable) { temp.delete(); throw failure }
    }

    /** Baseline publication precedes cleanup. Its ZIP comment watermark makes replay idempotent. */
    fun compact() {
        val position = RecoveryZipMarker.read(baseline)
        if (records().none { sequence(it) > position }) { discardOld(); return }
        val temp = File.createTempFile("compact-", ".writing", baseline.parentFile)
        try {
            layered().use { raw ->
                val source = raw as Layered
                val order = source.order ?: return
                temp.outputStream().use { out -> MobixIncrementalSave.repack(source, MobixSavePlan(emptyList(), order), out) }
                mark(temp, source.position)
            }
            DesktopSaves.replace(temp, baseline)
            discardOld()
        } finally { temp.delete() }
    }
    fun shouldCompact(): Boolean = records().size >= 16 || records().sumOf { it.length() } > maxOf(1_048_576L, baseline.length() / 2)
    fun discardOld() {
        val position = if (baseline.isFile) RecoveryZipMarker.read(baseline) else Long.MAX_VALUE
        records().filter { sequence(it) <= position }.forEach { it.delete() }
    }
    fun discardAll() { records().forEach { it.delete() } }
    private companion object {
        const val ORDER = "order"
        const val DATA = "entries/"
        fun encoded(name: String): String = DATA + Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))
    }
}
