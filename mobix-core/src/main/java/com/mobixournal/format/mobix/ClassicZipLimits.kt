package com.mobixournal.format.mobix

import java.io.IOException

/** Guard the non-ZIP64 incremental writer before truncated numeric fields can be published. */
internal object ClassicZipLimits {
    fun field(value: Long) {
        if (value < 0 || value >= 0xFFFFFFFFL) {
            throw IOException("This incremental save exceeds the ZIP size limit; save to a new file instead")
        }
    }

    fun entry(count: Int, offset: Long, nameBytes: Int, size: Long, compressed: Long) {
        if (count >= 0xFFFE || nameBytes > 0xFFFF) {
            throw IOException("This incremental save exceeds the ZIP entry limit; save to a new file instead")
        }
        field(offset)
        field(size)
        field(compressed)
    }
}
