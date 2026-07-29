package com.zhique.studio.features.editor

object CodeEditorFilePolicy {
    const val maxEditableBytes = 2 * 1024 * 1024

    fun isReadOnly(content: String): Boolean = content.toByteArray(Charsets.UTF_8).size > maxEditableBytes

    fun languageFor(path: String): String = when {
        path.endsWith(".css", ignoreCase = true) -> "css"
        path.endsWith(".js", ignoreCase = true) || path.endsWith(".mjs", ignoreCase = true) -> "javascript"
        path.endsWith(".json", ignoreCase = true) -> "json"
        else -> "html"
    }
}
