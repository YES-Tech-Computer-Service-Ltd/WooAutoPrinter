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
import kotlin.math.max

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
    private val bluetoothAdapter: BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
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
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .toInt()

                        // 保存设备，不过滤任何设备
                        discoveredDevices[it.address] = it

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
                    val prevBondState = intent.getIntExtra(
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
                val prevBondState = intent.getIntExtra(
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

    override suspend fun connect(config: PrinterConfig): Boolean {
        try {
//            Log.d(TAG, "【打印机连接】尝试连接打印机: ${config.name} (${config.address})")

            // 获取蓝牙设备
            val device = getBluetoothDevice(config.address)
            if (device == null) {
                Log.e(TAG, "未找到打印机设备: ${config.address}")
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return false
            }

            // 如果不是当前配置的打印机，则断开原连接
            if (currentPrinterConfig != null && currentPrinterConfig?.id != config.id) {
                disconnect(currentPrinterConfig!!)
            }

            // 如果已经连接，直接返回
            if (getPrinterStatus(config) == PrinterStatus.CONNECTED) {
                return true
            }

            // 检查连接是否有效
            if (currentConnection?.isConnected == true && currentPrinterConfig?.id == config.id) {
                updatePrinterStatus(config, PrinterStatus.CONNECTED)
                return true
            }

            // 直接进行连接尝试，而不是调用testPrinterConnection
            // 创建蓝牙连接
            val connection = BluetoothConnection(device)

            // 连接到打印机
            updatePrinterStatus(config, PrinterStatus.CONNECTING)
            
            // 尝试连接
            val isConnected = try {
                connection.connect()
                true
            } catch (e: Exception) {
                Log.e(TAG, "打印机连接失败: ${e.message}", e)
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return false
            }

            if (!isConnected) {
                Log.e(TAG, "无法建立打印机连接")
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
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

                // 启动心跳检测
                startHeartbeat(config)

                // 更新状态为已连接
                updatePrinterStatus(config, PrinterStatus.CONNECTED)

                // 保存当前打印机配置
                currentPrinterConfig = config
                
                return true
            } catch (e: Exception) {
                Log.e(TAG, "创建打印机实例失败: ${e.message}", e)
                updatePrinterStatus(config, PrinterStatus.ERROR)
                connection.disconnect()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接打印机异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.ERROR)
            return false
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
                } else {
                    outputStream.write(byteArrayOf(0x1B, 0x21, 0x10))  // 只双倍高度
                }
                isDoubleHeight = true
            }

            // 清除标签
            text = text.replace("<h>", "").replace("</h>", "")
        }

        // 写入纯文本内容
        outputStream.write(text.toByteArray(Charsets.UTF_8))

        // 重置格式
        if (isBold) {
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00))  // ESC E 0 - 关闭加粗
        }

        if (isDoubleWidth || isDoubleHeight) {
            outputStream.write(byteArrayOf(0x1B, 0x21, 0x00))  // ESC ! 0 - 重置字体大小
        }
    }

    /**
     * 发送Star专用切纸命令
     */
    private fun sendStarCutCommand() {
        try {
            Log.d(TAG, "【Star TSP100】发送切纸命令")

            // 添加线切纸命令
            currentConnection?.write(byteArrayOf(0x1B, 0x64, 0x02))
            Thread.sleep(200)

            Log.d(TAG, "【Star TSP100】切纸命令发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "【Star TSP100】发送切纸命令失败: ${e.message}")
        }
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
        additionalFeed: Boolean = true
    ): Boolean {
        try {
            // 检查是否应该切纸
            if (!forceCut && !config.autoCut) {
                Log.d(TAG, "【打印机】自动切纸已禁用，跳过切纸命令")
                return false
            }
            
            if (currentConnection == null) {
                Log.e(TAG, "【打印机】无有效连接，无法执行切纸")
                return false
            }
            
            // 记录切纸开始
            Log.d(TAG, "【打印机切纸】开始执行切纸, 打印机类型: ${config.brand.displayName}, 纸宽: ${config.paperWidth}mm")
            
            // 优先使用打印库执行切纸
            if (currentPrinter != null) {
                try {
                    // 先清理打印缓冲区
                    val clearCommand = byteArrayOf(0x18)  // CAN
                    currentConnection?.write(clearCommand)
                    Thread.sleep(50)
                    
                    // 如果需要额外走纸
                    if (additionalFeed) {
                        currentPrinter?.printFormattedText("[L]\n\n\n")
                        Thread.sleep(100)
                    }
                    
                    // 使用打印库切纸
                    currentPrinter?.cut()
                    Log.d(TAG, "【打印机切纸】使用打印库执行切纸")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "【打印机切纸】使用打印库切纸失败: ${e.message}")
                    // 继续尝试使用原始命令
                }
            }
            
            // 如果打印库切纸失败，使用原始命令
            
            // 确保执行切纸前没有其他命令在缓冲区中
            try {
                // 初始清除和重置
                val clearCommand = byteArrayOf(0x18)  // CAN - 清除打印缓冲区
                currentConnection?.write(clearCommand)
                Thread.sleep(100)
                
                val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @ - 初始化打印机
                currentConnection?.write(initCommand)
                Thread.sleep(100)
            } catch (e: Exception) {
                Log.e(TAG, "【打印机】切纸前清理缓冲区失败: ${e.message}")
                // 继续执行，不要因为这个错误中断
            }
            
            // 2. 添加额外走纸，确保有足够空间切纸
            if (additionalFeed) {
                // 简单的走纸命令
                val feedLines = 10 // 适中的走纸量
                val feedCommand = byteArrayOf(0x1B, 0x64, feedLines.toByte())  // ESC d n
                currentConnection?.write(feedCommand)
                Thread.sleep(100)
            }
            
            // 3. 执行切纸命令 - 根据打印机品牌和型号选择合适命令
            
            // 简单的切纸命令 - 只发送最常用的几种
            val cutCommands = mutableListOf(
                Pair(byteArrayOf(0x1D, 0x56, 0x01), "GS V 1 (部分切纸)")
            )
            
            // 如果是80mm打印机，添加一种额外的命令
            if (config.paperWidth >= 80) {
                cutCommands.add(Pair(byteArrayOf(0x1D, 0x56, 0x41, 0x10), "GS V A 16 (带走纸的切纸)"))
            }
            
            // 依次执行切纸命令
            var successCount = 0
            for ((command, description) in cutCommands) {
                try {
                    currentConnection?.write(command)
                    Log.d(TAG, "【打印机切纸】发送命令: $description")
                    Thread.sleep(100) // 短暂等待
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "【打印机切纸】命令失败: $description - ${e.message}")
                }
            }
            
            // 如果是Star品牌打印机，添加专用命令
            if (config.brand == PrinterBrand.STAR) {
                try {
                    val starCut = byteArrayOf(0x1B, 0x64, 0x02) // ESC d 2
                    currentConnection?.write(starCut)
                    Log.d(TAG, "【打印机切纸】发送Star专用切纸命令")
                    successCount++
                } catch (e: Exception) {
                    Log.e(TAG, "【打印机切纸】Star专用命令失败: ${e.message}")
                }
            }
            
            // 最后添加换行，确保下次打印开始位置正确
            currentConnection?.write(byteArrayOf(0x0A, 0x0A))
            
            Log.d(TAG, "【打印机切纸】切纸操作完成，发送 $successCount 个命令")
            return successCount > 0
            
        } catch (e: Exception) {
            Log.e(TAG, "【打印机切纸】切纸操作失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 发送通用切纸命令
     * 根据打印机品牌和配置决定使用什么切纸命令
     * @param printerConfig 打印机配置
     */
    private fun sendPaperCutCommand(printerConfig: PrinterConfig) {
        // 直接调用统一的切纸方法，保持额外走纸以兼容现有代码
        executeUnifiedPaperCut(printerConfig, forceCut = false, additionalFeed = true)
    }
    
    /**
     * 最终化打印过程
     * 发送最后的走纸和切纸命令
     * @param config 打印机配置
     */
    private fun finalizePrinting(config: PrinterConfig) {
        // 直接调用统一的切纸方法，强制执行切纸(设置forceCut=true)
        executeUnifiedPaperCut(config, forceCut = true, additionalFeed = true)
    }
    
    // 保留旧的发送额外走纸和切纸命令方法，用于兼容其他地方的调用
    private fun sendExtraPaperFeedCommands() {
        try {
            if (currentConnection == null) {
                Log.e(TAG, "【打印机】没有有效连接，无法发送额外命令")
                return
            }

            Log.d(TAG, "【打印机】发送额外走纸和切纸命令")

            // 使用当前打印机配置，如果没有则创建一个默认配置，强制启用切纸
            val configWithCut = currentPrinterConfig?.copy(autoCut = true) ?: PrinterConfig(
                name = "Default",
                address = "",
                autoCut = true
            )
            
            // 调用统一的切纸方法
            executeUnifiedPaperCut(configWithCut, forceCut = true, additionalFeed = true)

            Log.d(TAG, "【打印机】额外命令发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "【打印机】发送额外命令失败: ${e.message}")
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
//                Log.d(TAG, "已有扫描正在进行，先停止它")
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
//                Log.d(TAG, "开始蓝牙设备发现: $success")

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

            // 检查设备的配对状态并记录日志
            val bondState = when (device.bondState) {
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
        withContext(Dispatchers.IO) {
            try {
                // 停止心跳机制
                stopHeartbeat()

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
            val printerStatus = getPrinterStatus(config)
//        Log.d(TAG, "【打印机状态】开始打印订单 #${order.number}，当前打印机 ${config.name} 状态: $printerStatus")

            // 检查蓝牙适配器状态
            val bluetoothEnabled = bluetoothAdapter?.isEnabled ?: false
//        Log.d(TAG, "【打印机状态】蓝牙适配器状态: ${if (bluetoothEnabled) "已启用" else "未启用"}")

            // 检查打印机连接和实例
            val hasConnection = currentConnection != null
            val hasPrinter = currentPrinter != null
//        Log.d(TAG, "【打印机状态】连接实例: ${if (hasConnection) "存在" else "不存在"}, 打印机实例: ${if (hasPrinter) "存在" else "不存在"}")

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

                    // 1.5 打印订单前专门清理缓存和重置打印机
                    try {
                        Log.d(TAG, "【打印订单】订单#${order.number} - 打印前彻底重置打印机状态")
                        
                        // 断开再重连打印机，彻底清除打印机状态
                        if (currentConnection != null && currentPrinter != null) {
                            // 先保存当前连接信息
                            val savedConnection = currentConnection
                            
                            // 完全清除打印机状态，通过重置库实例
                            currentPrinter = null
                            
                            // 重置打印机
                            val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                            savedConnection?.write(initCommand)
                            Thread.sleep(100)
                            
                            // 清除缓冲区
                            val clearCommand = byteArrayOf(0x18)  // CAN 
                            savedConnection?.write(clearCommand)
                            Thread.sleep(100)
                            
                            // 重新创建打印机实例
                            try {
                                val dpi = 203 // 通用值
                                val paperWidthMm = config.paperWidth.toFloat()
                                val nbCharPerLine = when (config.paperWidth) {
                                    PrinterConfig.PAPER_WIDTH_57MM -> 32
                                    PrinterConfig.PAPER_WIDTH_80MM -> 42
                                    else -> 32
                                }
                                
                                // 重新创建打印机实例
                                val newPrinter = EscPosPrinter(savedConnection, dpi, paperWidthMm, nbCharPerLine)
                                currentPrinter = newPrinter
                                Log.d(TAG, "【打印订单】已重新创建打印机实例")
                            } catch (e: Exception) {
                                Log.e(TAG, "【打印订单】重新创建打印机实例失败: ${e.message}")
                            }
                        } else {
                            Log.d(TAG, "【打印订单】当前无有效连接，跳过重置步骤")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "【打印订单】重置打印机失败: ${e.message}")
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
                Log.d(TAG, "打印机未连接，测试连接失败")
                return false
            }

            // 用简单内容进行测试
            val testContent = """
                [C]<b>printing test</b>
                [C]----------------
                [L]brand: ${config.brand.displayName}
                [L]address: ${config.address}
                [L]name: ${config.name}
                [L]----------------
                [C]test success
                
                
                
            """.trimIndent()

            // 使用通用方法打印内容
            val success = printContent(testContent, config)

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
//        Log.d(TAG, "打印成功，检查订单 ${order.id} 当前打印状态")

        // 获取最新的订单信息
        val latestOrder = orderRepository.getOrderById(order.id)

        // 只有在订单未被标记为已打印时才进行标记
        if (latestOrder != null && !latestOrder.isPrinted) {
//            Log.d(TAG, "标记订单 ${order.id} 为已打印")
            val markResult = orderRepository.markOrderAsPrinted(order.id)
            if (markResult) {
//                Log.d(TAG, "成功标记订单 ${order.id} 为已打印")
            } else {
                Log.e(TAG, "标记订单 ${order.id} 为已打印-失败")
            }
        } else {
//            Log.d(TAG, "订单 ${order.id} 已被标记为已打印，跳过重复标记")
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
//                Log.d(TAG, "订单 #${order.number} 的最新状态已是已打印，跳过自动打印")
                return false
            }

            // 4. 获取默认打印机配置
            val printerConfig = getDefaultPrinterConfig() ?: return false

            // 5. 检查自动打印设置
            if (!printerConfig.isAutoPrint) {
//                Log.d(TAG, "打印机 ${printerConfig.name} 未启用自动打印功能")
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
        // 检查打印机状态
        val status = getPrinterStatus(config)
//        Log.d(TAG, "【打印机状态】确保打印机连接 - 当前状态: $status，打印机: ${config.name} (${config.address})")

        // 检查连接对象状态
        val connectionExists = currentConnection != null
        val printerExists = currentPrinter != null
//        Log.d(TAG, "【打印机状态】连接对象检查 - connection对象: ${if(connectionExists) "存在" else "不存在"}, printer对象: ${if(printerExists) "存在" else "不存在"}")

        if (status != PrinterStatus.CONNECTED) {
//            Log.d(TAG, "【打印机状态】打印机未连接，尝试连接...")
            
            // 创建原始连接而不是调用connect方法，避免潜在的循环
            try {
                // 获取蓝牙设备
                val device = getBluetoothDevice(config.address)
                if (device == null) {
                    Log.e(TAG, "未找到打印机设备: ${config.address}")
                    updatePrinterStatus(config, PrinterStatus.ERROR)
                    return false
                }

                // 断开现有连接
                if (currentConnection != null) {
                    disconnect(config)
                }

                // 创建新连接
                val connection = BluetoothConnection(device)
                updatePrinterStatus(config, PrinterStatus.CONNECTING)

                // 尝试连接
                val isConnected = try {
                    connection.connect()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "打印机连接失败: ${e.message}", e)
                    updatePrinterStatus(config, PrinterStatus.ERROR)
                    return false
                }

                if (!isConnected) {
                    Log.e(TAG, "无法建立打印机连接")
                    updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                    return false
                }

                // 保存当前连接信息
                currentConnection = connection

                // 创建打印机实例
                try {
                    val dpi = 203 // 通用值
                    val paperWidthMm = config.paperWidth.toFloat()
                    val nbCharPerLine = when (config.paperWidth) {
                        PrinterConfig.PAPER_WIDTH_57MM -> 32
                        PrinterConfig.PAPER_WIDTH_80MM -> 42
                        else -> 32
                    }

                    // 创建打印机实例
                    val printer = EscPosPrinter(connection, dpi, paperWidthMm, nbCharPerLine)
                    currentPrinter = printer

                    // 成功连接后简单初始化打印机
                    try {
                        Log.d(TAG, "【打印机】连接成功，执行基本初始化")
                        
                        // 重置打印机 - 最关键的命令
                        val resetCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                        currentConnection?.write(resetCommand)
                        Thread.sleep(100)
                        
                        // 清除缓冲区
                        val clearCommand = byteArrayOf(0x18)  // CAN
                        currentConnection?.write(clearCommand)
                        Thread.sleep(50)
                    } catch (e: Exception) {
                        // 记录错误但不中断流程
                        Log.e(TAG, "【打印机】初始化失败: ${e.message}")
                    }

                    // 启动心跳检测
                    startHeartbeat(config)

                    // 更新状态为已连接
                    updatePrinterStatus(config, PrinterStatus.CONNECTED)

                    // 保存当前打印机配置
                    currentPrinterConfig = config
                } catch (e: Exception) {
                    Log.e(TAG, "创建打印机实例失败: ${e.message}", e)
                    updatePrinterStatus(config, PrinterStatus.ERROR)
                    currentConnection?.disconnect()
                    currentConnection = null
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接打印机异常: ${e.message}", e)
                updatePrinterStatus(config, PrinterStatus.ERROR)
                return false
            }
        }

        return status == PrinterStatus.CONNECTED || getPrinterStatus(config) == PrinterStatus.CONNECTED
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

            // 添加额外检查，避免发送无效内容到打印机
            if (fixedContent.isBlank()) {
                Log.e(TAG, "修复后的内容仍然为空，无法打印")
                return false
            }

            // 在内容后添加额外的走纸命令
            val contentWithExtra = ensureProperEnding(fixedContent)

            // 保存最后一次打印内容，用于重试
            lastPrintContent = contentWithExtra

            // 开始打印前，简单清除缓冲区并初始化打印机
            try {
                Log.d(TAG, "【打印机】打印前清除缓冲区")
                
                // 初始化打印机 (这是最重要的命令)
                val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                currentConnection?.write(initCommand)
                Thread.sleep(100)
                
                // 清除缓冲区
                val cancelCommand = byteArrayOf(0x18)  // CAN
                currentConnection?.write(cancelCommand)
                Thread.sleep(50)
            } catch (e: Exception) {
                Log.e(TAG, "【打印机】清除缓冲区失败: ${e.message}")
                // 继续尝试打印，不要因为这个错误中断
            }

            // 分块打印内容，解决缓冲区溢出问题
            Log.d(TAG, "开始分块打印内容（总长度: ${contentWithExtra.length}字符）")
            return chunkedPrintingProcess(contentWithExtra, config)
        } catch (e: Exception) {
            // 捕获所有异常，包括解析异常
            Log.e(TAG, "打印机库异常: ${e.message}", e)

            // 如果是解析错误，尝试使用更简单的内容再试一次
            if (e is StringIndexOutOfBoundsException) {
                Log.d(TAG, "检测到解析错误，尝试使用简化内容")

                // 使用更简单的内容格式重试
                val simpleContent = createSimpleContent()

                try {
                    // 初始化打印机
                    val initCommand = byteArrayOf(0x1B, 0x40)  // ESC @
                    currentConnection?.write(initCommand)
                    Thread.sleep(200)
                
                    // 打印简单内容
                    chunkedPrintingProcess(simpleContent, config)
                    
                    Log.d(TAG, "使用简化内容打印成功")
                    return true
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
            Log.d(TAG, "分块打印，总行数: $totalLines")
            
            // 每块最大行数 - 根据行长度可能更少
            val maxChunkLines = 15 
            var currentLine = 0
            
            // 分块打印所有内容
            while (currentLine < totalLines) {
                // 计算当前块的终止行
                val endLine = minOf(currentLine + maxChunkLines, totalLines)
                
                // 提取当前块内容
                val chunkLines = lines.subList(currentLine, endLine)
                val chunkContent = chunkLines.joinToString("\n")
                
                if (chunkContent.isNotBlank()) {
                    Log.d(TAG, "打印内容块 ${currentLine / maxChunkLines + 1}: 行 $currentLine-${endLine-1}")
                    
                    // 使用打印库打印当前块
                    currentPrinter?.printFormattedText(chunkContent)
                    
                    // 每个块之后立即刷新缓冲区，确保完全打印
                    forcePrinterFlush()
                    delay(500) // 给打印机处理时间
                }
                
                // 移动到下一块
                currentLine = endLine
            }
            
            // 确保所有内容都已打印完毕
            forcePrinterFlush()
            delay(100) // 减少等待时间
            
            // 完全重构切纸流程，使用打印库直接切纸而不是发送单独命令
            Log.d(TAG, "所有内容打印完成，准备执行切纸")
            
            try {
                // 先添加额外走纸确保切纸位置正确
                currentPrinter?.printFormattedText("[L]\n\n\n")
                Thread.sleep(100)
                
                // 使用正确的ESC/POS切纸命令
                val cutCommand = byteArrayOf(0x1D, 0x56, 66, 0x00)  // 完全切纸命令
                
                Log.d(TAG, "【打印机】正在发送切纸命令...")
                printerOutputStream?.write(cutCommand)
                printerOutputStream?.flush()
                Thread.sleep(100)
                Log.d(TAG, "【打印机】切纸命令已发送")
            } catch (e: Exception) {
                Log.e(TAG, "【打印机】最终切纸命令失败: ${e.message}")
                // 打印仍然算成功，只是切纸失败
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "分块打印出错: ${e.message}")
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

    /**
     * 确保打印内容有正确的结束（多个换行）
     */
    private fun ensureProperEnding(content: String): String {
        // 如果内容已经有足够的换行符结尾，不需要添加
        if (content.endsWith("\n\n\n")) {
            return content
        }

        // 确保内容以换行结束
        val contentWithNewLine = if (content.endsWith("\n")) content else "$content\n"

        // 添加两个额外的换行
        return "$contentWithNewLine\n\n"
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
                        Log.d(TAG, "打印订单副本 ${i + 1}/${job.copies}: ${job.orderId}")
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
            val device = this.bluetoothAdapter?.getRemoteDevice(address)
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
        // 停止现有心跳任务
        stopHeartbeat()

        // 保存当前打印机配置
        currentPrinterConfig = config

        // 启动新的心跳任务
        heartbeatJob = managerScope.launch {
            try {
                Log.d(TAG, "启动打印机心跳机制，间隔: ${HEARTBEAT_INTERVAL / 1000}秒")
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
//                                    Log.d(TAG, "心跳成功，连接稳定")
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
                                minOf(
                                    RECONNECT_DELAY * (1 shl minOf(reconnectAttempts, 5)),
                                    120000L
                                )
                            } else {
                                0L
                            }

                            if (backoffDelay > 0) {
                                Log.d(
                                    TAG,
                                    "等待重连延迟: ${backoffDelay}ms (尝试次数: ${reconnectAttempts})"
                                )
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
                            e is EscPosConnectionException
                        ) {
                            // 标记连接为断开
                            updatePrinterStatus(config, PrinterStatus.DISCONNECTED)

                            reconnectAttempts++
                            // 使用指数退避策略
                            val backoffDelay = minOf(
                                RECONNECT_DELAY * (1 shl minOf(reconnectAttempts, 5)),
                                120000L
                            )

                            Log.d(
                                TAG,
                                "连接断开，等待 ${backoffDelay}ms 后尝试重连 (尝试次数: $reconnectAttempts)"
                            )
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
                if (ActivityCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
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
                if (this.bluetoothAdapter?.isDiscovering == true) {
                    this.bluetoothAdapter?.cancelDiscovery()
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

            // 检查是否为Star TSP100打印机
            val isStarTSP100 = config.brand == PrinterBrand.STAR &&
                    config.name.contains("TSP100", ignoreCase = true)

            // 1. 检查并确保连接
            if (!ensurePrinterConnected(config)) {
                Log.e(TAG, "打印机连接失败，无法执行测试打印")
                return@withContext false
            }

            // 2. 生成测试打印内容
            val testContent = templateManager.generateTestPrintContent(config)

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
            Log.d(TAG, "开始切纸功能测试")
            
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
                forceCut = true,
                additionalFeed = true
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
    suspend fun test80mmPrinterCut(config: PrinterConfig): Boolean {
        try {
            Log.d(TAG, "【80mm打印机】开始专项切纸测试")
            
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
                
                // STAR打印机特有
                Pair(byteArrayOf(0x1B, 0x64, 0x02), "ESC d 2 (STAR打印机)"),
                Pair(byteArrayOf(0x1B, 0x64, 0x33), "ESC d 3 (STAR变体)")
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
                    Log.d(TAG, "【80mm切纸测试】尝试命令 #$commandIndex: $description")
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
            
            // 尝试多次重复标准切纸命令，增加成功率
            for (i in 1..5) {
                currentConnection?.write(byteArrayOf(0x1D, 0x56, 0x01))  // GS V 1
                Thread.sleep(400)
            }
            
            Log.d(TAG, "【80mm打印机】切纸测试完成，共测试 ${specificCommands.size} 组命令")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "【80mm打印机】切纸测试失败: ${e.message}", e)
            return false
        }
    }
}