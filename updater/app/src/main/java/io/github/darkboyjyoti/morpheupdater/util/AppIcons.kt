package io.github.darkboyjyoti.morpheupdater.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

object AppIcons {

    private const val MAX_ICON_BYTES = 2L * 1024L * 1024L

    private val cache = LruCache<String, Drawable>(64)
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())

    fun load(
        context: Context,
        packageName: String,
        iconUrl: String?,
        apkFile: File?,
        onLoaded: (Drawable) -> Unit,
    ): Drawable? {
        val key = "$packageName|${iconUrl.orEmpty()}"
        cache.get(key)?.let {
            onLoaded(it)
            return it
        }

        installedIcon(context, packageName)?.let {
            cache.put(key, it)
            onLoaded(it)
            return it
        }

        cachedIcon(context, packageName)?.let {
            cache.put(key, it)
            onLoaded(it)
            return it
        }

        if (apkFile != null && apkFile.isFile) {
            archiveIcon(context, apkFile)?.let {
                cache.put(key, it)
                onLoaded(it)
                return it
            }
        }

        val url = iconUrl?.takeIf { it.isNotBlank() } ?: return null
        if (!inFlight.add(key)) return null

        executor.execute {
            val drawable = downloadIcon(context, packageName, url)
            inFlight.remove(key)
            if (drawable != null) {
                cache.put(key, drawable)
                main.post { onLoaded(drawable) }
            }
        }
        return null
    }

    private fun installedIcon(context: Context, packageName: String): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    private fun cachedIcon(context: Context, packageName: String): Drawable? {
        val file = iconFile(context, packageName)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)?.let { BitmapDrawable(context.resources, it) }
    }

    private fun archiveIcon(context: Context, apkFile: File): Drawable? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return@runCatching null
        val application = info.applicationInfo ?: return@runCatching null
        application.sourceDir = apkFile.absolutePath
        application.publicSourceDir = apkFile.absolutePath
        context.packageManager.getApplicationIcon(application)
    }.getOrNull()

    private fun downloadIcon(context: Context, packageName: String, url: String): Drawable? {
        val bytes = runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val contentLength = connection.contentLengthLong
                if (contentLength > MAX_ICON_BYTES) return null
                val buffer = ByteArray(64 * 1024)
                val output = java.io.ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        if (output.size() > MAX_ICON_BYTES) return null
                        read = input.read(buffer)
                    }
                }
                output.toByteArray()
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return null

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        runCatching {
            val target = iconFile(context, packageName)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun iconFile(context: Context, packageName: String): File =
        File(File(context.filesDir, "icons"), "icon-$packageName.img")
}
