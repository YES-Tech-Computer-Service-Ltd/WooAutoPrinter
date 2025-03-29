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
import com.example.wooauto.domain.printer.PrinterBrand
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: DomainSettingRepository,
    private val orderRepository: DomainOrderRepository,
    private val templateManager: OrderPrintTemplate
) : PrinterManager {

    // 创建协程作用域
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 使用推荐的方式获取BluetoothAdapter
    private val bluetoothAdapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    } else {
        // 兼容API 23以下的设备，尽管API已废弃但仍然可用
        @Suppress("DEPRECATION")
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
    
    // 添加打印机状态流
    private val _printerStatus = MutableStateFlow<PrinterStatus>(PrinterStatus.DISCONNECTED)
    
    // 添加心跳相关变量
    private var heartbeatJob: Job? = null
    private var currentPrinterConfig: PrinterConfig? = null
    private var heartbeatEnabled = true
    
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
        // 心跳间隔 (毫秒) - 每15秒发送一次心跳，避免蓝牙断连
        private const val HEARTBEAT_INTERVAL = 15000L
        // 连接检查间隔 (毫秒) - 每30秒检查一次连接状态
        private const val CONNECTION_CHECK_INTERVAL = 30000L
        // 连接重试间隔 (毫秒) - 断开连接后等待5秒再尝试重连
        private const val RECONNECT_DELAY = 5000L
        
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
                    // 获取找到的蓝牙设备 - 使用兼容方式获取BluetoothDevice
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
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
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
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
    
    override suspend fun connect(config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "尝试连接打印机: ${config.name} (${config.address})")
            
            // 如果已经连接了相同的打印机，则直接返回成功
            if (currentConnection?.device?.address == config.address) {
                Log.d(TAG, "打印机已经连接")
                updatePrinterStatus(config, PrinterStatus.CONNECTED)
                return@withContext true
            }
            
            // 如果已连接其他打印机，先断开连接
            if (currentConnection != null) {
                disconnect(currentPrinterConfig ?: config)
            }
            
            // 获取蓝牙设备对象
            val device = getBluetoothDevice(config.address)
            if (device == null) {
                Log.e(TAG, "未找到设备: ${config.address}")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return@withContext false
            }
            
            // 识别打印机品牌
            val deviceName = device.name ?: config.name
            val detectedBrand = PrinterBrand.identifyBrand(deviceName)
            
            // 创建打印机配置副本，包含识别的品牌
            val updatedConfig = if (detectedBrand != PrinterBrand.UNKNOWN && detectedBrand != config.brand) {
                val newConfig = config.copy(brand = detectedBrand)
                // 保存更新后的配置
                settingRepository.savePrinterConfig(newConfig)
                newConfig
            } else {
                config
            }
            
            // 设置当前打印机配置
            currentPrinterConfig = updatedConfig
            
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
                    updatePrinterStatus(config, PrinterStatus.ERROR)
                    return@withContext false
                }
            }
            
            // 检查是否为Star打印机的TSP100系列
            val isStarTSP100 = updatedConfig.brand == PrinterBrand.STAR && 
                              (deviceName.contains("TSP100", ignoreCase = true) || 
                               deviceName.contains("TSP-100", ignoreCase = true))
            
            if (isStarTSP100) {
                Log.d(TAG, "【Star打印机】检测到TSP100系列打印机，使用特殊连接流程")
                return@withContext connectStarTSP100Printer(device, updatedConfig)
            }
            
            // 对于其他打印机使用标准连接流程
            // 循环尝试所有UUID
            var connected = false
            var lastException: Exception? = null
            
            for (uuid in PRINTER_UUIDS) {
                // 记录当前尝试的UUID类型
                val uuidType = if (uuid.toString().contains("00001101-0000-1000-8000-00805")) "SPP协议" else "其他协议"
                Log.d(TAG, "【蓝牙协议】尝试使用 $uuidType UUID: $uuid")
                
                // 使用递增的重试次数
                for (attempt in 1..MAX_RETRY_COUNT) {
                    try {
                        Log.d(TAG, "尝试连接 UUID $uuid (尝试 $attempt/${MAX_RETRY_COUNT})")
                        
                        // 创建蓝牙连接 - 注意库不支持传入UUID，这里只用于日志
                        val connection = BluetoothConnection(device)
                        connection.connect()
                        
                        Log.d(TAG, "成功连接蓝牙设备 ${config.name}")
                        Log.d(TAG, "【蓝牙协议】成功使用 $uuidType 连接")
                        currentConnection = connection
                        
                        // 创建打印机实例 - 为Star打印机适配参数
                        val dpi = 203 // 标准DPI
                        val paperWidthMm = 72f // 根据配置截图设置为72mm
                        val nbCharPerLine = 42 // 根据配置截图设置为42字符每行
                        Log.d(TAG, "【打印机初始化】使用参数: DPI=$dpi, 宽度=${paperWidthMm}mm, 字符数=$nbCharPerLine")
                        
                        // 根据打印机品牌初始化
                        if (updatedConfig.brand == PrinterBrand.STAR) {
                            initializeStarPrinter(connection)
                        }
                        
                        val printer = EscPosPrinter(connection, dpi, paperWidthMm, nbCharPerLine)
                        currentPrinter = printer
                        
                        // 等待一段时间确保打印机初始化完成
                        delay(500)
                        
                        connected = true
                        updatePrinterStatus(config, PrinterStatus.CONNECTED)
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
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return@withContext false
            }
            
            // 启动心跳机制以保持连接
            startHeartbeat(config)
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "连接蓝牙打印机异常: ${e.message}")
            updatePrinterStatus(config, PrinterStatus.ERROR)
            return@withContext false
        }
    }
    
    /**
     * 专门用于连接Star TSP100系列打印机的方法
     */
    private suspend fun connectStarTSP100Printer(device: BluetoothDevice, config: PrinterConfig): Boolean {
        try {
            Log.d(TAG, "【Star TSP100】开始专用连接流程")
            updatePrinterStatus(config, PrinterStatus.CONNECTING)
            
            // TSP100系列打印机优先尝试标准SPP UUID
            val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            
            // 使用更高的重试次数和更长的延迟
            var connected = false
            var lastException: Exception? = null
            
            for (attempt in 1..8) { // 增加到8次尝试
                try {
                    Log.d(TAG, "【Star TSP100】尝试连接 (尝试 $attempt/8)")
                    
                    // 在每次连接前先等待一段时间
                    val initialDelay = 500L * attempt
                    delay(initialDelay)
                    
                    // 为TSP100创建特殊的连接对象
                    val connection = BluetoothConnection(device)
                    
                    // 添加额外的超时参数
                    try {
                        // 反射设置更长的连接超时
                        val socketField = connection.javaClass.getDeclaredField("mBluetoothSocket")
                        socketField.isAccessible = true
                        val socket = socketField.get(connection)
                        if (socket != null) {
                            val timeoutMethod = socket.javaClass.getMethod("connect", Int::class.java)
                            timeoutMethod.isAccessible = true
                            // 使用更长的超时时间 (15秒)
                            timeoutMethod.invoke(socket, 15000)
                        }
                    } catch (e: Exception) {
                        // 反射失败时使用标准连接方法
                        Log.w(TAG, "【Star TSP100】无法使用自定义超时，使用标准连接")
                        connection.connect()
                    }
                    
                    Log.d(TAG, "【Star TSP100】连接成功")
                    currentConnection = connection
                    
                    // TSP100特殊初始化参数
                    val dpi = 203
                    val paperWidthMm = 80f  // TSP100通常是80mm纸宽
                    val nbCharPerLine = 48  // 80mm纸宽对应的字符数
                    
                    // 进行Star TSP100专用初始化
                    initializeTSP100Printer(connection)
                    
                    // 等待更长时间确保初始化完成
                    delay(1000)
                    
                    // 创建打印机实例
                    val printer = EscPosPrinter(connection, dpi, paperWidthMm, nbCharPerLine)
                    currentPrinter = printer
                    
                    // 连接成功
                    connected = true
                    updatePrinterStatus(config, PrinterStatus.CONNECTED)
                    break
                } catch (e: Exception) {
                    lastException = e
                    Log.e(TAG, "【Star TSP100】连接失败 (尝试: $attempt): ${e.message}")
                    
                    // 断开任何可能的连接
                    try {
                        currentConnection?.disconnect()
                    } catch (e2: Exception) {
                        // 忽略断开连接的错误
                    }
                    
                    currentConnection = null
                    currentPrinter = null
                    
                    // 指数退避延迟
                    if (attempt < 8) {
                        val delayTime = 1000L * (1 shl (attempt - 1).coerceAtMost(5))
                        Log.d(TAG, "【Star TSP100】延迟 ${delayTime}ms 后重试")
                        delay(delayTime)
                    }
                }
            }
            
            if (!connected) {
                Log.e(TAG, "【Star TSP100】所有连接尝试均失败: ${lastException?.message}")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return false
            }
            
            // 启动特殊心跳机制
            startTSP100Heartbeat(config)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【Star TSP100】连接异常: ${e.message}")
            updatePrinterStatus(config, PrinterStatus.ERROR)
            return false
        }
    }
    
    /**
     * Star TSP100打印机特殊初始化
     */
    private fun initializeTSP100Printer(connection: BluetoothConnection) {
        try {
            Log.d(TAG, "【Star TSP100】开始专用初始化...")
            
            // 多次尝试，确保命令发送成功
            for (attempt in 1..3) {
                try {
                    // 1. 先清除打印缓冲区
                    val clearCommand = byteArrayOf(0x18)  // CAN - 清除缓冲区
                    connection.write(clearCommand)
                    Thread.sleep(300)
                    
                    // 2. 初始化打印机 (ESC @)
                    val initCommand = byteArrayOf(0x1B, 0x40)
                    connection.write(initCommand)
                    Thread.sleep(300)
                    
                    // 3. TSP100特殊字符集设置
                    val charSetCommand = byteArrayOf(0x1B, 0x74, 0x00)  // 美式字符集
                    connection.write(charSetCommand)
                    Thread.sleep(200)
                    
                    // 4. TSP100行间距设置
                    val lineSpacingCommand = byteArrayOf(0x1B, 0x33, 0x24)  // 36点行间距
                    connection.write(lineSpacingCommand)
                    Thread.sleep(200)
                    
                    // 5. TSP100设置打印模式
                    val printModeCommand = byteArrayOf(0x1B, 0x21, 0x00)  // 标准模式
                    connection.write(printModeCommand)
                    Thread.sleep(200)
                    
                    // 6. 对齐方式设置为左对齐
                    val alignCommand = byteArrayOf(0x1B, 0x61, 0x00)  // 左对齐
                    connection.write(alignCommand)
                    Thread.sleep(200)
                    
                    // 7. 设置默认行高
                    val defaultLineHeightCommand = byteArrayOf(0x1B, 0x32)
                    connection.write(defaultLineHeightCommand)
                    Thread.sleep(200)
                    
                    // 8. 发送一些空白字符确保初始化成功
                    val emptyCommand = byteArrayOf(0x00, 0x00, 0x00)
                    connection.write(emptyCommand)
                    Thread.sleep(300)
                    
                    Log.d(TAG, "【Star TSP100】初始化尝试 $attempt 成功")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "【Star TSP100】初始化尝试 $attempt 失败: ${e.message}")
                    if (attempt == 3) {
                        throw e  // 最后一次尝试，将异常抛出
                    }
                    Thread.sleep(500)  // 等待后重试
                }
            }
            
            Log.d(TAG, "【Star TSP100】初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "【Star TSP100】初始化失败: ${e.message}", e)
        }
    }
    
    /**
     * Star TSP100特殊心跳机制
     */
    private fun startTSP100Heartbeat(config: PrinterConfig) {
        // 停止现有心跳
        stopHeartbeat()
        
        // 保存当前打印机配置
        currentPrinterConfig = config
        
        // 启动TSP100特殊心跳
        heartbeatJob = managerScope.launch {
            try {
                Log.d(TAG, "【Star TSP100】启动特殊心跳机制，间隔: 5秒")
                
                while (isActive && heartbeatEnabled) {
                    try {
                        delay(5000)  // 更频繁的心跳检查
                        
                        // 检查打印机连接状态
                        val status = getPrinterStatus(config)
                        
                        if (status == PrinterStatus.CONNECTED && currentConnection != null) {
                            // 发送TSP100特殊心跳命令
                            sendTSP100HeartbeatCommand()
                        } else {
                            // 尝试重新连接
                            Log.d(TAG, "【Star TSP100】检测到连接断开，尝试重连")
                            reconnectTSP100Printer(config)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "【Star TSP100】心跳异常: ${e.message}")
                        
                        // 连接可能已断开，尝试重连
                        try {
                            delay(2000)  // 短暂延迟后重连
                            reconnectTSP100Printer(config)
                        } catch (re: Exception) {
                            Log.e(TAG, "【Star TSP100】重连失败: ${re.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "【Star TSP100】心跳任务异常: ${e.message}")
            }
        }
    }
    
    /**
     * 发送TSP100特殊心跳命令
     */
    private fun sendTSP100HeartbeatCommand() {
        try {
            currentConnection?.let { connection ->
                // 使用空命令作为心跳
                val heartbeatCommand = byteArrayOf(0x00)
                connection.write(heartbeatCommand)
            }
        } catch (e: Exception) {
            Log.e(TAG, "【Star TSP100】发送心跳命令失败: ${e.message}")
            throw e  // 重新抛出异常以触发重连
        }
    }
    
    /**
     * 重连TSP100打印机
     */
    private suspend fun reconnectTSP100Printer(config: PrinterConfig): Boolean {
        try {
            Log.d(TAG, "【Star TSP100】尝试重新连接")
            
            // 断开现有连接
            try {
                currentPrinter?.disconnectPrinter()
                currentConnection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "【Star TSP100】断开现有连接失败: ${e.message}")
            }
            
            currentPrinter = null
            currentConnection = null
            
            // 获取设备对象
            val device = getBluetoothDevice(config.address) ?: return false
            
            // 使用专用连接方法
            return connectStarTSP100Printer(device, config)
        } catch (e: Exception) {
            Log.e(TAG, "【Star TSP100】重连失败: ${e.message}")
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
                // 使用类实例中的bluetoothAdapter
                if (this.bluetoothAdapter != null && this.bluetoothAdapter.isEnabled) {
                    this.bluetoothAdapter.cancelDiscovery()
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
                // 停止心跳机制
                stopHeartbeat()
                
                currentPrinter?.disconnectPrinter()
                currentConnection?.disconnect()
                currentPrinter = null
                currentConnection = null
                
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
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
        // 添加打印前的详细状态日志
        val printerStatus = getPrinterStatus(config)
        Log.d(TAG, "【打印机状态】开始打印订单 #${order.number}，当前打印机 ${config.name} 状态: $printerStatus")
        
        // 检查蓝牙适配器状态
        val bluetoothEnabled = bluetoothAdapter?.isEnabled ?: false
        Log.d(TAG, "【打印机状态】蓝牙适配器状态: ${if (bluetoothEnabled) "已启用" else "未启用"}")
        
        // 检查打印机连接和实例
        val hasConnection = currentConnection != null
        val hasPrinter = currentPrinter != null
        Log.d(TAG, "【打印机状态】连接实例: ${if (hasConnection) "存在" else "不存在"}, 打印机实例: ${if (hasPrinter) "存在" else "不存在"}")
        
        var retryCount = 0
        while (retryCount < 3) {
            try {
                Log.d(TAG, "准备打印订单: ${order.number} (尝试 ${retryCount + 1}/3)")
                
                // 1. 检查并确保连接
                if (!ensurePrinterConnected(config)) {
                    Log.e(TAG, "打印机连接失败，尝试重试...")
                    retryCount++
                    delay(1000)
                    continue
                }
                
                // 2. 进行连接测试确认
                if (!testPrinterConnection(config)) {
                    continue
                }
                
                // 3. 生成打印内容
                val content = generateOrderContent(order, config)
                
                // 4. 发送打印内容
                val success = printContent(content, config)
                
                // 5. 处理打印结果
                if (success) {
                    // 成功打印后处理订单状态
                    return@withContext handleSuccessfulPrint(order)
                } else {
                    Log.e(TAG, "打印内容失败，尝试重试...")
                    retryCount++
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "打印订单异常: ${e.message}", e)
                
                // 对于连接断开的异常，尝试重新连接
                if (e.message?.contains("Broken pipe") == true || 
                    e is EscPosConnectionException) {
                    handleConnectionError(config)
                }
                
                retryCount++
                delay(1000)
            }
        }
        
        Log.e(TAG, "打印订单尝试3次后仍然失败: ${order.number}")
        return@withContext false
    }
    
    /**
     * 测试打印机连接
     * @param config 打印机配置
     * @return 连接测试是否成功
     */
    private suspend fun testPrinterConnection(config: PrinterConfig): Boolean {
        val testResult = testConnection(config)
        if (!testResult) {
            Log.w(TAG, "打印机连接测试失败，尝试重连后再打印")
            reconnectPrinter(config)
            return false
        }
        return true
    }
    
    /**
     * 生成订单打印内容
     * @param order 订单
     * @param config 打印机配置
     * @return 格式化后的打印内容
     */
    private fun generateOrderContent(order: Order, config: PrinterConfig): String {
        Log.d(TAG, "开始生成打印内容: 订单 ${order.number}")
        val content = templateManager.generateOrderPrintContent(order, config)
        Log.d(TAG, "生成打印内容完成，准备发送打印数据")
        return content
    }
    
    /**
     * 处理连接错误
     * @param config 打印机配置
     */
    private fun handleConnectionError(config: PrinterConfig) {
        try {
            Log.d(TAG, "检测到连接断开，尝试重新连接")
            managerScope.launch {
                reconnectPrinter(config)
            }
        } catch (re: Exception) {
            Log.e(TAG, "重新连接打印机失败: ${re.message}")
        }
    }
    
    /**
     * 处理打印成功后的逻辑
     * @param order 订单
     * @return 是否成功处理
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
                Log.e(TAG, "标记订单 ${order.id} 为已打印失败")
            }
        } else {
            Log.d(TAG, "订单 ${order.id} 已被标记为已打印，跳过重复标记")
        }
        
        return true
    }
    
    override suspend fun autoPrintNewOrder(order: Order): Boolean {
        Log.d(TAG, "========== 开始自动打印订单 #${order.number} ==========")
        try {
            // 1. 检查订单状态
            if (!validateOrderForPrinting(order)) {
                return false
            }
            
            // 2. 获取最新订单信息
            val latestOrder = orderRepository.getOrderById(order.id) ?: run {
                Log.e(TAG, "无法获取订单最新信息: #${order.number}")
                return false
            }
            
            // 3. 再次检查最新数据中订单是否已打印
            if (latestOrder.isPrinted) {
                Log.d(TAG, "订单 #${order.number} 的最新状态已是已打印，跳过自动打印")
                return false
            }
            
            // 4. 获取默认打印机配置
            val printerConfig = getDefaultPrinterConfig() ?: return false
            
            // 5. 检查自动打印设置
            if (!printerConfig.isAutoPrint) {
                Log.d(TAG, "打印机 ${printerConfig.name} 未启用自动打印功能")
                return false
            }
            
            // 6. 连接打印机并打印
            if (!ensurePrinterConnected(printerConfig)) {
                Log.e(TAG, "无法连接打印机 ${printerConfig.name}，自动打印失败")
                return false
            }
            
            // 7. 执行打印
            Log.d(TAG, "开始打印订单 #${order.number}")
            return printOrder(order, printerConfig)
            
        } catch (e: Exception) {
            Log.e(TAG, "自动打印订单 #${order.number} 时发生异常: ${e.message}", e)
            return false
        } finally {
            Log.d(TAG, "========== 自动打印订单 #${order.number} 处理完成 ==========")
        }
    }
    
    /**
     * 验证订单是否满足打印条件
     * @param order 订单
     * @return 是否满足打印条件
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
     * @return 默认打印机配置，如果没有则返回null
     */
    private suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        val printerConfig = settingRepository.getDefaultPrinterConfig()
        if (printerConfig == null) {
            Log.e(TAG, "未设置默认打印机，无法进行自动打印")
            return null
        }
        
        Log.d(TAG, "使用默认打印机: ${printerConfig.name} (${printerConfig.address})")
        return printerConfig
    }
    
    /**
     * 初始化Star打印机，发送特定的初始化命令
     * 适配Star打印机的ESC/POS模式
     */
    private fun initializeStarPrinter(connection: BluetoothConnection) {
        try {
            Log.d(TAG, "【Star打印机】开始发送初始化命令...")
            
            // 检查是否为TSP100系列
            val deviceName = currentConnection?.device?.name ?: ""
            if (deviceName.contains("TSP100")) {
                // 对于TSP100使用专用方法
                initializeTSP100Printer(connection)
                return
            }
            
            // 1. 初始化打印机 (ESC @)
            val initCommand = byteArrayOf(0x1B, 0x40)
            connection.write(initCommand)
            Log.d(TAG, "【Star打印机】发送初始化命令 ESC @")
            Thread.sleep(100)
            
            // 2. 设置字符编码 (ESC R n) - 使用美国字符集 (0)
            val encodingCommand = byteArrayOf(0x1B, 0x52, 0x00)
            connection.write(encodingCommand)
            Log.d(TAG, "【Star打印机】设置字符编码 ESC R 0")
            Thread.sleep(50)
            
            // 3. 设置行间距 (ESC 3 n) - 标准行间距
            val lineSpacingCommand = byteArrayOf(0x1B, 0x33, 0x30)
            connection.write(lineSpacingCommand)
            Log.d(TAG, "【Star打印机】设置行间距 ESC 3 30")
            Thread.sleep(50)
            
            // 4. 设置字符大小 (GS !) - 标准大小
            val fontSizeCommand = byteArrayOf(0x1D, 0x21, 0x00)
            connection.write(fontSizeCommand)
            Log.d(TAG, "【Star打印机】设置字符大小 GS ! 0")
            Thread.sleep(50)
            
            // 5. 选择打印模式 (ESC !) - 标准模式
            val printModeCommand = byteArrayOf(0x1B, 0x21, 0x00)
            connection.write(printModeCommand)
            Log.d(TAG, "【Star打印机】设置打印模式 ESC ! 0")
            Thread.sleep(50)
            
            // 6. 左边距设置为0 (GS L)
            val leftMarginCommand = byteArrayOf(0x1D, 0x4C, 0x00, 0x00)
            connection.write(leftMarginCommand)
            Log.d(TAG, "【Star打印机】设置左边距 GS L 0 0")
            Thread.sleep(50)
            
            // 7. 清除缓冲区数据 (CAN)
            val clearCommand = byteArrayOf(0x18)
            connection.write(clearCommand)
            Log.d(TAG, "【Star打印机】清除打印缓冲区 CAN")
            Thread.sleep(100)
            
            Log.d(TAG, "【Star打印机】初始化命令发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "【Star打印机】初始化失败: ${e.message}", e)
        }
    }

    
    /**
     * 确保打印机已连接
     * @param config 打印机配置
     * @return 连接是否成功
     */
    suspend fun ensurePrinterConnected(config: PrinterConfig): Boolean {
        // 检查打印机状态
        val status = getPrinterStatus(config)
        Log.d(TAG, "【打印机状态】确保打印机连接 - 当前状态: $status，打印机: ${config.name} (${config.address})")
        
        // 检查连接对象状态
        val connectionExists = currentConnection != null
        val printerExists = currentPrinter != null
        Log.d(TAG, "【打印机状态】连接对象检查 - connection对象: ${if(connectionExists) "存在" else "不存在"}, printer对象: ${if(printerExists) "存在" else "不存在"}")
        
        if (status != PrinterStatus.CONNECTED) {
            Log.d(TAG, "【打印机状态】打印机未连接，尝试连接...")
            val connectResult = connect(config)
            Log.d(TAG, "【打印机状态】连接尝试结果: ${if(connectResult) "成功" else "失败"}")
            return connectResult
        }
        
        // 即使状态是CONNECTED，也检查连接是否真的有效
        try {
            val isReallyConnected = currentConnection?.isConnected ?: false
            Log.d(TAG, "【打印机状态】验证连接是否真的有效: $isReallyConnected")
            
            if (!isReallyConnected) {
                Log.d(TAG, "【打印机状态】连接状态不一致，尝试重新连接")
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                return connect(config)
            }
        } catch (e: Exception) {
            Log.e(TAG, "【打印机状态】检查连接时出错: ${e.message}")
        }
        
        Log.d(TAG, "【打印机状态】打印机已连接")
        return true
    }
    
    /**
     * 打印内容到打印机
     * 这个方法负责将格式化后的内容发送到打印机
     * @param content 格式化后的打印内容
     * @param config 打印机配置
     * @return 打印是否成功
     */
    suspend fun printContent(content: String, config: PrinterConfig): Boolean {
        return try {
            // 添加打印机状态日志
            val printerStatus = getPrinterStatus(config)
            Log.d(TAG, "【打印机状态】准备打印内容，当前打印机 ${config.name} (${config.address}) 状态: $printerStatus")
            
            // 检查当前连接状态
            if (currentConnection == null) {
                Log.e(TAG, "【打印机状态】无有效连接，当前Connection为null")
                return false
            }
            
            // 检查打印机连接状态
            try {
                val isConnected = currentConnection?.isConnected ?: false
                Log.d(TAG, "【打印机状态】打印机连接状态检查: isConnected=$isConnected")
                if (!isConnected) {
                    Log.e(TAG, "【打印机状态】打印机连接已断开")
                    updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "【打印机状态】检查连接状态时出错: ${e.message}")
            }
            
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
            
            // 检查是否为TSP100III打印机
            val isStarTSP100III = config.brand == PrinterBrand.STAR && 
                                 (config.name.contains("TSP100", ignoreCase = true) || 
                                  config.name.contains("TSP-100", ignoreCase = true)) &&
                                config.name.contains("III", ignoreCase = true)
            
            try {
                // 打印详细检测信息帮助调试
                Log.d(TAG, "【打印机检测】品牌: ${config.brand}, 名称: ${config.name}")
                Log.d(TAG, "【打印机检测】是TSP100III: $isStarTSP100III")
                
                // 强制使用光栅模式处理TSP100打印机
                if (config.brand == PrinterBrand.STAR && config.name.contains("TSP100", ignoreCase = true)) {
                    Log.d(TAG, "【Star TSP100】检测到TSP100打印机，强制使用光栅模式打印")
                    
                    // 在内容后添加额外的走纸命令
                    val contentWithExtra = ensureProperEnding(fixedContent)
                    
                    // 发送内容前再次初始化打印机
                    currentConnection?.let { initializeTSP100Printer(it) }
                    
                    try {
                        // 使用文本字节数组作为输入
                        val textBytes = contentWithExtra.toByteArray(Charsets.UTF_8)
                        Log.d(TAG, "【Star TSP100】准备转换为光栅格式，文本大小: ${textBytes.size}字节")
                        
                        // 使用Star光栅转换器处理内容
                        val starRasterConverter = StarRasterConverter()
                        val rasterData = starRasterConverter.convertEscPosToStarRaster(textBytes)
                        
                        // 打印前添加延迟确保初始化完成
                        Thread.sleep(500)
                        
                        // 直接写入光栅数据
                        val rasterSize = rasterData.size
                        Log.d(TAG, "【Star TSP100】发送光栅数据，大小: ${rasterSize}字节")
                        
                        // 数据较大时分块发送，避免缓冲区溢出
                        if (rasterSize > 8192) {
                            Log.d(TAG, "【Star TSP100】数据较大，分块发送")
                            val blockSize = 4096
                            var sentBytes = 0
                            
                            while (sentBytes < rasterSize) {
                                val remaining = rasterSize - sentBytes
                                val currentBlockSize = Math.min(blockSize, remaining)
                                
                                // 发送当前数据块
                                currentConnection?.write(rasterData, sentBytes, currentBlockSize)
                                Log.d(TAG, "【Star TSP100】发送数据块: $sentBytes 到 ${sentBytes + currentBlockSize}")
                                
                                // 每个块之间添加短暂延迟，避免打印机缓冲区溢出
                                Thread.sleep(200)
                                sentBytes += currentBlockSize
                            }
                        } else {
                            // 小数据直接发送
                            currentConnection?.write(rasterData)
                        }
                        
                        // 短暂延迟后发送额外命令确保处理完成
                        Thread.sleep(1000)
                        
                        // 确保走纸和切纸命令发送完成
                        sendTSP100PaperFeedAndCutCommands()
                        
                        Log.d(TAG, "【Star TSP100】光栅模式打印完成")
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "【Star TSP100】光栅处理失败: ${e.message}", e)
                        // 失败时继续尝试普通模式
                    }
                }

                // 在内容后添加额外的走纸命令，确保Star打印机能够完成打印
                val contentWithExtra = ensureProperEnding(fixedContent)
                
                // 发送内容前再次初始化打印机
                currentConnection?.let { initializeStarPrinter(it) }
                
                // 打印格式化内容
                currentPrinter?.printFormattedText(contentWithExtra)
                
                // 发送额外的走纸和切纸命令
                sendExtraPaperFeedCommands()
                
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
                    
                    // 发送内容前再次初始化打印机
                    currentConnection?.let { initializeStarPrinter(it) }
                    
                    currentPrinter?.printFormattedText(simpleContent)
                    
                    // 发送额外的走纸和切纸命令
                    sendExtraPaperFeedCommands()
                    
                    Log.d(TAG, "使用简化内容打印成功")
                    true
                } else {
                    false
                }
            }
        } catch (e: EscPosConnectionException) {
            Log.e(TAG, "打印机连接异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
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
     * 确保打印内容有正确的结束（多个换行）
     */
    private fun ensureProperEnding(content: String): String {
        // 确保内容以多个换行结束，促使走纸
        var result = content.trimEnd()
        
        // 添加至少6个换行，确保内容完全打印并走纸
        result += "\n[L] \n[L] \n[L] \n[L] \n[L] \n[L] "
        
        return result
    }
    
    /**
     * 发送额外的走纸和切纸命令
     * 这些是直接发送到打印机的原始命令
     */
    private fun sendExtraPaperFeedCommands() {
        try {
            if (currentConnection == null) return
            
            // 检查是否为TSP100打印机
            val isStarTSP100 = currentPrinterConfig?.let { config ->
                config.brand == PrinterBrand.STAR && 
                currentConnection?.device?.name?.contains("TSP100", ignoreCase = true) == true
            } ?: false
            
            if (isStarTSP100) {
                sendTSP100PaperFeedAndCutCommands()
                return
            }
            
            Log.d(TAG, "【打印机】发送额外走纸和切纸命令")
            
            // 1. 清除缓冲区，确保之前的命令都被处理
            val clearCommand = byteArrayOf(0x18)  // CAN - 清除缓冲区
            currentConnection?.write(clearCommand)
            Thread.sleep(200)
            
            // 2. 打印并进纸命令 (ESC J n) - 强制打印缓冲区内容并进纸
            val printAndFeedCommand = byteArrayOf(0x1B, 0x4A, 0x10)  // 进纸16点行
            currentConnection?.write(printAndFeedCommand)
            Thread.sleep(300)
            
            // 3. 发送换页命令 (FF) - 表单走纸
            val formFeedCommand = byteArrayOf(0x0C)  // FF - Form Feed
            currentConnection?.write(formFeedCommand)
            Thread.sleep(300)
            
            // 4. 发送GS FF命令 - 在某些打印机上是进纸到切纸位置
            val gsFfCommand = byteArrayOf(0x1D, 0x0C)  // GS FF
            currentConnection?.write(gsFfCommand)
            Thread.sleep(300)
            
            // 5. 发送多个换行 (LF)，比之前增加更多行
            val lfCommand = byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A)
            currentConnection?.write(lfCommand)
            Thread.sleep(300)
            
            // 6. 走纸命令 (ESC d n) - 走12行（比之前增加）
            val feedCommand = byteArrayOf(0x1B, 0x64, 0x0C)
            currentConnection?.write(feedCommand)
            Thread.sleep(300)
            
            // 7. 部分切纸命令 (GS V m) - 仅适用于支持切纸的打印机
            val cutCommand = byteArrayOf(0x1D, 0x56, 0x01)
            currentConnection?.write(cutCommand)
            Thread.sleep(200)
            
            // 8. 备用切纸命令 (ESC m) - 某些Star打印机使用
            val starCutCommand = byteArrayOf(0x1B, 0x6D)
            currentConnection?.write(starCutCommand)
            Thread.sleep(200)
            
            Log.d(TAG, "【打印机】额外命令发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "【打印机】发送额外命令失败: ${e.message}")
        }
    }
    
    /**
     * 专门用于TSP100系列的走纸和切纸命令
     */
    private fun sendTSP100PaperFeedAndCutCommands() {
        try {
            Log.d(TAG, "【Star TSP100】发送特殊光栅走纸和切纸命令")
            
            // 1. Star TSP100专用的打印执行命令
            val starPrintCommand = byteArrayOf(0x1B, 0x1D, 0x65, 0x01)
            currentConnection?.write(starPrintCommand)
            Thread.sleep(500)  // 给执行命令足够时间
            
            // 2. 发送光栅模式结束命令（如果在光栅模式中）
            val rasterModeEnd = byteArrayOf(0x1B, 0x1D, 0x73, 0x30, 0x02)
            currentConnection?.write(rasterModeEnd)
            Thread.sleep(300)
            
            // 3. 清除缓冲区
            currentConnection?.write(byteArrayOf(0x18))
            Thread.sleep(300)
            
            // 4. 强制刷新缓冲区
            currentConnection?.write(byteArrayOf(0x0C))  // FF命令
            Thread.sleep(500)
            
            // 5. 清除缓冲区的另一种命令
            currentConnection?.write(byteArrayOf(0x1B, 0x40))  // ESC @ 初始化打印机
            Thread.sleep(500)
            
            // 6. 直接发送多个走纸命令 - 确保出纸
            for (i in 0 until 15) { // 增加到15次
                // 发送不同程度的走纸命令
                currentConnection?.write(byteArrayOf(0x1B, 0x4A, (20 + i * 8).toByte()))
                Thread.sleep(100)
            }
            
            // 7. 在走纸后添加执行命令确保纸张出来
            currentConnection?.write(starPrintCommand)
            Thread.sleep(300)
            
            // 8. 添加更多走纸命令 - 使用ESC d n命令
            currentConnection?.write(byteArrayOf(0x1B, 0x64, 0x14))  // 走20行
            Thread.sleep(500)
            
            // 9. 发送表单送纸命令
            currentConnection?.write(byteArrayOf(0x0C))  // FF - 表单送纸
            Thread.sleep(300)
            
            // 10. 发送多个换行确保内容完全打印
            val lfCommand = ByteArray(30) { 0x0A }  // 30个换行 - 确保出纸
            currentConnection?.write(lfCommand)
            Thread.sleep(500)
            
            // 11. 走纸到切纸位置 - TSP100专用命令
            currentConnection?.write(byteArrayOf(0x1D, 0x65, 0x03))  // GS e 3
            Thread.sleep(500)
            
            // 12. TSP100专用切纸命令
            currentConnection?.write(byteArrayOf(0x1B, 0x64, 0x02))
            Thread.sleep(300)
            
            // 13. 备用切纸命令
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x01))
            Thread.sleep(300)
            
            // 14. 最后再添加一次执行命令确保所有命令都被处理
            currentConnection?.write(starPrintCommand)
            Thread.sleep(300)
            
            Log.d(TAG, "【Star TSP100】光栅模式走纸和切纸命令完成")
        } catch (e: Exception) {
            Log.e(TAG, "【Star TSP100】光栅模式走纸切纸命令失败: ${e.message}")
        }
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
            // 检查当前打印机是否为Star TSP100
            val isStarTSP100 = currentPrinterConfig?.let { config ->
                config.brand == PrinterBrand.STAR && 
                currentConnection?.device?.name?.contains("TSP100", ignoreCase = true) == true
            } ?: false
            
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
                
                // 如果是Star TSP100打印机，执行额外的内容处理
                val finalContent = if (isStarTSP100) {
                    processTSP100Content(fixedContent, alignmentTag)
                } else {
                    fixedContent
                }
                
                // 添加修复后的行
                fixedLines.add("${alignmentTag}${finalContent}")
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
     * TSP100特殊内容处理
     */
    private fun processTSP100Content(content: String, alignmentTag: String): String {
        try {
            // TSP100可能需要特殊的字符映射或格式处理
            // 这里根据TSP100的特性调整内容
            
            // 如果是居中内容，确保不超过一定长度
            if (alignmentTag == "[C]" && content.length > 40) {
                return content.substring(0, 40)
            }
            
            // 处理特殊字符
            var result = content
                .replace("×", "x")  // 替换乘号为x
                .replace("－", "-")  // 替换全角减号为半角
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "TSP100内容处理错误: ${e.message}")
            return content  // 出错时返回原内容
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
    
    private suspend fun getBluetoothDevice(address: String): BluetoothDevice? {
        return try {
            Log.d(TAG, "尝试获取地址为 $address 的蓝牙设备")
            
            // 通过已保存的设备列表查找
            discoveredDevices[address]?.let { 
                Log.d(TAG, "在已发现设备中找到设备: ${it.name ?: "未知"}")
                return it
            }
            
            // 尝试通过地址获取设备对象
            val device = this.bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                Log.d(TAG, "通过地址获取到设备: ${device.name ?: "未知"}")
                return device
            }
            
            // 如果找不到设备，执行扫描尝试发现它
            Log.d(TAG, "未找到设备，开始扫描尝试发现")
            val scanResult = withTimeoutOrNull(SCAN_TIMEOUT) {
                scanForDevice(address)
            }
            
            scanResult ?: run {
                Log.e(TAG, "扫描超时，未找到设备: $address")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取蓝牙设备失败: ${e.message}", e)
            null
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

    /**
     * 启动心跳机制，防止打印机自动断开连接
     */
    private fun startHeartbeat(config: PrinterConfig) {
        // 停止现有心跳任务
        stopHeartbeat()
        
        // 保存当前打印机配置
        currentPrinterConfig = config
        
        // 启动新的心跳任务
        heartbeatJob = managerScope.launch {
            try {
                Log.d(TAG, "启动打印机心跳机制，间隔: ${HEARTBEAT_INTERVAL/1000}秒")
                var reconnectAttempts = 0
                
                while (isActive && heartbeatEnabled) {
                    try {
                        // 1. 检查打印机连接状态
                        val status = getPrinterStatus(config)
                        
                        if (status == PrinterStatus.CONNECTED && currentConnection != null) {
                            // 2a. 如果已连接，发送心跳命令
                            try {
                                sendHeartbeatCommand()
                                // 心跳成功不需要每次都记录日志，减少日志量
                                if (reconnectAttempts > 0) {
                                    // 只有之前有重连尝试时才记录，表示恢复正常
                                    Log.d(TAG, "心跳成功，连接稳定")
                                }
                                // 心跳成功，重置重连尝试次数
                                reconnectAttempts = 0
                            } catch (e: Exception) {
                                // 心跳发送失败，可能连接已断开
                                Log.e(TAG, "心跳命令发送失败: ${e.message}")
                                updatePrinterStatus(config, PrinterStatus.ERROR)
                                throw e // 向上抛出异常以触发重连逻辑
                            }
                        } else if (status != PrinterStatus.CONNECTED) {
                            // 2b. 如果未连接，尝试重新连接
                            Log.d(TAG, "打印机未连接，尝试重新连接: ${config.name}")
                            
                            // 增加指数退避重试，避免频繁重连
                            val backoffDelay = if (reconnectAttempts > 0) {
                                // 最长延迟不超过2分钟
                                minOf(RECONNECT_DELAY * (1 shl minOf(reconnectAttempts, 5)), 120000L)
                            } else {
                                0L
                            }
                            
                            if (backoffDelay > 0) {
                                Log.d(TAG, "等待重连延迟: ${backoffDelay}ms (尝试次数: ${reconnectAttempts})")
                                delay(backoffDelay)
                            }
                            
                            // 尝试重新连接
                            val reconnected = reconnectPrinter(config)
                            
                            // 更新重连尝试次数
                            if (reconnected) {
                                reconnectAttempts = 0
                                Log.d(TAG, "重连成功，重置重试计数")
                            } else {
                                reconnectAttempts++
                                Log.d(TAG, "重连失败，增加重试计数: $reconnectAttempts")
                            }
                        }
                    } catch (e: Exception) {
                        // 捕获所有异常但继续心跳循环
                        Log.e(TAG, "打印机心跳异常: ${e.message}")
                        
                        // 如果是连接断开的异常，尝试重新连接
                        if (e.message?.contains("Broken pipe") == true || 
                            e is EscPosConnectionException) {
                            // 标记连接为断开
                            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                            
                            reconnectAttempts++
                            // 使用指数退避策略
                            val backoffDelay = minOf(RECONNECT_DELAY * (1 shl minOf(reconnectAttempts, 5)), 120000L)
                            
                            Log.d(TAG, "连接断开，等待 ${backoffDelay}ms 后尝试重连 (尝试次数: $reconnectAttempts)")
                            delay(backoffDelay)
                            
                            // 尝试重新连接
                            try {
                                val reconnected = reconnectPrinter(config)
                                if (reconnected) {
                                    reconnectAttempts = 0
                                }
                            } catch (re: Exception) {
                                Log.e(TAG, "重新连接打印机失败: ${re.message}")
                            }
                        }
                    }
                    
                    // 等待到下一个心跳周期
                    delay(HEARTBEAT_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "心跳任务异常: ${e.message}")
            }
        }
    }
    
    /**
     * 停止心跳机制
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "停止打印机心跳机制")
    }
    
    /**
     * 发送心跳命令保持连接活跃
     * 使用查询打印机状态等无影响命令
     */
    private fun sendHeartbeatCommand() {
        try {
            currentConnection?.let { connection ->
                // 1. 发送一个空白字符作为心跳
                val nulChar = byteArrayOf(0x00)  // NUL字符，不会导致打印机有实际输出
                connection.write(nulChar)
                
                // 2. 或者发送一个初始化命令，不会产生可见输出
                // val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                // connection.write(initCommand)
            } ?: throw IllegalStateException("打印机未连接")
        } catch (e: Exception) {
            Log.e(TAG, "发送心跳命令失败: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 尝试重新连接打印机
     * @return 是否成功重连
     */
    private suspend fun reconnectPrinter(config: PrinterConfig): Boolean {
        try {
            Log.d(TAG, "尝试重新连接打印机: ${config.name}")
            
            // 先断开现有连接
            try {
                currentPrinter?.disconnectPrinter()
                currentConnection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "断开现有连接失败: ${e.message}")
                // 继续尝试新连接
            }
            
            // 重置连接对象
            currentPrinter = null
            currentConnection = null
            
            // 更新状态为正在连接
            updatePrinterStatus(config, PrinterStatus.CONNECTING)
            
            // 尝试建立新连接
            delay(500) // 等待500毫秒确保之前的连接完全关闭
            
            val connected = connect(config)
            if (connected) {
                Log.d(TAG, "打印机重新连接成功: ${config.name}")
                // 发送一次测试命令确认连接
                val testResult = testConnection(config)
                if (!testResult) {
                    Log.w(TAG, "连接测试未通过，标记连接为错误状态")
                    updatePrinterStatus(config, PrinterStatus.ERROR)
                    return false
                }
                return true
            } else {
                Log.e(TAG, "打印机重新连接失败: ${config.name}")
                // 更新状态为断开连接
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "重新连接过程中发生异常: ${e.message}", e)
            // 更新状态为断开连接
            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
            return false
        }
    }

    /**
     * 测试打印机连接状态
     * 发送一个简单的测试指令到打印机，验证连接是否正常
     */
    override suspend fun testConnection(config: PrinterConfig): Boolean {
        return try {
            Log.d(TAG, "测试打印机连接: ${config.name}")
            
            // 先检查状态
            val status = getPrinterStatus(config)
            if (status != PrinterStatus.CONNECTED) {
                Log.w(TAG, "打印机未连接，无法测试连接")
                return false
            }
            
            // 尝试获取当前的BluetoothConnection
            val connection = currentConnection ?: return false
            
            // 发送一个简单的ESC/POS初始化命令(ESC @)
            // 这个命令通常用于初始化打印机，不会产生实际打印输出
            val testCommand = byteArrayOf(0x1B, 0x40) // ESC @
            
            return withContext(Dispatchers.IO) {
                try {
                    // 如果连接已关闭，尝试重新打开
                    if (!connection.isConnected()) {
                        connection.connect()
                    }
                    
                    // 发送测试命令
                    connection.write(testCommand)
                    
                    // 如果命令成功发送，视为连接正常
                    Log.d(TAG, "打印机连接测试成功")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "打印机连接测试失败: ${e.message}", e)
                    // 连接测试失败，更新状态为错误
                    updatePrinterStatus(config, PrinterStatus.ERROR)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "测试打印机连接异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.ERROR)
            false
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
            
            // 检查蓝牙适配器
            if (this.bluetoothAdapter == null) {
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
            val device = getBluetoothDevice(deviceAddress) ?: run {
                Log.e(TAG, "无法获取设备: $deviceAddress")
                return false
            }
            
            // 创建一个基本的打印机配置
            val config = PrinterConfig(
                id = UUID.randomUUID().toString(),
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
     * 获取已配对的蓝牙设备列表
     */
    private fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "缺少BLUETOOTH_CONNECT权限，无法获取已配对设备")
                    emptySet()
                } else {
                    this.bluetoothAdapter?.bondedDevices ?: emptySet()
                }
            } else {
                @Suppress("DEPRECATION")
                this.bluetoothAdapter?.bondedDevices ?: emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取已配对设备失败: ${e.message}")
            emptySet()
        }
    }

    // 扫描特定地址的设备
    private suspend fun scanForDevice(targetAddress: String): BluetoothDevice? = suspendCancellableCoroutine { continuation ->
        // 在外部定义变量引用，但不初始化
        var receiver: BroadcastReceiver? = null
        
        try {
            Log.d(TAG, "开始扫描特定地址的设备: $targetAddress")
            
            // 取消当前的扫描
            if (this.bluetoothAdapter?.isDiscovering == true) {
                this.bluetoothAdapter?.cancelDiscovery()
            }
            
            // 尝试从已配对设备中找到目标设备
            val pairedDevices = getPairedDevices()
            val deviceFromPaired = pairedDevices.find { device -> device.address == targetAddress }
            if (deviceFromPaired != null) {
                Log.d(TAG, "在已配对设备中找到目标设备: ${deviceFromPaired.name}")
                // 找到设备，恢复协程
                continuation.resume(deviceFromPaired)
                return@suspendCancellableCoroutine
            }
            
            // 声明接收器变量并保存引用到外部变量
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            
                            device?.let {
                                if (it.address == targetAddress) {
                                    // 找到目标设备，停止扫描
                                    Log.d(TAG, "在扫描中找到目标设备: ${it.name}")
                                    this@BluetoothPrinterManager.bluetoothAdapter?.cancelDiscovery()
                                    
                                    // 解注册接收器
                                    try {
                                        context.unregisterReceiver(this)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "解注册扫描接收器失败", e)
                                    }
                                    
                                    // 恢复协程
                                    if (continuation.isActive) {
                                        continuation.resume(it)
                                    }
                                }
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            // 扫描完成但未找到目标设备
                            Log.d(TAG, "蓝牙扫描完成，但未找到目标设备: $targetAddress")
                            
                            // 解注册接收器
                            try {
                                context.unregisterReceiver(this)
                            } catch (e: Exception) {
                                Log.w(TAG, "解注册扫描接收器失败", e)
                            }
                            
                            // 没有找到设备，恢复协程为null
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                }
            }
            
            // 注册广播接收器
            val intentFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(receiver, intentFilter)
            
            // 开始扫描
            this.bluetoothAdapter?.startDiscovery() ?: run {
                Log.e(TAG, "蓝牙适配器为null，无法开始扫描")
                context.unregisterReceiver(receiver)
                continuation.resume(null)
            }
            
            // 添加取消时的清理操作
            continuation.invokeOnCancellation {
                try {
                    receiver?.let { context.unregisterReceiver(it) }
                    this.bluetoothAdapter?.cancelDiscovery()
                } catch (e: Exception) {
                    Log.w(TAG, "取消扫描时清理异常", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描设备时发生异常: ${e.message}", e)
            try {
                // 使用外部保存的接收器引用
                receiver?.let { context.unregisterReceiver(it) }
            } catch (e2: Exception) {
                Log.w(TAG, "解注册扫描接收器失败", e2)
            }
            continuation.resume(null)
        }
    }

    /**
     * 更新打印机状态
     * @param config 打印机配置
     * @param status 新的打印机状态
     */
    private fun updatePrinterStatus(config: PrinterConfig, status: PrinterStatus) {
        val address = config.address
        
        // 更新状态Map
        printerStatusMap[address] = status
        
        // 更新Flow
        val flow = printerStatusFlows.getOrPut(address) { 
            MutableStateFlow(PrinterStatus.DISCONNECTED) 
        }
        flow.value = status
        
        // 如果是当前打印机，更新ViewModel中的状态
        if (currentPrinterConfig?.address == address) {
            currentPrinterConfig = config
            _printerStatus.value = status
        }
    }

    /**
     * 检查是否有蓝牙权限
     * @return 是否有蓝牙权限
     */
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

    override suspend fun printTest(config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "执行测试打印")
            
            // 检查是否为Star TSP100打印机
            val isStarTSP100 = config.brand == PrinterBrand.STAR && 
                            config.name.contains("TSP100", ignoreCase = true)
            
            // 1. 检查并确保连接
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "打印机连接失败，无法执行测试打印")
                return@withContext false
            }
            
            // 2. 生成测试打印内容
            val testContent = if (isStarTSP100) {
                // 为TSP100生成简化测试内容
                generateTSP100TestContent(config)
            } else {
                templateManager.generateTestPrintContent(config)
            }
            
            // 3. 执行打印
            val success = printContent(testContent, config)
            
            if (success) {
                Log.d(TAG, "测试打印成功")
            } else {
                Log.e(TAG, "测试打印失败")
            }
            
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "测试打印异常: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 为TSP100生成简化测试内容
     */
    private fun generateTSP100TestContent(config: PrinterConfig): String {
        return """
            [C]<b>Star TSP100打印测试</b>
            [C]------------------------
            [L]
            [L]打印机型号: ${config.name}
            [L]打印机地址: ${config.address}
            [L]
            [C]打印测试成功
            [C]
            [L]
            [L]
            [L]
        """.trimIndent()
    }

    /**
     * Star TSP100III 光栅转换器
     * 将ESC/POS命令转换为Star光栅格式
     */
    inner class StarRasterConverter {
        
        // Star TSP100III光栅命令常量
        private val RASTER_MODE_START = byteArrayOf(0x1B, 0x1D, 0x73, 0x30, 0x01)
        private val RASTER_MODE_END = byteArrayOf(0x1B, 0x1D, 0x73, 0x30, 0x02)
        private val CUT_COMMAND = byteArrayOf(0x1B, 0x64, 0x02)
        private val PRINT_EXECUTE_COMMAND = byteArrayOf(0x1B, 0x1D, 0x65, 0x01) // Star特有的打印执行命令
        
        // 将ESC/POS命令转换为Star光栅格式
        fun convertEscPosToStarRaster(escposCommands: ByteArray): ByteArray {
            try {
                Log.d(TAG, "【Star TSP100】开始转换为光栅格式，源数据大小: ${escposCommands.size}字节")
                
                // 提取文本内容创建位图
                val contentText = String(escposCommands, Charsets.UTF_8)
                Log.d(TAG, "【Star TSP100】转换文本内容长度: ${contentText.length}")
                
                // 创建位图
                val contentBitmap = createTextBitmap(contentText)
                Log.d(TAG, "【Star TSP100】位图创建完成: ${contentBitmap.width}x${contentBitmap.height}")
                
                // 将位图转换为Star光栅格式
                return bitmapToStarRaster(contentBitmap)
                
            } catch (e: Exception) {
                Log.e(TAG, "【Star TSP100】转换失败: ${e.message}", e)
                // 出错时返回一个最小的测试光栅数据
                return createMinimalRasterData()
            }
        }
        
        // 创建最小测试光栅数据
        private fun createMinimalRasterData(): ByteArray {
            val outputStream = ByteArrayOutputStream()
            try {
                Log.d(TAG, "【Star TSP100】创建测试光栅数据")
                
                // 1. 光栅模式开始
                outputStream.write(RASTER_MODE_START)
                
                // 2. 写入一个20x200像素的测试图像 - 增大尺寸和对比度
                val width = 25  // 字节数
                val height = 120 // 减少高度避免溢出
                
                // 写入高度 (2字节)
                outputStream.write(height and 0xFF)
                outputStream.write((height shr 8) and 0xFF)
                
                // 写入宽度 (字节数, 2字节)
                outputStream.write(width and 0xFF)
                outputStream.write((width shr 8) and 0xFF)
                
                // 写入测试图像数据 (粗条纹和文字)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        // 创建强对比度的条纹图案
                        val pattern = when {
                            y < 20 -> 0xFF // 顶部黑条
                            y > height-20 -> 0xFF // 底部黑条
                            y % 30 < 15 -> 0xFF // 中间条纹
                            else -> 0x00
                        }
                        outputStream.write(pattern)
                    }
                }
                
                // 3. 光栅模式结束
                outputStream.write(RASTER_MODE_END)
                
                // 4. 添加Star专用的打印执行命令
                outputStream.write(PRINT_EXECUTE_COMMAND)
                
                // 5. 额外命令确保打印执行
                outputStream.write(byteArrayOf(0x1B, 0x0C))  // ESC FF - 强制执行
                outputStream.write(byteArrayOf(0x0C))  // FF - 表单走纸
                
                // 6. 添加走纸和切纸命令
                for (i in 0 until 5) {
                    outputStream.write(byteArrayOf(0x1B, 0x4A, (30 + i * 10).toByte()))  // 不同步长走纸
                }
                outputStream.write(CUT_COMMAND)  // 切纸
                
                Log.d(TAG, "【Star TSP100】测试光栅数据创建成功，大小: ${outputStream.size()}字节")
            } catch (e: Exception) {
                Log.e(TAG, "【Star TSP100】测试光栅数据创建失败: ${e.message}")
            }
            
            return outputStream.toByteArray()
        }
        
        // 创建文本位图 (改进版本)
        private fun createTextBitmap(text: String): Bitmap {
            try {
                // 设置更合适的尺寸 - 减小宽度以减少数据量
                val width = 384 // TSP100III适中宽度 (48mm约为384点)
                
                // 计算所需高度
                val lines = text.split("\n")
                val lineHeight = 24 // 减小行高
                var height = lineHeight * (lines.size + 5) // 减少额外空间
                height = minOf(height, 480) // 强制限制最大高度为480点
                
                Log.d(TAG, "【Star TSP100】创建位图 尺寸: ${width}x${height}, 行数: ${lines.size}")
                
                // 创建位图和画布
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                // 设置白色背景
                canvas.drawColor(Color.WHITE)
                
                // 创建画笔
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 24f // 适当减小字体
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
                
                // 绘制文本
                var y = 40f // 起始位置
                for (line in lines) {
                    // 处理对齐和格式
                    var textLine = line
                    var x = 8f // 减小左边距
                    
                    // 记录原始对齐方式
                    val align = when {
                        textLine.startsWith("[C]") -> "居中"
                        textLine.startsWith("[R]") -> "右对齐"
                        textLine.startsWith("[L]") -> "左对齐"
                        else -> "默认"
                    }
                    
                    if (textLine.startsWith("[C]")) {
                        // 居中对齐
                        textLine = textLine.substring(3)
                        val textWidth = paint.measureText(textLine)
                        x = (width - textWidth) / 2
                    } else if (textLine.startsWith("[R]")) {
                        // 右对齐
                        textLine = textLine.substring(3)
                        val textWidth = paint.measureText(textLine)
                        x = width - textWidth - 8
                    } else if (textLine.startsWith("[L]")) {
                        // 左对齐
                        textLine = textLine.substring(3)
                        x = 8f
                    }
                    
                    // 处理HTML标签
                    var isBold = false
                    if (textLine.contains("<b>")) {
                        paint.isFakeBoldText = true
                        textLine = textLine.replace("<b>", "").replace("</b>", "")
                        isBold = true
                    }
                    
                    // 绘制文本，如果文本长于宽度，进行截断
                    if (paint.measureText(textLine) > width - 16) {
                        // 文本需要截断处理
                        var shorterText = textLine
                        while (paint.measureText(shorterText) > width - 16 && shorterText.length > 1) {
                            shorterText = shorterText.substring(0, shorterText.length - 1)
                        }
                        textLine = shorterText + "..."
                        Log.d(TAG, "【Star TSP100】文本过长，截断为: '$textLine'")
                    }
                    
                    // 记录绘制信息帮助调试
                    Log.d(TAG, "【Star TSP100】绘制文本行: '$textLine', 位置: ($x,$y), 对齐: $align, 粗体: $isBold")
                    
                    canvas.drawText(textLine, x, y, paint)
                    
                    // 重置画笔
                    if (isBold) {
                        paint.isFakeBoldText = false
                    }
                    
                    y += lineHeight
                    
                    // 如果已经超出高度限制，不再继续绘制
                    if (y > height - lineHeight) {
                        Log.d(TAG, "【Star TSP100】位图高度已达限制，停止绘制剩余${lines.size - (y / lineHeight).toInt()}行")
                        break
                    }
                }
                
                // 添加底部空白区域
                y += 50
                
                return bitmap
            } catch (e: Exception) {
                Log.e(TAG, "【Star TSP100】创建位图异常: ${e.message}", e)
                // 创建一个简单的白底黑边位图作为替代
                val width = 384
                val height = 240
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }
                canvas.drawRect(10f, 10f, width-10f, height-10f, paint)
                return bitmap
            }
        }
        
        // 将位图转换为Star TSP100III光栅格式 (改进版本 - 分块处理)
        private fun bitmapToStarRaster(bitmap: Bitmap): ByteArray {
            val outputStream = ByteArrayOutputStream()
            
            try {
                Log.d(TAG, "【Star TSP100】开始转换位图为光栅数据，原始尺寸: ${bitmap.width}x${bitmap.height}")
                
                // 打印宽度限制 (设置为48mm宽度)
                val maxWidth = 384
                
                // 确保位图宽度不超过打印机最大宽度
                var resizedBitmap = bitmap
                if (bitmap.width > maxWidth) {
                    val ratio = maxWidth.toFloat() / bitmap.width
                    val newHeight = (bitmap.height * ratio).toInt()
                    resizedBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
                    Log.d(TAG, "【Star TSP100】调整位图尺寸为: ${resizedBitmap.width}x${resizedBitmap.height}")
                }
                
                val width = resizedBitmap.width
                val height = resizedBitmap.height
                
                // 计算每行所需字节数 (每8个像素点1个字节)
                val bytesPerRow = (width + 7) / 8
                Log.d(TAG, "【Star TSP100】光栅宽度: $width 点, 每行字节数: $bytesPerRow")
                
                // 分块处理大图像，避免缓冲区溢出
                // 每次处理最多200点高度
                val blockHeight = 200
                Log.d(TAG, "【Star TSP100】将分${Math.ceil(height.toFloat() / blockHeight)}块处理图像")
                
                var blockIndex = 0
                for (yStart in 0 until height step blockHeight) {
                    // 计算当前块高度
                    val currentBlockHeight = minOf(blockHeight, height - yStart)
                    blockIndex++
                    
                    Log.d(TAG, "【Star TSP100】处理图像块 $blockIndex: 高度=$currentBlockHeight, 起始行=$yStart")
                    
                    // 1. 光栅模式开始命令
                    outputStream.write(RASTER_MODE_START)
                    
                    // 2. 写入图像信息
                    // 高度 (2字节)
                    outputStream.write(currentBlockHeight and 0xFF)
                    outputStream.write((currentBlockHeight shr 8) and 0xFF)
                    
                    // 宽度 (用字节表示, 2字节)
                    outputStream.write(bytesPerRow and 0xFF)
                    outputStream.write((bytesPerRow shr 8) and 0xFF)
                    
                    // 3. 写入当前块的图像数据
                    var totalBlackDots = 0
                    for (y in yStart until yStart + currentBlockHeight) {
                        for (x in 0 until width step 8) {
                            var dots: Byte = 0
                            for (b in 0 until 8) {
                                if (x + b < width) {
                                    // 获取像素
                                    val pixel = resizedBitmap.getPixel(x + b, y)
                                    // 检查是否为黑色 (或接近黑色)
                                    if (isBlackPixel(pixel)) {
                                        dots = (dots.toInt() or (0x80 shr b)).toByte()
                                        totalBlackDots++
                                    }
                                }
                            }
                            outputStream.write(dots.toInt())
                        }
                    }
                    
                    // 4. 光栅模式结束命令
                    outputStream.write(RASTER_MODE_END)
                    
                    // 5. 添加Star专用的打印执行命令
                    outputStream.write(PRINT_EXECUTE_COMMAND)
                    
                    // 6. 添加走纸命令
                    outputStream.write(byteArrayOf(0x1B, 0x4A, 0x10))  // 走纸16点
                    
                    Log.d(TAG, "【Star TSP100】图像块 $blockIndex 处理完成，包含约${totalBlackDots}个黑点")
                    
                    // 每个块之间添加短暂延迟
                    Thread.sleep(50)
                }
                
                // 7. 最终处理 - 额外的走纸和切纸命令
                outputStream.write(byteArrayOf(0x1B, 0x4A, 0x30))  // 走纸48点行
                outputStream.write(byteArrayOf(0x0C))  // FF - 走纸到切纸位置
                outputStream.write(CUT_COMMAND)  // 切纸
                
                Log.d(TAG, "【Star TSP100】光栅转换完成，最终数据大小: ${outputStream.size()}字节")
            } catch (e: Exception) {
                Log.e(TAG, "【Star TSP100】光栅转换失败: ${e.message}", e)
                return createMinimalRasterData()  // 出错时返回测试数据
            }
            
            return outputStream.toByteArray()
        }
        
        // 判断像素是否为黑色 (更精确的实现)
        private fun isBlackPixel(pixel: Int): Boolean {
            // 忽略完全透明的像素
            if (Color.alpha(pixel) < 128) return false
            
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            
            // 使用加权平均法计算亮度
            val brightness = (red * 0.299 + green * 0.587 + blue * 0.114).toInt()
            
            // 返回是否为黑色 (阈值可调整)
            return brightness < 128
        }
    }
} 