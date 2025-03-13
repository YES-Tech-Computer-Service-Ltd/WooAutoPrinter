package com.example.wooauto.data.remote.interceptors

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Request
import java.io.IOException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.inject.Inject

/**
 * SSL错误拦截器
 * 专门处理SSL连接问题，提供详细的错误日志
 */
class SSLErrorInterceptor @Inject constructor() : Interceptor {
    
    companion object {
        private const val TAG = "SSLErrorInterceptor"
        private const val MAX_RETRIES = 3
    }
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var retryCount = 0
        var lastException: IOException? = null
        
        while (retryCount < MAX_RETRIES) {
            try {
                // 如果是重试请求，添加特殊头部以便于调试
                val newRequest = if (retryCount > 0) {
                    request.newBuilder()
                        .header("X-Retry-Count", retryCount.toString())
                        .build()
                } else {
                    request
                }
                
                return chain.proceed(newRequest)
            } catch (e: SSLHandshakeException) {
                if (++retryCount >= MAX_RETRIES) {
                    logSSLError("SSL握手失败（最终尝试）", e, request, retryCount)
                    throw e
                }
                
                logSSLError("SSL握手失败，正在重试", e, request, retryCount)
                lastException = e
                // 短暂延迟后重试
                Thread.sleep((retryCount * 1000).toLong())
            } catch (e: SSLException) {
                if (++retryCount >= MAX_RETRIES) {
                    logSSLError("SSL连接错误（最终尝试）", e, request, retryCount)
                    throw e
                }
                
                logSSLError("SSL连接错误，正在重试", e, request, retryCount)
                lastException = e
                // 短暂延迟后重试
                Thread.sleep((retryCount * 1000).toLong())
            } catch (e: IOException) {
                if (e.message?.contains("SSL") == true || 
                    e.message?.contains("socket") == true || 
                    e.message?.contains("reset") == true) {
                    
                    if (++retryCount >= MAX_RETRIES) {
                        logSSLError("网络IO错误（最终尝试）", e, request, retryCount)
                        throw e
                    }
                    
                    logSSLError("网络IO错误，正在重试", e, request, retryCount)
                    lastException = e
                    // 短暂延迟后重试
                    Thread.sleep((retryCount * 1000).toLong())
                } else {
                    // 不是SSL相关错误，直接抛出
                    throw e
                }
            }
        }
        
        // 如果所有重试都失败，抛出最后一个异常
        throw lastException ?: IOException("未知网络错误，已尝试 $MAX_RETRIES 次")
    }
    
    private fun logSSLError(message: String, e: Exception, request: Request, retryCount: Int) {
        Log.e(TAG, "[$retryCount/$MAX_RETRIES] $message: ${e.message}")
        Log.e(TAG, "请求URL: ${request.url}, 方法: ${request.method}")
        Log.e(TAG, "异常类型: ${e.javaClass.simpleName}, 堆栈: ${e.stackTraceToString().take(200)}...")
    }
} 