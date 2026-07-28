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
            "location",
            "weaver.location.getCurrent()",
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
            "files",
            "weaver.files.pick()",
            "Use the Android document picker bridge."
        ),
        Rule(
            Regex("Notification\\.requestPermission"),
            "notifications",
            "weaver.notifications.requestPermission()",
            "Use the Android notification permission bridge."
        )
    )

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
        return ImportAnalysis(
            suggestedCapabilities = suggestions.mapTo(linkedSetOf()) { suggestion ->
                rules.first { it.expression.pattern == suggestion.matchedApi }.capability
            },
            suggestions = suggestions
        )
    }
}
