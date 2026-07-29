package com.zhique.core.project

data class CapabilityDefinition(
    val id: String,
    val title: String,
    val manifestPermissions: Set<String> = emptySet(),
    val minimumApi: Int = 29,
    val requiresSpecialSystemFlow: Boolean = false,
    val availabilityNote: String? = null,
    val aliases: Set<String> = emptySet()
)

data class CapabilityValidation(
    val manifestPermissions: Set<String>,
    val restrictedCapabilities: Set<String>,
    val unknownCapabilities: Set<String>
) {
    val isAllowed: Boolean
        get() = unknownCapabilities.isEmpty()
}

object CapabilityRegistry {
    private val definitions = listOf(
        CapabilityDefinition("camera", "相机", setOf("android.permission.CAMERA")),
        CapabilityDefinition("media_images", "图片和相册", availabilityNote = "Uses Android Photo Picker and returns only user-selected URIs."),
        CapabilityDefinition("media_audio", "音频媒体", availabilityNote = "Uses Android's document picker and returns only user-selected URIs."),
        CapabilityDefinition("media_video", "视频媒体", availabilityNote = "Uses Android Photo Picker and returns only user-selected URIs."),
        CapabilityDefinition("storage", "文件与存储", aliases = setOf("files")),
        CapabilityDefinition(
            "geolocation",
            "精确定位",
            setOf("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"),
            aliases = setOf("location")
        ),
        CapabilityDefinition(
            "background_location",
            "后台定位",
            setOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION"
            ),
            requiresSpecialSystemFlow = true,
            availabilityNote = "Android requires a separate settings flow after foreground location is granted."
        ),
        CapabilityDefinition(
            "notification",
            "通知",
            setOf("android.permission.POST_NOTIFICATIONS"),
            33,
            aliases = setOf("notifications")
        ),
        CapabilityDefinition("contacts", "联系人", setOf("android.permission.READ_CONTACTS")),
        CapabilityDefinition("microphone", "麦克风", setOf("android.permission.RECORD_AUDIO")),
        CapabilityDefinition("clipboard", "剪贴板"),
        CapabilityDefinition("haptics", "振动与触觉", setOf("android.permission.VIBRATE"), aliases = setOf("vibrate")),
        CapabilityDefinition("sensors", "运动和设备传感器", aliases = setOf("sensor")),
        CapabilityDefinition("config", "运行时私密配置"),
        CapabilityDefinition(
            "bluetooth_le",
            "低功耗蓝牙",
            setOf(
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT"
            ),
            availabilityNote = "Android 10-11 needs foreground location for scanning; Android 12+ uses Nearby devices permission."
        ),
        CapabilityDefinition(
            "bluetooth_classic",
            "经典蓝牙",
            setOf("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN", "android.permission.BLUETOOTH_CONNECT"),
            availabilityNote = "Android 12+ needs Nearby devices permission; ordinary apps cannot silently enable Bluetooth."
        ),
        CapabilityDefinition(
            "wifi_scan",
            "Wi-Fi 扫描",
            setOf(
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_WIFI_STATE",
                "android.permission.NEARBY_WIFI_DEVICES"
            ),
            requiresSpecialSystemFlow = true,
            availabilityNote = "Android 10-12 usually needs foreground location; Android 13+ uses Nearby Wi-Fi devices. Device and system restrictions apply."
        ),
        CapabilityDefinition(
            "wifi_connect",
            "Wi-Fi 连接辅助",
            setOf(
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_WIFI_STATE",
                "android.permission.NEARBY_WIFI_DEVICES"
            ),
            requiresSpecialSystemFlow = true,
            availabilityNote = "Android presents a system confirmation UI."
        ),
        CapabilityDefinition(
            "local_hotspot",
            "局部热点",
            setOf(
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_WIFI_STATE",
                "android.permission.NEARBY_WIFI_DEVICES"
            ),
            requiresSpecialSystemFlow = true,
            availabilityNote = "Only local-only hotspot APIs are available to ordinary applications."
        ),
        CapabilityDefinition("network", "网络和 API", setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")),
        CapabilityDefinition("calendar", "日历", setOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")),
        CapabilityDefinition("phone_dial", "拨号"),
        CapabilityDefinition("nfc", "NFC"),
        CapabilityDefinition("share", "系统分享"),
        CapabilityDefinition("system_intents", "系统 Intent 与设备信息"),
        CapabilityDefinition("biometric", "生物识别", setOf("android.permission.USE_BIOMETRIC")),
        CapabilityDefinition("speech", "语音识别与朗读"),
        CapabilityDefinition(
            "screen_capture",
            "屏幕捕获",
            requiresSpecialSystemFlow = true,
            availabilityNote = "Android 必须显示系统屏幕录制授权，并在持续捕获时显示前台服务通知。"
        ),
        CapabilityDefinition("usb", "USB Host 设备"),
        CapabilityDefinition("background_tasks", "后台任务", requiresSpecialSystemFlow = true),
        CapabilityDefinition(
            "manage_external_storage",
            "所有文件访问",
            setOf("android.permission.MANAGE_EXTERNAL_STORAGE"),
            requiresSpecialSystemFlow = true,
            availabilityNote = "Android only grants this through a special settings screen when it is justified."
        )
    )

    private val canonicalById = definitions.associateBy(CapabilityDefinition::id)
    private val canonicalIdByAlias = definitions.flatMap { definition ->
        definition.aliases.map { alias -> alias to definition.id }
    }.toMap()

    fun all(): List<CapabilityDefinition> = definitions.sortedBy(CapabilityDefinition::title)

    fun canonicalId(id: String): String? = when {
        id in canonicalById -> id
        else -> canonicalIdByAlias[id]
    }

    fun validate(capabilityIds: Set<String>): CapabilityValidation {
        val canonicalIds = capabilityIds.mapNotNull(::canonicalId).toSet()
        val selected = canonicalIds.mapNotNull(canonicalById::get)
        val unknown = capabilityIds.filterTo(linkedSetOf()) { canonicalId(it) == null }
        return CapabilityValidation(
            manifestPermissions = selected.flatMapTo(linkedSetOf()) { it.manifestPermissions },
            restrictedCapabilities = selected.filter { it.requiresSpecialSystemFlow }.mapTo(linkedSetOf()) { it.id },
            unknownCapabilities = unknown
        )
    }
}
