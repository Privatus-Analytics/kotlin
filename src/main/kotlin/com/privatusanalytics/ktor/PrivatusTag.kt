package com.privatusanalytics.ktor

import io.ktor.server.application.ApplicationCall

/**
 * Builds the script tag.
 *
 * Before the tracker tag it can also print the queue stub ([TagOptions.queueStub]) and the
 * page props script ([TagOptions.props]), each with the CSP nonce when there is one.
 *
 * When the tag is off (disabled, or no site id) and the queue stub is on, only the queue stub
 * prints, so `privatus.track()` calls in your own JavaScript never fail. With the queue stub
 * off it prints an empty string. The props block is not called when the tag is off.
 */
public object PrivatusTag {
    public const val PROPS_FUNCTION: String = "privatusPageProps"
    public const val MARKER: String = "/js/pa.js"
    public const val QUEUE_STUB: String =
        "window.privatus=window.privatus||{q:[]};['track','pageview','props','optOut','optIn','ready']" +
            ".forEach(function(m){window.privatus[m]=window.privatus[m]||function(){window.privatus.q.push(" +
            "[m,Array.prototype.slice.call(arguments)])}})"

    /**
     * The HTML for the head. [call] is passed to the props and nonce blocks and decides the
     * environment. Without a call the tag prints only when `enabled` is true.
     */
    public fun render(
        config: PrivatusAnalyticsConfig,
        options: TagOptions = TagOptions(),
        call: ApplicationCall? = null,
    ): String {
        val queueStub = option(options, "queueStub", options.queueStub, config.queueStub) == true
        if (!shouldRender(config, options, call)) {
            return if (queueStub) inlineScript(QUEUE_STUB, options.nonce ?: config.nonceFor(call)) else ""
        }

        val nonce = options.nonce ?: config.nonceFor(call)
        val propsJson = PrivatusPageProps.json(pageProps(config, options, call))
        val html = mutableListOf<String>()
        if (queueStub) html += inlineScript(QUEUE_STUB, nonce)
        if (propsJson != null) html += inlineScript("window.$PROPS_FUNCTION=function(){return $propsJson}", nonce)
        html += scriptTag(attributes(config, options, nonce, propsJson != null))
        return html.joinToString("\n")
    }

    /** Whether a tag prints at all: there is a site id and the tag is enabled. */
    public fun shouldRender(
        config: PrivatusAnalyticsConfig,
        options: TagOptions = TagOptions(),
        call: ApplicationCall? = null,
    ): Boolean {
        if (siteId(config, options) == null) return false
        if (options.has("enabled") && options.enabled != null) return options.enabled!!
        return config.isEnabled(call?.application)
    }

    /** The configured props for this request with the per call props merged on top. */
    public fun pageProps(
        config: PrivatusAnalyticsConfig,
        options: TagOptions = TagOptions(),
        call: ApplicationCall? = null,
    ): Map<String, Any?> {
        val merged = LinkedHashMap<String, Any?>()
        config.pageProps(call).forEach { (key, value) -> merged[PrivatusAttributes.normalizeKey(key)] = value }
        options.props?.forEach { (key, value) -> merged[PrivatusAttributes.normalizeKey(key)] = value }
        return merged
    }

    private fun siteId(
        config: PrivatusAnalyticsConfig,
        options: TagOptions,
    ): String? =
        if (options.has("siteId")) {
            options.siteId?.trim()?.ifEmpty { null }
        } else {
            config.siteId
        }

    private fun attributes(
        config: PrivatusAnalyticsConfig,
        options: TagOptions,
        nonce: String?,
        withProps: Boolean,
    ): Map<String, Any> {
        val host =
            if (options.has("scriptHost") && !options.scriptHost.isNullOrBlank()) {
                options.scriptHost!!.trim().removeSuffix("/")
            } else {
                config.scriptHost!!
            }
        val attrs =
            linkedMapOf<String, Any>(
                "defer" to true,
                "src" to "$host$MARKER",
                "data-site" to siteId(config, options)!!,
            )

        // null keeps the tracker default. An empty modules list loads none.
        list(option(options, "modules", options.modules, config.modules))?.let {
            attrs["data-modules"] =
                it.joinToString(",")
        }
        list(option(options, "excludedPaths", options.excludedPaths, config.excludedPaths))
            ?.takeIf { it.isNotEmpty() }
            ?.let { attrs["data-exclude"] = it.joinToString(",") }
        list(option(options, "domains", options.domains, config.domains))
            ?.takeIf { it.isNotEmpty() }
            ?.let { attrs["data-domains"] = it.joinToString(",") }

        val spa = option(options, "spa", options.spa, config.spa)?.trim()
        if (spa != null && spa in PrivatusAnalyticsConfig.SPA_MODES && spa != "auto") attrs["data-spa"] = spa
        if (option(options, "manual", options.manual, config.manual) == true) attrs["data-manual"] = "true"

        option(options, "extraAttributes", options.extraAttributes, config.extraAttributes)?.forEach { (name, value) ->
            if (value != null) attrs[name] = value
        }
        if (nonce != null) attrs["nonce"] = nonce
        if (withProps) attrs["data-props"] = PROPS_FUNCTION
        return attrs
    }

    private fun scriptTag(attrs: Map<String, Any>): String {
        val parts =
            attrs.map { (name, value) ->
                if (value ==
                    true
                ) {
                    name
                } else {
                    "$name=\"${escapeHtml(value.toString())}\""
                }
            }
        return "<script ${parts.joinToString(" ")}></script>"
    }

    private fun inlineScript(
        code: String,
        nonce: String?,
    ): String {
        val opening = if (nonce != null) "<script nonce=\"${escapeHtml(nonce)}\">" else "<script>"
        return "$opening$code</script>"
    }

    private fun <T> option(
        options: TagOptions,
        name: String,
        override: T?,
        configured: T?,
    ): T? = if (options.has(name)) override else configured

    // null stays null. Anything else becomes a clean list of strings.
    private fun list(values: List<String>?): List<String>? =
        values?.flatMap { it.split(",") }?.map { it.trim() }?.filter { it.isNotEmpty() }
}
