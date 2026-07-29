# Changelog

All notable user-facing changes are recorded here.

## 0.5.0-alpha - 2026-07-29

### Added

- BLE Runtime 现在具有扫描、连接、服务发现、特征读写与通知订阅；每个连接串行化 GATT 操作，订阅和连接只会在所属预览会话结束时释放。
- 模板中心替换了“保存测试数据”占位页：拍照识别、相册管理、音乐播放器、录音便签、定位记录、联系人、BLE、NFC、Wi-Fi、局部热点、API 数据面板和通知提醒均有独立的真实 Runtime 调用与验收说明。
- 编辑器增加本地解析错误标记与超过 2MB 文件的只读保护；HTML、CSS、JavaScript 和 JSON 均继续离线工作。
- 预览和生成应用的数据管理增加密码加密的导入/导出：仅迁移 weaver.data 与 weaver.storage 数据，不导出 API Key、私密运行时配置或浏览器缓存。
- 项目元数据、能力、签名身份与构建历史进入 Room 索引；源码和二进制资源仍保存在每项目 workspace，旧 JSON 会保留为恢复来源。
- 增加旧项目 JSON、Prompt v1 与模板夹具，外部代码审核同时正确识别 window.weaver.* 和 weaver.* 的兼容写法。

### Changed

- BLE、经典蓝牙、Wi-Fi、Wi-Fi 连接和局部热点在能力目录中统一标记为 Android 10+；界面继续按不同系统版本说明位置、附近设备和系统确认限制。
- 模板卡片显示 Android 最低版本、实验状态和离线验收场景；没有设备证据的硬件模板继续保持“实验性”。
- Room schema、版本、许可证和发布元数据进入 CI 校验。

### Known limitations

- 尚未执行 API 29、31、33、35/最新系统的真机权限矩阵、Apilot 已安装往返、生成 APK 安装/覆盖更新/数据保留、TalkBack、截图基线和性能测量。

## 0.4.0-alpha - 2026-07-29

### Added

- 接入 Runtime 2.0 屏幕捕获桥，并将传感器、定位、蓝牙等异步事件统一投递到 Android 主线程，避免 WebView 错误线程崩溃。
- 项目卡片新增复制为新项目、项目 ZIP 导出和可恢复回收站；复制会清除包名锁、构建历史和签名身份。
- 项目 ZIP 只包含源码、资源和 `weaver.json` 能力声明，不包含预览数据、API Key 或签名材料。
- 设置、Apilot 启动流程增加“已安装但启动失败”状态，不再把启动失败误判为未安装。
- `weaver.capabilities.request/openSettings` 现在接入权限代理和系统设置流程；首页增加最近修改时间、运行状态与项目设置入口。
- 旧项目 JSON 迁移改为保留原始恢复文件；项目设置新增本地快照查看与确认恢复，编辑器新增触控撤销、重做与查找/替换入口。
- 新增受版本、测试和正式签名门禁保护的 GitHub Release 工作流，避免把调试安装包误发布。

### Changed

- 工作区资源复制任务声明显式 Gradle 依赖，模板图标在所有变体中稳定生成。
- 预览运行日志统一脱敏凭据、认证头、坐标、文件 URI 和 data URL。

### Known limitations

- 尚未完成 API 29、31、33、35 真机权限矩阵、Apilot 真机往返、生成 APK 覆盖安装和数据保留验收；相关能力未标记为正式可用模板。

## 0.3.1-alpha - 2026-07-28

### Fixed

- 系统返回键现在优先关闭当前弹层、返回项目首页，首页需连续返回两次才会退出应用。
- Apilot 现在声明 Android 11+ 包可见性，已安装且兼容的 Apilot 不再被误判为未安装。
- Apilot 未安装、版本不兼容或被系统停用时显示可取消的应用内引导；不会再自动跳转 GitHub。
- `window.weaver` 在项目脚本运行前注入，外部代码不会因注入顺序误判织雀环境不存在。
- 解析 `weaver-required` 权限注释并映射到项目能力；补齐麦克风和振动能力的 Manifest 声明。

### Changed

- 下线未通过真机能力验证的模板，仅保留明确标注的离线数据示例。
- 新增浅色/深色品牌主题基础，使用金色作为强调色而非整页黑金配色。
- 提示词升级为 2.0.0，提供中文和英文复制入口，并要求生成代码先等待 `window.weaver.ready()`。

### Known limitations

- 相机、定位、通知、联系人、麦克风、传感器等 `weaver` P0 API 已有稳定名称与环境注入，但真实 Android 原生调用仍在 `0.4.0-alpha` Runtime 模块中实现；当前会返回明确的 `UNSUPPORTED` 错误，不会伪造成功。

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
