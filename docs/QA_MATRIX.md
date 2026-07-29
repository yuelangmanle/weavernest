# 织雀 QA 矩阵

更新时间：2026-07-29；版本：`0.5.0-alpha`

## 自动化与离线门禁

| 范围 | 证据 | 状态 |
| --- | --- | --- |
| 核心策略、导入分析、双语 Prompt、版本更新状态 | `:core:test` | 通过 |
| Runtime 2.0 消息桥、来源校验、能力策略、事件调度 | `:runtime:testDebugUnitTest` | 通过 |
| BLE API 契约、加密运行时数据格式、来源校验、能力策略、事件调度 | `:core:test`、`:runtime:testDebugUnitTest` | 通过（真机硬件流程待测） |
| 工作区迁移、Room 元数据索引、快照、回收站、ZIP 导入/导出、应用壳编译 | `:app:testDebugUnitTest`、`:app:compileDebugKotlin` | 通过 |
| 模板运行时与图标资源 | `:template-runtime:compileDebugKotlin` | 通过 |
| 所有缓存、SDK、Gradle 和产物位置 | `scripts/build.ps1` | 固定到 `E:\weavernest\.local` / `artifacts` |

## Android 设备矩阵

以下项目必须在 API 29、31、33 和最新稳定版本的手机/平板上执行后，才可把模板状态从 Experimental 改为 Available：

| 场景 | API 29 | API 31 | API 33 | 最新稳定版 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| 粘贴、编辑、运行、停止、重启后数据 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |
| 相机、媒体、麦克风、通知 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |
| 定位、后台定位、传感器 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |
| BLE、经典蓝牙、Wi-Fi、局部热点 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |
| NFC、USB、日历、联系人、语音、生物识别 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |
| Apilot V2 已安装导入/导出与取消 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |
| 生成 APK 安装、同项目覆盖更新和数据保留 | 待测 | 待测 | 待测 | 待测 | 未宣称通过 |

设备测试记录必须包括型号、Android 版本、授予/拒绝/永久拒绝结果、系统限制、截图或 logcat 摘要；不得上传 API Key、联系人、位置或项目源码。
