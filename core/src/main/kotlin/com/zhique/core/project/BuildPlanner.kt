package com.zhique.core.project

data class BuildPlan(
    /** The exact project identity that was validated before assembly starts. */
    val sourceProject: ProjectMetadata,
    /** The identity that may be committed only after a signed APK was written successfully. */
    val candidateProject: ProjectMetadata,
    /** SHA-256 summary of the files and assets that the APK was assembled from. */
    val sourceContentFingerprint: String? = null,
    val manifestPermissions: Set<String>,
    val specialSystemFlows: Set<String>,
    val assetPaths: Set<String>
) {
    /**
     * Compatibility alias for callers that render the candidate version in a build preview.
     * Assembly code must use [sourceProject] and explicitly commit [candidateProject] on success.
     */
    val project: ProjectMetadata
        get() = candidateProject
}

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
            sourceProject = project,
            candidateProject = ProjectReleasePolicy.prepareExport(project),
            manifestPermissions = validation.manifestPermissions,
            specialSystemFlows = validation.restrictedCapabilities,
            assetPaths = assetPaths
        )
    }

    fun prepare(document: ProjectDocument): BuildPlan = prepare(
        project = document.metadata,
        assetPaths = document.files.keys + document.binaryAssets.keys
    ).copy(sourceContentFingerprint = document.contentFingerprint())

    /**
     * Advances package-lock and version identity only after the assembler has produced a signed APK.
     * A plan cannot be applied to a different project revision.
     */
    fun commitSuccessfulAssembly(currentProject: ProjectMetadata, plan: BuildPlan): ProjectMetadata {
        require(currentProject == plan.sourceProject) {
            "Project changed while the APK was being assembled. Review and rebuild the current revision."
        }
        return plan.candidateProject
    }

    fun commitSuccessfulAssembly(currentDocument: ProjectDocument, plan: BuildPlan): ProjectDocument {
        require(currentDocument.metadata == plan.sourceProject) {
            "Project identity changed while the APK was being assembled. Review and rebuild the current revision."
        }
        plan.sourceContentFingerprint?.let { expectedFingerprint ->
            require(currentDocument.contentFingerprint() == expectedFingerprint) {
                "Project files changed while the APK was being assembled. Review and rebuild the current revision."
            }
        }
        return currentDocument.copy(metadata = plan.candidateProject)
    }
}
