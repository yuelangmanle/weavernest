package com.zhique.core.project

data class BuildRecord(
    val versionName: String,
    val versionCode: Int,
    val createdAtEpochMillis: Long,
    val status: String,
    val message: String
)

data class ProjectDocument(
    val metadata: ProjectMetadata,
    val files: Map<String, String>,
    val binaryAssets: Map<String, String> = emptyMap(),
    val buildHistory: List<BuildRecord> = emptyList()
) {
    fun withFile(path: String, content: String): ProjectDocument = copy(
        files = files + (path to content)
    )
}
