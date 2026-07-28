# 织雀重构 TODO

本清单对应 [商业化产品重构 SPEC](docs/PRODUCT_REDESIGN_SPEC.md) 与 [实施计划](docs/PRODUCT_REDESIGN_PLAN.md)。`0.3.1-alpha` 的阻断性稳定化正在落地；未勾选项仍未完成。

## P0：阻断性问题，目标 `0.3.1-alpha`

### 根因与测试基线

- [x] 确认系统返回直接结束 Activity 的根因：缺少 NavHost、路由栈和 BackHandler。
- [x] 确认附件环境检测失败的根因：`window.weaver` 在用户脚本之后注入。
- [x] 确认原生权限不可用的根因：当前只有数据桥和 unavailable 占位，没有权限请求器或原生模块。
- [x] 确认附件能力未被识别的根因：没有解析 `weaver-required`，分析器只识别少量浏览器 API。
- [x] 确认模板无效的根因：所有模板复用同一个保存数据页面。
- [x] 确认 Apilot 误判的根因：Android 11+ 包可见性 queries 缺失，并且误判后自动跳转 GitHub。
- [x] 确认提示词缺陷：仅英文、无 API 版本、无精确方法和 ready 流程。
- [x] 将用户权限诊断 HTML 固定为 androidTest fixture 并记录哈希。
- [ ] 建立旧项目 JSON、旧 Prompt v1 和旧模板的回归夹具。

### 返回与导航

- [ ] 创建类型安全路由和 Navigation Compose NavHost。
- [ ] 把设置、模板、创建、粘贴从根布尔状态迁移到路由或局部弹层。
- [x] 系统返回先关闭现有弹层，再从项目回首页；Navigation Compose 子路由迁移待完成。
- [x] 首页实现两秒二次返回退出。
- [ ] 顶部返回图标与系统返回行为一致。
- [ ] 添加设置、模板、粘贴、项目四区和全屏预览返回测试。

### Apilot

- [x] Manifest 声明 Apilot package、PICK action、IMPORT action 的 queries。
- [ ] 区分已安装兼容、已安装不兼容、已停用、未安装、启动失败。
- [ ] 实际启动捕获 ActivityNotFoundException，禁止自动浏览器回退。
- [x] 未安装时显示织雀内引导页，三秒后才启用“前往安装说明”。
- [x] 引导页始终有取消；倒计时结束不自动跳转。
- [ ] 已安装兼容设备完成 PICK/IMPORT 真机测试。

### 环境注入与诚实模板

- [x] 在 document-start 创建 `window.weaver` 基础壳，保证附件同步检测通过；真机 WebView 验证待完成。
- [ ] Runtime 未实现的方法返回标准错误，不返回伪成功。
- [x] 下线所有复用保存数据页面的能力模板。
- [x] 只保留明确标记为“数据存储示例”的可运行离线模板。
- [x] 更新 `0.3.1-alpha` 版本号、CHANGELOG、开发书和进度书。

## P1：产品壳与 Runtime P0，目标 `0.4.0-alpha`

### 商业 UI 设计系统

- [ ] 建立浅色/深色颜色 token，品牌金只做强调。
- [ ] 建立中文/英文排版、等宽代码字体、间距、圆角和状态色。
- [ ] 建立统一顶部栏、状态提示、空状态、错误区和确认对话框。
- [ ] 手机使用四项底部导航；平板使用 NavigationRail 和双栏。
- [ ] 设置、更新、API 管理、Apilot 改为独立页面。
- [ ] 建立关键页面视觉回归截图。
- [ ] 完成 TalkBack、200% 字号、48dp 触控目标和颜色对比度验收。

### 首页与项目工作区

- [ ] 首页提供 AI 创建、粘贴、导入和模板四入口。
- [ ] 最近项目显示修改时间、版本、运行状态和构建状态。
- [ ] 项目溢出菜单支持重命名、复制、导出和可恢复删除。
- [ ] 项目工作区固定为创作、运行、能力、发布四区。
- [ ] AI 与文件编辑整合到创作区分段控件。
- [ ] 项目数据管理移入项目设置。

### 数据和编辑器

- [ ] 元数据迁移到 Room；文件迁移到项目 workspace。
- [ ] 旧 JSON 迁移幂等、失败可恢复、不删除原文件。
- [ ] 用 StateFlow 和分功能 ViewModel 替代单体 StudioViewModel。
- [ ] 本地打包 CodeMirror 6，禁止 CDN。
- [ ] 支持 HTML/CSS/JS/JSON、查找替换、诊断、撤销重做。
- [ ] 自动保存 500ms 防抖，保留 30 个项目快照。
- [ ] 外部代码应用前显示逐文件 diff 和沙箱预览。

### Runtime 2.0 内核

- [ ] 创建 `:runtime` Android Library。
- [ ] 使用 WebViewAssetLoader 和固定 HTTPS 本地域名。
- [ ] document-start 注入 `apiVersion`、`ready()` 和 API 壳。
- [ ] 建立 requestId Promise、subscriptionId 事件和标准错误码。
- [ ] 只暴露一个受控 JSON 消息入口。
- [ ] 验证项目能力、来源、会话、参数和数据大小。
- [ ] 页面停止、返回和崩溃时释放所有订阅及原生资源。

### 能力声明与权限代理

- [ ] 定义 `weaver.json` schema 和旧注释兼容。
- [ ] 导入附件时识别十项能力，不再返回空集合。
- [ ] 检查所有 `weaver.*` 方法是否存在于 RuntimeApiCatalog。
- [ ] 实现权限状态机和 Android API 差异映射。
- [ ] 能力开关不立刻请求权限；调用或测试时才请求。
- [ ] 永久拒绝显示设置入口；特殊权限显示系统流程说明。

### 附件 P0 十项能力

- [ ] `camera.capture`
- [ ] `geolocation.getCurrentPosition`
- [ ] `storage.writeFile` / `storage.readFile`
- [ ] `notification.requestPermission` / `notification.show`
- [ ] `contacts.pick`
- [ ] `microphone.requestPermission` / `microphone.record`
- [ ] `clipboard.write` / `clipboard.read`
- [ ] `vibrate`
- [ ] `sensor.subscribe` / `unsubscribe`
- [ ] `config.get` / `config.set`
- [ ] API29/31/33/35 分别验证允许、拒绝、取消和不支持。

### 双语提示词 2.0

- [ ] 中文完整提示词。
- [ ] English full prompt。
- [ ] 两版包含相同 Runtime 2.0 API、参数、返回值和错误码。
- [ ] 两版要求先 `await window.weaver.ready()`。
- [ ] 两版要求输出 `weaver.json` 或兼容注释。
- [ ] 两版禁止编造未列出的接口。
- [ ] 两版要求权限拒绝、取消、超时和不支持状态。
- [ ] UI 提供中文/英文独立复制按钮。
- [ ] 自动测试两版方法集合完全一致。

## P2：扩展能力与真实模板，目标 `0.5.0-alpha`

### 媒体与文件

- [ ] Photo Picker 图片/视频多选。
- [ ] MediaStore 保存图片、视频和音频。
- [ ] 本地/网络音乐播放和媒体会话。
- [ ] 录音、回放和导出。
- [ ] SAF 打开、创建和持久 URI 授权。

### 连接与近场

- [ ] BLE 扫描、连接、服务发现、读写和通知。
- [ ] 经典蓝牙已配对设备和连接限制。
- [ ] Wi-Fi 状态、扫描和 NetworkSpecifier 请求连接。
- [ ] LocalOnlyHotspot 启停和设备限制。
- [ ] NFC 前台标签读写。
- [ ] USB 设备枚举和逐设备授权。

### 系统能力

- [ ] 日历选择、添加和经授权读取。
- [ ] 分享、拨号、地图和外部链接 Intent。
- [ ] 生物识别确认。
- [ ] 语音识别和 TTS。
- [ ] MediaProjection 屏幕捕获系统授权。
- [ ] WorkManager、前台服务、通知和电池限制。
- [ ] 网络请求代理、上传下载和连接状态。

### 真实模板

- [ ] 权限诊断。
- [ ] 拍照识别。
- [ ] 相册管理。
- [ ] 音乐播放器。
- [ ] 录音便签。
- [ ] 定位记录。
- [ ] 联系人选择。
- [ ] BLE 工具。
- [ ] Wi-Fi 诊断。
- [ ] API 数据面板。
- [ ] 每个模板有独立文件、截图、能力清单、最低 API 和测试。
- [ ] 只有通过真机测试的模板标记 Available。

## P3：生成 APK，目标 `0.6.0-alpha`

- [ ] 创建 `:template-runtime` 并依赖同一 `:runtime`。
- [ ] 只允许组装织雀内置模板 APK。
- [ ] 替换包名、名称、版本、图标、Web 资源和所选 Manifest 权限。
- [ ] 每项目独立签名；同项目后续签名保持一致。
- [ ] 成功构建后自动递增 versionCode。
- [ ] 包名首次成功导出后锁定。
- [ ] v2/v3 签名、安装和覆盖更新通过。
- [ ] 覆盖更新保留 Web 数据和原生数据。
- [ ] 备份密码加密导出项目密钥。
- [ ] 换机恢复后可以继续更新同一应用。
- [ ] 预览与生成 APK 的 Runtime 2.0 合同测试一致。

## P4：商业发布门禁，目标 `1.0.0-rc1`

- [ ] API29、31、33、35/最新系统完整设备矩阵。
- [ ] 手机、平板、横屏、分屏和 200% 字号。
- [ ] 中文、英文、浅色、深色视觉回归。
- [ ] 权限首次请求、拒绝、永久拒绝和设置恢复。
- [ ] WebView 崩溃和运行会话恢复。
- [ ] 项目迁移、磁盘满、损坏 ZIP 和缺失资源恢复。
- [ ] Apilot 未安装、兼容、旧版、停用、取消和 URI 回传。
- [ ] API Key、联系人、定位、文件内容不进入日志。
- [ ] 不可信页面无法调用 `weaver`。
- [ ] 冷启动、1MB 文件编辑、预览启动和内存回收性能达标。
- [ ] CI 校验测试、版本、Manifest、许可、签名和 Release 资产。
- [ ] 所有版本同步更新 VERSION、versionCode、CHANGELOG、开发书、进度书和第三方声明。

## 每日执行规则

- 每次只推进一个可独立验收的 Task。
- 功能代码前先写能复现问题的失败测试。
- 完成一个 Task 后运行其单元测试、相关 Android 测试和完整构建。
- 没有设备证据时不得把权限或模板标记为“可用”。
- 不允许以提示词修补缺失原生能力；提示词和 Runtime API 必须共同更新。
- 不允许把任意启动失败都解释为“应用未安装”。
- 不允许为赶版本恢复统一占位模板或自动跳转行为。
