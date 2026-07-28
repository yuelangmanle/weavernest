package com.zhique.core.project

enum class PromptLanguage { ZhCn, En }

data class PromptPack(
    val version: String,
    val language: PromptLanguage,
    val instructions: String
) {
    fun renderForExternalModel(projectName: String): String = buildString {
        when (language) {
            PromptLanguage.ZhCn -> {
                appendLine("你正在为织雀（Zhique）生成可在 Android 手机上运行的 HTML/CSS/JavaScript 项目。")
                appendLine("项目名称：$projectName")
                appendLine("输出语言：中文。")
            }
            PromptLanguage.En -> {
                appendLine("You are generating a mobile HTML/CSS/JavaScript project for 织雀 (Zhique).")
                appendLine("Project: $projectName")
                appendLine("Output language: English.")
            }
        }
        appendLine()
        append(instructions)
    }

    companion object {
        fun default(language: PromptLanguage = PromptLanguage.ZhCn): PromptPack = PromptPack(
            version = "2.0.0",
            language = language,
            instructions = when (language) {
                PromptLanguage.ZhCn -> chineseInstructions
                PromptLanguage.En -> englishInstructions
            }
        )

        private val chineseInstructions = """
            使用相对资源路径，输出完整、移动端优先的项目文件。项目顶部必须声明需要的能力，例如：<!-- weaver-required: camera, geolocation, storage -->。
            页面启动时必须先执行 await window.weaver.ready()；在 ready 完成前不得判定织雀环境不存在。
            只能使用下列已登记接口：weaver.camera.capture、weaver.geolocation.getCurrentPosition、weaver.storage.readFile、weaver.storage.writeFile、weaver.notification.requestPermission、weaver.notification.show、weaver.contacts.pick、weaver.microphone.record、weaver.clipboard.read、weaver.clipboard.write、weaver.vibrate、weaver.sensor.subscribe、weaver.config.get。
            不得调用 Capacitor、Android JavaScript Interface、未列出的 weaver 方法或浏览器专有设备 API。每次能力调用都必须处理用户拒绝、取消、超时、UNSUPPORTED 和设备不支持状态。
            权限只能由用户点击相关功能后请求，不能在页面启动时批量请求。私密运行时密钥只能通过 weaver.config.get 读取，绝不能写入源码、日志或剪贴板。
            触及系统能力时，输出清晰的降级 UI 和错误说明。不要编造 API；不确定时保留功能入口并显示受限说明。
        """.trimIndent()

        private val englishInstructions = """
            Return a complete mobile-first project with relative asset paths. Declare capabilities at the top of the entry file, for example: <!-- weaver-required: camera, geolocation, storage -->.
            At startup, call await window.weaver.ready(); never conclude that the Zhique runtime is missing before ready resolves.
            Use only these registered APIs: weaver.camera.capture, weaver.geolocation.getCurrentPosition, weaver.storage.readFile, weaver.storage.writeFile, weaver.notification.requestPermission, weaver.notification.show, weaver.contacts.pick, weaver.microphone.record, weaver.clipboard.read, weaver.clipboard.write, weaver.vibrate, weaver.sensor.subscribe, weaver.config.get.
            Do not call Capacitor, Android JavaScript interfaces, unlisted weaver methods, or browser-only device APIs. Every capability call must handle user denial, cancellation, timeout, UNSUPPORTED, and unsupported-device states.
            Request a permission only after the user triggers the related action. Private runtime secrets must only be read through weaver.config.get and must never be embedded in source code, logs, or the clipboard.
            Provide clear fallback UI and error states for system limitations. Never invent an API; when uncertain, retain the feature entry and explain the limitation.
        """.trimIndent()
    }
}
