package com.example.wooauto.data.remote.interceptors

import android.util.Log
import com.example.wooauto.data.local.WooCommerceConfig
import com.example.wooauto.utils.UiLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import java.io.IOException

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
                    throw IOException("WooCommerce API认证失败：Consumer Key为空，请在设置中配置")
                }
                // 检查consumer_key的格式，通常应以ck_开头
                if (!key.startsWith("ck_")) {
                    Log.w("WooCommerceAuthInterceptor", "consumer_key格式可能不正确，通常应以'ck_'开头: $key")
                }
                // 检查consumer_key是否误用了URL
                if (key.contains("http")) {
                    Log.e("WooCommerceAuthInterceptor", "consumer_key包含URL，可能误用了站点URL作为key: $key")
                    throw IOException("WooCommerce API认证失败：Consumer Key包含URL，可能填写错误")
                }
                key
            } catch (e: Exception) {
                Log.e("WooCommerceAuthInterceptor", "获取consumer_key出错", e)
                if (e is IOException) throw e
                throw IOException("WooCommerce API认证失败：无法获取Consumer Key")
            }
        }
        
        val consumerSecret = runBlocking { 
            try {
                val secret = config.consumerSecret.first()
                if (secret.isBlank()) {
                    Log.e("WooCommerceAuthInterceptor", "consumer_secret为空，API认证将失败")
                    throw IOException("WooCommerce API认证失败：Consumer Secret为空，请在设置中配置")
                }
                // 检查consumer_secret的格式，通常应以cs_开头
                if (!secret.startsWith("cs_")) {
                    Log.w("WooCommerceAuthInterceptor", "consumer_secret格式可能不正确，通常应以'cs_'开头: $secret")
                }
                // 检查consumer_secret是否误用了URL
                if (secret.contains("http")) {
                    Log.e("WooCommerceAuthInterceptor", "consumer_secret包含URL，可能填写错误: $secret")
                    throw IOException("WooCommerce API认证失败：Consumer Secret包含URL，可能填写错误")
                }
                secret
            } catch (e: Exception) {
                Log.e("WooCommerceAuthInterceptor", "获取consumer_secret出错", e)
                if (e is IOException) throw e
                throw IOException("WooCommerce API认证失败：无法获取Consumer Secret")
            }
        }
        
        // 记录认证信息（不要记录完整的密钥信息）
        UiLog.d("WooCommerceAuthInterceptor", "添加API认证参数: key=${if (consumerKey.isNotBlank()) consumerKey.take(5) + "..." else "空"}, " +
                "secret=${if (consumerSecret.isNotBlank()) consumerSecret.take(5) + "..." else "空"}")
        
        // 添加认证参数
        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("consumer_key", consumerKey)
            .addQueryParameter("consumer_secret", consumerSecret)
            .build()
        
        val requestBuilder = original.newBuilder().url(url)
        val request = requestBuilder.build()
        
        UiLog.d("WooCommerceAuthInterceptor", "发送API请求: ${request.url}")
        
        // 执行请求并检查响应
        val response = chain.proceed(request)
        
        // 检查认证错误
        if (response.code == 401) {
            Log.e("WooCommerceAuthInterceptor", "API认证失败 (401): 无效的consumer_key或consumer_secret")
            // 这里不抛出异常，让调用者处理401错误
        } else if (!response.isSuccessful) {
            Log.e("WooCommerceAuthInterceptor", "API请求失败: ${response.code} ${response.message}")
        }
        
        return response
    }
} 