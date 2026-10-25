@file:JvmName("PrivatusTemplates")

package com.privatusanalytics.ktor

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.privatusanalytics.ktor.PrivatusAnalytics")

/**
 * Data attributes for a custom event, as HTML to put inside a tag you write yourself.
 * For Freemarker, Thymeleaf, Pebble or any other template engine:
 *
 *     privatusEventAttrs("Export CSV", "format" to "csv")
 *     // data-privatus-event="Export CSV" data-privatus-prop-format="csv"
 *
 * On a `<form>` the event is sent when the form is submitted. Returns "" for an empty name.
 * Prints in every environment, even when the tag is off.
 */
public fun privatusEventAttrs(
    name: String?,
    vararg props: Pair<String, Any?>,
): String = privatusEventAttrs(name, props.toMap())

/** The same as [privatusEventAttrs], with the props as a map. */
public fun privatusEventAttrs(
    name: String?,
    props: Map<String, Any?>,
): String = PrivatusAttributes.toHtml(PrivatusAttributes.event(name, props))

/** The event attributes as a map with full attribute names, for builders that take a map. */
public fun privatusEventData(
    name: String?,
    vararg props: Pair<String, Any?>,
): Map<String, String> = PrivatusAttributes.event(name, props.toMap())

/** The same as [privatusEventData], with the props as a map. */
public fun privatusEventData(
    name: String?,
    props: Map<String, Any?>,
): Map<String, String> = PrivatusAttributes.event(name, props)

/**
 * The click map name attribute as HTML: `data-privatus-click="pricing.cta"`.
 * Prints nothing, and logs a warning, when the name is not valid.
 */
public fun privatusClickAttr(name: String?): String = PrivatusAttributes.toHtml(privatusClickData(name))

/** The click map name as a map, for builders that take a map. Empty when the name is not valid. */
public fun privatusClickData(name: String?): Map<String, String> {
    val data = PrivatusAttributes.click(name)
    if (data.isEmpty()) warnInvalidClick(name)
    return data
}

internal fun warnInvalidClick(name: String?) {
    logger.warn(
        "[privatus-analytics] click map name \"{}\" is not valid. Use letters, digits and _ . : - only, " +
            "at most 100 characters. Nothing was printed.",
        name,
    )
}
