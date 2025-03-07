package com.example.wooauto.data.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import com.example.wooauto.domain.models.Order
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: DomainSettingRepository,
    private val orderRepository: DomainOrderRepository,
    private val templateManager: OrderPrintTemplate
) : PrinterManager {

    // 创建协程作用域
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val bluetoothAdapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    } else {
        BluetoothAdapter.getDefaultAdapter()
    }
    
    private var currentConnection: BluetoothConnection? = null
    private var currentPrinter: EscPosPrinter? = null
    
    // 打印机状态Map和Flow
    private val printerStatusMap = mutableMapOf<String, PrinterStatus>()
    private val printerStatusFlows = mutableMapOf<String, MutableStateFlow<PrinterStatus>>()
    
    // 打印队列
    private val printQueue = mutableListOf<PrintJob>()
    private var isProcessingQueue = false
    
    // 蓝牙设备扫描相关
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private var isScanning = false
    private val scanResultFlow = MutableStateFlow<List<PrinterDevice>>(emptyList())
    
    // 添加标准串口服务UUID常量
    companion object {
        private const val TAG = "BluetoothPrinterManager"
        // 多个常用的蓝牙串口服务UUID，按照常见度排序尝试
        private val PRINTER_UUIDS = listOf(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"), // 标准SPP UUID
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"), // 小写变体
            UUID.fromString("49535343-FE7D-4AE5-8FA9-9FAFD205E455"), // 部分打印机使用
            UUID.fromString("8CE255C0-200A-11E0-AC64-0800200C9A66"), // 蓝牙低功耗特性UUID
            UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")  // 通用备选UUID
        )
        // 蓝牙扫描超时时间 (毫秒) - 安卓7设备可能需要更长时间
        private const val SCAN_TIMEOUT = 60000L // 增加到60秒
        // 连接超时时间 (毫秒)
        private const val CONNECT_TIMEOUT = 15000L
        // 最大重试次数
        private const val MAX_RETRY_COUNT = 5
        
        // 蓝牙权限问题的诊断信息
        const val BLUETOOTH_PERMISSION_ISSUE = """
            蓝牙权限问题排查指南:
            1. 对于Android 12(API 31)及以上版本:
               - 需要在Manifest中声明BLUETOOTH_CONNECT和BLUETOOTH_SCAN权限
               - 需要在运行时请求这些权限
            2. 对于Android 11(API 30)及以下版本:
               - 需要BLUETOOTH和BLUETOOTH_ADMIN权限
               - 需要位置权限(ACCESS_FINE_LOCATION)
            3. 对于Android 6-11，确保:
               - 已启用位置服务
               - 已授予位置权限
            4. 对于所有版本:
               - 确保蓝牙已启用
               - 对于扫描未配对设备，位置服务必须开启
            """
    }
    
    // 蓝牙设备发现广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 获取找到的蓝牙设备
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // 增加详细日志帮助调试
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        Log.d(TAG, "发现蓝牙设备: ${it.name ?: "未知"} (${it.address}), RSSI: $rssi dBm")
                        
                        // 保存设备，不过滤任何设备
                        discoveredDevices[it.address] = it
                        
                        // 更新扫描结果
                        updateScanResults()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "蓝牙设备发现完成，找到${discoveredDevices.size}个设备")
                    isScanning = false
                    
                    // 最后一次更新扫描结果
                    updateScanResults()
                    
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.w(TAG, "无法注销蓝牙广播接收器", e)
                    }
                }
                // 监听配对状态变化
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    
                    Log.d(TAG, "设备${device?.name ?: "未知"}绑定状态变化: $prevBondState -> $bondState")
                    
                    // 如果设备完成配对，尝试连接
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        device?.let {
                            discoveredDevices[it.address] = it
                            updateScanResults()
                        }
                    }
                }
            }
        }
    }
    
    // 新增配对广播接收器
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                
                Log.d(TAG, "配对状态变化: ${bondStateToString(prevBondState)} -> ${bondStateToString(bondState)}")
                
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "设备配对成功: ${device?.name ?: "未知设备"}")
                    // 设备配对成功，尝试连接
                    device?.let { 
                        managerScope.launch {
                            tryConnectWithDevice(it.address)
                        }
                    }
                }
            }
        }
        
        private fun bondStateToString(state: Int): String {
            return when (state) {
                BluetoothDevice.BOND_NONE -> "未配对"
                BluetoothDevice.BOND_BONDING -> "正在配对"
                BluetoothDevice.BOND_BONDED -> "已配对"
                else -> "未知状态"
            }
        }
    }
    
    override suspend fun connect(config: PrinterConfig): Boolean {
        if (config.type != PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
            Log.e(TAG, "错误: 尝试连接一个非蓝牙打印机")
            updatePrinterStatus(config.address, PrinterStatus.ERROR)
            return false
        }
        
        // 取消当前的设备扫描，这对成功连接非常重要
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isDiscovering == true) {
            Log.d(TAG, "取消当前设备发现以提高连接成功率")
            bluetoothAdapter.cancelDiscovery()
        }
        
        Log.d(TAG, "正在连接蓝牙打印机: ${config.name} (${config.address})")
        updatePrinterStatus(config.address, PrinterStatus.CONNECTING)
        
        try {
            // 获取设备
            val device = getBluetoothDevice(config.address)
            if (device == null) {
                Log.e(TAG, "无法获取蓝牙设备: ${config.address}")
                updatePrinterStatus(config.address, PrinterStatus.ERROR)
                return false
            }
            
            // 检查配对状态
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                Log.d(TAG, "设备未配对，尝试配对...")
                // 尝试创建配对
                val method = device.javaClass.getMethod("createBond")
                val success = method.invoke(device) as Boolean
                
                if (success) {
                    // 等待配对完成 - 最多20秒
                    for (i in 1..20) {
                        if (device.bondState == BluetoothDevice.BOND_BONDED) {
                            Log.d(TAG, "配对成功")
                            break
                        }
                        delay(1000)
                        Log.d(TAG, "等待配对完成... ${device.bondState}")
                    }
                }
                
                // 如果仍未配对，返回失败
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    Log.e(TAG, "设备配对失败")
                    updatePrinterStatus(config.address, PrinterStatus.ERROR)
                    return false
                }
            }
            
            // 循环尝试所有UUID
            var connected = false
            var lastException: Exception? = null
            
            for (uuid in PRINTER_UUIDS) {
                // 使用递增的重试次数
                for (attempt in 1..MAX_RETRY_COUNT) {
                    try {
                        Log.d(TAG, "尝试连接 UUID $uuid (尝试 $attempt/${MAX_RETRY_COUNT})")
                        
                        // 创建蓝牙连接 - 注意库不支持传入UUID，这里只用于日志
                        val connection = BluetoothConnection(device)
                        connection.connect()
                        
                        Log.d(TAG, "成功连接蓝牙设备 ${config.name}")
                        currentConnection = connection
                        
                        // 创建打印机实例
                        val printer = EscPosPrinter(connection, 203, config.paperWidth.toFloat(), 48)
                        currentPrinter = printer
                        
                        connected = true
                        updatePrinterStatus(config.address, PrinterStatus.CONNECTED)
                        break
                    } catch (e: Exception) {
                        lastException = e
                        Log.e(TAG, "连接失败 (UUID: $uuid, 尝试: $attempt): ${e.message}")
                        
                        // 在重试之前断开连接
                        try {
                            currentConnection?.disconnect()
                        } catch (e2: Exception) {
                            Log.e(TAG, "断开连接失败: ${e2.message}")
                        }
                        
                        currentConnection = null
                        currentPrinter = null
                        
                        // 在重试之前添加延迟，每次尝试增加延迟
                        if (attempt < MAX_RETRY_COUNT) {
                            val delayTime = 1000L * attempt
                            Log.d(TAG, "延迟 ${delayTime}ms 后重试")
                            delay(delayTime)
                        }
                    }
                }
                
                if (connected) break
            }
            
            if (!connected) {
                Log.e(TAG, "所有连接尝试均失败: ${lastException?.message}")
                updatePrinterStatus(config.address, PrinterStatus.ERROR)
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "连接蓝牙打印机异常: ${e.message}")
            updatePrinterStatus(config.address, PrinterStatus.ERROR)
            return false
        }
    }
    
    /**
     * 获取蓝牙设备扫描结果Flow
     */
    fun getScanResultFlow(): Flow<List<PrinterDevice>> = scanResultFlow.asStateFlow()
    
    override suspend fun scanPrinters(type: String): List<PrinterDevice> {
        if (type != PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
            Log.e(TAG, "不支持的打印机类型: $type")
            return emptyList()
        }
        
        // 添加安卓版本日志帮助调试
        Log.d(TAG, "===== 蓝牙打印机扫描开始 =====")
        Log.d(TAG, "安卓版本: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "扫描打印机类型: $type")
        
        // 输出诊断信息，帮助识别问题
        logBluetoothDiagnostics()

        // 检查权限，但在低版本Android上不阻止操作
        val hasPermission = hasBluetoothPermission()
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e(TAG, "没有蓝牙权限")
            try {
                // 尝试发送错误信息给UI
                scanResultFlow.tryEmit(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "发送蓝牙权限错误消息失败", e)
            }
            return emptyList()
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "蓝牙未启用")
                return emptyList()
            }

            // 清空设备列表
            discoveredDevices.clear()
            
            // 首先添加配对设备 - 这对Android 7尤其重要
            try {
                val pairedDevices = bluetoothAdapter.bondedDevices ?: emptySet()
                Log.d(TAG, "已配对的设备数量: ${pairedDevices.size}")
                
                for (device in pairedDevices) {
                    // 在日志中记录所有配对设备信息以帮助调试
                    Log.d(TAG, "已配对设备: ${device.name ?: "未知设备"} (${device.address}), 绑定状态: ${device.bondState}")
                    discoveredDevices[device.address] = device
                    
                    // 立即更新已配对设备，确保用户可以看到
                    updateScanResults()
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取已配对设备失败: ${e.message}")
                // 继续执行，不要因为这个错误中断整个流程
            }
            
            // 确保已停止之前的扫描
            if (isScanning) {
                Log.d(TAG, "已有扫描正在进行，先停止它")
                bluetoothAdapter.cancelDiscovery()
                isScanning = false
                // 给适配器一点时间恢复
                delay(1000)
            }

            // 注册接收器
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                // 添加更多动作以捕获所有可能的蓝牙事件
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            
            try {
                context.registerReceiver(bluetoothReceiver, filter)
            } catch (e: Exception) {
                Log.e(TAG, "注册蓝牙广播接收器失败: ${e.message}")
                // 继续执行，至少可以显示已配对设备
            }
            
            // 尝试开始扫描
            try {
                // 开始扫描
                isScanning = true
                val success = bluetoothAdapter.startDiscovery()
                Log.d(TAG, "开始蓝牙设备发现: $success")
                
                if (!success) {
                    Log.e(TAG, "无法开始蓝牙设备发现")
                    // 但仍然继续，因为我们至少有配对的设备
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动蓝牙扫描失败: ${e.message}")
                // 继续执行，至少可以显示已配对设备
            }

            // 启动超时协程而不是暂停当前协程
            managerScope.launch {
                try {
                    delay(SCAN_TIMEOUT)
                    if (isScanning) {
                        Log.d(TAG, "蓝牙扫描超时，停止扫描")
                        stopDiscovery()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "扫描超时处理异常", e)
                }
            }

            // 立即返回当前设备列表（主要是配对设备）
            return getDiscoveredDevices()
        } catch (e: Exception) {
            Log.e(TAG, "扫描蓝牙设备出错", e)
            isScanning = false
            return emptyList()
        }
    }
    
    /**
     * 手动停止设备发现 (公共方法)
     */
    fun stopDiscovery() {
        try {
            if (isScanning) {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    bluetoothAdapter.cancelDiscovery()
                }
                
                try {
                    context.unregisterReceiver(bluetoothReceiver)
                } catch (e: Exception) {
                    Log.w(TAG, "无法注销蓝牙广播接收器", e)
                }
                
                isScanning = false
                Log.d(TAG, "蓝牙设备扫描已手动停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止蓝牙设备扫描出错", e)
        }
    }
    
    /**
     * 更新扫描结果
     */
    private fun updateScanResults() {
        try {
            val devices = getDiscoveredDevices()
            // 使用tryEmit代替emit，避免挂起函数的问题
            scanResultFlow.tryEmit(devices)
            Log.d(TAG, "更新蓝牙设备列表：${devices.size}个设备")
        } catch (e: Exception) {
            Log.e(TAG, "更新扫描结果失败", e)
        }
    }
    
    /**
     * 获取已发现的设备列表
     */
    private fun getDiscoveredDevices(): List<PrinterDevice> {
        return discoveredDevices.values.map { device ->
            val deviceName = device.name ?: "未知设备 (${device.address.takeLast(5)})"
            val status = printerStatusMap[device.address] ?: PrinterStatus.DISCONNECTED
            
            // 检查设备的配对状态并记录日志
            val bondState = when(device.bondState) {
                BluetoothDevice.BOND_BONDED -> "已配对"
                BluetoothDevice.BOND_BONDING -> "配对中"
                BluetoothDevice.BOND_NONE -> "未配对"
                else -> "未知状态(${device.bondState})"
            }
            Log.d(TAG, "设备: $deviceName, 地址: ${device.address}, 绑定状态: $bondState")
            
            PrinterDevice(
                name = deviceName,
                address = device.address,
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                status = status
            )
        }.sortedWith(compareBy(
            // 首先按已配对状态排序（已配对的设备优先）
            { device -> 
                val btDevice = discoveredDevices[device.address]
                if (btDevice?.bondState == BluetoothDevice.BOND_BONDED) -1 else 1
            },
            // 然后按名称排序
            { it.name }
        ))
    }
    
    override suspend fun disconnect(config: PrinterConfig) {
        withContext(Dispatchers.IO) {
            try {
                currentPrinter?.disconnectPrinter()
                currentConnection?.disconnect()
                currentPrinter = null
                currentConnection = null
                
                updatePrinterStatus(config.address, PrinterStatus.DISCONNECTED)
                Log.d(TAG, "已断开打印机连接: ${config.getDisplayName()}")
                
                // 更新打印机连接状态
                settingRepository.setPrinterConnection(false)
            } catch (e: Exception) {
                Log.e(TAG, "断开打印机连接失败: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getPrinterStatus(config: PrinterConfig): PrinterStatus {
        return printerStatusMap[config.address] ?: PrinterStatus.DISCONNECTED
    }
    
    override fun getPrinterStatusFlow(config: PrinterConfig): Flow<PrinterStatus> {
        return printerStatusFlows.getOrPut(config.address) { 
            MutableStateFlow(printerStatusMap[config.address] ?: PrinterStatus.DISCONNECTED)
        }.asStateFlow()
    }
    
    override suspend fun printOrder(order: Order, config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "准备打印订单: ${order.number}")
            
            // 检查打印机状态
            val status = getPrinterStatus(config)
            if (status != PrinterStatus.CONNECTED) {
                Log.d(TAG, "打印机未连接，尝试连接...")
                val connected = connect(config)
                if (!connected) {
                    Log.e(TAG, "打印机连接失败，无法打印订单")
                    return@withContext false
                }
            }
            
            // 打印订单
            val content = templateManager.generateOrderPrintContent(order, config)
            val success = printContent(content, config)
            
            // 如果打印成功，标记订单为已打印
            if (success) {
                orderRepository.markOrderAsPrinted(order.id)
                Log.d(TAG, "成功打印订单: ${order.number}")
                return@withContext true
            } else {
                Log.e(TAG, "打印订单失败: ${order.number}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "打印订单异常: ${e.message}", e)
            return@withContext false
        }
    }
    
    override suspend fun printTest(config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "执行测试打印")
            
            // 检查打印机状态
            val status = getPrinterStatus(config)
            if (status != PrinterStatus.CONNECTED) {
                Log.d(TAG, "打印机未连接，尝试连接...")
                val connected = connect(config)
                if (!connected) {
                    Log.e(TAG, "打印机连接失败，无法执行测试打印")
                    return@withContext false
                }
            }
            
            // 生成测试打印内容
            val testContent = templateManager.generateTestPrintContent(config)
            val success = printContent(testContent, config)
            
            if (success) {
                Log.d(TAG, "测试打印成功")
                return@withContext true
            } else {
                Log.e(TAG, "测试打印失败")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "测试打印异常: ${e.message}", e)
            return@withContext false
        }
    }
    
    override suspend fun autoPrintNewOrder(order: Order): Boolean {
        // 临时禁用自动打印功能
        Log.d(TAG, "自动打印功能已临时禁用，跳过打印订单: ${order.number}")
        return false
        
        /* 原代码暂时注释掉
        try {
            // 获取默认打印机
            val printerConfig = settingRepository.getDefaultPrinterConfig()
            if (printerConfig == null) {
                Log.e(TAG, "未设置默认打印机，无法自动打印")
                return false
            }
            
            // 检查是否启用自动打印
            if (!printerConfig.isAutoPrint) {
                Log.d(TAG, "未启用自动打印，跳过")
                return false
            }
            
            // 检查订单是否已经打印过
            if (order.isPrinted) {
                Log.d(TAG, "订单已打印，跳过: ${order.number}")
                return false
            }
            
            // 添加到打印队列
            val job = PrintJob(
                orderId = order.id,
                printerConfig = printerConfig,
                copies = printerConfig.printCopies,
                order = order
            )
            
            addToPrintQueue(job)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "自动打印订单失败: ${e.message}", e)
            return false
        }
        */
    }
    
    // 私有辅助方法
    
    private fun updatePrinterStatus(printerId: String, status: PrinterStatus) {
        printerStatusMap[printerId] = status
        printerStatusFlows[printerId]?.update { status }
    }
    
    private fun hasBluetoothPermission(): Boolean {
        Log.d(TAG, "检查蓝牙权限，安卓版本: ${Build.VERSION.SDK_INT}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本需要 BLUETOOTH_CONNECT 和 BLUETOOTH_SCAN 权限
            val hasConnectPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasScanPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasConnectPermission) {
                Log.e(TAG, "缺少 BLUETOOTH_CONNECT 权限，蓝牙功能将受限")
            }
            
            if (!hasScanPermission) {
                Log.e(TAG, "缺少 BLUETOOTH_SCAN 权限，蓝牙扫描功能将受限")
            }
            
            hasConnectPermission && hasScanPermission
        } else {
            // Android 11 及以下版本需要 BLUETOOTH 和 BLUETOOTH_ADMIN 权限
            val hasBluetoothPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasBluetoothAdminPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
            
            // Android 6.0 - 11.0 还需要位置权限才能扫描蓝牙设备
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val granted = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!granted) {
                    Log.e(TAG, "缺少位置权限，蓝牙扫描功能将受限")
                }
            }
            
            // 低版本Android上，即使没有权限也尝试继续执行，因为许多操作可能默认允许
            if (!hasBluetoothPermission) {
                Log.w(TAG, "缺少 BLUETOOTH 权限，但在低版本Android上尝试继续")
            }
            
            if (!hasBluetoothAdminPermission) {
                Log.w(TAG, "缺少 BLUETOOTH_ADMIN 权限，但在低版本Android上尝试继续")
            }
            
            // 在低版本Android上返回true继续执行
            true
        }
    }
    
    private fun findBluetoothDevice(address: String): BluetoothDevice? {
        return try {
            // 只在Android 12及以上版本检查权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "缺少蓝牙连接权限")
                return null
            }
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "获取蓝牙设备失败: ${e.message}", e)
            null
        }
    }
    
    private fun createBluetoothConnection(device: BluetoothDevice): BluetoothConnection? {
        return try {
            BluetoothConnection(device)
        } catch (e: Exception) {
            Log.e(TAG, "创建蓝牙连接失败: ${e.message}", e)
            null
        }
    }
    
    private fun createPrinter(connection: BluetoothConnection, config: PrinterConfig): EscPosPrinter? {
        return try {
            // 设置纸宽
            val paperWidthMM = config.paperWidth
            val charsPerLine = when (paperWidthMM) {
                PrinterConfig.PAPER_WIDTH_57MM -> 32
                PrinterConfig.PAPER_WIDTH_80MM -> 42
                else -> 32 // 默认
            }
            
            // 创建打印机实例
            EscPosPrinter(connection, 203, paperWidthMM.toFloat(), charsPerLine)
        } catch (e: Exception) {
            Log.e(TAG, "创建打印机对象失败: ${e.message}", e)
            null
        }
    }
    
    private suspend fun printContent(content: String, config: PrinterConfig): Boolean {
        return try {
            // 检查内容是否为空或空字符串
            if (content.isNullOrBlank()) {
                Log.e(TAG, "打印内容为空，返回测试文本")
                return printContent("[L]测试打印内容", config)
            }
            
            // 验证并修复内容格式
            val fixedContent = validateAndFixPrintContent(content)
            
            // 输出内容到日志中帮助调试
            Log.d(TAG, "准备打印内容:\n$fixedContent")
            
            // 添加额外检查，避免发送无效内容到打印机
            if (fixedContent.isBlank()) {
                Log.e(TAG, "修复后的内容仍然为空，无法打印")
                return false
            }
            
            try {
                currentPrinter?.printFormattedText(fixedContent)
                Log.d(TAG, "打印成功完成")
                true
            } catch (e: Exception) {
                // 捕获所有异常，包括解析异常
                Log.e(TAG, "打印机库异常: ${e.message}", e)
                
                // 如果是解析错误，尝试使用更简单的内容再试一次
                if (e is StringIndexOutOfBoundsException) {
                    Log.d(TAG, "检测到解析错误，尝试使用简化内容")
                    
                    // 使用更简单的内容格式重试
                    val simpleContent = createSimpleContent()
                    currentPrinter?.printFormattedText(simpleContent)
                    Log.d(TAG, "使用简化内容打印成功")
                    true
                } else {
                    false
                }
            }
        } catch (e: EscPosConnectionException) {
            Log.e(TAG, "打印机连接异常: ${e.message}", e)
            updatePrinterStatus(config.address, PrinterStatus.DISCONNECTED)
            false
        } catch (e: EscPosParserException) {
            Log.e(TAG, "打印内容解析异常: ${e.message}", e)
            false
        } catch (e: EscPosEncodingException) {
            Log.e(TAG, "打印编码异常: ${e.message}", e)
            false
        } catch (e: EscPosBarcodeException) {
            Log.e(TAG, "打印条码异常: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "打印异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 创建简单的打印内容，适用于出现解析问题时使用
     */
    private fun createSimpleContent(): String {
        return """
            [L]测试打印
            [L]----------------
            [L]热敏打印机测试
            [L]打印正常
            [L]----------------
            
            
            
        """.trimIndent()
    }
    
    /**
     * 验证并修复打印内容格式
     * 增强对标签和内容的处理
     */
    private fun validateAndFixPrintContent(content: String): String {
        if (content.isBlank()) {
            Log.w(TAG, "内容为空，返回默认测试内容")
            return "[L]测试打印内容"
        }
        
        try {
            // 分行处理
            val lines = content.split("\n")
            val fixedLines = mutableListOf<String>()
            
            for (line in lines) {
                val trimmedLine = line.trim()
                
                // 空行转换为一个空格的左对齐行，确保不会有空标签
                if (trimmedLine.isEmpty()) {
                    fixedLines.add("[L] ")
                    continue
                }
                
                // 检查行是否有对齐标记
                val hasAlignmentTag = trimmedLine.startsWith("[L]") || 
                                      trimmedLine.startsWith("[C]") || 
                                      trimmedLine.startsWith("[R]")
                
                if (!hasAlignmentTag) {
                    // 没有对齐标记，添加默认左对齐
                    fixedLines.add("[L]$trimmedLine")
                    continue
                }
                
                // 提取对齐标记和内容
                val alignmentTag = trimmedLine.substring(0, 3)
                
                // 确保内容部分非空
                if (trimmedLine.length <= 3) {
                    // 只有标签没有内容，添加一个空格
                    fixedLines.add("${alignmentTag} ")
                    continue
                }
                
                // 检查HTML标签是否正确闭合
                val contentPart = trimmedLine.substring(3)
                val fixedContent = fixHtmlTags(contentPart)
                
                // 添加修复后的行
                fixedLines.add("${alignmentTag}${fixedContent}")
            }
            
            val result = fixedLines.joinToString("\n")
            
            // 最终检查：确保结果至少有一个有效的打印行
            if (result.isBlank() || !result.contains("[")) {
                Log.w(TAG, "修复后内容无效，使用默认内容")
                return "[L]测试打印内容"
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "验证打印内容时出错: ${e.message}", e)
            return "[L]测试打印内容"
        }
    }
    
    /**
     * 修复HTML标签，确保标签正确闭合
     */
    private fun fixHtmlTags(content: String): String {
        if (content.isBlank()) return " "
        
        var result = content
        
        // 检查并修复未闭合的标签
        val openTags = listOf("<b>", "<i>", "<u>")
        val closeTags = listOf("</b>", "</i>", "</u>")
        
        for (i in openTags.indices) {
            val openTag = openTags[i]
            val closeTag = closeTags[i]
            
            // 计算开标签和闭标签的数量
            val openCount = result.split(openTag).size - 1
            val closeCount = result.split(closeTag).size - 1
            
            // 如果开标签多于闭标签，添加缺失的闭标签
            if (openCount > closeCount) {
                result += closeTag.repeat(openCount - closeCount)
            }
            // 如果闭标签多于开标签，添加缺失的开标签
            else if (closeCount > openCount) {
                result = openTag.repeat(closeCount - openCount) + result
            }
        }
        
        // 确保内容不是空的
        if (result.isBlank()) {
            result = " "
        }
        
        return result
    }
    
    private fun addToPrintQueue(job: PrintJob) {
        synchronized(printQueue) {
            printQueue.add(job)
            Log.d(TAG, "添加打印任务到队列，当前队列长度: ${printQueue.size}")
        }
    }
    
    private suspend fun processPrintQueue() {
        if (isProcessingQueue) return
        
        isProcessingQueue = true
        try {
            while (true) {
                val job = synchronized(printQueue) {
                    if (printQueue.isEmpty()) null else printQueue.removeAt(0)
                } ?: break
                
                Log.d(TAG, "处理打印队列任务: 订单ID=${job.orderId}")
                
                // 获取订单对象
                val order = job.order ?: orderRepository.getOrderById(job.orderId)
                if (order == null) {
                    Log.e(TAG, "未找到订单: ${job.orderId}")
                    continue
                }
                
                // 打印订单，重试3次
                var success = false
                for (i in 1..3) {
                    success = printOrder(order, job.printerConfig)
                    if (success) break
                    delay(1000)
                    Log.d(TAG, "重试打印订单: ${job.orderId}, 尝试次数: $i")
                }
                
                if (!success) {
                    Log.e(TAG, "打印订单失败，任务将重新添加到队列: ${job.orderId}")
                    synchronized(printQueue) {
                        // 重新添加任务，但最多重试3次
                        if (job.retryCount < 3) {
                            printQueue.add(job.copy(retryCount = job.retryCount + 1))
                        }
                    }
                }
                
                // 打印多份
                for (i in 1 until job.copies) {
                    if (printOrder(order, job.printerConfig)) {
                        Log.d(TAG, "打印订单副本 ${i+1}/${job.copies}: ${job.orderId}")
                    }
                }
                
                delay(500) // 添加短暂延迟避免过于频繁的打印请求
            }
        } finally {
            isProcessingQueue = false
        }
    }
    
    /**
     * 获取设备状态
     */
    private fun getDeviceStatus(deviceAddress: String): PrinterStatus {
        // 检查设备是否已连接
        val isConnected = currentConnection?.device?.address == deviceAddress
        return if (isConnected) PrinterStatus.CONNECTED else PrinterStatus.DISCONNECTED
    }
    
    // 打印任务数据类
    private data class PrintJob(
        val orderId: Long,
        val printerConfig: PrinterConfig,
        val copies: Int = 1,
        val retryCount: Int = 0,
        val order: Order? = null
    )
    
    private fun getBluetoothDevice(address: String): BluetoothDevice? {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            return bluetoothAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "获取蓝牙设备失败: ${e.message}")
            return null
        }
    }

    /**
     * 尝试连接指定地址的蓝牙设备
     * @param deviceAddress 蓝牙设备地址
     * @return 连接结果
     */
    suspend fun tryConnectWithDevice(deviceAddress: String): Boolean {
        Log.d(TAG, "尝试连接设备: $deviceAddress")
        try {
            // 停止搜索，以提高连接成功率
            stopDiscovery()
            
            // 获取蓝牙适配器
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
                Log.e(TAG, "蓝牙适配器为空")
                return false
            }
            
            // 权限检查 - 只在高版本Android上强制要求
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "没有BLUETOOTH_CONNECT权限")
                    return false
                }
            }
            
            // 获取蓝牙设备
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress) ?: run {
                Log.e(TAG, "无法获取设备: $deviceAddress")
                return false
            }
            
            // 创建一个基本的打印机配置
            val config = PrinterConfig(
                name = device.name ?: "未知设备",
                address = deviceAddress,
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                paperWidth = PrinterConfig.PAPER_WIDTH_57MM
            )
            
            // 调用现有的connect方法连接设备
            return connect(config)
        } catch (e: Exception) {
            Log.e(TAG, "连接设备时发生异常: ${e.message}", e)
            return false
        }
    }

    /**
     * 输出蓝牙诊断信息，帮助识别权限问题
     */
    fun logBluetoothDiagnostics() {
        Log.d(TAG, "==== 蓝牙诊断信息 ====")
        Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        
        // 检查蓝牙适配器
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        Log.d(TAG, "蓝牙适配器: ${bluetoothAdapter != null}")
        Log.d(TAG, "蓝牙已启用: ${bluetoothAdapter?.isEnabled == true}")
        
        // 检查权限状态
        val permissions = mutableListOf<Pair<String, Boolean>>()
        
        // 常规蓝牙权限
        permissions.add(Pair("BLUETOOTH", 
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED))
        permissions.add(Pair("BLUETOOTH_ADMIN", 
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED))
        
        // 位置权限(对于API 30及以下的扫描)
        permissions.add(Pair("ACCESS_FINE_LOCATION", 
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED))
        
        // Android 12的新权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Pair("BLUETOOTH_SCAN", 
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED))
            permissions.add(Pair("BLUETOOTH_CONNECT", 
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED))
        }
        
        // 输出所有权限状态
        Log.d(TAG, "权限状态:")
        for ((name, granted) in permissions) {
            Log.d(TAG, "- $name: ${if (granted) "已授予" else "未授予"}")
        }
        
        // 已配对设备信息
        if (bluetoothAdapter != null) {
            try {
                val devices = bluetoothAdapter.bondedDevices
                Log.d(TAG, "已配对设备数量: ${devices?.size ?: 0}")
                devices?.forEach { device ->
                    Log.d(TAG, "- ${device.name ?: "未命名设备"} (${device.address})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取已配对设备失败: ${e.message}")
            }
        }
        
        // 建议
        Log.d(TAG, BLUETOOTH_PERMISSION_ISSUE)
        Log.d(TAG, "==== 诊断结束 ====")
    }
} 