package io.github.darkboyjyoti.morpheupdater.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var repo: String
        get() = sp.getString(KEY_REPO, DEFAULT_REPO).orEmpty().ifBlank { DEFAULT_REPO }
        set(value) = sp.edit().putString(KEY_REPO, value.trim()).apply()

    var intervalHours: Int
        get() = sp.getInt(KEY_INTERVAL, 12)
        set(value) = sp.edit().putInt(KEY_INTERVAL, value).apply()

    var wifiOnly: Boolean
        get() = sp.getBoolean(KEY_WIFI, true)
        set(value) = sp.edit().putBoolean(KEY_WIFI, value).apply()

    var autoDownload: Boolean
        get() = sp.getBoolean(KEY_AUTO_DOWNLOAD, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_DOWNLOAD, value).apply()

    var notifications: Boolean
        get() = sp.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var lastCheckMillis: Long
        get() = sp.getLong(KEY_LAST_CHECK, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_CHECK, value).apply()

    var cachedReleases: String?
        get() = sp.getString(KEY_CACHED_RELEASES, null)
        set(value) = sp.edit().putString(KEY_CACHED_RELEASES, value).apply()

    var cachedReleasesEtag: String?
        get() = sp.getString(KEY_CACHED_RELEASES_ETAG, null)
        set(value) = sp.edit().putString(KEY_CACHED_RELEASES_ETAG, value).apply()

    fun clearReleaseCache() {
        sp.edit()
            .remove(KEY_CACHED_RELEASES)
            .remove(KEY_CACHED_RELEASES_ETAG)
            .apply()
    }

    fun installedTag(packageName: String): String? =
        sp.getString(KEY_INSTALLED_TAG_PREFIX + packageName, null)

    fun installedBuildVersionCode(packageName: String): Long =
        sp.getLong(KEY_INSTALLED_CODE_PREFIX + packageName, 0L)

    fun recordInstalled(packageName: String, tag: String, versionCode: Long) {
        sp.edit()
            .putString(KEY_INSTALLED_TAG_PREFIX + packageName, tag)
            .putLong(KEY_INSTALLED_CODE_PREFIX + packageName, versionCode)
            .apply()
    }

    fun clearInstalledRecord(packageName: String) {
        sp.edit()
            .remove(KEY_INSTALLED_TAG_PREFIX + packageName)
            .remove(KEY_INSTALLED_CODE_PREFIX + packageName)
            .apply()
    }

    companion object {
        const val DEFAULT_REPO = "DARKBOYJYOTI/Morphe-Builds"

        private const val KEY_REPO = "repo"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_WIFI = "wifi_only"
        private const val KEY_AUTO_DOWNLOAD = "auto_download"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_CACHED_RELEASES = "cached_releases"
        private const val KEY_CACHED_RELEASES_ETAG = "cached_releases_etag"
        private const val KEY_INSTALLED_TAG_PREFIX = "installed_tag_"
        private const val KEY_INSTALLED_CODE_PREFIX = "installed_code_"
    }
}
