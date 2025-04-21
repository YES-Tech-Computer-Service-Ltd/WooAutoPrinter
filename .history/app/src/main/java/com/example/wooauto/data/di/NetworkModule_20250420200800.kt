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
import okhttp3.Protocol
import com.example.wooauto.data.remote.interceptors.SSLErrorInterceptor
import android.content.Context
import com.example.wooauto.data.remote.interceptors.NetworkStatusInterceptor
import com.example.wooauto.data.remote.ConnectionResetHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.wooauto.data.remote.ssl.SslUtil

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
         * 提供NetworkStatusInterceptor实例
         * @param context 应用上下文
         * @return NetworkStatusInterceptor实例
         */
        @Provides
        @Singleton
        fun provideNetworkStatusInterceptor(@ApplicationContext context: Context): NetworkStatusInterceptor {
            return NetworkStatusInterceptor(context)
        }

        /**
         * 提供OkHttpClient实例
         * @param authInterceptor WooCommerce认证拦截器
         * @param sslErrorInterceptor SSL错误拦截器
         * @param networkStatusInterceptor 网络状态拦截器
         * @return OkHttpClient实例
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(authInterceptor: WooCommerceAuthInterceptor, 
                                sslErrorInterceptor: SSLErrorInterceptor,
                                networkStatusInterceptor: NetworkStatusInterceptor): OkHttpClient {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            Log.d("NetworkModule", "配置OkHttpClient，强制使用HTTP/1.1协议以避免HTTP/2 PROTOCOL_ERROR")

            // 创建信任所有证书的SSL套接字工厂
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            // 创建SSL上下文，指定TLS最低版本
            val sslContext = try {
                // 优先尝试TLS 1.3（安卓10+）
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    Log.d("NetworkModule", "使用TLS 1.3协议")
                    javax.net.ssl.SSLContext.getInstance("TLSv1.3")
                } else {
                    Log.d("NetworkModule", "使用TLS 1.2协议")
                    javax.net.ssl.SSLContext.getInstance("TLSv1.2")
                }
            } catch (e: Exception) {
                // 退回到TLS
                Log.w("NetworkModule", "无法创建高版本TLS，退回到标准TLS: ${e.message}")
                javax.net.ssl.SSLContext.getInstance("TLS")
            }
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // 获取TrustManager
            val trustManager = trustAllCerts[0] as javax.net.ssl.X509TrustManager
            
            // 创建Socket工厂
            val sslSocketFactory = sslContext.socketFactory

            // 收集一些网络状态信息
            Log.d("NetworkModule", "系统信息: ${System.getProperty("os.version")}, SDK: ${android.os.Build.VERSION.SDK_INT}")

            val builder = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .addInterceptor(sslErrorInterceptor)
                .addInterceptor(networkStatusInterceptor)
                // 添加更宽松的超时设置
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                // 强制使用HTTP/1.1协议，解决PROTOCOL_ERROR
                .protocols(listOf(Protocol.HTTP_1_1))
                // 添加自定义SSL工厂以解决SSL握手问题
                .sslSocketFactory(sslSocketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                // 配置连接池，提高连接复用效率
                .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                // 启用重试
                .retryOnConnectionFailure(true)
                
            // 配置TLS版本支持
            SslUtil.configureTls(builder)
                
            return builder.build()
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
                    val url = config.siteUrl.first().trim() // 添加trim()移除所有空白字符
                    if (url.isBlank()) {
                        Log.e("NetworkModule", "站点URL为空，将使用默认URL")
                        "https://example.com/"
                    } else {
                        // 确保URL以斜杠结尾且没有换行符
                        if (url.endsWith("/")) url.trim() else "${url.trim()}/"
                    }
                } catch (e: Exception) {
                    Log.e("NetworkModule", "获取站点URL出错", e)
                    "https://example.com/"
                }
            }
            
            Log.d("NetworkModule", "原始站点URL: $baseUrl")
            
            // 构建API基础URL
            val apiBaseUrl = if (baseUrl.contains("wp-json/wc/v3")) {
                // 如果已经包含API路径，直接使用
                baseUrl.trim()
            } else {
                // 否则，添加API路径
                if (baseUrl.endsWith("/")) {
                    "${baseUrl.trim()}wp-json/wc/v3/".trim()
                } else {
                    "${baseUrl.trim()}/wp-json/wc/v3/".trim()
                }
            }
            
            Log.d("NetworkModule", "创建Retrofit实例，baseUrl: $apiBaseUrl")
            
            try {
                return Retrofit.Builder()
                    .baseUrl(apiBaseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
            } catch (e: IllegalArgumentException) {
                // 如果发生URL错误，记录详细信息并尝试修复
                Log.e("NetworkModule", "创建Retrofit实例时出错：${e.message}，URL='$apiBaseUrl'")
                
                // 尝试进一步清理URL
                val cleanUrl = apiBaseUrl.replace(Regex("\\s+"), "")
                Log.d("NetworkModule", "尝试清理后的URL: $cleanUrl")
                
                return Retrofit.Builder()
                    .baseUrl(cleanUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
            }
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
         * @param sslErrorInterceptor SSL错误拦截器
         * @param connectionResetHandler 连接重置处理器
         * @return WooCommerceApi实例
         */
        @Provides
        @Singleton
        fun provideWooCommerceApi(config: WooCommerceConfig, 
                                 sslErrorInterceptor: SSLErrorInterceptor,
                                 connectionResetHandler: ConnectionResetHandler): WooCommerceApi {
            // 初始化元数据处理器注册表
            MetadataProcessorFactory.createDefaultRegistry()
            
            // 使用统一配置类的转换方法获取远程配置
            // 注意: 我们正在进行配置统一，最终会消除两种配置类的差异
            val remoteConfig = runBlocking {
                try {
                    val converted = config.toRemoteConfig()
                    Log.d("NetworkModule", "成功转换WooCommerce配置: $converted")
                    converted
                } catch (e: Exception) {
                    Log.e("NetworkModule", "转换配置失败，使用空配置: ${e.message}", e)
                    com.example.wooauto.data.remote.WooCommerceConfig("", "", "")
                }
            }
            
            Log.d("NetworkModule", "创建WooCommerceApi，使用统一配置源")
            return WooCommerceApiImpl(remoteConfig, sslErrorInterceptor, connectionResetHandler)
        }

        /**
         * 提供ConnectionResetHandler实例
         * @return ConnectionResetHandler实例
         */
        @Provides
        @Singleton
        fun provideConnectionResetHandler(): ConnectionResetHandler {
            return ConnectionResetHandler()
        }
    }
} 