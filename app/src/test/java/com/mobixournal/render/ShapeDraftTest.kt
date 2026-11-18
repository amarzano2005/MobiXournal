package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape draft as an object: what a drag fixes, what a move changes, and what survives lift-off.
 *
 * The three rules that used to live at the call sites are pinned here — the anchor and the width are
 * captured once, a move replaces only the end, and finishing the gesture keeps the geometry the commit
 * builds the table or the component from.
 */
class ShapeDraftTest {

    private val draft = ShapeDraft()

    @Test
    fun `a shape tool opens a draft and a freehand stroke closes one`() {
        draft.begin(shape = true)
        assertTrue(draft.active)

        draft.begin(shape = false)
        assertFalse("a freehand press must not inherit the shape in flight", draft.active)
    }

    @Test
    fun `placing anchors the drag, pins its width and starts the end on the anchor`() {
        draft.begin(shape = true)
        draft.place(x = 20.0, y = 30.0, widthPt = 2.5)

        assertEquals(20.0, draft.startX, 1e-9)
        assertEquals(30.0, draft.startY, 1e-9)
        assertEquals("an untouched draft draws nothing", 20.0, draft.endX, 1e-9)
        assertEquals(30.0, draft.endY, 1e-9)
        assertEquals(2.5, draft.widthPt, 1e-9)
    }

    @Test
    fun `a move replaces the end and never the anchor or the width`() {
        draft.begin(shape = true)
        draft.place(20.0, 30.0, widthPt = 2.5)

        draft.extend(140.0, 90.0)
        draft.extend(160.0, 40.0)

        assertEquals(20.0, draft.startX, 1e-9)
        assertEquals(30.0, draft.startY, 1e-9)
        assertEquals(160.0, draft.endX, 1e-9)
        assertEquals(40.0, draft.endY, 1e-9)
        assertEquals("a shape ignores pressure, so the width never changes mid-drag", 2.5, draft.widthPt, 1e-9)
    }

    @Test
    fun `finishing keeps the geometry the commit builds from`() {
        draft.begin(shape = true)
        draft.place(10.0, 10.0, widthPt = 1.5)
        draft.extend(90.0, 70.0)

        draft.finish()

        assertFalse(draft.active)
        assertEquals(10.0, draft.startX, 1e-9)
        assertEquals(90.0, draft.endX, 1e-9)
        assertEquals(70.0, draft.endY, 1e-9)
    }

    @Test
    fun `a drag under six points is a tap, and one past it is not`() {
        draft.begin(shape = true)

        draft.place(0.0, 0.0, widthPt = 1.5)
        draft.extend(3.0, 3.0)
        assertTrue("a table places at a default size on a tap", draft.isTap())

        draft.extend(6.0, 0.0)
        assertFalse("a real rectangle is kept as drawn", draft.isTap())
    }

    /**
     * The press that **let go** of the selection opens a draft that writes nothing if it stays a tap.
     *
     * Every placement slot stays in hand after its object is placed, so the press on empty paper begins another
     * placement; only the release knows whether the hand drew or merely let go.
     */
    @Test
    fun `a letting-go press opens a draft that writes nothing on a tap`() {
        draft.letGoNext()
        draft.begin(shape = true)
        draft.place(40.0, 40.0, widthPt = 1.5)

        assertTrue("the press only let go", draft.onlyLetGo)
        assertTrue("and it never travelled", draft.isTap())

        draft.letGoNext()
        draft.begin(shape = true)
        draft.place(40.0, 40.0, widthPt = 1.5)
        draft.extend(180.0, 120.0)
        assertFalse("a real drag is a drawing, not a letting go", draft.isTap())
    }

    @Test
    fun `an ordinary press inherits no letting go`() {
        draft.letGoNext()
        draft.begin(shape = true)
        draft.begin(shape = true)
        assertFalse("the ask belonged to the press before it", draft.onlyLetGo)
    }
}
