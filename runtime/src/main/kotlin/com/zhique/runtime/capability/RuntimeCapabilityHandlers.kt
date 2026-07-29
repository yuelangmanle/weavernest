package com.zhique.runtime.capability

import android.content.Context
import com.zhique.runtime.bridge.RuntimeCapabilityHandler
import com.zhique.runtime.bridge.RuntimeEventBus
import com.zhique.runtime.permission.PermissionBroker
import com.zhique.runtime.permission.RuntimeUiHost

object RuntimeCapabilityHandlers {
    fun create(context: Context, uiHost: RuntimeUiHost, eventBus: RuntimeEventBus): List<RuntimeCapabilityHandler> {
        val permissionBroker = PermissionBroker(uiHost)
        return listOf(
            CameraCapabilityHandler(context, uiHost, permissionBroker),
            GeolocationCapabilityHandler(context, permissionBroker, eventBus),
            StorageCapabilityHandler(context, uiHost),
            NotificationCapabilityHandler(context, permissionBroker),
            ContactsCapabilityHandler(context, uiHost, permissionBroker),
            MicrophoneCapabilityHandler(context, permissionBroker),
            ClipboardCapabilityHandler(context),
            HapticsCapabilityHandler(context),
            SensorCapabilityHandler(context, eventBus),
            ConfigCapabilityHandler(context, uiHost),
            NetworkCapabilityHandler(context),
            ImageMediaCapabilityHandler(uiHost),
            VideoMediaCapabilityHandler(uiHost),
            AudioMediaCapabilityHandler(context, uiHost),
            BluetoothLeCapabilityHandler(context, permissionBroker, eventBus),
            BluetoothClassicCapabilityHandler(context, permissionBroker),
            WifiScanCapabilityHandler(context, permissionBroker),
            WifiConnectionCapabilityHandler(context, permissionBroker),
            LocalHotspotCapabilityHandler(context, permissionBroker),
            ShareCapabilityHandler(context),
            SystemIntentCapabilityHandler(context),
            PhoneDialCapabilityHandler(context),
            CalendarCapabilityHandler(context, permissionBroker),
            BiometricCapabilityHandler(uiHost),
            SpeechCapabilityHandler(context, permissionBroker),
            ScreenCaptureCapabilityHandler(context, uiHost),
            NfcCapabilityHandler(context, uiHost),
            UsbCapabilityHandler(context),
            BackgroundTaskCapabilityHandler(context, permissionBroker)
        )
    }
}
