package com.zhique.studio.features.editor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

enum class CodeEditorAction { Undo, Redo, FindReplace }

data class CodeEditorCommand(
    val id: Long,
    val action: CodeEditorAction
)

@Composable
fun CodeEditorView(
    path: String,
    content: String,
    onContentChange: (String) -> Unit,
    command: CodeEditorCommand? = null,
    modifier: Modifier = Modifier
) {
    val currentOnContentChange by rememberUpdatedState(onContentChange)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LocalCodeEditorWebView(context).apply {
                this.onContentChange = { value -> currentOnContentChange(value) }
            }
        },
        update = { editor ->
            editor.onContentChange = { value -> currentOnContentChange(value) }
            editor.setDocument(path, content)
            command?.let(editor::runCommand)
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION") // Keep file-URL cross-origin access explicitly disabled on all supported Android versions.
private class LocalCodeEditorWebView(context: Context) : WebView(context) {
    var onContentChange: (String) -> Unit = {}
    private var isLoaded = false
    private var currentPath: String? = null
    private var currentContent: String? = null
    private var currentReadOnly = false
    private var lastCommandId = Long.MIN_VALUE

    init {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.domStorageEnabled = false
        addJavascriptInterface(EditorBridge(), "ZhiqueEditor")
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String) {
                isLoaded = true
                renderDocument(force = true)
            }
        }
        loadUrl("file:///android_asset/editor/editor.html")
    }

    fun setDocument(path: String, content: String) {
        val changedPath = currentPath != path
        val changedContent = currentContent != content
        val readOnly = CodeEditorFilePolicy.isReadOnly(content)
        val changedReadOnly = currentReadOnly != readOnly
        currentPath = path
        currentContent = content
        currentReadOnly = readOnly
        if (isLoaded && (changedPath || changedContent || changedReadOnly)) renderDocument(force = changedPath || changedReadOnly)
    }

    fun runCommand(command: CodeEditorCommand) {
        if (!isLoaded || command.id <= lastCommandId) return
        lastCommandId = command.id
        val name = when (command.action) {
            CodeEditorAction.Undo -> "undo"
            CodeEditorAction.Redo -> "redo"
            CodeEditorAction.FindReplace -> "find"
        }
        evaluateJavascript("window.ZhiqueCodeEditor.command(${JSONObject.quote(name)});", null)
    }

    private fun renderDocument(force: Boolean) {
        val path = currentPath ?: return
        val content = currentContent ?: ""
        val language = CodeEditorFilePolicy.languageFor(path)
        val script = if (force) {
            "window.ZhiqueCodeEditor.setDocument(${JSONObject.quote(content)}, ${JSONObject.quote(language)}, $currentReadOnly);"
        } else {
            "window.ZhiqueCodeEditor.setValue(${JSONObject.quote(content)});"
        }
        evaluateJavascript(script, null)
    }

    private inner class EditorBridge {
        @JavascriptInterface
        fun onChange(value: String) {
            post {
                currentContent = value
                onContentChange(value)
            }
        }
    }
}
