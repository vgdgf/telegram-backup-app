package com.telebackup.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Provides a Hilt-aware WorkManager so [UploadWorker] can have dependencies
 * injected. (The default WorkManager initializer is disabled in the manifest.)
 */
@HiltAndroidApp
class TeleBackupApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
