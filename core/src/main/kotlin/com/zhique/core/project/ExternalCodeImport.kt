package com.zhique.core.project

enum class ExternalCodePart(val label: String) {
    Html("HTML"),
    Css("CSS"),
    JavaScript("JavaScript"),
    EmbeddedCss("内嵌 CSS"),
    EmbeddedJavaScript("内嵌 JavaScript")
}

data class ExternalCodeDraft(
    val projectName: String,
    val files: Map<String, String>,
    val detectedParts: Set<ExternalCodePart>,
    val analysis: ImportAnalysis
)

object ExternalCodeImport {
    fun prepare(projectName: String, source: String): ExternalCodeDraft {
        val content = source.trim()
        val isHtml = content.contains("<html", ignoreCase = true) ||
            content.startsWith("<!doctype", ignoreCase = true) ||
            content.startsWith("<body", ignoreCase = true)
        val isJavaScript = !isHtml && Regex("\\b(const|let|var|function|document|window)\\b|=>").containsMatchIn(content)
        val fileName = when {
            isHtml -> "index.html"
            isJavaScript -> "app.js"
            else -> "style.css"
        }
        val files = when (fileName) {
            "index.html" -> mapOf(fileName to content)
            "app.js" -> mapOf("index.html" to starterHtml("app.js"), fileName to content)
            else -> mapOf("index.html" to starterHtml("style.css"), fileName to content)
        }
        val parts = linkedSetOf<ExternalCodePart>().apply {
            when (fileName) {
                "index.html" -> add(ExternalCodePart.Html)
                "app.js" -> add(ExternalCodePart.JavaScript)
                else -> add(ExternalCodePart.Css)
            }
            if (isHtml && Regex("<style[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(content)) add(ExternalCodePart.EmbeddedCss)
            if (isHtml && Regex("<script[\\s>]", RegexOption.IGNORE_CASE).containsMatchIn(content)) add(ExternalCodePart.EmbeddedJavaScript)
        }
        return ExternalCodeDraft(
            projectName = projectName.trim().ifBlank { "导入项目" },
            files = files,
            detectedParts = parts,
            analysis = CodeImportAnalyzer.analyze(files)
        )
    }

    private fun starterHtml(resource: String): String = """
        <!doctype html>
        <html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1" />
        <link rel="stylesheet" href="style.css" /></head><body><main id="app"></main><script src="$resource"></script></body></html>
    """.trimIndent()
}

object ApilotPolicy {
    fun requestedScopes(includeApiKey: Boolean): List<String> = buildList {
        add("connection")
        add("models.default")
        if (includeApiKey) add("secret.api_key")
    }
}
