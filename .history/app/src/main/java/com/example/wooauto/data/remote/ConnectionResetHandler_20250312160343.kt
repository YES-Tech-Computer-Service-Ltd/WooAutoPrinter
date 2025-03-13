package com.example.wooauto.data.remote

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlin.math.min
import kotlin.math.pow

/**
 * 连接重置处理器
 * 处理连接重置和各种网络错误
 */
class ConnectionResetHandler @Inject constructor() {
    
    companion object {
        private const val TAG = "ConnectionResetHandler"
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 60000L // 60秒
    }
    
    // 记录特定域名的连续失败次数
    private val failureCountMap = mutableMapOf<String, Int>()
    
    // 记录最后重试时间
    private val lastRetryTimeMap = mutableMapOf<String, Long>()

    /**
     * 处理网络错误，决定是否应该重试
     * @param e 捕获的异常
     * @param retryCount 当前重试次数
     * @param url 请求URL
     * @return 是否应该重试
     */
    fun shouldRetry(e: Exception, retryCount: Int, url: String): Boolean {
        val domain = extractDomain(url)
        
        // 更新失败计数
        val currentFailCount = failureCountMap.getOrDefault(domain, 0) + 1
        failureCountMap[domain] = currentFailCount
        
        val now = SystemClock.elapsedRealtime()
        val lastRetryTime = lastRetryTimeMap.getOrDefault(domain, 0L)
        
        // 如果该域名连续失败次数过多，可能需要等待更长时间
        val shouldWaitLonger = currentFailCount > 10
        
        // 如果刚刚尝试过该域名且失败次数较多，暂时不再尝试
        if (shouldWaitLonger && now - lastRetryTime < 60000) {
            Log.w(TAG, "域名 $domain 短时间内失败过多 ($currentFailCount 次)，暂时跳过重试")
            return false
        }
        
        // 判断是否应该重试
        val shouldRetry = when (e) {
            // 连接重置 - 重试
            is SSLException -> {
                if (e.message?.contains("Connection reset by peer") == true ||
                    e.message?.contains("Read error") == true ||
                    e.message?.contains("closed") == true) {
                    Log.e(TAG, "连接重置 (SSL)，准备重试 ($retryCount/$MAX_RETRIES)")
                    analyzeSSLReset(e, domain)
                    retryCount < MAX_RETRIES
                } else {
                    Log.e(TAG, "其他SSL错误，准备重试 ($retryCount/$MAX_RETRIES): ${e.message}")
                    retryCount < 3 // 其他SSL错误少尝试几次
                }
            }
            // SSL握手失败 - 少量重试
            is SSLHandshakeException -> {
                Log.e(TAG, "SSL握手失败，准备重试 ($retryCount/2): ${e.message}")
                analyzeSSLReset(e, domain)
                retryCount < 2 // SSL握手错误最多重试2次
            }
            // 连接问题 - 重试
            is ConnectException -> {
                Log.e(TAG, "连接异常，准备重试 ($retryCount/$MAX_RETRIES): ${e.message}")
                retryCount < MAX_RETRIES
            }
            // 套接字错误 - 重试
            is SocketException -> {
                Log.e(TAG, "套接字错误，准备重试 ($retryCount/$MAX_RETRIES): ${e.message}")
                retryCount < MAX_RETRIES
            }
            // 超时 - 重试
            is SocketTimeoutException -> {
                Log.e(TAG, "连接超时，准备重试 ($retryCount/$MAX_RETRIES): ${e.message}")
                retryCount < MAX_RETRIES
            }
            // 无法解析主机名 - 少量重试
            is UnknownHostException -> {
                Log.e(TAG, "无法解析主机名，准备重试 ($retryCount/2): ${e.message}")
                retryCount < 2 // DNS错误最多重试2次
            }
            // HTTP错误 - 按错误码决定
            is HttpException -> {
                val code = e.code()
                when {
                    code in 500..599 -> {
                        Log.e(TAG, "服务器错误 ($code)，准备重试 ($retryCount/$MAX_RETRIES)")
                        retryCount < MAX_RETRIES
                    }
                    code == 429 -> {
                        Log.e(TAG, "请求过于频繁 (429)，准备重试 ($retryCount/$MAX_RETRIES)")
                        retryCount < MAX_RETRIES
                    }
                    else -> {
                        Log.e(TAG, "HTTP错误 ($code)，不再重试")
                        false
                    }
                }
            }
            // 其他IO错误 - 有限重试
            is IOException -> {
                Log.e(TAG, "IO异常，准备重试 ($retryCount/$MAX_RETRIES): ${e.message}")
                retryCount < MAX_RETRIES
            }
            // 其他错误 - 不重试
            else -> {
                Log.e(TAG, "未知错误，不再重试: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }
        
        if (shouldRetry) {
            lastRetryTimeMap[domain] = now
        } else {
            // 清除该域名的失败计数
            failureCountMap.remove(domain)
            lastRetryTimeMap.remove(domain)
        }
        
        return shouldRetry
    }
    
    /**
     * 分析SSL重置错误
     */
    private fun analyzeSSLReset(e: Exception, domain: String) {
        Log.d(TAG, "分析 SSL 连接重置: $domain")
        
        if (e.message?.contains("Connection reset by peer") == true) {
            Log.d(TAG, "【连接诊断】: 连接被对端重置，可能原因:")
            Log.d(TAG, "1. 服务器主动关闭了连接 (防护机制或负载问题)")
            Log.d(TAG, "2. 网络中间设备 (如防火墙) 中断了连接")
            Log.d(TAG, "3. SSL/TLS版本不兼容")
            Log.d(TAG, "4. 密码套件不兼容")
        } else if (e.message?.contains("Read error") == true) {
            Log.d(TAG, "【连接诊断】: 读取错误，可能原因:")
            Log.d(TAG, "1. 连接在传输数据时被中断")
            Log.d(TAG, "2. 服务器SSL配置问题")
            Log.d(TAG, "3. 网络不稳定")
        }
    }
    
    /**
     * 计算重试延迟
     * @param retryCount 当前重试次数
     * @return 延迟毫秒数
     */
    fun calculateRetryDelay(retryCount: Int, url: String): Long {
        val domain = extractDomain(url)
        val consecutiveFailures = failureCountMap.getOrDefault(domain, 0)
        
        // 基于重试次数和连续失败次数计算延迟
        val baseDelay = INITIAL_BACKOFF_MS * 2.0.pow(retryCount.toDouble()).toLong()
        
        // 如果连续失败次数较多，增加延迟
        val additionalDelay = if (consecutiveFailures > 5) {
            consecutiveFailures * 1000L
        } else {
            0L
        }
        
        return min(baseDelay + additionalDelay, MAX_BACKOFF_MS)
    }
    
    /**
     * 执行重试等待
     * @param retryCount 当前重试次数
     * @param url 请求URL
     */
    suspend fun doRetryDelay(retryCount: Int, url: String) {
        val delayMs = calculateRetryDelay(retryCount, url)
        Log.d(TAG, "等待 $delayMs ms 后重试 (第 ${retryCount+1} 次)...")
        delay(delayMs)
    }
    
    /**
     * 提取域名
     * @param url 完整URL
     * @return 域名部分
     */
    private fun extractDomain(url: String): String {
        return try {
            val regex = "https?://([^/]+)".toRegex()
            val matchResult = regex.find(url)
            matchResult?.groupValues?.get(1) ?: url
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * 重置特定域名的失败状态
     * @param domain 域名
     */
    fun resetFailureCount(domain: String) {
        failureCountMap.remove(domain)
        lastRetryTimeMap.remove(domain)
        Log.d(TAG, "已重置域名 $domain 的失败计数")
    }
    
    /**
     * 清除所有失败状态
     */
    fun resetAllFailures() {
        failureCountMap.clear()
        lastRetryTimeMap.clear()
        Log.d(TAG, "已重置所有域名的失败计数")
    }
} 