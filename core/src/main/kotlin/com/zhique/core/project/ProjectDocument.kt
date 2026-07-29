package com.zhique.core.project

import java.security.MessageDigest

data class BuildRecord(
    val versionName: String,
    val versionCode: Int,
    val createdAtEpochMillis: Long,
    val status: String,
    val message: String,
    /** Relative artifact name only; the generated APK lives outside the project JSON. */
    val artifactFileName: String? = null,
    /** Integrity digest of the generated APK, never a signing secret. */
    val artifactSha256: String? = null,
    /** Stable reference to the project key held by the encrypted key store. */
    val signingKeyId: String? = null
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

    /**
     * Stable content identity for an APK assembly input. It deliberately excludes editable
     * metadata so a completed build only fails when its packaged files or assets changed.
     */
    fun contentFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun write(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        files.toSortedMap().forEach { (path, content) ->
            write("text:$path")
            write(content)
        }
        binaryAssets.toSortedMap().forEach { (path, encodedContent) ->
            write("binary:$path")
            write(encodedContent)
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
