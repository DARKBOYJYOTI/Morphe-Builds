package io.github.darkboyjyoti.morpheupdater.data

data class ReleaseAsset(
    val fileName: String,
    val url: String,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val abis: List<String>,
    val size: Long,
    val sha256: String?,
    val signerSha256: String?,
)

data class Release(
    val tag: String,
    val title: String,
    val publishedAt: String,
    val assets: List<ReleaseAsset>,
)
