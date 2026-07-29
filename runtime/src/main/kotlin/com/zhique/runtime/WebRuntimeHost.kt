package com.zhique.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.zhique.runtime.bridge.RuntimeBridgeDispatcher
import com.zhique.runtime.bridge.RuntimeBridgeError
import com.zhique.runtime.bridge.RuntimeBridgeResponse
import com.zhique.runtime.bridge.RuntimeDataStore
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.bridge.RuntimeBridgeEvent
import com.zhique.runtime.bridge.RuntimeMessageCodec
import com.zhique.runtime.bridge.RuntimeOriginPolicy
import com.zhique.runtime.bridge.RuntimeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.Locale

const val runtimeDomain = "zhique.local"

fun runtimeDomainFor(projectId: String): String {
    val label = projectId.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit).take(48).ifBlank { "project" }
    return "$label.$runtimeDomain"
}

fun runtimeOriginFor(projectId: String): String = "https://${runtimeDomainFor(projectId)}"

data class RuntimeProject(
    val id: String,
    val files: Map<String, String>,
    val binaryAssets: Map<String, String> = emptyMap()
) {
    fun entryHtml(needsFallbackBootstrap: Boolean, sessionId: String): String {
        val source = files["index.html"].orEmpty().ifBlank { "<html><body><p>index.html was not found.</p></body></html>" }
        val additions = buildString {
            if ("style.css" !in source && files["style.css"] != null) append("<style>${files.getValue("style.css")}</style>")
            if ("app.js" !in source && files["app.js"] != null) append("<script>${files.getValue("app.js")}</script>")
        }
        val document = if (additions.isEmpty()) source else when {
            source.contains("</body>", ignoreCase = true) -> source.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$additions</body>")
            else -> "$source$additions"
        }
        return if (needsFallbackBootstrap) RuntimeBootstrap.injectIntoHtml(document, sessionId) else document
    }
}

interface RuntimeHostCallbacks {
    fun onPageReady()
    fun onLog(message: String, isError: Boolean)
    fun onBlockedNavigation(url: String)
}

class SharedPreferencesRuntimeDataStore(context: Context) : RuntimeDataStore {
    private val appContext = context.applicationContext

    override fun get(projectId: String, key: String): String? = preferences(projectId).getString(key, null)

    override fun set(projectId: String, key: String, value: String) {
        preferences(projectId).edit().putString(key, value).apply()
    }

    override fun remove(projectId: String, key: String) {
        preferences(projectId).edit().remove(key).apply()
    }

    override fun clear(projectId: String) {
        preferences(projectId).edit().clear().apply()
    }

    /** Snapshot only public weaver.data values; encrypted weaver.config values are never included. */
    fun snapshot(projectId: String): Map<String, String> = preferences(projectId).all
        .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
        .toMap()

    fun replace(projectId: String, values: Map<String, String>) {
        val editor = preferences(projectId).edit().clear()
        values.forEach { (key, value) -> editor.putString(key, value) }
        require(editor.commit()) {
            "Android could not restore the project data values."
        }
    }

    private fun preferences(projectId: String) = appContext.getSharedPreferences(
        "zhique_runtime_preview_$projectId",
        Context.MODE_PRIVATE
    )
}

/**
 * Owns a single trusted WebView session. A session rebuilds the WebView so document-start code
 * always carries the active session id and stale documents cannot send a later native request.
 */
class WebRuntimeHost(
    context: Context,
    private val dispatcher: RuntimeBridgeDispatcher,
    private val eventBus: RuntimeEventBus,
    private val callbacks: RuntimeHostCallbacks
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val container = FrameLayout(context)

    private var activeSession: RuntimeSession? = null
    private var activeProject: RuntimeProject? = null
    private var activeOrigin: String? = null
    private var assetLoader: WebViewAssetLoader? = null
    private var webView: WebView? = null

    init {
        eventBus.attach(::deliverEvent)
    }

    fun load(project: RuntimeProject, session: RuntimeSession) {
        activeSession?.id?.let(dispatcher::releaseSession)
        closeWebView()
        activeProject = project
        activeSession = session
        val origin = runtimeOriginFor(project.id)
        activeOrigin = origin
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(runtimeDomainFor(project.id))
            .addPathHandler("/project/", ProjectPathHandler(project, session.id))
            .build()
        val nextWebView = createWebView(session, origin)
        webView = nextWebView
        container.addView(nextWebView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        nextWebView.loadUrl("$origin/project/${project.id}/index.html")
    }

    fun stop() {
        activeSession?.id?.let(dispatcher::releaseSession)
        closeWebView()
        activeSession = null
        activeProject = null
        activeOrigin = null
        assetLoader = null
    }

    fun close() {
        stop()
        eventBus.detach()
        scope.cancel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createWebView(session: RuntimeSession, trustedOrigin: String): WebView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                this,
                "ZhiqueRuntime",
                setOf(trustedOrigin),
                WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
                    receiveFromPage(message.data.orEmpty(), sourceOrigin.toString(), isMainFrame)
                }
            )
        } else {
            callbacks.onLog("当前 WebView 不支持安全运行时消息桥；原生能力调用已禁用。", true)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, RuntimeBootstrap.documentStartScript(session.id), setOf(trustedOrigin))
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                callbacks.onLog(
                    "JS: ${consoleMessage.message()} (${consoleMessage.lineNumber()})",
                    consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                )
                return true
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader?.shouldInterceptRequest(request.url) ?: super.shouldInterceptRequest(view, request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("$trustedOrigin/project/")) false else {
                    callbacks.onBlockedNavigation(url)
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("$trustedOrigin/project/")) callbacks.onPageReady()
            }
        }
    }

    private fun receiveFromPage(raw: String, sourceOrigin: String, isMainFrame: Boolean) {
        val parsed = RuntimeMessageCodec.parseRequest(raw)
        val request = parsed.request
        if (request == null) {
            parsed.requestId?.let { deliver(RuntimeBridgeResponse(it, error = parsed.error ?: invalidRequest())) }
            return
        }
        scope.launch {
            val session = activeSession
            val trustedOrigin = RuntimeOriginPolicy.accepts(sourceOrigin, activeOrigin, isMainFrame)
            val response = when {
                session == null || !trustedOrigin -> RuntimeBridgeResponse(
                    request.requestId,
                    error = RuntimeBridgeError("RUNTIME_NOT_READY", "The preview session is not trusted or has stopped.", recoverable = true, action = "reload")
                )
                else -> dispatcher.dispatch(request, session)
            }
            deliver(response)
        }
    }

    private fun deliver(response: RuntimeBridgeResponse) {
        val currentWebView = webView ?: return
        val payload = RuntimeMessageCodec.responseJson(response)
        currentWebView.evaluateJavascript("window.__weaverResolve(${JSONObject.quote(payload)});", null)
    }

    private fun deliverEvent(event: RuntimeBridgeEvent) {
        val currentWebView = webView ?: return
        val payload = RuntimeMessageCodec.eventJson(event)
        currentWebView.evaluateJavascript("window.__weaverEvent(${JSONObject.quote(payload)});", null)
    }

    private fun closeWebView() {
        webView?.let { view ->
            view.stopLoading()
            container.removeView(view)
            view.destroy()
        }
        webView = null
    }

    private fun invalidRequest() = RuntimeBridgeError("INVALID_ARGUMENT", "Invalid Runtime request.")

    private class ProjectPathHandler(
        private val project: RuntimeProject,
        private val sessionId: String
    ) : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val relativePath = path.substringAfter("${project.id}/", "index.html").substringBefore('?')
            if (relativePath.split('/').any { it == ".." }) return null
            val textContent = when (relativePath) {
                "", "index.html" -> project.entryHtml(
                    needsFallbackBootstrap = !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT),
                    sessionId = sessionId
                )
                else -> project.files[relativePath]
            }
            if (textContent != null) {
                return WebResourceResponse(
                    mimeType(relativePath),
                    "UTF-8",
                    ByteArrayInputStream(textContent.toByteArray(Charsets.UTF_8))
                )
            }
            val asset = project.binaryAssets[relativePath] ?: return null
            val bytes = runCatching { Base64.getDecoder().decode(asset) }.getOrNull() ?: return null
            return WebResourceResponse(mimeType(relativePath), null, ByteArrayInputStream(bytes))
        }

        private fun mimeType(path: String): String = when {
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "text/javascript"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".pdf") -> "application/pdf"
            path.endsWith(".html") || path.isEmpty() -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
