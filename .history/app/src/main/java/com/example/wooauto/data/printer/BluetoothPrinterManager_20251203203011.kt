package com.example.wooauto.data.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.wooauto.utils.UiLog
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
import com.example.wooauto.domain.printer.PrinterConnectionCheckResult
import com.example.wooauto.domain.printer.PrinterConnectionState
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.OrderPrintTemplate
import com.example.wooauto.domain.printer.PrinterVendor
import com.example.wooauto.data.printer.detect.VendorDetector
import com.example.wooauto.data.printer.star.StarPrinterDriver
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
import androidx.annotation.RequiresPermission
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Date
import kotlin.math.max
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.withLock
import com.example.wooauto.utils.ThermalPrinterFormatter
import com.example.wooauto.utils.GlobalErrorManager
import android.provider.Settings

@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: DomainSettingRepository,
    private val orderRepository: DomainOrderRepository,
    private val templateManager: OrderPrintTemplate,
    private val globalErrorManager: GlobalErrorManager
) : PrinterManager {

    // 创建协程作用域
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 专用系统轮询调度器，避免与其他IO任务竞争
    private val heartbeatDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "PrinterHeartbeat").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    // 延迟初始化蓝牙适配器，只在需要时获取
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // 蓝牙断开报警防抖任务
    private var disconnectAlertJob: Job? = null
    // 标记是否为用户主动断开，避免误报
    private var isUserInitiatedDisconnect = false
    
    private var bluetoothInitialized = false
    
    /**
     * 安全获取蓝牙适配器，只在权限检查通过后初始化
     */
    private fun getBluetoothAdapter(): BluetoothAdapter? {
        if (!bluetoothInitialized) {
            try {
                bluetoothAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val bluetoothManager =
                        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    bluetoothManager.adapter
                } else {
                    @Suppress("DEPRECATION")
                    BluetoothAdapter.getDefaultAdapter()
                }
                bluetoothInitialized = true
                UiLog.d(TAG, "【蓝牙初始化】蓝牙适配器初始化完成")
            } catch (e: Exception) {
                Log.e(TAG, "【蓝牙初始化】蓝牙适配器初始化失败: ${e.message}")
                bluetoothAdapter = null
            }
        }
        return bluetoothAdapter
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
    // 连接状态广播接收器
    private var connectionStateReceiver: BroadcastReceiver? = null

    // 供应商识别与Star驱动
    private val addressToVendor = ConcurrentHashMap<String, PrinterVendor>()
    private val starDriver: StarPrinterDriver = StarPrinterDriver(context)
    private var currentVendor: PrinterVendor? = null

    private fun ensureHeartbeatRunning(config: PrinterConfig) {
        val active = heartbeatJob?.isActive == true
        if (!active) {
            UiLog.d(TAG, "系统轮询（打印机）未运行，准备启动")
            startHeartbeat(config)
        }
    }
    private var currentPrinterConfig: PrinterConfig? = null
    private var heartbeatEnabled = true
    // 当使用外部的系统轮询时，禁用内部心跳循环
    private var externalPollingEnabled: Boolean = false

    fun setExternalPollingEnabled(enabled: Boolean) {
        externalPollingEnabled = enabled
        if (enabled) {
            // 外部接管后，停止内部心跳
            stopHeartbeat()
            UiLog.d(TAG, "外部系统轮询已启用，内部心跳停止")
        } else {
            // 由调用方在需要时重启内部心跳
            UiLog.d(TAG, "外部系统轮询已关闭，可恢复内部心跳")
        }
    }

    // 保存最后一次打印内容，用于重试
    private var lastPrintContent: String? = null

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
        private const val CONNECT_TIMEOUT = 20000L // 增加到20秒
        private const val CONNECTION_TIMEOUT = 15000L // 增加到15秒
        private const val STATUS_QUERY_TIMEOUT_MS = 3000L

        // 最大重试次数
        private const val MAX_RETRY_COUNT = 3 // 减少重试次数，避免过度重试
        private const val MAX_RECONNECT_ATTEMPTS = 2 // 减少重连次数

        // 心跳间隔 (毫秒) - 每15秒发送一次心跳，避免蓝牙断连
        private const val HEARTBEAT_INTERVAL = 5000L

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
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        // 增加详细日志帮助调试
                        intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .toInt()

                        // 保存设备，不过滤任何设备
                        discoveredDevices[it.address] = it
                        // 供应商识别（基于名称的轻量判断）
                        try {
                            val vendor = VendorDetector.detectByName(it.name)
                            addressToVendor[it.address] = vendor
                        } catch (_: Exception) {
                            // 忽略识别异常，保持通用类型
                        }

                        // 更新扫描结果
                        updateScanResults()
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
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
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val bondState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.ERROR
                    )


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
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val bondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )


                if (bondState == BluetoothDevice.BOND_BONDED) {
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

    // 添加连接锁，防止并发连接
    private val connectionMutex = Mutex()
    private var isConnecting = false

    override suspend fun connect(config: PrinterConfig): Boolean {
        return withContext(Dispatchers.IO) {
            // 使用互斥锁防止并发连接
            connectionMutex.withLock {
                connectInternal(config)
            }
        }
    }
    
    private suspend fun connectInternal(config: PrinterConfig): Boolean {
        // 重置主动断开标志，因为这是新的连接尝试
        isUserInitiatedDisconnect = false
        
        try {
            // 防止重复连接
            if (isConnecting) {
                Log.w(TAG, "正在连接中，跳过重复连接请求")
                return false
            }
            
            isConnecting = true
            UiLog.d(TAG, "【打印机连接】开始连接打印机: ${config.name} (${config.address})")

            // 检查蓝牙适配器状态
            val adapter = getBluetoothAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.e(TAG, "蓝牙未开启或不可用")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                
                // 修复：如果尝试连接时发现蓝牙没开，且不是用户主动断开，也应该报警
                // 这种情况通常发生在自动重连时发现蓝牙被关了
                if (!isUserInitiatedDisconnect) {
                    // 为了避免日志刷屏导致重复弹窗，我们可以检查一下最后一次报错时间或者状态
                    // 但由于 globalErrorManager 本身会处理 UI 展示，这里只要触发即可
                    // 为了安全起见，可以加一个简单的限流，或者复用前面的防抖逻辑
                    // 考虑到 connect() 会被轮询频繁调用，这里必须防抖
                    
                    // 简单的限流：只有当状态发生变化或者距离上次报错有一段时间才报？
                    // 其实这里直接用 status 变化来驱动最好。
                    // updatePrinterStatus 会更新状态流。
                    // 我们可以在这里触发一个一次性的检查任务
                    
                    managerScope.launch {
                         // 稍微延迟一点，确保 updatePrinterStatus 生效
                         delay(500)
                         // 如果还没开，就报警
                         val currentAdapter = getBluetoothAdapter()
                         if (currentAdapter == null || !currentAdapter.isEnabled) {
                             // 只有在当前没有显示弹窗的时候才弹？GlobalErrorManager 会处理
                             // 这里我们只负责发事件。
                             // 但为了防止轮询导致的疯狂弹窗，我们需要检查一下是否刚刚报过警
                             // 这里借用 disconnectAlertJob 来做防抖
                             if (disconnectAlertJob?.isActive != true) {
                                 disconnectAlertJob = launch {
                                     delay(2000) // 2秒防抖
                                     val dAdapter = getBluetoothAdapter()
                                     if (dAdapter == null || !dAdapter.isEnabled) {
                                         UiLog.e(TAG, "【连接失败】蓝牙未开启，触发报警")
                                         globalErrorManager.reportError(
                                            source = com.example.wooauto.utils.ErrorSource.BLUETOOTH,
                                            title = "蓝牙未开启",
                                            message = "系统蓝牙未开启，无法连接打印机。请开启蓝牙。",
                                            debugInfo = "Reason: Connect attempt failed because BT is disabled.",
                                            onSettingsAction = { 
                                                try {
                                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "无法打开蓝牙设置: ${e.message}")
                                                }
                                            }
                                        )
                                     }
                                 }
                             }
                         }
                    }
                }

                // 启动心跳以便后续自动重连
                startHeartbeat(config)
                return false
            }

            // 停止任何正在进行的设备发现，提高连接成功率
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                    delay(500) // 等待发现完全停止
                }
            } catch (e: Exception) {
                Log.w(TAG, "停止设备发现失败: ${e.message}")
            }

            // 获取蓝牙设备
            val device = getBluetoothDevice(config.address)
            if (device == null) {
                Log.e(TAG, "未找到打印机设备: ${config.address}")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                // 启动心跳以便后续自动重连（设备稍后可能出现）
                startHeartbeat(config)
                return false
            }

            // 检查设备配对状态
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        if (device.bondState != BluetoothDevice.BOND_BONDED) {
                            Log.w(TAG, "设备未配对: ${config.name}")
                        }
                    }
                } else {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        Log.w(TAG, "设备未配对: ${config.name}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "检查配对状态失败: ${e.message}")
            }

            // 如果不是当前配置的打印机，则断开原连接
            if (currentPrinterConfig != null && currentPrinterConfig?.id != config.id) {
                disconnect(currentPrinterConfig!!)
                delay(1000) // 等待断开完成
            }

            // 如果已经连接，先测试连接是否有效
            if (getPrinterStatus(config) == PrinterStatus.CONNECTED) {
                val testResult = testConnection(config)
                if (testResult) {
                    return true
                } else {
                    Log.w(TAG, "现有连接测试失败，重新建立连接")
                    // 清理现有连接
                    try {
                        currentConnection?.disconnect()
                        currentConnection = null
                        currentPrinter = null
                    } catch (e: Exception) {
                        Log.w(TAG, "清理连接失败: ${e.message}")
                    }
                }
            }

            // 依据供应商路由连接流程（Star 走独立驱动，其他走通用 ESC/POS）
            run {
                val vendor = detectVendorForDevice(device)
                currentVendor = vendor
                if (vendor == PrinterVendor.STAR) {
                    updatePrinterStatus(config, PrinterStatus.CONNECTING)
                    val ok = ensureStarConnected(config)
                    if (ok) {
                        updatePrinterStatus(config, PrinterStatus.CONNECTED)
                        currentPrinterConfig = config
                        settingRepository.setPrinterConnection(true)
                        UiLog.d(TAG, "【打印机连接】Star 流程连接成功: ${config.name}")
                        return true
                    } else {
                        Log.e(TAG, "Star 流程连接失败")
                        updatePrinterStatus(config, PrinterStatus.ERROR)
                        return false
                    }
                } else {
                    currentVendor = PrinterVendor.GENERIC
                }
            }

            // 创建蓝牙连接，添加超时控制（通用 ESC/POS 流程）

            val connection = BluetoothConnection(device)
            updatePrinterStatus(config, PrinterStatus.CONNECTING)

            // 使用withTimeout添加超时控制，增加重试机制
            var connected = false
            var lastException: Exception? = null
            
            for (attempt in 1..MAX_RETRY_COUNT) {
                try {
                    
                    
                    connected = withTimeoutOrNull(CONNECTION_TIMEOUT) {
                        try {
                            connection.connect()
                            true
                        } catch (e: Exception) {
                            Log.e(TAG, "连接尝试失败: ${e.message}")
                            lastException = e
                            false
                        }
                    } ?: false

                    if (connected) {
                        UiLog.d(TAG, "连接成功")
                        break
                    } else {
                        Log.w(TAG, "连接尝试 $attempt 失败")
                        if (attempt < MAX_RETRY_COUNT) {
                            delay(2000) // 等待2秒再重试
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "连接尝试 $attempt 异常: ${e.message}")
                    lastException = e
                    if (attempt < MAX_RETRY_COUNT) {
                        delay(2000)
                    }
                }
            }

            if (!connected) {
                Log.e(TAG, "所有连接尝试失败，最后一个异常: ${lastException?.message}")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                // 启动心跳以便后续自动重连
                startHeartbeat(config)
                return false
            }

            // 保存当前连接
            currentConnection = connection
            
            // 创建打印机实例
            val dpi = 203
            val paperWidthMm = config.paperWidth.toFloat()
            val nbCharPerLine = when (config.paperWidth) {
                PrinterConfig.PAPER_WIDTH_57MM -> 32
                PrinterConfig.PAPER_WIDTH_80MM -> 42
                else -> 32
            }

            try {
                // 创建EscPos打印机实例
                val printer = EscPosPrinter(connection, dpi, paperWidthMm, nbCharPerLine)
                currentPrinter = printer

                // 先更新状态为已连接，避免心跳线程抢先读取到旧状态触发误重连
                updatePrinterStatus(config, PrinterStatus.CONNECTED)
                // 保存当前打印机配置
                currentPrinterConfig = config

                // 注册蓝牙连接状态监听
                registerConnectionStateReceiver(config)

                // 启动心跳检测（此时状态已是 CONNECTED）
                startHeartbeat(config)
                ensureHeartbeatRunning(config)

                UiLog.d(TAG, "【打印机连接】连接成功: ${config.name}")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "创建打印机实例失败: ${e.message}", e)
                updatePrinterStatus(config, PrinterStatus.ERROR)
                connection.disconnect()
                // 启动心跳以便后续自动重连
                startHeartbeat(config)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接过程中发生异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.ERROR)
            // 启动心跳以便后续自动重连
            startHeartbeat(config)
            return false
        } finally {
            isConnecting = false
        }
    }

    /**
     * 处理格式化文本行，支持基本HTML标签
     */
    private fun processFormattedLine(line: String, outputStream: ByteArrayOutputStream) {
        var text = line
        var isBold = false
        var isDoubleWidth = false
        var isDoubleHeight = false

        // 检测当前行是否包含中文字符（当前未分支使用，去除未使用变量）
        // val hasChineseInLine = containsChineseCharacters(line)

        // 首先处理对齐标记
        when {
            text.startsWith("[L]") -> {
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 - 左对齐
                text = text.substring(3) // 移除[L]标记
            }
            text.startsWith("[C]") -> {
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 - 居中对齐
                text = text.substring(3) // 移除[C]标记
            }
            text.startsWith("[R]") -> {
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x02)) // ESC a 2 - 右对齐
                text = text.substring(3) // 移除[R]标记
            }
            else -> {
                outputStream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 - 左对齐
            }
        }
        

        // 处理加粗标签
        if (text.contains("<b>") || text.contains("</b>")) {
            // 开启加粗模式
            if (text.contains("<b>")) {
                outputStream.write(byteArrayOf(0x1B, 0x45, 0x01))  // ESC E 1
                isBold = true
            }

            // 清除标签
            text = text.replace("<b>", "").replace("</b>", "")
        }

        // 处理下划线标签
        if (text.contains("<u>") || text.contains("</u>")) {
            // 开启下划线模式
            if (text.contains("<u>")) {
                outputStream.write(byteArrayOf(0x1B, 0x2D, 0x01))  // ESC - 1
            }

            // 清除标签
            text = text.replace("<u>", "").replace("</u>", "")
        }

        // 处理双倍宽度标签
        if (text.contains("<w>") || text.contains("</w>")) {
            if (text.contains("<w>")) {
                // 开启双倍宽度 - ESC ! 设置打印模式
                outputStream.write(byteArrayOf(0x1B, 0x21, 0x20))  // 双倍宽度
                // 同时使用 GS ! 设置水平放大，增强在部分设备/中文模式下对拉丁字符的兼容
                outputStream.write(byteArrayOf(0x1D, 0x21, 0x01))  // GS ! 0x01 - 仅双倍宽度（水平 x2）
                
                // 为中文字符额外发送字体控制命令
                outputStream.write(byteArrayOf(0x1C, 0x21, 0x04))  // FS ! 4 - 中文双倍宽度
                
                isDoubleWidth = true
            }

            // 清除标签
            text = text.replace("<w>", "").replace("</w>", "")
        }

        // 处理双倍高度标签
        if (text.contains("<h>") || text.contains("</h>")) {
            if (text.contains("<h>")) {
                // 开启双倍高度 - 如果已经开启了双倍宽度，则使用双倍高宽
                if (isDoubleWidth) {
                    outputStream.write(byteArrayOf(0x1B, 0x21, 0x30))  // 双倍高宽
                    // 使用 GS ! 设置双倍高宽，确保拉丁字符同样生效（垂直 x2 且水平 x2）
                    outputStream.write(byteArrayOf(0x1D, 0x21, 0x11))  // GS ! 0x11 - 双倍高宽
                    // 为中文字符额外发送字体控制命令
                    outputStream.write(byteArrayOf(0x1C, 0x21, 0x0C))  // FS ! 12 - 中文双倍高宽
                } else {
                    outputStream.write(byteArrayOf(0x1B, 0x21, 0x10))  // 只双倍高度
                    // 使用 GS ! 设置仅双倍高度（垂直 x2）
                    outputStream.write(byteArrayOf(0x1D, 0x21, 0x10))  // GS ! 0x10 - 仅双倍高度
                    // 为中文字符额外发送字体控制命令
                    outputStream.write(byteArrayOf(0x1C, 0x21, 0x08))  // FS ! 8 - 中文双倍高度
                }
                isDoubleHeight = true
            }

            // 清除标签
            text = text.replace("<h>", "").replace("</h>", "")
        }

        // 写入纯文本内容，使用GB18030编码支持中文打印
        val encodedBytes = text.toByteArray(charset("GBK"))
        outputStream.write(encodedBytes)

        // 重置格式
        if (isBold) {
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00))  // ESC E 0 - 关闭加粗
        }

        if (isDoubleWidth || isDoubleHeight) {
            outputStream.write(byteArrayOf(0x1B, 0x21, 0x00))  // ESC ! 0 - 重置字体大小
            // 同时重置 GS ! 字符大小，避免后续英文/数字仍保持放大
            outputStream.write(byteArrayOf(0x1D, 0x21, 0x00))  // GS ! 0 - 重置字符大小
            
            // 同时重置中文字体大小
            outputStream.write(byteArrayOf(0x1C, 0x21, 0x00))  // FS ! 0 - 重置中文字体大小
        }
        
    }

    /**
     * 将我们自定义的放大标签 <h>/<w> 映射为 DantSu 可识别的放大标签
     * - 保留 <b> 由库解析
     * - 统一将 <h>/<w> 视为"大号字体"
     */
    private fun mapEnlargeTagsForDantSu(text: String): String {
        return text
            .replace("<h>", "<font size='big'>")
            .replace("</h>", "</font>")
            .replace("<w>", "<font size='big'>")
            .replace("</w>", "</font>")
    }

    

    /**
     * 统一的切纸功能实现
     * 集中所有切纸相关的代码，支持不同品牌和型号的打印机
     * 
     * @param config 打印机配置
     * @param forceCut 是否强制切纸(忽略autoCut设置)
     * @param additionalFeed 是否需要额外走纸(如首次切纸需要)
     * @return 切纸操作是否成功执行
     */
    private fun executeUnifiedPaperCut(
        config: PrinterConfig, 
        forceCut: Boolean = false,
    ): Boolean {
        try {
            // 检查是否应该切纸
            if (!forceCut && !config.autoCut) {
                return false
            }
            
            if (currentConnection == null) {
                Log.e(TAG, "【打印机】无有效连接，无法执行切纸")
                return false
            }
            
            // 记录切纸开始
            
            // 定义重置打印机命令（移到try块外部）
            val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @ - 初始化打印机
            
            // 确保执行切纸前没有其他命令在缓冲区中
            try {
                // 初始清除和重置
                val clearCommand = byteArrayOf(0x18)  // CAN - 清除打印缓冲区
                currentConnection?.write(clearCommand)
                Thread.sleep(100)
                
                currentConnection?.write(initCommand)
                Thread.sleep(100)
            } catch (e: Exception) {
                Log.e(TAG, "【打印机】切纸前清理缓冲区失败: ${e.message}")
                // 继续执行，不要因为这个错误中断
            }
            
            // 直接使用标准切纸命令 (GS V)
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x01))  // GS V 1 - 部分切纸
            // 删除等待时间，立即发送走纸命令
            
            // 发送小走纸确保切纸命令执行
            currentConnection?.write(byteArrayOf(0x0A, 0x0D))  // LF CR
            
            // 添加虚拟打印任务以触发切纸命令执行
            Thread.sleep(200) // 短暂等待确保前面命令已进入缓冲区
            
            // 添加一个几乎空白的打印内容作为触发任务
            try {
                // 发送单个空格作为内容，编码为GB18030以支持中文打印机
                val emptyContent = " ".toByteArray(charset("GBK"))
                currentConnection?.write(emptyContent)
                // 再发送一个换行，确保命令被处理
                currentConnection?.write(byteArrayOf(0x0A))
                
                // 再次重置打印机以确保所有命令被执行
                Thread.sleep(200)
                currentConnection?.write(initCommand) // 再次初始化打印机
            } catch (e: Exception) {
                Log.e(TAG, "【打印机】发送虚拟打印任务失败: ${e.message}")
                // 继续执行，这只是一个额外的尝试
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【打印机切纸】切纸操作失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 获取蓝牙设备扫描结果Flow
     */
    fun getScanResultFlow(): Flow<List<PrinterDevice>> = scanResultFlow.asStateFlow()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun scanPrinters(type: String): List<PrinterDevice> {
        if (type != PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
            Log.e(TAG, "不支持的打印机类型: $type")
            return emptyList()
        }

        // 添加安卓版本日志帮助调试
        

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
            val bluetoothAdapter = getBluetoothAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.e(TAG, "蓝牙未启用或初始化失败")
                return emptyList()
            }

            // 清空设备列表
            discoveredDevices.clear()

            // 首先添加配对设备 - 这对Android 7尤其重要
            try {
                val pairedDevices = bluetoothAdapter.bondedDevices ?: emptySet()
//                Log.d(TAG, "已配对的设备数量: ${pairedDevices.size}")

                for (device in pairedDevices) {
                    // 在日志中记录所有配对设备信息以帮助调试
//                    Log.d(TAG, "已配对设备: ${device.name ?: "未知设备"} (${device.address}), 绑定状态: ${device.bondState}")
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
                // 已有扫描正在进行，先停止它
                if (ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val adapter = getBluetoothAdapter()
                    adapter?.cancelDiscovery()
                }
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
                val adapter = bluetoothAdapter // 保存引用避免智能转换问题
                val success = adapter.startDiscovery()
//                Log.d(TAG, "开始蓝牙设备发现: $success")

                if (!success) {
                    Log.e(TAG, "无法开始蓝牙设备发现")
                    // 但仍然继续，因为我们至少有配对的设备
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动蓝牙扫描失败: ${e.message}")
                // 继续执行，至少可以显示已配对设备
            }

            // 并行调用 Star SDK 搜索（若可用），用于更精准的 Star 设备标注
            managerScope.launch {
                try {
                    val macs = starDriver.searchBluetoothMacs()
                    if (macs.isNotEmpty()) {
                        for (mac in macs) {
                            addressToVendor[mac] = PrinterVendor.STAR
                        }
                        updateScanResults()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Star 蓝牙搜索失败: ${e.message}")
                }
            }

            // 启动超时协程而不是暂停当前协程
            managerScope.launch {
                try {
                    delay(SCAN_TIMEOUT)
                    if (isScanning) {
                    UiLog.d(TAG, "蓝牙扫描超时，停止扫描")
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
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopDiscovery() {
        try {
            if (isScanning) {
                // 使用类实例中的bluetoothAdapter
                val adapter = getBluetoothAdapter()
                if (adapter != null && adapter.isEnabled) {
                    adapter.cancelDiscovery()
                }

                try {
                    context.unregisterReceiver(bluetoothReceiver)
                } catch (e: Exception) {
                    Log.w(TAG, "无法注销蓝牙广播接收器", e)
                }

                isScanning = false
//                Log.d(TAG, "蓝牙设备扫描已手动停止")
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
//            Log.d(TAG, "更新蓝牙设备列表：${devices.size}个设备")
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
            val vendor = addressToVendor[device.address] ?: PrinterVendor.GENERIC
            val displayName = if (vendor == PrinterVendor.STAR) "[STAR] $deviceName" else deviceName

            // 可按需记录配对状态
            

            PrinterDevice(
                name = displayName,
                address = device.address,
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                status = status
            )
        }.sortedWith(
            compareBy(
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
        // 标记为主动断开，抑制断联弹窗
        isUserInitiatedDisconnect = true
        
        withContext(Dispatchers.IO) {
            try {
                // 停止心跳机制
                stopHeartbeat()

                // Star 流程独立断开
                val vendor = currentVendor ?: getVendorForAddress(config.address)
                if (vendor == PrinterVendor.STAR) {
                    try {
                        starDriver.disconnect()
                    } catch (_: Exception) {
                    }
                    updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                    settingRepository.setPrinterConnection(false)
                    return@withContext
                }

                currentPrinter?.disconnectPrinter()
                currentConnection?.disconnect()
                currentPrinter = null
                currentConnection = null

                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
//                Log.d(TAG, "已断开打印机连接: ${config.getDisplayName()}")

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

    override suspend fun printOrder(order: Order, config: PrinterConfig): Boolean =
        withContext(Dispatchers.IO) {
            // 添加打印前的详细状态日志
            // 调用保留以触发状态查询，但不保存为局部变量，避免未使用告警
            getPrinterStatus(config)
//        Log.d(TAG, "【打印机状态】开始打印订单 #${order.number}，当前打印机 ${config.name} 状态: $printerStatus")

            // 检查蓝牙适配器状态
            // 保留变量名但内联使用避免未使用警告
            // 仅保留一次性检查，无需保存变量
            bluetoothAdapter?.isEnabled ?: false
//        Log.d(TAG, "【打印机状态】蓝牙适配器状态: ${if (_bluetoothEnabled) "已启用" else "未启用"}")

            // 检查打印机连接和实例
            // 一次性检查，避免未使用告警
            currentConnection != null
            currentPrinter != null
//        Log.d(TAG, "【打印机状态】连接实例: ${if (_hasConnection) "存在" else "不存在"}, 打印机实例: ${if (hasPrinter) "存在" else "不存在"}")

            // Star 路径：完全独立于 ESC/POS 流程
            if (getVendorForAddress(config.address) == PrinterVendor.STAR) {
                try {
                    if (!ensureStarConnected(config)) {
                        Log.e(TAG, "【STAR】打印机连接失败，无法打印订单")
                        return@withContext false
                    }
                    // 生成内容并转换为 Star 可打印的纯文本（由驱动内部处理粗略转换）
                    val content = generateOrderContent(order, config)
                    val ok = starDriver.printOrder(order, config, content)
                    if (ok) {
                        return@withContext handleSuccessfulPrint(order)
                    }
                    Log.e(TAG, "【STAR】打印失败")
                    return@withContext false
                } catch (e: Exception) {
                    Log.e(TAG, "【STAR】打印异常: ${e.message}", e)
                    return@withContext false
                }
            }

            var retryCount = 0
            while (retryCount < 3) {
                try {
//                Log.d(TAG, "准备打印订单: ${order.number} (尝试 ${retryCount + 1}/3)")

                    // 1. 检查并确保连接
                    if (!ensurePrinterConnected(config)) {
                        Log.e(TAG, "打印机连接失败，尝试重试...")
                        retryCount++
                        delay(1000)
                        continue
                    }

                    // 1.5 打印订单前专门清理缓存
                    try {
                        Log.d(TAG, "【打印订单】订单#${order.number} - 打印前清理缓存")
                        
                        // 初始化打印机
                        val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                        currentConnection?.write(initCommand)
                        Thread.sleep(100)
                        
                        // 清除缓冲区
                        val clearCommand = byteArrayOf(0x18)  // CAN
                        currentConnection?.write(clearCommand)
                        Thread.sleep(100)
                        
                        // 走纸一小段确保打印头位置正确
                        val feedCommand = byteArrayOf(0x1B, 0x64, 2)  // ESC d 2 - 走2行
                        currentConnection?.write(feedCommand)
                        Thread.sleep(50)
                    } catch (e: Exception) {
                        Log.e(TAG, "【打印订单】清理缓存失败: ${e.message}")
                        // 继续尝试打印，不要因为这个错误中断
                    }

                    // 2. 生成打印内容
                    val content = generateOrderContent(order, config)

                    // 3. 发送打印内容
                    val success = printContent(content, config)

                    // 4. 处理打印结果
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
                        e is EscPosConnectionException
                    ) {
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
     * 测试打印机连接，打印测试内容
     * @param config 打印机配置
     * @return 测试是否成功
     */
    suspend fun testPrinterConnection(config: PrinterConfig): Boolean {
        return try {
//            Log.d(TAG, "测试打印机连接: ${config.name} (${config.address})")

            // 检查是否有已建立的连接
            if (getPrinterStatus(config) != PrinterStatus.CONNECTED) {
//                Log.d(TAG, "打印机未连接，尝试连接...")

                // 避免循环调用 connect 方法，直接返回 false
                // if (!connect(config)) {
                //     Log.e(TAG, "无法连接到打印机，测试失败")
                //     return false
                // }
            UiLog.d(TAG, "打印机未连接，测试连接失败")
                return false
            }

            // 创建测试订单并使用正常的打印流程
            val testOrder = templateManager.createTestOrder(config)
            val success = printOrder(testOrder, config)

            if (success) {
//                Log.d(TAG, "打印测试成功")
            } else {
                Log.e(TAG, "打印测试失败")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "打印测试异常: ${e.message}", e)
            false
        }
    }

    /**
     * 生成订单打印内容
     * @param order 订单
     * @param config 打印机配置
     * @return 格式化后的打印内容
     */
    private fun generateOrderContent(order: Order, config: PrinterConfig): String {
//        Log.d(TAG, "开始生成打印内容: 订单 ${order.number}")
        val content = templateManager.generateOrderPrintContent(order, config)
//        Log.d(TAG, "生成打印内容完成，准备发送打印数据")
        return content
    }
    
    /**
     * 生成订单打印内容（指定模板）
     * @param order 订单
     * @param config 打印机配置
     * @param templateId 模板ID
     * @return 格式化后的打印内容
     */
    private fun generateOrderContent(order: Order, config: PrinterConfig, templateId: String): String {
        UiLog.d(TAG, "开始生成打印内容: 订单 ${order.number}, 模板: $templateId")
        val content = templateManager.generateOrderPrintContent(order, config, templateId)
        UiLog.d(TAG, "生成打印内容完成，准备发送打印数据")
        return content
    }

    /**
     * 处理连接错误
     * @param config 打印机配置
     */
    private suspend fun handleConnectionError(config: PrinterConfig) {
        try {
            UiLog.d(TAG, "检测到连接断开，标记为断开并同步重连")
            // 先立即标记断开，避免后续逻辑误判为已连接
            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)

            // 同步等待重连完成，减少竞态
            val reconnected = reconnectPrinter(config)
            UiLog.d(TAG, "同步重连结果: $reconnected")
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
        UiLog.d(TAG, "打印成功，检查订单 ${order.id} 当前打印状态")

        // 获取最新的订单信息
        val latestOrder = orderRepository.getOrderById(order.id)

        // 只有在订单未被标记为已打印时才进行标记
        if (latestOrder != null && !latestOrder.isPrinted) {
            UiLog.d(TAG, "标记订单 ${order.id} 为已打印")
            val markResult = orderRepository.markOrderAsPrinted(order.id)
            if (markResult) {
                UiLog.d(TAG, "成功标记订单 ${order.id} 为已打印")
            } else {
                Log.e(TAG, "标记订单 ${order.id} 为已打印-失败")
            }
        } else {
            UiLog.d(TAG, "订单 ${order.id} 已被标记为已打印，跳过重复标记")
        }

        return true
    }

    override suspend fun autoPrintNewOrder(order: Order): Boolean {
        UiLog.d(TAG, "========== 开始自动打印订单 #${order.number} ==========")
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
//                Log.d(TAG, "订单 #${order.number} 的最新状态已是已打印，跳过自动打印")
                return false
            }

            // 4. 获取默认打印机配置
            val printerConfig = getDefaultPrinterConfig() ?: return false

            // 5. 检查自动打印设置 - 只需要检查全局设置
            val globalAutoPrintEnabled = settingRepository.getAutoPrintEnabled()
            if (!globalAutoPrintEnabled) {
            UiLog.d(TAG, "全局自动打印功能未开启")
                return false
            }

            // 6. 连接打印机并打印
            if (!ensurePrinterConnected(printerConfig)) {
                Log.e(TAG, "无法连接打印机 ${printerConfig.name}，自动打印失败")
                return false
            }

            // 7. 执行打印
//            Log.d(TAG, "开始打印订单 #${order.number}")
            return printOrder(order, printerConfig)

        } catch (e: Exception) {
            Log.e(TAG, "自动打印订单 #${order.number} 时发生异常: ${e.message}", e)
            return false
        } finally {
//            Log.d(TAG, "========== 自动打印订单 #${order.number} 处理完成 ==========")
        }
    }
    
    override suspend fun printOrderWithTemplate(order: Order, config: PrinterConfig, templateId: String): Boolean =
        withContext(Dispatchers.IO) {
            UiLog.d(TAG, "使用模板打印订单: #${order.number}, 模板ID: $templateId")
            
            // 添加打印前的详细状态日志
            val currentStatus = getPrinterStatus(config)
            UiLog.d(TAG, "【打印机状态】开始使用模板打印订单 #${order.number}，当前打印机 ${config.name} 状态: $currentStatus")

            // Star 路径
            if (getVendorForAddress(config.address) == PrinterVendor.STAR) {
                try {
                    if (!ensureStarConnected(config)) {
                        Log.e(TAG, "【STAR】打印机连接失败，无法使用模板打印")
                        return@withContext false
                    }
                    val content = generateOrderContent(order, config, templateId)
                    val ok = starDriver.printOrder(order, config, content)
                    if (ok) return@withContext handleSuccessfulPrint(order)
                    Log.e(TAG, "【STAR】使用模板打印失败")
                    return@withContext false
                } catch (e: Exception) {
                    Log.e(TAG, "【STAR】使用模板打印异常: ${e.message}", e)
                    return@withContext false
                }
            }

            var retryCount = 0
            while (retryCount < 3) {
                try {
                    UiLog.d(TAG, "准备使用模板打印订单: ${order.number} (尝试 ${retryCount + 1}/3)")

                    // 1. 检查并确保连接
                    if (!ensurePrinterConnected(config)) {
                        Log.e(TAG, "打印机连接失败，尝试重试...")
                        retryCount++
                        delay(1000)
                        continue
                    }

                    // 1.5 打印订单前专门清理缓存
                    try {
                    UiLog.d(TAG, "【打印订单】订单#${order.number} - 使用模板$templateId 打印前清理缓存")
                        
                        // 初始化打印机
                        val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                        currentConnection?.write(initCommand)
                        Thread.sleep(100)
                        
                        // 清除缓冲区
                        val clearCommand = byteArrayOf(0x18)  // CAN
                        currentConnection?.write(clearCommand)
                        Thread.sleep(100)
                        
                        // 走纸一小段确保打印头位置正确
                        val feedCommand = byteArrayOf(0x1B, 0x64, 2)  // ESC d 2 - 走2行
                        currentConnection?.write(feedCommand)
                        Thread.sleep(50)
                    } catch (e: Exception) {
                        Log.e(TAG, "【打印订单】清理缓存失败: ${e.message}")
                        // 继续尝试打印，不要因为这个错误中断
                    }

                    // 2. 生成指定模板的打印内容
                    val content = generateOrderContent(order, config, templateId)

                    // 3. 发送打印内容
                    val success = printContent(content, config)

                    // 4. 处理打印结果
                    if (success) {
                        // 成功打印后处理订单状态
                        return@withContext handleSuccessfulPrint(order)
                    } else {
                        Log.e(TAG, "使用模板打印内容失败，尝试重试...")
                        retryCount++
                        delay(1000)
                    }
                } catch (e: Exception) {
                    // 关键修复：尊重协程取消，避免在超时/取消后继续重试导致重复打印
                    if (e is kotlinx.coroutines.CancellationException) {
                        UiLog.d(TAG, "使用模板打印任务被取消，停止重试: #${order.number}")
                        throw e
                    }
                    Log.e(TAG, "使用模板打印订单异常: ${e.message}", e)

                    // 对于连接断开的异常，尝试重新连接
                    if (e.message?.contains("Broken pipe") == true ||
                        e is EscPosConnectionException
                    ) {
                        handleConnectionError(config)
                    }

                    retryCount++
                    delay(1000)
                }
            }

            Log.e(TAG, "使用模板打印订单尝试3次后仍然失败: ${order.number}")
            return@withContext false
        }

    /**
     * 验证订单是否满足打印条件
     * @param order 订单
     * @return 是否满足打印条件
     */
    private fun validateOrderForPrinting(order: Order): Boolean {
        // 检查订单是否已经打印
        if (order.isPrinted) {
//            Log.d(TAG, "订单 #${order.number} 已标记为已打印，跳过自动打印")
            return false
        }

        // 检查订单状态
        if (order.status != "processing") {
//            Log.d(TAG, "订单 #${order.number} 状态不是'处理中'(${order.status})，跳过自动打印")
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

//        Log.d(TAG, "使用默认打印机: ${printerConfig.name} (${printerConfig.address})")
        return printerConfig
    }


    /**
     * 确保打印机已连接
     * @param config 打印机配置
     * @return 连接是否成功
     */
    suspend fun ensurePrinterConnected(config: PrinterConfig): Boolean {
        // 根据供应商选择不同的连接保障逻辑
        val vendor = getVendorForAddress(config.address)
        // Star 流程
        if (vendor == PrinterVendor.STAR) {
            val status = getPrinterStatus(config)
            UiLog.d(TAG, "【打印机状态】(STAR) 确保打印机连接 - 当前状态: $status，打印机: ${config.name}")

            if (status != PrinterStatus.CONNECTED) {
                updatePrinterStatus(config, PrinterStatus.CONNECTING)
                val ok = ensureStarConnected(config)
                if (ok) {
                    updatePrinterStatus(config, PrinterStatus.CONNECTED)
                    settingRepository.setPrinterConnection(true)
                    currentVendor = PrinterVendor.STAR
                    currentPrinterConfig = config
                    return true
                }
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return false
            }

            // 已标记连接，做一次轻量测试
            val ok = withTimeoutOrNull(2000) {
                try {
                    starDriver.testConnection()
                } catch (_: Exception) {
                    false
                }
            } ?: false
            if (!ok) {
                Log.w(TAG, "【打印机状态】(STAR) 连通性测试失败，尝试重连")
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                val re = ensureStarConnected(config)
                if (re) {
                    updatePrinterStatus(config, PrinterStatus.CONNECTED)
                    settingRepository.setPrinterConnection(true)
                }
                return re
            }
            return true
        }

        // 通用 ESC/POS 流程
        // 检查打印机状态
        val status = getPrinterStatus(config)
        UiLog.d(TAG, "【打印机状态】确保打印机连接 - 当前状态: $status，打印机: ${config.name}")

        // 如果未连接，使用标准连接方法
        if (status != PrinterStatus.CONNECTED) {
            UiLog.d(TAG, "【打印机状态】打印机未连接，调用标准连接方法")
            return connect(config)
        }

        // 如果状态显示已连接，但连接对象不存在，需要重新建立连接
        val connectionExists = currentConnection != null
        val printerExists = currentPrinter != null
        
        if (!connectionExists || !printerExists) {
            Log.w(TAG, "【打印机状态】状态显示已连接，但连接对象缺失，重新连接")
            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
            return connect(config)
        }

        // 额外健壮性：在标记为已连接且对象存在时，快速测试连通性
        // 避免底层蓝牙已断但状态尚未更新导致需要手动去设置页重连的情况
        val testOk = withTimeoutOrNull(3000) {
            try {
                testConnection(config)
            } catch (e: Exception) {
                false
            }
        } ?: false

        if (!testOk) {
            Log.w(TAG, "【打印机状态】连通性测试失败，标记为断开并尝试重连")
            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
            return connect(config)
        }

        return true
    }

    /**
     * 打印内容到打印机
     * @param content 打印内容
     * @param config 打印机配置
     * @return 是否打印成功
     */
    private suspend fun printContent(content: String, config: PrinterConfig): Boolean {
        // 移除未使用的局部tag
        try {
            // 确保有当前连接
            if (currentConnection == null) {
                Log.e(TAG, "【打印机】无有效连接，无法打印内容")
                return false
            }
            
            // 不再在每次打印前都初始化，避免重复操作和长走纸
            // 只有在连接时初始化一次就足够了

            // 预处理内容
            val fixedContent = validateAndFixPrintContent(content)

            // 添加额外检查，避免发送无效内容到打印机
            if (fixedContent.isBlank()) {
                Log.e(TAG, "修复后的内容仍然为空，无法打印")
                return false
            }

            // 在内容后添加额外的走纸命令
            val contentWithExtra = ensureProperEnding(fixedContent)

            // 预热探测：在真正发送大块内容之前，先发一个极小的探测字节，尽早暴露已断开的管道
            try {
                currentConnection?.write(byteArrayOf(0x00)) // NUL，不产生输出
            } catch (probeEx: Exception) {
                Log.w(TAG, "【打印机】预热探测写入失败，准备同步重连: ${probeEx.message}")
                // 同步重连
                handleConnectionError(config)
                // 重连后再次确认
                if (!ensurePrinterConnected(config)) {
                    Log.e(TAG, "【打印机】重连失败，取消本次打印")
                    return false
                }
            }

            // 保存最后一次打印内容，用于重试
            lastPrintContent = contentWithExtra

            // 不在每次打印前重复初始化，避免长走纸
            // 打印机在连接时已经初始化完成
            
            // 分块打印内容，解决缓冲区溢出问题
            UiLog.d(TAG, "开始分块打印内容（总长度: ${contentWithExtra.length}字符）")
            return chunkedPrintingProcess(contentWithExtra, config)
            
            // TODO: 后续可以添加设置选项来切换打印模式
            // 原有的分块打印逻辑暂时保留但不使用
            // return chunkedPrintingProcess(contentWithExtra, config)
        } catch (e: Exception) {
            // 捕获所有异常，包括解析异常
            Log.e(TAG, "打印机库异常: ${e.message}", e)

            // 如果是解析错误，尝试使用更简单的内容再试一次
            if (e is StringIndexOutOfBoundsException) {
                UiLog.d(TAG, "检测到解析错误，尝试使用简化内容")

                // 使用更简单的内容格式重试
                val simpleContent = createSimpleContent()

                try {
                    // 初始化打印机
                    val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                    currentConnection?.write(initCommand)
                    Thread.sleep(200)
                
                    // 打印简单内容
                    return chunkedPrintingProcess(simpleContent, config)
                    
                } catch (e2: Exception) {
                    Log.e(TAG, "简化内容打印失败: ${e2.message}")
                    return false
                }
            } else {
                return false
            }
        }
    }

    /**
     * 确保打印内容有适当的结尾，添加特殊触发打印字符
     */
    private fun ensureProperEnding(content: String): String {
        UiLog.d(TAG, "【打印机】确保内容适当结尾")
        
        // 确保内容以换行结束
        val contentWithNewLine = if (content.endsWith("\n")) content else "$content\n"
        
        // 只添加最小必要的结尾内容，避免长走纸
        // 仅添加一个换行符和切纸命令，不添加多余的走纸
        val triggerSequence = "\u001D\u0056\u0001"  // GS V 1 - 部分切纸命令
        
        UiLog.d(TAG, "【打印机】添加最小必要的结尾序列")
        
        return contentWithNewLine + triggerSequence
    }
    
    /**
     * 分块打印流程
     * 将打印内容分成小块进行打印，确保每块内容都能被处理
     * @param content 要打印的内容
     * @param config 打印机配置
     * @return 是否成功打印
     */
    private suspend fun chunkedPrintingProcess(content: String, config: PrinterConfig): Boolean {
        try {
            // 将内容按行分割
            val lines = content.split("\n")
            val totalLines = lines.size
            UiLog.d(TAG, "分块打印，总行数: $totalLines")
            
            // 检查整个内容是否包含中文字符，决定使用统一的处理方式
            val hasChineseContent = containsChineseCharacters(content)
            UiLog.d(TAG, "【编码策略】整个订单包含中文: $hasChineseContent")
            
            if (hasChineseContent) {
                // 如果订单包含中文，整个订单都使用GBK编码处理
                UiLog.d(TAG, "【统一中文处理】整个订单使用GBK编码处理")
                val startTime = System.currentTimeMillis()
                sendContentWithGBKEncoding(content)
                val endTime = System.currentTimeMillis()
                UiLog.d(TAG, "【统一中文处理】完整订单GBK处理完成，耗时: ${endTime - startTime}ms")

                // 添加ESC/POS触发器 - 发送一个空的英文打印任务，确保部分机型立即处理缓存
                UiLog.d(TAG, "【中文触发器】发送ESC/POS触发任务")
                try {
                    currentPrinter?.printFormattedText(" \n")
                    delay(100)
                    UiLog.d(TAG, "【中文触发器】ESC/POS触发任务完成")
                } catch (e: Exception) {
                    Log.e(TAG, "【中文触发器】发送触发任务失败: ${e.message}")
                }
            } else {
                // 如果订单不包含中文，先尝试整块发送；失败则回退为分块发送
                UiLog.d(TAG, "【统一英文处理】整个订单使用ESC/POS库处理")
                val startTime = System.currentTimeMillis()
                try {
                    val mapped = mapEnlargeTagsForDantSu(content)
                    currentPrinter?.printFormattedText(mapped)
                    val endTime = System.currentTimeMillis()
                    UiLog.d(TAG, "【统一英文处理】完整订单ESC/POS处理完成，耗时: ${endTime - startTime}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "【统一英文处理】整块发送失败: ${e.message}，尝试分块发送")
                    // 若为连接类错误，先同步重连，再按小块发送
                    if (e.message?.contains("Broken pipe") == true || e is EscPosConnectionException) {
                        handleConnectionError(config)
                    }

                    // 分块（按行）发送，减小缓冲压力
                    val chunkSize = 8
                    var index = 0
                    while (index < lines.size) {
                        val end = minOf(index + chunkSize, lines.size)
                        val chunk = lines.subList(index, end).joinToString("\n")
                        val sanitizedChunk = mapEnlargeTagsForDantSu(chunk)
                        try {
                            currentPrinter?.printFormattedText(sanitizedChunk)
                        } catch (ce: Exception) {
                            Log.e(TAG, "【统一英文处理】分块发送失败: ${ce.message}")
                            if (ce.message?.contains("Broken pipe") == true || ce is EscPosConnectionException) {
                                handleConnectionError(config)
                                // 重连后重试当前块一次
                                try {
                                    currentPrinter?.printFormattedText(sanitizedChunk)
                                } catch (retryEx: Exception) {
                                    Log.e(TAG, "【统一英文处理】分块重试仍失败: ${retryEx.message}")
                                    throw retryEx
                                }
                            } else {
                                throw ce
                            }
                        }
                        index = end
                        delay(30)
                    }
                }
            }
            
            // 确保所有内容都已打印完毕
            forcePrinterFlush()
            delay(100) // 减少等待时间
            
            // 最后清除缓冲区，但不再单独发送切纸命令
            UiLog.d(TAG, "所有内容打印完成，刷新缓冲区")
            
            try {
                // 添加虚拟微型打印任务，触发硬件执行上一个打印任务中的切纸命令
                try {
                    UiLog.d(TAG, "【打印机】添加虚拟打印任务触发切纸执行")
                    
                    // 1. 初始化打印机
                    currentConnection?.write(byteArrayOf(0x1B, 0x40))  // ESC @
                    Thread.sleep(50)
                    
                    // 2. 小走纸，触发处理
                    currentConnection?.write(byteArrayOf(0x1B, 0x64, 0x01))  // ESC d 1
                    Thread.sleep(50)
                    
                    UiLog.d(TAG, "【打印机】虚拟打印任务完成")
                } catch (e: Exception) {
                    // 忽略错误继续执行
                    Log.e(TAG, "【打印机】虚拟打印任务失败: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "【打印机】刷新缓冲区失败: ${e.message}")
                // 打印仍然算成功，只是切纸失败
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "分块打印出错: ${e.message}")
            // 若为连接类错误，立即标记断开并同步重连，交由上层重试
            if (e.message?.contains("Broken pipe") == true || e is EscPosConnectionException) {
                handleConnectionError(config)
            }
            return false
        }
    }
    
    /**
     * 强制打印机刷新缓冲区
     * 发送多种命令确保缓冲区内容被处理
     */
    private fun forcePrinterFlush() {
        try {
            if (currentConnection == null) {
                return
            }
            
            // 发送多个换行符，确保内容输出
            val lineFeeds = byteArrayOf(0x0A, 0x0A, 0x0A)  // 3个LF
            currentConnection?.write(lineFeeds)
            Thread.sleep(50) // 减少休眠时间
            
            // 发送换页命令
            val formFeedCommand = byteArrayOf(0x0C)  // FF
            currentConnection?.write(formFeedCommand)
            Thread.sleep(50) // 减少休眠时间
            
            // 发送回车
            val crCommand = byteArrayOf(0x0D)  // CR
            currentConnection?.write(crCommand)
            Thread.sleep(50) // 减少休眠时间
            
        } catch (e: Exception) {
            Log.e(TAG, "刷新打印机缓冲区失败: ${e.message}")
        }
    }

    /**
     * 创建简单的打印内容，适用于出现解析问题时使用
     */
    private fun createSimpleContent(): String {
        return """
            [L]test printing
            [L]----------------
            [L]
            [L]printing is working
            [L]----------------
            
            
            
        """.trimIndent()
    }

    private fun addToPrintQueue(job: PrintJob) {
        synchronized(printQueue) {
            printQueue.add(job)
//            Log.d(TAG, "添加打印任务到队列，当前队列长度: ${printQueue.size}")
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

//                Log.d(TAG, "处理打印队列任务: 订单ID=${job.orderId}")

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
                    UiLog.d(TAG, "重试打印订单: ${job.orderId}, 尝试次数: $i")
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
                        UiLog.d(TAG, "打印订单副本 ${i + 1}/${job.copies}: ${job.orderId}")
                    }
                }

                delay(500) // 添加短暂延迟避免过于频繁的打印请求
            }
        } finally {
            isProcessingQueue = false
        }
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
//            Log.d(TAG, "尝试获取地址为 $address 的蓝牙设备")

            // 通过已保存的设备列表查找
            discoveredDevices[address]?.let {
//                Log.d(TAG, "在已发现设备中找到设备: ${it.name ?: "未知"}")
                return it
            }

            // 尝试通过地址获取设备对象
            val device = getBluetoothAdapter()?.getRemoteDevice(address)
            if (device != null) {
//                Log.d(TAG, "通过地址获取到设备: ${device.name ?: "未知"}")
                return device
            }

            // 如果找不到设备，执行扫描尝试发现它
//            Log.d(TAG, "未找到设备，开始扫描尝试发现")
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
     * 供应商识别（设备级）
     */
    private fun detectVendorForDevice(device: BluetoothDevice): PrinterVendor {
        // 先用缓存
        addressToVendor[device.address]?.let { return it }
        val vendor = try {
            VendorDetector.detectByName(device.name)
        } catch (_: Exception) {
            PrinterVendor.GENERIC
        }
        addressToVendor[device.address] = vendor
        return vendor
    }

    /**
     * 供应商识别（地址级）
     */
    private suspend fun getVendorForAddress(address: String): PrinterVendor {
        addressToVendor[address]?.let { return it }
        val device = getBluetoothDevice(address) ?: return PrinterVendor.GENERIC
        return detectVendorForDevice(device)
    }

    /**
     * 确保 Star 打印机连接
     */
    private suspend fun ensureStarConnected(config: PrinterConfig): Boolean {
        val device = getBluetoothDevice(config.address) ?: return false
        if (!starDriver.isConnected(config.address)) {
            if (!starDriver.connect(device, config)) {
                return false
            }
        }
        return true
    }

    /**
     * 输出蓝牙诊断信息，帮助识别权限问题
     */
    fun logBluetoothDiagnostics() {
        
        Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")

        // 检查蓝牙适配器（统一走封装，避免直接使用已弃用API）
        val bluetoothAdapter = getBluetoothAdapter()
        

        // 检查权限状态
        val permissions = mutableListOf<Pair<String, Boolean>>()

        // 常规蓝牙权限
        permissions.add(
            Pair(
                "BLUETOOTH",
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED
            )
        )
        permissions.add(
            Pair(
                "BLUETOOTH_ADMIN",
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 位置权限(对于API 30及以下的扫描)
        permissions.add(
            Pair(
                "ACCESS_FINE_LOCATION",
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            )
        )

        // Android 12的新权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(
                Pair(
                    "BLUETOOTH_SCAN",
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                )
            )
            permissions.add(
                Pair(
                    "BLUETOOTH_CONNECT",
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                )
            )
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
        if (externalPollingEnabled) {
            // 外部系统轮询已接管，仅更新当前配置，避免重复循环
            currentPrinterConfig = config
            // Log.d(TAG, "外部系统轮询启用，跳过内部系统轮询启动")
            return
        }
        // 若已有相同打印机的心跳在运行，则不重复启动，避免短时间被取消
        if (heartbeatJob?.isActive == true && currentPrinterConfig?.address == config.address) {
            return
        }

        // 对于不同设备，先停止旧的心跳
        if (heartbeatJob?.isActive == true && currentPrinterConfig?.address != config.address) {
            stopHeartbeat()
        }

        // 保存当前打印机配置
        currentPrinterConfig = config

        // 启动新的心跳任务
        heartbeatJob = managerScope.launch(heartbeatDispatcher) {
            try {
                Log.d(TAG, "启动系统轮询（打印机），间隔: ${HEARTBEAT_INTERVAL / 1000}秒")
                var reconnectAttempts = 0
                var lastReconnectTime = 0L
                // 仅在需要统计心跳成功时间时启用
                // var lastSuccessfulHeartbeat = System.currentTimeMillis()

                while (isActive && heartbeatEnabled) {
                    try {
                        val currentTime = System.currentTimeMillis()

                        // 1. 优先以连接对象为准判断连接性，避免依赖UI状态造成竞态
                        val hasConnection = currentConnection != null
                        val isSocketConnected = try {
                            val conn = currentConnection
                            if (conn == null) false else conn.isConnected()
                        } catch (_: Exception) { false }

                        if (hasConnection) {
                            if (!isSocketConnected) {
                                Log.w(TAG, "检测到底层socket未连接，标记为断开")
                                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                                throw EscPosConnectionException("Socket not connected")
                            }
                            // 存在连接对象，主动发心跳验证
                            var writeOk = false
                            try {
                                sendHeartbeatCommand()
                                writeOk = true
                            } catch (e: Exception) {
                                Log.e(TAG, "系统轮询写入失败（打印机）: ${e.message}")
                                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                                throw e
                            } finally {
                                // 不再基于 isConnected() 做写后确认，避免假阳性
                                if (writeOk) {
                                    // 轻量心跳：不在此处更新 CONNECTED，交由连接/广播路径维护
                                }
                                if (reconnectAttempts > 0) {
                                    Log.d(TAG, "系统轮询成功，连接恢复稳定")
                                    reconnectAttempts = 0
                                }
                            }
                        } else {
                            // 无连接对象时才进入重连逻辑
                            val timeSinceLastReconnect = currentTime - lastReconnectTime

                            val reconnectWindow = 10000L // 打开设备后10秒内就进行一次尝试
                            if (timeSinceLastReconnect > reconnectWindow && reconnectAttempts < MAX_RECONNECT_ATTEMPTS * 2) {
                                Log.d(TAG, "打印机未连接，尝试重新连接: ${config.name} (尝试次数: ${reconnectAttempts + 1})")

                                val backoffDelay = if (reconnectAttempts > 0) {
                                    minOf(RECONNECT_DELAY * (1 shl (reconnectAttempts / 2)), 60000L)
                                } else 0L

                                if (backoffDelay > 0) {
                                    Log.d(TAG, "等待${backoffDelay}ms后重连")
                                    delay(backoffDelay)
                                }

                                lastReconnectTime = currentTime
                                val reconnected = reconnectPrinter(config)

                                if (reconnected) {
                                    reconnectAttempts = 0
                                    // lastSuccessfulHeartbeat = System.currentTimeMillis()
                                    Log.d(TAG, "重连成功，重置重试计数")
                                } else {
                                    reconnectAttempts++
                                    Log.d(TAG, "重连失败，增加重试计数: $reconnectAttempts")
                                }
                            } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS * 2) {
                                Log.w(TAG, "已达到最大重连次数，暂停重连尝试")
                                if (timeSinceLastReconnect > 600000) { // 10分钟后重置
                                    reconnectAttempts = 0
                                    Log.d(TAG, "长时间暂停后重置重连计数")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "系统轮询（打印机）异常: ${e.message}")
                        
                        // 处理连接断开异常
                        if (e.message?.contains("Broken pipe", ignoreCase = true) == true ||
                            e.message?.contains("Connection reset", ignoreCase = true) == true ||
                            e.message?.contains("software caused connection abort", ignoreCase = true) == true ||
                            e.message?.contains("connection timed out", ignoreCase = true) == true ||
                            e is EscPosConnectionException) {
                            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                            reconnectAttempts++
                            Log.d(TAG, "检测到连接断开，增加重连计数: $reconnectAttempts")
                        }
                    }

                    // 等待到下一个心跳周期
                    delay(HEARTBEAT_INTERVAL)
                }
            } catch (e: Exception) {
                Log.e(TAG, "系统轮询任务异常（打印机）: ${e.message}")
            }
        }
    }

    /**
     * 停止心跳机制
     */
    private fun stopHeartbeat() {
        if (heartbeatJob?.isActive == true) {
            heartbeatJob?.cancel()
            Log.d(TAG, "停止打印机心跳机制")
        }
        heartbeatJob = null
        // 注销连接状态监听
        unregisterConnectionStateReceiver()
    }

    /**
     * 发送心跳命令保持连接活跃
     * 使用查询打印机状态等无影响命令
     */
    private fun sendHeartbeatCommand() {
        try {
            currentConnection?.let { connection ->
                // 使用无副作用的心跳字节，避免重置打印机
                val heartbeat = byteArrayOf(0x00)  // NUL
                connection.write(heartbeat)
            } ?: throw IllegalStateException("打印机未连接")
        } catch (e: Exception) {
            Log.e(TAG, "发送心跳命令失败: ${e.message}", e)
            throw e
        }
    }

    private fun registerConnectionStateReceiver(config: PrinterConfig) {
        try {
            unregisterConnectionStateReceiver()
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            connectionStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    try {
                        when (action) {
                            BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED,
                            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }
                                
                                val isTargetDevice = device?.address == config.address
                                val isFallback = device == null && currentPrinterConfig?.address == config.address && currentConnection != null
                                
                                if (isTargetDevice || isFallback) {
                                    Log.d(TAG, "接收到设备断开广播，标记为断开 (Target=$isTargetDevice, Fallback=$isFallback)")
                                    updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                                    
                                    // 防抖弹窗逻辑
                                    if (!isUserInitiatedDisconnect) {
                                        disconnectAlertJob?.cancel()
                                        disconnectAlertJob = managerScope.launch {
                                            delay(5000) // 5秒防抖，等待自动重连机会
                                            
                                            // [新增逻辑] 如果蓝牙已关闭，则不报打印机断联，避免与"蓝牙已关闭"弹窗重复
                                            val adapter = getBluetoothAdapter()
                                            if (adapter == null || !adapter.isEnabled) {
                                                Log.d(TAG, "防抖结束，检测到蓝牙已关闭，忽略打印机层面的断联报警")
                                                return@launch
                                            }

                                            val currentStatus = getPrinterStatus(config)
                                            // 修正：即使状态是 CONNECTING 也应该检查是否实际上失败了
                                            // 5秒后如果不是 CONNECTED，就应该报警
                                            // 如果是 CONNECTING，说明正在重连，但5秒都没连上，可能也需要报警或者继续观察
                                            // 这里简化逻辑：只要不是 CONNECTED 并且没有被取消，就报警
                                            // 同时也需要排除正在重连的情况，避免打断自动重连
                                            
                                            Log.d(TAG, "防抖结束检查状态: $currentStatus, 主动断开: $isUserInitiatedDisconnect")

                                            if (currentStatus != PrinterStatus.CONNECTED && !isUserInitiatedDisconnect) {
                                                // 如果是 CONNECTING，说明 SystemPollingManager 正在努力重连
                                                // 我们给它更多时间，或者检查重连是否已经持续太久
                                                // 简单的做法：如果是 CONNECTING，我们再给一次机会，或者直接由 SystemPollingManager 失败后处理
                                                // 但 SystemPollingManager 失败只是打印日志，不会弹窗
                                                
                                                // 决定：只要 5 秒后还没连上 (Connected)，就弹窗
                                                // 这样用户知道出问题了。如果随后连上了，弹窗还在也没关系，用户点确定就行
                                                // 或者可以监听连接成功关闭弹窗（太复杂）
                                                
                                                UiLog.e(TAG, "【蓝牙断联】触发全局弹窗报警 (状态: $currentStatus)")
                                                globalErrorManager.reportError(
                                                    source = com.example.wooauto.utils.ErrorSource.PRINTER_CONN,
                                                    title = "打印机连接中断",
                                                    message = "蓝牙打印机已断开，新订单将无法自动打印。请检查打印机电源和状态。",
                                                    debugInfo = "Reason: ACL_DISCONNECTED broadcast received and timed out (5s).\nDevice: ${config.name}\nAddress: ${config.address}",
                                                    onSettingsAction = { 
                                                        try {
                                                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "无法打开蓝牙设置: ${e.message}")
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        UiLog.d(TAG, "用户主动断开，忽略断联弹窗")
                                    }
                                }
                            }
                            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                                // 连接恢复，立即取消待触发的报警
                                disconnectAlertJob?.cancel()
                                
                                // 清除打印机断联错误
                                globalErrorManager.resolveError(com.example.wooauto.utils.ErrorSource.PRINTER_CONN)
                                
                                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }
                                if (device?.address == config.address) {
                                    Log.d(TAG, "接收到设备已连接广播")
                                    updatePrinterStatus(config, PrinterStatus.CONNECTED)
                                }
                            }
                            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                                if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                                    Log.d(TAG, "蓝牙适配器关闭，标记断开")
                                    updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                                    
                                    // 蓝牙关闭是大事，直接报警，不需要防抖 (因为蓝牙关闭了肯定连不上)
                                    if (!isUserInitiatedDisconnect) {
                                         UiLog.e(TAG, "【蓝牙关闭】触发全局弹窗报警")
                                         globalErrorManager.reportError(
                                            source = com.example.wooauto.utils.ErrorSource.BLUETOOTH,
                                            title = "蓝牙已关闭",
                                            message = "检测到系统蓝牙已关闭，打印机无法工作。请开启蓝牙。",
                                            debugInfo = "Reason: Bluetooth Adapter turned OFF.",
                                            onSettingsAction = { 
                                                try {
                                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "无法打开蓝牙设置: ${e.message}")
                                                }
                                            }
                                        )
                                    }
                                } else if (state == BluetoothAdapter.STATE_ON) {
                                    // 蓝牙恢复开启，自动清除蓝牙错误
                                    globalErrorManager.resolveError(com.example.wooauto.utils.ErrorSource.BLUETOOTH)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "连接状态广播处理异常: ${e.message}")
                    }
                }
            }
            context.registerReceiver(connectionStateReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "注册连接状态广播失败: ${e.message}")
        }
    }

    private fun unregisterConnectionStateReceiver() {
        try {
            connectionStateReceiver?.let {
                context.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "注销连接状态广播失败: ${e.message}")
        } finally {
            connectionStateReceiver = null
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
     * 改进：使用 queryRealtimeStatus 进行双向健康检查，而不仅仅是发送 ESC @
     */
    override suspend fun testConnection(config: PrinterConfig): Boolean {
        return try {
//            Log.d(TAG, "测试打印机连接: ${config.name}")

            // Star 路径
            if (getVendorForAddress(config.address) == PrinterVendor.STAR) {
                if (!starDriver.isConnected(config.address)) {
                    Log.w(TAG, "【STAR】未连接，连接测试失败")
                    return false
                }
                return withContext(Dispatchers.IO) {
                    starDriver.testConnection()
                }
            }

            // 先检查状态
            val status = getPrinterStatus(config)
            if (status != PrinterStatus.CONNECTED) {
                UiLog.d(TAG, "打印机未连接，无法测试连接")
                return false
            }

            // 使用 queryRealtimeStatus 进行深度检查
            // 这会触发 DLE EOT 指令并使用 parseOfflineStatus 解析，能准确识别缺纸、开盖等硬件问题
            val result = queryRealtimeStatus(config)
            
            val isHealthy = when (result.state) {
                PrinterConnectionState.ONLINE -> true
                PrinterConnectionState.WARNING -> true // 警告（如纸将尽）暂视为连接正常，允许继续工作
                else -> false
            }

            if (isHealthy) {
//                Log.d(TAG, "打印机深度健康检查通过: ${result.summary}")
                true
            } else {
                Log.w(TAG, "打印机健康检查失败: ${result.summary} (${result.detail})")
                // 更新为错误状态，以便触发 SystemPollingManager 或其他机制进行重连/报错
                updatePrinterStatus(config, PrinterStatus.ERROR)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "测试打印机连接异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.ERROR)
            false
        }
    }

    override suspend fun queryRealtimeStatus(config: PrinterConfig): PrinterConnectionCheckResult {
        Log.d(TAG, "【状态检查入口】queryRealtimeStatus 被调用，目标: ${config.name}")
        return try {
            val vendor = getVendorForAddress(config.address)
            Log.d(TAG, "【状态检查入口】厂商识别结果: $vendor")
            
            when (vendor) {
                PrinterVendor.STAR -> {
                    Log.d(TAG, "【状态检查入口】分发到 Star 驱动")
                    queryStarRealtimeStatus(config)
                }
                else -> {
                    Log.d(TAG, "【状态检查入口】分发到 ESC/POS 驱动")
                    queryEscPosRealtimeStatus(config)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打印机状态检测异常: ${e.message}", e)
            PrinterConnectionCheckResult(
                state = PrinterConnectionState.ERROR,
                summary = "检测失败: ${e.message ?: "未知错误"}"
            )
        }
    }

    private suspend fun queryStarRealtimeStatus(config: PrinterConfig): PrinterConnectionCheckResult {
        val connected = ensurePrinterConnected(config)
        if (!connected) {
            return PrinterConnectionCheckResult(
                state = PrinterConnectionState.ERROR,
                summary = "无法连接到打印机"
            )
        }
        val ok = withContext(Dispatchers.IO) {
            runCatching { starDriver.testConnection() }.getOrDefault(false)
        }
        return if (ok) {
            PrinterConnectionCheckResult(
                state = PrinterConnectionState.ONLINE,
                summary = "打印机在线",
                detail = "Star 驱动返回正常状态"
            )
        } else {
            PrinterConnectionCheckResult(
                state = PrinterConnectionState.ERROR,
                summary = "无法获取打印机状态"
            )
        }
    }

    private suspend fun queryEscPosRealtimeStatus(config: PrinterConfig): PrinterConnectionCheckResult {
        Log.d(TAG, "【状态检查】开始执行 queryEscPosRealtimeStatus")
        if (!ensurePrinterConnected(config)) {
            Log.w(TAG, "【状态检查】打印机未连接，直接返回错误")
            return PrinterConnectionCheckResult(
                state = PrinterConnectionState.ERROR,
                summary = "无法连接到打印机"
            )
        }

        val connection = currentConnection ?: return PrinterConnectionCheckResult(
            state = PrinterConnectionState.ERROR,
            summary = "无有效连接对象"
        )

        val socket = PrinterDiagnosticTool.getSocket(connection) ?: return PrinterConnectionCheckResult(
            state = PrinterConnectionState.ERROR,
            summary = "无法访问蓝牙Socket"
        )

        val inputStream = try {
            socket.inputStream
        } catch (e: Exception) {
            Log.e(TAG, "获取输入流失败: ${e.message}")
            return PrinterConnectionCheckResult(
                state = PrinterConnectionState.ERROR,
                summary = "读取打印机响应失败"
            )
        }

        val outputStream = try {
            socket.outputStream
        } catch (e: Exception) {
            Log.e(TAG, "获取输出流失败: ${e.message}")
            return PrinterConnectionCheckResult(
                state = PrinterConnectionState.ERROR,
                summary = "发送打印机指令失败"
            )
        }

        val traceRecords = mutableListOf<String>()
        var lastResponseHex: String? = null
        var lastResponseDec: String? = null
        var lastCommandId: String? = null
        var responseCount = 0

        return withContext(Dispatchers.IO) {
            // 使用互斥锁保护状态查询过程，防止心跳或测试线程干扰
            try {
                for (query in escPosStatusQueries) {
                    Log.d(TAG, "【状态检查】准备发送指令: ${query.id} (${query.description})")
                    
                    // 使用 PrinterDiagnosticTool 的可靠通信方法替换原有的 sendEscPosStatusCommand
                    val response = PrinterDiagnosticTool.sendAndReceiveRaw(
                        inputStream, 
                        outputStream, 
                        query.command, 
                        3000 // 3秒超时
                    )
                    
                    if (response == null || response.isEmpty()) {
                        Log.w(TAG, "【状态检查】指令 ${query.id} 无响应")
                        traceRecords += "${query.id}: 无响应"
                        continue
                    }

                    Log.d(TAG, "【状态检查】指令 ${query.id} 收到响应: ${response.toHexString()}")
                    responseCount++
                    val hex = response.toHexString()
                    val dec = response.toDecimalString()
                    traceRecords += "${query.id}: $hex"
                    lastResponseHex = hex
                    lastResponseDec = dec
                    lastCommandId = query.id

                    val parsed = query.parser(response)
                    if (parsed != null) {
                        Log.i(TAG, "【状态检查】解析成功: ${parsed.summary}")
                        val trace = traceRecords.joinToString("\n").takeIf { it.isNotBlank() }
                        return@withContext parsed.copy(
                            detail = mergeStatusDetails(parsed.detail, trace),
                            commandUsed = query.id,
                            rawResponseHex = hex,
                            rawResponseDec = dec
                        )
                    } else {
                        Log.w(TAG, "【状态检查】无法解析响应数据")
                    }
                }

                val trace = traceRecords.joinToString("\n").takeIf { it.isNotBlank() }
                if (responseCount > 0) {
                    PrinterConnectionCheckResult(
                        state = PrinterConnectionState.ONLINE,
                        summary = "打印机已响应，但无法解析具体状态",
                        detail = trace,
                        commandUsed = lastCommandId,
                        rawResponseHex = lastResponseHex,
                        rawResponseDec = lastResponseDec
                    )
                } else {
                    PrinterConnectionCheckResult(
                        state = PrinterConnectionState.ERROR,
                        summary = "未收到打印机状态回应",
                        detail = trace
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "状态检查过程异常: ${e.message}")
                PrinterConnectionCheckResult(
                    state = PrinterConnectionState.ERROR,
                    summary = "状态检查异常: ${e.message}"
                )
            }
        }
    }

    private suspend fun sendEscPosStatusCommand(
        connection: BluetoothConnection,
        inputStream: InputStream,
        command: ByteArray,
        timeoutMs: Long
    ): ByteArray? {
        return try {
            // 恢复 drainInputStream，确保每次发送命令前缓冲区是干净的
            // 这能有效防止“读到上一次命令的延迟响应”导致的解析错误
            drainInputStream(inputStream)
            
            connection.write(command)
            readStatusResponse(inputStream, timeoutMs)
        } catch (e: Exception) {
            Log.e(TAG, "发送状态命令失败: ${e.message}")
            null
        }
    }

    private suspend fun readStatusResponse(inputStream: InputStream, timeoutMs: Long): ByteArray? {
        return withContext(Dispatchers.IO) {
            val buffer = ByteArrayOutputStream()
            val start = SystemClock.elapsedRealtime()
            
            Log.d(TAG, "【状态检查】readStatusResponse 开始读取 (纯非阻塞模式)，超时设定: ${timeoutMs}ms")

            try {
                while (SystemClock.elapsedRealtime() - start < timeoutMs) {
                    // 1. 只使用 available (非阻塞)，绝对不调用可能导致死锁的 inputStream.read()
                    val available = runCatching { inputStream.available() }.getOrElse { 0 }
                    
                    if (available > 0) {
                        Log.v(TAG, "【状态检查】发现 available 数据: $available bytes")
                        val chunk = ByteArray(minOf(available, 1024))
                        val read = inputStream.read(chunk) // 有 available 保证，这里的 read 不会阻塞
                        if (read > 0) {
                            Log.v(TAG, "【状态检查】读取到 chunk: $read bytes")
                            buffer.write(chunk, 0, read)
                            
                            // 读到了数据，稍微等一下看有没有更多
                            delay(20)
                            
                            // 如果读完这一波没有更多了，就认为读完了
                            if (runCatching { inputStream.available() }.getOrElse { 0 } == 0) {
                                Log.d(TAG, "【状态检查】数据读取完毕，总长度: ${buffer.size()}")
                                return@withContext buffer.toByteArray()
                            }
                        }
                    } else {
                        // 没有数据，等待重试
                        // 注意：这里不尝试阻塞读取，防止在某些设备上线程卡死
                        delay(50)
                    }
                }
                Log.w(TAG, "【状态检查】读取循环超时 (${timeoutMs}ms)")
            } catch (e: Exception) {
                Log.w(TAG, "【状态检查】读取状态异常: ${e.message}")
            }

            if (buffer.size() > 0) {
                Log.d(TAG, "【状态检查】超时退出，返回已读数据: ${buffer.size()} bytes")
                buffer.toByteArray()
            } else {
                Log.w(TAG, "【状态检查】超时退出，无数据")
                null
            }
        }
    }

    private fun drainInputStream(inputStream: InputStream) {
        try {
            while (inputStream.available() > 0) {
                val skip = ByteArray(minOf(inputStream.available(), 256))
                inputStream.read(skip)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 全面检查打印机状态（生成诊断报告）
     * 委托给 PrinterDiagnosticTool 处理
     */
    override suspend fun checkPrinterStatusFull(config: PrinterConfig): String {
        // 1. 基础状态检查
        val currentStatus = getPrinterStatus(config)
        if (currentStatus != PrinterStatus.CONNECTED) {
            return "❌ 状态错误: 显示为未连接 (当前状态: ${currentStatus.name})"
        }

        // 2. 使用诊断工具运行测试
        return PrinterDiagnosticTool.runFullDiagnostics(
            config = config,
            connection = currentConnection,
            isConnectedChecker = { currentConnection?.isConnected == true }
        )
    }

    private fun mergeStatusDetails(primary: String?, trace: String?): String? {
        val parts = listOfNotNull(
            primary?.takeIf { it.isNotBlank() },
            trace?.takeIf { it.isNotBlank() }
        )
        return if (parts.isEmpty()) null else parts.joinToString("\n")
    }

    private data class EscPosStatusQuery(
        val id: String,
        val description: String,
        val command: ByteArray,
        val parser: (ByteArray) -> PrinterConnectionCheckResult?,
        val timeoutMs: Long = STATUS_QUERY_TIMEOUT_MS
    )

    private val escPosStatusQueries = listOf(
        // DLE EOT 2: 最核心的状态查询指令 (脱机、缺纸、开盖)
        // 包含: 打印机脱机、机盖打开、按住进纸键、检测到缺纸、错误状态等
        // 这一条指令通常足以覆盖95%的日常状态监控需求
        EscPosStatusQuery(
            id = "DLE EOT 2",
            description = "脱机/缺纸状态",
            command = byteArrayOf(0x10, 0x04, 0x02),
            parser = this::parseOfflineStatus
        ),
        // DLE EOT 3: 辅助查询不可恢复错误 (切刀堵塞等)
        // 当 DLE EOT 2 报告 Error 时，此指令可提供更具体的硬件错误信息
        EscPosStatusQuery(
            id = "DLE EOT 3",
            description = "硬件错误状态",
            command = byteArrayOf(0x10, 0x04, 0x03),
            parser = this::parseErrorStatus
        ),
        // DLE EOT 4: 纸张传感器状态 (纸将尽预警)
        // 提供"纸将尽" (Near End) 的早期警告，避免打印中途缺纸
        EscPosStatusQuery(
            id = "DLE EOT 4",
            description = "纸张传感器状态",
            command = byteArrayOf(0x10, 0x04, 0x04),
            parser = this::parsePaperStatus
        )
        // 移除 DLE EOT 1 (基础状态): 信息价值低，多为钱箱引脚状态
        // 移除 GS r 1 (ASB): 避免主动状态回传干扰一问一答逻辑
        // 移除 GS I 0 (设备信息): 响应慢且变长，严禁在心跳轮询中使用
    )

    private fun parsePrinterGeneralStatus(response: ByteArray): PrinterConnectionCheckResult? {
        if (response.isEmpty()) return null
        val value = response[0].toInt() and 0xFF
        val notices = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (value and 0x01 != 0) {
            notices += "钱箱引脚3为高电平"
        }
        if (value and 0x02 != 0) {
            notices += "钱箱引脚2为高电平"
        }
        if (value and 0x10 != 0) {
            warnings += "打印机忙或脱机"
        }
        if (value and 0x20 != 0) {
            warnings += "等待恢复上线（面板被暂停）"
        }
        if (value and 0x40 != 0) {
            warnings += "面板正在走纸"
        }
        val hasError = value and 0x80 != 0

        val state = when {
            hasError -> PrinterConnectionState.ERROR
            value and 0x10 != 0 -> PrinterConnectionState.OFFLINE
            warnings.isNotEmpty() -> PrinterConnectionState.WARNING
            else -> PrinterConnectionState.ONLINE
        }

        val detailParts = mutableListOf<String>()
        if (warnings.isNotEmpty()) detailParts += warnings.joinToString("、")
        if (hasError) detailParts += "打印机出现错误"
        if (notices.isNotEmpty()) detailParts += notices.joinToString("、")
        if (response.size > 1) {
            val extraBytes = response.drop(1).joinToString(" ") { String.format("%02X", it) }
            detailParts += "附加字节: $extraBytes"
        }

        val summary = when (state) {
            PrinterConnectionState.ONLINE -> "打印机在线"
            PrinterConnectionState.WARNING -> "打印机在线（存在提示）"
            PrinterConnectionState.OFFLINE -> "打印机忙或脱机"
            PrinterConnectionState.ERROR -> "打印机报告错误"
        }

        return PrinterConnectionCheckResult(
            state = state,
            summary = summary,
            detail = detailParts.takeIf { it.isNotEmpty() }?.joinToString("\n")
        )
    }

    private fun parseOfflineStatus(response: ByteArray): PrinterConnectionCheckResult? {
        if (response.isEmpty()) return null
        val value = response[0].toInt() and 0xFF
        val issues = mutableListOf<String>()
        
        // ESC/POS DLE EOT 2 状态位解析
        // 0x12 (0001 0010) = 正常 (Bit 1=1, Bit 4=1 均为固定位)
        
        // Bit 0: 0 (Fixed)
        // Bit 1: 1 (Fixed) - 注意：旧代码曾错误地将其解析为机盖打开
        // Bit 2: 机盖状态 (0=合上, 1=打开) - 标准打印机使用此位
        // Bit 3: 进纸键 (0=未按, 1=按下)
        // Bit 4: 1 (Fixed)
        // Bit 5: 缺纸 (0=有纸, 1=缺纸) - GLPrinter 等部分机型用此位同时表示缺纸或开盖
        // Bit 6: 错误 (0=无, 1=有)
        // Bit 7: 0 (Fixed)
        
        // 兼容性检测逻辑
        if (value and 0x04 != 0) issues += "机盖打开" // 适配标准设备
        if (value and 0x08 != 0) issues += "进纸键被按下"
        if (value and 0x20 != 0) issues += "检测到缺纸/开盖" // 适配 GLPrinter (0x32) 及标准设备的缺纸
        if (value and 0x40 != 0) issues += "打印机错误"
        
        // 严格的离线判断 (Bit 2, 5, 6 任意一个置位即视为离线/异常)
        val isOffline = (value and 0x04 != 0) || (value and 0x20 != 0) || (value and 0x40 != 0)

        val state = when {
            isOffline -> PrinterConnectionState.OFFLINE
            issues.isNotEmpty() -> PrinterConnectionState.WARNING
            else -> PrinterConnectionState.ONLINE
        }

        val summary = when (state) {
            PrinterConnectionState.ONLINE -> "打印机在线"
            PrinterConnectionState.WARNING -> "打印机在线但存在警告"
            PrinterConnectionState.OFFLINE -> "打印机处于脱机状态"
            PrinterConnectionState.ERROR -> "打印机异常"
        }

        return PrinterConnectionCheckResult(
            state = state,
            summary = summary,
            detail = issues.takeIf { it.isNotEmpty() }?.joinToString("、")
        )
    }

    private fun parseErrorStatus(response: ByteArray): PrinterConnectionCheckResult? {
        if (response.isEmpty()) return null
        val value = response[0].toInt() and 0xFF
        if (value == 0) {
            return PrinterConnectionCheckResult(
                state = PrinterConnectionState.ONLINE,
                summary = "未检测到打印机错误"
            )
        }

        val issues = mutableListOf<String>()
        if (value and 0x01 != 0) issues += "存在可恢复错误"
        if (value and 0x02 != 0) issues += "切刀错误"
        if (value and 0x04 != 0) issues += "不可恢复错误"
        if (value and 0x08 != 0) issues += "需要自动恢复"

        val state = if (value and 0x04 != 0) PrinterConnectionState.ERROR else PrinterConnectionState.WARNING
        val summary = if (state == PrinterConnectionState.ERROR) "检测到打印机错误" else "打印机返回警告"

        return PrinterConnectionCheckResult(
            state = state,
            summary = summary,
            detail = issues.joinToString("、")
        )
    }

    private fun parsePaperStatus(response: ByteArray): PrinterConnectionCheckResult? {
        if (response.isEmpty()) return null
        val value = response[0].toInt() and 0xFF
        val issues = mutableListOf<String>()
        if (value and 0x01 != 0) issues += "纸张将用尽"
        if (value and 0x02 != 0) issues += "缺纸"

        val state = when {
            value and 0x02 != 0 -> PrinterConnectionState.OFFLINE
            value and 0x01 != 0 -> PrinterConnectionState.WARNING
            else -> PrinterConnectionState.ONLINE
        }

        val summary = when (state) {
            PrinterConnectionState.ONLINE -> "纸张状态正常"
            PrinterConnectionState.WARNING -> "纸张即将用尽"
            PrinterConnectionState.OFFLINE -> "打印机缺纸"
            PrinterConnectionState.ERROR -> "未知纸张状态"
        }

        return PrinterConnectionCheckResult(
            state = state,
            summary = summary,
            detail = issues.takeIf { it.isNotEmpty() }?.joinToString("、")
        )
    }

    private fun parseSensorStatus(response: ByteArray): PrinterConnectionCheckResult? {
        if (response.isEmpty()) return null
        val value = response[0].toInt() and 0xFF
        val issues = mutableListOf<String>()
        if (value and 0x01 != 0) issues += "前传感器：纸将用尽"
        if (value and 0x02 != 0) issues += "前传感器：缺纸"
        if (value and 0x04 != 0) issues += "后传感器：纸将用尽"
        if (value and 0x08 != 0) issues += "后传感器：缺纸"

        val state = when {
            value and 0x02 != 0 || value and 0x08 != 0 -> PrinterConnectionState.OFFLINE
            value and 0x01 != 0 || value and 0x04 != 0 -> PrinterConnectionState.WARNING
            else -> PrinterConnectionState.ONLINE
        }

        val summary = when (state) {
            PrinterConnectionState.ONLINE -> "传感器状态正常"
            PrinterConnectionState.WARNING -> "传感器提示纸张不足"
            PrinterConnectionState.OFFLINE -> "传感器检测到缺纸"
            PrinterConnectionState.ERROR -> "传感器异常"
        }

        return PrinterConnectionCheckResult(
            state = state,
            summary = summary,
            detail = issues.takeIf { it.isNotEmpty() }?.joinToString("、")
        )
    }

    private fun parseDeviceInfoStatus(response: ByteArray): PrinterConnectionCheckResult? {
        if (response.isEmpty()) return null
        val text = runCatching { String(response, Charsets.UTF_8).trim() }.getOrDefault("")
        if (text.isBlank()) return null
        return PrinterConnectionCheckResult(
            state = PrinterConnectionState.ONLINE,
            summary = "打印机返回设备信息",
            detail = text
        )
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { String.format("%02X", it) }

    private fun ByteArray.toDecimalString(): String =
        joinToString(" ") { ((it.toInt() and 0xFF)).toString() }

    /**
     * 最小走纸（1-2行），用于唤醒打印机
     */
    override suspend fun feedPaperMinimal(config: PrinterConfig, lines: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val safeLines = lines.coerceIn(1, 2)
            val vendor = getVendorForAddress(config.address)
            if (vendor == PrinterVendor.STAR) {
                if (!ensureStarConnected(config)) return@withContext false
                return@withContext starDriver.feedMinimal(safeLines)
            }

            if (!ensurePrinterConnected(config)) return@withContext false
            val connection = currentConnection ?: return@withContext false

            try {
                if (!connection.isConnected()) connection.connect()

                // 按照正式打印流程的唤醒顺序来推进
                connection.write(byteArrayOf(0x1B, 0x40)) // ESC @ 初始化
                Thread.sleep(80)

                connection.write(byteArrayOf(0x18)) // CAN 清缓冲
                Thread.sleep(60)

                connection.write(byteArrayOf(0x1B, 0x64, safeLines.toByte())) // ESC d n 行走纸
                Thread.sleep(60)

                // 利用格式化打印（与真实打印同路径）输出一个空行，确保缓冲刷新
                runCatching { currentPrinter?.printFormattedText(" \n") }.onFailure {
                    Log.w(TAG, "最小走纸触发打印失败: ${it.message}")
                }

                Log.d(TAG, "最小走纸完成（${safeLines}行）")
                true
            } catch (e: Exception) {
                Log.e(TAG, "最小走纸失败: ${e.message}", e)
                updatePrinterStatus(config, PrinterStatus.ERROR)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "最小走纸异常: ${e.message}", e)
            false
        }
    }

    /**
     * 尝试连接指定地址的蓝牙设备
     * @param deviceAddress 蓝牙设备地址
     * @return 连接结果
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
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
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
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
    private suspend fun scanForDevice(targetAddress: String): BluetoothDevice? =
        suspendCancellableCoroutine { continuation ->
            // 在外部定义变量引用，但不初始化
            var receiver: BroadcastReceiver? = null

            try {
                Log.d(TAG, "开始扫描特定地址的设备: $targetAddress")

                // 取消当前的扫描
                val adapter = getBluetoothAdapter()
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                }

                // 尝试从已配对设备中找到目标设备
                val pairedDevices = getPairedDevices()
                val deviceFromPaired =
                    pairedDevices.find { device -> device.address == targetAddress }
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
                                val device =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE,
                                            BluetoothDevice::class.java
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                    }

                                device?.let {
                                    if (it.address == targetAddress) {
                                        // 找到目标设备，停止扫描
                                        Log.d(TAG, "在扫描中找到目标设备: ${it.name}")
                                        this@BluetoothPrinterManager.getBluetoothAdapter()?.cancelDiscovery()

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
                adapter?.startDiscovery() ?: run {
                    Log.e(TAG, "蓝牙适配器为null，无法开始扫描")
                    context.unregisterReceiver(receiver)
                    continuation.resume(null)
                }

                // 添加取消时的清理操作
                continuation.invokeOnCancellation {
                    try {
                        if (receiver != null) context.unregisterReceiver(receiver)
                        getBluetoothAdapter()?.cancelDiscovery()
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
            if (status == PrinterStatus.CONNECTED) {
                ensureHeartbeatRunning(config)
            }
        }
    }

    /**
     * 检查是否有蓝牙权限
     * @return 是否有蓝牙权限
     */
    private fun hasBluetoothPermission(): Boolean {
        
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
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ) {
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

    /**
     * 执行打印测试
     * @param config 打印机配置
     * @return 测试是否成功
     */
    override suspend fun printTest(config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "执行测试打印")

            // Star 路径
            if (getVendorForAddress(config.address) == PrinterVendor.STAR) {
                if (!ensureStarConnected(config)) {
                    Log.e(TAG, "【STAR】打印机连接失败，无法执行测试打印")
                    return@withContext false
                }
                val ok = starDriver.printTest()
                if (!ok) Log.e(TAG, "【STAR】测试打印失败")
                return@withContext ok
            }

            // 1. 检查并确保连接
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "打印机连接失败，无法执行测试打印")
                return@withContext false
            }

            // 2. 创建测试订单对象
            val testOrder = templateManager.createTestOrder(config)

            // 3. 使用正常的订单打印流程，这样可以享受完整的切纸逻辑
            val success = printOrder(testOrder, config)

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

                // 空行保持为空行，不添加多余的空格
                if (trimmedLine.isEmpty()) {
                    fixedLines.add("")
                    continue
                }

                // 检查行是否有对齐标记
                val hasAlignmentTag = trimmedLine.startsWith("[L]") ||
                        trimmedLine.startsWith("[C]") ||
                        trimmedLine.startsWith("[R]")

                // 如果没有对齐标记，添加左对齐标记
                val lineWithTag = if (!hasAlignmentTag) "[L]$trimmedLine" else trimmedLine
                fixedLines.add(lineWithTag)
            }

            // 合并修复后的行
            return fixedLines.joinToString("\n")

        } catch (e: Exception) {
            Log.e(TAG, "修复内容格式失败: ${e.message}")
            return "[L]打印内容解析错误，请检查格式"
        }
    }

    /**
     * 打印直接切纸测试
     * 直接发送切纸命令测试打印机切纸功能
     */
    suspend fun testDirectCut(config: PrinterConfig): Boolean {
        try {
            
            
            // 确保连接
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "无法连接打印机")
                return false
            }
            
            // 初始化打印机
            val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
            currentConnection?.write(initCommand)
            Thread.sleep(100)
            
            // 打印测试内容
            val testText = """
                [C]切纸测试
                [C]------------
                [L]如果您看到这段文字
                [L]说明打印功能工作正常
                [L]下面将测试切纸功能
                [C]------------
                
                
            """.trimIndent()
            
            currentPrinter?.printFormattedText(testText)
            Thread.sleep(1000)  // 给打印机更多时间处理文本
            
            // 使用强制切纸选项和额外走纸
            val result = executeUnifiedPaperCut(
                config.copy(autoCut = true),
                forceCut = true
            )
            
            Log.d(TAG, "切纸测试完成，结果: ${if (result) "成功" else "失败"}")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "切纸测试失败: ${e.message}")
            return false
        }
    }

    /**
     * 测试80mm打印机切纸功能
     * 专门针对80mm打印机的切纸测试，尝试更多可能的切纸命令和组合
     * 
     * @param config 打印机配置
     * @return 测试是否成功执行
     */
    /* suspend fun test80mmPrinterCut(config: PrinterConfig): Boolean {
        try {
            
            
            // 确保连接
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "无法连接打印机")
                return false
            }
            
            // 打印提示信息
            val testText = """
                [C]<b>80MM打印机切纸测试</b>
                [C]==================
                [L]将尝试多种切纸命令
                [L]如果任一命令生效，纸张将被切断
                [L]适用于:
                [L]- 80mm热敏打印机
                [L]- ESC/POS指令集打印机
                [C]------------------
                
                
                
            """.trimIndent()
            
            // 打印测试标题
            currentPrinter?.printFormattedText(testText)
            Thread.sleep(1000)  // 等待打印完成
            
            // 大量走纸，避免卡纸
            val feedCommand = byteArrayOf(0x1B, 0x64, 30)  // ESC d 30 - 走纸30行
            currentConnection?.write(feedCommand)
            Thread.sleep(800)  // 给予足够时间走纸
            
            // 80mm打印机的特定切纸命令
            val specificCommands = listOf(
                // GS V系列命令 - 最常用的切纸命令
                Pair(byteArrayOf(0x1D, 0x56, 0x00), "GS V 0 (完全切纸)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x01), "GS V 1 (部分切纸)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x30), "GS V 48 (0x30, 十进制格式)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x31), "GS V 49 (0x31, 十进制格式)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x41, 0x00), "GS V A 0 (切纸前不走纸)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x41, 0x40), "GS V A 64 (切纸前走纸64点)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x42, 0x00), "GS V B 0 (切纸前不走纸)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x42, 0x50), "GS V B 80 (切纸前走纸80点)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x61, 0x00), "GS V a 0 (十进制形式)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x61, 0x01), "GS V a 1 (十进制形式)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x61, 0x01, 0x00), "GS V a 1 0 (位置参数)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x65, 0x00), "GS V e 0 (特殊切纸模式)"),
                Pair(byteArrayOf(0x1D, 0x56, 0x6D), "GS V m (厂商特定)"),
                
                // ESC系列命令 - 一些品牌特有
                Pair(byteArrayOf(0x1B, 0x69), "ESC i (部分切纸)"),
                Pair(byteArrayOf(0x1B, 0x6D), "ESC m (完全切纸)"),
                Pair(byteArrayOf(0x1B, 0x4D), "ESC M (部分切纸变体)"),
                
                // 原始ASCII形式
                Pair(byteArrayOf(27, 105), "ESC i (ASCII)"),
                Pair(byteArrayOf(27, 109), "ESC m (ASCII)"),
                Pair(byteArrayOf(29, 86, 0), "GS V NUL (ASCII)"),
                Pair(byteArrayOf(29, 86, 1), "GS V SOH (ASCII)"),
                Pair(byteArrayOf(29, 86, 48), "GS V 0 (ASCII 十进制)"),
                Pair(byteArrayOf(29, 86, 49), "GS V 1 (ASCII 十进制)"),
                
                // 特殊组合 - 清除+初始化+走纸+切纸
                Pair(byteArrayOf(0x18, 0x1B, 0x40, 0x1B, 0x64, 0x10, 0x1D, 0x56, 0x01), "复合命令1: 清除+初始化+走纸+切纸"),
                Pair(byteArrayOf(0x1B, 0x40, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x01), "复合命令2: 初始化+5LF+切纸"),
                
                
            )
            
            var commandIndex = 1
            for ((command, description) in specificCommands) {
                try {
                    // 先打印当前测试的命令描述
                    val descText = """
                        [C]测试命令 #$commandIndex
                        [L]$description
                        [C]- - - - - - - -
                        
                    """.trimIndent()
                    
                    currentPrinter?.printFormattedText(descText)
                Thread.sleep(300)
                    
                    // 走纸
                    currentConnection?.write(byteArrayOf(0x1B, 0x64, 0x08))  // 走纸8行
                    Thread.sleep(500)
                    
                    // 发送切纸命令
                    
                    currentConnection?.write(command)
                    Thread.sleep(800)  // 给予足够时间执行命令
                    
                    commandIndex++
                } catch (e: Exception) {
                    Log.e(TAG, "【80mm切纸测试】命令执行失败: $description - ${e.message}")
                }
            }
            
            // 最后进行一次大走纸，确保命令执行完
            currentConnection?.write(byteArrayOf(0x1B, 0x64, 0x20))  // 走纸32行
            Thread.sleep(1000)
            
            // 尝试多种切纸命令，增加成功率
            
            
            // 1. 标准切纸命令 (GS V)
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x01))  // GS V 1 - 部分切纸
            
            // 2. 带参数切纸命令 (GS V A)
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))  // GS V A 16 - 带走纸的切纸
            
            // 3. ESC指令集切纸 (某些品牌使用)
            currentConnection?.write(byteArrayOf(0x1B, 0x69))  // ESC i
            
            // 4. 发送小走纸指令以触发打印机缓冲处理
            currentConnection?.write(byteArrayOf(0x0A, 0x0D, 0x0A))  // LF CR LF
            
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "【80mm打印机】切纸测试失败: ${e.message}", e)
            return false
        }
    } */

    /**
     * 确保命令立即执行
     * 使用多种技术触发打印机处理所有排队命令
     */
    private fun ensureCommandExecution() {
        try {
            if (currentConnection == null) {
                return
            }
            
            Log.d(TAG, "【打印机】强制执行所有命令")
            
            // 1. 发送状态请求命令
            currentConnection?.write(byteArrayOf(0x10, 0x04, 0x01))  // DLE EOT 1
            Thread.sleep(20)
            
            // 2. 发送进纸命令
            currentConnection?.write(byteArrayOf(0x0A))  // LF
            Thread.sleep(20)
            
            // 3. 发送紧急处理命令 (部分打印机支持)
            currentConnection?.write(byteArrayOf(0x10, 0x14, 0x08))  // DLE DC4 8 - 清除缓冲区
            Thread.sleep(20)
            
            // 4. 重置打印机 - 通常会执行队列中的所有命令
            currentConnection?.write(byteArrayOf(0x1B, 0x40))  // ESC @
            Thread.sleep(50)
        } catch (e: Exception) {
            Log.e(TAG, "强制执行命令失败: ${e.message}")
        }
    }

    /**
     * 强制切纸方法
     * 当遇到切纸命令不立即执行的情况，调用此方法可以尝试用多种组合命令强制执行切纸
     * 
     * @param config 打印机配置
     * @return 切纸操作是否成功执行
     */
    fun forcePaperCut(@Suppress("UNUSED_PARAMETER") config: PrinterConfig): Boolean {
        try {
            
            
            if (currentConnection == null) {
                Log.e(TAG, "【打印机】无有效连接，无法执行强制切纸")
                return false
            }
            
            // 第一步：清除打印缓冲区和初始化打印机
            currentConnection?.write(byteArrayOf(0x18))  // CAN - 清除打印缓冲区
            Thread.sleep(100)
            currentConnection?.write(byteArrayOf(0x1B, 0x40))  // ESC @ - 初始化打印机
            Thread.sleep(100)
            
            // 第二步：多次走纸确保纸张位置正确
            for (i in 1..3) {
                currentConnection?.write(byteArrayOf(0x1B, 0x64, 8.toByte()))  // ESC d 8 - 走纸8行
                Thread.sleep(100)
            }
            
            // 第三步：尝试不同的切纸命令组合
            
            // 组合1：GS V 0 - 全切
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x00))
            Thread.sleep(200)
            
            // 组合2：GS V 1 - 部分切纸
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x01))
            Thread.sleep(200)
            
            // 组合3：GS V 65 - 带走纸的切纸
            currentConnection?.write(byteArrayOf(0x1D, 0x56, 65.toByte(), 30.toByte()))
            Thread.sleep(200)
            
            // 组合4：ESC i - 部分切纸 (EPSON)
            currentConnection?.write(byteArrayOf(0x1B, 0x69))
            Thread.sleep(200)
            
            // 组合5：ESC m - 部分切纸 (EPSON)
            currentConnection?.write(byteArrayOf(0x1B, 0x6D))
            Thread.sleep(200)
            
            // 第四步：发送虚拟打印任务激活切纸命令
            Log.d(TAG, "【打印机】发送虚拟打印任务以触发切纸命令执行")
            // 发送多个空格和换行作为触发，使用GB18030编码
            val emptyContent = "      ".toByteArray(charset("GBK"))
            currentConnection?.write(emptyContent)
            
            // 多个换行确保命令被处理
            for (i in 1..5) {
                currentConnection?.write(byteArrayOf(0x0A))
                Thread.sleep(50)
            }
            
            // 第五步：再次初始化打印机
            currentConnection?.write(byteArrayOf(0x1B, 0x40))
            
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【打印机】强制切纸操作失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 中文字符测试打印
     */
    override suspend fun printChineseTest(config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "【中文测试】开始中文字符测试打印")

            // 1. 检查并确保连接 - 与testPrint使用相同的连接逻辑
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "【中文测试】打印机连接失败，无法执行中文测试打印")
                return@withContext false
            }

            // 2. 创建中文测试订单对象 - 使用与testPrint相同的方法
            val chineseTestOrder = templateManager.createChineseTestOrder(config)

            // 3. 使用正常的订单打印流程 - 享受完整的缓冲区管理和切纸逻辑
            val success = printOrder(chineseTestOrder, config)

            if (success) {
                Log.d(TAG, "【中文测试】中文测试打印成功")
            } else {
                Log.e(TAG, "【中文测试】中文测试打印失败")
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "【中文测试】中文测试打印异常: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 检测文本是否包含中文字符
     */
    private fun containsChineseCharacters(text: String): Boolean {
        // 先移除格式标记，避免误判
        val cleanText = text.replace(Regex("\\[L\\]|\\[C\\]|\\[R\\]|<[^>]*>"), "")
        
        return cleanText.any { char ->
            // 检查是否为中文字符（包括CJK统一汉字、中文标点符号等）
            char.code in 0x4E00..0x9FFF || // CJK统一汉字
            char.code in 0x3400..0x4DBF || // CJK扩展A
            char.code in 0x3000..0x303F || // CJK符号和标点
            char.code in 0xFF00..0xFFEF || // 全角ASCII、全角标点符号
            char.code in 0xFE30..0xFE4F || // CJK兼容形式
            char.code in 0x2E80..0x2EFF || // CJK部首补充
            char.code in 0x31C0..0x31EF || // CJK笔画
            char == '￥' || char == '￿'     // 常见中文符号
        }.also { result ->
            if (result) {
                Log.d(TAG, "【中文检测】检测到中文字符: ${cleanText.take(20)}...")
            }
        }
    }

    /**
     * 使用GB18030编码发送内容
     */
    private suspend fun sendContentWithGBKEncoding(content: String) {
        try {
            val connection = currentConnection ?: return
            
            Log.d(TAG, "【GBK编码】开始处理中文内容，总长度: ${content.length}")
            
            // 先设置中文模式
            setupChineseMode(connection)
            
            // 逐行处理内容
            val lines = content.split("\n")
            Log.d(TAG, "【GBK编码】分解为 ${lines.size} 行")
            val outputStream = ByteArrayOutputStream()
            
            for ((index, line) in lines.withIndex()) {
                Log.d(TAG, "【GBK编码】处理第${index + 1}行: \"$line\"")
                
                // 使用我们现有的格式化处理方法，它支持GB18030编码
                processFormattedLine(line, outputStream)
                outputStream.write(byteArrayOf(0x0A)) // 添加换行
                
                Log.d(TAG, "【GBK编码】第${index + 1}行处理完成")
            }
            
            // 发送处理好的内容
            val data = outputStream.toByteArray()
            Log.d(TAG, "【GBK编码】准备发送数据，大小: ${data.size}字节")
            connection.write(data)
            Log.d(TAG, "【GBK编码】数据已发送到连接")
            
            // 立即强制刷新，确保内容被发送到打印机
            forceImmediateFlush(connection)
            
            Log.d(TAG, "【GBK编码】中文内容处理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "【GBK编码】发送中文内容失败: ${e.message}", e)
        }
    }

    /**
     * 设置中文模式
     */
    private suspend fun setupChineseMode(connection: BluetoothConnection) {
        try {
            Log.d(TAG, "【中文模式】设置中文字符模式")
            
            // 使用之前成功的双重中文模式设置策略
            // 取消默认中文模式
            connection.write(byteArrayOf(0x1C, 0x2E)) // FS . - Cancel Chinese mode
            delay(50)
            
            // 重新启用正确的中文字符模式  
            connection.write(byteArrayOf(0x1C, 0x26)) // FS & - Set Chinese Character Mode
            delay(50)
            
            // 为确保中文模式下字体控制命令正常工作，增加兼容性设置
            // 设置打印机为混合模式，同时支持中文和字体控制
            connection.write(byteArrayOf(0x1B, 0x40)) // ESC @ - 初始化打印机
            delay(50)
            
            // 再次设置中文字符模式
            connection.write(byteArrayOf(0x1C, 0x26)) // FS & - Set Chinese Character Mode
            delay(50)
            
            Log.d(TAG, "【中文模式】中文字符模式和字体兼容性设置完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "【中文模式】设置中文模式失败: ${e.message}")
        }
    }

    /**
     * 立即强制刷新 - 确保内容立即输出到打印机
     */
    private suspend fun forceImmediateFlush(connection: BluetoothConnection) {
        try {
            Log.d(TAG, "【立即刷新】强制立即输出内容")
            
            // 发送多种立即输出命令
            // 1. 实时状态查询，强制缓冲区刷新
            connection.write(byteArrayOf(0x10, 0x04, 0x01)) // DLE EOT 1
            delay(20)
            
            // 2. 立即输出当前缓冲区
            connection.write(byteArrayOf(0x0A)) // LF
            delay(10)
            
            // 3. 强制表单进纸
            connection.write(byteArrayOf(0x0C)) // FF
            delay(20)
            
            Log.d(TAG, "【立即刷新】立即输出序列完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "【立即刷新】立即刷新失败: ${e.message}")
        }
    }

}
