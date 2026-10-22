package io.github.darkboyjyoti.morpheupdater.data

import java.io.File

data class UpdateRow(
    val appName: String,
    val packageName: String,
    val asset: ReleaseAsset,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val installedSignerSha256: String?,
    val support: SupportResult,
    val upToDate: Boolean,
    val signatureConflict: Boolean,
    val downloadedFile: File?,
    val releaseTag: String,
) {
    val canInstall: Boolean
        get() = support is SupportResult.Supported && !signatureConflict
}
