package com.mobixournal.collections

/** Path-copied AVL map with stable insertion order and O(1) immutable snapshots. */
class PersistentIntMap<V> private constructor(
    private val root: Node<V>?, private val order: PersistentVector<Int>, override val size: Int,
) : AbstractMap<Int, V>() {
    private data class Node<V>(val key: Int, val value: V, val ordinal: Int,
        val left: Node<V>? = null, val right: Node<V>? = null) {
        val height: Int = 1 + maxOf(left?.height ?: 0, right?.height ?: 0)
    }
    private fun node(key: Int): Node<V>? {
        var current = root
        while (current != null) {
            current = when { key < current.key -> current.left; key > current.key -> current.right; else -> return current }
        }
        return null
    }
    override fun get(key: Int): V? = node(key)?.value
    override fun containsKey(key: Int): Boolean = node(key) != null
    override val entries: Set<Map.Entry<Int, V>> get() = object : AbstractSet<Map.Entry<Int, V>>() {
        override val size: Int get() = this@PersistentIntMap.size
        override fun iterator(): Iterator<Map.Entry<Int, V>> = order.asSequence().mapIndexedNotNull { ordinal, key ->
            node(key)?.takeIf { it.ordinal == ordinal }?.let { java.util.AbstractMap.SimpleImmutableEntry(it.key, it.value) }
        }.iterator()
    }
    fun put(key: Int, value: V): PersistentIntMap<V> {
        val old = node(key)
        val nextOrder = if (old == null) order.appended(key) else order
        return PersistentIntMap(insert(root, key, value, old?.ordinal ?: order.size), nextOrder, size + if (old == null) 1 else 0)
    }
    fun removeKey(key: Int): PersistentIntMap<V> {
        if (!containsKey(key)) return this
        val next = PersistentIntMap(remove(root, key), order, size - 1)
        if (next.size == 0) return empty()
        if (order.size <= next.size * 2 + 64) return next
        var compact = empty<V>()
        for ((k, value) in next) compact = compact.put(k, value)
        return compact
    }
    private fun insert(n: Node<V>?, key: Int, value: V, ordinal: Int): Node<V> = when {
        n == null -> Node(key, value, ordinal)
        key < n.key -> balance(n.copy(left = insert(n.left, key, value, ordinal)))
        key > n.key -> balance(n.copy(right = insert(n.right, key, value, ordinal)))
        else -> n.copy(value = value)
    }
    private fun remove(n: Node<V>?, key: Int): Node<V>? {
        n ?: return null
        return when {
            key < n.key -> balance(n.copy(left = remove(n.left, key)))
            key > n.key -> balance(n.copy(right = remove(n.right, key)))
            n.left == null -> n.right
            n.right == null -> n.left
            else -> {
                var successor = n.right
                while (successor!!.left != null) successor = successor.left
                balance(successor.copy(left = n.left, right = remove(n.right, successor.key)))
            }
        }
    }
    private fun balance(n: Node<V>): Node<V> {
        val delta = height(n.left) - height(n.right)
        if (delta > 1) {
            val left = n.left!!
            return rotateRight(if (height(left.left) < height(left.right)) n.copy(left = rotateLeft(left)) else n)
        }
        if (delta < -1) {
            val right = n.right!!
            return rotateLeft(if (height(right.right) < height(right.left)) n.copy(right = rotateRight(right)) else n)
        }
        return n
    }
    private fun height(n: Node<V>?): Int = n?.height ?: 0
    private fun rotateLeft(n: Node<V>): Node<V> {
        val pivot = n.right!!
        return pivot.copy(left = n.copy(right = pivot.left))
    }
    private fun rotateRight(n: Node<V>): Node<V> {
        val pivot = n.left!!
        return pivot.copy(right = n.copy(left = pivot.right))
    }
    companion object {
        fun <V> empty(): PersistentIntMap<V> = PersistentIntMap(null, PersistentVector.empty(), 0)
    }
}
