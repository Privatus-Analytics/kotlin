package com.privatusanalytics.ktor

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writer
import kotlinx.coroutines.coroutineScope

/**
 * Adds the tag just before `</head>` in HTML responses. Off unless `autoInject = true`, or the
 * route has the [PrivatusAutoInject] plugin.
 * Only touches status 200, uncompressed `text/html` that is not an Ajax request and has no tag yet.
 */
internal object AutoInject {
    private val headClose = Regex("</head>", RegexOption.IGNORE_CASE)

    suspend fun transform(
        call: ApplicationCall,
        config: PrivatusAnalyticsConfig,
        body: OutgoingContent,
    ): OutgoingContent? {
        if (!inject(call, config, body)) return null

        val buffered = bytes(body) ?: return null
        val contentType = body.contentType ?: ContentType.Text.Html
        val charset = contentType.charset() ?: Charsets.UTF_8
        val html = String(buffered, charset)
        val match = headClose.find(html)

        if (html.contains(PrivatusTag.MARKER) || match == null) {
            // Streamed bodies were read already, so send the bytes we hold.
            return if (body is TextContent ||
                body is OutgoingContent.ByteArrayContent
            ) {
                null
            } else {
                Injected(buffered, body)
            }
        }

        val tag = PrivatusTag.render(config, TagOptions(), call)
        val result = html.substring(0, match.range.first) + tag + "\n" + html.substring(match.range.first)
        return Injected(result.toByteArray(charset), body)
    }

    private fun inject(
        call: ApplicationCall,
        config: PrivatusAnalyticsConfig,
        body: OutgoingContent,
    ): Boolean {
        if (!config.autoInject && !call.attributes.contains(AutoInjectRouteKey)) return false
        val status = body.status ?: call.response.status() ?: HttpStatusCode.OK
        if (status != HttpStatusCode.OK) return false
        val type = body.contentType ?: call.response.headers[HttpHeaders.ContentType]?.let(ContentType::parse)
        if (type == null || !type.withoutParameters().match(ContentType.Text.Html)) return false
        if (body.headers[HttpHeaders.ContentEncoding] != null) return false
        if (call.response.headers[HttpHeaders.ContentEncoding] != null) return false
        if (call.request.headers["X-Requested-With"].equals("XMLHttpRequest", ignoreCase = true)) return false
        return PrivatusTag.shouldRender(config, TagOptions(), call)
    }

    private suspend fun bytes(body: OutgoingContent): ByteArray? =
        when (body) {
            is TextContent -> body.bytes()
            is OutgoingContent.ByteArrayContent -> body.bytes()
            is OutgoingContent.ReadChannelContent -> body.readFrom().toByteArray()
            is OutgoingContent.WriteChannelContent ->
                coroutineScope { writer { body.writeTo(channel) }.channel.toByteArray() }
            else -> null
        }

    /** The new body with the same type, status and headers, and a correct Content-Length. */
    private class Injected(
        private val content: ByteArray,
        private val original: OutgoingContent,
    ) : OutgoingContent.ByteArrayContent() {
        override val contentType: ContentType? =
            original.contentType ?: ContentType.Text.Html.withCharset(Charsets.UTF_8)
        override val status: HttpStatusCode? = original.status
        override val contentLength: Long = content.size.toLong()
        override val headers: Headers =
            Headers.build {
                original.headers.forEach { name, values ->
                    if (!name.equals(HttpHeaders.ContentLength, ignoreCase = true)) appendAll(name, values)
                }
            }

        override fun bytes(): ByteArray = content
    }
}
