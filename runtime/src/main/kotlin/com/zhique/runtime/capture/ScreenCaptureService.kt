package com.zhique.runtime.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startIntent = intent ?: return START_NOT_STICKY
        val resultCode = startIntent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData = startIntent.parcelableIntent(EXTRA_RESULT_DATA) ?: return START_NOT_STICKY
        startForegroundCompat()
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData).also { mediaProjection ->
            val metrics = resources.displayMetrics
            imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, android.graphics.PixelFormat.RGBA_8888, 2)
            display = mediaProjection.createVirtualDisplay(
                "ZhiqueScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
            )
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCapture() }
            }, null)
        }
        active = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "织雀屏幕捕获", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("织雀正在捕获屏幕")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopCapture() {
        active = false
        display?.release(); display = null
        imageReader?.close(); imageReader = null
        val currentProjection = projection
        projection = null
        currentProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "zhique_screen_capture"
        private const val NOTIFICATION_ID = 7301

        @Volatile var active: Boolean = false
            private set

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            ContextCompat.startForegroundService(context, Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            })
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
            active = false
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableIntent(key: String): Intent? = if (Build.VERSION.SDK_INT >= 33) {
    getParcelableExtra(key, Intent::class.java)
} else {
    getParcelableExtra(key)
}
