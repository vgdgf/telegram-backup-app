package com.telebackup.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UploadStatus { PENDING, UPLOADING, UPLOADED, FAILED, SKIPPED }

enum class MediaType { IMAGE, VIDEO }

/**
 * One row per discovered media file. The [contentHash] is the dedup key so
 * the same file is never uploaded twice, even if its path/URI changes.
 */
@Entity(
    tableName = "backup_items",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["status"]),
    ]
)
data class BackupItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val contentHash: String,
    val dateAddedSec: Long,
    val status: UploadStatus = UploadStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val telegramFileId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long? = null,
)
