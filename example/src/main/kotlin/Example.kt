// Conformance example app for privatus-analytics-ktor.
//
// Built by the conformance suite (https://github.com/Privatus-Analytics/conformance).
// See its CONTRACT.md for what each route must render. The site id and script
// host come from PRIVATUS_SITE_ID and PRIVATUS_SCRIPT_HOST.

import com.privatusanalytics.ktor.PrivatusAnalytics
import com.privatusanalytics.ktor.PrivatusAutoInject
import com.privatusanalytics.ktor.privatusAnalytics
import com.privatusanalytics.ktor.privatusClick
import com.privatusanalytics.ktor.privatusEvent
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.html.respondHtml
import io.ktor.server.netty.Netty
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.title

const val NONCE = "testnonce"
const val NASTY = "</script><script>window.__pwned=1</script>\u2028\u2029&<>\"'"

val pageProps: Map<String, Any?> = mapOf("plan" to "pro", "role" to "admin", "beta" to true, "seats" to 3)

val autoPage =
    """
    <!doctype html>
    <html lang="en">
    <head>
    <meta charset="utf-8">
    <title>Auto inject</title>
    </head>
    <body><h1>Auto inject</h1><p>No tag in the template. The plugin adds it.</p></body>
    </html>
    """.trimIndent()

// Sends the policy. Ktor has no built-in nonce, so the plugin is told where it is (see below).
// A real app makes a new random nonce for each request.
val ContentSecurityPolicy =
    createApplicationPlugin("ContentSecurityPolicy") {
        val host = (System.getenv("PRIVATUS_SCRIPT_HOST") ?: "https://privatusstats.com").removeSuffix("/")
        onCall { call -> call.response.header("Content-Security-Policy", "script-src 'nonce-$NONCE' $host") }
    }

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentSecurityPolicy)
    install(PrivatusAnalytics) {
        // Forced on, so the tag prints whatever the environment.
        enabled = true
        nonce { NONCE }
    }

    routing {
        get("/") { call.page("Privatus conformance", pageProps) { h1 { +"Home" } } }
        get("/event") {
            call.page("Events", pageProps) {
                h1 { +"Events" }
                button(type = ButtonType.button) {
                    privatusEvent("Report exported", "format" to "csv", "source" to "toolbar")
                    +"Export report"
                }
                p {
                    a(href = "#pricing") {
                        privatusClick("pricing.cta")
                        +"Pricing"
                    }
                }
                form(action = "/event", method = FormMethod.get) {
                    privatusEvent("Signup form")
                    button(type = ButtonType.submit) { +"Sign up" }
                }
            }
        }
        get("/nasty") {
            call.page("Nasty", pageProps + ("nasty" to NASTY)) {
                h1 { +"Nasty values" }
                button(type = ButtonType.button) {
                    privatusEvent("Nasty value", "nasty" to NASTY)
                    +"Nasty"
                }
            }
        }
        // Off with the queue stub on: only the stub prints, so privatus.track still works.
        get("/off") { call.offPage(stub = true) }
        // Off with the queue stub off: nothing prints.
        get("/off-plain") { call.offPage(stub = false) }
        // Auto inject on this route only, so the other routes test the helper alone.
        route("/auto") {
            install(PrivatusAutoInject)
            get { call.respondText(autoPage, ContentType.Text.Html) }
        }
    }
}

suspend fun ApplicationCall.page(
    title: String,
    props: Map<String, Any?>,
    content: kotlinx.html.BODY.() -> Unit,
) {
    val call = this
    respondHtml {
        head {
            meta(charset = "utf-8")
            title(title)
            privatusAnalytics(call) {
                this.props = props
                queueStub = true
            }
        }
        body(block = content)
    }
}

suspend fun ApplicationCall.offPage(stub: Boolean) {
    val call = this
    respondHtml(block = fun HTML.() {
        head {
            meta(charset = "utf-8")
            title("Off")
            meta(name = "privatus-off-start")
            privatusAnalytics(call) {
                enabled = false
                props = pageProps
                queueStub = stub
            }
            meta(name = "privatus-off-end")
        }
        body { h1 { +"Off" } }
    })
}
