package com.privatusanalytics.ktor

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivatusTagTest {
    private val config =
        PrivatusAnalyticsConfig().apply {
            env = { null }
            siteId = "pa_TEST123456"
            enabled = true
        }

    private fun render(options: TagOptions.() -> Unit = {}) = PrivatusTag.render(config, TagOptions().apply(options))

    @Test
    fun `prints the basic snippet`() {
        assertEquals(
            """<script defer src="https://privatusstats.com/js/pa.js" data-site="pa_TEST123456"></script>""",
            render(),
        )
    }

    @Test
    fun `prints nothing without a site id`() {
        config.siteId = null

        assertEquals("", render())
    }

    @Test
    fun `prints nothing without a call unless enabled is forced`() {
        config.enabled = null

        assertEquals("", render())
    }

    @Test
    fun `adds modules and exclusions`() {
        config.modules = listOf("engage", "auto")
        config.excludedPaths = listOf("/admin/*", "/preview/*")
        val tag = render()

        assertContains(tag, """data-modules="engage,auto"""")
        assertContains(tag, """data-exclude="/admin/*,/preview/*"""")
    }

    @Test
    fun `an empty modules list loads none`() {
        config.modules = emptyList()

        assertContains(render(), """data-modules=""""")
    }

    @Test
    fun `adds domains, spa mode and manual mode`() {
        config.domains = listOf("example.com, www.example.com")
        config.spa = "hash"
        config.manual = true
        val tag = render()

        assertContains(tag, """data-domains="example.com,www.example.com"""")
        assertContains(tag, """data-spa="hash"""")
        assertContains(tag, """data-manual="true"""")
    }

    @Test
    fun `ignores an unknown spa mode and the auto default`() {
        config.spa = "bogus"
        assertFalse(render().contains("data-spa"))
        config.spa = "auto"
        assertFalse(render().contains("data-spa"))
    }

    @Test
    fun `escapes attribute values`() {
        config.excludedPaths = listOf("/a\"b")

        assertContains(render(), """data-exclude="/a&quot;b"""")
    }

    @Test
    fun `per call options override the configuration`() {
        config.modules = listOf("engage")

        assertContains(render { modules = listOf("vitals") }, """data-modules="vitals"""")
        assertContains(render { siteId = "pa_OTHER" }, """data-site="pa_OTHER"""")
        assertContains(
            render { scriptHost = "https://stats.example.com/" },
            """src="https://stats.example.com/js/pa.js"""",
        )
        assertEquals("", render { enabled = false })
    }

    @Test
    fun `adds extra attributes and a nonce`() {
        config.extraAttributes =
            mapOf("data-mask" to "/orders/*->/orders/:id", "data-debug" to true, "data-gone" to null)
        val tag = render { nonce = "abc123" }

        assertContains(tag, """data-mask="/orders/*-&gt;/orders/:id"""")
        assertContains(tag, " data-debug ")
        assertContains(tag, """nonce="abc123"""")
        assertFalse(tag.contains("data-gone"))
    }

    @Test
    fun `page props print an inline script before the tag and data-props on it`() {
        val html = render { props = mapOf("plan" to "pro", "role" to "admin") }

        val propsScript =
            """<script>window.privatusPageProps=function(){return {"plan":"pro","role":"admin"}}</script>"""
        val tracker =
            """<script defer src="https://privatusstats.com/js/pa.js" data-site="pa_TEST123456" """ +
                """data-props="privatusPageProps"></script>"""
        assertEquals("$propsScript\n$tracker", html)
    }

    @Test
    fun `the props script gets the nonce`() {
        assertTrue(
            render {
                props = mapOf("plan" to "pro")
                nonce = "abc"
            }.startsWith("""<script nonce="abc">window.privatusPageProps="""),
        )
    }

    @Test
    fun `empty props print no script and no data-props`() {
        assertFalse(render { props = mapOf("plan" to null) }.contains("privatusPageProps"))
    }

    @Test
    fun `configured props can be a map`() {
        config.props("plan" to "pro")

        assertContains(render(), """{"plan":"pro"}""")
    }

    @Test
    fun `per call props are merged over configured props and null removes one`() {
        config.props(mapOf("plan" to "pro", "role" to "admin"))

        assertContains(render { props = mapOf("role" to null, "tier" to "gold") }, """{"plan":"pro","tier":"gold"}""")
    }

    @Test
    fun `the props block is not called when the tag is off`() {
        config.enabled = false
        config.props { error("should not run") }

        assertEquals("", render())
    }

    @Test
    fun `off with the queue stub prints only the stub with the nonce`() {
        config.enabled = false
        config.props { error("should not run") }

        assertEquals(
            """<script nonce="abc">${PrivatusTag.QUEUE_STUB}</script>""",
            render {
                queueStub = true
                nonce = "abc"
                props = mapOf("plan" to "pro")
            },
        )
    }

    @Test
    fun `off without the queue stub prints nothing`() {
        config.enabled = false

        assertEquals(
            "",
            render {
                queueStub = false
                nonce = "abc"
                props = mapOf("plan" to "pro")
            },
        )
    }

    @Test
    fun `no site id with the queue stub prints only the stub`() {
        config.siteId = null
        config.queueStub = true

        assertEquals("""<script nonce="abc">${PrivatusTag.QUEUE_STUB}</script>""", render { nonce = "abc" })
    }

    @Test
    fun `queue stub prints the official stub first`() {
        config.queueStub = true
        val parts =
            render {
                nonce = "abc"
                props = mapOf("plan" to "pro")
            }.split("\n")

        assertEquals("""<script nonce="abc">${PrivatusTag.QUEUE_STUB}</script>""", parts[0])
        assertContains(parts[1], "privatusPageProps")
        assertContains(parts[2], "/js/pa.js")
    }

    @Test
    fun `the queue stub is the official one`() {
        assertEquals(
            "window.privatus=window.privatus||{q:[]};['track','pageview','props','optOut','optIn','ready']" +
                ".forEach(function(m){window.privatus[m]=window.privatus[m]||function(){window.privatus.q.push(" +
                "[m,Array.prototype.slice.call(arguments)])}})",
            PrivatusTag.QUEUE_STUB,
        )
    }

    @Test
    fun `queue stub can be switched on per call`() {
        assertContains(render { queueStub = true }, "q:[]")
        assertFalse(render().contains("q:[]"))
    }
}
