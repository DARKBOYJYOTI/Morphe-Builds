package io.github.darkboyjyoti.morpheupdater.data

import android.os.Build

object Compatibility {

    fun forAsset(asset: ReleaseAsset, installed: InstalledApps.InstalledApp?): SupportResult {
        if (asset.minSdk != null && asset.minSdk > Build.VERSION.SDK_INT) {
            return SupportResult.RequiresSdk(asset.minSdk)
        }
        if (!ApkInspector.isAbiSupported(asset.abis)) {
            return SupportResult.UnsupportedAbi(
                deviceAbis = Build.SUPPORTED_ABIS.toList(),
                assetAbis = asset.abis,
            )
        }
        if (installed != null &&
            asset.signerSha256 != null &&
            installed.signerSha256 != null &&
            !asset.signerSha256.equals(installed.signerSha256, ignoreCase = true)
        ) {
            return SupportResult.SignatureMismatch
        }
        return SupportResult.Supported
    }

    fun forApk(
        details: ApkInspector.ApkDetails,
        installed: InstalledApps.InstalledApp?,
    ): SupportResult {
        if (details.minSdk > 0 && details.minSdk > Build.VERSION.SDK_INT) {
            return SupportResult.RequiresSdk(details.minSdk)
        }
        if (details.abis.isNotEmpty() && !ApkInspector.isAbiSupported(details.abis)) {
            return SupportResult.UnsupportedAbi(
                deviceAbis = Build.SUPPORTED_ABIS.toList(),
                assetAbis = details.abis.toList(),
            )
        }
        if (installed != null &&
            details.signerSha256 != null &&
            installed.signerSha256 != null &&
            !details.signerSha256.equals(installed.signerSha256, ignoreCase = true)
        ) {
            return SupportResult.SignatureMismatch
        }
        return SupportResult.Supported
    }
}
