package com.telebackup.app.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.telebackup.app.data.local.entity.BackupItem
import com.telebackup.app.data.local.entity.MediaType
import com.telebackup.app.util.HashUtil

data class ScannedMedia(
    val mediaStoreId: Long,
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val dateAddedSec: Long,
)

/**
 * Reads images and videos from MediaStore (scoped, no full-storage access).
 * Works across Android 10-14 by using RELATIVE_PATH (API 29+).
 */
class MediaScanner(private val context: Context) {

    fun queryImagesAndVideos(sinceDateAddedSec: Long = 0): List<ScannedMedia> {
        return queryCollection(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            type = MediaType.IMAGE,
            since = sinceDateAddedSec,
        ) + queryCollection(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            type = MediaType.VIDEO,
            since = sinceDateAddedSec,
        )
    }

    private fun queryCollection(
        collection: Uri,
        type: MediaType,
        since: Long,
    ): List<ScannedMedia> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
        val args = arrayOf(since.toString())
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        val result = mutableListOf<ScannedMedia>()
        context.contentResolver.query(collection, projection, selection, args, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    result += ScannedMedia(
                        mediaStoreId = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameCol) ?: "media_$id",
                        relativePath = cursor.getString(pathCol) ?: "",
                        mediaType = type,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                    )
                }
            }
        return result
    }

    /** Builds a dedup-ready entity (computes content hash by reading the stream). */
    fun toBackupItem(media: ScannedMedia): BackupItem? {
        val hash = runCatching {
            HashUtil.quickHash(
                input = { context.contentResolver.openInputStream(media.uri)!! },
                fileSize = media.sizeBytes,
            )
        }.getOrNull() ?: return null

        return BackupItem(
            mediaStoreId = media.mediaStoreId,
            uri = media.uri.toString(),
            displayName = media.displayName,
            relativePath = media.relativePath,
            mediaType = media.mediaType,
            sizeBytes = media.sizeBytes,
            contentHash = hash,
            dateAddedSec = media.dateAddedSec,
        )
    }
}
