package com.example.wooauto.crash_reporter

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * Android崩溃报告系统
 * 与WordPress crash reporter插件配合使用
 */
class CrashReporter private constructor(
    private val context: Context,
    private val config: CrashReporterConfig
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    companion object {
        private const val TAG = "CrashReporter"
        private const val CRASH_FILE_PREFIX = "crash_"
        private const val MAX_STACK_TRACE_LENGTH = 8000
        
        @Volatile
        private var INSTANCE: CrashReporter? = null
        
        fun init(context: Context, config: CrashReporterConfig) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = CrashReporter(context.applicationContext, config)
                        INSTANCE?.setupUncaughtExceptionHandler()
                    }
                }
            }
        }
        
        fun getInstance(): CrashReporter? = INSTANCE
        
        fun reportException(throwable: Throwable, customData: Map<String, String>? = null) {
            getInstance()?.handleException(throwable, customData)
        }
    }

    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    /**
     * 设置全局异常处理器
     */
    private fun setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleCrash(throwable, thread)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling crash", e)
            } finally {
                // 调用原始异常处理器
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    /**
     * 处理崩溃
     */
    private fun handleCrash(throwable: Throwable, thread: Thread) {
        val crashData = collectCrashData(throwable, thread, true)
        
        // 保存到本地
        saveCrashDataLocally(crashData)
        
        // 立即尝试上传
        launch {
            uploadCrashData(crashData)
        }
    }
    
    /**
     * 处理非致命异常
     */
    fun handleException(throwable: Throwable, customData: Map<String, String>? = null) {
        launch {
            try {
                val crashData = collectCrashData(throwable, Thread.currentThread(), false, customData)
                
                // 保存到本地
                saveCrashDataLocally(crashData)
                
                // 尝试上传
                uploadCrashData(crashData)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling exception", e)
            }
        }
    }
    
    /**
     * 收集崩溃数据
     */
    private fun collectCrashData(
        throwable: Throwable,
        thread: Thread,
        isFatal: Boolean,
        customData: Map<String, String>? = null
    ): CrashData {
        val deviceInfo = DeviceInfoCollector.collect(context)
        val appInfo = AppInfoCollector.collect(context)
        
        val stackTrace = getStackTrace(throwable).take(MAX_STACK_TRACE_LENGTH)
        
        return CrashData(
            timestamp = System.currentTimeMillis(),
            appVersion = appInfo.versionName,
            appVersionCode = appInfo.versionCode,
            androidVersion = deviceInfo.androidVersion,
            deviceModel = deviceInfo.model,
            deviceBrand = deviceInfo.brand,
            deviceManufacturer = deviceInfo.manufacturer,
            errorType = throwable.javaClass.simpleName,
            errorMessage = throwable.message ?: "Unknown error",
            stackTrace = stackTrace,
            threadName = thread.name,
            isFatal = isFatal,
            packageName = appInfo.packageName,
            availableMemory = deviceInfo.availableMemory,
            totalMemory = deviceInfo.totalMemory,
            customData = customData ?: emptyMap()
        )
    }
    
    /**
     * 获取异常堆栈跟踪
     */
    private fun getStackTrace(throwable: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        return stringWriter.toString()
    }
    
    /**
     * 保存崩溃数据到本地
     */
    private fun saveCrashDataLocally(crashData: CrashData) {
        try {
            val crashDir = File(context.filesDir, "crashes")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            
            val fileName = "${CRASH_FILE_PREFIX}${crashData.timestamp}.json"
            val file = File(crashDir, fileName)
            
            val jsonData = crashData.toJson()
            file.writeText(jsonData)
            
            Log.d(TAG, "Crash data saved: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash data", e)
        }
    }
    
    /**
     * 上传崩溃数据
     */
    private suspend fun uploadCrashData(crashData: CrashData) {
        try {
            val success = CrashUploader.upload(crashData, config)
            if (success) {
                Log.d(TAG, "Crash data uploaded successfully")
                // 删除已上传的本地文件
                deleteCrashFile(crashData.timestamp)
            } else {
                Log.w(TAG, "Failed to upload crash data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading crash data", e)
        }
    }
    
    /**
     * 上传待处理的崩溃文件
     */
    fun uploadPendingCrashes() {
        launch {
            try {
                val crashDir = File(context.filesDir, "crashes")
                if (!crashDir.exists()) return@launch
                
                val crashFiles = crashDir.listFiles { file ->
                    file.name.startsWith(CRASH_FILE_PREFIX) && file.name.endsWith(".json")
                } ?: return@launch
                
                for (file in crashFiles) {
                    try {
                        val jsonData = file.readText()
                        val crashData = CrashData.fromJson(jsonData)
                        
                        val success = CrashUploader.upload(crashData, config)
                        if (success) {
                            file.delete()
                            Log.d(TAG, "Pending crash uploaded and deleted: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing crash file: ${file.name}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading pending crashes", e)
            }
        }
    }
    
    private fun deleteCrashFile(timestamp: Long) {
        try {
            val crashDir = File(context.filesDir, "crashes")
            val file = File(crashDir, "${CRASH_FILE_PREFIX}${timestamp}.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting crash file", e)
        }
    }
    
    /**
     * 清理旧的崩溃文件
     */
    fun cleanupOldCrashes(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) { // 7天
        launch {
            try {
                val crashDir = File(context.filesDir, "crashes")
                if (!crashDir.exists()) return@launch
                
                val currentTime = System.currentTimeMillis()
                val crashFiles = crashDir.listFiles() ?: return@launch
                
                for (file in crashFiles) {
                    if (currentTime - file.lastModified() > maxAgeMs) {
                        file.delete()
                        Log.d(TAG, "Old crash file deleted: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old crashes", e)
            }
        }
    }
    
    fun destroy() {
        job.cancel()
    }
}

/**
 * 崩溃报告器配置
 */
data class CrashReporterConfig(
    val apiEndpoint: String,  // WordPress API端点
    val apiKey: String,       // API密钥
    val enableAutoUpload: Boolean = true,
    val enableDebugLogs: Boolean = false,
    val maxRetries: Int = 3,
    val connectTimeoutMs: Int = 30000,
    val readTimeoutMs: Int = 30000
) 