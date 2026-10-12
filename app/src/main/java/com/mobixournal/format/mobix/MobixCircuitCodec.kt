package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.ElementId

/**
 * The `circuit` element codec (`docs/mobix-elements.md`): the electronics symbol `.xopp` can only store
 * as a set of strokes.
 *
 * A component is written as **placement plus connectivity**, not as drawn geometry: the symbol's key,
 * where it sits, how far it is turned, the label the user typed, and its magnetic pins. The symbol's own
 * outline is the symbol library's business, so a component has **no `bounds`** — the file would only be
 * storing a copy of a drawing this build does not own, and a stale copy of it would be worse than none.
 *
 * Pin coordinates are relative to the position and **before** the rotation, which is what lets a
 * component turn without its wires coming adrift: the file states the anchor once, in the component's
 * own frame, and the renderer applies the rotation. [CircuitPin.connectedTo] names a *net* rather than
 * another element, because a wire is connectivity and not a stroke.
 *
 * [decode] models the component or leaves it verbatim: an unreadable `position`, a pin that is not a
 * pin, an unknown `symbol` (which, like a shape's kind, is an open key set rather than a refusal — the
 * placement and the pins are the data, the drawing is the library's).
 */
internal object MobixCircuitCodec {

    /** The `type` discriminator this codec owns. */
    const val TYPE: String = "circuit"

    private const val PINS = "pins"
    private const val POSITION = "position"

    /** [circuit] as JSON. */
    fun encode(circuit: CircuitElement): JsonNode.Obj = MobixJson.obj { out ->
        out["type"] = JsonNode.of(TYPE)
        circuit.id?.let { out["id"] = JsonNode.of(it) }
        out["symbol"] = JsonNode.of(circuit.symbol)
        circuit.value?.let { out["value"] = JsonNode.of(it) }
        out[POSITION] = MobixJson.obj { position ->
            position["x"] = JsonNode.of(circuit.positionX)
            position["y"] = JsonNode.of(circuit.positionY)
        }
        out["rotation"] = JsonNode.of(circuit.rotation)
        // A component with no pins is a plain symbol, so an empty list is written as nothing at all:
        // "this symbol has no anchors" and "this symbol's anchors are an empty set" are one fact.
        if (circuit.pins.isNotEmpty()) out[PINS] = JsonNode.arr(circuit.pins.map(::encodePin))
        if (circuit.extraAttrs.isNotEmpty()) out[MobixJson.ATTRS] = MobixJson.attrsNode(circuit.extraAttrs)
    }

    private fun encodePin(pin: CircuitPin): JsonNode = MobixJson.obj { out ->
        out["id"] = JsonNode.of(pin.id)
        out["relX"] = JsonNode.of(pin.relX)
        out["relY"] = JsonNode.of(pin.relY)
        pin.connectedTo?.let { out["connectedTo"] = JsonNode.of(it) }
    }

    /** The component [node] describes, or null when this build cannot read it whole. */
    fun decode(node: JsonNode.Obj, mint: (String) -> String = ElementId::mint): CircuitElement? {
        val position = node.getObj(POSITION) ?: return null
        val pins = decodePins(node) ?: return null
        return CircuitElement(
            symbol = node.getStrOrNull("symbol") ?: return null,
            positionX = position.getDoubleOrNull("x") ?: return null,
            positionY = position.getDoubleOrNull("y") ?: return null,
            rotation = node.getDouble("rotation", 0.0),
            value = node.getStrOrNull("value"),
            pins = pins,
            extraAttrs = MobixJson.readAttrs(node),
            id = node.getStrOrNull("id") ?: mint(ElementId.CIRCUIT),
        )
    }

    /** The pins of [node]; an empty list when the symbol has no anchors, null when one is unreadable. */
    private fun decodePins(node: JsonNode.Obj): List<CircuitPin>? {
        val pins = node.getArr(PINS)?.list ?: return emptyList()
        val out = ArrayList<CircuitPin>(pins.size)
        for (pin in pins) {
            val obj = pin as? JsonNode.Obj ?: return null
            out += CircuitPin(
                id = obj.getStrOrNull("id") ?: return null,
                relX = obj.getDouble("relX", 0.0),
                relY = obj.getDouble("relY", 0.0),
                connectedTo = obj.getStrOrNull("connectedTo"),
            )
        }
        return out
    }
}
