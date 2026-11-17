package com.klvw.wallpaper.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.klvw.wallpaper.data.model.FolderType

@Database(entities = [FolderEntity::class, StaticImageEntity::class], version = 3, exportSchema = false)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun staticImageDao(): StaticImageDao

    class Converters {
        @TypeConverter
        fun fromFolderType(value: FolderType): String = value.name

        @TypeConverter
        fun toFolderType(value: String): FolderType = FolderType.valueOf(value)
    }

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE folders ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
