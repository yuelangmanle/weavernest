package com.zhique.studio.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.zhique.core.project.ProjectDocument
import com.zhique.core.project.PreviewDataPersistence
import com.zhique.runtime.PreviewDataManager
import com.zhique.runtime.RuntimeHostCallbacks
import com.zhique.runtime.RuntimeProject
import com.zhique.runtime.SharedPreferencesRuntimeDataStore
import com.zhique.runtime.WebRuntimeHost
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.bridge.RuntimeEventDispatchers
import com.zhique.runtime.bridge.RuntimeBridgeDispatcher
import com.zhique.runtime.bridge.RuntimeSession
import com.zhique.runtime.capability.RuntimeCapabilityHandlers
import com.zhique.runtime.permission.AndroidRuntimeCapabilityControls
import com.zhique.studio.runtime.rememberStudioRuntimeUiHost

@Composable
fun WeaverPreview(
    document: ProjectDocument,
    runToken: Long,
    isRunning: Boolean,
    onReady: () -> Unit,
    onLog: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestReady by rememberUpdatedState(onReady)
    val latestLog by rememberUpdatedState(onLog)
    val runtimeUiHost = rememberStudioRuntimeUiHost()
    val eventBus = remember { RuntimeEventBus(RuntimeEventDispatchers::main) }
    val previewDataManager = remember(context.applicationContext) { PreviewDataManager(context) }
    val host = remember(context.applicationContext, runtimeUiHost, eventBus) {
        WebRuntimeHost(
            context = context,
            dispatcher = RuntimeBridgeDispatcher(
                dataStore = SharedPreferencesRuntimeDataStore(context),
                handlers = RuntimeCapabilityHandlers.create(context, runtimeUiHost, eventBus),
                capabilityControls = AndroidRuntimeCapabilityControls(context, runtimeUiHost)
            ),
            eventBus = eventBus,
            callbacks = object : RuntimeHostCallbacks {
                override fun onPageReady() = latestReady()

                override fun onLog(message: String, isError: Boolean) = latestLog(message, isError)

                override fun onBlockedNavigation(url: String) {
                    latestLog("已拦截不可信页面导航：$url", true)
                }
            }
        )
    }

    DisposableEffect(host) {
        onDispose(host::close)
    }

    AndroidView(
        modifier = modifier,
        factory = { host.container }
    )

    LaunchedEffect(document.metadata.id, document.files, document.binaryAssets, document.metadata.capabilities, document.metadata.previewDataPersistence, runToken, isRunning) {
        if (!isRunning) {
            host.stop()
            return@LaunchedEffect
        }
        if (document.metadata.previewDataPersistence == PreviewDataPersistence.Ephemeral) {
            previewDataManager.clearProjectData(document.metadata.id)
        }
        host.load(
            project = RuntimeProject(document.metadata.id, document.files, document.binaryAssets),
            session = RuntimeSession(
                id = "${document.metadata.id}-$runToken",
                projectId = document.metadata.id,
                selectedCapabilities = document.metadata.capabilities,
                runtimeName = "preview",
                androidApi = android.os.Build.VERSION.SDK_INT
            )
        )
    }
}
