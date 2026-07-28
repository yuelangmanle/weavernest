package com.zhique.studio.preview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zhique.core.project.ProjectDocument
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun WeaverPreview(document: ProjectDocument, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> createWebView(context) },
        update = { webView ->
            webView.removeJavascriptInterface("ZhiqueNative")
            webView.addJavascriptInterface(
                PreviewBridge(context = webView.context, document = document),
                "ZhiqueNative"
            )
            webView.loadDataWithBaseURL(
                "https://zhique.local/${document.metadata.id}/",
                document.previewHtml(),
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mediaPlaybackRequiresUserGesture = true
    webViewClient = WebViewClient()
    webChromeClient = WebChromeClient()
}

private class PreviewBridge(
    context: Context,
    private val document: ProjectDocument
) {
    private val preferences = context.getSharedPreferences("preview_${document.metadata.id}", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun capabilities(): String = JSONArray(document.metadata.capabilities.toList()).toString()

    @JavascriptInterface
    fun dataGet(key: String): String = preferences.getString(key, null)?.let { value ->
        JSONObject().put("found", true).put("value", value).toString()
    } ?: JSONObject().put("found", false).toString()

    @JavascriptInterface
    fun dataSet(key: String, value: String): Boolean {
        preferences.edit().putString(key, value).apply()
        return true
    }

    @JavascriptInterface
    fun requestCapability(capability: String): String {
        if (capability !in document.metadata.capabilities) {
            return JSONObject().put("ok", false).put("error", "项目未启用 $capability 能力。").toString()
        }
        return JSONObject()
            .put("ok", false)
            .put("error", "$capability 需要 Android 原生模块和系统授权；请在能力页查看要求。")
            .toString()
    }
}

private fun ProjectDocument.previewHtml(): String {
    val source = files["index.html"].orEmpty().ifBlank { "<html><body><p>未找到 index.html</p></body></html>" }
    val style = files["style.css"].orEmpty()
    val script = files["app.js"].orEmpty()
    val bootstrap = """
        <script>
        (function () {
          const native = window.ZhiqueNative;
          const parse = (value) => JSON.parse(value);
          const request = async (capability) => {
            const result = parse(native.requestCapability(capability));
            if (!result.ok) throw new Error(result.error);
            return result;
          };
          window.weaver = Object.freeze({
            apiVersion: '1',
            capabilities: () => parse(native.capabilities()),
            request,
            data: {
              get: (key) => { const result = parse(native.dataGet(String(key))); return result.found ? result.value : null; },
              set: (key, value) => native.dataSet(String(key), String(value))
            },
            config: { get: () => null },
            camera: { capture: () => request('camera') },
            files: { pick: () => request('files') },
            location: { getCurrent: () => request('location') },
            bluetooth: { requestDevice: () => request('bluetooth_le') },
            network: { request: () => request('network') },
            notifications: { requestPermission: () => request('notifications') }
          });
        }());
        </script>
    """.trimIndent()
    val styleTag = if (style.isBlank()) "" else "<style>$style</style>"
    val scriptTag = if (script.isBlank()) "" else "<script>$script</script>"
    val withHead = if (source.contains("</head>", ignoreCase = true)) source.replace("</head>", "$styleTag$bootstrap</head>", true) else "$styleTag$bootstrap$source"
    return if (withHead.contains("</body>", ignoreCase = true)) withHead.replace("</body>", "$scriptTag</body>", true) else "$withHead$scriptTag"
}
