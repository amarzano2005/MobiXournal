package com.mobixournal.desktop.ink

import com.mobixournal.render.InkLine
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/** Geometry fingerprint retains no Stroke objects and avoids the collisions of a 32-bit line hash. */
internal fun lineFingerprint(line: InkLine): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DataOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest)).use { out ->
        out.writeInt(line.words.size)
        for (word in line.words) {
            out.writeInt(word.size)
            for (stroke in word) {
                out.writeInt(stroke.points.size)
                for (point in stroke.points) {
                    out.writeDouble(point.x); out.writeDouble(point.y); out.writeDouble(point.width)
                }
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
