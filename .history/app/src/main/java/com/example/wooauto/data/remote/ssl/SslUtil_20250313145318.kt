package com.example.wooauto.data.remote.ssl

import android.os.Build
import android.util.Log
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.security.KeyStore
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL工具类
 * 提供SSL和TLS相关的配置方法
 */
object SslUtil {
    private const val TAG = "SslUtil"

    /**
     * 获取针对Android 7.0 (Nougat, API 24) 的ConnectionSpec
     * @return ConnectionSpec 适用于Android 7.0的配置
     */
    fun getSpecForNougat(): ConnectionSpec {
        return ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .build()
    }

    /**
     * 获取针对API 22以下设备的ConnectionSpecs列表
     * @param builder OkHttpClient.Builder实例
     * @return List<ConnectionSpec> 适用于低版本Android的配置列表
     */
    fun getSpecsBelowLollipopMR1(builder: OkHttpClient.Builder): List<ConnectionSpec>? {
        return try {
            // 创建TrustManager
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
            
            // 创建SSLContext并指定TLS 1.2
            val sc = SSLContext.getInstance("TLSv1.2")
            sc.init(null, arrayOf(trustManager), null)
            
            // 使用TLS 1.2 SocketFactory，同时提供TrustManager
            builder.sslSocketFactory(Tls12SocketFactory(sc.socketFactory), trustManager)
            
            // 创建ConnectionSpec
            val cs = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()
            
            // 返回ConnectionSpecs列表
            Arrays.asList(
                cs,
                ConnectionSpec.COMPATIBLE_TLS
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取TLS 1.2配置失败: ${e.message}", e)
            null
        }
    }

    /**
     * 配置OkHttpClient以适应不同Android版本的TLS支持
     * @param builder OkHttpClient.Builder实例
     */
    fun configureTls(builder: OkHttpClient.Builder) {
        Log.d(TAG, "配置TLS，API级别: ${Build.VERSION.SDK_INT}")
        
        when {
            // 针对Android 7.0 (API 24)
            Build.VERSION.SDK_INT == Build.VERSION_CODES.N -> {
                Log.d(TAG, "为Android 7.0配置TLS")
                builder.connectionSpecs(Collections.singletonList(getSpecForNougat()))
            }
            
            // 针对Android 5.1以下 (API 22以下)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 -> {
                Log.d(TAG, "为API 22以下设备配置TLS")
                val specsList = getSpecsBelowLollipopMR1(builder)
                if (specsList != null) {
                    builder.connectionSpecs(specsList)
                }
            }
            
            // 其他版本使用默认配置
            else -> {
                Log.d(TAG, "使用默认TLS配置")
                // 使用默认的安全配置
            }
        }
    }
} 