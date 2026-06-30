package com.telebackup.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.telebackup.app.R
import com.telebackup.app.data.repository.BackupRepository
import com.telebackup.app.media.MediaContentObserver
import com.telebackup.app.ui.MainActivity
import com.telebackup.app.util.Constants
import com.telebackup.app.worker.UploadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Always-on service. It registers a ContentObserver to detect new media,
 * keeps a persistent notification, and delegates the actual upload to
 * WorkManager so the heavy work survives the service being killed.
 */
@AndroidEntryPoint
class BackupForegroundService : Service() {

    @Inject lateinit var repository: BackupRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var observer: MediaContentObserver

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startAsForeground(getString(R.string.notification_idle))
        registerObserver()
        observeCounts()
        // Catch up on anything added while the app/service was off.
        scope.launch { handleMediaChange() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY => system recreates the service after it is killed.
        return START_STICKY
    }

    private fun registerObserver() {
        observer = MediaContentObserver { scope.launch { handleMediaChange() } }
        contentResolver.registerContentObserver(
            MediaContentObserver.IMAGE_URI, true, observer
        )
        contentResolver.registerContentObserver(
            MediaContentObserver.VIDEO_URI, true, observer
        )
    }

    private suspend fun handleMediaChange() {
        val settings = repository.settings.value
        if (!settings.backupEnabled || !settings.isConfigured) return
        repository.discoverNewMedia()
        // Always kick the worker; it no-ops if the queue is empty.
        UploadWorker.enqueue(applicationContext, settings.wifiOnly)
    }

    private fun observeCounts() {
        scope.launch {
            repository.remainingCount.collectLatest { remaining ->
                val text = if (remaining > 0) {
                    "Backing up… $remaining file(s) remaining"
                } else {
                    getString(R.string.notification_idle)
                }
                updateNotification(text)
            }
        }
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BackupForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.backup_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.backup_channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.telebackup.app.STOP"

        fun start(context: Context) {
            val intent = Intent(context, BackupForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackupForegroundService::class.java))
        }
    }
}
