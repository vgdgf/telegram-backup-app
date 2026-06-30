package com.telebackup.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.telebackup.app.data.local.dao.BackupDao
import com.telebackup.app.data.local.entity.BackupItem

@Database(
    entities = [BackupItem::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupDao(): BackupDao

    companion object {
        const val DB_NAME = "telebackup.db"
    }
}
