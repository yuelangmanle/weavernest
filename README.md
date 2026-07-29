# 织雀 Zhique

织雀是一个 Android 端 AI 小工具创作器，面向不会编程但希望快速做出可用工具的人。用户可以用自然语言创建项目，也可以粘贴 HTML/CSS/JavaScript 或导入完整 ZIP，在手机上编辑、预览、保存并配置能力。

当前版本：`0.5.0-alpha`

## 当前可用能力

- Kotlin + Jetpack Compose 创作器，默认简体中文，可切换英文。
- AI 创建入口、外部代码提示词复制、OpenAI-compatible API 设置（包含 DeepSeek 预设）。
- 首页“粘贴外部代码”工作流：剪贴板、HTML/CSS/JavaScript/ZIP 导入、导入检查、全屏等宽编辑和自动保存。
- 本地 WebView 预览，`weaver` JS 桥、按项目隔离的持久化预览数据、运行/停止控制和 JavaScript 错误日志。
- GitHub Releases 更新检查；仅在新版本附带 APK 时显示直接下载按钮，下载交给 Android 系统下载管理器。
- Apilot V2 API Profile 互操作：从 Apilot 选择连接/模型，或经确认后把当前方案发送给 Apilot。
- Android 返回行为、Apilot 安装引导、`weaver-required` 能力识别和中英文外部 AI 提示词。
- 织雀启动图标、项目版本/包名锁定规则、项目能力到 Manifest 权限的核心策略。
- 项目复制（新包名/新签名）、可恢复回收站和不含密钥的项目 ZIP 导出。
- BLE 扫描、连接、服务发现、读写和通知订阅；相机、相册、音乐、录音、定位、联系人、NFC、Wi-Fi、热点、API 与通知实验模板。
- 编辑器语法错误标记、2MB 大文件只读保护，以及预览/生成应用的加密数据备份与恢复。
- Room 项目元数据索引；文件和资源继续存储在独立项目工作区。

## Alpha 边界

这是 `0.5.0-alpha` 可构建基线。相机、媒体、定位、蓝牙、Wi-Fi/热点、NFC、USB、语音、屏幕捕获等能力已接入统一 Runtime 2.0 桥；权限弹窗、硬件差异和轻量 APK 覆盖更新仍必须经过 Android 10+ 真机矩阵，受系统限制的能力会明确提示，不会伪装成可用。

`0.5.0-alpha` 会在项目脚本前提供 `window.weaver`，按项目和用户动作请求权限，并对暂未验证的系统流程返回结构化错误。没有设备证据的模板仍标记为实验性。

## 本地构建

项目约束所有工具链和缓存位于 `E:\weavernest\.local`，构建产物位于 `E:\weavernest\artifacts`，不使用 C 盘的 SDK、JDK 或 Gradle 缓存。

```powershell
.\scripts\bootstrap-tools.ps1
.\scripts\build.ps1 -Task ':app:assembleDebug'
```

APK 输出到 `artifacts\zhique-v<当前 VERSION>.apk`。发布前必须运行核心测试、APK 签名校验和 Manifest 检查。

## 更新

应用设置会读取 [GitHub Releases](https://github.com/yuelangmanle/weavernest/releases)。当远端版本严格高于本机且发布中含 APK 时，会显示“下载更新”按钮；不会在检查更新时把用户跳转到发布页。

## Apilot 互操作

织雀使用 [Apilot](https://github.com/yuelangmanle/Apilot) 的 Android API Profile V2 协议，包名为 `com.example.api_manager`。用户可从 Apilot 选择 API 连接和默认模型，也可将织雀当前方案发送到 Apilot；两端都会要求用户确认。

默认请求范围为 `connection`、`models.default`。API Key 不是默认范围，只有用户明确打开授权开关后才会传输。Apilot 未安装时，织雀会引导用户到 Apilot GitHub 仓库安装。

## 文档

- [开发书](docs/DEVELOPMENT.md)
- [开发进度书](docs/PROGRESS.md)
- [更新日志](CHANGELOG.md)
- [贡献指南](CONTRIBUTING.md)
- [安全说明](SECURITY.md)
- [第三方声明](THIRD_PARTY_NOTICES.md)
