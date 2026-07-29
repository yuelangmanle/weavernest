package com.zhique.runtime.capability

object RuntimeFilePolicy {
    fun requireRelativePath(path: String): String {
        require(path.isNotBlank()) { "A file path is required." }
        require(!path.startsWith('/') && !path.startsWith('\\')) { "Only relative paths are allowed." }
        require(!path.contains('\\')) { "Backslash paths are not allowed." }
        val normalized = path.split('/').filter { it.isNotBlank() }.joinToString("/")
        require(normalized.isNotBlank() && normalized.split('/').none { it == "." || it == ".." }) {
            "The file path escapes the project sandbox."
        }
        return normalized
    }
}
