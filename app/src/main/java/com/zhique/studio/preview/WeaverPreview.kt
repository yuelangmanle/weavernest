package com.zhique.studio.preview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zhique.core.project.ProjectDocument
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeaverPreview(
    document: ProjectDocument,
    runToken: Long,
    isRunning: Boolean,
    onReady: () -> Unit,
    onLog: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestReady by rememberUpdatedState(onReady)
    val latestLog by rememberUpdatedState(onLog)
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            createWebView(
                context = context,
                onReady = { latestReady() },
                onLog = { message, isError -> latestLog(message, isError) }
            ).also { webView = it }
        }
    )

    LaunchedEffect(document.metadata.id, runToken, isRunning) {
        val target = webView ?: return@LaunchedEffect
        if (!isRunning) {
            target.loadUrl("about:blank")
            return@LaunchedEffect
        }
        target.removeJavascriptInterface("ZhiqueNative")
        target.addJavascriptInterface(PreviewBridge(target.context, document), "ZhiqueNative")
        target.loadDataWithBaseURL(
            "https://zhique.local/${document.metadata.id}/",
            document.previewHtml(),
            "text/html",
            "UTF-8",
            null
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    onReady: () -> Unit,
    onLog: (String, Boolean) -> Unit
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            onLog("JS: ${consoleMessage.message()} (${consoleMessage.lineNumber()})", consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
            return true
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            onReady()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onLog("预览加载失败：${error.description}", true)
        }
    }
}

private class PreviewBridge(
    context: Context,
    private val document: ProjectDocument
) {
    private val preferences = context.getSharedPreferences("preview_${document.metadata.id}", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun capabilities(): String = JSONArray(document.metadata.capabilities.toList()).toString()

    @JavascriptInterface
    fun dataGet(key: String): String? = preferences.getString(key, null)

    @JavascriptInterface
    fun dataSet(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    fun unavailable(capability: String): String = JSONObject().apply {
        put("ok", false)
        put("error", "${capability} native module is not available in this Alpha preview.")
    }.toString()
}

private fun ProjectDocument.previewHtml(): String {
    val source = files["index.html"].orEmpty().ifBlank { "<html><body><p>未找到 index.html</p></body></html>" }
    val style = files["style.css"].orEmpty()
    val script = files["app.js"].orEmpty()
    val bridge = """
        <script>
        window.weaver = window.weaver || {};
        window.weaver.capabilities = () => JSON.parse(window.ZhiqueNative.capabilities());
        window.weaver.data = {
          get: (key) => window.ZhiqueNative.dataGet(String(key)),
          set: (key, value) => window.ZhiqueNative.dataSet(String(key), String(value))
        };
        ['camera','media','files','location','bluetooth','wifi','hotspot','notifications','system'].forEach((name) => {
          window.weaver[name] = window.weaver[name] || { unavailable: () => JSON.parse(window.ZhiqueNative.unavailable(name)) };
        });
        </script>
    """.trimIndent()
    val additions = "<style>$style</style>$bridge<script>$script</script>"
    return if (source.contains("</body>", ignoreCase = true)) {
        source.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$additions</body>")
    } else {
        "$source$additions"
    }
}
