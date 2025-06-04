package com.example.wooauto.crash_reporter

/**
 * 崩溃报告器配置
 * 包含文件大小优化选项
 */
data class CrashReporterConfig(
    val apiEndpoint: String,  // WordPress API端点
    val apiKey: String,       // API密钥
    val enableAutoUpload: Boolean = true,
    val enableDebugLogs: Boolean = false,
    val maxRetries: Int = 3,
    val connectTimeoutMs: Int = 30000,
    val readTimeoutMs: Int = 30000,
    
    // 文件大小优化配置
    val dataSizeConfig: CrashDataSizeConfig = CrashDataSizeConfig()
)

/**
 * 数据大小控制配置
 */
data class CrashDataSizeConfig(
    // 堆栈跟踪限制
    val maxStackTraceLength: Int = 6000,  // 减少到6KB
    
    // 上下文日志限制
    val maxContextLogs: Int = 50,  // 减少到50条
    val maxLogMessageLength: Int = 300,  // 每条日志最大300字符
    
    // 用户操作限制
    val maxUserActions: Int = 15,  // 减少到15个
    val maxActionDetailLength: Int = 100,  // 每个操作详情最大100字符
    
    // 性能数据限制
    val maxPerformanceMetrics: Int = 5,  // 减少到5个
    
    // 总体大小限制
    val maxTotalSizeKB: Int = 100,  // 总大小不超过100KB
    
    // 数据压缩选项
    val enableCompression: Boolean = true,  // 启用压缩
    
    // 关键数据优先级
    val prioritizeEssentialData: Boolean = true  // 优先保留关键数据
)

/**
 * 数据优先级定义
 */
enum class DataPriority {
    ESSENTIAL,    // 必需数据：错误类型、消息、基本堆栈
    IMPORTANT,    // 重要数据：设备信息、最近几条日志
    OPTIONAL      // 可选数据：详细堆栈、所有用户操作
} 