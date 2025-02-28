package com.example.wooauto.data.di

import com.example.wooauto.data.remote.adapters.CategoryDtoTypeAdapter
import com.example.wooauto.data.remote.adapters.FlexibleTypeAdapter
import com.example.wooauto.data.remote.adapters.OrderDtoTypeAdapter
import com.example.wooauto.data.remote.adapters.ProductDtoTypeAdapter
import com.example.wooauto.data.remote.api.WooCommerceApiService
import com.example.wooauto.data.remote.dto.CategoryDto
import com.example.wooauto.data.remote.dto.OrderDto
import com.example.wooauto.data.remote.dto.ProductDto
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
import com.google.gson.GsonBuilder
import com.google.gson.Gson
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.data.remote.interceptors.WooCommerceAuthInterceptor
import com.example.wooauto.data.remote.WooCommerceApiFactory
import com.example.wooauto.data.remote.WooCommerceApiFactoryImpl
import dagger.Binds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
/**
 * 网络模块
 * 提供网络相关的依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /**
     * 绑定WooCommerceApiFactory接口与其实现
     * @param factory WooCommerceApiFactoryImpl实例
     * @return WooCommerceApiFactory实例
     */
    @Binds
    @Singleton
    abstract fun bindWooCommerceApiFactory(
        factory: WooCommerceApiFactoryImpl
    ): WooCommerceApiFactory

    companion object {
        /**
         * 提供Gson实例
         * @return Gson实例
         */
        @Provides
        @Singleton
        fun provideGson(): Gson {
            return GsonBuilder()
                .registerTypeAdapter(Any::class.java, FlexibleTypeAdapter())
                .registerTypeAdapter(OrderDto::class.java, OrderDtoTypeAdapter())
                .registerTypeAdapter(ProductDto::class.java, ProductDtoTypeAdapter())
                .registerTypeAdapter(CategoryDto::class.java, CategoryDtoTypeAdapter())
                .create()
        }

        /**
         * 提供WooCommerce认证拦截器
         * @param config WooCommerce配置
         * @return WooCommerceAuthInterceptor实例
         */
        @Provides
        @Singleton
        fun provideWooCommerceAuthInterceptor(config: WooCommerceConfig): WooCommerceAuthInterceptor {
            return WooCommerceAuthInterceptor(config)
        }

        /**
         * 提供OkHttpClient实例
         * @param authInterceptor WooCommerce认证拦截器
         * @return OkHttpClient实例
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(authInterceptor: WooCommerceAuthInterceptor): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            return OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * 提供Retrofit实例
         * @param okHttpClient OkHttpClient实例
         * @param config WooCommerce配置
         * @return Retrofit实例
         */
        @Provides
        @Singleton
        fun provideRetrofit(okHttpClient: OkHttpClient, config: WooCommerceConfig, gson: Gson): Retrofit {
            // 从配置中获取baseUrl
            val baseUrl = runBlocking { config.siteUrl.first() }
            val apiBaseUrl = config.getBaseUrl(baseUrl)

            return Retrofit.Builder()
                .baseUrl(apiBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
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
} 