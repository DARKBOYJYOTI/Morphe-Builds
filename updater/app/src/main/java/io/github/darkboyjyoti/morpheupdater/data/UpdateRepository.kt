package io.github.darkboyjyoti.morpheupdater.data

import android.content.Context
import android.os.Build

object UpdateRepository {

    suspend fun loadRelease(context: Context, force: Boolean): Release =
        io.github.darkboyjyoti.morpheupdater.net.GithubApi
            .loadLatestRelease(context.applicationContext, force)

    fun buildRows(context: Context, release: Release): List<UpdateRow> {
        val prefs = Prefs(context.applicationContext)
        val grouped = LinkedHashMap<String, MutableList<ReleaseAsset>>()

        release.assets.forEach { asset ->
            if (asset.packageName.isBlank()) return@forEach
            grouped.getOrPut(asset.packageName) { mutableListOf() } += asset
        }

        val rows = grouped.map { (packageName, assets) ->
            val installed = InstalledApps.get(context, packageName)
            val asset = chooseAsset(assets)
            val support = Compatibility.forAsset(asset, installed)
            val signatureConflict = support is SupportResult.SignatureMismatch

            val trackedTag = prefs.installedTag(packageName)
            val trackedCode = prefs.installedBuildVersionCode(packageName)
            val trustTracked = installed != null &&
                trackedTag != null &&
                trackedCode > 0L &&
                installed.versionCode == trackedCode

            val upToDate = when {
                installed == null -> false
                signatureConflict -> false
                trustTracked -> trackedTag == release.tag
                asset.versionCode > 0L -> installed.versionCode >= asset.versionCode
                asset.versionName.isNotBlank() -> installed.versionName == asset.versionName
                else -> false
            }

            UpdateRow(
                appName = AppRegistry.displayName(packageName, asset.label),
                packageName = packageName,
                asset = asset,
                installedVersionName = installed?.versionName,
                installedVersionCode = installed?.versionCode,
                installedSignerSha256 = installed?.signerSha256,
                support = support,
                upToDate = upToDate,
                signatureConflict = signatureConflict,
                downloadedFile = DownloadFiles.find(context, packageName),
                releaseTag = release.tag,
            )
        }

        return rows.sortedWith(
            compareBy({ registryIndex(it.packageName) }, { it.appName.lowercase() }),
        )
    }

    private fun chooseAsset(assets: List<ReleaseAsset>): ReleaseAsset {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.let { ApkInspector.normalizeAbi(it) }
        val ranked = assets.sortedWith(
            compareByDescending<ReleaseAsset> { it.versionCode }
                .thenByDescending { asset ->
                    primaryAbi != null && asset.abis.any { ApkInspector.normalizeAbi(it) == primaryAbi }
                }
                .thenByDescending { it.size },
        )
        val compatible = ranked.firstOrNull { asset ->
            (asset.minSdk == null || asset.minSdk <= Build.VERSION.SDK_INT) &&
                ApkInspector.isAbiSupported(asset.abis)
        }
        return compatible ?: ranked.first()
    }

    private fun registryIndex(packageName: String): Int {
        val index = AppRegistry.apps.indexOfFirst { it.packageName == packageName }
        return if (index >= 0) index else AppRegistry.apps.size
    }
}
