package com.zhique.studio.integrations

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.zhique.core.stabilization.ApilotAvailability
import com.zhique.core.stabilization.ApilotAvailabilityPolicy

object AndroidApilotDetector {
    fun detect(context: Context): ApilotAvailability {
        val packageManager = context.packageManager
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(ApilotV2.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(ApilotV2.packageName, 0)
            }
        }.getOrNull() ?: return ApilotAvailability.NotInstalled
        val applicationInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(ApilotV2.packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(ApilotV2.packageName, 0)
            }
        }.getOrNull()
        val pickSupported = ApilotV2.createPickIntent(includeApiKey = false).resolveActivity(packageManager) != null
        val importSupported = ApilotV2.createImportProbeIntent().resolveActivity(packageManager) != null
        return ApilotAvailabilityPolicy.classify(
            packageVisible = true,
            applicationEnabled = applicationInfo?.enabled == true,
            supportsPick = pickSupported,
            supportsImport = importSupported,
            versionName = packageInfo.versionName,
            versionCode = packageInfo.longVersionCode
        )
    }

    fun launchApplication(context: Context): Boolean = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(ApilotV2.packageName)
            ?: return false
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
