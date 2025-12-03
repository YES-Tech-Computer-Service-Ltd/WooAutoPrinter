package com.example.wooauto.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

// 保留旧类以兼容（如果需要），或直接废弃
data class GlobalErrorEvent(
    val title: String,
    val userMessage: String,
    val debugMessage: String,
    val onSettingsAction: (() -> Unit)? = null
)

/**
 * 全局错误管理器 (Centralized Error Hub)
 * 负责收集来自各个模块的错误状态，并提供聚合的错误信息给 UI 层。
 * 采用状态管理模式 (State Management)，而非事件流模式。
 */
@Singleton
class GlobalErrorManager @Inject constructor() {
    
    // 核心状态：当前活跃的错误集合 (Source -> Error)
    private val _activeErrors = MutableStateFlow<Map<ErrorSource, AppError>>(emptyMap())
    val activeErrors = _activeErrors.asStateFlow()

    // 兼容旧代码的事件流（逐步废弃）
    private val _errorEvents = MutableSharedFlow<GlobalErrorEvent>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    /**
     * 报告一个新的错误状态
     * 如果该 Source 已有错误，将被新错误覆盖
     */
    fun reportError(
        source: ErrorSource,
        title: String,
        message: String,
        debugInfo: String? = null,
        onSettingsAction: (() -> Unit)? = null
    ) {
        val error = AppError(
            source = source,
            title = title,
            message = message,
            debugInfo = debugInfo,
            onSettingsAction = onSettingsAction
        )
        
        _activeErrors.update { current ->
            // 更新 Map
            current + (source to error)
        }
    }

    /**
     * 解决/清除某个源的错误
     * 当故障恢复时调用此方法
     */
    fun resolveError(source: ErrorSource) {
        _activeErrors.update { current ->
            if (current.containsKey(source)) {
                current - source
            } else {
                current
            }
        }
    }
    
    /**
     * 清除所有错误
     */
    fun clearAllErrors() {
        _activeErrors.value = emptyMap()
    }

    /**
     * 旧接口兼容实现
     * 默认映射为 SYSTEM 类型，且无法自动 resolve (只能通过 UI 交互 dismiss)
     */
    fun showError(title: String, userMessage: String, debugMessage: String, onSettingsAction: (() -> Unit)? = null) {
        // 为了兼容，我们同时发送 Event (给未修改的 UI) 和 更新 State (给新 UI)
        // 注意：如果 MainActivity 改为监听 activeErrors，则这里只需要更新 State
        // 如果 MainActivity 还没改，则需要 emit Event。
        // 既然我们要改 MainActivity，这里主要负责 State 更新。
        // 但为了安全，我们将旧接口映射为 ErrorSource.OTHER 或 ErrorSource.SYSTEM
        
        // 注意：多次调用 showError 会覆盖上一次的 SYSTEM 错误。这与旧行为（连续弹窗）不同，
        // 但符合“错误中心”的设计理念（同一类错误只显示一个）。
        
        reportError(
            source = ErrorSource.SYSTEM,
            title = title,
            message = userMessage,
            debugInfo = debugMessage,
            onSettingsAction = onSettingsAction
        )
        
        // 同时也发射 Event，以防万一有其他监听者
        _errorEvents.tryEmit(GlobalErrorEvent(title, userMessage, debugMessage, onSettingsAction))
    }
}
