package io.github.darkboyjyoti.morpheupdater.data

sealed class SupportResult {
    object Supported : SupportResult()
    data class UnsupportedAbi(val deviceAbis: List<String>, val assetAbis: List<String>) : SupportResult()
    data class RequiresSdk(val minSdk: Int) : SupportResult()
    object SignatureMismatch : SupportResult()
}
