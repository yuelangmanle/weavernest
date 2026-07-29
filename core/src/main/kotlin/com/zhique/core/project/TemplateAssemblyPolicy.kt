package com.zhique.core.project

/** Security boundary for the fixed Zhique template APK. */
object TemplateAssemblyPolicy {
    const val templateAssetPath = "template/zhique-template-runtime.apk"
    const val projectAssetsRoot = "assets/weaver/project/"

    fun projectAssetPath(projectPath: String): String {
        val normalized = projectPath.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && ':' !in normalized) {
            "Project asset path must be relative."
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Project asset path contains an unsafe segment."
        }
        require(normalized.length <= 240) { "Project asset path is too long." }
        return projectAssetsRoot + normalized
    }

    fun artifactFileName(candidate: ProjectMetadata): String {
        PackageNamePolicy.requireValid(candidate.packageName)
        require(candidate.versionCode > 0) { "A generated APK needs a positive version code." }
        require(candidate.versionName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
            "Version name may contain letters, numbers, dots, underscores, and hyphens only."
        }
        return "${candidate.packageName}-v${candidate.versionName}-${candidate.versionCode}.apk"
    }
}
