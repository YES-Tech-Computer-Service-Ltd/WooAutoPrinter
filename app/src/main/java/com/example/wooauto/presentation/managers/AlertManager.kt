package com.example.wooauto.presentation.managers

import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.utils.NetworkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 警报类型定义
 */
enum class AlertType {
    NETWORK_ERROR,
    PRINTER_ERROR,
    STORE_INFO_MISSING,
    API_ERROR,
}

/**
 * 警报数据模型
 */
data class SystemAlert(
    val type: AlertType,
    val title: String,
    val message: String,
    val logs: List<String> = emptyList(),
    val isDismissible: Boolean = true,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * 统一警报管理器
 * 负责监控系统各项状态，并生成统一的警报流
 */
@Singleton
class AlertManager @Inject constructor(
    private val networkManager: NetworkManager,
    private val printerManager: PrinterManager,
    private val settingsRepository: DomainSettingRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 当前激活的警报集合
    private val _activeAlerts = MutableStateFlow<Map<AlertType, SystemAlert>>(emptyMap())
    val activeAlerts: StateFlow<Map<AlertType, SystemAlert>> = _activeAlerts.asStateFlow()
    
    // 用户已手动忽略的警报（本次会话有效）
    private val _dismissedAlerts = ConcurrentHashMap<AlertType, Boolean>()
    
    // 打印机日志缓存（包含时间戳的原始日志）
    private val _printerLogs = MutableStateFlow<List<String>>(emptyList())
    
    // API日志缓存
    private val _apiLogs = MutableStateFlow<List<String>>(emptyList())

    init {
        monitorNetwork()
        monitorPrinter()
    }
    
    private fun monitorNetwork() {
        scope.launch {
            networkManager.isNetworkAvailable.collectLatest { available ->
                if (available) {
                    // 网络恢复，移除警报并重置忽略状态
                    removeAlert(AlertType.NETWORK_ERROR)
                    _dismissedAlerts.remove(AlertType.NETWORK_ERROR)
                } else {
                    // 网络断开，如果未忽略则添加警报
                    if (_dismissedAlerts[AlertType.NETWORK_ERROR] != true) {
                        // 我们需要从NetworkManager获取实时日志
                        addAlert(
                            SystemAlert(
                                type = AlertType.NETWORK_ERROR,
                                title = "网络连接问题",
                                message = "检测到设备网络连接已断开，请检查您的网络设置。",
                                isDismissible = true
                            )
                        )
                    }
                }
            }
        }
    }
    
    private fun monitorPrinter() {
        scope.launch {
            val defaultConfig = settingsRepository.getDefaultPrinterConfig()
            if (defaultConfig != null) {
                addPrinterLog("初始化：监控打印机 ${defaultConfig.name}")
                // 启动一个独立的协程来收集状态，避免 settingsRepository 调用阻塞
                launch {
                    printerManager.getPrinterStatusFlow(defaultConfig).collectLatest { status ->
                        val isConnected = status == PrinterStatus.CONNECTED
                        
                        // 每次状态变更都打印日志，方便调试
                        addPrinterLog("收到状态更新: $status")
                        
                        if (isConnected) {
                            removeAlert(AlertType.PRINTER_ERROR)
                            _dismissedAlerts.remove(AlertType.PRINTER_ERROR)
                            addPrinterLog("状态恢复：已连接")
                        } else if (status == PrinterStatus.DISCONNECTED || status == PrinterStatus.ERROR) {
                            if (_dismissedAlerts[AlertType.PRINTER_ERROR] != true) {
                                addPrinterLog("状态异常：$status - 触发警报")
                                addAlert(
                                    SystemAlert(
                                        type = AlertType.PRINTER_ERROR,
                                        title = "打印机连接问题",
                                        message = "检测到打印机连接已断开，请检查设备蓝牙设置或打印机状态。",
                                        isDismissible = true
                                    )
                                )
                            } else {
                                addPrinterLog("状态异常：$status - 警报已忽略")
                            }
                        }
                    }
                }
            } else {
                addPrinterLog("未找到默认打印机配置，无法监控")
            }
        }
    }
    
    private fun addAlert(alert: SystemAlert) {
        val currentMap = _activeAlerts.value.toMutableMap()
        currentMap[alert.type] = alert
        _activeAlerts.value = currentMap
    }
    
    fun removeAlert(type: AlertType) {
        val currentMap = _activeAlerts.value.toMutableMap()
        if (currentMap.containsKey(type)) {
            currentMap.remove(type)
            _activeAlerts.value = currentMap
        }
    }
    
    /**
     * 用户手动忽略某个警报
     */
    fun dismissAlert(type: AlertType) {
        _dismissedAlerts[type] = true
        removeAlert(type)
    }
    
    // 专门用于触发"店铺信息缺失"警报（这个逻辑比较特殊，通常由外部触发）
    fun setStoreInfoMissingAlert(show: Boolean, onFixAction: () -> Unit) {
        if (show) {
            addAlert(
                SystemAlert(
                    type = AlertType.STORE_INFO_MISSING,
                    title = "完善店铺信息",
                    message = "检测到您的店铺信息尚未设置。为了打印小票的完整性，请填写店铺信息。",
                    isDismissible = true,
                    actionLabel = "立即设置",
                    onAction = onFixAction
                )
            )
        } else {
            removeAlert(AlertType.STORE_INFO_MISSING)
        }
    }
    
    // API 警报触发
    fun reportApiError(code: Int, message: String, url: String) {
        val logMessage = "请求失败: $code - $message ($url)"
        addApiLog(logMessage, isSuccess = false)
        
        if (_dismissedAlerts[AlertType.API_ERROR] != true) {
            addAlert(
                SystemAlert(
                    type = AlertType.API_ERROR,
                    title = "API 请求异常 ($code)",
                    message = "服务器返回错误: $message。请检查 API 设置或服务器状态。",
                    isDismissible = true
                )
            )
        }
    }
    
    fun reportApiSuccess(url: String) {
        // 请求成功，自动清除 API 警报（如果是因为网络波动导致的临时错误）
        if (_activeAlerts.value.containsKey(AlertType.API_ERROR)) {
            removeAlert(AlertType.API_ERROR)
            _dismissedAlerts.remove(AlertType.API_ERROR)
        }
        addApiLog("请求成功: $url", isSuccess = true)
    }
    
    fun addPrinterLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        val currentLogs = _printerLogs.value.toMutableList()
        currentLogs.add(0, logEntry)
        if (currentLogs.size > 100) currentLogs.removeAt(currentLogs.lastIndex)
        _printerLogs.value = currentLogs
    }
    
    // 供外部（如BluetoothPrinterManager）调用以记录详细异常堆栈
    fun addPrinterError(message: String, e: Throwable?) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("[$timestamp] [ERROR] $message")
        if (e != null) {
            sb.append("\nType: ${e.javaClass.simpleName}")
            sb.append("\nMsg: ${e.message}")
            val stackTrace = e.stackTrace.take(3)
            for (element in stackTrace) {
                sb.append("\n  at ${element}")
            }
        }
        val logEntry = sb.toString()
        val currentLogs = _printerLogs.value.toMutableList()
        currentLogs.add(0, logEntry)
        if (currentLogs.size > 100) currentLogs.removeAt(currentLogs.lastIndex)
        _printerLogs.value = currentLogs
    }
    
    private fun addApiLog(message: String, isSuccess: Boolean) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        
        var currentLogs = _apiLogs.value.toMutableList()
        
        if (isSuccess) {
            // 如果是成功日志，只保留最后一条成功日志，移除之前的成功日志，保留错误日志
            // 策略：过滤掉所有之前的“请求成功”日志，只把最新的加到最前面
            // 或者更简单：只保留最近的一条成功日志在顶部？
            // 需求是：之前返回200成功的那些内容不用全部显示，就显示最后一次成功
            
            // 移除列表中已有的所有成功日志
            currentLogs.removeAll { it.contains("请求成功") }
            // 将最新成功日志加到顶部
            currentLogs.add(0, logEntry)
        } else {
            // 错误日志直接添加
            currentLogs.add(0, logEntry)
        }
        
        // 限制总数量
        if (currentLogs.size > 50) currentLogs = currentLogs.take(50).toMutableList()
        
        _apiLogs.value = currentLogs
    }
    
    fun getPrinterLogs(): StateFlow<List<String>> = _printerLogs.asStateFlow()
    fun getApiLogs(): StateFlow<List<String>> = _apiLogs.asStateFlow()
}
