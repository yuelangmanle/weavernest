package com.zhique.core.project

object ReleaseUpdatePolicy {
    fun isNewer(currentVersion: String, releaseTag: String): Boolean =
        parse(releaseTag) > parse(currentVersion)

    fun selectApkAsset(assetNames: List<String>): String? = assetNames.firstOrNull {
        it.endsWith(".apk", ignoreCase = true)
    }

    fun availability(currentVersion: String, releaseTag: String, apkName: String?): UpdateAvailability = when {
        !isNewer(currentVersion, releaseTag) -> UpdateAvailability.UpToDate
        apkName.isNullOrBlank() -> UpdateAvailability.PackageMissing
        else -> UpdateAvailability.DownloadAvailable
    }

    private fun parse(value: String): SemanticVersion {
        val normalized = value.trim().removePrefix("v")
        val numeric = normalized.substringBefore('-')
            .split('.')
            .map { segment -> segment.toIntOrNull() ?: 0 }
            .let { values -> (values + listOf(0, 0, 0)).take(3) }
        return SemanticVersion(numeric, normalized.substringAfter('-', missingDelimiterValue = "").ifBlank { null })
    }

    private data class SemanticVersion(
        val numeric: List<Int>,
        val prerelease: String?
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            numeric.indices.forEach { index ->
                val comparison = numeric[index].compareTo(other.numeric[index])
                if (comparison != 0) return comparison
            }
            return when {
                prerelease == null && other.prerelease != null -> 1
                prerelease != null && other.prerelease == null -> -1
                else -> prerelease.orEmpty().compareTo(other.prerelease.orEmpty(), ignoreCase = true)
            }
        }
    }
}

enum class UpdateAvailability {
    UpToDate,
    DownloadAvailable,
    PackageMissing
}
