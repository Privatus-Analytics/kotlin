package com.privatusanalytics.ktor

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Per call overrides for one tag. Only the settings you set here change; the rest come from
 * the plugin configuration.
 *
 *     call.privatusTag { modules = listOf("engage", "auto"); props = mapOf("section" to "billing") }
 */
public class TagOptions {
    private val values = HashMap<String, Any?>()

    public var siteId: String? by Override()
    public var scriptHost: String? by Override()
    public var modules: List<String>? by Override()
    public var excludedPaths: List<String>? by Override()
    public var domains: List<String>? by Override()
    public var spa: String? by Override()
    public var manual: Boolean? by Override()
    public var enabled: Boolean? by Override()
    public var extraAttributes: Map<String, Any?>? by Override()
    public var queueStub: Boolean? by Override()

    /** Merged over the configured props. A null value removes one. */
    public var props: Map<String, Any?>? = null

    /** The CSP nonce for this tag. Without it the plugin's nonce block is used. */
    public var nonce: String? = null

    internal fun has(name: String): Boolean = values.containsKey(name)

    private class Override<T> : ReadWriteProperty<TagOptions, T?> {
        @Suppress("UNCHECKED_CAST")
        override fun getValue(
            thisRef: TagOptions,
            property: KProperty<*>,
        ): T? = thisRef.values[property.name] as T?

        override fun setValue(
            thisRef: TagOptions,
            property: KProperty<*>,
            value: T?,
        ) {
            thisRef.values[property.name] = value
        }
    }
}
