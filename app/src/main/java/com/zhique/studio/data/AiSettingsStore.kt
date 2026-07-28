package com.zhique.studio.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AiSettings(
    val endpoint: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val providerId: String = "deepseek",
    val protocolId: String = "openai_compatible"
)

class AiSettingsStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        "zhique_ai_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): AiSettings = AiSettings(
        endpoint = preferences.getString("endpoint", null) ?: AiSettings().endpoint,
        model = preferences.getString("model", null) ?: AiSettings().model,
        apiKey = preferences.getString("apiKey", null).orEmpty(),
        providerId = preferences.getString("providerId", null) ?: AiSettings().providerId,
        protocolId = preferences.getString("protocolId", null) ?: AiSettings().protocolId
    )

    fun save(settings: AiSettings) {
        preferences.edit()
            .putString("endpoint", settings.endpoint.trim().trimEnd('/'))
            .putString("model", settings.model.trim())
            .putString("apiKey", settings.apiKey.trim())
            .putString("providerId", settings.providerId.trim().ifBlank { "custom" })
            .putString("protocolId", settings.protocolId.trim().ifBlank { "openai_compatible" })
            .apply()
    }
}
