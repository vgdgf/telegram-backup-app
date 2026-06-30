package com.telebackup.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stores all user settings. The Telegram bot token + chat id are sensitive,
 * so everything lives in an AES-256 EncryptedSharedPreferences file backed by
 * the Android Keystore. Nothing here is ever logged or transmitted anywhere
 * except to the user's own Telegram bot.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow(readSnapshot())
    val state: StateFlow<AppSettings> = _state

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_state.value)
        prefs.edit().apply {
            putString(KEY_TOKEN, updated.botToken)
            putString(KEY_CHAT_ID, updated.chatId)
            putBoolean(KEY_ENABLED, updated.backupEnabled)
            putBoolean(KEY_WIFI_ONLY, updated.wifiOnly)
            putBoolean(KEY_COMPRESS, updated.compressImages)
            putStringSet(KEY_FOLDERS, updated.monitoredFolders)
        }.apply()
        _state.value = updated
    }

    private fun readSnapshot() = AppSettings(
        botToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
        chatId = prefs.getString(KEY_CHAT_ID, "").orEmpty(),
        backupEnabled = prefs.getBoolean(KEY_ENABLED, false),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        compressImages = prefs.getBoolean(KEY_COMPRESS, true),
        monitoredFolders = prefs.getStringSet(KEY_FOLDERS, emptySet()) ?: emptySet(),
    )

    private companion object {
        const val KEY_TOKEN = "bot_token"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_ENABLED = "backup_enabled"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_COMPRESS = "compress_images"
        const val KEY_FOLDERS = "monitored_folders"
    }
}

data class AppSettings(
    val botToken: String = "",
    val chatId: String = "",
    val backupEnabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val compressImages: Boolean = true,
    /** Empty set = monitor everything (Camera, Screenshots, Downloads, etc.) */
    val monitoredFolders: Set<String> = emptySet(),
) {
    val isConfigured: Boolean get() = botToken.isNotBlank() && chatId.isNotBlank()
}
