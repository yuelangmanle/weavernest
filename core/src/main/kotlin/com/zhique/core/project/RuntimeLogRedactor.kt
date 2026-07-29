package com.zhique.core.project

/**
 * Removes values which must never leave the device when runtime diagnostics are copied or sent to AI.
 * Keep this deliberately conservative: an over-redacted diagnostic remains useful, a leaked credential does not.
 */
object RuntimeLogRedactor {
    private const val REDACTED = "[redacted]"
    private const val REDACTED_LOCATION = "[redacted-location]"

    private val dataUrl = Regex("(?i)data:[^,\\s]{1,256},[^\\s]{0,}")
    private val contentOrFileUri = Regex("(?i)(?:content|file)://[^\\s'\\\"]+")
    private val bearer = Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+")
    private val namedSecret = Regex(
        "(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|secret)\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}&]+)"
    )
    private val coordinate = Regex(
        "(?<![\\d.])[-+]?\\d{1,2}\\.\\d{4,}\\s*[,，]\\s*[-+]?\\d{1,3}\\.\\d{4,}(?![\\d.])"
    )
    private val locationQuery = Regex("(?i)([?&](?:lat(?:itude)?|lon(?:gitude)?|lng)=)[^&#\\s]+")

    fun redact(message: String): String = message
        .replace(dataUrl, REDACTED)
        .replace(contentOrFileUri, REDACTED)
        .replace(bearer) { match -> "${match.groupValues[1]}$REDACTED" }
        .replace(namedSecret) { match -> "${match.groupValues[1]}$REDACTED" }
        .replace(coordinate, REDACTED_LOCATION)
        .replace(locationQuery) { match -> "${match.groupValues[1]}$REDACTED_LOCATION" }
}
