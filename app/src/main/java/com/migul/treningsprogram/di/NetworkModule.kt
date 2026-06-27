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
            // H4 (v1.10.4): generation now STREAMS (Server-Sent Events). With streaming, readTimeout is
            // an INTER-EVENT stall guard, NOT a time-to-first-byte deadline: Anthropic emits continuous
            // content_block_delta + periodic ping events, so a healthy long generation never goes 180 s
            // without a read, while a genuinely hung stream still trips it. Previously (non-streaming)
            // time-to-first-byte ≈ the WHOLE generation time, so a heavy/slow generation crossed this
            // read deadline and timed out — the v1.10.3 regression this fix addresses.
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // Whole-call wall-clock ceiling. A healthy long STREAM must not be cut here, so this was
            // raised 240 s → 300 s (the per-read inter-event readTimeout above is the real stall guard;
            // this is only a belt-and-suspenders upper bound). The app-level generation deadline
            // (GENERATION_OVERALL_DEADLINE_MS = 360 s) still bounds the full multi-attempt flow above this.
            .callTimeout(300, TimeUnit.SECONDS)
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
