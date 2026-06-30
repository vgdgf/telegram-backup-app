package com.telebackup.app.media

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

/**
 * Fires whenever MediaStore changes (new photo/video saved by any app).
 * We debounce because a single capture can emit several change events.
 */
class MediaContentObserver(
    private val onChange: () -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var lastTrigger = 0L
    private val debounceMs = 1500L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val now = System.currentTimeMillis()
        if (now - lastTrigger >= debounceMs) {
            lastTrigger = now
            onChange()
        }
    }

    companion object {
        val IMAGE_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val VIDEO_URI: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
}
