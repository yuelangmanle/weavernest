# Changelog

All notable user-facing changes are recorded here.

## 0.3.0-alpha - 2026-07-28

### Added

- 首页新增“粘贴外部代码”主入口：可读取剪贴板、导入 HTML/CSS/JavaScript/ZIP，先显示识别结果与兼容性检查，再创建项目。
- 项目工作区新增固定运行、重新运行、停止控制；预览显示运行状态、WebView 错误和 JavaScript 控制台日志。
- 更新检查增加明确状态：仅当远端版本较新且含 APK 时提供“下载更新”按钮；无 APK、已是最新、下载完成和下载失败均有对应提示。
- 集成 Apilot Android V2 API Profile 互操作：可选择已保存方案或在用户确认后发送方案；默认仅共享连接地址与默认模型。

### Changed

- 文件区升级为全屏代码工作区，支持全部项目文件切换、等宽编辑与自动保存；导入代码后直接进入编辑器。
- 构建页明确当前仅保存构建计划，生成独立项目 APK 的模板组装器仍处于真机技术验证阶段。

### Security

- Apilot 的 API Key 请求和导出均默认关闭，必须由用户单独开启并确认；密钥不写入运行日志、剪贴板或 Apilot 审计说明。

## 0.2.1-alpha - 2026-07-28

### Fixed

- GitHub Actions 在 Linux Runner 上显式赋予 Gradle Wrapper 执行权限，确保 CI 可运行构建与测试。

## 0.2.0-alpha - 2026-07-28

### Added

- Kotlin/Compose Android creator shell with Chinese default and English option.
- Project workspace for AI, files, preview, capabilities, build metadata, and data management.
- Persistent WebView preview with a versioned `weaver` bridge and project-isolated data.
- Capability registry, package/version policy, import analysis, prompt pack, and build-plan validation.
- Encrypted local AI settings, GitHub release update checking, and APK download action.
- Android launcher icon based on `图标.png`.

### Known limitations

- Native camera, media, location, BLE, Wi-Fi/hotspot modules and the final template APK assembler are still in technical validation.

## 0.1.0 - 2026-07-28

### Added

- Project governance baseline: versioning, changelog, development book, progress book, and non-`C:` environment policy.
