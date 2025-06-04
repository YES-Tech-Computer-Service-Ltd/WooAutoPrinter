package com.example.wooauto.crash_reporter

import android.content.Context
import android.util.Log

/**
 * 崩溃报告管理器
 * 提供简化的接口用于初始化和使用崩溃报告系统
 */
object CrashReporterManager {
    
    private const val TAG = "CrashReporterManager"
    
    /**
     * 初始化崩溃报告系统
     * 在Application.onCreate()中调用
     */
    fun initialize(
        context: Context,
        apiEndpoint: String,
        apiKey: String,
        enableDebugLogs: Boolean = false,
        customSizeConfig: CrashDataSizeConfig? = null
    ) {
        try {
            val config = CrashReporterConfig(
                apiEndpoint = apiEndpoint,
                apiKey = apiKey,
                enableAutoUpload = true,
                enableDebugLogs = enableDebugLogs,
                maxRetries = 3,
                connectTimeoutMs = 30000,
                readTimeoutMs = 30000,
                dataSizeConfig = customSizeConfig ?: CrashDataSizeConfig()
            )
            
            CrashReporter.init(context, config)
            
            // 启用上下文日志收集
            LogInterceptor.enable()
            
            // 记录初始化完成
            ContextLogger.i(TAG, "崩溃报告系统初始化完成")
            AppStateCollector.setAppState("初始化", "crash_reporter_init")
            
            // 记录数据大小配置
            if (enableDebugLogs) {
                Log.i(TAG, "数据大小配置: 最大堆栈${config.dataSizeConfig.maxStackTraceLength}字符, " +
                          "最大日志${config.dataSizeConfig.maxContextLogs}条, " +
                          "压缩${config.dataSizeConfig.enableCompression}")
            }
            
            // 启动时上传待处理的崩溃报告
            CrashReporter.getInstance()?.uploadPendingCrashes()
            
            Log.i(TAG, "Crash reporter initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize crash reporter", e)
            ContextLogger.e(TAG, "崩溃报告系统初始化失败", e)
        }
    }
    
    /**
     * 初始化崩溃报告系统（简化版本）
     * 使用默认的大小限制配置
     */
    fun initializeWithDefaults(
        context: Context,
        apiEndpoint: String,
        apiKey: String,
        enableDebugLogs: Boolean = false
    ) {
        initialize(context, apiEndpoint, apiKey, enableDebugLogs, null)
    }
    
    /**
     * 初始化崩溃报告系统（紧凑版本）
     * 使用更严格的大小限制，适合网络条件较差的情况
     */
    fun initializeCompact(
        context: Context,
        apiEndpoint: String,
        apiKey: String,
        enableDebugLogs: Boolean = false
    ) {
        val compactConfig = CrashDataSizeConfig(
            maxStackTraceLength = 3000,      // 3KB
            maxContextLogs = 20,             // 20条日志
            maxLogMessageLength = 200,       // 200字符每条
            maxUserActions = 10,             // 10个操作
            maxActionDetailLength = 50,      // 50字符每个操作
            maxPerformanceMetrics = 3,       // 3个性能指标
            maxTotalSizeKB = 50,            // 最大50KB
            enableCompression = true,        // 启用压缩
            prioritizeEssentialData = true   // 优先核心数据
        )
        
        initialize(context, apiEndpoint, apiKey, enableDebugLogs, compactConfig)
    }
    
    /**
     * 报告非致命异常
     */
    fun reportException(throwable: Throwable, customData: Map<String, String>? = null) {
        CrashReporter.reportException(throwable, customData)
    }
    
    /**
     * 报告自定义错误
     */
    fun reportError(
        errorType: String,
        errorMessage: String,
        customData: Map<String, String>? = null
    ) {
        val exception = CustomException(errorType, errorMessage)
        reportException(exception, customData)
    }
    
    /**
     * 清理旧的崩溃文件
     */
    fun cleanupOldCrashes() {
        CrashReporter.getInstance()?.cleanupOldCrashes()
    }
    
    /**
     * 手动上传待处理的崩溃报告
     */
    fun uploadPendingCrashes() {
        CrashReporter.getInstance()?.uploadPendingCrashes()
    }
    
    /**
     * 销毁崩溃报告系统
     */
    fun destroy() {
        CrashReporter.getInstance()?.destroy()
    }
}

/**
 * 自定义异常类
 */
class CustomException(
    private val errorType: String,
    private val errorMessage: String
) : Exception(errorMessage) {
    
    override fun toString(): String = "$errorType: $errorMessage"
    
    override val message: String?
        get() = errorMessage
}

/**
 * 常见错误类型定义
 */
object ErrorTypes {
    // 网络相关错误
    const val NETWORK_ERROR = "NetworkError"
    const val CONNECTION_TIMEOUT = "ConnectionTimeout"
    const val HTTP_ERROR = "HttpError"
    
    // 数据库相关错误
    const val DATABASE_ERROR = "DatabaseError"
    const val SQL_ERROR = "SQLError"
    
    // 文件操作错误
    const val FILE_NOT_FOUND = "FileNotFound"
    const val IO_ERROR = "IOError"
    const val PERMISSION_DENIED = "PermissionDenied"
    
    // 解析错误
    const val JSON_PARSE_ERROR = "JsonParseError"
    const val XML_PARSE_ERROR = "XmlParseError"
    
    // 业务逻辑错误
    const val VALIDATION_ERROR = "ValidationError"
    const val AUTHENTICATION_ERROR = "AuthenticationError"
    const val AUTHORIZATION_ERROR = "AuthorizationError"
    
    // 内存相关错误
    const val OUT_OF_MEMORY = "OutOfMemoryError"
    const val MEMORY_LEAK = "MemoryLeak"
    
    // UI相关错误
    const val UI_ERROR = "UIError"
    const val LAYOUT_ERROR = "LayoutError"
    
    // 第三方服务错误
    const val PAYMENT_ERROR = "PaymentError"
    const val PUSH_NOTIFICATION_ERROR = "PushNotificationError"
    const val ANALYTICS_ERROR = "AnalyticsError"
}

/**
 * 使用示例和工具函数
 */
object CrashReporterUtils {
    
    /**
     * 捕获并报告代码块中的异常
     */
    inline fun <T> catchAndReport(
        errorType: String = "UnknownError",
        customData: Map<String, String>? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            val data = customData?.toMutableMap() ?: mutableMapOf()
            data["error_type"] = errorType
            CrashReporter.reportException(e, data)
            null
        }
    }
    
    /**
     * 网络请求错误报告
     */
    fun reportNetworkError(
        url: String,
        method: String,
        responseCode: Int,
        errorMessage: String
    ) {
        val customData = mapOf(
            "url" to url,
            "method" to method,
            "response_code" to responseCode.toString(),
            "category" to "network"
        )
        
        CrashReporterManager.reportError(
            ErrorTypes.NETWORK_ERROR,
            errorMessage,
            customData
        )
    }
    
    /**
     * 数据库错误报告
     */
    fun reportDatabaseError(
        query: String,
        errorMessage: String
    ) {
        val customData = mapOf(
            "query" to query,
            "category" to "database"
        )
        
        CrashReporterManager.reportError(
            ErrorTypes.DATABASE_ERROR,
            errorMessage,
            customData
        )
    }
    
    /**
     * 用户操作错误报告
     */
    fun reportUserActionError(
        action: String,
        screen: String,
        errorMessage: String
    ) {
        val customData = mapOf(
            "user_action" to action,
            "screen" to screen,
            "category" to "user_interaction"
        )
        
        CrashReporterManager.reportError(
            ErrorTypes.UI_ERROR,
            errorMessage,
            customData
        )
    }
    
    /**
     * 性能问题报告
     */
    fun reportPerformanceIssue(
        operation: String,
        duration: Long,
        threshold: Long
    ) {
        if (duration > threshold) {
            val customData = mapOf(
                "operation" to operation,
                "duration_ms" to duration.toString(),
                "threshold_ms" to threshold.toString(),
                "category" to "performance"
            )
            
            CrashReporterManager.reportError(
                "PerformanceIssue",
                "Operation '$operation' took ${duration}ms (threshold: ${threshold}ms)",
                customData
            )
        }
    }
} 