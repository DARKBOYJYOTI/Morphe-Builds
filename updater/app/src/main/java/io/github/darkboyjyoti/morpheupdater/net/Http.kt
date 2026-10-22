package io.github.darkboyjyoti.morpheupdater.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Http {

    const val USER_AGENT =
        "MorpheBuildsUpdater/1.0 (+https://github.com/DARKBOYJYOTI/Morphe-Builds)"

    data class Response(
        val code: Int,
        val body: String?,
        val etag: String?,
    )

    fun get(
        url: String,
        etag: String? = null,
        accept: String = "application/vnd.github+json",
        timeoutMs: Int = 20_000,
    ): Response {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Encoding", "gzip")
            if (!etag.isNullOrBlank()) {
                setRequestProperty("If-None-Match", etag)
            }
        }

        return try {
            val code = connection.responseCode
            val raw = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = raw?.let { stream ->
                val decoded = if (connection.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                    GZIPInputStream(stream)
                } else {
                    stream
                }
                decoded.bufferedReader().use { it.readText() }
            }
            if (code == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0") {
                throw IOException("rate_limited")
            }
            Response(code, body, connection.getHeaderField("ETag"))
        } finally {
            connection.disconnect()
        }
    }
}
