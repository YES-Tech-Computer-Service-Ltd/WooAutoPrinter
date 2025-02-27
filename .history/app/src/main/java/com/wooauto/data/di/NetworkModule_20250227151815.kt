package com.wooauto.data.di

import com.wooauto.data.remote.api.WooCommerceApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络模块
 * 提供网络相关的依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 提供OkHttpClient实例
     * @return OkHttpClient实例
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 提供Retrofit实例
     * @param okHttpClient OkHttpClient实例
     * @return Retrofit实例
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://your-woocommerce-site.com/wp-json/wc/v3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * 提供WooCommerceApiService实例
     * @param retrofit Retrofit实例
     * @return WooCommerceApiService实例
     */
    @Provides
    @Singleton
    fun provideWooCommerceApiService(retrofit: Retrofit): WooCommerceApiService {
        return retrofit.create(WooCommerceApiService::class.java)
    }
} 