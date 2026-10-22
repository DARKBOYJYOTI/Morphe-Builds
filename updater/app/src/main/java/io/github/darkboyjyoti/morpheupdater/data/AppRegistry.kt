package io.github.darkboyjyoti.morpheupdater.data

object AppRegistry {

    data class KnownApp(
        val packageName: String,
        val displayName: String,
        val assetPrefixes: List<String>,
        val color: Int,
    )

    const val COLOR_BLUE = 0xFF4285F4.toInt()
    const val COLOR_RED = 0xFFEA4335.toInt()
    const val COLOR_YELLOW = 0xFFFBBC05.toInt()
    const val COLOR_GREEN = 0xFF34A853.toInt()

    val apps: List<KnownApp> = listOf(
        KnownApp(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            assetPrefixes = listOf("youtube-", "youtube-music-"),
            color = COLOR_RED,
        ),
        KnownApp(
            packageName = "com.google.android.apps.youtube.music",
            displayName = "YouTube Music",
            assetPrefixes = listOf("music-", "youtube-music-"),
            color = COLOR_RED,
        ),
        KnownApp(
            packageName = "com.reddit.frontpage",
            displayName = "Reddit",
            assetPrefixes = listOf("reddit-"),
            color = COLOR_YELLOW,
        ),
        KnownApp(
            packageName = "com.google.android.apps.photos",
            displayName = "Google Photos",
            assetPrefixes = listOf("photos-", "googlephotos-"),
            color = COLOR_BLUE,
        ),
        KnownApp(
            packageName = "com.twitter.android",
            displayName = "X (Twitter)",
            assetPrefixes = listOf("twitter-", "x-"),
            color = COLOR_BLUE,
        ),
    )

    fun byPackage(packageName: String): KnownApp? =
        apps.firstOrNull { it.packageName == packageName }

    fun byAssetName(fileName: String): KnownApp? {
        val lower = fileName.lowercase()
        var best: KnownApp? = null
        var bestPrefixLength = -1
        for (app in apps) {
            for (prefix in app.assetPrefixes) {
                if (lower.startsWith(prefix) && prefix.length > bestPrefixLength) {
                    best = app
                    bestPrefixLength = prefix.length
                }
            }
        }
        return best
    }

    fun displayName(packageName: String, fallback: String? = null): String =
        byPackage(packageName)?.displayName ?: fallback?.takeIf { it.isNotBlank() } ?: packageName

    fun colorFor(packageName: String): Int =
        byPackage(packageName)?.color ?: COLOR_BLUE
}
