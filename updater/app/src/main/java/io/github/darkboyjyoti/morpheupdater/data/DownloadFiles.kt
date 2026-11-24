package io.github.darkboyjyoti.morpheupdater.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

object DownloadFiles {

    fun directory(context: Context): File {
        val base = context.getExternalFilesDir("updates") ?: File(context.filesDir, "updates")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun fileFor(
        context: Context,
        packageName: String,
        versionCode: Long,
        releaseTag: String? = null,
    ): File = File(directory(context), "$packageName--${assetKey(versionCode, releaseTag)}.apk")

    fun partFor(file: File): File = File(file.absolutePath + ".part")

    fun find(context: Context, packageName: String): File? {
        val files = directory(context).listFiles { file -> file.isFile && file.name.endsWith(".apk") }
            ?: return null
        return files
            .filter { it.name.startsWith("$packageName--") || it.name.startsWith("$packageName-") }
            .maxByOrNull { it.lastModified() }
    }

    fun findFor(
        context: Context,
        packageName: String,
        versionCode: Long,
        releaseTag: String? = null,
    ): File? {
        val expected = fileFor(context, packageName, versionCode, releaseTag)
        if (expected.isFile) return expected
        if (versionCode <= 0L) return null
        val candidates = packageNames(packageName)
        return candidates
            .asSequence()
            .map { File(directory(context), "$it-$versionCode.apk") }
            .firstOrNull { it.isFile }
    }

    fun packageNameFromFile(file: File): String {
        val name = file.name
        val marker = name.indexOf("--")
        if (marker > 0) return name.substring(0, marker)
        return AppRegistry.apps
            .flatMap { app -> listOf(app.packageName) + app.aliases }
            .sortedByDescending { it.length }
            .firstOrNull { name.startsWith("$it-") }
            ?: name.substringBeforeLast('-', name)
    }

    fun releaseTagFor(file: File): String? =
        runCatching { File(file.absolutePath + ".tag").readText(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    fun saveReleaseTag(file: File, releaseTag: String?) {
        if (releaseTag.isNullOrBlank()) return
        runCatching { File(file.absolutePath + ".tag").writeText(releaseTag, Charsets.UTF_8) }
    }

    fun list(context: Context): List<File> =
        directory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".apk") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun delete(file: File) {
        file.delete()
        partFor(file).delete()
        File(file.absolutePath + ".tag").delete()
    }

    fun deleteForPackage(context: Context, packageName: String) {
        val prefixes = packageNames(packageName).map { "$it-" } +
            packageNames(packageName).map { "$it--" }
        directory(context)
            .listFiles { file ->
                file.isFile && prefixes.any { prefix -> file.name.startsWith(prefix) }
            }
            ?.forEach(::delete)
    }

    fun cleanupStaleParts(context: Context, maxAgeMillis: Long = 24L * 60L * 60L * 1000L) {
        val now = System.currentTimeMillis()
        directory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".part") }
            ?.forEach { part ->
                if (now - part.lastModified() > maxAgeMillis) part.delete()
            }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(value, units[index])
    }

    private fun assetKey(versionCode: Long, releaseTag: String?): String {
        if (versionCode > 0L) return "v$versionCode"
        val tag = releaseTag.orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(tag.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "t$digest"
    }

    private fun packageNames(packageName: String): List<String> {
        val known = AppRegistry.byPackage(packageName) ?: return listOf(packageName)
        return (listOf(known.packageName) + known.aliases).distinct()
    }
}
