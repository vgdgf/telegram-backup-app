package com.telebackup.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telebackup.app.data.prefs.SettingsManager

/**
 * Restarts the backup service after a reboot, but only if the user had
 * backup enabled. Respects user choice (no silent always-on behaviour).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val settings = SettingsManager(context.applicationContext).state.value
            if (settings.backupEnabled && settings.isConfigured) {
                BackupForegroundService.start(context.applicationContext)
            }
        }
    }
}
