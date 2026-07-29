package com.zhique.runtime.capability

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

object NetworkRequestPolicy {
    private const val maxBodyBytes = 512 * 1024
    private const val maxResponseBytes = 1024 * 1024
    private val blockedHeaders = setOf("cookie", "host", "content-length")

    fun requireHttps(raw: String): URI {
        val uri = runCatching { URI(raw).normalize() }.getOrElse { throw IllegalArgumentException("url must be a valid HTTPS URL.") }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Only HTTPS URLs are allowed."
        }
        require(uri.userInfo.isNullOrBlank()) { "URLs with embedded credentials are not allowed." }
        return uri
    }

    fun headers(raw: Map<String, Any?>?): Map<String, String> = raw.orEmpty().mapNotNull { (name, value) ->
        val normalized = name.trim()
        if (normalized.isEmpty() || normalized.lowercase() in blockedHeaders || value !is String) null
        else normalized to value.take(8 * 1024)
    }.toMap()

    fun requireBody(raw: Any?): ByteArray? = when (raw) {
        null -> null
        is String -> raw.toByteArray(Charsets.UTF_8).also { bytes ->
            require(bytes.size <= maxBodyBytes) { "Request body exceeds 512 KB." }
        }
        else -> throw IllegalArgumentException("body must be a string.")
    }

    fun readBounded(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..399) connection.inputStream else connection.errorStream
            ?: return ""
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                require(output.size() + read <= maxResponseBytes) { "Response exceeds 1 MB." }
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}

class NetworkCapabilityHandler(context: Context) : RuntimeCapabilityHandler {
    override val capabilityId = "network"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf(
        "network.status",
        "network.request",
        "network.download"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "network.status" -> status()
        "network.request" -> request(params)
        "network.download" -> download(params)
        else -> throw IllegalArgumentException("Unsupported network method: $method")
    }

    private fun status(): Map<String, Any?> {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return mapOf("connected" to false, "validated" to false, "transport" to null)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return mapOf("connected" to false, "validated" to false, "transport" to null)
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }
        return mapOf(
            "connected" to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            "validated" to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            "transport" to transport
        )
    }

    private suspend fun request(params: Map<String, Any?>): Map<String, Any?> = withContext(Dispatchers.IO) {
        val uri = NetworkRequestPolicy.requireHttps(params.requiredString("url"))
        val method = (params["method"] as? String ?: "GET").uppercase()
        require(method in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")) { "Unsupported HTTP method." }
        val body = NetworkRequestPolicy.requireBody(params["body"])
        val headers = NetworkRequestPolicy.headers(params["headers"].stringMap())
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = ((params["timeoutMs"] as? Number)?.toInt() ?: 15_000).coerceIn(1_000, 60_000)
            readTimeout = connectTimeout
            instanceFollowRedirects = false
            headers.forEach(::setRequestProperty)
            if (body != null) {
                doOutput = true
                outputStream.use { output -> output.write(body) }
            }
        }
        try {
            val statusCode = connection.responseCode
            mapOf(
                "status" to statusCode,
                "ok" to (statusCode in 200..299),
                "body" to NetworkRequestPolicy.readBounded(connection),
                "contentType" to connection.contentType
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun download(params: Map<String, Any?>): Map<String, Long> {
        val uri = NetworkRequestPolicy.requireHttps(params.requiredString("url"))
        val name = (params["fileName"] as? String)?.trim().orEmpty().ifBlank { "zhique-download" }.take(120)
        val request = DownloadManager.Request(Uri.parse(uri.toString()))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, name)
        val manager = appContext.getSystemService(DownloadManager::class.java)
        return mapOf("downloadId" to manager.enqueue(request))
    }
}

private fun Map<String, Any?>.requiredString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")

private fun Any?.stringMap(): Map<String, Any?>? = (this as? Map<*, *>)?.mapNotNull { (key, value) ->
    (key as? String)?.let { it to value }
}?.toMap()
