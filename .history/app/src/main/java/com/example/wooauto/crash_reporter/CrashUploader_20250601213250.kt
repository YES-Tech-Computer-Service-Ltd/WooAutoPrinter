package com.example.wooauto.crash_reporter

import android.util.Log
import kotlinx.coroutines.Dispatchers
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
    private const val REQUEST_METHOD = "POST"
    private const val HEADER_API_KEY = "X-API-Key"
    private const val HEADER_CONTENT_TYPE = "Content-Type"
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
    private const val BOUNDARY = "----CrashReporter${System.currentTimeMillis()}"
    private const val MULTIPART_CONTENT_TYPE = "multipart/form-data; boundary=$BOUNDARY"
    
    /**
     * 上传崩溃数据到WordPress后端
     */
    suspend fun upload(crashData: CrashData, config: CrashReporterConfig): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                for (attempt in 1..config.maxRetries) {
                    try {
                        connection = createConnection(config)
                        
                        if (config.enableDebugLogs) {
                            Log.d(TAG, "Uploading crash data (attempt $attempt/${config.maxRetries})")
                        }
                        
                        // 发送数据
                        sendCrashData(connection, crashData)
                        
                        // 检查响应
                        val responseCode = connection.responseCode
                        val responseMessage = connection.responseMessage
                        
                        if (config.enableDebugLogs) {
                            Log.d(TAG, "Response: $responseCode $responseMessage")
                        }
                        
                        when (responseCode) {
                            HttpURLConnection.HTTP_OK,
                            HttpURLConnection.HTTP_CREATED -> {
                                // 读取响应内容
                                val response = readResponse(connection)
                                if (config.enableDebugLogs) {
                                    Log.d(TAG, "Upload successful: $response")
                                }
                                return@withContext true
                            }
                            
                            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                                Log.e(TAG, "Upload failed: Invalid API key")
                                return@withContext false // 不重试认证错误
                            }
                            
                            HttpURLConnection.HTTP_BAD_REQUEST -> {
                                val errorResponse = readErrorResponse(connection)
                                Log.e(TAG, "Upload failed: Bad request - $errorResponse")
                                return@withContext false // 不重试格式错误
                            }
                            
                            429 -> { // Too Many Requests
                                Log.w(TAG, "Rate limited, will retry")
                                if (attempt < config.maxRetries) {
                                    Thread.sleep(5000 * attempt) // 递增延迟
                                }
                                continue
                            }
                            
                            HttpURLConnection.HTTP_INTERNAL_ERROR,
                            HttpURLConnection.HTTP_BAD_GATEWAY,
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> {
                                Log.w(TAG, "Server error ($responseCode), will retry")
                                if (attempt < config.maxRetries) {
                                    Thread.sleep(2000 * attempt)
                                }
                                continue
                            }
                            
                            else -> {
                                Log.e(TAG, "Upload failed with code: $responseCode $responseMessage")
                                return@withContext false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Upload attempt $attempt failed", e)
                        if (attempt == config.maxRetries) {
                            throw e
                        }
                        Thread.sleep(1000 * attempt)
                    } finally {
                        connection?.disconnect()
                    }
                }
                
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload crash data", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 创建HTTP连接
     */
    private fun createConnection(config: CrashReporterConfig): HttpURLConnection {
        val url = URL(config.apiEndpoint)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = REQUEST_METHOD
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            doOutput = true
            doInput = true
            useCaches = false
            
            // 设置请求头
            setRequestProperty(HEADER_API_KEY, config.apiKey)
            setRequestProperty(HEADER_CONTENT_TYPE, MULTIPART_CONTENT_TYPE)
            setRequestProperty(HEADER_USER_AGENT, "WooAutoPrinter-CrashReporter/1.0")
        }
        
        return connection
    }
    
    /**
     * 发送崩溃数据
     */
    private fun sendCrashData(connection: HttpURLConnection, crashData: CrashData) {
        connection.outputStream.use { outputStream ->
            val writer = PrintWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)
            
            // 添加表单字段
            addFormField(writer, "app_version", crashData.appVersion)
            addFormField(writer, "android_version", crashData.androidVersion)
            addFormField(writer, "device_model", crashData.deviceModel)
            addFormField(writer, "device_brand", crashData.deviceBrand)
            addFormField(writer, "error_type", crashData.errorType)
            addFormField(writer, "error_message", crashData.errorMessage)
            addFormField(writer, "stack_trace", crashData.stackTrace)
            addFormField(writer, "app_package", crashData.packageName)
            
            // 添加文件（JSON格式的详细数据）
            addFilePart(writer, outputStream, "crash_data", crashData.toJson())
            
            // 结束边界
            writer.append("--$BOUNDARY--").append("\r\n")
            writer.flush()
        }
    }
    
    /**
     * 添加表单字段
     */
    private fun addFormField(writer: PrintWriter, name: String, value: String) {
        writer.append("--$BOUNDARY").append("\r\n")
        writer.append("Content-Disposition: form-data; name=\"$name\"").append("\r\n")
        writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n")
        writer.append("\r\n")
        writer.append(value).append("\r\n")
        writer.flush()
    }
    
    /**
     * 添加文件部分
     */
    private fun addFilePart(
        writer: PrintWriter,
        outputStream: OutputStream,
        fieldName: String,
        content: String
    ) {
        val fileName = "crash_${System.currentTimeMillis()}.json"
        
        writer.append("--$BOUNDARY").append("\r\n")
        writer.append("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"").append("\r\n")
        writer.append("Content-Type: application/json").append("\r\n")
        writer.append("Content-Transfer-Encoding: binary").append("\r\n")
        writer.append("\r\n")
        writer.flush()
        
        outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
        
        writer.append("\r\n")
        writer.flush()
    }
    
    /**
     * 读取成功响应
     */
    private fun readResponse(connection: HttpURLConnection): String {
        return connection.inputStream.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }
    }
    
    /**
     * 读取错误响应
     */
    private fun readErrorResponse(connection: HttpURLConnection): String {
        return try {
            connection.errorStream?.use { errorStream ->
                BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: "Unknown error"
        } catch (e: Exception) {
            "Error reading error response: ${e.message}"
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