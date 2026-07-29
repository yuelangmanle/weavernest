package com.zhique.runtime.capability

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import java.util.concurrent.atomic.AtomicInteger

class ClipboardCapabilityHandler(context: Context) : RuntimeCapabilityHandler {
    override val capabilityId: String = "clipboard"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf("clipboard.read", "clipboard.write")
    private val clipboard = requireNotNull(appContext.getSystemService<ClipboardManager>())

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "clipboard.read" -> clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(appContext)?.toString()
        "clipboard.write" -> {
            val text = params["text"] as? String ?: throw IllegalArgumentException("text is required.")
            clipboard.setPrimaryClip(ClipData.newPlainText("Zhique", text))
            null
        }
        else -> throw IllegalArgumentException("Unsupported clipboard method: $method")
    }
}

class HapticsCapabilityHandler(context: Context) : RuntimeCapabilityHandler {
    override val capabilityId: String = "haptics"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf("haptics.vibrate", "haptics.impact")

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? {
        val duration = when (method) {
            "haptics.vibrate" -> (params["durationMs"] as? Number)?.toLong()?.coerceIn(1L, 10_000L)
                ?: throw IllegalArgumentException("durationMs must be a positive number.")
            "haptics.impact" -> when (params["style"] as? String) {
                "light" -> 20L
                "medium" -> 45L
                "heavy" -> 80L
                else -> throw IllegalArgumentException("style must be light, medium, or heavy.")
            }
            else -> throw IllegalArgumentException("Unsupported haptics method: $method")
        }
        if (Build.VERSION.SDK_INT >= 31) {
            appContext.getSystemService<VibratorManager>()?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService<Vibrator>()?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        return null
    }
}

class NotificationCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId: String = "notification"
    private val appContext = context.applicationContext

    override fun supports(method: String) = method in setOf("notification.requestPermission", "notification.show", "notification.cancel")
    private val notificationManager = requireNotNull(appContext.getSystemService<NotificationManager>())

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "notification.requestPermission" -> {
            permissionBroker.ensure(capabilityId)
            "granted"
        }

        "notification.show" -> {
            permissionBroker.ensure(capabilityId)
            val channelId = ((params["options"] as? Map<*, *>)?.get("channelId") as? String).orEmpty().ifBlank { defaultChannelId }
            ensureChannel(channelId)
            val title = params["title"] as? String ?: throw IllegalArgumentException("title is required.")
            val body = params["body"] as? String ?: throw IllegalArgumentException("body is required.")
            val id = notificationSequence.incrementAndGet()
            notificationManager.notify(id, android.app.Notification.Builder(appContext, channelId).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).build())
            mapOf("id" to id)
        }

        "notification.cancel" -> {
            val id = when (val value = params["id"]) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: throw IllegalArgumentException("id must be a notification number.")
                else -> throw IllegalArgumentException("id is required.")
            }
            notificationManager.cancel(id)
            null
        }

        else -> throw IllegalArgumentException("Unsupported notification method: $method")
    }

    private fun ensureChannel(channelId: String) {
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, "Zhique Runtime", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private companion object {
        const val defaultChannelId = "zhique_runtime"
        val notificationSequence = AtomicInteger(1_000)
    }
}
