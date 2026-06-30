package com.telebackup.app.data.remote

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * Streams a content:// Uri directly into the multipart request without first
 * copying the whole (possibly large) video into memory or temp storage.
 */
class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val sizeBytes: Long,
    private val mime: String,
) : RequestBody() {

    override fun contentType(): MediaType? = mime.toMediaTypeOrNull()

    override fun contentLength(): Long = sizeBytes

    override fun writeTo(sink: BufferedSink) {
        resolver.openInputStream(uri)?.use { input ->
            sink.writeAll(input.source())
        } ?: error("Unable to open stream for $uri")
    }
}
