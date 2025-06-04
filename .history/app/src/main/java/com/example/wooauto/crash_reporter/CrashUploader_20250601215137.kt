package com.example.wooauto.crash_reporter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 崩溃数据上传器
 */
object CrashUploader {
    
    private const val TAG = "CrashUploader"
    private const val DEFAULT_CONNECT_TIMEOUT = 30000L  // 30秒
    private const val DEFAULT_READ_TIMEOUT = 30000L     // 30秒
    
    /**
     * 上传崩溃数据到WordPress API
     */
    suspend fun upload(crashData: CrashData, config: CrashReporterConfig): Boolean {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            var lastException: Exception? = null
            
            while (attempt < config.maxRetries) {
                attempt++
                try {
                    Log.d(TAG, "尝试上传崩溃数据 (第${attempt}次尝试)")
                    
                    val url = URL("${config.apiEndpoint.trimEnd('/')}/wp-json/android-crash/v2/report")
                    val connection = url.openConnection() as HttpURLConnection
                    
                    // 设置连接参数
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-API-Key", config.apiKey)
                    connection.setRequestProperty("User-Agent", "WooAutoPrinter-Android/${crashData.appVersion}")
                    connection.connectTimeout = config.connectTimeoutMs
                    connection.readTimeout = config.readTimeoutMs
                    
                    // 发送数据
                    val jsonData = crashData.toJson()
                    connection.outputStream.use { output ->
                        output.write(jsonData.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                    
                    // 检查响应
                    val responseCode = connection.responseCode
                    
                    when (responseCode) {
                        in 200..299 -> {
                            Log.i(TAG, "崩溃数据上传成功 (响应码: $responseCode)")
                            
                            // 读取响应内容（可选）
                            val response = connection.inputStream.use { input ->
                                input.bufferedReader().readText()
                            }
                            Log.d(TAG, "服务器响应: $response")
                            
                            return@withContext true
                        }
                        401 -> {
                            Log.e(TAG, "API密钥无效 (响应码: $responseCode)")
                            return@withContext false // 不重试认证错误
                        }
                        413 -> {
                            Log.e(TAG, "上传文件过大 (响应码: $responseCode)")
                            return@withContext false // 不重试文件过大错误
                        }
                        429 -> {
                            Log.w(TAG, "请求频率限制 (响应码: $responseCode)")
                            // 等待后重试
                            delay(5000L) // 等待5秒
                        }
                        in 500..599 -> {
                            Log.w(TAG, "服务器错误 (响应码: $responseCode)，将重试")
                            delay(1000L * attempt) // 递增延迟
                        }
                        else -> {
                            Log.e(TAG, "上传失败，响应码: $responseCode")
                            val errorResponse = connection.errorStream?.use { input ->
                                input.bufferedReader().readText()
                            } ?: "无错误信息"
                            Log.e(TAG, "错误响应: $errorResponse")
                            delay(1000L * attempt)
                        }
                    }
                    
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "上传崩溃数据失败 (第${attempt}次尝试): ${e.message}", e)
                    
                    // 连接超时或网络错误时等待后重试
                    if (attempt < config.maxRetries) {
                        delay(2000L * attempt) // 递增延迟
                    }
                }
            }
            
            Log.e(TAG, "崩溃数据上传失败，已达到最大重试次数 (${config.maxRetries})")
            lastException?.let { Log.e(TAG, "最后一次失败原因", it) }
            
            return@withContext false
        }
    }
}

/**
 * 网络状态检查器
 */
object NetworkChecker {
    
    /**
     * 检查网络连接状态
     */
    fun isNetworkAvailable(context: android.content.Context): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
            as android.net.ConnectivityManager
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            activeNetworkInfo?.isConnected == true
        }
    }
    
    /**
     * 是否为WiFi连接
     */
    fun isWifiConnected(context: android.content.Context): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
            as android.net.ConnectivityManager
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_WIFI && 
            activeNetworkInfo.isConnected
        }
    }
} 