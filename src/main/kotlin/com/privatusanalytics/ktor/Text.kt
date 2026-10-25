package com.privatusanalytics.ktor

/** Escapes text for an HTML attribute value or text node: & < > " and '. */
internal fun escapeHtml(value: String): String {
    if (value.none { it == '&' || it == '<' || it == '>' || it == '"' || it == '\'' }) return value
    val out = StringBuilder(value.length + 16)
    for (char in value) {
        when (char) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(char)
        }
    }
    return out.toString()
}

/** The first [count] characters, counting by code point so emoji are never cut in half. */
internal fun String.takeCodePoints(count: Int): String {
    if (length <= count) return this
    if (codePointCount(0, length) <= count) return this
    return substring(0, offsetByCodePoints(0, count))
}
