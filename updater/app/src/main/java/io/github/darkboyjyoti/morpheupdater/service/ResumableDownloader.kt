package io.github.darkboyjyoti.morpheupdater.service

import io.github.darkboyjyoti.morpheupdater.data.ApkInspector
import io.github.darkboyjyoti.morpheupdater.net.Http
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class ResumableDownloader(
    private val url: String,
    private val target: File,
    private val expectedSha256: String?,
    private val onProgress: (downloaded: Long, total: Long) -> Unit,
    private val isCancelled: () -> Boolean,
) {

    sealed class Result {
        data class Success(val file: File, val bytes: Long) : Result()
        data class Failure(val message: String, val cause: Throwable? = null) : Result()
        object Cancelled : Result()
    }

    fun run(): Result {
        val part = File(target.absolutePath + ".part")
        target.parentFile?.mkdirs()

        var offset = if (part.exists()) part.length() else 0L
        var restarted = false

        while (true) {
            if (isCancelled()) return Result.Cancelled
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", Http.USER_AGENT)
                    setRequestProperty("Accept", "application/octet-stream")
                    if (offset > 0L) {
                        setRequestProperty("Range", "bytes=$offset-")
                    }
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK && offset > 0L) {
                    offset = 0L
                    part.delete()
                } else if (code == 416 && !restarted) {
                    restarted = true
                    part.delete()
                    offset = 0L
                    continue
                } else if (code != HttpURLConnection.HTTP_OK && code != 206) {
                    return Result.Failure("HTTP $code")
                }

                val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    ?: connection.contentLength.toLong()
                val total = if (code == 206) {
                    offset + contentLength.coerceAtLeast(0L)
                } else {
                    contentLength.coerceAtLeast(0L)
                }

                RandomAccessFile(part, "rw").use { randomAccess ->
                    randomAccess.seek(offset)
                    val input = connection.inputStream.buffered()
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = offset
                    var sinceProgress = 0L
                    var sinceThrottle = 0L

                    while (true) {
                        if (isCancelled()) return Result.Cancelled
                        val read = input.read(buffer)
                        if (read < 0) break
                        randomAccess.write(buffer, 0, read)
                        downloaded += read
                        sinceProgress += read
                        sinceThrottle += read

                        if (sinceProgress >= 256 * 1024) {
                            onProgress(downloaded, if (total > 0L) total else downloaded)
                            sinceProgress = 0L
                        }
                        if (sinceThrottle >= 2 * 1024 * 1024) {
                            sinceThrottle = 0L
                            Thread.sleep(120)
                        }
                    }
                    randomAccess.fd.sync()
                }

                if (isCancelled()) return Result.Cancelled

                if (total > 0L && part.length() < total) {
                    offset = part.length()
                    continue
                }
                break
            } catch (error: Exception) {
                if (isCancelled()) return Result.Cancelled
                return Result.Failure(error.message ?: "network_error", error)
            } finally {
                connection?.disconnect()
            }
        }

        if (isCancelled()) return Result.Cancelled

        val sha = expectedSha256
        if (!sha.isNullOrBlank()) {
            onProgress(part.length(), part.length())
            val actual = ApkInspector.sha256(part)
            if (!actual.equals(sha, ignoreCase = true)) {
                part.delete()
                return Result.Failure("checksum_mismatch")
            }
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return Result.Success(target, target.length())
    }
}
