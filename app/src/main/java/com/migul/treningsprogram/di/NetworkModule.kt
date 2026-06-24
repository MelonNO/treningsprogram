package com.migul.treningsprogram.di

import com.google.gson.Gson
import com.migul.treningsprogram.data.api.ClaudeApiService
import com.migul.treningsprogram.data.api.WgerApi
import com.migul.treningsprogram.data.preferences.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(preferencesManager: PreferencesManager): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Hard ceiling per call: if the TCP read is stuck for 180 s the read timeout fires,
            // but a callTimeout also guards against the rare case the whole call (connect +
            // waiting for first byte + streaming body) drags on beyond a reasonable wall-clock
            // limit. 240 s gives the model time to produce a large response on a slow link
            // while still guaranteeing the coroutine is eventually unblocked.
            .callTimeout(240, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val key = preferencesManager.apiKey.filter { it.code in 0x20..0x7E }
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", key)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideClaudeApiService(retrofit: Retrofit): ClaudeApiService =
        retrofit.create(ClaudeApiService::class.java)

    @Provides
    @Singleton
    @Named("wger")
    fun provideWgerOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("wger")
    fun provideWgerRetrofit(@Named("wger") client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://wger.de/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideWgerApi(@Named("wger") retrofit: Retrofit): WgerApi =
        retrofit.create(WgerApi::class.java)
}
