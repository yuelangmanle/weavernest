# 织雀商业化产品重构 SPEC

- 文档状态：已确认，`0.5.0-alpha` 已完成离线实现，等待设备矩阵
- 基线版本：`0.5.0-alpha`
- 目标平台：Android 10（API 29）及以上手机和平板
- 默认语言：简体中文；完整英文界面与英文提示词同步维护
- 目标用户：不会编程、依靠 AI 生成 HTML/CSS/JavaScript 小工具的普通用户

## 1. 目标

织雀必须从“功能入口集合”重构为可以连续完成创作任务的 Android 商业级工具。用户应能理解自己当前在哪里、下一步做什么、代码是否正在运行、哪些系统能力已具备、为什么某项能力不可用，以及如何恢复。

核心闭环固定为：

`创建或导入 -> 审核代码与能力 -> 编辑 -> 本地运行 -> 处理权限或错误 -> 配置应用信息 -> 构建 APK -> 更新同一项目`

本次重构同时解决四类基础问题：

1. 建立真实导航栈和符合 Android 习惯的返回行为。
2. 建立统一、克制、可扫描的商业软件 UI 设计系统。
3. 建立版本化、可在文档加载前注入、可实际调用 Android 能力的 `weaver` 运行时。
4. 修复 Apilot 安装识别、引导、API 方案导入/导出和 API Key 管理流程。

## 2. 当前问题与已确认根因

### 2.1 返回键直接退出

- 当前应用没有 `NavHost`、路由栈或 `BackHandler`。
- 设置、模板、创建和粘贴页由根 Composable 中的布尔变量控制。
- Android 系统返回键没有机会先关闭弹层或回退子页面，因此会直接结束 `MainActivity`。

### 2.2 外部代码显示“未检测到织雀环境”

用户提供的权限检测页面在自己的脚本启动时同步检查 `window.weaver`。当前织雀把 `window.weaver` 的脚本追加到 `</body>` 前，用户脚本可能更早执行，因此会得到未检测到环境的结果。

即使环境对象被检测到，当前运行时也只有以下真实接口：

- `weaver.capabilities()`
- `weaver.data.get()`
- `weaver.data.set()`
- 各占位命名空间的 `unavailable()`

用户附件需要的 `camera.capture`、`geolocation.getCurrentPosition`、`storage.readFile/writeFile`、`notification.show`、`contacts.pick`、`microphone.record`、`clipboard.read/write`、`vibrate`、`sensor.subscribe`、`config.get` 均未实现。

### 2.3 权限无法调用

- Manifest 声明权限不等于运行时已经获得权限。
- 当前没有 Android 运行时权限请求器，也没有处理“拒绝”“不再询问”“特殊设置页”“系统确认页”。
- 当前相机、定位、文件、蓝牙、Wi-Fi 等没有对应原生模块。
- `RECORD_AUDIO`、`VIBRATE`、API 29-32 兼容权限等声明不完整。
- 粘贴代码中的 `<!-- weaver-required: ... -->` 没有被解析；项目能力通常为空。
- 当前导入分析器只识别少量浏览器 API，无法识别用户已经正确使用的 `weaver.*` 调用。

### 2.4 模板不可用

所有模板都复用同一个“保存测试数据”页面，仅标题、说明和能力集合不同。模板没有调用它声明的系统能力，也没有能力状态、权限拒绝和设备不支持的处理，因此不能作为示例或验收依据。

### 2.5 Apilot 已安装仍被判定为未安装

- 织雀目标 SDK 为 35，Android 11+ 对包查询实施可见性限制。
- 织雀 Manifest 没有声明 Apilot 包或 Intent 的 `<queries>`。
- `resolveActivity()` 因不可见可能返回空，即使 `com.example.api_manager` 已安装且已声明 V2 Intent。
- 当前误判后立即打开 GitHub，没有确认页、倒计时或取消机会。

### 2.6 提示词不足

- 提示词只有英文。
- 只说“使用 window.weaver”，没有列出准确方法、参数、返回值、错误码和运行时就绪流程。
- 没有固定 API 版本，AI 可以自由编造接口名称。
- 没有说明能力清单的标准格式，导致附件注释与项目能力注册表无法对齐。
- 当前代码分析器建议的 `location/files/notifications` 命名，与附件使用的 `geolocation/storage/notification` 也不一致。

## 3. 产品原则

1. 任何页面都必须有明确层级、当前位置、主要操作和恢复路径。
2. 权限只在用户触发相关动作时请求；不在启动或导入时批量索取。
3. “已声明”“可请求”“已授权”“被拒绝”“系统限制”“设备不支持”必须是不同状态。
4. 未实现的能力不得出现在“可用模板”中，也不得返回伪成功。
5. 预览与生成 APK 使用同一运行时模块和同一 `weaver` API 契约。
6. 外部代码、AI 修改和自动修复必须先显示改动与预览，用户确认后才能覆盖项目。
7. 用户数据、API Key、签名密钥和运行日志必须分区保存，敏感信息不得写入日志或剪贴板。
8. 手机优先，同时为平板提供适配，不用简单放大手机布局。

## 4. 信息架构与导航

### 4.1 根路由

```text
home
create
  ai
  paste
  import
templates
project/{projectId}
  create
  run
  capabilities
  build
project/{projectId}/settings
settings
  appearance
  language
  api-providers
  integrations/apilot
  updates
  about
```

手机项目工作区使用四项底部导航：

- 创作：AI 对话、文件树、CodeMirror 编辑器、资源管理。
- 运行：预览、运行/停止/刷新、设备视口、日志和错误诊断。
- 能力：能力选择、当前系统状态、权限测试和限制说明。
- 发布：名称、图标、包名、版本、签名、数据策略、APK 构建历史。

AI 与文件编辑在“创作”中使用分段控件切换，不再占用两个顶层标签。数据管理放入项目设置，不再成为顶层标签。

平板使用左侧 NavigationRail；创作页采用编辑器与预览双栏，最小宽度不足时自动回到单栏。

### 4.2 返回行为

返回优先级固定如下：

1. 关闭最上层菜单、底部抽屉、对话框、日志面板或文件抽屉。
2. 退出搜索、全屏预览、文件重命名等局部模式。
3. 弹出当前子路由，回到上一个页面。
4. 从项目根页面返回首页，项目继续自动保存。
5. 首页第一次返回显示“再按一次退出织雀”，两秒内第二次返回才退出。

系统返回手势、系统返回键和顶部返回箭头必须调用同一导航行为。支持 Android 预测性返回动画。运行预览返回项目时不删除项目数据；只有用户明确停止时才销毁运行会话。

## 5. 核心用户流程

### 5.1 首页

- 顶部显示织雀品牌、全局搜索和设置图标。
- 第一操作区提供“AI 创建”“粘贴代码”“导入 ZIP/文件”“选择模板”。
- 最近项目采用可扫描列表，显示名称、修改时间、版本、最近运行状态和构建状态。
- 长按或溢出菜单提供重命名、复制、导出项目、删除；删除必须二次确认并进入可恢复回收站。
- 空状态只保留一个明确主操作和三个次操作，不展示功能介绍式大段文字。

### 5.2 粘贴与导入审核

1. 用户粘贴代码或选择文件。
2. 织雀解析完整 HTML、多文件结构、资源引用、`weaver.json` 和旧版 `weaver-required` 注释。
3. 显示文件列表、识别到的能力、未知接口、危险模式、缺失资源和最低 Android 版本。
4. 左侧或上方显示变更摘要；右侧或下方提供沙箱预览。
5. 用户选择“创建新项目”或“应用到当前项目”。
6. 覆盖当前文件前显示逐文件差异；取消不会修改项目。

### 5.3 创作工作区

- 顶部栏包含返回、项目名、自动保存状态、撤销/重做、运行和更多菜单。
- 文件区使用本地打包的 CodeMirror 6，不依赖 CDN；支持 HTML/CSS/JS/JSON、语法高亮、查找替换、错误标记、自动缩进和大文件保护。
- 手机使用可收起文件抽屉；平板固定显示文件树。
- 自动保存采用 500ms 防抖，并保留最近 30 个本地快照。
- 资源区支持图片、音频、视频和其他二进制资源，禁止继续用 Base64 塞入单个项目 JSON。

### 5.4 运行工作区

- 运行按钮启动一个明确的运行会话；重新运行使用最新已保存文件。
- WebView 顶部显示运行状态：准备中、运行中、等待权限、已停止、错误。
- 日志按信息、警告、错误、权限和原生事件分级，可复制脱敏报告给 AI。
- WebView 发生错误时，显示错误文件、行号、调用能力、Android 状态和可执行修复动作。
- “发送给 AI 修复”必须先生成补丁，展示差异和新预览，用户确认后才应用。

### 5.5 能力工作区

每项能力显示：

- 是否被项目选择。
- 当前设备和 Android 版本是否支持。
- 预览宿主是否已在 Manifest 声明。
- 当前权限状态。
- 是否需要系统确认页或特殊设置页。
- 最近测试时间和结果。
- “测试能力”或“前往设置”操作。

能力开关只改变项目配置，不立即请求权限。实际测试或网页调用时才请求。

### 5.6 发布工作区

- 分为应用信息、能力与权限、版本与签名、数据、构建结果五段。
- 包名首次成功导出后锁定；修改包名必须明确创建为新应用。
- 同项目沿用签名并自动递增 `versionCode`。
- 构建前列出新增、删除和特殊权限，并要求用户确认。
- 模板 APK 组装器未通过真机验收前，导出按钮保持“技术预览”状态，不能伪装成功。

## 6. UI 视觉设计系统

### 6.1 品牌方向

图标的“黑色、金色、编织飞鸟”作为品牌来源，但界面不能成为单一黑金主题。金色只用于品牌和主要强调；状态使用青绿色、蓝色、琥珀色和红色，保持工具类软件的可读性。

### 6.2 颜色

浅色模式：

- 页面背景：`#F7F8FA`
- 主表面：`#FFFFFF`
- 次表面：`#F0F2F5`
- 主文字：`#17191D`
- 次文字：`#5F6670`
- 边框：`#DDE1E6`
- 品牌主色：`#8A5A00`
- 品牌浅色：`#F2C665`
- 成功：`#0A7C68`
- 信息：`#2F6BBA`
- 警告：`#9A5B00`
- 错误：`#B42318`

深色模式：

- 页面背景：`#151617`
- 主表面：`#1E2022`
- 次表面：`#292C2F`
- 主文字：`#F3F4F4`
- 次文字：`#A8ADB4`
- 边框：`#3A3E43`
- 品牌主色：`#E8B34E`
- 成功：`#49BFA8`
- 信息：`#72A5E8`
- 警告：`#E3A33B`
- 错误：`#F97066`

### 6.3 排版与布局

- 中文正文使用系统无衬线字体；代码使用随应用打包的等宽字体。
- 页面标题 22sp，区域标题 16sp，正文 14-16sp，辅助文字不低于 12sp。
- 间距基准为 4dp；常用间距 8/12/16/24/32dp。
- 普通卡片圆角不超过 8dp；页面区域不使用浮动卡片外壳。
- 触控目标至少 48dp；图标按钮必须带无障碍标签和必要的工具提示。
- 主要操作每屏最多一个高强调按钮；相邻次操作使用图标或低强调按钮。
- 动画时长 150-220ms，尊重系统“减少动画”设置。

### 6.4 组件规范

- 命令：按钮或图标按钮。
- 二元设置：开关或复选框。
- 模式选择：分段控件。
- 页面层级：顶部返回图标，不使用写着“项目”的文字按钮代替返回。
- 状态：状态点、标签和一句原因，不仅依赖颜色。
- 错误：页面内错误区或 Snackbar；需要用户决策时才使用对话框。
- 设置、更新日志、API 管理和 Apilot 集成必须是独立页面，不再塞进一个长 `AlertDialog`。

## 7. `weaver` Runtime API 2.0

### 7.1 启动和握手

`window.weaver` 必须在任何项目脚本运行前创建。首选 AndroidX WebKit document-start script；不支持该特性的 WebView 使用在 `<head>` 第一个节点前注入的兼容实现。

公开入口：

```javascript
window.weaver.apiVersion // "2.0"
await window.weaver.ready()
await window.weaver.capabilities.list()
await window.weaver.capabilities.status("camera")
```

`ready()` 返回：

```json
{
  "runtime": "preview",
  "apiVersion": "2.0",
  "androidApi": 35,
  "projectId": "...",
  "selectedCapabilities": ["camera"]
}
```

### 7.2 调用协议

- JS 方法默认返回 Promise。
- JS 通过唯一 `requestId` 发送 JSON 请求。
- 原生分发器验证可信来源、API 版本、项目能力、参数和权限。
- Activity Result 或原生异步任务完成后，以相同 `requestId` 回传。
- 传感器、BLE 通知等事件使用 `subscriptionId`；取消订阅后立即释放监听器。
- 日志不得包含文件正文、联系人内容、定位坐标、API Key 或录音数据。

统一错误对象：

```json
{
  "code": "PERMISSION_DENIED",
  "message": "用户拒绝了相机权限",
  "capability": "camera",
  "recoverable": true,
  "action": "request_again"
}
```

固定错误码包括：`RUNTIME_NOT_READY`、`CAPABILITY_NOT_SELECTED`、`PERMISSION_NOT_DECLARED`、`PERMISSION_DENIED`、`PERMISSION_BLOCKED`、`USER_CANCELLED`、`UNSUPPORTED_ANDROID_VERSION`、`UNSUPPORTED_DEVICE`、`SPECIAL_FLOW_REQUIRED`、`INVALID_ARGUMENT`、`TIMEOUT`、`NATIVE_FAILURE`。

### 7.3 P0 API：必须完整通过用户附件

```javascript
await weaver.camera.capture({ quality: 0.7 })
await weaver.geolocation.getCurrentPosition({ accuracy: "high", timeoutMs: 15000 })
await weaver.storage.writeFile(path, text)
await weaver.storage.readFile(path)
await weaver.notification.requestPermission()
await weaver.notification.show(title, body, options)
await weaver.contacts.pick()
await weaver.microphone.requestPermission()
await weaver.microphone.record({ duration: 1500 })
await weaver.clipboard.write(text)
await weaver.clipboard.read()
await weaver.vibrate(300)
const sub = weaver.sensor.subscribe("accelerometer", callback)
sub.unsubscribe()
await weaver.config.get(key)
await weaver.config.set(key, value)
```

为现有项目提供兼容别名：`location` 映射到 `geolocation`，`files` 映射到 `storage` 的选择器方法，`notifications` 映射到 `notification`，`data` 映射到项目键值存储。别名在日志中标记弃用，但不阻断运行。

### 7.4 能力声明

多文件项目使用 `weaver.json`：

```json
{
  "schemaVersion": 1,
  "runtimeApi": "2.0",
  "capabilities": ["camera", "geolocation", "storage", "notification"]
}
```

单 HTML 粘贴继续兼容：

```html
<!-- weaver-required: camera, geolocation, storage, notification -->
```

导入审核页把注释转换为 `weaver.json`，保留原始代码，不静默改写用户接口。

## 8. Android 能力矩阵

| 类别 | 公开能力 | Android 实现 | 权限/限制 |
| --- | --- | --- | --- |
| 相机 | 拍照、录像、扫码 | CameraX、系统选择器、ZXing | `CAMERA`；用户触发后请求 |
| 图片/视频/音频 | 选择、保存、播放 | Photo Picker、MediaStore、Media3 | API 33+ 分媒体权限；优先无权限选择器 |
| 麦克风 | 授权、录音、停止 | MediaRecorder | `RECORD_AUDIO` |
| 文件 | 打开、创建、读写项目文件 | Storage Access Framework、应用私有目录 | SAF 通常不需要存储权限；所有文件访问仅特殊场景 |
| 定位 | 当前定位、持续定位 | Fused/平台 LocationManager | 前台精确/粗略；后台定位单独流程 |
| 通知 | 授权、显示、取消、计划 | NotificationManager、WorkManager | API 33+ `POST_NOTIFICATIONS`；精确提醒另走特殊流程 |
| 联系人 | 选择、经授权读取 | `ACTION_PICK`、ContactsContract | 选择器优先；批量读取需要 `READ_CONTACTS` |
| 日历 | 选择、添加、读取 | Calendar Intent/Provider | Intent 优先；读写 Provider 需要权限 |
| 剪贴板 | 用户触发的读写 | ClipboardManager | 受系统隐私提示和后台限制 |
| 触觉 | 振动、预设反馈 | Vibrator/VibratorManager | `VIBRATE` 普通权限 |
| 传感器 | 加速度、陀螺仪、方向、光线 | SensorManager | 部分身体传感器需要专门权限 |
| 蓝牙 LE | 扫描、连接、读写、通知 | Nordic BLE + Android API | API 31+ Nearby 权限；旧版依赖定位 |
| 经典蓝牙 | 已配对设备、连接 | Android Bluetooth API | 现代系统不能静默开关蓝牙 |
| Wi-Fi | 网络信息、扫描、请求连接 | WifiManager、NetworkSpecifier | Android 版本、定位/Nearby 和系统确认限制 |
| 局部热点 | 启动、停止、状态 | LocalOnlyHotspot | 系统和设备限制；不能承诺互联网共享热点 |
| NFC | 前台读写标签 | NfcAdapter | 设备必须支持 NFC；用户开启 NFC |
| 网络/API | HTTPS 请求、上传下载、连接状态 | OkHttp、ConnectivityManager | 禁止明文流量；私密 Key 走 config |
| 分享/系统 Intent | 分享、打开链接、拨号、地图 | Android Intent | 优先使用无危险权限的系统 Intent |
| 生物识别 | 本机确认 | BiometricPrompt | 只返回验证结果，不暴露生物数据 |
| 语音 | 语音识别、文字转语音 | RecognizerIntent、TextToSpeech | 识别可能依赖服务和麦克风权限 |
| 屏幕捕获 | 用户授权的屏幕录制 | MediaProjection | 每次由系统明确授权 |
| USB | 枚举和用户授权设备 | UsbManager | 每设备授权，取决于硬件支持 |
| 后台任务 | 可靠延迟任务 | WorkManager/前台服务 | 按任务类型声明前台服务，遵守电池限制 |

系统签名权限、静默开关 Wi-Fi/蓝牙、绕过系统授权、读取受限设备标识、静默安装 APK、设备所有者能力不属于普通应用能力。UI 必须标记为“不支持”，而不是提供无效开关。

### 8.1 Runtime API 2.0 方法目录

下列名称是提示词、导入分析、模板和运行时共同使用的唯一正式目录。新增方法必须先更新目录和双语提示词，再实现原生模块。

| Namespace | 方法 | 结果/用途 | Capability ID |
| --- | --- | --- | --- |
| runtime | `ready()`、`info()` | 等待运行时、读取版本和宿主信息 | 无 |
| capabilities | `list()`、`status(id)`、`request(id)`、`openSettings(id)` | 查询和请求能力 | 无 |
| camera | `capture(options)`、`recordVideo(options)`、`scanCode(options)` | 照片、视频、扫码 | `camera` |
| media | `pickImages(options)`、`pickVideo(options)`、`pickAudio(options)`、`save(uri, collection)` | 系统媒体选择和保存 | `media_images` / `media_video` / `media_audio` |
| audio | `play(source, options)`、`pause()`、`stop()`、`state()` | 音频播放 | `media_audio` |
| microphone | `requestPermission()`、`record(options)`、`stop()` | 麦克风授权与录音 | `microphone` |
| storage | `readFile(path)`、`writeFile(path, data)`、`deleteFile(path)`、`list(path)`、`pickFile(options)`、`createFile(options)` | 项目沙箱和 SAF 文件 | `storage` |
| data | `get(key)`、`set(key, value)`、`remove(key)`、`clear()` | 项目键值数据 | `data` |
| geolocation | `getCurrentPosition(options)`、`watchPosition(options, callback)`、`clearWatch(id)` | 当前和持续定位 | `geolocation` |
| notification | `requestPermission()`、`show(title, body, options)`、`cancel(id)`、`schedule(options)` | 通知与计划提醒 | `notification` |
| contacts | `pick()`、`list(options)` | 最小选择或经授权读取 | `contacts` |
| calendar | `addEvent(event)`、`pickEvent()`、`list(options)` | 日历 Intent 或 Provider | `calendar` |
| clipboard | `read()`、`write(text)` | 前台剪贴板 | `clipboard` |
| haptics | `vibrate(duration)`、`impact(style)` | 振动和触觉反馈 | `haptics` |
| root alias | `vibrate(duration)` | `haptics.vibrate` 兼容入口 | `haptics` |
| sensor | `subscribe(type, callback)` | 返回含 `unsubscribe()` 的订阅对象 | `sensors` |
| bluetooth | `scan(options, callback)`、`stopScan()`、`connect(id)`、`disconnect(id)`、`discover(id)`、`read(request)`、`write(request)`、`subscribe(request, callback)` | BLE 与受限经典蓝牙 | `bluetooth_le` / `bluetooth_classic` |
| wifi | `state()`、`scan()`、`requestConnection(config)`、`openSettings()` | Wi-Fi 状态、扫描和系统确认连接 | `wifi_scan` / `wifi_connect` |
| hotspot | `startLocalOnly()`、`stop()`、`state()` | LocalOnlyHotspot | `local_hotspot` |
| nfc | `isAvailable()`、`read(options)`、`write(message)` | 前台 NFC 标签 | `nfc` |
| network | `status()`、`request(options)`、`download(options)` | 连接状态和 HTTPS 代理 | `network` |
| share | `open(payload)` | Android 分享面板 | `share` |
| system | `openUrl(url)`、`dial(number)`、`openMap(location)`、`appInfo()`、`deviceInfo()` | 受控系统 Intent 和非敏感设备信息 | `system_intents` |
| biometric | `authenticate(options)` | 本机生物识别确认结果 | `biometric` |
| speech | `recognize(options)`、`speak(text, options)`、`stopSpeaking()` | 系统语音识别和 TTS | `speech` |
| screenCapture | `request(options)`、`start(options)`、`stop()` | MediaProjection 系统授权捕获 | `screen_capture` |
| usb | `list()`、`requestPermission(deviceId)`、`open(deviceId, options)`、`close(deviceId)` | USB Host 设备操作 | `usb` |
| background | `schedule(task)`、`cancel(id)`、`list()` | WorkManager 和受限前台任务 | `background_tasks` |
| config | `get(key)`、`set(key, value)`、`remove(key)` | 加密运行时配置和私密 Key | `config` |

`vibrate` 和 `sensor` 保留附件兼容；新生成代码的提示词优先使用 `haptics.vibrate`，但不得导致附件失效。

### 8.2 Manifest 与 Android 版本映射

| 能力 | API 29-30 | API 31-32 | API 33+ |
| --- | --- | --- | --- |
| 相机 | `CAMERA` | `CAMERA` | `CAMERA` |
| 麦克风 | `RECORD_AUDIO` | `RECORD_AUDIO` | `RECORD_AUDIO` |
| 振动 | `VIBRATE` | `VIBRATE` | `VIBRATE` |
| 定位 | `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`；后台单独申请 | 同左 | 同左；用户可选择近似位置 |
| 通知 | 无运行时通知权限 | 无运行时通知权限 | `POST_NOTIFICATIONS` |
| 媒体读取 | `READ_EXTERNAL_STORAGE`，优先 SAF | `READ_EXTERNAL_STORAGE`，优先 Photo Picker/SAF | `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`；优先 Photo Picker |
| BLE 扫描/连接 | `BLUETOOTH`、`BLUETOOTH_ADMIN`、扫描通常需定位 | `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE` | 同 API31，并按用途声明 `neverForLocation` |
| Wi-Fi | `ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、扫描需要定位 | 同左；连接走 NetworkSpecifier | 加 `NEARBY_WIFI_DEVICES`，扫描/连接仍受系统限制 |
| 联系人 | `READ_CONTACTS` 仅批量读取；ACTION_PICK 可免 | 同左 | 同左 |
| 日历 | `READ_CALENDAR` / `WRITE_CALENDAR` 仅 Provider；Intent 可免 | 同左 | 同左 |
| NFC | `NFC` 普通权限和可选 feature | 同左 | 同左 |
| 生物识别 | `USE_BIOMETRIC` | `USE_BIOMETRIC` | `USE_BIOMETRIC` |
| 活动/身体传感器 | 依数据类型使用 `ACTIVITY_RECOGNITION` / `BODY_SENSORS` | 同左 | 后台身体传感器遵守新增限制 |
| 网络 | `INTERNET` / `ACCESS_NETWORK_STATE` | 同左 | 同左 |
| 后台任务 | 按用途使用 `FOREGROUND_SERVICE`、`WAKE_LOCK` | 同左 | 声明具体 `FOREGROUND_SERVICE_*` 类型并遵守启动限制 |
| 屏幕捕获 | MediaProjection 系统授权；前台服务 | 同左 | 使用 mediaProjection 前台服务类型和持续通知 |

织雀预览宿主可以声明普通应用预览所需权限并按项目延迟请求；生成 APK 的 Manifest 只能包含用户在项目中选择、且模板运行时实际需要的权限。

## 9. 权限状态机

每次能力调用必须经过：

```text
项目是否选择能力
  -> 当前 Android 是否支持
  -> 设备是否具备硬件
  -> Manifest 是否声明
  -> 是否属于普通/危险/特殊权限
  -> 是否已授权
  -> 请求或打开系统流程
  -> 执行能力
  -> 标准化返回或错误
```

状态枚举：`not_selected`、`unsupported_os`、`unsupported_device`、`not_declared`、`not_requested`、`granted`、`denied`、`blocked`、`special_flow_required`、`restricted`。

预览时权限属于织雀；生成 APK 后权限属于生成应用。两者的 API 行为、错误码和能力状态必须一致。

## 10. 模板系统

### 10.1 发布规则

- 每个模板有独立文件、资源、截图、能力清单、最低 API、设备限制和自动化测试。
- 只有对应运行时模块通过真机矩阵后，模板状态才可为“可用”。
- 未完成模板只能标记“实验”并默认隐藏；不得继续用统一占位页。
- 模板首页必须执行该模板的主要动作，并展示拒绝权限和设备不支持状态。

### 10.2 首批真实模板

1. 权限诊断：以用户附件为验收基准，覆盖 P0 十项能力。
2. 拍照识别：拍照、预览、压缩、配置公开或私有识别 API。
3. 相册管理：Photo Picker、多选、分类和保存。
4. 音乐播放器：本地选择、播放列表、后台播放限制说明。
5. 录音便签：麦克风授权、录音、回放和导出。
6. 定位记录：获取位置、地图或文本展示、历史保存。
7. 联系人选择：系统选择器、最小数据返回。
8. BLE 工具：扫描、连接、服务发现、读写和通知。
9. Wi-Fi 诊断：当前网络、扫描限制和系统连接确认。
10. API 数据面板：公开 API、运行时私密 Key、错误与重试。

## 11. 双语提示词包 2.0

### 11.1 版本与入口

- 提示词包版本与 Runtime API 版本分别记录，初始为 Prompt `2.0.0` / Runtime `2.0`。
- 设置中提供中文和英文默认语言。
- 项目内“复制给外部 AI”提供“中文完整规范”“English full spec”两个按钮。
- 内置 AI 自动使用当前界面语言对应提示词，但始终附带同一机器可读 API 清单。

### 11.2 必含内容

- 完整项目输出格式和相对资源路径规则。
- `weaver.json` 与旧注释格式。
- 精确 API 名称、参数、返回值、错误码和 Android 限制。
- 页面启动必须 `await window.weaver.ready()`；禁止在就绪前断言运行环境不存在。
- 禁止调用未列出的 `weaver` 方法；不确定时返回说明，不得编造。
- 权限只在用户点击相关操作后请求。
- 私密 Key 从 `weaver.config.get()` 获取，禁止写入源码。
- 所有能力必须有拒绝、取消、超时和不支持的界面状态。
- AI 修改输出统一为文件补丁、能力变更、变更说明和测试步骤。

中文与英文提示词使用相同结构、示例和 API 表；测试校验两版 API 标识完全一致，防止文档漂移。

## 12. API 提供商与 Apilot

### 12.1 API 设置

- 设置改为独立页面，显示提供商、协议、Base URL、模型和 Key 状态。
- 提供 DeepSeek、OpenAI 和 OpenAI-compatible 预设。
- 保存前校验 HTTPS、模型非空和 URL 结构。
- 提供“测试连接”，只发送最小请求；错误信息脱敏。
- API Key 默认遮挡，可临时显示；复制必须显式操作。

### 12.2 Apilot 安装状态

状态模型：

- `installed_compatible`
- `installed_incompatible`
- `installed_disabled`
- `not_installed`
- `launch_failed`

Manifest 同时声明 Apilot 包和两项 V2 Intent 的 `<queries>`。检测后仍以实际启动结果为准，捕获 `ActivityNotFoundException`，不得把所有失败都解释为“未安装”。

### 12.3 未安装引导

点击导入或导出时，若确认未安装：

1. 打开织雀内的引导对话框，不自动跳转。
2. 说明 Apilot 用途、仓库来源和即将离开织雀。
3. “前往安装说明”按钮显示三秒倒计时，倒计时结束后可点击。
4. 同时提供“取消”，倒计时结束也不自动跳转。
5. 用户确认后才打开 Apilot GitHub 仓库。

若已安装但不兼容，显示“打开 Apilot”“检查 Apilot 更新”“取消”，不直接跳转仓库。

### 12.4 导入和导出

- 导入前显示请求 scopes；默认 `connection`、`models.default`。
- `secret.api_key` 只能由用户显式打开，Apilot 还会再次授权。
- 返回方案先进入预览页，显示来源、提供商、地址、模型和是否含 Key；用户确认后保存。
- 导出先显示将发送的字段；Key 默认不发送。
- 导入不含 Key 且提供商或地址改变时，不复用旧 Key；明确提示用户重新填写。
- 临时 URI 立即读取，不持久保存 URI；审计日志不含 Key 和原始 payload。

## 13. 数据与架构

### 13.1 模块边界

- `:core`：纯 Kotlin 项目模型、能力契约、提示词、导入分析和构建策略。
- `:runtime`：Android Library，包含 WebView 安全宿主、桥分发器、权限代理和原生能力模块。
- `:app`：Compose 创作器、导航、项目管理、AI、设置和构建编排。
- `:template-runtime`：预编译生成应用模板，依赖同一个 `:runtime`。

UI 不按每个页面拆 Gradle 模块；在 `:app` 内按 feature 分包，控制构建复杂度。

### 13.2 状态和依赖

- 使用 `StateFlow` 暴露 UI 状态，不再由单个 482 行 ViewModel 管理全部功能。
- 使用轻量 `StudioContainer` 提供 repository/service，不引入 Hilt 作为本轮必需依赖。
- 拆分 `HomeViewModel`、`ProjectViewModel`、`RuntimeViewModel`、`SettingsViewModel`、`BuildViewModel`。
- 778 行单一 UI 文件拆为导航、主题、共享组件和各 feature 页面。

### 13.3 项目存储迁移

- 元数据和构建历史迁移到 Room。
- 项目文本与二进制资源迁移到 `files/projects/{id}/workspace`。
- 现有 JSON 项目首次启动时执行幂等迁移；成功后保留只读备份，下一稳定版再允许用户清理。
- 迁移失败不删除原文件，显示恢复入口并输出不含源码的错误报告。

## 14. WebView 安全

- 使用 `WebViewAssetLoader` 从固定 HTTPS 本地域名加载项目资源。
- 禁止 `file://`、任意内容 URI、混合内容和不可信页面导航。
- 桥只对项目本地域名注入；外部链接交给系统浏览器并先确认。
- `addJavascriptInterface` 只暴露一个 JSON 消息入口，不逐项公开高权限方法。
- 每个请求校验当前运行会话、项目能力、参数大小和来源。
- 摄像、录音、联系人、定位和文件结果限制大小，并通过一次性 URI 传输大数据。

## 15. 测试与验收

### 15.1 自动化

- 核心单元测试：能力解析、提示词中英一致性、权限状态机、错误码、项目迁移。
- Android 单元测试：Apilot 查询/Intent/结果解析、权限代理和模块参数校验。
- Compose UI 测试：导航与返回、导入审核、能力状态、设置与引导倒计时。
- WebView instrumentation：document-start 注入、附件环境检测、Promise 回传、日志和来源隔离。
- 视觉回归：手机和平板、浅色和深色、中文和英文关键页面。

### 15.2 设备矩阵

- API 29、31、33、35 或当时最新版本。
- 至少一台真实手机和一台平板或大屏模拟器。
- 权限状态覆盖首次请求、允许、拒绝、永久拒绝、系统关闭能力和设备不支持。
- Apilot 覆盖未安装、已安装兼容、已安装旧版、被停用、用户取消和 URI 回传。

### 15.3 发布门槛

- 系统返回不会从任何子页面直接退出应用。
- 用户附件在预览启动时显示“织雀已连接”，P0 每项要么成功，要么返回准确系统原因，不得显示假“不支持”。
- 首批模板各自主流程可操作，不再显示统一保存按钮。
- 已安装 Apilot 能正常打开；未安装时不自动跳转。
- 中文和英文提示词包含完全一致的 Runtime API 2.0 清单。
- 所有自动化测试、APK 构建、签名、Manifest 和设备 smoke test 通过。
- 每次发布同步更新 `VERSION`、`CHANGELOG.md`、`docs/DEVELOPMENT.md`、`docs/PROGRESS.md` 和第三方许可声明。

## 16. 明确不承诺的能力

- 不提供普通应用无法获得的系统签名权限。
- 不绕过 Android 权限、系统确认或隐私限制。
- 不承诺所有设备都能静默开关 Wi-Fi、蓝牙或互联网共享热点。
- 不在本轮允许用户编译任意 Kotlin/Java 插件。
- 不在模板组装器通过签名、覆盖更新和数据保留验收前宣称生成 APK 已完成。
