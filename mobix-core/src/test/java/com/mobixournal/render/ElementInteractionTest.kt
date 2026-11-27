package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementInteractionTest {

    private fun rect(fillColor: Int? = null) = ParametricShapeElement(
        shape = "rectangle",
        vertices = listOf(
            ShapeVertex(0.0, 0.0),
            ShapeVertex(100.0, 0.0),
            ShapeVertex(100.0, 100.0),
            ShapeVertex(0.0, 100.0),
        ),
        strokeColor = 0xFF000000.toInt(),
        fillColor = fillColor,
    )

    private fun spline() = SplineElement(
        nodes = listOf(SplineNode(0.0, 0.0), SplineNode(50.0, 20.0), SplineNode(100.0, 0.0)),
    )

    @Test
    fun profilesCoverEveryElementFamilyConsistently() {
        val lockedRect = ElementInteraction.profileOf(rect(), aspectLocked = true)
        assertEquals(ChromeMode.BOX_8_HANDLES, lockedRect.chromeMode)
        assertEquals(HitMode.STROKE_OR_INTERIOR_IF_FILLED, lockedRect.hitMode)
        assertTrue(lockedRect.supportsAspectLock)
        assertEquals(PinRole.ANCHOR, lockedRect.pinRole)

        val unlockedRect = ElementInteraction.profileOf(rect(), aspectLocked = false)
        assertEquals(ChromeMode.FREE_VERTICES, unlockedRect.chromeMode)

        val splineProfile = ElementInteraction.profileOf(spline())
        assertEquals(ChromeMode.DIRECT_NODES, splineProfile.chromeMode)
        assertFalse(splineProfile.supportsAspectLock)
        assertTrue(splineProfile.rotatable)

        val circuitProfile = ElementInteraction.profileOf(CircuitElement("resistor", 10.0, 10.0))
        assertEquals(PinRole.TERMINAL, circuitProfile.pinRole)
        assertEquals(HitMode.FILLED_BODY, circuitProfile.hitMode)

        val imageProfile = ElementInteraction.profileOf(ImageElement(0.0, 0.0, 100.0, 100.0, byteArrayOf()))
        assertEquals(ChromeMode.BOX_PROPORTIONAL_ONLY, imageProfile.chromeMode)
        assertFalse(imageProfile.rotatable)

        val wire = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = null,
            points = listOf(StrokePoint(0.0, 0.0, 1.5), StrokePoint(80.0, 0.0, 1.5)),
            uniformWidth = true,
            extraAttrs = FigurePins.figureAttrs(ShapeKind.LINE),
        )
        val wireProfile = ElementInteraction.profileOf(wire)
        assertEquals(ChromeMode.SHAFT_ENDS, wireProfile.chromeMode)
        assertEquals(PinRole.CONNECTOR, wireProfile.pinRole)
        assertFalse(wireProfile.supportsAspectLock)

        assertEquals(HitMode.FILLED_BODY, ElementInteraction.profileOf(TableElement(0.0, 0.0, listOf(40.0), listOf(20.0))).hitMode)
        assertEquals(HitMode.FILLED_BODY, ElementInteraction.profileOf(PlotElement(0.0, 0.0, 100.0, 100.0, emptyList())).hitMode)
        assertEquals(HitMode.FILLED_BODY, ElementInteraction.profileOf(MathBlockElement(0.0, 0.0, 50.0, 20.0, "x")).hitMode)
        assertEquals(HitMode.FILLED_BODY, ElementInteraction.profileOf(TexImageElement(0.0, 0.0, 50.0, 20.0, "x", 0)).hitMode)
        assertEquals(HitMode.FILLED_BODY, ElementInteraction.profileOf(TextElement("Sans", 12.0, 0.0, 0.0, 0, "hi")).hitMode)
        assertEquals(HitMode.FILLED_BODY, ElementInteraction.profileOf(RawElement("custom", emptyMap())).hitMode)
    }

    @Test
    fun hollowRectangleDoesNotGrabCenterForMoveWhileFilledRectangleDoes() {
        val hollow = rect(fillColor = null)
        assertFalse(ElementInteraction.hitsMoveBody(hollow, 50.0, 50.0, 6.0))
        assertTrue(ElementInteraction.hitsMoveBody(hollow, 50.0, 2.0, 6.0))

        val filled = rect(fillColor = 0x6600AAFF)
        assertTrue(ElementInteraction.hitsMoveBody(filled, 50.0, 50.0, 6.0))
    }

    @Test
    fun proximityFilterRevealsOnlyNearbyPins() {
        val near = CircuitSnapping.PinTarget(null, "a", 10.0, 10.0, null)
        val far = CircuitSnapping.PinTarget(null, "b", 300.0, 300.0, null)
        val visible = ElementInteraction.filterTargetsByProximity(
            targets = listOf(near, far),
            cursorXPt = 20.0,
            cursorYPt = 20.0,
            pxPerPt = 1.0f,
            revealRadiusPx = 84f,
        )
        assertEquals(listOf(near), visible)
    }

    @Test
    fun smallObjectsHideCrowdedEdgeHandles() {
        assertFalse(ElementInteraction.showEdgeHandlesForSpan(30f, flat = false))
        assertTrue(ElementInteraction.showEdgeHandlesForSpan(48f, flat = false))
        assertFalse(ElementInteraction.showEdgeHandlesForSpan(120f, flat = true))
    }
}
