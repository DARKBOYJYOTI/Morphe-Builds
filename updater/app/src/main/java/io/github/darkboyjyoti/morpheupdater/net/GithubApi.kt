package io.github.darkboyjyoti.morpheupdater.net

import android.content.Context
import io.github.darkboyjyoti.morpheupdater.data.AppRegistry
import io.github.darkboyjyoti.morpheupdater.data.Prefs
import io.github.darkboyjyoti.morpheupdater.data.Release
import io.github.darkboyjyoti.morpheupdater.data.ReleaseAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

object GithubApi {

    private const val API = "https://api.github.com"

    private val VERSION_ARCH = Regex(
        """-v([0-9][A-Za-z0-9.]*?)-(arm64-v8a|armeabi-v7a|arm-v7a|x86|x86_64|universal|all)\.apk$""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun loadLatestRelease(context: Context, force: Boolean): Release =
        withContext(Dispatchers.IO) {
            val prefs = Prefs(context.applicationContext)
            val repo = prefs.repo
            val etag = if (force) null else prefs.cachedReleasesEtag

            val response = Http.get("$API/repos/$repo/releases?per_page=10", etag = etag)

            val body: String = when {
                response.code == 304 && !prefs.cachedReleases.isNullOrBlank() -> prefs.cachedReleases!!
                response.code == 200 && response.body != null -> {
                    prefs.cachedReleases = response.body
                    prefs.cachedReleasesEtag = response.etag
                    response.body
                }
                response.code == 404 -> throw IOException("release_not_found")
                response.code == 403 -> throw IOException("rate_limited")
                else -> throw IOException("http_${response.code}")
            }

            val releases = JSONArray(body)
            var selected: JSONObject? = null
            var selectedAssets: JSONArray? = null

            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", false)) continue
                val assets = release.optJSONArray("assets") ?: continue
                if (!containsInstallableAsset(assets)) continue
                selected = release
                selectedAssets = assets
                break
            }

            val release = selected ?: throw IOException("release_not_found")
            val assets = selectedAssets ?: throw IOException("release_not_found")
            val tag = release.optString("tag_name").ifBlank { release.optString("name") }

            val manifestAsset = findAsset(assets, "manifest.json")
            val parsed = if (manifestAsset != null) {
                val manifestUrl = manifestAsset.optString("browser_download_url")
                runCatching { fetchManifest(repo, tag, manifestUrl) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val finalAssets = parsed.ifEmpty { parseAssetsFallback(repo, tag, assets) }
            if (finalAssets.isEmpty()) throw IOException("release_not_found")

            Release(
                tag = tag,
                title = release.optString("name").ifBlank { tag },
                publishedAt = release.optString("published_at"),
                assets = finalAssets,
            )
        }

    private fun containsInstallableAsset(assets: JSONArray): Boolean {
        for (index in 0 until assets.length()) {
            val name = assets.optJSONObject(index)?.optString("name").orEmpty()
            if (name.endsWith(".apk", ignoreCase = true) || name == "manifest.json") return true
        }
        return false
    }

    private fun findAsset(assets: JSONArray, name: String): JSONObject? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name").equals(name, ignoreCase = true)) return asset
        }
        return null
    }

    private fun fetchManifest(repo: String, tag: String, url: String): List<ReleaseAsset> {
        val response = Http.get(
            url = url,
            accept = "application/json",
            timeoutMs = 20_000,
        )
        if (response.code !in 200..299 || response.body == null) {
            throw IOException("http_${response.code}")
        }
        return parseManifest(repo, tag, response.body!!)
    }

    private fun parseManifest(repo: String, tag: String, body: String): List<ReleaseAsset> {
        val root = JSONObject(body)
        val array = root.optJSONArray("assets") ?: return emptyList()
        val result = mutableListOf<ReleaseAsset>()

        for (index in 0 until array.length()) {
            val asset = array.optJSONObject(index) ?: continue
            val fileName = asset.optString("file").ifBlank { asset.optString("name") }
            if (!fileName.endsWith(".apk", ignoreCase = true)) continue

            val packageName = asset.optString("package").ifBlank {
                AppRegistry.byAssetName(fileName)?.packageName.orEmpty()
            }
            if (packageName.isBlank()) continue

            val abis = asset.optJSONArray("abis")?.let { abisArray ->
                buildList {
                    for (abiIndex in 0 until abisArray.length()) {
                        val abi = abisArray.optString(abiIndex)
                        if (abi.isNotBlank()) add(abi)
                    }
                }
            }.orEmpty()

            result += ReleaseAsset(
                fileName = fileName,
                url = releaseDownloadUrl(repo, tag, fileName),
                packageName = packageName,
                label = asset.optString("label").ifBlank {
                    AppRegistry.displayName(packageName)
                },
                versionName = asset.optString("versionName"),
                versionCode = asset.optLong("versionCode", 0L),
                minSdk = asset.optInt("minSdk", 0).takeIf { it > 0 },
                targetSdk = asset.optInt("targetSdk", 0).takeIf { it > 0 },
                abis = abis,
                size = asset.optLong("size", 0L),
                sha256 = asset.optString("sha256").takeIf { it.isNotBlank() },
                signerSha256 = asset.optString("signerSha256").takeIf { it.isNotBlank() },
                iconUrl = asset.optString("icon")
                    .takeIf { it.isNotBlank() }
                    ?.let { releaseDownloadUrl(repo, tag, it) },
            )
        }

        return result
    }

    private fun parseAssetsFallback(repo: String, tag: String, assets: JSONArray): List<ReleaseAsset> {
        val result = mutableListOf<ReleaseAsset>()

        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val fileName = asset.optString("name")
            if (!fileName.endsWith(".apk", ignoreCase = true)) continue

            val known = AppRegistry.byAssetName(fileName) ?: continue
            val match = VERSION_ARCH.find(fileName)
            val versionName = match?.groupValues?.getOrNull(1).orEmpty()
            val arch = match?.groupValues?.getOrNull(2).orEmpty()
            val abis = when {
                arch.isBlank() -> emptyList()
                arch.equals("universal", ignoreCase = true) -> emptyList()
                arch.equals("all", ignoreCase = true) -> emptyList()
                else -> listOf(arch)
            }

            result += ReleaseAsset(
                fileName = fileName,
                url = asset.optString("browser_download_url")
                    .ifBlank { releaseDownloadUrl(repo, tag, fileName) },
                packageName = known.packageName,
                label = known.displayName,
                versionName = versionName,
                versionCode = 0L,
                minSdk = null,
                targetSdk = null,
                abis = abis,
                size = asset.optLong("size", 0L),
                sha256 = null,
                signerSha256 = null,
                iconUrl = null,
            )
        }

        return result
    }

    private fun releaseDownloadUrl(repo: String, tag: String, fileName: String): String {
        val encodedTag = tag.split('/').joinToString("/") { encode(it) }
        return "https://github.com/$repo/releases/download/$encodedTag/${encode(fileName)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
