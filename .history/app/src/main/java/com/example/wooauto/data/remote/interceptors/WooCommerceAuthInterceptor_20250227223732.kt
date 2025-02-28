package com.example.wooauto.data.remote.interceptors

import com.wooauto.data.local.WooCommerceConfig
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
        val consumerKey = runBlocking { config.consumerKey.first() }
        val consumerSecret = runBlocking { config.consumerSecret.first() }
        
        // 添加认证参数
        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("consumer_key", consumerKey)
            .addQueryParameter("consumer_secret", consumerSecret)
            .build()
        
        val requestBuilder = original.newBuilder().url(url)
        val request = requestBuilder.build()
        
        return chain.proceed(request)
    }
} 