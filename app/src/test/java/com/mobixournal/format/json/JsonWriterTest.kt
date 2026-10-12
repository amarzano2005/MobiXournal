package com.mobixournal.format.json

import com.mobixournal.format.json.JsonNode.Arr
import com.mobixournal.format.json.JsonNode.Bool
import com.mobixournal.format.json.JsonNode.Num
import com.mobixournal.format.json.JsonNode.Obj
import com.mobixournal.format.json.JsonNode.Str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON layer's write half. It is shared by the settings backup's readers and the `.mobix` codecs,
 * so its two contracts are pinned here rather than at each call site: **whatever it writes, the parser
 * reads back as the same tree** (whitespace and number spelling aside), and an **integral number is
 * written as an integer**, because the `.mobix` schema mixes counts and millisecond stamps with
 * coordinates and `"samples": 300.0` would be a lie about the field's type.
 */
class JsonWriterTest {

    private fun roundTrip(node: JsonNode): JsonNode = JsonParser.parse(JsonWriter.write(node))

    @Test
    fun aWrittenTreeParsesBackAsTheSameTree() {
        val tree = Obj(
            linkedMapOf(
                "a" to Str("plain"),
                "b" to Num(1.0),
                "c" to Num(-2.5),
                "d" to Bool(true),
                "e" to JsonNode.Null,
                "f" to Arr(listOf(Str("x"), Num(3.0), Obj(linkedMapOf("g" to Bool(false))))),
                "h" to Obj(emptyMap()),
                "i" to Arr(emptyList()),
            ),
        )
        assertEquals(tree, roundTrip(tree))
        assertTrue("compact output has no whitespace", " " !in JsonWriter.write(tree))
    }

    @Test
    fun keyOrderIsTheTreesOwn() {
        val out = JsonWriter.write(Obj(linkedMapOf("z" to Num(1.0), "a" to Num(2.0), "m" to Num(3.0))))
        assertEquals("""{"z":1,"a":2,"m":3}""", out)
    }

    @Test
    fun anIntegralNumberIsWrittenWithoutAFraction() {
        assertEquals("""{"samples":300,"created":1775952000000}""", JsonWriter.write(
            Obj(linkedMapOf("samples" to Num(300.0), "created" to Num(1775952000000.0))),
        ))
        assertEquals("1.5", JsonWriter.number(1.5))
        assertEquals("0.62", JsonWriter.number(0.62))
        // A small magnitude may come out in exponent notation. JSON allows it and the contract is that
        // it re-parses to the same value — what must never happen is a *fraction on an integer*.
        assertEquals(-0.0001, (JsonParser.parse(JsonWriter.number(-0.0001)) as Num).value, 0.0)
        assertEquals(1.0E-320, (JsonParser.parse(JsonWriter.number(1.0E-320)) as Num).value, 0.0)
    }

    @Test
    fun aValueJsonCannotExpressIsWrittenAsNull() {
        assertEquals("null", JsonWriter.number(Double.NaN))
        assertEquals("null", JsonWriter.number(Double.POSITIVE_INFINITY))
        assertEquals("null", JsonWriter.write(Num(Double.NaN)))
    }

    @Test
    fun theEscapesJsonRequiresSurviveAParse() {
        val tricky = "quote \" backslash \\ newline \n tab \t cr \r backspace \b formfeed \u000c" +
            " control \u0001 unicode → ½ emoji ✍"
        assertEquals(tricky, (roundTrip(Str(tricky)) as Str).value)
        assertEquals("\"", (roundTrip(Str("\"")) as Str).value)
        assertEquals("a\\b", (roundTrip(Str("a\\b")) as Str).value)
        assertEquals("/", (roundTrip(Str("/")) as Str).value)
        assertTrue("no raw control character survives into the output", JsonWriter.write(Str("\u0001")).none { it < ' ' })
    }

    @Test
    fun aStringLiteralIsQuotableOnItsOwn() {
        assertEquals("\"a\\\"b\"", jsonString("a\"b"))
        assertEquals("\"canali\u00e0\"", jsonString("canali\u00e0"))
    }
}
