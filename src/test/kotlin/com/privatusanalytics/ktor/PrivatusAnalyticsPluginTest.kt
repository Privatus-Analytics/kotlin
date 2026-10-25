package com.privatusanalytics.ktor

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.title
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PrivatusAnalyticsPluginTest {
    private val snippet =
        """<script defer src="https://privatusstats.com/js/pa.js" data-site="pa_TEST123456"></script>"""
    private val plain = "<html><head><title>Plain</title></head><body>Hi</body></html>"

    private fun app(
        environmentName: String? = "production",
        configure: PrivatusAnalyticsConfig.() -> Unit = {},
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        environment {
            config =
                if (environmentName == null) {
                    MapApplicationConfig()
                } else {
                    MapApplicationConfig("ktor.deployment.environment" to environmentName)
                }
        }
        application {
            install(PrivatusAnalytics) {
                env = { null }
                siteId = "pa_TEST123456"
                configure()
            }
            routing { pages() }
        }
        test()
    }

    private fun Routing.pages() {
        get("/") {
            call.respondHtml {
                head {
                    title("Home")
                    privatusAnalytics(call)
                }
                body { h1 { +"Home" } }
            }
        }
        get("/plain") { call.respondText(plain, ContentType.Text.Html) }
        get("/stream") {
            call.respondHtml {
                head { title("Stream") }
                body { +"Hi" }
            }
        }
        get("/data") { call.respondText("""{"ok":true}""", ContentType.Application.Json) }
        get("/bare") { call.respondText("<p>No head here</p>", ContentType.Text.Html) }
        get("/missing") { call.respondText(plain, ContentType.Text.Html, HttpStatusCode.NotFound) }
        get("/features") {
            call.respondHtml {
                body {
                    button {
                        privatusEvent("Export CSV", "format" to "csv")
                        +"Export"
                    }
                    form(action = "/signup") {
                        privatusEvent("Newsletter signup", mapOf("list" to "weekly"))
                    }
                    a(href = "/pricing") {
                        privatusClick("pricing.cta")
                        +"Pricing"
                    }
                    a(href = "/bad") {
                        privatusClick("not valid!")
                        +"Bad"
                    }
                }
            }
        }
        get("/tag") { call.respondText(call.privatusTag { modules = listOf("engage", "auto") }) }
    }

    private suspend fun HttpResponse.html() = bodyAsText()

    @Test
    fun `the kotlinx html helper prints the tag in the head`() =
        app {
            val body = client.get("/").html()

            assertContains(body, snippet)
            assertTrue(body.indexOf(snippet) < body.indexOf("</head>"))
        }

    @Test
    fun `the tag prints nothing outside production`() =
        app(environmentName = "test") {
            assertFalse(client.get("/").html().contains("pa.js"))
        }

    @Test
    fun `without an environment name, development mode decides`() =
        testApplication {
            var devMode = false
            environment { config = MapApplicationConfig() }
            application {
                devMode = developmentMode
                install(PrivatusAnalytics) {
                    env = { null }
                    siteId = "pa_TEST123456"
                }
                routing { get("/tag") { call.respondText(call.privatusTag()) } }
            }
            val printed = client.get("/tag").html().contains("pa.js")

            // Ktor test apps run in development mode, so the tag is off.
            assertTrue(devMode)
            assertFalse(printed)
        }

    @Test
    fun `enabled forces the tag on in any environment`() =
        app(environmentName = "development", configure = { enabled = true }) {
            assertContains(client.get("/").html(), snippet)
        }

    @Test
    fun `the tag prints nothing without a site id`() =
        app(configure = { siteId = null }) {
            assertFalse(client.get("/").html().contains("pa.js"))
        }

    @Test
    fun `call privatusTag takes per call options`() =
        app {
            assertContains(client.get("/tag").html(), """data-modules="engage,auto"""")
        }

    @Test
    fun `auto inject does nothing by default`() =
        app {
            assertEquals(plain, client.get("/plain").html())
        }

    @Test
    fun `auto inject adds the tag to the head and fixes Content-Length`() =
        app(configure = { autoInject = true }) {
            val response = client.get("/plain")
            val body = response.html()

            assertContains(body, "$snippet\n</head>")
            assertEquals(body.toByteArray().size.toString(), response.headers[HttpHeaders.ContentLength])
        }

    @Test
    fun `auto inject works on streamed kotlinx html responses`() =
        app(configure = { autoInject = true }) {
            val body = client.get("/stream").html()

            assertContains(body, "$snippet\n</head>")
            assertContains(body, "<title>Stream</title>")
        }

    @Test
    fun `auto inject does not add a second tag when the page has one`() =
        app(configure = { autoInject = true }) {
            assertEquals(1, Regex("/js/pa.js").findAll(client.get("/").html()).count())
        }

    @Test
    fun `auto inject runs before the Compression plugin`() =
        testApplication {
            environment { config = MapApplicationConfig("ktor.deployment.environment" to "production") }
            application {
                install(Compression) { gzip { minimumSize(1) } }
                install(PrivatusAnalytics) {
                    env = { null }
                    siteId = "pa_TEST123456"
                    autoInject = true
                }
                routing { pages() }
            }
            val response = client.get("/plain") { header(HttpHeaders.AcceptEncoding, "gzip") }

            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            val body = GZIPInputStream(ByteArrayInputStream(response.bodyAsBytes())).readBytes().decodeToString()
            assertContains(body, "$snippet\n</head>")
        }

    @Test
    fun `auto inject leaves JSON alone`() =
        app(configure = { autoInject = true }) {
            assertEquals("""{"ok":true}""", client.get("/data").html())
        }

    @Test
    fun `auto inject leaves HTML without a head alone`() =
        app(configure = { autoInject = true }) {
            assertEquals("<p>No head here</p>", client.get("/bare").html())
        }

    @Test
    fun `auto inject leaves other statuses alone`() =
        app(configure = { autoInject = true }) {
            assertEquals(plain, client.get("/missing").html())
        }

    @Test
    fun `auto inject leaves Ajax requests alone`() =
        app(configure = { autoInject = true }) {
            val response = client.get("/plain") { header("X-Requested-With", "XMLHttpRequest") }

            assertEquals(plain, response.html())
        }

    @Test
    fun `auto inject adds nothing outside the enabled environments`() =
        app(environmentName = "test", configure = { autoInject = true }) {
            assertEquals(plain, client.get("/plain").html())
        }

    @Test
    fun `event and click helpers render in a kotlinx html page`() =
        app(environmentName = "test") {
            val body = client.get("/features").html()

            assertContains(
                body,
                """<button data-privatus-event="Export CSV" data-privatus-prop-format="csv">Export</button>""",
            )
            assertContains(body, """data-privatus-event="Newsletter signup" data-privatus-prop-list="weekly"""")
            assertContains(body, """<a href="/pricing" data-privatus-click="pricing.cta">Pricing</a>""")
            assertContains(body, """<a href="/bad">Bad</a>""")
        }

    @Test
    fun `the tag sends configured props from the call`() =
        app(configure = { props { call -> mapOf("plan" to call.request.queryParameters["plan"]) } }) {
            val body = client.get("/?plan=pro").html()

            assertContains(body, """window.privatusPageProps=function(){return {"plan":"pro"}}""")
            assertContains(body, """data-props="privatusPageProps"""")
        }

    @Test
    fun `auto inject calls the props block with the call`() {
        var seen: ApplicationCall? = null
        app(
            configure = {
                autoInject = true
                props { call ->
                    seen = call
                    mapOf(
                        "section" to
                            call.request.local.uri
                                .removePrefix("/"),
                    )
                }
            },
        ) {
            val body = client.get("/plain").html()

            assertTrue(seen != null)
            assertContains(body, """{"section":"plain"}""")
            assertContains(body, """data-props="privatusPageProps"""")
        }
    }

    @Test
    fun `auto inject adds the nonce and the queue stub`() =
        app(
            configure = {
                autoInject = true
                queueStub = true
                props("plan" to "pro")
                nonce { "n0nce" }
            },
        ) {
            val response = client.get("/plain")
            val body = response.html()

            assertEquals(3, Regex("""nonce="n0nce"""").findAll(body).count())
            assertContains(body, "q:[]")
            assertEquals(body.toByteArray().size.toString(), response.headers[HttpHeaders.ContentLength])
        }

    @Test
    fun `the plugin config is stored on the application`() =
        testApplication {
            application {
                install(PrivatusAnalytics) { siteId = "pa_STORED" }
                assertSame(attributes[PrivatusAnalyticsConfigKey], privatusAnalyticsConfig)
                assertEquals("pa_STORED", privatusAnalyticsConfig.siteId)
            }
            startApplication()
        }
}
