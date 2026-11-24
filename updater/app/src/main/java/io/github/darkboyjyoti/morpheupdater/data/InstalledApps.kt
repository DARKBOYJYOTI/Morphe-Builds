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
        val known = AppRegistry.byPackage(packageName)
        val candidates = buildList {
            add(packageName)
            known?.aliases?.forEach { if (it !in this) add(it) }
            known?.packageName?.let { if (it !in this) add(it) }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        for (candidate in candidates) {
            val info = try {
                packageManager.getPackageInfo(candidate, flags)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            } catch (_: Exception) {
                continue
            }
            return InstalledApp(
                packageName = candidate,
                versionName = info.versionName,
                versionCode = ApkInspector.versionCodeCompat(info),
                signerSha256 = ApkInspector.signerSha256(info),
            )
        }
        return null
    }
}
