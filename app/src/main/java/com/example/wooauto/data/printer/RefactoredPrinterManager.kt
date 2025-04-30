package com.example.wooauto.data.printer

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterConnection
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 重构后的打印机管理器实现
 * 使用更清晰的接口和职责划分
 */
@Singleton
class RefactoredPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: DomainSettingRepository,
    private val orderRepository: DomainOrderRepository,
    private val templateManager: OrderPrintTemplate,
    private val connectionFactory: PrinterConnectionFactory,
    private val deviceScanner: PrinterDeviceScanner
) : PrinterManager {
    
    private val TAG = "PrinterManager"
    
    // 协程作用域
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 当前连接
    private var currentConnection: PrinterConnection? = null
    
    // 当前配置
    private var currentConfig: PrinterConfig? = null
    
    // 打印机状态Map和Flow
    private val printerStatusMap = mutableMapOf<String, PrinterStatus>()
    private val printerStatusFlows = mutableMapOf<String, MutableStateFlow<PrinterStatus>>()
    
    // 心跳任务
    private var heartbeatJob: Job? = null
    
    // 打印队列处理
    private val printQueue = mutableListOf<PrintJob>()
    private var isProcessingQueue = false
    
    // 扫描结果Flow
    private val scanResultFlow = MutableStateFlow<List<PrinterDevice>>(emptyList())
    
    /**
     * 打印任务数据类
     */
    private data class PrintJob(
        val orderId: Long,
        val printerConfig: PrinterConfig,
        val copies: Int = 1,
        val retryCount: Int = 0,
        val order: Order? = null
    )
    
    /**
     * 连接打印机
     */
    override suspend fun connect(config: PrinterConfig): Boolean {
        Log.d(TAG, "尝试连接打印机: ${config.name}")
        
        try {
            // 如果已经连接相同的打印机，直接返回
            if (currentConnection != null && 
                currentConfig?.id == config.id && 
                currentConnection?.isConnected() == true) {
                updatePrinterStatus(config, PrinterStatus.CONNECTED)
                return true
            }
            
            // 如果当前有其他打印机连接，先断开
            if (currentConnection != null && currentConfig?.id != config.id) {
                disconnect(currentConfig!!)
            }
            
            // 更新状态为连接中
            updatePrinterStatus(config, PrinterStatus.CONNECTING)
            
            // 创建新的连接
            val connection = connectionFactory.createConnection(config) ?: run {
                Log.e(TAG, "无法创建打印机连接: ${config.name}")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return false
            }
            
            // 建立连接
            val connected = connection.connect()
            if (!connected) {
                Log.e(TAG, "无法连接到打印机: ${config.name}")
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                return false
            }
            
            // 连接成功，保存当前连接
            currentConnection = connection
            currentConfig = config
            
            // 启动心跳
            startHeartbeat(config)
            
            // 更新状态
            updatePrinterStatus(config, PrinterStatus.CONNECTED)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "连接打印机异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.ERROR)
            return false
        }
    }
    
    /**
     * 断开打印机连接
     */
    override suspend fun disconnect(config: PrinterConfig) {
        withContext(Dispatchers.IO) {
            try {
                // 停止心跳
                stopHeartbeat()
                
                // 断开当前连接
                currentConnection?.disconnect()
                
                // 清空引用
                currentConnection = null
                currentConfig = null
                
                // 更新状态
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                
                // 更新设置
                settingRepository.setPrinterConnection(false)
            } catch (e: Exception) {
                Log.e(TAG, "断开打印机连接失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 获取打印机状态
     */
    override suspend fun getPrinterStatus(config: PrinterConfig): PrinterStatus {
        // 检查是否为当前连接的打印机
        if (currentConnection != null && currentConfig?.id == config.id) {
            // 读取实际状态
            val status = try {
                currentConnection?.readStatus() ?: PrinterStatus.DISCONNECTED
            } catch (e: Exception) {
                Log.e(TAG, "读取打印机状态失败: ${e.message}", e)
                PrinterStatus.ERROR
            }
            
            // 更新缓存状态
            updatePrinterStatus(config, status)
            return status
        }
        
        // 返回缓存状态
        return printerStatusMap[config.address] ?: PrinterStatus.DISCONNECTED
    }
    
    /**
     * 获取打印机状态Flow
     */
    override fun getPrinterStatusFlow(config: PrinterConfig): Flow<PrinterStatus> {
        return printerStatusFlows.getOrPut(config.address) {
            MutableStateFlow(printerStatusMap[config.address] ?: PrinterStatus.DISCONNECTED)
        }.asStateFlow()
    }
    
    /**
     * 扫描打印机
     */
    override suspend fun scanPrinters(type: String): List<PrinterDevice> {
        return when (type) {
            PrinterConfig.PRINTER_TYPE_BLUETOOTH -> scanBluetoothPrinters()
            PrinterConfig.PRINTER_TYPE_WIFI -> emptyList() // TODO
            PrinterConfig.PRINTER_TYPE_USB -> emptyList() // TODO
            else -> emptyList()
        }
    }
    
    /**
     * 扫描蓝牙打印机
     */
    private suspend fun scanBluetoothPrinters(): List<PrinterDevice> {
        Log.d(TAG, "开始扫描蓝牙打印机")
        
        try {
            // 启动扫描
            deviceScanner.startBluetoothScan()
            
            // 收集扫描结果
            deviceScanner.getDeviceScanFlow().collect { devices ->
                scanResultFlow.value = devices
            }
            
            // 返回最新结果
            return scanResultFlow.value
        } catch (e: Exception) {
            Log.e(TAG, "扫描蓝牙打印机失败: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * 获取蓝牙扫描结果Flow
     */
    fun getScanResultFlow(): Flow<List<PrinterDevice>> {
        return scanResultFlow.asStateFlow()
    }
    
    /**
     * 停止蓝牙扫描
     */
    fun stopBluetoothScan() {
        deviceScanner.stopBluetoothScan()
    }
    
    /**
     * 打印订单
     */
    override suspend fun printOrder(order: Order, config: PrinterConfig): Boolean {
        Log.d(TAG, "准备打印订单: ${order.number}")
        
        var retryCount = 0
        while (retryCount < 3) {
            try {
                // 确保打印机已连接
                if (!ensurePrinterConnected(config)) {
                    Log.e(TAG, "打印机连接失败，尝试重试...")
                    retryCount++
                    delay(1000)
                    continue
                }
                
                // 生成打印内容
                val content = generateOrderContent(order, config)
                
                // 打印内容
                val success = currentConnection?.print(content) ?: false
                
                // 如果启用自动切纸，执行切纸
                if (success && config.autoCut) {
                    currentConnection?.cutPaper(true)
                }
                
                // 处理打印结果
                if (success) {
                    handleSuccessfulPrint(order)
                    return true
                } else {
                    Log.e(TAG, "打印内容失败，尝试重试...")
                    retryCount++
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "打印订单异常: ${e.message}", e)
                retryCount++
                delay(1000)
            }
        }
        
        Log.e(TAG, "打印订单尝试3次后仍然失败: ${order.number}")
        return false
    }
    
    /**
     * 处理打印成功后的逻辑
     */
    private suspend fun handleSuccessfulPrint(order: Order): Boolean {
        Log.d(TAG, "打印成功，检查订单 ${order.id} 当前打印状态")
        
        // 获取最新的订单信息
        val latestOrder = orderRepository.getOrderById(order.id)
        
        // 只有在订单未被标记为已打印时才进行标记
        if (latestOrder != null && !latestOrder.isPrinted) {
            Log.d(TAG, "标记订单 ${order.id} 为已打印")
            val markResult = orderRepository.markOrderAsPrinted(order.id)
            if (markResult) {
                Log.d(TAG, "成功标记订单 ${order.id} 为已打印")
            } else {
                Log.e(TAG, "标记订单 ${order.id} 为已打印-失败")
            }
        } else {
            Log.d(TAG, "订单 ${order.id} 已被标记为已打印，跳过重复标记")
        }
        
        return true
    }
    
    /**
     * 生成订单打印内容
     */
    private fun generateOrderContent(order: Order, config: PrinterConfig): String {
        Log.d(TAG, "生成订单打印内容: ${order.number}")
        return templateManager.generateOrderPrintContent(order, config)
    }
    
    /**
     * 测试打印
     */
    override suspend fun printTest(config: PrinterConfig): Boolean {
        Log.d(TAG, "执行测试打印")
        
        try {
            // 确保打印机已连接
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "打印机连接失败，无法执行测试打印")
                return false
            }
            
            // 生成测试打印内容
            val testContent = templateManager.generateTestPrintContent(config)
            
            // 打印测试内容
            val success = currentConnection?.print(testContent) ?: false
            
            // 如果启用自动切纸，执行切纸
            if (success && config.autoCut) {
                currentConnection?.cutPaper(true)
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "测试打印异常: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 自动打印新订单
     */
    override suspend fun autoPrintNewOrder(order: Order): Boolean {
        Log.d(TAG, "开始自动打印订单 #${order.number}")
        
        try {
            // 检查订单状态
            if (!validateOrderForPrinting(order)) {
                return false
            }
            
            // 获取最新订单信息
            val latestOrder = orderRepository.getOrderById(order.id) ?: run {
                Log.e(TAG, "无法获取订单最新信息: #${order.number}")
                return false
            }
            
            // 再次检查最新数据中订单是否已打印
            if (latestOrder.isPrinted) {
                Log.d(TAG, "订单 #${order.number} 的最新状态已是已打印，跳过自动打印")
                return false
            }
            
            // 获取默认打印机配置
            val printerConfig = getDefaultPrinterConfig() ?: return false
            
            // 检查自动打印设置
            if (!printerConfig.isAutoPrint) {
                Log.d(TAG, "打印机 ${printerConfig.name} 未启用自动打印功能")
                return false
            }
            
            // 连接打印机并打印
            if (!ensurePrinterConnected(printerConfig)) {
                Log.e(TAG, "无法连接打印机 ${printerConfig.name}，自动打印失败")
                return false
            }
            
            // 执行打印
            return printOrder(order, printerConfig)
        } catch (e: Exception) {
            Log.e(TAG, "自动打印订单 #${order.number} 时发生异常: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 验证订单是否满足打印条件
     */
    private fun validateOrderForPrinting(order: Order): Boolean {
        // 检查订单是否已经打印
        if (order.isPrinted) {
            Log.d(TAG, "订单 #${order.number} 已标记为已打印，跳过自动打印")
            return false
        }
        
        // 检查订单状态
        if (order.status != "processing") {
            Log.d(TAG, "订单 #${order.number} 状态不是'处理中'(${order.status})，跳过自动打印")
            return false
        }
        
        return true
    }
    
    /**
     * 获取默认打印机配置
     */
    private suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        val printerConfig = settingRepository.getDefaultPrinterConfig()
        if (printerConfig == null) {
            Log.e(TAG, "未设置默认打印机，无法进行自动打印")
            return null
        }
        
        Log.d(TAG, "使用默认打印机: ${printerConfig.name}")
        return printerConfig
    }
    
    /**
     * 确保打印机已连接
     */
    private suspend fun ensurePrinterConnected(config: PrinterConfig): Boolean {
        // 检查当前连接状态
        if (currentConnection != null && 
            currentConfig?.id == config.id && 
            currentConnection?.isConnected() == true) {
            return true
        }
        
        // 尝试连接
        return connect(config)
    }
    
    /**
     * 测试打印机连接
     */
    override suspend fun testConnection(config: PrinterConfig): Boolean {
        // 尝试连接
        if (!ensurePrinterConnected(config)) {
            return false
        }
        
        // 测试连接
        return currentConnection?.testConnection() ?: false
    }
    
    /**
     * 启动心跳
     */
    private fun startHeartbeat(config: PrinterConfig) {
        // 停止现有心跳
        stopHeartbeat()
        
        // 启动新的心跳
        heartbeatJob = managerScope.launch {
            val HEARTBEAT_INTERVAL = 15000L // 15秒
            
            try {
                Log.d(TAG, "启动打印机心跳机制，间隔: ${HEARTBEAT_INTERVAL/1000}秒")
                
                while (true) {
                    try {
                        // 检查连接状态
                        if (currentConnection?.isConnected() == true) {
                            // 发送心跳
                            currentConnection?.sendHeartbeat()
                        } else {
                            // 尝试重连
                            Log.d(TAG, "打印机连接已断开，尝试重连")
                            connect(config)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "心跳检测异常: ${e.message}", e)
                    }
                    
                    // 等待下一次心跳
                    delay(HEARTBEAT_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "心跳任务异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    /**
     * 更新打印机状态
     */
    private fun updatePrinterStatus(config: PrinterConfig, status: PrinterStatus) {
        // 更新状态Map
        printerStatusMap[config.address] = status
        
        // 更新状态Flow
        val flow = printerStatusFlows.getOrPut(config.address) {
            MutableStateFlow(PrinterStatus.DISCONNECTED)
        }
        flow.value = status
    }
} 