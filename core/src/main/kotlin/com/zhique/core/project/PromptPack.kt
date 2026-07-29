package com.zhique.core.project

import java.io.InputStreamReader

enum class PromptLanguage { ZhCn, En }

data class PromptPack(
    val version: String,
    val language: PromptLanguage,
    private val template: String
) {
    fun renderForExternalModel(projectName: String): String = template
        .replace("{{PROJECT_NAME}}", projectName)
        .replace("{{API_VERSION}}", "Runtime ${RuntimeApiCatalog.apiVersion}")
        .replace("{{API_CONTRACT}}", RuntimeApiCatalog.renderPromptContract(language))

    companion object {
        const val currentVersion = "2.0.0"

        fun default(language: PromptLanguage = PromptLanguage.ZhCn): PromptPack = PromptPack(
            version = currentVersion,
            language = language,
            template = loadTemplate(language)
        )

        private fun loadTemplate(language: PromptLanguage): String {
            val path = when (language) {
                PromptLanguage.ZhCn -> "/prompts/zh-CN/runtime-2.0.md"
                PromptLanguage.En -> "/prompts/en/runtime-2.0.md"
            }
            return requireNotNull(PromptPack::class.java.getResourceAsStream(path)) {
                "Missing bundled prompt template: $path"
            }.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
        }
    }
}
