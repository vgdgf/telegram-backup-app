package com.telebackup.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TelegramResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("description") val description: String? = null,
    @SerializedName("error_code") val errorCode: Int? = null,
    @SerializedName("result") val result: TelegramMessage? = null,
)

data class TelegramMessage(
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("photo") val photo: List<TelegramPhotoSize>? = null,
    @SerializedName("video") val video: TelegramVideo? = null,
    @SerializedName("document") val document: TelegramDocument? = null,
)

data class TelegramPhotoSize(
    @SerializedName("file_id") val fileId: String,
    @SerializedName("file_size") val fileSize: Long? = null,
)

data class TelegramVideo(@SerializedName("file_id") val fileId: String)

data class TelegramDocument(@SerializedName("file_id") val fileId: String)
