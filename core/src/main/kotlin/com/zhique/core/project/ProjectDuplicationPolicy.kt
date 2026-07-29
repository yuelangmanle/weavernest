package com.zhique.core.project

/**
 * A duplicate is deliberately a different Android application. It may reuse source files and
 * non-secret assets, but never package-lock, signing, backup, or build-history identity.
 */
object ProjectDuplicationPolicy {
    fun duplicate(source: ProjectDocument, newProjectId: String): ProjectDocument {
        require(newProjectId.matches(PROJECT_ID)) { "Invalid copied project id." }
        require(newProjectId != source.metadata.id) { "A copied project needs a new project id." }

        val metadata = source.metadata.copy(
            id = newProjectId,
            displayName = "${source.metadata.displayName} 副本",
            packageName = copiedPackageName(source.metadata.packageName, newProjectId),
            packageNameLocked = false,
            versionName = "1.0.0",
            versionCode = 0,
            signingKeyId = null,
            signingCertificateSha256 = null,
            signingBackupId = null
        )
        return ProjectDocument(
            metadata = metadata,
            files = source.files,
            binaryAssets = source.binaryAssets
        )
    }

    private fun copiedPackageName(sourcePackageName: String, newProjectId: String): String {
        val suffix = newProjectId.lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .take(12)
            .ifBlank { "copy" }
        val packageName = "$sourcePackageName.copy$suffix"
        PackageNamePolicy.requireValid(packageName)
        return packageName
    }

    private val PROJECT_ID = Regex("[A-Za-z0-9-]{8,80}")
}
