package com.mobixournal.collections

import org.junit.Assert.*
import org.junit.Test

class IdentityLruCacheTest {
    private class HostileKey {
        override fun hashCode(): Int = error("structural hash must never run")
        override fun equals(other: Any?): Boolean = error("structural equals must never run")
    }

    @Test fun evictionUsesIdentityAndAccessOrder() {
        val cache = IdentityLruCache<HostileKey, String>(2)
        val a = HostileKey(); val b = HostileKey(); val c = HostileKey()
        cache.put(a, "a"); cache.put(b, "b")
        assertEquals("a", cache[a])
        cache.put(c, "c")
        assertNull(cache[b])
        assertEquals("a", cache[a])
        cache.put(a, "changed")
        assertEquals(2, cache.size)
        assertEquals("changed", cache[a])
    }

    @Test fun weightedBudgetIncludesUpdatesAndRejectsOversizedEntries() {
        val cache = IdentityLruCache<Any, Long>(100, 10) { it }
        val a = Any(); val b = Any()
        cache.put(a, 6); cache.put(b, 6)
        assertNull(cache[a])
        assertEquals(6L, cache.cachedWeight)
        cache.put(b, 11)
        assertEquals(0, cache.size)
        cache.put(a, 10)
        cache.clear()
        assertEquals(0L, cache.cachedWeight)
    }
}
