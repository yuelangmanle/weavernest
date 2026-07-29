package com.zhique.runtime.capability

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.permission.PermissionBroker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

object BackgroundTaskPolicy {
    fun requireType(value: String): String {
        require(value == "notification") { "Only native notification background tasks are supported." }
        return value
    }
}

class BackgroundTaskCapabilityHandler(
    context: Context,
    private val permissionBroker: PermissionBroker
) : RuntimeCapabilityHandler {
    override val capabilityId = "background_tasks"
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun supports(method: String) = method in setOf(
        "background.schedule",
        "background.cancel",
        "background.list"
    )

    override suspend fun invoke(method: String, params: Map<String, Any?>, session: RuntimeSession): Any? = when (method) {
        "background.schedule" -> schedule(params)
        "background.cancel" -> {
            workManager.cancelUniqueWork(workName(params.requiredBackgroundString("id")))
            null
        }
        "background.list" -> list()
        else -> throw IllegalArgumentException("Unsupported background method: $method")
    }

    private suspend fun schedule(params: Map<String, Any?>): Map<String, String> {
        val task = params["task"] as? Map<*, *> ?: throw IllegalArgumentException("task is required.")
        val type = BackgroundTaskPolicy.requireType(task["type"] as? String ?: "")
        permissionBroker.ensure("notification")
        val id = (task["id"] as? String).orEmpty().trim().ifBlank { UUID.randomUUID().toString() }.take(96)
        val title = (task["title"] as? String).orEmpty().ifBlank { "织雀后台任务" }.take(100)
        val body = (task["body"] as? String).orEmpty().ifBlank { "计划任务已执行。" }.take(500)
        val delay = ((task["delayMs"] as? Number)?.toLong() ?: 0L).coerceIn(0L, 7L * 24 * 60 * 60 * 1000)
        val request = OneTimeWorkRequestBuilder<RuntimeBackgroundWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("type", type).putString("title", title).putString("body", body).putString("taskId", id).build())
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        return mapOf("id" to id, "workId" to request.id.toString())
    }

    private suspend fun list(): Map<String, Any?> = withContext(Dispatchers.IO) {
        val tasks = workManager.getWorkInfosByTag(TAG).get().map { info ->
            mapOf("id" to info.id.toString(), "state" to info.state.name)
        }
        mapOf("tasks" to tasks)
    }

    private fun workName(id: String) = "zhique.background.$id"

    private companion object {
        const val TAG = "zhique-runtime-background"
    }
}

class RuntimeBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (inputData.getString("type") != "notification") return Result.failure()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "织雀后台任务", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(
            inputData.getString("taskId").hashCode(),
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(inputData.getString("title"))
                .setContentText(inputData.getString("body"))
                .setAutoCancel(true)
                .build()
        )
        return Result.success()
    }

    private companion object {
        const val CHANNEL = "zhique_runtime_background"
    }
}

private fun Map<String, Any?>.requiredBackgroundString(key: String): String = this[key] as? String
    ?: throw IllegalArgumentException("$key is required.")
