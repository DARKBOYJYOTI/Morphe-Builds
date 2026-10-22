package io.github.darkboyjyoti.morpheupdater.data

import android.content.Context
import java.io.File

object DownloadFiles {

    fun directory(context: Context): File {
        val base = context.getExternalFilesDir("updates") ?: File(context.filesDir, "updates")
        if (!base.exists()) base.mkdirs()
        return base
    }

    fun fileFor(context: Context, packageName: String, versionCode: Long): File =
        File(directory(context), "$packageName-$versionCode.apk")

    fun partFor(file: File): File = File(file.absolutePath + ".part")

    fun find(context: Context, packageName: String): File? {
        val files = directory(context).listFiles { file -> file.isFile && file.name.endsWith(".apk") }
            ?: return null
        return files
            .filter { it.name.startsWith("$packageName-") }
            .maxByOrNull { it.lastModified() }
    }

    fun findFor(context: Context, packageName: String, versionCode: Long): File? {
        val file = find(context, packageName) ?: return null
        if (versionCode <= 0L) return file
        val expected = "$packageName-$versionCode.apk"
        return file.takeIf { it.name == expected }
    }

    fun list(context: Context): List<File> =
        directory(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".apk") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun delete(file: File) {
        file.delete()
        partFor(file).delete()
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
}
