package com.mobixournal.collections
import org.junit.Assert.*
import org.junit.Test

class PersistentIntMapTest {
    @Test fun branchesPreserveValuesAndFirstInsertionOrder() {
        var map = PersistentIntMap.empty<Int>()
        val expected = linkedMapOf<Int, Int>()
        val random = java.util.Random(17)
        repeat(10000) {
            val old = map
            val key = random.nextInt(1000)
            if (random.nextBoolean()) { map = map.put(key, it); expected[key] = it }
            else { map = map.removeKey(key); expected.remove(key) }
            if (it % 100 == 0) {
                assertEquals(expected, map)
                assertEquals(expected.keys.toList(), map.keys.toList())
                assertEquals(old, old.toMap())
            }
        }
        val saved = map
        map = map.put(-1, 1)
        assertFalse(saved.containsKey(-1))
        assertEquals(1, map[-1])
    }
}
