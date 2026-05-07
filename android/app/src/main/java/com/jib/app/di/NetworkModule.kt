package com.jib.app.di

import com.jib.app.BuildConfig
import com.jib.app.data.remote.StationApiService
import com.jib.app.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                            else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("${BuildConfig.API_BASE_URL}/api/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideStationApi(retrofit: Retrofit): StationApiService =
        retrofit.create(StationApiService::class.java)
}
