package com.jib.app.di

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.jib.app.data.places.PlacesRepository
import com.jib.app.data.places.PlacesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlacesModule {
    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext ctx: Context): PlacesClient? =
        if (Places.isInitialized()) Places.createClient(ctx) else null
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlacesBindModule {
    @Binds
    @Singleton
    abstract fun bindPlacesRepository(impl: PlacesRepositoryImpl): PlacesRepository
}
