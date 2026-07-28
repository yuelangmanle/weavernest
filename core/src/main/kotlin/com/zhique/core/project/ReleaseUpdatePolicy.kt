package com.zhique.core.project

object ReleaseUpdatePolicy {
    fun isNewer(currentVersion: String, releaseTag: String): Boolean =
        parse(releaseTag) > parse(currentVersion)

    fun selectApkAsset(assetNames: List<String>): String? = assetNames.firstOrNull {
        it.endsWith(".apk", ignoreCase = true)
    }

    private fun parse(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { segment -> segment.toIntOrNull() ?: 0 }
        .let { values -> (values + listOf(0, 0, 0)).take(3) }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        indices.forEach { index ->
            val comparison = this[index].compareTo(other[index])
            if (comparison != 0) return comparison
        }
        return 0
    }
}
