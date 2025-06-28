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
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.models.PrinterDevice
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * 支持GB18030编码的打印机管理器
 * 替换原有的EscPosPrinter库，专门处理GB18030编码的中文打印
 */
class GB18030PrinterManager(
    private val context: Context,
    private val scope: CoroutineScope
) : PrinterManager {

    companion object {
        private const val TAG = "GB18030PrinterManager"
        
        // GB18030字符集
        private val GB18030_CHARSET = Charset.forName("GB18030")
        
        // 打印机命令常量
        private const val ESC = 0x1B
        private const val GS = 0x1D
        private const val LF = 0x0A
        private const val CR = 0x0D
        private const val NUL = 0x00
    }

    // 蓝牙相关
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var isScanning = false
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val scanResultFlow = MutableStateFlow<List<PrinterDevice>>(emptyList())
    
    // 连接相关
    private var currentConnection: BluetoothConnection? = null
    private var currentPrinterConfig: PrinterConfig? = null
    private var isConnecting = false
    private var heartbeatJob: Job? = null
    
    // 打印队列
    private val printQueue = mutableListOf<PrintJob>()
    private var isProcessingQueue = false
    
    // 状态管理
    private val printerStatusFlow = MutableStateFlow<Map<String, PrinterStatus>>(emptyMap())
    private var lastPrintContent: String? = null

    init {
        initializeBluetooth()
    }

    /**
     * 初始化蓝牙适配器
     */
    private fun initializeBluetooth() {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
            Log.d(TAG, "蓝牙适配器初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "蓝牙适配器初始化失败: ${e.message}")
        }
    }

    /**
     * 获取蓝牙适配器
     */
    private fun getBluetoothAdapter(): BluetoothAdapter? {
        if (bluetoothAdapter == null) {
            initializeBluetooth()
        }
        return bluetoothAdapter
    }

    /**
     * 检查蓝牙权限
     */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 扫描蓝牙打印机
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun scanPrinters(type: String): List<PrinterDevice> {
        if (type != PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
            Log.e(TAG, "不支持的打印机类型: $type")
            return emptyList()
        }

        Log.d(TAG, "===== GB18030打印机扫描开始 =====")
        Log.d(TAG, "安卓版本: ${Build.VERSION.SDK_INT}")

        val hasPermission = hasBluetoothPermission()
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e(TAG, "没有蓝牙权限")
            scanResultFlow.tryEmit(emptyList())
            return emptyList()
        }

        try {
            val adapter = getBluetoothAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.e(TAG, "蓝牙未启用或初始化失败")
                return emptyList()
            }

            discoveredDevices.clear()

            // 添加已配对设备
            try {
                val pairedDevices = adapter.bondedDevices ?: emptySet()
                for (device in pairedDevices) {
                    discoveredDevices[device.address] = device
                    updateScanResults()
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取已配对设备失败: ${e.message}")
            }

            // 停止之前的扫描
            if (isScanning) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    adapter.cancelDiscovery()
                }
                isScanning = false
                delay(1000)
            }

            // 注册广播接收器
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            device?.let {
                                discoveredDevices[it.address] = it
                                updateScanResults()
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            isScanning = false
                            Log.d(TAG, "蓝牙扫描完成，发现设备数量: ${discoveredDevices.size}")
                        }
                    }
                }
            }

            context.registerReceiver(receiver, filter)

            // 开始扫描
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                isScanning = adapter.startDiscovery()
                Log.d(TAG, "开始蓝牙扫描: $isScanning")
            }

            // 等待扫描完成
            var scanTimeout = 0
            while (isScanning && scanTimeout < 30) {
                delay(1000)
                scanTimeout++
            }

            // 取消扫描
            if (isScanning) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    adapter.cancelDiscovery()
                }
                isScanning = false
            }

            // 注销接收器
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e(TAG, "注销广播接收器失败: ${e.message}")
            }

            val devices = discoveredDevices.values.map { device ->
                PrinterDevice(
                    name = device.name ?: "未知设备",
                    address = device.address,
                    type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                    isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                )
            }

            scanResultFlow.tryEmit(devices)
            Log.d(TAG, "扫描完成，返回设备数量: ${devices.size}")
            return devices

        } catch (e: Exception) {
            Log.e(TAG, "扫描打印机失败: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 更新扫描结果
     */
    private fun updateScanResults() {
        val devices = discoveredDevices.values.map { device ->
            PrinterDevice(
                name = device.name ?: "未知设备",
                address = device.address,
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                isPaired = device.bondState == BluetoothDevice.BOND_BONDED
            )
        }
        scanResultFlow.tryEmit(devices)
    }

    /**
     * 连接打印机
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override suspend fun connectPrinter(config: PrinterConfig): Boolean {
        if (isConnecting) {
            Log.d(TAG, "已有连接正在进行中")
            return false
        }

        if (currentConnection?.isConnected == true) {
            Log.d(TAG, "已有连接，先断开")
            disconnectPrinter()
        }

        isConnecting = true
        try {
            Log.d(TAG, "开始连接打印机: ${config.name} (${config.address})")

            val adapter = getBluetoothAdapter()
            if (adapter == null) {
                Log.e(TAG, "蓝牙适配器不可用")
                return false
            }

            // 查找设备
            val device = discoveredDevices[config.address] ?: adapter.getRemoteDevice(config.address)
            
            // 创建连接
            val connection = BluetoothConnection(device)
            connection.connect()

            if (!connection.isConnected) {
                Log.e(TAG, "连接失败")
                return false
            }

            // 保存当前连接
            currentConnection = connection
            currentPrinterConfig = config

            // 初始化打印机
            initializePrinter(config)

            // 启动心跳检测
            startHeartbeat(config)

            // 更新状态
            updatePrinterStatus(config, PrinterStatus.CONNECTED)

            Log.d(TAG, "【GB18030打印机连接】连接成功: ${config.name}")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "连接过程中发生异常: ${e.message}", e)
            updatePrinterStatus(config, PrinterStatus.ERROR)
            return false
        } finally {
            isConnecting = false
        }
    }

    /**
     * 初始化打印机
     */
    private fun initializePrinter(config: PrinterConfig) {
        try {
            val connection = currentConnection ?: return

            // 发送初始化命令
            connection.write(byteArrayOf(ESC, 0x40)) // ESC @ - 初始化打印机
            Thread.sleep(100)

            // 设置字符编码为GB18030
            setCharacterEncoding(connection)

            // 设置打印密度和速度
            setPrintDensity(connection, config.printDensity)
            setPrintSpeed(connection, config.printSpeed)

            Log.d(TAG, "打印机初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "打印机初始化失败: ${e.message}")
        }
    }

    /**
     * 设置字符编码为GB18030
     */
    private fun setCharacterEncoding(connection: BluetoothConnection) {
        try {
            // 设置代码页为GB18030 (0x1E)
            connection.write(byteArrayOf(ESC, 0x74, 0x1E))
            Thread.sleep(50)
            
            // 设置字符集为GB18030
            connection.write(byteArrayOf(ESC, 0x52, 0x0F))
            Thread.sleep(50)
            
            Log.d(TAG, "字符编码设置为GB18030")
        } catch (e: Exception) {
            Log.e(TAG, "设置字符编码失败: ${e.message}")
        }
    }

    /**
     * 设置打印密度
     */
    private fun setPrintDensity(connection: BluetoothConnection, density: Int) {
        try {
            val densityValue = when (density) {
                PrinterConfig.PRINT_DENSITY_LIGHT -> 0x00
                PrinterConfig.PRINT_DENSITY_DARK -> 0x02
                else -> 0x01 // 正常
            }
            connection.write(byteArrayOf(GS, 0x28, 0x4E, 0x02, 0x00, densityValue.toByte()))
            Thread.sleep(50)
        } catch (e: Exception) {
            Log.e(TAG, "设置打印密度失败: ${e.message}")
        }
    }

    /**
     * 设置打印速度
     */
    private fun setPrintSpeed(connection: BluetoothConnection, speed: Int) {
        try {
            val speedValue = when (speed) {
                PrinterConfig.PRINT_SPEED_SLOW -> 0x00
                PrinterConfig.PRINT_SPEED_FAST -> 0x02
                else -> 0x01 // 正常
            }
            connection.write(byteArrayOf(GS, 0x28, 0x4E, 0x02, 0x01, speedValue.toByte()))
            Thread.sleep(50)
        } catch (e: Exception) {
            Log.e(TAG, "设置打印速度失败: ${e.message}")
        }
    }

    /**
     * 启动心跳检测
     */
    private fun startHeartbeat(config: PrinterConfig) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    delay(30000) // 30秒检测一次
                    if (currentConnection?.isConnected != true) {
                        Log.w(TAG, "心跳检测失败，连接已断开")
                        updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "心跳检测异常: ${e.message}")
                }
            }
        }
    }

    /**
     * 断开打印机连接
     */
    override suspend fun disconnectPrinter() {
        try {
            heartbeatJob?.cancel()
            currentConnection?.disconnect()
            currentConnection = null
            currentPrinterConfig = null
            
            currentPrinterConfig?.let { config ->
                updatePrinterStatus(config, PrinterStatus.DISCONNECTED)
            }
            
            Log.d(TAG, "打印机连接已断开")
        } catch (e: Exception) {
            Log.e(TAG, "断开连接失败: ${e.message}")
        }
    }

    /**
     * 打印内容
     */
    override suspend fun printContent(content: String, config: PrinterConfig): Boolean {
        if (currentConnection?.isConnected != true) {
            Log.e(TAG, "打印机未连接")
            return false
        }

        try {
            Log.d(TAG, "开始GB18030编码打印，内容长度: ${content.length}")
            
            // 将内容转换为GB18030编码
            val gb18030Bytes = content.toByteArray(GB18030_CHARSET)
            Log.d(TAG, "GB18030编码后字节数: ${gb18030Bytes.size}")

            // 分块发送数据
            val chunkSize = 1024
            var offset = 0
            
            while (offset < gb18030Bytes.size) {
                val end = minOf(offset + chunkSize, gb18030Bytes.size)
                val chunk = gb18030Bytes.copyOfRange(offset, end)
                
                currentConnection?.write(chunk)
                Thread.sleep(50) // 短暂延迟确保数据发送完成
                
                offset = end
            }

            // 发送换行和切纸命令
            sendCutCommand(config)
            
            Log.d(TAG, "GB18030编码打印完成")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "GB18030编码打印失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 发送切纸命令
     */
    private fun sendCutCommand(config: PrinterConfig) {
        try {
            if (!config.autoCut) {
                Log.d(TAG, "自动切纸已禁用，跳过切纸命令")
                return
            }

            // 发送走纸命令
            currentConnection?.write(byteArrayOf(LF, LF, LF))
            Thread.sleep(100)

            // 发送切纸命令
            currentConnection?.write(byteArrayOf(GS, 0x56, 0x01)) // GS V 1 - 部分切纸
            Thread.sleep(200)

            Log.d(TAG, "切纸命令发送完成")
        } catch (e: Exception) {
            Log.e(TAG, "发送切纸命令失败: ${e.message}")
        }
    }

    /**
     * 获取扫描结果Flow
     */
    override fun getScanResultFlow(): Flow<List<PrinterDevice>> = scanResultFlow.asStateFlow()

    /**
     * 获取打印机状态Flow
     */
    override fun getPrinterStatusFlow(): Flow<Map<String, PrinterStatus>> = printerStatusFlow.asStateFlow()

    /**
     * 更新打印机状态
     */
    private fun updatePrinterStatus(config: PrinterConfig, status: PrinterStatus) {
        val currentStatus = printerStatusFlow.value.toMutableMap()
        currentStatus[config.id] = status
        printerStatusFlow.tryEmit(currentStatus)
    }

    /**
     * 蓝牙连接类
     */
    private inner class BluetoothConnection(private val device: BluetoothDevice) {
        private var socket: android.bluetooth.BluetoothSocket? = null
        
        val isConnected: Boolean
            get() = socket?.isConnected == true

        fun connect() {
            try {
                socket = device.createRfcommSocketToServiceRecord(
                    java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                socket?.connect()
                Log.d(TAG, "蓝牙连接建立成功")
            } catch (e: Exception) {
                Log.e(TAG, "蓝牙连接失败: ${e.message}")
                throw e
            }
        }

        fun write(data: ByteArray) {
            try {
                socket?.outputStream?.write(data)
                socket?.outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "写入数据失败: ${e.message}")
                throw e
            }
        }

        fun disconnect() {
            try {
                socket?.close()
                socket = null
            } catch (e: Exception) {
                Log.e(TAG, "断开连接失败: ${e.message}")
            }
        }
    }

    /**
     * 打印任务数据类
     */
    private data class PrintJob(
        val orderId: String,
        val content: String,
        val config: PrinterConfig
    )
} 