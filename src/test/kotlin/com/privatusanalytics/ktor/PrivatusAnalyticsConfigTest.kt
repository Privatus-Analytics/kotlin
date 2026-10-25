package com.privatusanalytics.ktor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivatusAnalyticsConfigTest {
    private fun config(vars: Map<String, String> = emptyMap()) = PrivatusAnalyticsConfig().apply { env = { vars[it] } }

    @Test
    fun `site id is null by default`() {
        assertNull(config().siteId)
    }

    @Test
    fun `site id falls back to the environment variable`() {
        assertEquals("pa_FROMENV123", config(mapOf("PRIVATUS_SITE_ID" to "pa_FROMENV123")).siteId)
    }

    @Test
    fun `an explicit site id wins over the environment variable`() {
        val config = config(mapOf("PRIVATUS_SITE_ID" to "pa_FROMENV123")).apply { siteId = "pa_EXPLICIT01" }

        assertEquals("pa_EXPLICIT01", config.siteId)
    }

    @Test
    fun `script host defaults, reads the environment and drops a trailing slash`() {
        assertEquals("https://privatusstats.com", config().scriptHost)
        assertEquals(
            "https://env.example.com",
            config(mapOf("PRIVATUS_SCRIPT_HOST" to "https://env.example.com/")).scriptHost,
        )
        assertEquals(
            "https://stats.example.com",
            config().apply { scriptHost = "https://stats.example.com/" }.scriptHost,
        )
    }

    @Test
    fun `only production is enabled by default`() {
        val config = config()

        assertTrue(config.isEnabled("production", developmentMode = false))
        assertFalse(config.isEnabled("development", developmentMode = false))
        assertFalse(config.isEnabled("test", developmentMode = false))
    }

    @Test
    fun `without an environment name, development mode decides`() {
        val config = config()

        assertTrue(config.isEnabled(null, developmentMode = false))
        assertFalse(config.isEnabled(null, developmentMode = true))
    }

    @Test
    fun `enabled environments can be changed`() {
        val config = config().apply { enabledEnvironments = listOf("prod", "staging") }

        assertTrue(config.isEnabled("staging", developmentMode = false))
        assertFalse(config.isEnabled("production", developmentMode = false))
    }

    @Test
    fun `enabled forces the tag on or off`() {
        val config = config().apply { enabled = true }
        assertTrue(config.isEnabled("development", developmentMode = true))

        config.enabled = false
        assertFalse(config.isEnabled("production", developmentMode = false))
    }

    @Test
    fun `props, queue stub and auto inject are off by default`() {
        val config = config()

        assertTrue(config.pageProps(null).isEmpty())
        assertFalse(config.queueStub)
        assertFalse(config.autoInject)
    }

    @Test
    fun `a props block needs a call`() {
        val config = config().apply { props { mapOf("plan" to "pro") } }

        assertTrue(config.pageProps(null).isEmpty())
    }
}
