package com.coursework.unifiedmail.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, FolderEntity::class, MessageEntity::class, OutboxEntity::class,
        AttachmentEntity::class, OutboxAttachmentEntity::class, DraftEntity::class,
    ],
    version = 16,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun outboxAttachmentDao(): OutboxAttachmentDao
    abstract fun draftDao(): DraftDao

    companion object {
        /** Adds the messages(folderName) index the cross-account (unified) inbox queries filter on. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_folderName` ON `messages` (`folderName`)")
            }
        }

        /** Adds an explicit per-account Sent folder override, matching trash/archive/spam's existing pattern — see AccountEntity.sentFolderFullName. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN sentFolderFullName TEXT")
            }
        }
    }
}
