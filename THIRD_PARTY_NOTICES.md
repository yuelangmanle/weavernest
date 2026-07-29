# 第三方组件声明

织雀当前发行构建使用以下组件。Gradle 与 npm lockfile 锁定版本；发布版本必须附带相应许可证文本。

- AndroidX / Jetpack Compose / WebKit / Security Crypto / WorkManager — Apache-2.0
- AndroidX Room 2.5.0 — Apache-2.0
- Kotlin 2.0.21 — Apache-2.0
- OkHttp 4.12.0 — Apache-2.0
- zip4j 2.11.5 — Apache-2.0
- ARSCLib 1.3.5 — Apache-2.0
- Android APK Signature Scheme tools 8.7.3 — Apache-2.0
- CodeMirror 6 and bundled language packages — MIT
- esbuild 0.24.0 — MIT
- Apilot Android API Profile V2（已安装应用互操作）— 织雀不内嵌其代码；默认仅请求 `connection`、`models.default`，用户明确授权时才请求 `secret.api_key`。协议与安装入口见 https://github.com/yuelangmanle/Apilot

以下组件仅保留为后续能力实现的评估候选，当前发行构建未引入：Capacitor（MIT）、Nordic Android BLE Library（BSD-3-Clause）、ZXing（Apache-2.0）、Tink（Apache-2.0）、MapLibre Native（BSD-2-Clause）。

GPL/AGPL 项目不作为织雀发行版的代码底座。
