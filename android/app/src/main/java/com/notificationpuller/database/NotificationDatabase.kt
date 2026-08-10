package com.notificationpuller.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NotificationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        private const val DATABASE_NAME = "notification_puller.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN status TEXT NOT NULL DEFAULT 'posted'")
                database.execSQL("ALTER TABLE notifications ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE notifications ADD COLUMN removedAt INTEGER")
                database.execSQL("ALTER TABLE notifications ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE notifications ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE notifications ADD COLUMN isGroupSummary INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_status ON notifications(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isActive ON notifications(isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_groupKey ON notifications(groupKey)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE notifications ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isRead ON notifications(isRead)")
            }
        }

        @Volatile private var instance: NotificationDatabase? = null

        fun getInstance(context: Context): NotificationDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NotificationDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
