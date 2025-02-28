package com.example.wooauto.data.remote.interceptors

import android.util.Log
import com.example.wooauto.data.local.WooCommerceConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * WooCommerce认证拦截器
 * 为每个请求添加consumer_key和consumer_secret参数
 */
class WooCommerceAuthInterceptor @Inject constructor(
    private val config: WooCommerceConfig
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val originalHttpUrl = original.url
        
        // 从配置中获取认证信息
        val consumerKey = runBlocking { 
            try {
                val key = config.consumerKey.first()
                if (key.isBlank()) {
                    Log.e("WooCommerceAuthInterceptor", "consumer_key为空，API认证将失败")
                }
                key
            } catch (e: Exception) {
                Log.e("WooCommerceAuthInterceptor", "获取consumer_key出错", e)
                ""
            }
        }
        
        val consumerSecret = runBlocking { 
            try {
                val secret = config.consumerSecret.first()
                if (secret.isBlank()) {
                    Log.e("WooCommerceAuthInterceptor", "consumer_secret为空，API认证将失败")
                }
                secret
            } catch (e: Exception) {
                Log.e("WooCommerceAuthInterceptor", "获取consumer_secret出错", e)
                ""
            }
        }
        
        // 记录认证信息（不要记录完整的密钥信息）
        Log.d("WooCommerceAuthInterceptor", "添加API认证参数: key=${if (consumerKey.isNotBlank()) consumerKey.take(5) + "..." else "空"}, " +
                "secret=${if (consumerSecret.isNotBlank()) consumerSecret.take(5) + "..." else "空"}")
        
        // 添加认证参数
        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("consumer_key", consumerKey)
            .addQueryParameter("consumer_secret", consumerSecret)
            .build()
        
        val requestBuilder = original.newBuilder().url(url)
        val request = requestBuilder.build()
        
        Log.d("WooCommerceAuthInterceptor", "发送API请求: ${request.url}")
        return chain.proceed(request)
    }
} 