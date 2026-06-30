package com.telebackup.app.di

import android.content.Context
import androidx.room.Room
import com.telebackup.app.data.local.AppDatabase
import com.telebackup.app.data.local.dao.BackupDao
import com.telebackup.app.data.prefs.SettingsManager
import com.telebackup.app.data.remote.TelegramApi
import com.telebackup.app.data.remote.TelegramUploader
import com.telebackup.app.data.repository.BackupRepository
import com.telebackup.app.media.MediaScanner
import com.telebackup.app.util.MediaCompressor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideDao(db: AppDatabase): BackupDao = db.backupDao()

    @Provides @Singleton
    fun provideSettings(@ApplicationContext ctx: Context) = SettingsManager(ctx)

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Headers only — never log bodies (would leak token in URL/data).
            level = HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)  // large uploads
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides @Singleton
    fun provideTelegramApi(client: OkHttpClient): TelegramApi =
        Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelegramApi::class.java)

    @Provides @Singleton
    fun provideCompressor(@ApplicationContext ctx: Context) = MediaCompressor(ctx)

    @Provides @Singleton
    fun provideScanner(@ApplicationContext ctx: Context) = MediaScanner(ctx)

    @Provides @Singleton
    fun provideUploader(
        @ApplicationContext ctx: Context,
        api: TelegramApi,
        compressor: MediaCompressor,
    ) = TelegramUploader(ctx, api, compressor)

    @Provides @Singleton
    fun provideRepository(
        dao: BackupDao,
        scanner: MediaScanner,
        uploader: TelegramUploader,
        settings: SettingsManager,
    ) = BackupRepository(dao, scanner, uploader, settings)
}
