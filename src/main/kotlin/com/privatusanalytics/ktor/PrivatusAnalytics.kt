package com.privatusanalytics.ktor

import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase

/** Where the plugin keeps its settings, so call.privatusTag() can find them. */
public val PrivatusAnalyticsConfigKey: AttributeKey<PrivatusAnalyticsConfig> =
    AttributeKey("PrivatusAnalyticsConfig")

private val injectPhase = PipelinePhase("PrivatusAnalyticsInject")

/**
 * Adds the cookieless Privatus Analytics tracking script to a Ktor app.
 *
 *     install(PrivatusAnalytics) {
 *         siteId = "pa_YOURSITEID"
 *         props { call -> mapOf("plan" to call.principal<UserSession>()?.plan) }
 *     }
 *
 * The plugin only prints the browser script. It never sends events from the server.
 */
public val PrivatusAnalytics: ApplicationPlugin<PrivatusAnalyticsConfig> =
    createApplicationPlugin("PrivatusAnalytics", ::PrivatusAnalyticsConfig) {
        val config = pluginConfig
        application.attributes.put(PrivatusAnalyticsConfigKey, config)

        // After Render the body is real content with a content type, and it is not compressed yet.
        application.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.Render, injectPhase)
        application.sendPipeline.intercept(injectPhase) { body ->
            if (body is OutgoingContent) {
                AutoInject.transform(context, config, body)?.let { proceedWith(it) }
            }
        }
    }

internal val AutoInjectRouteKey: AttributeKey<Unit> = AttributeKey("PrivatusAutoInjectRoute")

/**
 * Turns auto inject on for some routes only, and leaves `autoInject` off for the rest.
 * Install [PrivatusAnalytics] in the application first; this plugin uses its settings.
 *
 *     routing {
 *         route("/blog") {
 *             install(PrivatusAutoInject)
 *             get { call.respondText(page, ContentType.Text.Html) }
 *         }
 *     }
 */
public val PrivatusAutoInject: RouteScopedPlugin<Unit> =
    createRouteScopedPlugin("PrivatusAutoInject") {
        onCall { call -> call.attributes.put(AutoInjectRouteKey, Unit) }
    }

/** The plugin settings for this application, or the defaults when the plugin is not installed. */
public val Application.privatusAnalyticsConfig: PrivatusAnalyticsConfig
    get() = attributes.getOrNull(PrivatusAnalyticsConfigKey) ?: defaultConfig

private val defaultConfig = PrivatusAnalyticsConfig()

/**
 * The Privatus Analytics tag for this request, as an HTML string for the head.
 * When the tag is off (no site id, or outside the enabled environments) it prints only the
 * queue stub when that is on, and otherwise nothing.
 *
 *     call.privatusTag()
 *     call.privatusTag { modules = listOf("engage", "auto"); props = mapOf("section" to "billing") }
 */
public fun ApplicationCall.privatusTag(options: TagOptions.() -> Unit = {}): String =
    PrivatusTag.render(application.privatusAnalyticsConfig, TagOptions().apply(options), this)
