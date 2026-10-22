package io.github.darkboyjyoti.morpheupdater.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object InstalledApps {

    data class InstalledApp(
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val signerSha256: String?,
    )

    fun get(context: Context, packageName: String): InstalledApp? {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return try {
            val info = packageManager.getPackageInfo(packageName, flags)
            InstalledApp(
                packageName = packageName,
                versionName = info.versionName,
                versionCode = ApkInspector.versionCodeCompat(info),
                signerSha256 = ApkInspector.signerSha256(info),
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
