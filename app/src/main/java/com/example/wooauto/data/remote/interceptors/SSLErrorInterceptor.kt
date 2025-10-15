package com.example.wooauto.data.remote.interceptors

import android.util.Log
import com.example.wooauto.utils.UiLog
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Request
import java.io.IOException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.inject.Inject
import kotlin.math.pow

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
                        .header("X-Connection-New", "true") // 提示服务器这是一个新连接
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
                // 使用指数退避策略
                val delayMs = (2.0.pow(retryCount.toDouble()) * 1000).toLong()
                Thread.sleep(delayMs)
            } catch (e: SSLException) {
                // 检查是否是连接重置错误
                val isConnectionReset = e.message?.contains("reset") == true || 
                                       e.message?.contains("closed") == true ||
                                       e.message?.contains("peer") == true
                
                if (isConnectionReset) {
                    logSSLError("连接被重置，可能是服务器主动关闭", e, request, retryCount)
                    
                    // 对于连接重置错误，增加更长的延迟
                    if (++retryCount >= MAX_RETRIES) {
                        logSSLError("连接重置（最终尝试）", e, request, retryCount)
                        throw e
                    }
                    
                    // 使用更长的指数退避
                    val delayMs = (3.0.pow(retryCount.toDouble()) * 1000).toLong()
                    UiLog.d(TAG, "连接重置，等待 $delayMs ms 后重试...")
                    Thread.sleep(delayMs)
                } else {
                    if (++retryCount >= MAX_RETRIES) {
                        logSSLError("SSL连接错误（最终尝试）", e, request, retryCount)
                        throw e
                    }
                    
                    logSSLError("SSL连接错误，正在重试", e, request, retryCount)
                    lastException = e
                    // 使用指数退避策略
                    val delayMs = (2.0.pow(retryCount.toDouble()) * 1000).toLong()
                    Thread.sleep(delayMs)
                }
            } catch (e: IOException) {
                val isNetworkRelated = e.message?.contains("SSL") == true || 
                    e.message?.contains("socket") == true || 
                    e.message?.contains("reset") == true ||
                    e.message?.contains("peer") == true ||
                    e.message?.contains("connect") == true ||
                    e.message?.contains("timed out") == true
                
                if (isNetworkRelated) {
                    if (++retryCount >= MAX_RETRIES) {
                        logSSLError("网络IO错误（最终尝试）", e, request, retryCount)
                        throw e
                    }
                    
                    logSSLError("网络IO错误，正在重试", e, request, retryCount)
                    lastException = e
                    // 使用指数退避策略
                    val delayMs = (2.0.pow(retryCount.toDouble()) * 1000).toLong()
                    Thread.sleep(delayMs)
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
        
        // 提取更多连接信息
        if (e.message?.contains("reset") == true || e.message?.contains("peer") == true) {
            Log.e(TAG, "连接重置分析: 可能是服务器主动关闭了连接，通常由服务器负载过高或防火墙策略导致")
            Log.e(TAG, "建议检查: 1) 服务器负载 2) 防火墙规则 3) SSL证书配置 4) 减少并发请求数")
        }
    }
} 