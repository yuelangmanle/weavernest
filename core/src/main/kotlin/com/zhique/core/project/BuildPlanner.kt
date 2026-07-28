package com.zhique.core.project

data class BuildPlan(
    val project: ProjectMetadata,
    val manifestPermissions: Set<String>,
    val specialSystemFlows: Set<String>,
    val assetPaths: Set<String>
)

class BuildValidationException(message: String) : IllegalArgumentException(message)

object BuildPlanner {
    fun prepare(project: ProjectMetadata, assetPaths: Set<String>): BuildPlan {
        require("index.html" in assetPaths) { "Every project needs an index.html entry point." }
        val validation = CapabilityRegistry.validate(project.capabilities)
        if (!validation.isAllowed) {
            throw BuildValidationException(
                "Unsupported capabilities: ${validation.unknownCapabilities.sorted().joinToString()}"
            )
        }
        return BuildPlan(
            project = ProjectReleasePolicy.prepareExport(project),
            manifestPermissions = validation.manifestPermissions,
            specialSystemFlows = validation.restrictedCapabilities,
            assetPaths = assetPaths
        )
    }
}
