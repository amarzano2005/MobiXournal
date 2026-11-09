package com.mobixournal.format.mobix

import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import org.junit.Assert.*
import org.junit.Test

class ClassicZipLimitsTest {
    @Test fun rejectsOverflowBeforeCopyingRawPayload() {
        val entry = object : MobixRawEntry {
            override val method = ZipEntry.STORED
            override val crc = 0xFFFFFFFFL
            override val size = 0xFFFFFFFFL
            override val compressedSize = size
            override fun copyTo(sink: OutputStream) { fail("oversized entry must not be copied") }
        }
        assertThrows(IOException::class.java) { MobixZip.Writer(OutputStream.nullOutputStream()).putRaw("asset", entry) }
    }

    @Test fun rejectsClassicSentinelsAndOversizedNames() {
        ClassicZipLimits.entry(0, 0, 1, 0xFFFFFFFEL, 0xFFFFFFFEL)
        assertThrows(IOException::class.java) { ClassicZipLimits.entry(0xFFFE, 0, 1, 0, 0) }
        assertThrows(IOException::class.java) { ClassicZipLimits.entry(0, 0xFFFFFFFFL, 1, 0, 0) }
        assertThrows(IOException::class.java) { ClassicZipLimits.entry(0, 0, 65536, 0, 0) }
    }
}
