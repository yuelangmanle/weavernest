package com.zhique.studio.data

import com.zhique.core.project.ReleaseUpdatePolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkName: String?,
    val apkUrl: String?
)

class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun latest(repository: String): GitHubReleaseInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("GitHub 更新检查返回 ${response.code}。")
            val json = JSONObject(payload)
            val assets = json.optJSONArray("assets")
            val names = buildList {
                for (index in 0 until (assets?.length() ?: 0)) add(assets!!.getJSONObject(index).getString("name"))
            }
            val apkName = ReleaseUpdatePolicy.selectApkAsset(names)
            val apkUrl = assets?.let { values ->
                (0 until values.length())
                    .map { values.getJSONObject(it) }
                    .firstOrNull { it.getString("name") == apkName }
                    ?.getString("browser_download_url")
            }
            GitHubReleaseInfo(
                tagName = json.getString("tag_name"),
                releaseNotes = json.optString("body", "暂无更新说明。"),
                apkName = apkName,
                apkUrl = apkUrl
            )
        }
    }
}
