package com.jib.app.di

import com.jib.app.data.repository.StationRepository
import com.jib.app.data.repository.StationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindStationRepository(impl: StationRepositoryImpl): StationRepository
}
