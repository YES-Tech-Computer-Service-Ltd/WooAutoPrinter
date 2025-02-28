package com.example.wooauto.data.printer

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
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
import com.example.wooauto.data.settings.SettingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothPrinterManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingRepository: DomainSettingRepository,
    private val orderRepository: DomainOrderRepository,
    private val templateManager: OrderPrintTemplate
) : PrinterManager {

    private val TAG = "BluetoothPrinterManager"
    
    // 蓝牙适配器
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    // 保存当前连接的打印机
    private var currentConnection: BluetoothConnection? = null
    private var currentPrinter: EscPosPrinter? = null
    
    // 打印机状态Map和Flow
    private val printerStatusMap = mutableMapOf<String, PrinterStatus>()
    private val printerStatusFlows = mutableMapOf<String, MutableStateFlow<PrinterStatus>>()
    
    // 打印队列
    private val printQueue = mutableListOf<PrintJob>()
    private var isProcessingQueue = false
    
    // 添加标准串口服务UUID常量
    companion object {
        // 标准的蓝牙串口服务UUID，大多数打印机都使用这个
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    override suspend fun connect(config: PrinterConfig): Boolean = withContext(Dispatchers.IO) {
        if (config.type != PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
            Log.e(TAG, "不支持的打印机类型: ${config.type}")
            updatePrinterStatus(config.id, PrinterStatus.ERROR)
            return@withContext false
        }

        try {
            Log.d(TAG, "===== 开始连接蓝牙打印机 =====")
            Log.d(TAG, "打印机ID: ${config.id}")
            Log.d(TAG, "打印机地址: ${config.address}")
            
            // 检查蓝牙权限
            if (!hasBluetoothPermission()) {
                Log.e(TAG, "缺少蓝牙权限，无法连接打印机，请授予蓝牙和位置权限")
                updatePrinterStatus(config.id, PrinterStatus.ERROR)
                return@withContext false
            }

            // 检查蓝牙适配器
            if (bluetoothAdapter == null) {
                Log.e(TAG, "设备不支持蓝牙功能")
                updatePrinterStatus(config.id, PrinterStatus.ERROR)
                return@withContext false
            }

            // 检查蓝牙是否启用
            if (!bluetoothAdapter.isEnabled) {
                Log.e(TAG, "蓝牙未启用，请先开启蓝牙")
                updatePrinterStatus(config.id, PrinterStatus.ERROR)
                return@withContext false
            }

            // 断开当前连接
            disconnect(config)

            // 获取蓝牙设备
            Log.d(TAG, "尝试获取蓝牙设备: ${config.address}")
            val bluetoothDevice = if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    bluetoothAdapter.getRemoteDevice(config.address)
                } catch (e: Exception) {
                    Log.e(TAG, "获取蓝牙设备失败: ${e.message}", e)
                    updatePrinterStatus(config.id, PrinterStatus.ERROR)
                    return@withContext false
                }
            } else {
                Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法获取蓝牙设备")
                updatePrinterStatus(config.id, PrinterStatus.ERROR)
                return@withContext false
            }

            // 创建蓝牙连接
            Log.d(TAG, "创建蓝牙连接")
            try {
                // 使用标准的SPP UUID
                currentConnection = BluetoothConnection(bluetoothDevice, SPP_UUID)
                
                // 连接超时设置为5秒，热敏打印机响应可能较慢
                Log.d(TAG, "尝试连接打印机，超时设置为5秒...")
                currentConnection?.connect(5000)
                
                Log.d(TAG, "蓝牙连接成功，创建EscPosPrinter实例")
                // 创建打印机实例，使用更广泛兼容的设置
                currentPrinter = EscPosPrinter(
                    currentConnection,
                    203, // 203 DPI是大多数热敏打印机的标准分辨率
                    58f, // 58mm纸宽
                    32, // 每行最多32个字符
                    EscPosPrinter.CHARSET_UTF8 // 使用UTF-8编码以支持中文
                )
                
                updatePrinterStatus(config.id, PrinterStatus.CONNECTED)
                Log.d(TAG, "打印机连接成功")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "连接打印机失败: ${e.message}", e)
                currentConnection = null
                currentPrinter = null
                updatePrinterStatus(config.id, PrinterStatus.ERROR)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接过程中发生异常: ${e.message}", e)
            updatePrinterStatus(config.id, PrinterStatus.ERROR)
            return@withContext false
        }
    }
    
    override suspend fun disconnect(config: PrinterConfig) {
        withContext(Dispatchers.IO) {
            try {
                currentPrinter?.disconnectPrinter()
                currentConnection?.disconnect()
                currentPrinter = null
                currentConnection = null
                
                updatePrinterStatus(config.id, PrinterStatus.DISCONNECTED)
                Log.d(TAG, "已断开打印机连接: ${config.getDisplayName()}")
                
                // 更新打印机连接状态
                settingRepository.setPrinterConnection(false)
            } catch (e: Exception) {
                Log.e(TAG, "断开打印机连接失败: ${e.message}", e)
            }
        }
    }
    
    override suspend fun getPrinterStatus(config: PrinterConfig): PrinterStatus {
        return printerStatusMap[config.id] ?: PrinterStatus.DISCONNECTED
    }
    
    override fun getPrinterStatusFlow(config: PrinterConfig): Flow<PrinterStatus> {
        return printerStatusFlows.getOrPut(config.id) { 
            MutableStateFlow(printerStatusMap[config.id] ?: PrinterStatus.DISCONNECTED)
        }.asStateFlow()
    }
    
    override suspend fun scanPrinters(type: String): List<PrinterDevice> = withContext(Dispatchers.IO) {
        if (type != PrinterConfig.PRINTER_TYPE_BLUETOOTH) {
            Log.e(TAG, "不支持的打印机类型: $type")
            return@withContext emptyList()
        }

        try {
            Log.d(TAG, "===== 蓝牙打印机扫描开始 =====")
            Log.d(TAG, "安卓版本: ${Build.VERSION.SDK_INT}")
            Log.d(TAG, "扫描打印机类型: $type")

            // 检查蓝牙权限
            if (!hasBluetoothPermission()) {
                Log.e(TAG, "缺少蓝牙权限，无法扫描打印机设备，请授予蓝牙和位置权限")
                return@withContext emptyList()
            }

            // 检查蓝牙适配器
            if (bluetoothAdapter == null) {
                Log.e(TAG, "设备不支持蓝牙功能")
                return@withContext emptyList()
            }

            // 检查蓝牙是否启用
            if (!bluetoothAdapter.isEnabled) {
                Log.e(TAG, "蓝牙未启用，请先开启蓝牙")
                return@withContext emptyList()
            }

            // 获取已配对的设备
            Log.d(TAG, "尝试获取已配对的蓝牙设备...")
            val pairedDevices = if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val devices = bluetoothAdapter.bondedDevices ?: emptySet()
                    Log.d(TAG, "系统报告的已配对设备数量: ${devices.size}个")
                    // 打印所有已配对设备
                    devices.forEach { device ->
                        try {
                            Log.d(TAG, "已配对设备: ${device.name ?: "未知名称"} - ${device.address}")
                        } catch (e: Exception) {
                            Log.w(TAG, "无法获取设备信息: ${e.message}")
                        }
                    }
                    devices
                } catch (e: Exception) {
                    Log.e(TAG, "获取配对设备列表失败: ${e.message}", e)
                    emptySet()
                }
            } else {
                Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法获取已配对设备")
                emptySet()
            }

            // 转换为打印机设备对象
            val printerDevices = pairedDevices.mapNotNull { device ->
                try {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // 不过滤任何设备，获取所有蓝牙设备
                        val deviceName = try {
                            // 设备名称可能为null，提供默认值
                            device.name ?: "未知打印机"
                        } catch (e: Exception) {
                            Log.w(TAG, "无法获取设备名称: ${e.message}")
                            "未知打印机"
                        }

                        val deviceAddress = try {
                            device.address
                        } catch (e: Exception) {
                            Log.w(TAG, "无法获取设备地址: ${e.message}")
                            return@mapNotNull null
                        }
                        
                        Log.d(TAG, "创建打印机设备对象: $deviceName - $deviceAddress")
                        PrinterDevice(
                            name = deviceName,
                            address = deviceAddress,
                            type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                            status = getDeviceStatus(deviceAddress)
                        )
                    } else {
                        Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法访问设备信息")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理设备信息时出错: ${e.message}", e)
                    null
                }
            }

            Log.d(TAG, "找到${printerDevices.size}个蓝牙设备")
            printerDevices
        } catch (e: Exception) {
            Log.e(TAG, "扫描蓝牙打印机时发生异常: ${e.message}", e)
            emptyList()
        }
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
    }
    
    // 私有辅助方法
    
    private fun updatePrinterStatus(printerId: String, status: PrinterStatus) {
        printerStatusMap[printerId] = status
        printerStatusFlows[printerId]?.update { status }
    }
    
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
            
            if (!hasBluetoothPermission) {
                Log.e(TAG, "缺少 BLUETOOTH 权限，蓝牙功能将受限")
            }
            
            if (!hasBluetoothAdminPermission) {
                Log.e(TAG, "缺少 BLUETOOTH_ADMIN 权限，蓝牙管理功能将受限")
            }
            
            hasBluetoothPermission && hasBluetoothAdminPermission
        }
    }
    
    private fun findBluetoothDevice(address: String): BluetoothDevice? {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "缺少蓝牙连接权限")
                return null
            }
            bluetoothAdapter.getRemoteDevice(address)
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
            currentPrinter?.printFormattedText(content)
            true
        } catch (e: EscPosConnectionException) {
            Log.e(TAG, "打印机连接异常: ${e.message}", e)
            updatePrinterStatus(config.id, PrinterStatus.DISCONNECTED)
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
} 