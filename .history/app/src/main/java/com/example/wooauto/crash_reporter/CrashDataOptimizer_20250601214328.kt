package com.example.wooauto.crash_reporter

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import java.util.Base64

/**
 * 崩溃数据优化器
 * 负责压缩、裁剪和优化崩溃报告数据大小
 */
object CrashDataOptimizer {
    
    /**
     * 优化崩溃数据
     */
    fun optimizeCrashData(
        crashData: CrashData,
        config: CrashDataSizeConfig
    ): CrashData {
        
        // 1. 裁剪各个数据字段
        val optimizedStackTrace = truncateStackTrace(crashData.stackTrace, config.maxStackTraceLength)
        val optimizedContextLogs = optimizeContextLogs(crashData.contextLogs, config)
        val optimizedUserActions = optimizeUserActions(crashData.userActions, config)
        val optimizedPerformanceData = optimizePerformanceData(crashData.performanceData, config)
        
        // 2. 创建优化后的数据对象
        val optimizedData = crashData.copy(
            stackTrace = optimizedStackTrace,
            contextLogs = optimizedContextLogs,
            userActions = optimizedUserActions,
            performanceData = optimizedPerformanceData
        )
        
        // 3. 检查总大小并进一步优化（如果需要）
        val totalSize = estimateDataSize(optimizedData)
        return if (totalSize > config.maxTotalSizeKB * 1024) {
            furtherOptimize(optimizedData, config)
        } else {
            optimizedData
        }
    }
    
    /**
     * 压缩文本数据
     */
    fun compressText(text: String): String? {
        if (text.isEmpty()) return null
        
        return try {
            val byteOut = ByteArrayOutputStream()
            val gzipOut = GZIPOutputStream(byteOut)
            gzipOut.write(text.toByteArray(Charsets.UTF_8))
            gzipOut.close()
            
            val compressed = byteOut.toByteArray()
            val base64 = Base64.getEncoder().encodeToString(compressed)
            
            // 只有在压缩效果明显时才返回压缩数据
            if (compressed.size < text.toByteArray().size * 0.7) {
                "GZIP:$base64"
            } else {
                text // 压缩效果不佳，返回原文
            }
        } catch (e: Exception) {
            text // 压缩失败，返回原文
        }
    }
    
    /**
     * 解压缩文本数据
     */
    fun decompressText(compressedText: String): String {
        if (!compressedText.startsWith("GZIP:")) {
            return compressedText
        }
        
        return try {
            val base64Data = compressedText.substring(5)
            val compressed = Base64.getDecoder().decode(base64Data)
            val gzipIn = GZIPInputStream(compressed.inputStream())
            gzipIn.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            compressedText // 解压失败，返回原文
        }
    }
    
    /**
     * 裁剪堆栈跟踪
     */
    private fun truncateStackTrace(stackTrace: String, maxLength: Int): String {
        if (stackTrace.length <= maxLength) return stackTrace
        
        val lines = stackTrace.lines()
        if (lines.isEmpty()) return stackTrace
        
        // 保留最重要的前几行和异常信息
        val importantLines = mutableListOf<String>()
        var currentLength = 0
        
        // 首先添加异常声明行
        for (line in lines) {
            if (currentLength + line.length > maxLength - 100) break
            importantLines.add(line)
            currentLength += line.length + 1
            
            // 如果遇到"Caused by"，优先保留
            if (line.trim().startsWith("Caused by") && importantLines.size > 5) {
                break
            }
            
            // 保留前10行关键堆栈
            if (importantLines.size >= 10 && !line.trim().startsWith("Caused by")) {
                break
            }
        }
        
        val result = importantLines.joinToString("\n")
        return if (result.length < stackTrace.length) {
            "$result\n... [堆栈跟踪已截断，原长度: ${stackTrace.length}字符]"
        } else {
            result
        }
    }
    
    /**
     * 优化上下文日志
     */
    private fun optimizeContextLogs(contextLogs: String, config: CrashDataSizeConfig): String {
        if (contextLogs.isEmpty()) return contextLogs
        
        val lines = contextLogs.lines()
        if (lines.size <= config.maxContextLogs) return contextLogs
        
        // 保留最近的日志和错误级别的日志
        val priorityLines = mutableListOf<String>()
        val errorLines = lines.filter { it.contains(" E/") || it.contains(" ERROR") }
        val recentLines = lines.takeLast(config.maxContextLogs - errorLines.size.coerceAtMost(10))
        
        // 先添加错误日志
        priorityLines.addAll(errorLines.take(10))
        // 再添加最近的日志
        priorityLines.addAll(recentLines.filter { it !in priorityLines })
        
        // 截断过长的日志消息
        val truncatedLines = priorityLines.take(config.maxContextLogs).map { line ->
            if (line.length > config.maxLogMessageLength) {
                line.take(config.maxLogMessageLength - 3) + "..."
            } else {
                line
            }
        }
        
        return truncatedLines.joinToString("\n") + 
               if (lines.size > truncatedLines.size) "\n[日志已优化，原${lines.size}条，现${truncatedLines.size}条]" else ""
    }
    
    /**
     * 优化用户操作
     */
    private fun optimizeUserActions(userActions: String, config: CrashDataSizeConfig): String {
        if (userActions.isEmpty()) return userActions
        
        val lines = userActions.lines()
        if (lines.size <= config.maxUserActions) return userActions
        
        // 保留最近的用户操作
        val recentActions = lines.takeLast(config.maxUserActions)
        
        // 截断详情过长的操作
        val truncatedActions = recentActions.map { action ->
            if (action.length > config.maxActionDetailLength) {
                action.take(config.maxActionDetailLength - 3) + "..."
            } else {
                action
            }
        }
        
        return truncatedActions.joinToString("\n") +
               if (lines.size > truncatedActions.size) "\n[用户操作已优化，原${lines.size}条，现${truncatedActions.size}条]" else ""
    }
    
    /**
     * 优化性能数据
     */
    private fun optimizePerformanceData(performanceData: String, config: CrashDataSizeConfig): String {
        if (performanceData.isEmpty()) return performanceData
        
        val lines = performanceData.lines()
        if (lines.size <= config.maxPerformanceMetrics + 2) return performanceData // +2 for headers
        
        // 保留标题和最近的几个性能指标
        val titleLines = lines.filter { it.startsWith("===") || it.trim().isEmpty() }
        val dataLines = lines.filter { !it.startsWith("===") && it.trim().isNotEmpty() }
        
        val recentMetrics = dataLines.takeLast(config.maxPerformanceMetrics)
        
        return (titleLines + recentMetrics).joinToString("\n") +
               if (dataLines.size > recentMetrics.size) "\n[性能数据已优化，原${dataLines.size}项，现${recentMetrics.size}项]" else ""
    }
    
    /**
     * 进一步优化（当总大小仍然过大时）
     */
    private fun furtherOptimize(crashData: CrashData, config: CrashDataSizeConfig): CrashData {
        // 更激进的优化策略
        return crashData.copy(
            stackTrace = truncateStackTrace(crashData.stackTrace, config.maxStackTraceLength / 2),
            contextLogs = optimizeContextLogs(crashData.contextLogs, config.copy(
                maxContextLogs = config.maxContextLogs / 2,
                maxLogMessageLength = config.maxLogMessageLength / 2
            )),
            userActions = optimizeUserActions(crashData.userActions, config.copy(
                maxUserActions = config.maxUserActions / 2
            )),
            performanceData = if (config.prioritizeEssentialData) "" else crashData.performanceData
        )
    }
    
    /**
     * 估算数据大小（字节）
     */
    private fun estimateDataSize(crashData: CrashData): Int {
        return crashData.toJson().toByteArray(Charsets.UTF_8).size
    }
    
    /**
     * 获取数据大小统计
     */
    fun getDataSizeStats(crashData: CrashData): Map<String, Int> {
        return mapOf(
            "total" to estimateDataSize(crashData),
            "stackTrace" to crashData.stackTrace.toByteArray().size,
            "contextLogs" to crashData.contextLogs.toByteArray().size,
            "userActions" to crashData.userActions.toByteArray().size,
            "performanceData" to crashData.performanceData.toByteArray().size,
            "basic" to (crashData.toJson().toByteArray().size - 
                       crashData.stackTrace.toByteArray().size -
                       crashData.contextLogs.toByteArray().size -
                       crashData.userActions.toByteArray().size -
                       crashData.performanceData.toByteArray().size).coerceAtLeast(0)
        )
    }
} 