package io.github.darkboyjyoti.morpheupdater.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object ApkInspector {

    data class ApkDetails(
        val packageName: String,
        val label: String,
        val versionName: String,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val abis: Set<String>,
        val size: Long,
        val signerSha256: String?,
    )

    fun inspect(context: Context, file: File): ApkDetails? {
        if (!file.isFile) return null
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val applicationInfo = info.applicationInfo ?: return null
        applicationInfo.sourceDir = file.absolutePath
        applicationInfo.publicSourceDir = file.absolutePath

        val label = runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(info.packageName)

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            applicationInfo.minSdkVersion
        } else {
            0
        }
        val targetSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            applicationInfo.targetSdkVersion
        } else {
            0
        }

        return ApkDetails(
            packageName = info.packageName,
            label = label.ifBlank { info.packageName },
            versionName = info.versionName.orEmpty(),
            versionCode = versionCodeCompat(info),
            minSdk = minSdk,
            targetSdk = targetSdk,
            abis = readAbis(file),
            size = file.length(),
            signerSha256 = signerSha256(info),
        )
    }

    @Suppress("DEPRECATION")
    fun versionCodeCompat(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    fun signerSha256(info: PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        val signature = signatures?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun readAbis(file: File): Set<String> {
        return runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .mapNotNull { entry ->
                        val name = entry.name
                        if (!name.startsWith("lib/")) return@mapNotNull null
                        val parts = name.split('/')
                        if (parts.size < 3) return@mapNotNull null
                        parts[1].takeIf { it.isNotBlank() }
                    }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun isAbiSupported(abis: Collection<String>): Boolean {
        if (abis.isEmpty()) return true
        val device = Build.SUPPORTED_ABIS.map { normalizeAbi(it) }.toSet()
        return abis.any { normalizeAbi(it) in device }
    }

    fun normalizeAbi(abi: String): String = when (abi.lowercase()) {
        "arm-v7a", "armv7", "armeabi-v7a" -> "armeabi-v7a"
        "arm64", "arm64-v8a" -> "arm64-v8a"
        "x86" -> "x86"
        "x86_64", "x64" -> "x86_64"
        else -> abi.lowercase()
    }

    fun deviceAbiLabel(): String =
        Build.SUPPORTED_ABIS.joinToString(", ") { normalizeAbi(it) }

    fun isCompatibleDevice(minSdk: Int, abis: Collection<String>): Boolean {
        if (minSdk > 0 && minSdk > Build.VERSION.SDK_INT) return false
        return isAbiSupported(abis)
    }
}
