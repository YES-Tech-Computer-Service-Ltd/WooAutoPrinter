package com.example.wooauto.crash_reporter

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 崩溃数据模型
 */
data class CrashData(
    val timestamp: Long,
    val appVersion: String,
    val appVersionCode: Long,
    val androidVersion: String,
    val deviceModel: String,
    val deviceBrand: String,
    val deviceManufacturer: String,
    val errorType: String,
    val errorMessage: String,
    val stackTrace: String,
    val threadName: String,
    val isFatal: Boolean,
    val packageName: String,
    val availableMemory: Long,
    val totalMemory: Long,
    val customData: Map<String, String>,
    val contextLogs: String = "",
    val userActions: String = "",
    val appState: Map<String, String> = emptyMap(),
    val performanceData: String = ""
) {
    
    /**
     * 转换为JSON格式
     */
    fun toJson(): String {
        val json = JSONObject().apply {
            put("timestamp", timestamp)
            put("app_version", appVersion)
            put("app_version_code", appVersionCode)
            put("android_version", androidVersion)
            put("device_model", deviceModel)
            put("device_brand", deviceBrand)
            put("device_manufacturer", deviceManufacturer)
            put("error_type", errorType)
            put("error_message", errorMessage)
            put("stack_trace", stackTrace)
            put("thread_name", threadName)
            put("is_fatal", isFatal)
            put("package_name", packageName)
            put("available_memory", availableMemory)
            put("total_memory", totalMemory)
            put("formatted_time", getFormattedTime())
            
            // 上下文信息
            put("context_logs", contextLogs)
            put("user_actions", userActions)
            put("performance_data", performanceData)
            
            // 应用状态
            if (appState.isNotEmpty()) {
                val appStateJson = JSONObject()
                appState.forEach { (key, value) ->
                    appStateJson.put(key, value)
                }
                put("app_state", appStateJson)
            }
            
            // 自定义数据
            if (customData.isNotEmpty()) {
                val customJson = JSONObject()
                customData.forEach { (key, value) ->
                    customJson.put(key, value)
                }
                put("custom_data", customJson)
            }
        }
        return json.toString()
    }
    
    /**
     * 获取格式化时间
     */
    private fun getFormattedTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * 获取严重程度
     */
    fun getSeverity(): String {
        return when {
            isFatal -> "critical"
            errorType.contains("OutOfMemoryError", true) -> "critical"
            errorType.contains("SecurityException", true) -> "critical"
            errorType.contains("NullPointerException", true) -> "high"
            errorType.contains("RuntimeException", true) -> "high"
            errorType.contains("IllegalStateException", true) -> "high"
            errorType.contains("ClassCastException", true) -> "high"
            errorType.contains("IOException", true) -> "medium"
            errorType.contains("SQLException", true) -> "medium"
            errorType.contains("ParseException", true) -> "medium"
            else -> "high"
        }
    }
    
    companion object {
        /**
         * 从JSON字符串创建CrashData对象
         */
        fun fromJson(jsonString: String): CrashData {
            val json = JSONObject(jsonString)
            
            val customData = mutableMapOf<String, String>()
            if (json.has("custom_data")) {
                val customJson = json.getJSONObject("custom_data")
                val keys = customJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    customData[key] = customJson.getString(key)
                }
            }
            
            return CrashData(
                timestamp = json.getLong("timestamp"),
                appVersion = json.getString("app_version"),
                appVersionCode = json.getLong("app_version_code"),
                androidVersion = json.getString("android_version"),
                deviceModel = json.getString("device_model"),
                deviceBrand = json.getString("device_brand"),
                deviceManufacturer = json.getString("device_manufacturer"),
                errorType = json.getString("error_type"),
                errorMessage = json.getString("error_message"),
                stackTrace = json.getString("stack_trace"),
                threadName = json.getString("thread_name"),
                isFatal = json.getBoolean("is_fatal"),
                packageName = json.getString("package_name"),
                availableMemory = json.getLong("available_memory"),
                totalMemory = json.getLong("total_memory"),
                customData = customData,
                contextLogs = json.getString("context_logs"),
                userActions = json.getString("user_actions"),
                appState = mutableMapOf<String, String>().apply {
                    if (json.has("app_state")) {
                        val appStateJson = json.getJSONObject("app_state")
                        val appStateKeys = appStateJson.keys()
                        while (appStateKeys.hasNext()) {
                            val key = appStateKeys.next()
                            put(key, appStateJson.getString(key))
                        }
                    }
                },
                performanceData = json.getString("performance_data")
            )
        }
    }
}

/**
 * 设备信息收集器
 */
object DeviceInfoCollector {
    
    fun collect(context: android.content.Context): DeviceInfo {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) 
            as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return DeviceInfo(
            model = android.os.Build.MODEL,
            brand = android.os.Build.BRAND,
            manufacturer = android.os.Build.MANUFACTURER,
            androidVersion = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            availableMemory = memoryInfo.availMem,
            totalMemory = memoryInfo.totalMem
        )
    }
}

/**
 * 应用信息收集器
 */
object AppInfoCollector {
    
    fun collect(context: android.content.Context): AppInfo {
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
        
        return AppInfo(
            packageName = context.packageName,
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        )
    }
}

/**
 * 设备信息数据类
 */
data class DeviceInfo(
    val model: String,
    val brand: String,
    val manufacturer: String,
    val androidVersion: String,
    val availableMemory: Long,
    val totalMemory: Long
)

/**
 * 应用信息数据类
 */
data class AppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long
) 