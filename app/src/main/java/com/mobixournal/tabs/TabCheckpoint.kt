package com.mobixournal.tabs

import com.mobixournal.format.model.Document
import java.io.File

/** Referential model identity avoids hashing/decoding a lazy notebook just to skip an idle write. */
internal class TabCheckpoint private constructor(
    private val document: Document,
    private val suffix: String,
    private val payloadToken: Any?,
    private val files: Map<String, FileStamp?>,
) {
    private data class FileStamp(val path: String, val length: Long, val modified: Long)

    fun matches(other: TabCheckpoint): Boolean =
        document === other.document && suffix == other.suffix && payloadToken == other.payloadToken && files == other.files

    companion object {
        fun of(tab: OpenTab, suffix: String, sources: SnapshotSources, payloadToken: Any? = null): TabCheckpoint {
            fun stamp(file: File?): FileStamp? = file?.let {
                FileStamp(it.absolutePath, it.length(), it.lastModified())
            }
            val files = sources.images.mapKeys { "image:${it.key}" }.mapValues { stamp(it.value) }.toMutableMap()
            files["pdf"] = stamp(sources.pdf)
            return TabCheckpoint(tab.document, suffix, payloadToken, files)
        }
    }
}
