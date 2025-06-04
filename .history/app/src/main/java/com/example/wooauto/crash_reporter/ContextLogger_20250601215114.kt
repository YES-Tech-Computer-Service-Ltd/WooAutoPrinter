package com.example.wooauto.crash_reporter

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 上下文日志收集器
 * 在内存中维护一个滚动的日志缓冲区，用于崩溃报告时提供上下文信息
 */
object ContextLogger {
    
    private const val MAX_LOG_ENTRIES = 500  // 最大日志条目数
    private const val MAX_USER_ACTIONS = 50  // 最大用户操作记录数
    
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val userActionBuffer = ConcurrentLinkedQueue<UserAction>()
    private val entryCounter = AtomicInteger(0)
    
    /**
     * 日志条目
     */
    data class LogEntry(
        val id: Int,
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val throwable: String? = null
    ) {
        fun toFormattedString(): String {
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val timeStr = dateFormat.format(Date(timestamp))
            return "$timeStr $level/$tag: $message${throwable?.let { "\n$it" } ?: ""}"
        }
    }
    
    /**
     * 用户操作记录
     */
    data class UserAction(
        val timestamp: Long,
        val action: String,
        val screen: String,
        val details: Map<String, String> = emptyMap()
    ) {
        fun toFormattedString(): String {
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val timeStr = dateFormat.format(Date(timestamp))
            val detailsStr = if (details.isNotEmpty()) {
                details.entries.joinToString(", ") { "${it.key}=${it.value}" }
            } else ""
            return "$timeStr [$screen] $action${if (detailsStr.isNotEmpty()) " ($detailsStr)" else ""}"
        }
    }
    
    /**
     * 记录日志
     */
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            id = entryCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable?.let { getStackTrace(it) }
        )
        
        logBuffer.offer(entry)
        
        // 保持缓冲区大小限制
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.poll()
        }
    }
    
    /**
     * 记录用户操作
     */
    fun logUserAction(action: String, screen: String, details: Map<String, String> = emptyMap()) {
        val userAction = UserAction(
            timestamp = System.currentTimeMillis(),
            action = action,
            screen = screen,
            details = details
        )
        
        userActionBuffer.offer(userAction)
        
        // 保持缓冲区大小限制
        while (userActionBuffer.size > MAX_USER_ACTIONS) {
            userActionBuffer.poll()
        }
    }
    
    /**
     * 获取最近的日志（发生错误前的上下文）
     */
    fun getRecentLogs(maxEntries: Int = 100): List<LogEntry> {
        return logBuffer.toList().takeLast(maxEntries)
    }
    
    /**
     * 获取最近的用户操作
     */
    fun getRecentUserActions(maxActions: Int = 20): List<UserAction> {
        return userActionBuffer.toList().takeLast(maxActions)
    }
    
    /**
     * 获取格式化的上下文日志字符串
     */
    fun getContextLogsAsString(maxEntries: Int = 100): String {
        val logs = getRecentLogs(maxEntries)
        return if (logs.isNotEmpty()) {
            buildString {
                appendLine("=== 上下文日志 (最近 ${logs.size} 条) ===")
                logs.forEach { log ->
                    appendLine(log.toFormattedString())
                }
            }
        } else {
            "无可用日志"
        }
    }
    
    /**
     * 获取格式化的用户操作字符串
     */
    fun getUserActionsAsString(maxActions: Int = 20): String {
        val actions = getRecentUserActions(maxActions)
        return if (actions.isNotEmpty()) {
            buildString {
                appendLine("=== 用户操作序列 (最近 ${actions.size} 条) ===")
                actions.forEach { action ->
                    appendLine(action.toFormattedString())
                }
            }
        } else {
            "无用户操作记录"
        }
    }
    
    /**
     * 清空日志缓冲区
     */
    fun clear() {
        logBuffer.clear()
        userActionBuffer.clear()
    }
    
    /**
     * 获取异常堆栈跟踪
     */
    private fun getStackTrace(throwable: Throwable): String {
        return try {
            val stringWriter = java.io.StringWriter()
            val printWriter = java.io.PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            stringWriter.toString()
        } catch (e: Exception) {
            "Error getting stack trace: ${e.message}"
        }
    }
    
    // 便捷的日志记录方法
    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String) = log("ERROR", tag, message)
    fun e(tag: String, message: String, throwable: Throwable) = log("ERROR", tag, message, throwable)
}

/**
 * 应用状态收集器
 * 收集应用当前状态信息
 */
object AppStateCollector {
    
    private var currentScreen: String = "Unknown"
    private var currentUserId: String? = null
    private var appState: String = "Foreground"
    private val stateChangeHistory = mutableListOf<StateChange>()
    
    data class StateChange(
        val timestamp: Long,
        val fromState: String,
        val toState: String,
        val trigger: String
    )
    
    /**
     * 设置当前屏幕
     */
    fun setCurrentScreen(screenName: String) {
        val oldScreen = currentScreen
        currentScreen = screenName
        ContextLogger.logUserAction("screen_change", screenName, mapOf(
            "from_screen" to oldScreen,
            "to_screen" to screenName
        ))
    }
    
    /**
     * 设置当前用户ID
     */
    fun setCurrentUserId(userId: String?) {
        currentUserId = userId
        ContextLogger.logUserAction("user_session", currentScreen, mapOf(
            "user_id" to (userId ?: "anonymous")
        ))
    }
    
    /**
     * 设置应用状态
     */
    fun setAppState(state: String, trigger: String = "unknown") {
        val oldState = appState
        appState = state
        
        stateChangeHistory.add(StateChange(
            timestamp = System.currentTimeMillis(),
            fromState = oldState,
            toState = state,
            trigger = trigger
        ))
        
        // 保持历史记录限制
        if (stateChangeHistory.size > 20) {
            stateChangeHistory.removeAt(0)
        }
        
        ContextLogger.logUserAction("app_state_change", currentScreen, mapOf(
            "from_state" to oldState,
            "to_state" to state,
            "trigger" to trigger
        ))
    }
    
    /**
     * 记录用户交互
     */
    fun logUserInteraction(action: String, elementId: String? = null, details: Map<String, String> = emptyMap()) {
        val actionDetails = details.toMutableMap()
        elementId?.let { actionDetails["element_id"] = it }
        
        ContextLogger.logUserAction(action, currentScreen, actionDetails)
    }
    
    /**
     * 获取当前应用状态摘要
     */
    fun getAppStateSummary(): Map<String, String> {
        return mapOf(
            "current_screen" to currentScreen,
            "current_user_id" to (currentUserId ?: "anonymous"),
            "app_state" to appState,
            "state_changes_count" to stateChangeHistory.size.toString(),
            "last_state_change" to (stateChangeHistory.lastOrNull()?.let { 
                "${it.fromState} -> ${it.toState} (${it.trigger})" 
            } ?: "none")
        )
    }
    
    /**
     * 获取状态变化历史
     */
    fun getStateChangeHistory(): String {
        return if (stateChangeHistory.isNotEmpty()) {
            buildString {
                appendLine("=== 应用状态变化历史 ===")
                stateChangeHistory.forEach { change ->
                    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                    val timeStr = dateFormat.format(Date(change.timestamp))
                    appendLine("$timeStr: ${change.fromState} -> ${change.toState} (${change.trigger})")
                }
            }
        } else {
            "无状态变化记录"
        }
    }
}

/**
 * 性能监控器
 * 监控应用性能指标
 */
object PerformanceMonitor {
    
    private val performanceMetrics = mutableMapOf<String, PerformanceMetric>()
    
    data class PerformanceMetric(
        val name: String,
        val startTime: Long,
        var endTime: Long? = null,
        var duration: Long? = null,
        val metadata: MutableMap<String, String> = mutableMapOf()
    )
    
    /**
     * 开始性能监控
     */
    fun startMonitoring(operationName: String, metadata: Map<String, String> = emptyMap()): String {
        val metricId = "${operationName}_${System.currentTimeMillis()}"
        performanceMetrics[metricId] = PerformanceMetric(
            name = operationName,
            startTime = System.currentTimeMillis(),
            metadata = metadata.toMutableMap()
        )
        return metricId
    }
    
    /**
     * 结束性能监控
     */
    fun endMonitoring(metricId: String, additionalMetadata: Map<String, String> = emptyMap()) {
        performanceMetrics[metricId]?.let { metric ->
            metric.endTime = System.currentTimeMillis()
            metric.duration = metric.endTime!! - metric.startTime
            metric.metadata.putAll(additionalMetadata)
            
            ContextLogger.d("PerformanceMonitor", 
                "Operation '${metric.name}' completed in ${metric.duration}ms")
            
            // 如果操作时间过长，记录为上下文信息
            if (metric.duration!! > 1000) {  // 超过1秒
                ContextLogger.w("PerformanceMonitor", 
                    "Slow operation detected: '${metric.name}' took ${metric.duration}ms")
            }
        }
    }
    
    /**
     * 获取性能摘要
     */
    fun getPerformanceSummary(): String {
        val recentMetrics = performanceMetrics.values
            .filter { it.endTime != null }
            .sortedByDescending { it.startTime }
            .take(10)
        
        return if (recentMetrics.isNotEmpty()) {
            buildString {
                appendLine("=== 性能指标 (最近10个操作) ===")
                recentMetrics.forEach { metric ->
                    appendLine("${metric.name}: ${metric.duration}ms")
                    if (metric.metadata.isNotEmpty()) {
                        appendLine("  元数据: ${metric.metadata}")
                    }
                }
            }
        } else {
            "无性能数据"
        }
    }
    
    /**
     * 清理旧的性能数据
     */
    fun cleanup() {
        val cutoffTime = System.currentTimeMillis() - (60 * 60 * 1000) // 1小时前
        performanceMetrics.values.removeAll { metric ->
            metric.startTime < cutoffTime
        }
    }
} 