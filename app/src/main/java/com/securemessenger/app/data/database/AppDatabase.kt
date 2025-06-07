// مسیر: app/src/main/java/com/securemessenger/app/data/database/AppDatabase.kt
package com.securemessenger.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.securemessenger.app.data.models.ChatEntity
import com.securemessenger.app.data.models.ClientEntity
import com.securemessenger.app.data.models.MessageEntity
import com.securemessenger.app.data.models.MessageType

/**
 * کلاس تبدیل نوع پیام برای Room
 */
class Converters {
    @TypeConverter
    fun fromMessageType(messageType: MessageType): String {
        return messageType.name
    }

    @TypeConverter
    fun toMessageType(messageType: String): MessageType {
        return MessageType.valueOf(messageType)
    }
}

/**
 * پایگاه داده اصلی برنامه
 */
@Database(
    entities = [MessageEntity::class, ChatEntity::class, ClientEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun clientDao(): ClientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "secure_messenger_database"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Callback برای مقداردهی اولیه دیتابیس
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // اینجا می‌توانید داده‌های اولیه را وارد کنید
            }
        }

        /**
         * Migration برای نسخه‌های آینده دیتابیس
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // مثال migration برای آینده
                // database.execSQL("ALTER TABLE messages ADD COLUMN new_column TEXT")
            }
        }
    }
}