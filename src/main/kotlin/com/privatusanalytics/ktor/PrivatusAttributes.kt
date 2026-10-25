package com.privatusanalytics.ktor

/**
 * Builds the data attributes the tracker reads for custom events and click maps.
 * Plain Kotlin, no Ktor needed. The template and kotlinx.html helpers wrap these functions.
 */
public object PrivatusAttributes {
    public const val MAX_NAME: Int = 120
    public const val MAX_KEY: Int = 64
    public const val MAX_PROPS: Int = 30
    public const val MAX_VALUE: Int = 500

    private val clickName = Regex("[A-Za-z0-9_.:-]{1,100}")
    private val keyJunk = Regex("[^a-z0-9_]")

    /**
     * `data-privatus-event` plus one `data-privatus-prop-<key>` per prop, or an empty map
     * when the name is empty.
     */
    public fun event(
        name: String?,
        props: Map<*, Any?>? = null,
    ): Map<String, String> {
        val clean =
            name
                .orEmpty()
                .trim()
                .takeCodePoints(MAX_NAME)
                .trim()
        if (clean.isEmpty()) return emptyMap()

        val attrs = linkedMapOf("data-privatus-event" to clean)
        normalize(props).forEach { (key, value) -> attrs["data-privatus-prop-$key"] = stringValue(value) }
        return attrs
    }

    /** `data-privatus-click`, or an empty map when the name is not valid. */
    public fun click(name: String?): Map<String, String> =
        if (isValidClick(name)) mapOf("data-privatus-click" to name!!) else emptyMap()

    /** Whether a click map name is valid: letters, digits, `_ . : -` and at most 100 characters. */
    public fun isValidClick(name: String?): Boolean = name != null && clickName.matches(name)

    /** Turns an attribute map into HTML, with every value escaped. */
    public fun toHtml(attrs: Map<String, String>): String =
        attrs.entries.joinToString(" ") { (name, value) -> "$name=\"${escapeHtml(value)}\"" }

    /**
     * Lowercases keys, swaps anything outside a-z 0-9 _ for _, cuts them to 64 characters,
     * drops null values and keeps the first 30 props.
     */
    public fun normalize(props: Map<*, Any?>?): Map<String, Any> {
        val clean = LinkedHashMap<String, Any>()
        props?.forEach { (key, value) ->
            val cleanKey = normalizeKey(key)
            if (cleanKey.isNotEmpty() && value != null) clean[cleanKey] = value
        }
        if (clean.size <= MAX_PROPS) return clean
        return clean.entries.take(MAX_PROPS).associateTo(LinkedHashMap()) { it.key to it.value }
    }

    /** One prop key, cleaned the way the tracker expects. */
    public fun normalizeKey(key: Any?): String =
        key
            ?.toString()
            .orEmpty()
            .lowercase()
            .replace(keyJunk, "_")
            .take(MAX_KEY)

    internal fun stringValue(value: Any): String {
        val text =
            when (value) {
                is Iterable<*> -> value.filterNotNull().joinToString(",")
                is Array<*> -> value.filterNotNull().joinToString(",")
                else -> value.toString()
            }
        return text.takeCodePoints(MAX_VALUE)
    }
}
