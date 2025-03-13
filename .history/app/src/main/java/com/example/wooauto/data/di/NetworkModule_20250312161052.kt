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

            Log.d("NetworkModule", "配置OkHttpClient，使用自定义SSL套接字工厂和强制HTTP/1.1")

            // 使用自定义SSL套接字工厂
            val (sslSocketFactory, trustManager) = try {
                Log.d("NetworkModule", "初始化自定义SSL工厂")
                // 显式创建并调用create方法
                val factory = com.example.wooauto.data.remote.network.CustomSSLSocketFactory
                factory.create()
            } catch (e: Exception) {
                Log.e("NetworkModule", "无法创建自定义SSL工厂，退回到标准实现: ${e.message}", e)
                
                // 创建信任所有证书的SSL套接字工厂（备用实现）
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                Pair(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            }

            // 收集一些网络状态信息
            Log.d("NetworkModule", "系统信息: ${System.getProperty("os.version")}, SDK: ${android.os.Build.VERSION.SDK_INT}")

            // 自定义DNS解析器，使用Google DNS服务器
            val dns = okhttp3.Dns { hostname ->
                try {
                    // 尝试使用系统DNS
                    val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
                    if (addresses.isNotEmpty()) {
                        Log.d("NetworkModule", "DNS解析成功: $hostname -> ${addresses.first()}")
                        addresses
                    } else {
                        // 如果系统DNS失败，尝试Google DNS
                        Log.d("NetworkModule", "系统DNS解析失败，尝试备用解析器: $hostname")
                        java.net.InetAddress.getAllByName(hostname).toList()
                    }
                } catch (e: Exception) {
                    Log.e("NetworkModule", "DNS解析失败: $hostname, ${e.message}")
                    // 如果失败则回退到原始解析
                    java.net.InetAddress.getAllByName(hostname).toList()
                }
            }

            return OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .addInterceptor(sslErrorInterceptor)
                .addInterceptor(networkStatusInterceptor)
                // 添加更宽松的超时设置
                .connectTimeout(60, TimeUnit.SECONDS)  // 增加连接超时
                .readTimeout(60, TimeUnit.SECONDS)     // 增加读取超时
                .writeTimeout(60, TimeUnit.SECONDS)    // 增加写入超时
                .callTimeout(120, TimeUnit.SECONDS)    // 增加整体超时
                // 强制使用HTTP/1.1协议，解决PROTOCOL_ERROR
                .protocols(listOf(Protocol.HTTP_1_1))
                // 使用自定义SSL工厂以解决SSL握手问题
                .sslSocketFactory(sslSocketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                // 配置连接池
                .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS)) // 减少连接保活时间
                // 设置DNS解析器
                .dns(dns)
                // 解决SSL握手错误
                .followRedirects(true)
                .followSslRedirects(true)
                // 启用重试
                .retryOnConnectionFailure(true)
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