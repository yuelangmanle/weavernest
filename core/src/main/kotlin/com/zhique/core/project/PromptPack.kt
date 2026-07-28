package com.zhique.core.project

data class PromptPack(
    val version: String,
    val instructions: String
) {
    fun renderForExternalModel(projectName: String): String = buildString {
        appendLine("You are generating a mobile web project for 织雀 (Zhique).")
        appendLine("Project: $projectName")
        appendLine()
        append(instructions)
    }

    companion object {
        fun default(): PromptPack = PromptPack(
            version = "1",
            instructions = """
                Return a complete mobile-first HTML/CSS/JavaScript project with relative asset paths.
                Use window.weaver for Android capabilities. Do not call Capacitor, Android JavaScript interfaces, or browser-only device APIs directly.
                Declare the required weaver capabilities in a short manifest comment at the top of index.html.
                private runtime secrets must never be embedded in source code. Read them through weaver.config.get when the user configures them at runtime.
                Keep UI touch-friendly, explain graceful fallbacks for unsupported devices, and do not request a permission until the related user action occurs.
            """.trimIndent()
        )
    }
}
