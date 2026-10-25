@file:JvmName("PrivatusKotlinxHtml")

package com.privatusanalytics.ktor

import io.ktor.server.application.ApplicationCall
import kotlinx.html.HEAD
import kotlinx.html.Tag
import kotlinx.html.unsafe

// kotlinx.html helpers. They need kotlinx-html on the classpath, which
// ktor-server-html-builder brings. Apps without it never load this file.

/**
 * Prints the Privatus Analytics tag in the head:
 *
 *     call.respondHtml { head { title("My app"); privatusAnalytics(call) } }
 *
 * When the tag is off (no site id, or outside the enabled environments) it prints only the
 * queue stub when that is on, and otherwise nothing.
 */
public fun HEAD.privatusAnalytics(
    call: ApplicationCall,
    options: TagOptions.() -> Unit = {},
) {
    val html = call.privatusTag(options)
    if (html.isNotEmpty()) unsafe { +html }
}

/**
 * Adds custom event attributes to this tag. Call it first in the tag's block:
 *
 *     button { privatusEvent("Export CSV", "format" to "csv"); +"Export" }
 *
 * On a form the event is sent when the form is submitted.
 */
public fun Tag.privatusEvent(
    name: String,
    vararg props: Pair<String, Any?>,
) {
    privatusEvent(name, props.toMap())
}

/** The same as the vararg form, with the props as a map. */
public fun Tag.privatusEvent(
    name: String,
    props: Map<String, Any?>,
) {
    PrivatusAttributes.event(name, props).forEach { (key, value) -> attributes[key] = value }
}

/**
 * Names this tag for click maps: `a(href = "/pricing") { privatusClick("pricing.cta"); +"See plans" }`.
 * Adds nothing, and logs a warning, when the name is not valid.
 */
public fun Tag.privatusClick(name: String) {
    privatusClickData(name).forEach { (key, value) -> attributes[key] = value }
}
