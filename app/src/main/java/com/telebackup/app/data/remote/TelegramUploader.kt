package com.telebackup.app.data.remote

import android.content.Context
import android.net.Uri
import com.telebackup.app.data.local.entity.BackupItem
import com.telebackup.app.data.local.entity.MediaType
import com.telebackup.app.data.prefs.AppSettings
import com.telebackup.app.util.Constants
import com.telebackup.app.util.MediaCompressor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

sealed interface UploadResult {
    data class Success(val telegramFileId: String?) : UploadResult
    /** Recoverable: worker should retry later. */
    data class Retryable(val message: String) : UploadResult
    /** Permanent: do not retry (file too big, deleted, etc.). */
    data class Permanent(val message: String) : UploadResult
}

/**
 * Service layer that converts a [BackupItem] into the right Telegram Bot API
 * call, applying compression and choosing photo / video / document endpoints.
 */
class TelegramUploader(
    private val context: Context,
    private val api: TelegramApi,
    private val compressor: MediaCompressor,
) {

    suspend fun upload(item: BackupItem, settings: AppSettings): UploadResult {
        val uri = Uri.parse(item.uri)

        // Verify the source still exists (user may have deleted it).
        val exists = runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
        if (!exists) return UploadResult.Permanent("Source no longer exists")

        return when (item.mediaType) {
            MediaType.IMAGE -> uploadImage(item, uri, settings)
            MediaType.VIDEO -> uploadVideo(item, uri, settings)
        }
    }

    private suspend fun uploadImage(
        item: BackupItem,
        uri: Uri,
        settings: AppSettings,
    ): UploadResult {
        var tempFile: File? = null
        try {
            val part: MultipartBody.Part = if (settings.compressImages) {
                tempFile = compressor.compressImage(uri)
                if (tempFile != null) {
                    val body = tempFile.asRequestBody("image/jpeg".toMediaType())
                    MultipartBody.Part.createFormData("photo", item.displayName, body)
                } else {
                    streamPart("photo", uri, item)
                }
            } else {
                streamPart("photo", uri, item)
            }

            val response = api.sendPhoto(
                token = settings.botToken,
                chatId = settings.chatId.toBody(),
                caption = item.displayName.toBody(),
                photo = part,
            )
            return mapResponse(response.isSuccessful, response.body()?.ok, response.code(),
                response.body()?.description,
                response.body()?.result?.photo?.lastOrNull()?.fileId)
        } catch (e: Exception) {
            return UploadResult.Retryable(e.message ?: "network error")
        } finally {
            tempFile?.delete()
        }
    }

    private suspend fun uploadVideo(
        item: BackupItem,
        uri: Uri,
        settings: AppSettings,
    ): UploadResult {
        if (item.sizeBytes > Constants.MAX_TELEGRAM_FILE_BYTES) {
            return UploadResult.Permanent("Video exceeds 50MB Telegram bot limit")
        }
        return try {
            val part = streamPart("video", uri, item, mime = "video/mp4")
            val response = api.sendVideo(
                token = settings.botToken,
                chatId = settings.chatId.toBody(),
                caption = item.displayName.toBody(),
                video = part,
            )
            mapResponse(response.isSuccessful, response.body()?.ok, response.code(),
                response.body()?.description,
                response.body()?.result?.video?.fileId)
        } catch (e: Exception) {
            UploadResult.Retryable(e.message ?: "network error")
        }
    }

    private fun streamPart(
        field: String,
        uri: Uri,
        item: BackupItem,
        mime: String = if (item.mediaType == MediaType.IMAGE) "image/*" else "video/mp4",
    ): MultipartBody.Part {
        val body = UriRequestBody(context.contentResolver, uri, item.sizeBytes, mime)
        return MultipartBody.Part.createFormData(field, item.displayName, body)
    }

    private fun mapResponse(
        httpOk: Boolean,
        apiOk: Boolean?,
        code: Int,
        description: String?,
        fileId: String?,
    ): UploadResult {
        return when {
            httpOk && apiOk == true -> UploadResult.Success(fileId)
            // 429 (rate limit) and 5xx are retryable.
            code == 429 || code in 500..599 -> UploadResult.Retryable("HTTP $code: $description")
            // 400/401/403 usually mean bad token/chat or bad file -> permanent.
            code in 400..403 -> UploadResult.Permanent("HTTP $code: $description")
            else -> UploadResult.Retryable("Unexpected HTTP $code: $description")
        }
    }

    private fun String.toBody(): RequestBody =
        toRequestBody("text/plain".toMediaType())
}
