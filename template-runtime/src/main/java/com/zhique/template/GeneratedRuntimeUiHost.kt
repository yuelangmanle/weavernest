package com.zhique.template

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
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
fun rememberGeneratedRuntimeUiHost(): GeneratedRuntimeUiHost {
    val activity = requireNotNull(LocalContext.current.findComponentActivity()) { "Generated runtime needs a ComponentActivity." }
    val host = remember(activity) { GeneratedRuntimeUiHost(activity) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), host::completePermissions)
    val picture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), host::completePicture)
    val contact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact(), host::completeContact)
    val image = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> host.completeVisual(uri?.let(::listOf).orEmpty()) }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), host::completeVisual)
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), host::completeDocument)
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), host::completeCreatedDocument)
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> host.completeCapture(result.resultCode, result.data) }
    SideEffect { host.bind(permissions, picture, contact, image, images, document, createDocument, capture) }
    return host
}

class GeneratedRuntimeUiHost(override val activity: ComponentActivity) : RuntimeUiHost {
    private val mutex = Mutex()
    private var permissions: ActivityResultLauncher<Array<String>>? = null
    private var picture: ActivityResultLauncher<Uri>? = null
    private var contact: ActivityResultLauncher<Void?>? = null
    private var visualSingle: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var visualMultiple: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var document: ActivityResultLauncher<Array<String>>? = null
    private var createDocument: ActivityResultLauncher<String>? = null
    private var capture: ActivityResultLauncher<Intent>? = null
    private var pendingPermissions: CompletableDeferred<Map<String, Boolean>>? = null
    private var pendingPicture: CompletableDeferred<Boolean>? = null
    private var pendingContact: CompletableDeferred<Uri?>? = null
    private var pendingVisual: CompletableDeferred<List<Uri>>? = null
    private var pendingDocument: CompletableDeferred<Uri?>? = null
    private var pendingCreatedDocument: CompletableDeferred<Uri?>? = null
    private var pendingCapture: CompletableDeferred<ScreenCaptureAuthorization?>? = null

    fun bind(
        permissions: ActivityResultLauncher<Array<String>>, picture: ActivityResultLauncher<Uri>, contact: ActivityResultLauncher<Void?>,
        visualSingle: ActivityResultLauncher<PickVisualMediaRequest>, visualMultiple: ActivityResultLauncher<PickVisualMediaRequest>,
        document: ActivityResultLauncher<Array<String>>, createDocument: ActivityResultLauncher<String>, capture: ActivityResultLauncher<Intent>
    ) {
        this.permissions = permissions; this.picture = picture; this.contact = contact; this.visualSingle = visualSingle
        this.visualMultiple = visualMultiple; this.document = document; this.createDocument = createDocument; this.capture = capture
    }

    override suspend fun requestPermissions(permissions: Set<String>): Map<String, Boolean> = await(pending = { pendingPermissions = it }, clear = { if (pendingPermissions === it) pendingPermissions = null }) { deferred -> requireNotNull(this.permissions) { "Permission launcher is not ready." }.launch(permissions.toTypedArray()) }
    override suspend fun takePicture(outputUri: Uri): Boolean = await(pending = { pendingPicture = it }, clear = { if (pendingPicture === it) pendingPicture = null }) { deferred -> requireNotNull(picture) { "Camera launcher is not ready." }.launch(outputUri) }
    override suspend fun pickContact(): Uri? = await(pending = { pendingContact = it }, clear = { if (pendingContact === it) pendingContact = null }) { deferred -> requireNotNull(contact) { "Contact launcher is not ready." }.launch(null) }
    override suspend fun pickVisualMedia(type: VisualMediaType, multiple: Boolean): List<Uri> = await(pending = { pendingVisual = it }, clear = { if (pendingVisual === it) pendingVisual = null }) { deferred ->
        val request = PickVisualMediaRequest(if (type == VisualMediaType.Images) ActivityResultContracts.PickVisualMedia.ImageOnly else ActivityResultContracts.PickVisualMedia.VideoOnly)
        if (multiple) requireNotNull(visualMultiple) { "Media launcher is not ready." }.launch(request) else requireNotNull(visualSingle) { "Media launcher is not ready." }.launch(request)
    }
    override suspend fun openDocument(mimeTypes: Array<String>): Uri? = await(pending = { pendingDocument = it }, clear = { if (pendingDocument === it) pendingDocument = null }) { deferred -> requireNotNull(document) { "File launcher is not ready." }.launch(mimeTypes) }
    override suspend fun createDocument(suggestedName: String): Uri? = await(pending = { pendingCreatedDocument = it }, clear = { if (pendingCreatedDocument === it) pendingCreatedDocument = null }) { deferred -> requireNotNull(createDocument) { "Create-file launcher is not ready." }.launch(suggestedName) }
    override suspend fun requestScreenCapture(intent: Intent): ScreenCaptureAuthorization? = await(pending = { pendingCapture = it }, clear = { if (pendingCapture === it) pendingCapture = null }) { deferred -> requireNotNull(capture) { "Screen-capture launcher is not ready." }.launch(intent) }

    override suspend fun confirmPrivateConfig(key: String): Boolean = suspendCancellableCoroutine { continuation ->
        activity.runOnUiThread {
            val dialog = AlertDialog.Builder(activity)
                .setTitle("保存私密配置")
                .setMessage("此应用请求保存私密配置“$key”。该值仅加密保存在本机，不会包含在 APK 或运行日志中。")
                .setNegativeButton("取消") { _, _ -> if (continuation.isActive) continuation.resume(false) }
                .setPositiveButton("允许保存") { _, _ -> if (continuation.isActive) continuation.resume(true) }
                .setOnCancelListener { if (continuation.isActive) continuation.resume(false) }
                .create()
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }
    }

    fun completePermissions(value: Map<String, Boolean>) { pendingPermissions?.complete(value) }
    fun completePicture(value: Boolean) { pendingPicture?.complete(value) }
    fun completeContact(value: Uri?) { pendingContact?.complete(value) }
    fun completeVisual(value: List<Uri>) { pendingVisual?.complete(value) }
    fun completeDocument(value: Uri?) { pendingDocument?.complete(value) }
    fun completeCreatedDocument(value: Uri?) { pendingCreatedDocument?.complete(value) }
    fun completeCapture(resultCode: Int, data: Intent?) { pendingCapture?.complete(data?.let { ScreenCaptureAuthorization(resultCode, it) }) }

    private suspend fun <T> await(pending: (CompletableDeferred<T>) -> Unit, clear: (CompletableDeferred<T>) -> Unit, launch: (CompletableDeferred<T>) -> Unit): T = mutex.withLock {
        val deferred = CompletableDeferred<T>(); pending(deferred); launch(deferred)
        try { deferred.await() } finally { clear(deferred) }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
