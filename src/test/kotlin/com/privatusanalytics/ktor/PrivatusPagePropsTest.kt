package com.privatusanalytics.ktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PrivatusPagePropsTest {
    @Test
    fun `props become a JSON object`() {
        assertEquals(
            """{"plan":"pro","role":"admin"}""",
            PrivatusPageProps.json(
                mapOf(
                    "plan" to "pro",
                    "role" to "admin",
                ),
            ),
        )
    }

    @Test
    fun `empty props give null`() {
        assertNull(PrivatusPageProps.json(emptyMap<String, Any?>()))
        assertNull(PrivatusPageProps.json(null))
        assertNull(PrivatusPageProps.json(mapOf("gone" to null)))
    }

    @Test
    fun `numbers, booleans and lists keep their JSON types`() {
        assertEquals(
            """{"seats":5,"ratio":1.5,"beta":true,"old":false,"flags":["a","b",3]}""",
            PrivatusPageProps.json(
                mapOf(
                    "seats" to 5,
                    "ratio" to 1.5,
                    "beta" to true,
                    "old" to false,
                    "flags" to listOf("a", null, "b", 3),
                ),
            ),
        )
    }

    @Test
    fun `numbers that are not finite are dropped`() {
        assertEquals(
            """{"a":1}""",
            PrivatusPageProps.json(
                mapOf(
                    "a" to 1L,
                    "b" to Double.NaN,
                    "c" to Float.POSITIVE_INFINITY,
                ),
            ),
        )
    }

    @Test
    fun `keys are cleaned like event props`() {
        assertEquals("""{"app_version":"2.1"}""", PrivatusPageProps.json(mapOf("App Version" to "2.1")))
    }

    @Test
    fun `limits props, list items and value length`() {
        val many = (1..40).associate { "p$it" to it }
        val manyJson = PrivatusPageProps.json(many)!!
        val listJson = PrivatusPageProps.json(mapOf("list" to (1..50).map { it.toString() }))!!
        val longJson = PrivatusPageProps.json(mapOf("long" to "x".repeat(900)))!!

        assertEquals(30, Regex("\"p\\d+\":").findAll(manyJson).count())
        assertEquals(30, listJson.count { it == ',' } + 1)
        assertEquals("""{"long":"${"x".repeat(500)}"}""", longJson)
    }

    @Test
    fun `the JSON is safe inside a script tag`() {
        val json = PrivatusPageProps.json(mapOf("note" to "</script><b>&\u2028\u2029"))!!

        assertEquals("""{"note":"\u003c/script\u003e\u003cb\u003e\u0026\u2028\u2029"}""", json)
        assertFalse(Regex("[<>&\u2028\u2029]").containsMatchIn(json))
    }

    @Test
    fun `quotes, backslashes and control characters are escaped`() {
        assertEquals("""{"a":"x\"y\\z\n\u0001"}""", PrivatusPageProps.json(mapOf("a" to "x\"y\\z\n\u0001")))
    }
}
