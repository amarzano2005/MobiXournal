package com.mobixournal.render

import org.junit.Assert.*
import org.junit.Test

class CanvasChromeRefreshTest {
    @Test fun unchangedChromeDoesNotAskTheNativeSurfaceToPaintAgain() {
        val chrome = CanvasChrome()
        assertTrue(chrome.applyColors(1, 2, 3, 4, 5, 6))
        repeat(100) { assertFalse(chrome.applyColors(1, 2, 3, 4, 5, 6)) }
        assertTrue(chrome.applyColors(1, 2, 3, 4, 5, 7))
        assertFalse(chrome.applyColors(1, 2, 3, 4, 5, 7))
    }
}
