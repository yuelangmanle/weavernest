package com.zhique.runtime.permission

import android.app.Activity
import android.content.Intent
import android.net.Uri

enum class VisualMediaType { Images, Video }

data class ScreenCaptureAuthorization(val resultCode: Int, val resultData: Intent)

/** Bridges Activity Result APIs into runtime handlers without exposing an Activity to web code. */
interface RuntimeUiHost {
    val activity: Activity

    suspend fun requestPermissions(permissions: Set<String>): Map<String, Boolean>

    suspend fun takePicture(outputUri: Uri): Boolean

    suspend fun pickContact(): Uri?

    suspend fun pickVisualMedia(type: VisualMediaType, multiple: Boolean): List<Uri>

    suspend fun openDocument(mimeTypes: Array<String>): Uri?

    suspend fun createDocument(suggestedName: String): Uri?

    suspend fun requestScreenCapture(intent: Intent): ScreenCaptureAuthorization?

    /** A page may not silently persist a private API key or other secret. */
    suspend fun confirmPrivateConfig(key: String): Boolean
}
