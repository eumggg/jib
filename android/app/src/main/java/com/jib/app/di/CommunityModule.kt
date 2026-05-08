package com.jib.app.di

import com.jib.app.data.remote.CommunityApiService
import com.jib.app.data.repository.CommunityRepository
import com.jib.app.data.repository.CommunityRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommunityNetworkModule {
    @Provides
    @Singleton
    fun provideCommunityApi(retrofit: Retrofit): CommunityApiService =
        retrofit.create(CommunityApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityModule {
    @Binds
    @Singleton
    abstract fun bindCommunityRepository(impl: CommunityRepositoryImpl): CommunityRepository
}
