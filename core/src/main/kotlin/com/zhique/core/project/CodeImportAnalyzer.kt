package com.zhique.core.project

data class ImportSuggestion(
    val fileName: String,
    val matchedApi: String,
    val replacement: String,
    val description: String
)

data class ImportAnalysis(
    val suggestedCapabilities: Set<String>,
    val suggestions: List<ImportSuggestion>
)

object CodeImportAnalyzer {
    private data class Rule(
        val expression: Regex,
        val capability: String,
        val replacement: String,
        val description: String
    )

    private val rules = listOf(
        Rule(
            Regex("navigator\\.geolocation\\.getCurrentPosition"),
            "geolocation",
            "weaver.geolocation.getCurrentPosition()",
            "Use the permission-aware 织雀 location bridge."
        ),
        Rule(
            Regex("navigator\\.bluetooth\\.requestDevice"),
            "bluetooth_le",
            "weaver.bluetooth.requestDevice()",
            "Use the Android BLE bridge instead of the browser-only API."
        ),
        Rule(
            Regex("navigator\\.mediaDevices\\.getUserMedia"),
            "camera",
            "weaver.camera.capture()",
            "Use the Android camera bridge so runtime permission state is reported consistently."
        ),
        Rule(
            Regex("showOpenFilePicker"),
            "storage",
            "weaver.storage.pickFile()",
            "Use the Android document picker bridge."
        ),
        Rule(
            Regex("Notification\\.requestPermission"),
            "notification",
            "weaver.notification.requestPermission()",
            "Use the Android notification permission bridge."
        )
    )

    private val declaredCapabilityAliases = mapOf(
        "camera" to "camera",
        "geolocation" to "geolocation",
        "location" to "geolocation",
        "storage" to "storage",
        "files" to "storage",
        "notification" to "notification",
        "notifications" to "notification",
        "contacts" to "contacts",
        "microphone" to "microphone",
        "clipboard" to "clipboard",
        "vibrate" to "haptics",
        "haptics" to "haptics",
        "sensor" to "sensors",
        "sensors" to "sensors",
        "config" to "config"
    )

    private val requiredCapabilityComment = Regex("<!--\\s*weaver-required\\s*:\\s*([^>]+?)-->", RegexOption.IGNORE_CASE)

    fun analyze(files: Map<String, String>): ImportAnalysis {
        val suggestions = buildList {
            files.forEach { (fileName, content) ->
                rules.filter { it.expression.containsMatchIn(content) }.forEach { rule ->
                    add(
                        ImportSuggestion(
                            fileName = fileName,
                            matchedApi = rule.expression.pattern,
                            replacement = rule.replacement,
                            description = rule.description
                        )
                    )
                }
            }
        }
        val suggestedCapabilities = suggestions.mapTo(linkedSetOf()) { suggestion ->
            rules.first { it.expression.pattern == suggestion.matchedApi }.capability
        }
        files.values.forEach { content ->
            requiredCapabilityComment.findAll(content).forEach { match ->
                match.groupValues[1]
                    .split(',')
                    .map(String::trim)
                    .map(String::lowercase)
                    .mapNotNull(declaredCapabilityAliases::get)
                    .forEach(suggestedCapabilities::add)
            }
        }
        return ImportAnalysis(
            suggestedCapabilities = suggestedCapabilities,
            suggestions = suggestions
        )
    }
}
