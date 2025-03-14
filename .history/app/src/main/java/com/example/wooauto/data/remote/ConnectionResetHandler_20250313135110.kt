package com.example.wooauto.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.delay

/**
 * 连接重置处理器
 * 专门处理"Connection reset by peer"等连接问题
 */
@Singleton
class ConnectionResetHandler @Inject constructor() {
    
    companion object {
        private const val TAG = "ConnectionResetHandler"
        private const val MAX_REPAIR_ATTEMPTS = 3
        
        // 用于存储检测到的问题
        private var resetCount = 0
        private var lastResetTime = 0L
        private var isRecoveryMode = false
        
        // 特别针对Android 7的标志
        private val isAndroid7 = android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.N
    }
    
    /**
     * 记录连接重置事件
     */
    fun recordConnectionReset() {
        val now = System.currentTimeMillis()
        
        // 重置计数（如果上次重置太久以前）
        if (now - lastResetTime > 5 * 60 * 1000) { // 5分钟
            resetCount = 0
        }
        
        resetCount++
        lastResetTime = now
        
        // 在Android 7上更积极地进入恢复模式
        if (isAndroid7 && resetCount >= 2 && !isRecoveryMode) {
            Log.w(TAG, "Android 7设备检测到连接重置，立即进入恢复模式")
            isRecoveryMode = true
        } else {
            Log.d(TAG, "记录连接重置，当前计数: $resetCount")
            
            // 如果连续出现多次重置，切换到恢复模式
            if (resetCount >= 3 && !isRecoveryMode) {
                Log.w(TAG, "检测到连续的连接重置，切换到恢复模式")
                isRecoveryMode = true
            }
        }
    }
    
    /**
     * 获取当前是否处于恢复模式
     */
    fun isInRecoveryMode(): Boolean {
        return isRecoveryMode
    }
    
    /**
     * 尝试解决连接问题
     * @return 是否成功修复
     */
    suspend fun attemptConnectionRepair(): Boolean {
        var success = false
        
        // Android 7需要特殊处理
        if (isAndroid7) {
            Log.d(TAG, "Android 7设备，使用特殊修复流程")
            
            for (i in 1..MAX_REPAIR_ATTEMPTS) {
                Log.d(TAG, "Android 7设备修复尝试 (${i}/${MAX_REPAIR_ATTEMPTS})")
                
                try {
                    // 测试连接到Google而非原始服务器，避免SSL问题
                    val repaired = testFallbackConnection()
                    if (repaired) {
                        Log.d(TAG, "Android 7设备连接修复成功")
                        resetCount = 0
                        isRecoveryMode = false
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Android 7设备修复尝试失败: ${e.message}")
                }
                
                // 更长延迟以避免重复失败
                delay(3000L * i)
            }
        } else {
            // 非Android 7设备的标准处理
            for (i in 1..MAX_REPAIR_ATTEMPTS) {
                Log.d(TAG, "尝试修复连接问题 (${i}/${MAX_REPAIR_ATTEMPTS})")
                
                try {
                    // 尝试简单连接到主机来重置连接状态
                    val repaired = testConnection()
                    if (repaired) {
                        Log.d(TAG, "连接问题已修复")
                        resetCount = 0
                        isRecoveryMode = false
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "修复尝试失败: ${e.message}")
                }
                
                // 增加延迟
                delay(2000L * i)
            }
        }
        
        if (!success) {
            Log.w(TAG, "无法修复连接问题，需要用户干预")
        }
        
        return success
    }
    
    /**
     * 恢复正常模式
     */
    fun resetToNormalMode() {
        Log.d(TAG, "手动重置到正常模式")
        resetCount = 0
        isRecoveryMode = false
    }
    
    /**
     * 测试连接
     * @return 连接是否成功
     */
    private fun testConnection(): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url("https://www.google.com")
            .build()
            
        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "测试连接失败: ${e.message}")
            false
        }
    }
    
    /**
     * 为Android 7测试替代连接
     * 使用HTTP而非HTTPS避免SSL问题
     */
    private fun testFallbackConnection(): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
            
        val request = Request.Builder()
            .url("http://clients3.google.com/generate_204")
            .build()
            
        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "替代连接测试失败: ${e.message}")
            false
        }
    }
    
    /**
     * 分析连接重置错误
     * @param exception 捕获的异常
     * @return 分析结果
     */
    fun analyzeResetError(exception: Exception): String {
        // 对Android 7设备的错误进行特殊处理
        if (isAndroid7) {
            if (exception is SSLException && (exception.message?.contains("peer") == true || 
                exception.message?.contains("reset") == true)) {
                return "Android 7设备SSL连接重置，可能是缺乏对特定椭圆曲线的支持导致"
            }
        }
        
        return when (exception) {
            is SSLException -> {
                if (exception.message?.contains("peer") == true) {
                    "服务器主动断开连接，可能是服务器负载过高或SSL配置问题"
                } else if (exception.message?.contains("handshake") == true) {
                    "SSL握手失败，服务器可能不支持当前的TLS版本或密码套件"
                } else {
                    "SSL连接错误：${exception.message}"
                }
            }
            is IOException -> {
                if (exception.message?.contains("reset") == true) {
                    "连接被重置，可能是网络不稳定或服务器端关闭了连接"
                } else if (exception.message?.contains("timeout") == true) {
                    "连接超时，服务器响应时间过长"
                } else {
                    "网络IO错误：${exception.message}"
                }
            }
            else -> "未知错误：${exception.message}"
        }
    }
} 