package com.telebackup.app.data.remote

import com.telebackup.app.data.remote.dto.TelegramResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * The bot token is part of the URL path: https://api.telegram.org/bot<TOKEN>/sendPhoto
 * We pass it dynamically so a single Retrofit instance can serve any token.
 */
interface TelegramApi {

    @Multipart
    @POST("bot{token}/sendPhoto")
    suspend fun sendPhoto(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("caption") caption: RequestBody,
        @Part photo: MultipartBody.Part,
    ): Response<TelegramResponse>

    @Multipart
    @POST("bot{token}/sendVideo")
    suspend fun sendVideo(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("caption") caption: RequestBody,
        @Part video: MultipartBody.Part,
    ): Response<TelegramResponse>

    /** Fallback for files that should keep original quality (sent as a file). */
    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part("caption") caption: RequestBody,
        @Part document: MultipartBody.Part,
    ): Response<TelegramResponse>
}
