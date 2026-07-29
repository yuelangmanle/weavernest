package com.zhique.core.project

data class ImportSuggestion(
    val fileName: String,
    val matchedApi: String,
    val replacement: String,
    val description: String
)

data class UnknownRuntimeMethod(
    val fileName: String,
    val method: String
)

data class UnavailableRuntimeMethod(
    val fileName: String,
    val method: String
)

data class ImportManifestError(
    val fileName: String,
    val message: String
)

data class ImportAnalysis(
    val suggestedCapabilities: Set<String>,
    val suggestions: List<ImportSuggestion>,
    val detectedRuntimeMethods: Set<String> = emptySet(),
    val unknownRuntimeMethods: List<UnknownRuntimeMethod> = emptyList(),
    val unavailableRuntimeMethods: List<UnavailableRuntimeMethod> = emptyList(),
    val unknownDeclaredCapabilities: Set<String> = emptySet(),
    val manifestErrors: List<ImportManifestError> = emptyList()
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
            "weaver.bluetooth.scan(options, listener)",
            "Use the Android BLE scan and explicit connect flow instead of the browser-only chooser."
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
        "mic" to "microphone",
        "clipboard" to "clipboard",
        "vibrate" to "haptics",
        "haptics" to "haptics",
        "sensor" to "sensors",
        "sensors" to "sensors",
        "config" to "config",
        "photos" to "media_images",
        "images" to "media_images",
        "album" to "media_images",
        "video" to "media_video",
        "audio" to "media_audio",
        "speaker" to "media_audio",
        "bluetooth" to "bluetooth_le",
        "ble" to "bluetooth_le",
        "wifi" to "wifi_scan",
        "hotspot" to "local_hotspot",
        "nfc" to "nfc",
        "usb" to "usb",
        "calendar" to "calendar",
        "biometrics" to "biometric",
        "network" to "network"
    )

    private val requiredCapabilityComment = Regex("<!--\\s*weaver-required\\s*:\\s*([^>]+?)-->", RegexOption.IGNORE_CASE)
    private val runtimeCall = Regex("(?:window\\.)?weaver(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+\\s*(?=\\()")

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
        val detectedRuntimeMethods = linkedSetOf<String>()
        val unknownRuntimeMethods = mutableListOf<UnknownRuntimeMethod>()
        val unavailableRuntimeMethods = mutableListOf<UnavailableRuntimeMethod>()
        files.forEach { (fileName, content) ->
            runtimeCall.findAll(content).forEach { match ->
                val method = match.value.trim().let { call ->
                    if (call.startsWith("window.weaver.")) {
                        if (call == "window.weaver.ready") call else call.removePrefix("window.")
                    } else {
                        val suffix = call.removePrefix("weaver.")
                        if (suffix == "ready") "window.weaver.ready" else "weaver.$suffix"
                    }
                }
                detectedRuntimeMethods += method
                val api = RuntimeApiCatalog.methods.firstOrNull { candidate -> candidate.name == method }
                when {
                    api == null -> unknownRuntimeMethods.add(UnknownRuntimeMethod(fileName, method))
                    !RuntimeApiCatalog.isImplemented(method) -> unavailableRuntimeMethods.add(UnavailableRuntimeMethod(fileName, method))
                    api.capabilityId != null -> suggestedCapabilities += api.capabilityId
                }
            }
        }
        val unknownDeclaredCapabilities = linkedSetOf<String>()
        val manifestErrors = mutableListOf<ImportManifestError>()
        fun addDeclaredCapability(declared: String) {
            val capability = declaredCapabilityAliases[declared]
                ?: CapabilityRegistry.canonicalId(declared)
            if (capability == null) unknownDeclaredCapabilities += declared
            else suggestedCapabilities += capability
        }
        files.values.forEach { content ->
            requiredCapabilityComment.findAll(content).forEach { match ->
                match.groupValues[1]
                    .split(',')
                    .map(String::trim)
                    .map(String::lowercase)
                    .filter(String::isNotBlank)
                    .forEach(::addDeclaredCapability)
            }
        }
        files.filterKeys { name -> name.substringAfterLast('/').equals("weaver.json", ignoreCase = true) }
            .forEach { (fileName, content) ->
                val manifest = WeaverManifest.parse(content)
                manifest.error?.let { message -> manifestErrors += ImportManifestError(fileName, message) }
                manifest.capabilities.forEach(::addDeclaredCapability)
            }
        return ImportAnalysis(
            suggestedCapabilities = suggestedCapabilities,
            suggestions = suggestions,
            detectedRuntimeMethods = detectedRuntimeMethods,
            unknownRuntimeMethods = unknownRuntimeMethods.distinct(),
            unavailableRuntimeMethods = unavailableRuntimeMethods.distinct(),
            unknownDeclaredCapabilities = unknownDeclaredCapabilities,
            manifestErrors = manifestErrors
        )
    }
}
