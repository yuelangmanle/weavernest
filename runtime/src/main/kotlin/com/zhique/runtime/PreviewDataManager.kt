package com.zhique.runtime

import android.content.Context
import android.webkit.WebStorage
import java.io.File

/** Clears ordinary preview application data without touching encrypted runtime configuration. */
class PreviewDataManager(context: Context) {
    private val appContext = context.applicationContext

    fun clearProjectData(projectId: String) {
        SharedPreferencesRuntimeDataStore(appContext).clear(projectId)
        File(appContext.filesDir, "zhique/runtime/$projectId/storage").deleteRecursively()
        WebStorage.getInstance().deleteOrigin(runtimeOriginFor(projectId))
    }

    fun exportEncryptedBackup(projectId: String, password: String): ByteArray =
        RuntimeProjectDataBackupManager(appContext).export(projectId, password)

    fun restoreEncryptedBackup(projectId: String, password: String, backup: ByteArray) =
        RuntimeProjectDataBackupManager(appContext).restore(projectId, password, backup)
}
