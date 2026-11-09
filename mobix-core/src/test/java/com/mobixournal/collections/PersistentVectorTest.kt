package com.mobixournal.collections

import org.junit.Assert.*
import org.junit.Test

class PersistentVectorTest {
    @Test fun appendAndUpdateShareImmutableSnapshotsAcrossTreeBoundaries() {
        var vector = PersistentVector.empty<Int>()
        val snapshots = mutableListOf<PersistentVector<Int>>()
        repeat(100_000) { index ->
            vector = vector.appended(index)
            if (index in listOf(0, 31, 32, 1023, 1055, 1056, 32767, 32800)) snapshots += vector
        }
        assertEquals((0 until 100_000).toList(), vector)
        for (snapshot in snapshots) assertEquals((0 until snapshot.size).toList(), snapshot)
        for (index in listOf(0, 31, 32, 1056, 99999)) {
            val changed = vector.updated(index, -1)
            assertEquals(-1, changed[index])
            assertEquals(index, vector[index])
            assertEquals(vector.size, changed.size)
        }
    }

    @Test fun promotionDoesNotMutateTheImportedList() {
        val imported = mutableListOf(1, 2)
        val grown = imported.persistentAppend(3)
        imported[0] = 9
        assertEquals(listOf(1, 2, 3), grown)
        assertEquals(listOf(9, 2), imported)
        assertSame(grown, PersistentVector.of(grown))
    }
}
