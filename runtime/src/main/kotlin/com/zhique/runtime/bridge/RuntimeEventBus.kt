package com.zhique.runtime.bridge

import android.os.Handler
import android.os.Looper

data class RuntimeBridgeEvent(
    val subscriptionId: String,
    val payload: Any?
)

class RuntimeEventBus(
    private val dispatch: ((() -> Unit) -> Unit) = { task -> task() }
) {
    private var listener: ((RuntimeBridgeEvent) -> Unit)? = null

    fun attach(listener: (RuntimeBridgeEvent) -> Unit) {
        this.listener = listener
    }

    fun detach() {
        listener = null
    }

    fun emit(event: RuntimeBridgeEvent) {
        dispatch { listener?.invoke(event) }
    }
}

/** Runtime handlers may receive framework callbacks on binder threads; WebView delivery must be main-thread only. */
object RuntimeEventDispatchers {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun main(task: () -> Unit) {
        mainHandler.post(task)
    }
}

interface RuntimeLifecycleHandler {
    fun releaseSession(sessionId: String)
}
