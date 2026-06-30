package com.telebackup.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.telebackup.app.data.local.entity.BackupItem
import com.telebackup.app.data.local.entity.UploadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {

    /** Returns -1 if the hash already exists (IGNORE conflict). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: BackupItem): Long

    @Query("SELECT EXISTS(SELECT 1 FROM backup_items WHERE contentHash = :hash)")
    suspend fun existsByHash(hash: String): Boolean

    @Query("UPDATE backup_items SET status = :status, lastError = :error, retryCount = :retry, telegramFileId = :tgId, uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun updateStatus(
        id: Long,
        status: UploadStatus,
        error: String?,
        retry: Int,
        tgId: String?,
        uploadedAt: Long?,
    )

    /** Oldest-first queue of work that still needs uploading. */
    @Query(
        "SELECT * FROM backup_items WHERE status IN ('PENDING','FAILED') " +
            "AND retryCount < :maxRetries ORDER BY dateAddedSec ASC, id ASC LIMIT 1"
    )
    suspend fun nextPending(maxRetries: Int): BackupItem?

    @Query("SELECT COUNT(*) FROM backup_items WHERE status = 'UPLOADED'")
    fun uploadedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_items WHERE status IN ('PENDING','UPLOADING','FAILED')")
    fun remainingCount(): Flow<Int>

    @Query("SELECT * FROM backup_items ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<BackupItem>>

    @Query("UPDATE backup_items SET status = 'PENDING' WHERE status = 'UPLOADING'")
    suspend fun resetStuckUploads()
}
