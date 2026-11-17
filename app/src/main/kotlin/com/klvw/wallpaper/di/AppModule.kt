package com.klvw.wallpaper.di

import android.content.Context
import androidx.room.Room
import com.klvw.wallpaper.data.db.AppDatabase
import com.klvw.wallpaper.data.db.FolderDao
import com.klvw.wallpaper.data.db.StaticImageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "klvw_db")
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideStaticImageDao(db: AppDatabase): StaticImageDao = db.staticImageDao()
}
