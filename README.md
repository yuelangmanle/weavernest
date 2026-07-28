# 织雀 Zhique

织雀是一个 Android 端 AI 小工具创作器，面向不会编程但希望快速做出可用工具的人。用户可以用自然语言创建项目，也可以粘贴 HTML/CSS/JavaScript 或导入完整 ZIP，在手机上编辑、预览、保存并配置能力。

当前版本：`0.2.1-alpha`

## 当前可用能力

- Kotlin + Jetpack Compose 创作器，默认简体中文，可切换英文。
- AI 创建入口、外部代码提示词复制、OpenAI-compatible API 设置（包含 DeepSeek 预设）。
- 项目文件编辑、模板中心、能力清单和构建元数据校验。
- 本地 WebView 预览，`weaver` JS 桥和按项目隔离的持久化预览数据。
- GitHub Releases 更新检查、发布说明展示和 APK 下载。
- 织雀启动图标、项目版本/包名锁定规则、项目能力到 Manifest 权限的核心策略。

## Alpha 边界

这是首个可构建 Alpha 基线。相机、媒体、定位、蓝牙、Wi-Fi/热点等能力已经进入能力注册表和模板规划；部分 Android 原生模块和轻量 APK 模板组装器仍处于真机技术验证阶段，受系统限制的能力会明确提示，不会伪装成可用。

## 本地构建

项目约束所有工具链和缓存位于 `E:\weavernest\.local`，构建产物位于 `E:\weavernest\artifacts`，不使用 C 盘的 SDK、JDK 或 Gradle 缓存。

```powershell
.\scripts\bootstrap-tools.ps1
.\scripts\build.ps1 -Task ':app:assembleDebug'
```

APK 输出到 `artifacts\zhique-v0.2.1-alpha.apk`。发布前必须运行核心测试、APK 签名校验和 Manifest 检查。

## 更新

应用设置会读取 [GitHub Releases](https://github.com/yuelangmanle/weavernest/releases)，显示更新日志。用户确认后，APK 交给 Android 系统下载管理器下载。

## 文档

- [开发书](docs/DEVELOPMENT.md)
- [开发进度书](docs/PROGRESS.md)
- [更新日志](CHANGELOG.md)
- [贡献指南](CONTRIBUTING.md)
- [安全说明](SECURITY.md)
- [第三方声明](THIRD_PARTY_NOTICES.md)
