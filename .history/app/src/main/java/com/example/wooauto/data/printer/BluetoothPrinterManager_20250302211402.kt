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
        // 蓝牙扫描超时时间 (毫秒)
        private const val SCAN_TIMEOUT = 30000L // 增加到30秒
        // 连接超时时间 (毫秒)
        private const val CONNECT_TIMEOUT = 15000L
        // 最大重试次数
        private const val MAX_RETRY_COUNT = 5
    }
    
    // 蓝牙设备发现广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }?.let { device ->
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                val deviceName = device.name ?: "未知设备"
                                val deviceAddress = device.address
                                Log.d(TAG, "发现新蓝牙设备: $deviceName ($deviceAddress)")
                                discoveredDevices[deviceAddress] = device
                                updateScanResults()
                            } catch (e: Exception) {
                                Log.e(TAG, "处理蓝牙设备信息失败: ${e.message}", e)
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "蓝牙设备扫描完成")
                    isScanning = false
                    unregisterBluetoothReceiver()
                    updateScanResults()
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
            if (bluetoothAdapter.isEnabled == false) {
                Log.e(TAG, "蓝牙未启用，请先开启蓝牙")
                updatePrinterStatus(config.id, PrinterStatus.ERROR)
                return@withContext false
            }

            // 断开当前连接
            disconnect(config)
            
            // 更新状态为连接中
            updatePrinterStatus(config.id, PrinterStatus.CONNECTING)
            
            // 开始多阶段连接流程
            return@withContext tryConnectWithDevice(config.address, config.id)
        } catch (e: Exception) {
            Log.e(TAG, "连接过程中发生异常: ${e.message}", e)
            updatePrinterStatus(config.id, PrinterStatus.ERROR)
            return@withContext false
        }
    }
    
    /**
     * 多阶段连接流程 - 尝试使用设备地址进行连接
     */
    private suspend fun tryConnectWithDevice(address: String, configId: String? = null): Boolean {
        // 如果蓝牙适配器为空或不可用，直接返回失败
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "蓝牙适配器不可用，无法连接")
            configId?.let { updatePrinterStatus(it, PrinterStatus.ERROR) }
            return false
        }
        
        // 取消当前搜索，以提高连接成功率
        try {
            if (bluetoothAdapter.isDiscovering && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter.cancelDiscovery()
                Log.d(TAG, "已取消当前设备搜索以提高连接成功率")
            }
        } catch (e: Exception) {
            Log.w(TAG, "取消搜索失败，继续连接: ${e.message}")
        }
        
        // 获取蓝牙设备
        val device = if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                bluetoothAdapter.getRemoteDevice(address)
            } catch (e: Exception) {
                Log.e(TAG, "获取蓝牙设备失败: ${e.message}", e)
                configId?.let { updatePrinterStatus(it, PrinterStatus.ERROR) }
                return false
            }
        } else {
            Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法获取蓝牙设备")
            configId?.let { updatePrinterStatus(it, PrinterStatus.ERROR) }
            return false
        }
        
        // 检查设备配对状态
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val bondState = device.bondState
                Log.d(TAG, "设备配对状态: ${
                    when(bondState) {
                        BluetoothDevice.BOND_NONE -> "未配对"
                        BluetoothDevice.BOND_BONDING -> "正在配对"
                        BluetoothDevice.BOND_BONDED -> "已配对"
                        else -> "未知状态"
                    }
                }")
                
                // 如果设备未配对，尝试配对
                if (bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "设备未配对，尝试配对...")
                    
                    // 注册配对状态监听器
                    val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    context.registerReceiver(bondStateReceiver, filter)
                    
                    // 尝试配对
                    try {
                        val createBondMethod = device.javaClass.getMethod("createBond")
                        val result = createBondMethod.invoke(device) as Boolean
                        if (result) {
                            Log.d(TAG, "设备配对请求已发送，等待用户确认...")
                            configId?.let { updatePrinterStatus(it, PrinterStatus.CONNECTING) }
                            return false // 返回失败，等待配对完成后再次尝试连接
                        } else {
                            Log.e(TAG, "发送配对请求失败")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "尝试配对设备时出错: ${e.message}", e)
                    }
                } else if (bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "设备正在配对中，请稍候...")
                    configId?.let { updatePrinterStatus(it, PrinterStatus.CONNECTING) }
                    return false // 返回失败，等待配对完成后再次尝试连接
                }
                // 如果设备已配对或配对失败，继续尝试连接
            } else {
                Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法检查设备配对状态")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查设备配对状态时出错: ${e.message}", e)
        }
        
        // 尝试使用多个UUID进行连接
        for (uuid in PRINTER_UUIDS) {
            try {
                Log.d(TAG, "尝试使用UUID连接: $uuid")
                
                // 创建蓝牙连接
                currentConnection = BluetoothConnection(device)
                
                // 尝试连接打印机 - 使用自定义超时
                Log.d(TAG, "尝试连接打印机(超时: ${CONNECT_TIMEOUT/1000}秒)...")
                
                var connected = false
                
                // 实现智能重试逻辑
                for (retry in 0 until MAX_RETRY_COUNT) {
                    try {
                        if (retry > 0) {
                            // 第一次尝试失败后，增加延时
                            val delayTime = 1000L * (retry + 1) // 递增延时
                            Log.d(TAG, "连接失败，第${retry+1}次重试(延时${delayTime/1000}秒)...")
                            delay(delayTime)
                        }
                        
                        // 尝试连接
                        currentConnection?.connect()
                        connected = true
                        Log.d(TAG, "蓝牙连接成功!")
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "第${retry+1}次连接尝试失败: ${e.message}")
                        if (retry == MAX_RETRY_COUNT - 1) {
                            // 最后一次重试也失败，抛出异常
                            throw e
                        }
                    }
                }
                
                if (!connected) {
                    continue // 尝试下一个UUID
                }
                
                // 连接成功，创建打印机实例
                Log.d(TAG, "创建EscPosPrinter实例")
                // 创建打印机实例，使用更广泛兼容的设置
                currentPrinter = EscPosPrinter(
                    currentConnection,
                    203, // 203 DPI是大多数热敏打印机的标准分辨率
                    58f, // 58mm纸宽
                    32  // 每行最多32个字符
                )
                
                // 更新打印机状态
                configId?.let { updatePrinterStatus(it, PrinterStatus.CONNECTED) }
                Log.d(TAG, "打印机连接成功")
                
                // 保存连接状态
                settingRepository.setPrinterConnection(true)
                
                return true // 成功连接
            } catch (e: Exception) {
                Log.e(TAG, "使用UUID(${uuid})连接失败: ${e.message}", e)
                // 释放资源，准备尝试下一个UUID
                currentConnection = null
                currentPrinter = null
            }
        }
        
        // 所有UUID尝试都失败
        Log.e(TAG, "所有连接尝试均失败")
        configId?.let { updatePrinterStatus(it, PrinterStatus.ERROR) }
        return false
    }
    
    /**
     * 获取蓝牙设备扫描结果Flow
     */
    fun getScanResultFlow(): Flow<List<PrinterDevice>> = scanResultFlow.asStateFlow()
    
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
            
            // 清除之前的设备列表
            discoveredDevices.clear()
            
            // 获取已配对的设备
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val pairedDevices = bluetoothAdapter.bondedDevices ?: emptySet()
                    Log.d(TAG, "已配对设备数量: ${pairedDevices.size}个")
                    
                    // 将已配对设备添加到发现列表
                    pairedDevices.forEach { device ->
                        try {
                            val deviceName = try { device.name } catch (e: Exception) { "未知设备" }
                            val deviceAddress = device.address
                            Log.d(TAG, "已配对设备: $deviceName ($deviceAddress)")
                            discoveredDevices[deviceAddress] = device
                        } catch (e: Exception) {
                            Log.w(TAG, "处理配对设备信息失败: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取配对设备列表失败: ${e.message}", e)
                }
            } else {
                Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法获取已配对设备")
            }
            
            // 更新初始扫描结果（已配对设备）
            updateScanResults()
            
            // 开始主动扫描发现新设备
            val discoveredDevices = startDiscovery()
            
            // 将扫描结果转换为PrinterDevice对象
            return@withContext discoveredDevices
        } catch (e: Exception) {
            Log.e(TAG, "扫描蓝牙打印机时发生异常: ${e.message}", e)
            return@withContext emptyList()
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
    
    /**
     * 开始蓝牙设备发现
     * 使用协程实现异步扫描
     */
    private suspend fun startDiscovery(): List<PrinterDevice> = suspendCancellableCoroutine { continuation ->
        if (isScanning) {
            Log.d(TAG, "已经在扫描中，忽略重复请求")
            val currentDevices = getDevicesList()
            continuation.resume(currentDevices)
            return@suspendCancellableCoroutine
        }
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "没有BLUETOOTH_SCAN权限，无法发现新设备")
                val currentDevices = getDevicesList()
                continuation.resume(currentDevices)
                return@suspendCancellableCoroutine
            }
            
            // 注册广播接收器
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            
            // 检查是否正在进行发现，如果是先取消
            if (bluetoothAdapter?.isDiscovering == true) {
                Log.d(TAG, "取消当前正在进行的蓝牙扫描")
                bluetoothAdapter.cancelDiscovery()
            }
            
            // 启动发现
            Log.d(TAG, "开始主动扫描蓝牙设备...")
            isScanning = true
            val started = bluetoothAdapter?.startDiscovery() ?: false
            
            if (!started) {
                Log.e(TAG, "启动蓝牙设备发现失败")
                unregisterBluetoothReceiver()
                isScanning = false
                val currentDevices = getDevicesList()
                continuation.resume(currentDevices)
                return@suspendCancellableCoroutine
            }
            
            // 设置超时（在单独的协程中运行）
            managerScope.launch {
                delay(SCAN_TIMEOUT)
                if (isScanning) {
                    Log.d(TAG, "蓝牙设备扫描超时")
                    isScanning = false
                    if (bluetoothAdapter?.isDiscovering == true && 
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothAdapter.cancelDiscovery()
                    }
                    unregisterBluetoothReceiver()
                    // 发送最终的设备列表
                    updateScanResults()
                }
            }
            
            // 取消协程时确保清理资源
            continuation.invokeOnCancellation {
                try {
                    isScanning = false
                    if (bluetoothAdapter?.isDiscovering == true) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_SCAN
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            bluetoothAdapter.cancelDiscovery()
                        }
                    }
                    unregisterBluetoothReceiver()
                } catch (e: Exception) {
                    Log.e(TAG, "取消扫描时发生异常: ${e.message}", e)
                }
            }
            
            // 返回当前的设备列表，后续将通过Flow更新
            val initialDevices = getDevicesList()
            continuation.resume(initialDevices)
            
        } catch (e: Exception) {
            Log.e(TAG, "蓝牙设备发现过程中发生异常: ${e.message}", e)
            isScanning = false
            unregisterBluetoothReceiver()
            val currentDevices = getDevicesList()
            continuation.resume(currentDevices)
        }
    }
    
    /**
     * 安全地注销蓝牙广播接收器
     */
    private fun unregisterBluetoothReceiver() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // 如果接收器未注册，忽略异常
            Log.d(TAG, "注销蓝牙广播接收器: ${e.message}")
        }
    }
    
    /**
     * 更新扫描结果列表并通知观察者
     */
    private fun updateScanResults() {
        val devices = getDevicesList()
        scanResultFlow.tryEmit(devices)
    }
    
    /**
     * 获取当前发现的设备列表 - 不进行过滤，显示所有发现的设备
     */
    private fun getDevicesList(): List<PrinterDevice> {
        return discoveredDevices.values.mapNotNull { device ->
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // 尝试获取设备名称，但不过滤未命名设备
                    val deviceName = try {
                        device.name ?: "未命名设备"
                    } catch (e: Exception) {
                        "未命名设备-${device.address.takeLast(5)}"
                    }
                    
                    val deviceAddress = try {
                        device.address
                    } catch (e: Exception) {
                        Log.e(TAG, "无法获取设备地址: ${e.message}")
                        return@mapNotNull null
                    }
                    
                    // 不进行任何过滤，返回所有设备
                    PrinterDevice(
                        name = deviceName,
                        address = deviceAddress,
                        type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                        status = getDeviceStatus(deviceAddress)
                    )
                } else {
                    Log.e(TAG, "没有BLUETOOTH_CONNECT权限，无法获取设备信息")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理设备信息出错: ${e.message}", e)
                null
            }
        }
    }
} 