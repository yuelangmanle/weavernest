# 织雀商业化产品重构实施计划

**目标：** 在保留现有项目数据和签名基线的前提下，将 `0.3.0-alpha` 重构为导航可靠、UI 统一、权限真实可用、模板可验证、Apilot 可互操作的 Android 创作器。

**架构：** 新增共享 `:runtime` Android Library 和 `:template-runtime` 模板应用；`:app` 保留为 Compose 创作器并按 feature 分包。先建立契约和自动化基线，再替换导航与 UI，随后按能力批次实现原生模块，最后验证模板 APK 组装。

**技术栈：** Kotlin、Jetpack Compose、Navigation Compose、StateFlow、AndroidX WebKit、WebViewAssetLoader、Activity Result API、Room、CodeMirror 6、OkHttp、WorkManager、CameraX、Media3、Nordic BLE、ZXing、Tink/Android Keystore。

## 0.5.0-alpha 实施快照（2026-07-29）

本计划的离线实现已推进到 Runtime 2.0、数据恢复与模板组装边界：Navigation Compose 返回栈、CodeMirror 6 本地编辑与语法诊断、Room 元数据索引、项目 workspace/快照、导入差异审核、双语 Prompt、主线程事件投递、项目复制/回收/ZIP 导出、Apilot V2 安装与启动失败状态、BLE GATT 深度桥、真实实验模板、预览与生成应用的加密公开数据备份、固定模板 APK 组装、项目密钥备份及 v2/v3 签名验证均已进入代码与自动化测试。

尚未满足完成定义的部分全部需要设备证据：API 29/31/33/最新系统权限矩阵、Apilot 已安装往返、模板可用性、生成 APK 安装/覆盖更新/数据保留、视觉无障碍与性能。详见 [QA 矩阵](QA_MATRIX.md)。这些项在通过前保持 Experimental 或技术预览状态。

## 全局约束

- 最低 Android API 29，目标 SDK 保持最新稳定版本。
- 所有 SDK、JDK、Gradle、npm、临时目录和构建产物只能放在 `E:\weavernest\.local` 与 `E:\weavernest\artifacts`。
- 不引入 GPL/AGPL 依赖；新增依赖必须锁版本并更新第三方声明。
- 每次可发布迭代同步更新版本号、版本代码、CHANGELOG、开发书和进度书。
- 外部代码和 AI 修改必须先展示差异与预览，用户确认后才能写入。
- 预览和生成 APK 必须依赖同一 `:runtime`，禁止维护两套 API 行为。

## 方案比较与选择

### 方案 A：继续在现有页面打补丁

优点是初期修改少。缺点是 778 行 UI、482 行 ViewModel、布尔页面状态和占位桥会继续互相影响；返回、权限、模板和设置无法形成稳定边界。此方案不采用。

### 方案 B：完全新建项目并迁移功能

优点是目录干净。缺点是容易丢失现有项目数据、更新签名、GitHub 发布链和已经验证的策略代码，且在较长时间内没有可安装版本。此方案不采用。

### 方案 C：纵向受控重构（采用）

先用回归测试固定现状和数据，再增加导航与运行时模块；每个版本都能安装、迁移旧数据和执行核心闭环。旧实现只有在替代实现通过验收后才删除。

## 版本里程碑

| 版本 | 目标 | 发布条件 |
| --- | --- | --- |
| `0.3.1-alpha` | 阻断性修复 | 返回栈、Apilot 包可见性/确认引导、环境桥提前注入、虚假模板下线 |
| `0.4.0-alpha` | 商业化产品壳与 P0 Runtime | 新 UI、项目迁移、CodeMirror、附件十项能力通过 |
| `0.5.0-alpha` | 扩展原生能力与真实模板 | 媒体、BLE、Wi-Fi/热点、NFC、系统能力及首批十个模板通过 |
| `0.6.0-alpha` | 本地生成 APK | 模板组装、项目签名、覆盖更新、数据保留和备份恢复通过 |
| `1.0.0-rc1` | 发布候选 | 全设备矩阵、无障碍、性能、安全和恢复演练通过 |

---

## Task 1：建立重构测试基线与用户附件夹具

**文件：**

- 创建 `app/src/androidTest/assets/permission-diagnostic.html`
- 创建 `app/src/androidTest/java/com/zhique/studio/runtime/PermissionDiagnosticFixtureTest.kt`
- 创建 `app/src/test/java/com/zhique/studio/navigation/BackPolicyTest.kt`
- 修改 `app/build.gradle.kts`

**产出接口：** `PermissionDiagnosticFixture`，为后续 Runtime 模块提供固定 HTML 验收输入。

- [ ] 将用户提供的权限诊断 HTML 原样加入 androidTest assets，并记录 SHA-256，防止测试夹具被无意修改。
- [ ] 添加测试，断言夹具包含十个声明能力：camera、geolocation、storage、notification、contacts、microphone、clipboard、vibrate、sensor、config。
- [ ] 添加测试，断言夹具启动后期望 `window.weaver` 在同步环境检测前已存在。
- [ ] 添加纯 Kotlin 返回策略测试，覆盖弹层、子路由、项目根、首页首次返回和首页二次返回。
- [ ] 运行 `./scripts/build.ps1 -Task ':app:testDebugUnitTest'`，确认新测试因生产接口不存在而失败。
- [ ] 提交 `test: add redesign regression fixtures`。

## Task 2：修复 Apilot 包可见性与安全引导（`0.3.1-alpha`）

**文件：**

- 修改 `app/src/main/AndroidManifest.xml`
- 拆分 `app/src/main/java/com/zhique/studio/integrations/ApilotV2.kt`
- 创建 `app/src/main/java/com/zhique/studio/integrations/ApilotAvailability.kt`
- 创建 `app/src/main/java/com/zhique/studio/integrations/ApilotLauncher.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/settings/apilot/ApilotRequiredDialog.kt`
- 测试 `app/src/test/java/com/zhique/studio/integrations/ApilotAvailabilityTest.kt`

**接口：**

```kotlin
sealed interface ApilotAvailability {
    data class InstalledCompatible(val versionName: String, val versionCode: Long) : ApilotAvailability
    data class InstalledIncompatible(val versionName: String?) : ApilotAvailability
    data object InstalledDisabled : ApilotAvailability
    data object NotInstalled : ApilotAvailability
}

interface ApilotDetector {
    fun detect(): ApilotAvailability
}
```

- [ ] 在 Manifest 添加 `<queries>`，同时声明 `com.example.api_manager` 包、PICK 和 IMPORT Intent。
- [ ] 测试已安装兼容、已安装但无 V2 action、已停用和未安装四种结果。
- [ ] 检测同时使用 PackageInfo 和 Intent resolve；实际启动捕获 `ActivityNotFoundException`，返回 `launch_failed`，不自动打开浏览器。
- [ ] 新增未安装引导对话框，三秒后启用“前往安装说明”，始终提供取消，永不自动跳转。
- [ ] 已安装不兼容时显示“打开 Apilot”“检查更新”“取消”，不显示“未安装”。
- [ ] 添加 Compose UI 测试，确认倒计时前按钮不可点、倒计时后仍需用户点击。
- [ ] 用安装了 Apilot 的 API 33/35 设备运行 PICK 和 IMPORT smoke test。
- [ ] 更新 `VERSION` 到 `0.3.1-alpha`、`versionCode`、CHANGELOG、开发书和进度书。
- [ ] 提交 `fix: detect and guide Apilot safely`。

## Task 3：建立 Navigation Compose 路由与返回栈（`0.3.1-alpha`）

**文件：**

- 创建 `app/src/main/java/com/zhique/studio/navigation/StudioRoute.kt`
- 创建 `app/src/main/java/com/zhique/studio/navigation/StudioNavHost.kt`
- 创建 `app/src/main/java/com/zhique/studio/navigation/HomeExitController.kt`
- 修改 `app/src/main/java/com/zhique/studio/MainActivity.kt`
- 修改 `app/src/main/java/com/zhique/studio/ui/ZhiqueApp.kt`
- 测试 `app/src/androidTest/java/com/zhique/studio/navigation/StudioNavigationTest.kt`

**接口：**

```kotlin
sealed interface StudioRoute {
    data object Home : StudioRoute
    data object Create : StudioRoute
    data object Templates : StudioRoute
    data class Project(val projectId: String, val destination: ProjectDestination) : StudioRoute
    data object Settings : StudioRoute
}

enum class ProjectDestination { Create, Run, Capabilities, Build }
```

- [ ] 为首页、创建、粘贴、模板、项目四区、项目设置和全局设置建立路由。
- [ ] 将根 Composable 的页面布尔变量迁移到路由或局部弹层状态。
- [ ] 添加统一 back dispatcher：先关弹层，再 popBackStack，再从项目回首页。
- [ ] 首页实现两秒二次返回退出；首次返回只显示 Snackbar。
- [ ] 顶部返回图标调用 `navigateUp()`，不得直接调用 `closeProject()`。
- [ ] 添加系统返回、返回手势和顶部返回的等价 UI 测试。
- [ ] 测试设置、模板、粘贴、项目运行页返回时 Activity 不结束。
- [ ] 提交 `refactor: add reliable studio navigation`。

## Task 4：下线虚假模板并建立模板发布状态（`0.3.1-alpha`）

**文件：**

- 创建 `core/src/main/kotlin/com/zhique/core/template/TemplateDefinition.kt`
- 创建 `app/src/main/java/com/zhique/studio/templates/TemplateRepository.kt`
- 修改 `app/src/main/java/com/zhique/studio/StudioViewModel.kt`
- 测试 `core/src/test/kotlin/com/zhique/core/template/TemplatePolicyTest.kt`

**接口：**

```kotlin
enum class TemplateStatus { Available, Experimental, Hidden }

data class TemplateDefinition(
    val id: String,
    val titleKey: String,
    val category: String,
    val status: TemplateStatus,
    val minimumApi: Int,
    val capabilities: Set<String>,
    val entryFile: String,
    val verificationScenario: String
)
```

- [ ] 测试 Available 模板必须有独立文件、能力清单和验收场景。
- [ ] 删除所有模板复用 `starterPage()` 的逻辑。
- [ ] 在真实能力完成前，将旧模板标记 Hidden；保留一个明确标记“仅数据存储示例”的离线表单模板。
- [ ] 模板中心显示可用、实验和系统限制状态，不显示 Hidden。
- [ ] 提交 `fix: remove placeholder capability templates`。

## Task 5：创建品牌主题与共享商业 UI 组件

**文件：**

- 创建 `app/src/main/java/com/zhique/studio/ui/theme/Color.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/theme/Type.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/theme/Shape.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/theme/ZhiqueTheme.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/components/StudioTopBar.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/components/StatusBanner.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/components/EmptyState.kt`
- 创建 `app/src/main/java/com/zhique/studio/ui/components/ProjectNavigation.kt`
- 测试 `app/src/test/java/com/zhique/studio/ui/theme/ThemeTokenTest.kt`

- [ ] 按 SPEC 固化浅色/深色颜色、排版、圆角、间距、状态色和动画时长。
- [ ] 从图标提取金色作为品牌强调，禁止整页黑金或棕色单色化。
- [ ] 所有触控目标至少 48dp；图标按钮添加 contentDescription。
- [ ] 创建手机 BottomNavigation 和平板 NavigationRail 的统一 destination 模型。
- [ ] 添加浅色/深色、中文/英文、手机/平板截图基线。
- [ ] 用无障碍扫描检查对比度、触控目标和阅读顺序。
- [ ] 提交 `feat: add Zhique product design system`。

## Task 6：重构首页、创建入口和项目工作区

**文件：**

- 创建 `app/src/main/java/com/zhique/studio/features/home/HomeScreen.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/home/HomeViewModel.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/create/CreateProjectScreen.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/project/ProjectShell.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/project/ProjectViewModel.kt`
- 删除迁移完成后的单体 `app/src/main/java/com/zhique/studio/ui/ZhiqueApp.kt`

- [ ] 首页实现品牌栏、四个创作入口、最近项目、项目菜单和空状态。
- [ ] 项目 Shell 固定四项导航：创作、运行、能力、发布。
- [ ] AI 与文件编辑放入创作页分段控件；数据管理放入项目设置。
- [ ] 运行入口在编辑器顶部和项目导航中保持一致。
- [ ] 平板宽度下启用 NavigationRail 和编辑/预览双栏。
- [ ] 添加项目创建、打开、切换页面、回首页和进程恢复 UI 测试。
- [ ] 提交 `feat: rebuild commercial creator workflow`。

## Task 7：项目仓库与旧数据迁移

**文件：**

- 创建 `app/src/main/java/com/zhique/studio/data/db/StudioDatabase.kt`
- 创建 `app/src/main/java/com/zhique/studio/data/db/ProjectEntity.kt`
- 创建 `app/src/main/java/com/zhique/studio/data/project/ProjectRepository.kt`
- 创建 `app/src/main/java/com/zhique/studio/data/project/LegacyProjectMigrator.kt`
- 测试 `app/src/test/java/com/zhique/studio/data/project/LegacyProjectMigratorTest.kt`

**接口：**

```kotlin
interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectSummary>>
    suspend fun loadProject(id: String): ProjectWorkspace
    suspend fun saveTextFile(id: String, path: String, content: String)
    suspend fun createSnapshot(id: String, reason: SnapshotReason): SnapshotId
}
```

- [ ] 元数据、能力和构建历史迁移到 Room。
- [ ] 文本与二进制资源迁移到每项目 workspace 目录，停止 Base64 JSON 存储。
- [ ] 迁移测试覆盖正常项目、缺字段、损坏资源、重复执行和磁盘不足。
- [ ] 成功迁移后保留只读旧 JSON 备份；失败时不修改原数据。
- [ ] ViewModel 改用 StateFlow 和 repository，禁止直接操作文件。
- [ ] 提交 `refactor: add resilient project repository`。

## Task 8：集成本地 CodeMirror 6 编辑器

**文件：**

- 创建 `editor/package.json`
- 创建 `editor/src/index.ts`
- 创建 `editor/src/bridge.ts`
- 创建 `app/src/main/java/com/zhique/studio/features/editor/CodeEditorView.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/editor/EditorViewModel.kt`
- 修改 `scripts/build.ps1`
- 测试 `app/src/androidTest/java/com/zhique/studio/features/editor/CodeEditorTest.kt`

- [ ] npm 缓存固定到 `.local/npm-cache`，构建输出固定到 app assets；禁止 CDN。
- [ ] 支持 HTML/CSS/JS/JSON 高亮、查找替换、缩进、撤销重做和诊断标记。
- [ ] 编辑内容通过版本化消息协议同步给 Android，500ms 防抖自动保存。
- [ ] 外部代码应用到当前项目时先显示逐文件 diff 和沙箱预览。
- [ ] 大于 2MB 文件进入只读保护并提示拆分，不阻塞整个应用。
- [ ] 添加旋转、后台恢复、中文输入法、长文本和多文件测试。
- [ ] 提交 `feat: add offline CodeMirror workspace`。

## Task 9：创建 `:runtime` 模块与安全 WebView 宿主

**文件：**

- 修改 `settings.gradle.kts`
- 创建 `runtime/build.gradle.kts`
- 创建 `runtime/src/main/java/com/zhique/runtime/WeaverRuntime.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/WebRuntimeHost.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/bridge/BridgeDispatcher.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/bridge/RuntimeBootstrap.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/bridge/WeaverError.kt`
- 测试 `runtime/src/androidTest/java/com/zhique/runtime/RuntimeInjectionTest.kt`

**接口：**

```kotlin
data class BridgeRequest(val requestId: String, val method: String, val params: JsonObject)
data class BridgeResponse(val requestId: String, val result: JsonElement?, val error: WeaverError?)

interface CapabilityHandler {
    val capabilityId: String
    suspend fun invoke(method: String, params: JsonObject, session: RuntimeSession): JsonElement
}
```

- [ ] 用 WebViewAssetLoader 从固定 HTTPS 本地域名加载项目。
- [ ] 在 document-start 创建完整 `window.weaver` 壳和 `ready()`；回退实现也必须早于用户脚本。
- [ ] 只暴露一个 JSON 消息入口，验证来源、会话、方法和参数大小。
- [ ] 实现 requestId Promise 回传、subscriptionId 事件和停止时资源清理。
- [ ] 添加外部导航拦截、混合内容禁用和不可信来源无法调用桥的测试。
- [ ] 使用附件测试断言同步 `hasWeaver` 为 true。
- [ ] 提交 `feat: add secure Weaver Runtime 2.0`。

## Task 10：能力注册表 2.0 与导入分析

**文件：**

- 重写 `core/src/main/kotlin/com/zhique/core/project/CapabilityRegistry.kt`
- 重写 `core/src/main/kotlin/com/zhique/core/project/CodeImportAnalyzer.kt`
- 创建 `core/src/main/kotlin/com/zhique/core/project/WeaverManifest.kt`
- 创建 `core/src/main/kotlin/com/zhique/core/project/RuntimeApiCatalog.kt`
- 测试 `core/src/test/kotlin/com/zhique/core/project/WeaverManifestTest.kt`

- [ ] 定义 API 2.0 canonical 名称、旧名称 alias、权限、最低 API、硬件和特殊流程。
- [ ] 解析 `weaver.json` 与 `weaver-required` 注释，未知能力必须在审核页显式显示。
- [ ] 检测 `weaver.*` 调用并对照 API catalog，报告拼写错误和不存在的方法。
- [ ] 继续识别 navigator/browser API，但只提出转换补丁，不自动覆盖。
- [ ] 用用户附件测试识别十项能力，不能再返回空能力集合。
- [ ] 提交 `feat: add versioned capability contract`。

## Task 11：权限代理与状态机

**文件：**

- 创建 `runtime/src/main/java/com/zhique/runtime/permission/PermissionBroker.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/permission/PermissionState.kt`
- 创建 `app/src/main/java/com/zhique/studio/runtime/StudioPermissionHost.kt`
- 测试 `runtime/src/test/java/com/zhique/runtime/permission/PermissionBrokerTest.kt`

- [ ] 实现 not_selected、unsupported_os、unsupported_device、not_declared、not_requested、granted、denied、blocked、special_flow_required、restricted。
- [ ] 普通权限直接执行；危险权限在用户动作后请求；永久拒绝提供设置动作；特殊能力打开对应系统流程。
- [ ] API 29/31/33/35 映射相机、定位、麦克风、通知、媒体、蓝牙和 Wi-Fi 权限差异。
- [ ] 拒绝、取消和返回设置未授权必须产生稳定错误码。
- [ ] 能力页显示同一状态机，不维护第二份 UI 判断逻辑。
- [ ] 提交 `feat: add Android permission broker`。

## Task 12：实现附件 P0 十项原生能力

**文件：**

- 创建 `runtime/src/main/java/com/zhique/runtime/capability/camera/CameraHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/location/GeolocationHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/storage/StorageHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/notification/NotificationHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/contacts/ContactsHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/audio/MicrophoneHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/system/ClipboardHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/system/HapticsHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/sensor/SensorHandler.kt`
- 创建 `runtime/src/main/java/com/zhique/runtime/capability/config/ConfigHandler.kt`

- [ ] 为每个 handler 先写参数、成功、拒绝、取消、不支持和资源释放测试。
- [ ] Camera 返回一次性 content URI 或受限 data URL；大图不得经 Binder/JSON 直接回传。
- [ ] Geolocation 支持超时和取消；不默认请求后台定位。
- [ ] Storage 的相对路径限定在项目沙箱；外部文件走 SAF。
- [ ] Notification 创建稳定 channel；API 33+ 按需请求权限。
- [ ] Contacts 优先系统选择器，只返回用户选择的最小字段。
- [ ] Microphone 加入 `RECORD_AUDIO`，录音超时后强制释放。
- [ ] Clipboard 只允许前台用户动作；Haptics 加 `VIBRATE`。
- [ ] Sensor 订阅停止、页面销毁和超时均释放监听器。
- [ ] Config 使用加密存储，API Key 不进入日志和项目源码。
- [ ] 在用户附件上逐项运行并记录 API29/31/33/35 结果。
- [ ] 提交 `feat: implement P0 native capabilities`。

## Task 13：运行页面、能力页面和错误诊断

**文件：**

- 创建 `app/src/main/java/com/zhique/studio/features/runtime/RuntimeScreen.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/runtime/RuntimeViewModel.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/runtime/RuntimeLogSheet.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/capabilities/CapabilitiesScreen.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/capabilities/CapabilityRow.kt`

- [ ] 运行状态显示准备中、运行中、等待权限、停止和错误。
- [ ] 日志按级别和来源分类，限制数量，自动脱敏。
- [ ] 每个能力显示项目选择、设备支持、Manifest、权限和最近测试结果。
- [ ] “发送给 AI 修复”只生成补丁和预览，不直接写文件。
- [ ] 添加权限等待期间返回、旋转、停止和重新运行测试。
- [ ] 提交 `feat: add trustworthy runtime diagnostics`。

## Task 14：中英文 Prompt Pack 2.0

**文件：**

- 重写 `core/src/main/kotlin/com/zhique/core/project/PromptPack.kt`
- 创建 `core/src/main/resources/prompts/zh-CN/runtime-2.0.md`
- 创建 `core/src/main/resources/prompts/en/runtime-2.0.md`
- 创建 `app/src/main/java/com/zhique/studio/features/prompts/PromptPackScreen.kt`
- 测试 `core/src/test/kotlin/com/zhique/core/project/PromptPackParityTest.kt`

**接口：**

```kotlin
enum class PromptLanguage { ZhCn, En }

data class PromptPack(
    val promptVersion: String,
    val runtimeApiVersion: String,
    val language: PromptLanguage,
    val content: String
)
```

- [ ] 两版都包含完整 API、能力清单、ready 流程、错误处理、私密配置和输出格式。
- [ ] 测试从两版提取的方法名集合完全一致。
- [ ] 中文界面默认复制中文；用户可明确复制英文。
- [ ] 内置 AI 系统提示和外部复制提示使用同一资源，不维护两份文本。
- [ ] 添加入参为用户附件需求时，输出必须使用存在的 API 且先等待 ready 的快照测试。
- [ ] 更新项目元数据 promptPackVersion 和迁移默认值。
- [ ] 提交 `feat: add bilingual Runtime 2.0 prompts`。

## Task 15：API 提供商和 Apilot 完整工作流

**文件：**

- 创建 `app/src/main/java/com/zhique/studio/features/settings/api/ApiProvidersScreen.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/settings/api/ApiProfileEditor.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/settings/api/ApiConnectionTester.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/settings/apilot/ApilotImportReviewScreen.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/settings/apilot/ApilotExportReviewScreen.kt`

- [ ] 设置改为独立页面，API profile 列表支持 DeepSeek/OpenAI/custom。
- [ ] 保存前校验 HTTPS、模型和 endpoint；测试连接返回脱敏结果。
- [ ] Apilot 导入先展示 scopes 和方案内容，确认后保存。
- [ ] 不含 Key 且 provider/base URL 改变时清除旧 Key并提示；相同连接允许用户选择保留。
- [ ] 导出 review 明确列出字段和 Key 是否包含；默认不含 Key。
- [ ] URI 在 60 秒内立即读取并关闭，日志不含 payload。
- [ ] 提交 `feat: complete API provider interoperability`。

## Task 16：实现扩展 Android 能力

**文件：** `runtime/src/main/java/com/zhique/runtime/capability/` 下按 media、bluetooth、wifi、nfc、calendar、biometric、speech、usb、network、background 分包。

- [ ] 媒体：Photo Picker、MediaStore、播放和后台限制。
- [ ] BLE：扫描、连接、服务发现、读写、通知和超时。
- [ ] 经典蓝牙：已配对设备和用户确认流程。
- [ ] Wi-Fi：状态、扫描、NetworkSpecifier 请求连接、系统限制。
- [ ] LocalOnlyHotspot：启动、停止、回调和设备不支持。
- [ ] NFC：前台标签读写和禁用状态。
- [ ] 日历、分享、拨号、地图和系统 Intent。
- [ ] 生物识别、语音识别、TTS、MediaProjection 和 USB 用户授权。
- [ ] WorkManager、前台服务、通知与电池限制。
- [ ] 每类在 API29/31/33/35 记录成功和限制结果。
- [ ] 提交按能力类别拆分，禁止一个巨型“all permissions”提交。

## Task 17：制作并验证真实模板

**文件：**

- 创建 `app/src/main/assets/templates/catalog.json`
- 在 `app/src/main/assets/templates/{templateId}/` 创建独立项目文件
- 创建 `app/src/androidTest/java/com/zhique/studio/templates/TemplateContractTest.kt`

- [ ] 权限诊断模板首先通过附件 P0 全部测试。
- [ ] 依次完成拍照识别、相册、音乐、录音、定位、联系人、BLE、Wi-Fi 和 API 面板。
- [ ] 每个模板添加能力清单、最低 API、截图、成功路径、拒绝路径和系统限制。
- [ ] 自动测试模板声明的方法都存在于 RuntimeApiCatalog。
- [ ] 只有通过真机矩阵的模板标记 Available。
- [ ] 提交 `feat: add verified capability templates`。

## Task 18：共享生成应用运行时与本地 APK 组装

**文件：**

- 创建 `template-runtime/build.gradle.kts`
- 创建 `template-runtime/src/main/...`
- 创建 `app/src/main/java/com/zhique/studio/build/ApkAssembler.kt`
- 创建 `app/src/main/java/com/zhique/studio/build/ProjectKeyStore.kt`
- 创建 `app/src/main/java/com/zhique/studio/features/build/BuildScreen.kt`

- [ ] 模板应用依赖同一个 `:runtime`，包含数据管理与运行时配置页面。
- [ ] 组装器只接受内置模板，替换包名、名称、版本、图标、Web 资产和所选权限。
- [ ] 每项目首次构建生成独立密钥；同项目后续沿用；不同项目密钥不同。
- [ ] v2/v3 签名、安装、覆盖更新和数据保留通过。
- [ ] 备份密码加密导出项目密钥，换机恢复后可继续更新同一应用。
- [ ] API29/31/33/35 验证预览与生成 APK API 返回一致。
- [ ] 提交 `feat: assemble signed project APKs locally`。

## Task 19：商业发布质量门禁

**文件：**

- 修改 `.github/workflows/android.yml`
- 创建 `docs/QA_MATRIX.md`
- 修改 `SECURITY.md`、`CONTRIBUTING.md`、`THIRD_PARTY_NOTICES.md`

- [ ] CI 运行 core/runtime/app 单元测试、Compose UI、instrumentation 可运行子集和 APK 构建。
- [ ] 校验版本一致、Manifest、依赖许可、APK 签名和 Release 资产。
- [ ] 性能门槛：冷启动、编辑 1MB 文件、运行预览、项目切换和内存回收。
- [ ] 无障碍门槛：对比度、文字缩放 200%、TalkBack、触控目标和横屏。
- [ ] 恢复门槛：迁移失败、磁盘满、WebView 崩溃、权限永久拒绝、Apilot 取消、下载失败。
- [ ] 安全门槛：不可信来源无法调用桥；Key、联系人、定位和文件正文不进日志。
- [ ] 每个版本更新 VERSION、versionCode、CHANGELOG、开发书、进度书和第三方声明。
- [x] GitHub Release 工作流只上传通过 CI 验证、并使用仓库 Secrets 签名的 APK。

## 完成定义

计划只有在以下条件同时满足时完成：

1. 用户附件在织雀预览和生成 APK 中均能识别环境。
2. P0 十项能力返回真实结果或准确的 Android 限制。
3. 任意子页面返回不会直接关闭软件。
4. UI 在手机/平板、中文/英文、浅色/深色下通过视觉和无障碍验收。
5. 已安装 Apilot 可完成导入/导出；未安装不会自动跳转。
6. 提示词中英双版与 Runtime 2.0 API 自动校验一致。
7. 所有公开模板都能执行其标称功能。
8. 同项目 APK 覆盖升级保留数据和签名身份。
