package com.mobixournal.collections

/** Immutable 32-way vector. Appends copy a tail or one tree path; older snapshots share the rest. */
class PersistentVector<E> private constructor(
    override val size: Int,
    private val shift: Int,
    private val root: Array<Any?>,
    private val tail: Array<Any?>,
) : AbstractList<E>(), RandomAccess {
    private val tailOffset: Int get() = if (size < 32) 0 else ((size - 1) ushr 5) shl 5

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        checkIndex(index)
        if (index >= tailOffset) return tail[index and 31] as E
        var node = root
        var level = shift
        while (level > 0) {
            node = node[(index ushr level) and 31] as Array<Any?>
            level -= 5
        }
        return node[index and 31] as E
    }

    fun appended(value: E): PersistentVector<E> {
        require(size < Int.MAX_VALUE) { "Vector is full" }
        if (size - tailOffset < 32) {
            val next = tail.copyOf(tail.size + 1)
            next[next.lastIndex] = value
            return PersistentVector(size + 1, shift, root, next)
        }
        val overflow = (size ushr 5) > (1 shl shift)
        val nextRoot = if (overflow) arrayOfNulls<Any?>(32).also {
            it[0] = root
            it[1] = path(shift, tail)
        } else pushTail(shift, root, tail, size - 1)
        return PersistentVector(size + 1, shift + if (overflow) 5 else 0, nextRoot, arrayOf(value))
    }

    fun updated(index: Int, value: E): PersistentVector<E> {
        checkIndex(index)
        return if (index >= tailOffset) {
            PersistentVector(size, shift, root, tail.copyOf().also { it[index and 31] = value })
        } else PersistentVector(size, shift, update(shift, root, index, value), tail)
    }

    private fun checkIndex(index: Int) {
        if (index !in 0 until size) throw IndexOutOfBoundsException("index=$index size=$size")
    }

    @Suppress("UNCHECKED_CAST")
    private fun pushTail(level: Int, parent: Array<Any?>, node: Array<Any?>, index: Int): Array<Any?> {
        val next = parent.copyOf()
        val slot = (index ushr level) and 31
        next[slot] = if (level == 5) node else {
            val child = parent[slot] as Array<Any?>?
            if (child == null) path(level - 5, node) else pushTail(level - 5, child, node, index)
        }
        return next
    }

    private fun path(level: Int, node: Array<Any?>): Array<Any?> =
        if (level == 0) node else arrayOfNulls<Any?>(32).also { it[0] = path(level - 5, node) }

    @Suppress("UNCHECKED_CAST")
    private fun update(level: Int, node: Array<Any?>, index: Int, value: E): Array<Any?> =
        node.copyOf().also { next ->
            val slot = (index ushr level) and 31
            next[slot] = if (level == 0) value else update(level - 5, node[slot] as Array<Any?>, index, value)
        }

    companion object {
        fun <E> empty(): PersistentVector<E> = PersistentVector(0, 5, arrayOfNulls(32), emptyArray())
        fun <E> of(values: List<E>): PersistentVector<E> {
            if (values is PersistentVector<E>) return values
            var result = empty<E>()
            for (value in values) result = result.appended(value)
            return result
        }
    }
}

/** Promotes a plain imported list once, then shares tree nodes between subsequent edit snapshots. */
fun <E> List<E>.persistentAppend(value: E): List<E> = PersistentVector.of(this).appended(value)
