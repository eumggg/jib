package com.jib.app.di

import android.content.Context
import androidx.room.Room
import com.jib.app.data.local.JibDatabase
import com.jib.app.data.local.StationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): JibDatabase =
        Room.databaseBuilder(ctx, JibDatabase::class.java, "jib.db").build()

    @Provides
    fun provideStationDao(db: JibDatabase): StationDao = db.stationDao()
}
