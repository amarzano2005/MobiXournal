package com.mobixournal.collections

/** A bounded access-ordered cache whose keys never invoke a model's structural hash/equals. */
class IdentityLruCache<K : Any, V>(
    private val capacity: Int,
    private val maxWeight: Long = Long.MAX_VALUE,
    private val weigh: (V) -> Long = { 1L },
) {
    private class Key<K : Any>(val value: K) {
        override fun hashCode(): Int = System.identityHashCode(value)
        override fun equals(other: Any?): Boolean = other is Key<*> && value === other.value
    }
    private class Entry<V>(val value: V, val weight: Long)
    private val entries = LinkedHashMap<Key<K>, Entry<V>>(16, 0.75f, true)
    private var weight = 0L

    init { require(capacity > 0 && maxWeight > 0) }

    @Synchronized operator fun get(key: K): V? = entries[Key(key)]?.value

    @Synchronized fun put(key: K, value: V) {
        val identity = Key(key)
        entries.remove(identity)?.let { weight -= it.weight }
        val cost = weigh(value).coerceAtLeast(1)
        if (cost > maxWeight) return
        entries[identity] = Entry(value, cost)
        weight += cost
        val oldest = entries.entries.iterator()
        while (entries.size > capacity || weight > maxWeight) {
            weight -= oldest.next().value.weight
            oldest.remove()
        }
    }

    @Synchronized fun clear() { entries.clear(); weight = 0 }
    val size: Int @Synchronized get() = entries.size
    val cachedWeight: Long @Synchronized get() = weight
}
