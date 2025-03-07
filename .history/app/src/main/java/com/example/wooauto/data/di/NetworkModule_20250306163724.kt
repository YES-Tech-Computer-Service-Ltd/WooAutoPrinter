package com.example.wooauto.data.di

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.wooauto.data.remote.WooCommerceApi
import com.example.wooauto.data.remote.impl.WooCommerceApiImpl
import com.example.wooauto.data.remote.metadata.MetadataProcessorFactory

/**
 * 网络模块
 * 提供网络相关的依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

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
                .setLenient() // 增加容错性
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
            val baseUrl = runBlocking { 
                try {
                    val url = config.siteUrl.first()
                    if (url.isBlank()) {
                        Log.e("NetworkModule", "站点URL为空，将使用默认URL")
                        "https://example.com/"
                    } else {
                        // 确保URL以斜杠结尾
                        if (url.endsWith("/")) url else "$url/"
                    }
                } catch (e: Exception) {
                    Log.e("NetworkModule", "获取站点URL出错", e)
                    "https://example.com/"
                }
            }
            
            // 构建API基础URL
            val apiBaseUrl = if (baseUrl.contains("wp-json/wc/v3")) {
                // 如果已经包含API路径，直接使用
                baseUrl
            } else {
                // 否则，添加API路径
                if (baseUrl.endsWith("/")) {
                    "${baseUrl}wp-json/wc/v3/"
                } else {
                    "$baseUrl/wp-json/wc/v3/"
                }
            }
            
            Log.d("NetworkModule", "创建Retrofit实例，baseUrl: $apiBaseUrl")

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

        /**
         * 提供WooCommerceConfig实例
         * @param dataStore 数据存储实例
         * @return WooCommerceConfig实例
         */
        @Provides
        @Singleton
        fun provideWooCommerceConfig(dataStore: DataStore<Preferences>): WooCommerceConfig {
            return WooCommerceConfig(dataStore)
        }

        /**
         * 提供WooCommerceApi实例
         * @param config 本地配置
         * @return WooCommerceApi实例
         */
        @Provides
        @Singleton
        fun provideWooCommerceApi(config: WooCommerceConfig): WooCommerceApi {
            // 初始化元数据处理器注册表
            MetadataProcessorFactory.createDefaultRegistry()
            
            // 从本地配置构建远程配置对象
            val remoteConfig = runBlocking {
                com.example.wooauto.data.remote.WooCommerceConfig(
                    apiUrl = config.apiUrl.first(),
                    consumerKey = config.consumerKey.first(),
                    consumerSecret = config.consumerSecret.first()
                )
            }
            
            return WooCommerceApiImpl(remoteConfig)
        }
    }
} 