package com.mobixournal.format.xml

import java.io.Reader

/** Sliding input window; only an explicitly captured unknown subtree pins consumed markup. */
internal class XmlInput(private val reader: Reader) {
    private val buffer = StringBuilder()
    private var base = 0
    private var eof = false
    private val chunk = CharArray(8192)
    var pinned = false
    fun has(index: Int): Boolean {
        while (!eof && index >= base + buffer.length) {
            val count = reader.read(chunk)
            if (count < 0) eof = true else if (count > 0) buffer.append(chunk, 0, count)
        }
        return index >= base && index < base + buffer.length
    }
    operator fun get(index: Int): Char {
        check(has(index)) { "XML cursor outside input window" }
        return buffer[index - base]
    }
    fun startsWith(value: String, from: Int = 0): Boolean =
        value.indices.all { has(from + it) && get(from + it) == value[it] }
    fun indexOf(value: Char, from: Int): Int {
        var index = from
        while (has(index)) { if (get(index) == value) return index; index++ }
        return -1
    }
    fun indexOf(value: String, from: Int): Int {
        var index = from
        while (has(index)) { if (startsWith(value, index)) return index; index++ }
        return -1
    }
    fun substring(from: Int, to: Int): String {
        if (to > from) check(has(to - 1))
        return buffer.substring(from - base, to - base)
    }
    fun discardBefore(position: Int) {
        if (!pinned && position > base) {
            buffer.delete(0, position - base)
            base = position
        }
    }
}
