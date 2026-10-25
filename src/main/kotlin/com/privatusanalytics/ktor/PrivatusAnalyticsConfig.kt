package com.privatusanalytics.ktor

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/**
 * Settings for the tracking script. Set them in `install(PrivatusAnalytics) { ... }`.
 * Every setting is optional except the site id.
 */
public class PrivatusAnalyticsConfig {
    /** The site id (starts with pa_). Falls back to the PRIVATUS_SITE_ID environment variable. */
    public var siteId: String? = null
        get() = field.orBlank().ifEmpty { env("PRIVATUS_SITE_ID").orBlank() }.ifEmpty { null }

    /** Origin that serves /js/pa.js. Falls back to PRIVATUS_SCRIPT_HOST, then https://privatusstats.com. */
    public var scriptHost: String? = null
        get() =
            field
                .orBlank()
                .ifEmpty { env("PRIVATUS_SCRIPT_HOST").orBlank() }
                .ifEmpty { DEFAULT_HOST }
                .removeSuffix("/")

    /** Tracker modules to load, for example listOf("engage", "auto"). null keeps the tracker default. */
    public var modules: List<String>? = null

    /** Path globs the tracker never counts, for example every page under /admin. */
    public var excludedPaths: List<String> = emptyList()

    /** Hostnames to count. null counts every host. */
    public var domains: List<String>? = null

    /** One of auto, history, hash, off. null keeps the tracker default. */
    public var spa: String? = null

    /** true turns off the automatic pageview. See the manual mode docs. */
    public var manual: Boolean = false

    /** true or false forces the tag on or off. null decides from the environment. */
    public var enabled: Boolean? = null

    /** Values of `ktor.deployment.environment` that get the tag. Used when [enabled] is null. */
    public var enabledEnvironments: List<String> = listOf("production")

    /** true adds the tag before `</head>` in HTML responses. */
    public var autoInject: Boolean = false

    /** Extra data- attributes, for example mapOf("data-debug" to "true"). A true value prints the bare name. */
    public var extraAttributes: Map<String, Any?> = emptyMap()

    /** true prints the tracker's queue stub first, so privatus.track works before pa.js loads. */
    public var queueStub: Boolean = false

    private var staticProps: Map<String, Any?> = emptyMap()
    private var propsBlock: ((ApplicationCall) -> Map<String, Any?>?)? = null
    private var nonceBlock: ((ApplicationCall) -> String?)? = null

    // Swapped in tests, because the JVM cannot change its own environment variables.
    internal var env: (String) -> String? = System::getenv

    /**
     * Page props sent with every pageview and event, worked out on each request:
     *
     *     props { call -> mapOf("plan" to call.principal<UserSession>()?.plan) }
     *
     * Never put names, emails, user ids or anything else that points to one person here.
     */
    public fun props(block: (ApplicationCall) -> Map<String, Any?>?) {
        propsBlock = block
        staticProps = emptyMap()
    }

    /** The same page props for every page. */
    public fun props(values: Map<String, Any?>) {
        staticProps = values
        propsBlock = null
    }

    /** The same page props for every page: props("plan" to "pro"). */
    public fun props(vararg values: Pair<String, Any?>) {
        props(values.toMap())
    }

    /**
     * The Content Security Policy nonce for a request. Ktor has no built-in nonce,
     * so tell the plugin where yours lives:
     *
     *     nonce { call -> call.attributes[CspNonceKey] }
     */
    public fun nonce(block: (ApplicationCall) -> String?) {
        nonceBlock = block
    }

    /** The configured props for one request. Calls the props block when there is one and a call. */
    public fun pageProps(call: ApplicationCall?): Map<String, Any?> {
        val block = propsBlock ?: return staticProps
        return call?.let(block) ?: emptyMap()
    }

    /** The nonce for one request, or null. */
    public fun nonceFor(call: ApplicationCall?): String? = call?.let { nonceBlock?.invoke(it) }

    /**
     * Whether the tag prints. [enabled] wins when it is set. Otherwise the tag is on when
     * [environment] is in [enabledEnvironments], or, with no environment set, when
     * [developmentMode] is false.
     */
    public fun isEnabled(
        environment: String?,
        developmentMode: Boolean,
    ): Boolean {
        enabled?.let { return it }
        if (!environment.isNullOrBlank()) return environment.trim() in enabledEnvironments
        return !developmentMode
    }

    /** Whether the tag prints for this application, from `ktor.deployment.environment` and `developmentMode`. */
    public fun isEnabled(application: Application?): Boolean {
        enabled?.let { return it }
        application ?: return false
        val environment =
            application.environment.config
                .propertyOrNull("ktor.deployment.environment")
                ?.getString()
        return isEnabled(environment, application.developmentMode)
    }

    private fun String?.orBlank(): String = this?.trim().orEmpty()

    public companion object {
        public const val DEFAULT_HOST: String = "https://privatusstats.com"
        public val MODULES: List<String> = listOf("engage", "auto", "vitals", "clicks", "ask")
        public val SPA_MODES: List<String> = listOf("auto", "history", "hash", "off")
    }
}
