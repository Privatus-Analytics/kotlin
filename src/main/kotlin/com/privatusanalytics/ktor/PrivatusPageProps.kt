package com.privatusanalytics.ktor

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Turns page props (plan, role and so on) into JSON that is safe inside a `<script>`.
 * Keys follow the same rules as event props. Numbers, booleans and lists keep their JSON types.
 */
public object PrivatusPageProps {
    public const val MAX_ITEMS: Int = 30

    /** A JSON object string, or null when there are no props to send. */
    public fun json(props: Map<*, Any?>?): String? {
        val clean = LinkedHashMap<String, Any>()
        PrivatusAttributes.normalize(props).forEach { (key, value) ->
            jsonValue(value)?.let { clean[key] = it }
        }
        if (clean.isEmpty()) return null

        val out = StringBuilder("{")
        clean.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) out.append(',')
            writeString(out, key)
            out.append(':')
            writeValue(out, value)
        }
        return out.append('}').toString()
    }

    private fun jsonValue(value: Any): Any? =
        when (value) {
            is Iterable<*> -> list(value.toList())
            is Array<*> -> list(value.toList())
            else -> scalar(value)
        }

    private fun list(items: List<Any?>): List<Any> = items.filterNotNull().take(MAX_ITEMS).mapNotNull(::scalar)

    private fun scalar(value: Any?): Any? =
        when (value) {
            null -> null
            is Boolean, is Int, is Long, is Short, is Byte, is BigInteger, is BigDecimal -> value
            is Double -> if (value.isFinite()) value else null
            is Float -> if (value.isFinite()) value else null
            else -> value.toString().takeCodePoints(PrivatusAttributes.MAX_VALUE)
        }

    private fun writeValue(
        out: StringBuilder,
        value: Any,
    ) {
        when (value) {
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeValue(out, item!!)
                }
                out.append(']')
            }
            is String -> writeString(out, value)
            is BigDecimal -> out.append(value.toPlainString())
            else -> out.append(value.toString())
        }
    }

    // Escapes like JSON, plus < > & U+2028 U+2029 so the JSON can never end the script early.
    private fun writeString(
        out: StringBuilder,
        value: String,
    ) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000c' -> out.append("\\f")
                '<', '>', '&', ' ', ' ' -> out.append(unicodeEscape(char))
                else -> if (char < ' ') out.append(unicodeEscape(char)) else out.append(char)
            }
        }
        out.append('"')
    }

    private fun unicodeEscape(char: Char): String = "\\u%04x".format(char.code)
}
