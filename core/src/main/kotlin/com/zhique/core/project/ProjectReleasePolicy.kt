package com.zhique.core.project

import java.util.UUID

data class ProjectMetadata(
    val id: String,
    val displayName: String,
    val packageName: String,
    val packageNameLocked: Boolean = false,
    val versionName: String = "1.0.0",
    val versionCode: Int = 0,
    val capabilities: Set<String> = emptySet(),
    val promptPackVersion: String = "1"
) {
    companion object {
        fun create(displayName: String, packageName: String): ProjectMetadata {
            require(displayName.isNotBlank()) { "Application name is required." }
            PackageNamePolicy.requireValid(packageName)
            return ProjectMetadata(
                id = UUID.randomUUID().toString(),
                displayName = displayName.trim(),
                packageName = packageName
            )
        }
    }
}

class PackageNameLockedException : IllegalStateException(
    "The package name is locked after the first export. Duplicate the project to create a new application."
)

object ProjectReleasePolicy {
    fun prepareExport(project: ProjectMetadata): ProjectMetadata {
        PackageNamePolicy.requireValid(project.packageName)
        require(project.versionCode >= 0) { "Version code cannot be negative." }
        require(project.versionName.isNotBlank()) { "Display version is required." }

        return project.copy(
            packageNameLocked = true,
            versionCode = project.versionCode + 1
        )
    }

    fun changePackageName(project: ProjectMetadata, packageName: String): ProjectMetadata {
        if (project.packageNameLocked) {
            throw PackageNameLockedException()
        }
        PackageNamePolicy.requireValid(packageName)
        return project.copy(packageName = packageName)
    }
}

object PackageNamePolicy {
    private val segment = Regex("[a-z][a-z0-9_]*")

    fun requireValid(packageName: String) {
        val parts = packageName.split('.')
        require(parts.size >= 2 && parts.all { segment.matches(it) }) {
            "Use at least two lowercase package-name segments, for example app.zhique.camera."
        }
    }
}
