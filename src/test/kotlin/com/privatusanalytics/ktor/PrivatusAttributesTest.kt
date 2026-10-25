package com.privatusanalytics.ktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivatusAttributesTest {
    @Test
    fun `an event becomes data attributes`() {
        assertEquals(
            mapOf(
                "data-privatus-event" to "Export CSV",
                "data-privatus-prop-format" to "csv",
                "data-privatus-prop-source" to "toolbar",
            ),
            PrivatusAttributes.event("Export CSV", mapOf("format" to "csv", "source" to "toolbar")),
        )
    }

    @Test
    fun `the event name is trimmed and cut to 120 characters`() {
        assertEquals("Export CSV", PrivatusAttributes.event("  Export CSV  ")["data-privatus-event"])
        assertEquals("a".repeat(120), PrivatusAttributes.event("a".repeat(200))["data-privatus-event"])
    }

    @Test
    fun `an empty event name gives nothing`() {
        assertTrue(PrivatusAttributes.event("   ", mapOf("format" to "csv")).isEmpty())
        assertTrue(PrivatusAttributes.event(null).isEmpty())
    }

    @Test
    fun `prop keys are lowercased, cleaned and cut to 64 characters`() {
        val attrs = PrivatusAttributes.event("Saved", mapOf("Report Type" to "a", "x-y" to "b", "K".repeat(80) to "c"))

        assertEquals("a", attrs["data-privatus-prop-report_type"])
        assertEquals("b", attrs["data-privatus-prop-x_y"])
        assertEquals("c", attrs["data-privatus-prop-${"k".repeat(64)}"])
    }

    @Test
    fun `values become strings and null values are dropped`() {
        val attrs =
            PrivatusAttributes.event(
                "Saved",
                mapOf(
                    "on" to true,
                    "off" to false,
                    "count" to 3,
                    "ratio" to 1.5,
                    "tags" to listOf("a", null, "b"),
                    "gone" to null,
                ),
            )
        val names = listOf("on", "off", "count", "ratio", "tags").map { "data-privatus-prop-$it" }

        assertEquals(listOf("true", "false", "3", "1.5", "a,b"), names.map { attrs[it] })
        assertFalse(attrs.containsKey("data-privatus-prop-gone"))
    }

    @Test
    fun `values are cut to 500 characters and at most 30 props are kept`() {
        val props = (1..40).associate { "p$it" to "v" }

        assertEquals(31, PrivatusAttributes.event("Saved", props).size)
        assertEquals(
            500,
            PrivatusAttributes.event("Saved", mapOf("long" to "x".repeat(900)))["data-privatus-prop-long"]!!.length,
        )
    }

    @Test
    fun `html escapes every value`() {
        val html = PrivatusAttributes.toHtml(PrivatusAttributes.event("Say \"hi\" <now>", mapOf("note" to "a&b")))

        assertEquals("data-privatus-event=\"Say &quot;hi&quot; &lt;now&gt;\" data-privatus-prop-note=\"a&amp;b\"", html)
    }

    @Test
    fun `a valid click name becomes a data attribute`() {
        assertEquals(mapOf("data-privatus-click" to "pricing.cta"), PrivatusAttributes.click("pricing.cta"))
        assertEquals(mapOf("data-privatus-click" to "a-b_c:d.1"), PrivatusAttributes.click("a-b_c:d.1"))
    }

    @Test
    fun `an invalid click name gives nothing`() {
        listOf("", "has space", "x".repeat(101), "<b>", "a\n", null).forEach {
            assertTrue(PrivatusAttributes.click(it).isEmpty(), "expected nothing for $it")
        }
    }

    @Test
    fun `string helpers print attributes for any template engine`() {
        assertEquals(
            "data-privatus-event=\"Export CSV\" data-privatus-prop-format=\"csv\" " +
                "data-privatus-prop-source=\"toolbar\"",
            privatusEventAttrs("Export CSV", "format" to "csv", "source" to "toolbar"),
        )
        assertEquals(
            "data-privatus-event=\"Saved\" data-privatus-prop-report_type=\"weekly\"",
            privatusEventAttrs("Saved", mapOf("Report Type" to "weekly")),
        )
        assertEquals("data-privatus-event=\"&lt;b&gt;\"", privatusEventAttrs("<b>"))
        assertEquals("", privatusEventAttrs(" "))
        assertEquals(mapOf("data-privatus-event" to "Docs opened"), privatusEventData("Docs opened"))
    }

    @Test
    fun `click string helpers`() {
        assertEquals("data-privatus-click=\"pricing.cta\"", privatusClickAttr("pricing.cta"))
        assertEquals("", privatusClickAttr("not valid!"))
        assertTrue(privatusClickData("not valid!").isEmpty())
    }
}
