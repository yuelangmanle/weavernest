package com.zhique.runtime.bridge

/** Only the active project's top-level HTTPS document may address the native runtime bridge. */
object RuntimeOriginPolicy {
    fun accepts(sourceOrigin: String, activeOrigin: String?, isMainFrame: Boolean): Boolean =
        isMainFrame && activeOrigin != null && sourceOrigin == activeOrigin
}
