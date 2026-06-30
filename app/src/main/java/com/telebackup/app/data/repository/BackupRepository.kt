package com.telebackup.app.data.repository

import com.telebackup.app.data.local.dao.BackupDao
import com.telebackup.app.data.local.entity.BackupItem
import com.telebackup.app.data.local.entity.UploadStatus
import com.telebackup.app.data.prefs.AppSettings
import com.telebackup.app.data.prefs.SettingsManager
import com.telebackup.app.data.remote.TelegramUploader
import com.telebackup.app.data.remote.UploadResult
import com.telebackup.app.media.MediaScanner
import com.telebackup.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for backup operations. The Service / Worker / UI all
 * go through here, keeping the data flow unidirectional (Clean architecture).
 */
class BackupRepository(
    private val dao: BackupDao,
    private val scanner: MediaScanner,
    private val uploader: TelegramUploader,
    private val settingsManager: SettingsManager,
) {
    val settings: StateFlow<AppSettings> get() = settingsManager.state
    val uploadedCount: Flow<Int> get() = dao.uploadedCount()
    val remainingCount: Flow<Int> get() = dao.remainingCount()
    fun recent(limit: Int = 30): Flow<List<BackupItem>> = dao.recent(limit)

    fun updateSettings(transform: (AppSettings) -> AppSettings) =
        settingsManager.update(transform)

    /**
     * Discovers media not yet tracked and inserts it as PENDING.
     * Deduplication is enforced by the unique contentHash index.
     * @return number of newly queued items.
     */
    suspend fun discoverNewMedia(): Int {
        val current = settingsManager.state.value
        val folders = current.monitoredFolders
        var queued = 0

        scanner.queryImagesAndVideos().forEach { media ->
            // Folder filter (empty = all folders).
            if (folders.isNotEmpty() && folders.none { media.relativePath.contains(it, true) }) {
                return@forEach
            }
            val item = scanner.toBackupItem(media) ?: return@forEach
            if (dao.existsByHash(item.contentHash)) return@forEach
            if (dao.insert(item) != -1L) queued++
        }
        return queued
    }

    /** Cleans up rows left in UPLOADING after a crash/restart. */
    suspend fun recoverStuck() = dao.resetStuckUploads()

    /**
     * Uploads exactly ONE item from the queue (oldest first) then returns
     * whether more work remains. Designed to be looped by the Worker.
     */
    suspend fun uploadNext(): QueueOutcome {
        val settings = settingsManager.state.value
        if (!settings.isConfigured) return QueueOutcome.NOT_CONFIGURED

        val item = dao.nextPending(Constants.MAX_UPLOAD_RETRIES)
            ?: return QueueOutcome.EMPTY

        dao.updateStatus(item.id, UploadStatus.UPLOADING, null, item.retryCount, null, null)

        return when (val result = uploader.upload(item, settings)) {
            is UploadResult.Success -> {
                dao.updateStatus(
                    item.id, UploadStatus.UPLOADED, null, item.retryCount,
                    result.telegramFileId, System.currentTimeMillis(),
                )
                QueueOutcome.UPLOADED
            }
            is UploadResult.Permanent -> {
                dao.updateStatus(
                    item.id, UploadStatus.SKIPPED, result.message,
                    Constants.MAX_UPLOAD_RETRIES, null, null,
                )
                QueueOutcome.UPLOADED // move on to next
            }
            is UploadResult.Retryable -> {
                dao.updateStatus(
                    item.id, UploadStatus.FAILED, result.message,
                    item.retryCount + 1, null, null,
                )
                QueueOutcome.RETRY_LATER
            }
        }
    }
}

enum class QueueOutcome { UPLOADED, EMPTY, RETRY_LATER, NOT_CONFIGURED }
