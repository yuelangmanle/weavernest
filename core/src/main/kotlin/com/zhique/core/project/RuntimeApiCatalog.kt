package com.zhique.core.project

data class RuntimeApiMethod(
    val name: String,
    val capabilityId: String?,
    val parametersZh: String,
    val returnsZh: String,
    val parametersEn: String,
    val returnsEn: String
)

/**
 * The single source of truth for web code that is allowed to call the native runtime.
 * A method may be documented before its Android handler is released, but the runtime must
 * reject it with a structured error until the corresponding device verification passes.
 */
object RuntimeApiCatalog {
    const val apiVersion = "2.0"

    val methods = listOf(
        RuntimeApiMethod(
            name = "window.weaver.ready",
            capabilityId = null,
            parametersZh = "无参数",
            returnsZh = "{ runtime, apiVersion, androidApi, projectId, selectedCapabilities }",
            parametersEn = "no arguments",
            returnsEn = "{ runtime, apiVersion, androidApi, projectId, selectedCapabilities }"
        ),
        RuntimeApiMethod(
            name = "weaver.capabilities.list",
            capabilityId = null,
            parametersZh = "无参数",
            returnsZh = "能力状态数组",
            parametersEn = "no arguments",
            returnsEn = "an array of capability states"
        ),
        RuntimeApiMethod(
            name = "weaver.capabilities.status",
            capabilityId = null,
            parametersZh = "capabilityId: string",
            returnsZh = "{ id, selected, state, action? }",
            parametersEn = "capabilityId: string",
            returnsEn = "{ id, selected, state, action? }"
        ),
        RuntimeApiMethod(
            name = "weaver.data.get",
            capabilityId = null,
            parametersZh = "key: string",
            returnsZh = "string | null（项目隔离的兼容数据存储）",
            parametersEn = "key: string",
            returnsEn = "string | null (project-isolated compatibility data store)"
        ),
        RuntimeApiMethod(
            name = "weaver.data.set",
            capabilityId = null,
            parametersZh = "key: string, value: string",
            returnsZh = "void",
            parametersEn = "key: string, value: string",
            returnsEn = "void"
        ),
        RuntimeApiMethod(
            name = "weaver.camera.capture",
            capabilityId = "camera",
            parametersZh = "{ quality?: 0..1 }",
            returnsZh = "{ uri, mimeType, width?, height?, dataUrl? }",
            parametersEn = "{ quality?: 0..1 }",
            returnsEn = "{ uri, mimeType, width?, height?, dataUrl? }"
        ),
        RuntimeApiMethod(
            name = "weaver.geolocation.getCurrentPosition",
            capabilityId = "geolocation",
            parametersZh = "{ accuracy?: 'low' | 'balanced' | 'high', timeoutMs?: number }",
            returnsZh = "{ latitude, longitude, accuracyMeters, timestamp }",
            parametersEn = "{ accuracy?: 'low' | 'balanced' | 'high', timeoutMs?: number }",
            returnsEn = "{ latitude, longitude, accuracyMeters, timestamp }"
        ),
        RuntimeApiMethod(
            name = "weaver.storage.readFile",
            capabilityId = "storage",
            parametersZh = "path: 相对路径字符串",
            returnsZh = "string",
            parametersEn = "path: relative path string",
            returnsEn = "string"
        ),
        RuntimeApiMethod(
            name = "weaver.storage.writeFile",
            capabilityId = "storage",
            parametersZh = "path: 相对路径字符串, text: string",
            returnsZh = "{ path, bytes }",
            parametersEn = "path: relative path string, text: string",
            returnsEn = "{ path, bytes }"
        ),
        RuntimeApiMethod(
            name = "weaver.notification.requestPermission",
            capabilityId = "notification",
            parametersZh = "无参数",
            returnsZh = "'granted' | 'denied' | 'blocked' | 'not_required'",
            parametersEn = "no arguments",
            returnsEn = "'granted' | 'denied' | 'blocked' | 'not_required'"
        ),
        RuntimeApiMethod(
            name = "weaver.notification.show",
            capabilityId = "notification",
            parametersZh = "title: string, body: string, options?: { channelId?: string }",
            returnsZh = "{ id }",
            parametersEn = "title: string, body: string, options?: { channelId?: string }",
            returnsEn = "{ id }"
        ),
        RuntimeApiMethod(
            name = "weaver.contacts.pick",
            capabilityId = "contacts",
            parametersZh = "无参数",
            returnsZh = "{ name, phone?, email? } | null",
            parametersEn = "no arguments",
            returnsEn = "{ name, phone?, email? } | null"
        ),
        RuntimeApiMethod(
            name = "weaver.microphone.requestPermission",
            capabilityId = "microphone",
            parametersZh = "无参数",
            returnsZh = "'granted' | 'denied' | 'blocked'",
            parametersEn = "no arguments",
            returnsEn = "'granted' | 'denied' | 'blocked'"
        ),
        RuntimeApiMethod(
            name = "weaver.microphone.record",
            capabilityId = "microphone",
            parametersZh = "{ durationMs?: number }",
            returnsZh = "{ uri, mimeType, durationMs, dataUrl? }",
            parametersEn = "{ durationMs?: number }",
            returnsEn = "{ uri, mimeType, durationMs, dataUrl? }"
        ),
        RuntimeApiMethod(
            name = "weaver.clipboard.read",
            capabilityId = "clipboard",
            parametersZh = "无参数",
            returnsZh = "string | null",
            parametersEn = "no arguments",
            returnsEn = "string | null"
        ),
        RuntimeApiMethod(
            name = "weaver.clipboard.write",
            capabilityId = "clipboard",
            parametersZh = "text: string",
            returnsZh = "void",
            parametersEn = "text: string",
            returnsEn = "void"
        ),
        RuntimeApiMethod(
            name = "weaver.vibrate",
            capabilityId = "haptics",
            parametersZh = "durationMs: number",
            returnsZh = "void",
            parametersEn = "durationMs: number",
            returnsEn = "void"
        ),
        RuntimeApiMethod(
            name = "weaver.sensor.subscribe",
            capabilityId = "sensors",
            parametersZh = "type: 'accelerometer', listener: (sample) => void",
            returnsZh = "{ id, unsubscribe: () => void }",
            parametersEn = "type: 'accelerometer', listener: (sample) => void",
            returnsEn = "{ id, unsubscribe: () => void }"
        ),
        RuntimeApiMethod(
            name = "weaver.config.get",
            capabilityId = "config",
            parametersZh = "key: string",
            returnsZh = "string | null（私密值不进入源码或日志）",
            parametersEn = "key: string",
            returnsEn = "string | null (private values never enter source or logs)"
        ),
        RuntimeApiMethod(
            name = "weaver.config.set",
            capabilityId = "config",
            parametersZh = "key: string, value: string",
            returnsZh = "void",
            parametersEn = "key: string, value: string",
            returnsEn = "void"
        ),
        reserved("weaver.runtime.info", null, "无参数", "运行时与设备基础信息", "no arguments", "basic runtime and device information"),
        reserved("weaver.capabilities.request", null, "id: string", "请求能力或返回系统流程说明", "id: string", "requests a capability or returns the system-flow requirement"),
        reserved("weaver.capabilities.openSettings", null, "id: string", "打开对应的系统设置流程", "id: string", "opens the applicable system settings flow"),
        reserved("weaver.camera.recordVideo", "camera", "options: object", "视频 URI 与元数据", "options: object", "video URI and metadata"),
        reserved("weaver.camera.scanCode", "camera", "options: object", "扫码结果", "options: object", "scanned code result"),
        reserved("weaver.media.pickImages", "media_images", "options: object", "所选图片 URI 数组", "options: object", "selected image URI array"),
        reserved("weaver.media.pickVideo", "media_video", "options: object", "所选视频 URI", "options: object", "selected video URI"),
        reserved("weaver.media.pickAudio", "media_audio", "options: object", "所选音频 URI", "options: object", "selected audio URI"),
        reserved("weaver.media.save", "storage", "uri: string, collection: string", "已保存媒体的 URI", "uri: string, collection: string", "saved media URI"),
        reserved("weaver.audio.play", "media_audio", "source: string, options?: object", "播放状态", "source: string, options?: object", "playback state"),
        reserved("weaver.audio.pause", "media_audio", "无参数", "播放状态", "no arguments", "playback state"),
        reserved("weaver.audio.stop", "media_audio", "无参数", "播放状态", "no arguments", "playback state"),
        reserved("weaver.audio.state", "media_audio", "无参数", "当前播放状态", "no arguments", "current playback state"),
        reserved("weaver.microphone.stop", "microphone", "无参数", "已停止录音的 URI 与时长", "no arguments", "stopped recording URI and duration"),
        reserved("weaver.storage.deleteFile", "storage", "path: 相对路径", "void", "path: relative path", "void"),
        reserved("weaver.storage.list", "storage", "path?: 相对路径", "文件条目数组", "path?: relative path", "file entry array"),
        reserved("weaver.storage.pickFile", "storage", "options?: object", "用户选择的文件 URI", "options?: object", "user-selected file URI"),
        reserved("weaver.storage.createFile", "storage", "options: object", "新文件 URI", "options: object", "new file URI"),
        reserved("weaver.data.remove", null, "key: string", "void", "key: string", "void"),
        reserved("weaver.data.clear", null, "无参数", "void", "no arguments", "void"),
        reserved("weaver.geolocation.watchPosition", "geolocation", "options: object, listener: function", "订阅对象", "options: object, listener: function", "subscription object"),
        reserved("weaver.geolocation.clearWatch", "geolocation", "id: string", "void", "id: string", "void"),
        reserved("weaver.notification.cancel", "notification", "id: string", "void", "id: string", "void"),
        reserved("weaver.notification.schedule", "notification", "options: object", "计划通知 ID", "options: object", "scheduled notification ID"),
        reserved("weaver.contacts.list", "contacts", "options?: object", "联系人最小字段数组", "options?: object", "minimal contact-field array"),
        reserved("weaver.calendar.addEvent", "calendar", "event: object", "日历事件 ID", "event: object", "calendar event ID"),
        reserved("weaver.calendar.pickEvent", "calendar", "options?: object", "用户选择的日历事件", "options?: object", "user-selected calendar event"),
        reserved("weaver.calendar.list", "calendar", "options?: object", "日历事件数组", "options?: object", "calendar event array"),
        reserved("weaver.haptics.impact", "haptics", "style: string", "void", "style: string", "void"),
        reserved("weaver.bluetooth.scan", "bluetooth_le", "options: object, listener?: function", "扫描会话或设备数组", "options: object, listener?: function", "scan session or device array"),
        reserved("weaver.bluetooth.stopScan", "bluetooth_le", "无参数", "void", "no arguments", "void"),
        reserved("weaver.bluetooth.connect", "bluetooth_le", "id: string", "连接信息", "id: string", "connection information"),
        reserved("weaver.bluetooth.disconnect", "bluetooth_le", "id: string", "void", "id: string", "void"),
        reserved("weaver.bluetooth.discover", "bluetooth_le", "id: string", "服务与特征数组", "id: string", "service and characteristic array"),
        reserved("weaver.bluetooth.read", "bluetooth_le", "request: object", "字节数据", "request: object", "byte data"),
        reserved("weaver.bluetooth.write", "bluetooth_le", "request: object", "void", "request: object", "void"),
        reserved("weaver.bluetooth.subscribe", "bluetooth_le", "request: object, listener: function", "订阅对象", "request: object, listener: function", "subscription object"),
        reserved("weaver.bluetooth.unsubscribe", "bluetooth_le", "subscriptionId: string", "void", "subscriptionId: string", "void"),
        reserved("weaver.bluetooth.classic.listPaired", "bluetooth_classic", "无参数", "已配对设备数组", "no arguments", "paired device array"),
        reserved("weaver.bluetooth.classic.openSettings", "bluetooth_classic", "无参数", "启动系统蓝牙设置", "no arguments", "launches Android Bluetooth settings"),
        reserved("weaver.wifi.state", "wifi_scan", "无参数", "Wi-Fi 状态", "no arguments", "Wi-Fi state"),
        reserved("weaver.wifi.scan", "wifi_scan", "无参数", "可见网络数组", "no arguments", "visible network array"),
        reserved("weaver.wifi.requestConnection", "wifi_connect", "config: object", "系统连接确认结果", "config: object", "system connection confirmation result"),
        reserved("weaver.wifi.openSettings", "wifi_connect", "无参数", "void", "no arguments", "void"),
        reserved("weaver.hotspot.startLocalOnly", "local_hotspot", "无参数", "局部热点信息", "no arguments", "local-only hotspot information"),
        reserved("weaver.hotspot.stop", "local_hotspot", "无参数", "void", "no arguments", "void"),
        reserved("weaver.hotspot.state", "local_hotspot", "无参数", "热点状态", "no arguments", "hotspot state"),
        reserved("weaver.nfc.isAvailable", "nfc", "无参数", "是否可用", "no arguments", "availability result"),
        reserved("weaver.nfc.read", "nfc", "options?: object", "标签内容", "options?: object", "tag payload"),
        reserved("weaver.nfc.write", "nfc", "message: object", "写入结果", "message: object", "write result"),
        reserved("weaver.network.status", "network", "无参数", "连接状态", "no arguments", "connectivity state"),
        reserved("weaver.network.request", "network", "options: object", "受限 HTTPS 响应", "options: object", "restricted HTTPS response"),
        reserved("weaver.network.download", "network", "options: object", "下载文件 URI", "options: object", "downloaded file URI"),
        reserved("weaver.share.open", "share", "payload: object", "用户完成或取消结果", "payload: object", "user completion or cancellation result"),
        reserved("weaver.system.openUrl", "system_intents", "url: string", "启动结果", "url: string", "launch result"),
        reserved("weaver.system.dial", "phone_dial", "number: string", "启动结果", "number: string", "launch result"),
        reserved("weaver.system.openMap", "system_intents", "location: object", "启动结果", "location: object", "launch result"),
        reserved("weaver.system.appInfo", "system_intents", "无参数", "应用信息", "no arguments", "application information"),
        reserved("weaver.system.deviceInfo", "system_intents", "无参数", "非敏感设备信息", "no arguments", "non-sensitive device information"),
        reserved("weaver.biometric.authenticate", "biometric", "options?: object", "认证结果", "options?: object", "authentication result"),
        reserved("weaver.speech.recognize", "speech", "options?: object", "识别文本", "options?: object", "recognized text"),
        reserved("weaver.speech.speak", "speech", "text: string, options?: object", "朗读会话信息", "text: string, options?: object", "speech session information"),
        reserved("weaver.speech.stopSpeaking", "speech", "无参数", "void", "no arguments", "void"),
        reserved("weaver.screenCapture.request", "screen_capture", "options?: object", "系统授权结果", "options?: object", "system authorization result"),
        reserved("weaver.screenCapture.start", "screen_capture", "options?: object", "捕获会话信息", "options?: object", "capture session information"),
        reserved("weaver.screenCapture.stop", "screen_capture", "无参数", "void", "no arguments", "void"),
        reserved("weaver.usb.list", "usb", "无参数", "USB 设备数组", "no arguments", "USB device array"),
        reserved("weaver.usb.requestPermission", "usb", "deviceId: string", "授权结果", "deviceId: string", "permission result"),
        reserved("weaver.usb.open", "usb", "deviceId: string, options?: object", "连接信息", "deviceId: string, options?: object", "connection information"),
        reserved("weaver.usb.close", "usb", "deviceId: string", "void", "deviceId: string", "void"),
        reserved("weaver.background.schedule", "background_tasks", "task: object", "任务 ID", "task: object", "task ID"),
        reserved("weaver.background.cancel", "background_tasks", "id: string", "void", "id: string", "void"),
        reserved("weaver.background.list", "background_tasks", "无参数", "任务数组", "no arguments", "task array"),
        reserved("weaver.config.remove", "config", "key: string", "void", "key: string", "void")
    )

    val publicMethodNames: Set<String> = methods.mapTo(linkedSetOf()) { it.name }

    /** Methods backed by the current runtime handlers, rather than merely reserved for a later release. */
    private val implementedBridgeMethods = setOf(
        "runtime.ready", "capabilities.list", "capabilities.status", "capabilities.request", "capabilities.openSettings",
        "data.get", "data.set", "data.remove", "data.clear",
        "camera.capture",
        "geolocation.getCurrentPosition", "geolocation.watchPosition", "geolocation.clearWatch",
        "storage.readFile", "storage.writeFile", "storage.deleteFile", "storage.list", "storage.pickFile", "storage.createFile",
        "media.save", "media.pickImages", "media.pickVideo", "media.pickAudio",
        "audio.play", "audio.pause", "audio.stop", "audio.state",
        "notification.requestPermission", "notification.show", "notification.cancel",
        "contacts.pick", "microphone.requestPermission", "microphone.record",
        "clipboard.read", "clipboard.write", "haptics.vibrate", "haptics.impact", "sensor.subscribe",
        "config.get", "config.set", "config.remove",
        "network.status", "network.request", "network.download",
        "bluetooth.scan", "bluetooth.stopScan", "bluetooth.connect", "bluetooth.disconnect", "bluetooth.discover",
        "bluetooth.read", "bluetooth.write", "bluetooth.subscribe", "bluetooth.unsubscribe",
        "bluetooth.classic.listPaired", "bluetooth.classic.openSettings",
        "wifi.state", "wifi.scan", "wifi.requestConnection", "wifi.openSettings",
        "hotspot.startLocalOnly", "hotspot.stop", "hotspot.state",
        "nfc.isAvailable", "nfc.read", "nfc.write",
        "share.open", "system.openUrl", "system.openMap", "system.appInfo", "system.deviceInfo", "system.dial",
        "calendar.addEvent", "calendar.list", "calendar.pickEvent",
        "biometric.authenticate", "speech.recognize", "speech.speak", "speech.stopSpeaking",
        "screenCapture.request", "screenCapture.start", "screenCapture.stop",
        "usb.list", "usb.requestPermission", "usb.open", "usb.close",
        "background.schedule", "background.cancel", "background.list"
    )

    val implementedPublicMethodNames: Set<String> = methods
        .filter { isImplemented(it.name) }
        .mapTo(linkedSetOf()) { it.name }

    val p0CapabilityIds: Set<String> = setOf(
        "camera", "geolocation", "storage", "notification", "contacts", "microphone", "clipboard", "haptics", "sensors", "config"
    )

    fun contains(method: String): Boolean = method in publicMethodNames

    fun isImplemented(publicMethod: String): Boolean = bridgeMethodFor(publicMethod) in implementedBridgeMethods

    fun capabilityForBridgeMethod(method: String): String? = methods
        .firstOrNull { bridgeMethodFor(it.name) == method }
        ?.capabilityId

    fun containsBridgeMethod(method: String): Boolean = methods.any { bridgeMethodFor(it.name) == method }

    fun renderPromptContract(language: PromptLanguage): String = methods
        .filter { isImplemented(it.name) }
        .joinToString("\n") { method ->
        when (language) {
            PromptLanguage.ZhCn -> "- `${method.name}`：参数 ${method.parametersZh}；返回 ${method.returnsZh}。"
            PromptLanguage.En -> "- `${method.name}`: parameters ${method.parametersEn}; returns ${method.returnsEn}."
        }
    }

    private fun bridgeMethodFor(publicName: String): String = when (publicName) {
        "window.weaver.ready" -> "runtime.ready"
        "weaver.vibrate" -> "haptics.vibrate"
        else -> publicName.removePrefix("weaver.")
    }

    private fun reserved(
        name: String,
        capabilityId: String?,
        parametersZh: String,
        returnsZh: String,
        parametersEn: String,
        returnsEn: String
    ) = RuntimeApiMethod(name, capabilityId, parametersZh, returnsZh, parametersEn, returnsEn)
}
