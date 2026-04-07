package org.akuatech.ksupatcher.network

import org.akuatech.ksupatcher.data.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubReleaseRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchLatestTag(owner: String, repo: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${owner}/${repo}/releases/latest"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "ksupatcher")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Release check failed: ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty response")
                val release = JSONObject(body)
                release.optString("tag_name").ifBlank {
                    error("Missing tag name")
                }
            }
        }
    }

    suspend fun fetchAppUpdateInfo(owner: String, repo: String, currentBuildHash: String): Result<AppUpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.github.com/repos/${owner}/${repo}/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "ksupatcher")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Release check failed: ${response.code}")
                    }
                    val body = response.body?.string() ?: error("Empty response")
                    val release = JSONObject(body)
                    val latestReleaseHash = release.optString("tag_name").ifBlank {
                        error("Missing tag name")
                    }
                    val assets = release.optJSONArray("assets") ?: JSONArray()
                    val apkAsset = findAsset(assets, ".apk")
                    val checksumAsset = findAsset(assets, ".apk.sha256")
                    AppUpdateInfo(
                        currentBuildHash = currentBuildHash,
                        latestReleaseHash = latestReleaseHash,
                        isUpdateAvailable = latestReleaseHash != currentBuildHash,
                        apkAssetName = apkAsset?.optString("name")?.ifBlank { null },
                        apkDownloadUrl = apkAsset?.optString("browser_download_url")?.ifBlank { null },
                        checksumDownloadUrl = checksumAsset?.optString("browser_download_url")?.ifBlank { null },
                        releaseUrl = release.optString("html_url").ifBlank { null },
                        publishedAt = release.optString("published_at").ifBlank { null },
                        notes = release.optString("body").trim().ifBlank { null }
                    )
                }
            }
        }

    private fun findAsset(assets: JSONArray, suffix: String): JSONObject? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(suffix)) {
                return asset
            }
        }
        return null
    }
}
