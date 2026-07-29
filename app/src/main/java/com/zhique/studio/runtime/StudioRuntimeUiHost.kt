package com.zhique.studio.runtime

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.zhique.runtime.permission.RuntimeUiHost
import com.zhique.runtime.permission.ScreenCaptureAuthorization
import com.zhique.runtime.permission.VisualMediaType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
fun rememberStudioRuntimeUiHost(): StudioRuntimeUiHost {
    val activity = requireNotNull(LocalContext.current.findComponentActivity()) { "织雀预览需要 ComponentActivity。" }
    val host = remember(activity) { StudioRuntimeUiHost(activity) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        host.completePermissions(result)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { result ->
        host.completeCamera(result)
    }
    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { result ->
        host.completeContact(result)
    }
    val visualSingleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { result ->
        host.completeVisualMedia(result?.let(::listOf).orEmpty())
    }
    val visualMultipleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { result ->
        host.completeVisualMedia(result)
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        host.completeDocument(result)
    }
    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { result ->
        host.completeCreatedDocument(result)
    }
    val screenCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        host.completeScreenCapture(result.resultCode, result.data)
    }
    SideEffect {
        host.bind(permissionLauncher, cameraLauncher, contactLauncher, visualSingleLauncher, visualMultipleLauncher, documentLauncher, createDocumentLauncher, screenCaptureLauncher)
    }
    return host
}

class StudioRuntimeUiHost(
    override val activity: ComponentActivity
) : RuntimeUiHost {
    private val operationMutex = Mutex()
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var cameraLauncher: ActivityResultLauncher<Uri>? = null
    private var contactLauncher: ActivityResultLauncher<Void?>? = null
    private var visualSingleLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var visualMultipleLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var documentLauncher: ActivityResultLauncher<Array<String>>? = null
    private var createDocumentLauncher: ActivityResultLauncher<String>? = null
    private var screenCaptureLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingPermissions: CompletableDeferred<Map<String, Boolean>>? = null
    private var pendingCamera: CompletableDeferred<Boolean>? = null
    private var pendingContact: CompletableDeferred<Uri?>? = null
    private var pendingVisualMedia: CompletableDeferred<List<Uri>>? = null
    private var pendingDocument: CompletableDeferred<Uri?>? = null
    private var pendingCreatedDocument: CompletableDeferred<Uri?>? = null
    private var pendingScreenCapture: CompletableDeferred<ScreenCaptureAuthorization?>? = null

    fun bind(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        cameraLauncher: ActivityResultLauncher<Uri>,
        contactLauncher: ActivityResultLauncher<Void?>,
        visualSingleLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
        visualMultipleLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
        documentLauncher: ActivityResultLauncher<Array<String>>,
        createDocumentLauncher: ActivityResultLauncher<String>,
        screenCaptureLauncher: ActivityResultLauncher<Intent>
    ) {
        this.permissionLauncher = permissionLauncher
        this.cameraLauncher = cameraLauncher
        this.contactLauncher = contactLauncher
        this.visualSingleLauncher = visualSingleLauncher
        this.visualMultipleLauncher = visualMultipleLauncher
        this.documentLauncher = documentLauncher
        this.createDocumentLauncher = createDocumentLauncher
        this.screenCaptureLauncher = screenCaptureLauncher
    }

    override suspend fun requestPermissions(permissions: Set<String>): Map<String, Boolean> = operationMutex.withLock {
        val pending = CompletableDeferred<Map<String, Boolean>>()
        pendingPermissions = pending
        requireNotNull(permissionLauncher) { "Permission launcher is not ready." }.launch(permissions.toTypedArray())
        try {
            pending.await()
        } finally {
            if (pendingPermissions === pending) pendingPermissions = null
        }
    }

    override suspend fun takePicture(outputUri: Uri): Boolean = operationMutex.withLock {
        val pending = CompletableDeferred<Boolean>()
        pendingCamera = pending
        requireNotNull(cameraLauncher) { "Camera launcher is not ready." }.launch(outputUri)
        try {
            pending.await()
        } finally {
            if (pendingCamera === pending) pendingCamera = null
        }
    }

    override suspend fun pickContact(): Uri? = operationMutex.withLock {
        val pending = CompletableDeferred<Uri?>()
        pendingContact = pending
        requireNotNull(contactLauncher) { "Contact launcher is not ready." }.launch(null)
        try {
            pending.await()
        } finally {
            if (pendingContact === pending) pendingContact = null
        }
    }

    override suspend fun pickVisualMedia(type: VisualMediaType, multiple: Boolean): List<Uri> = operationMutex.withLock {
        val pending = CompletableDeferred<List<Uri>>()
        pendingVisualMedia = pending
        val mediaType = when (type) {
            VisualMediaType.Images -> ActivityResultContracts.PickVisualMedia.ImageOnly
            VisualMediaType.Video -> ActivityResultContracts.PickVisualMedia.VideoOnly
        }
        val request = PickVisualMediaRequest(mediaType)
        if (multiple) {
            requireNotNull(visualMultipleLauncher) { "Visual media launcher is not ready." }.launch(request)
        } else {
            requireNotNull(visualSingleLauncher) { "Visual media launcher is not ready." }.launch(request)
        }
        try {
            pending.await()
        } finally {
            if (pendingVisualMedia === pending) pendingVisualMedia = null
        }
    }

    override suspend fun openDocument(mimeTypes: Array<String>): Uri? = operationMutex.withLock {
        val pending = CompletableDeferred<Uri?>()
        pendingDocument = pending
        requireNotNull(documentLauncher) { "Document launcher is not ready." }.launch(mimeTypes)
        try {
            pending.await()
        } finally {
            if (pendingDocument === pending) pendingDocument = null
        }
    }

    override suspend fun createDocument(suggestedName: String): Uri? = operationMutex.withLock {
        val pending = CompletableDeferred<Uri?>()
        pendingCreatedDocument = pending
        requireNotNull(createDocumentLauncher) { "Create document launcher is not ready." }.launch(suggestedName)
        try {
            pending.await()
        } finally {
            if (pendingCreatedDocument === pending) pendingCreatedDocument = null
        }
    }

    override suspend fun requestScreenCapture(intent: Intent): ScreenCaptureAuthorization? = operationMutex.withLock {
        val pending = CompletableDeferred<ScreenCaptureAuthorization?>()
        pendingScreenCapture = pending
        requireNotNull(screenCaptureLauncher) { "Screen capture launcher is not ready." }.launch(intent)
        try {
            pending.await()
        } finally {
            if (pendingScreenCapture === pending) pendingScreenCapture = null
        }
    }

    override suspend fun confirmPrivateConfig(key: String): Boolean = suspendCancellableCoroutine { continuation ->
        activity.runOnUiThread {
            val dialog = AlertDialog.Builder(activity)
                .setTitle("保存私密配置")
                .setMessage("当前项目请求保存私密配置“$key”。该值仅加密保存在本机，不会写入项目文件或运行日志。")
                .setNegativeButton("取消") { _, _ -> if (continuation.isActive) continuation.resume(false) }
                .setPositiveButton("允许保存") { _, _ -> if (continuation.isActive) continuation.resume(true) }
                .setOnCancelListener { if (continuation.isActive) continuation.resume(false) }
                .create()
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }
    }

    fun completePermissions(result: Map<String, Boolean>) {
        pendingPermissions?.complete(result)
    }

    fun completeCamera(result: Boolean) {
        pendingCamera?.complete(result)
    }

    fun completeContact(result: Uri?) {
        pendingContact?.complete(result)
    }

    fun completeVisualMedia(result: List<Uri>) {
        pendingVisualMedia?.complete(result)
    }

    fun completeDocument(result: Uri?) {
        pendingDocument?.complete(result)
    }

    fun completeCreatedDocument(result: Uri?) {
        pendingCreatedDocument?.complete(result)
    }

    fun completeScreenCapture(resultCode: Int, resultData: Intent?) {
        pendingScreenCapture?.complete(resultData?.let { data -> ScreenCaptureAuthorization(resultCode, data) })
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
