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
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.models.PrinterConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 打印机设备扫描器
 * 负责扫描和发现不同类型的打印机设备
 */
@Singleton
class PrinterDeviceScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "PrinterDeviceScanner"
    
    // 蓝牙设备扫描相关
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private var isScanning = false
    private val deviceScanFlow = MutableStateFlow<List<PrinterDevice>>(emptyList())
    
    // 协程作用域
    private val scannerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 蓝牙适配器
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }
    
    // 蓝牙设备发现广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 获取找到的蓝牙设备
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
                        // 保存设备
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
                    
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    
                    // 如果设备完成配对，更新列表
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
    
    /**
     * 获取设备扫描结果Flow
     */
    fun getDeviceScanFlow(): Flow<List<PrinterDevice>> {
        return deviceScanFlow.asStateFlow()
    }
    
    /**
     * 开始蓝牙设备扫描
     */
    suspend fun startBluetoothScan() {
        if (isScanning) {
            Log.d(TAG, "扫描已在进行中")
            return
        }
        
        Log.d(TAG, "开始蓝牙设备扫描")
        
        // 检查权限
        if (!hasBluetoothPermission()) {
            Log.e(TAG, "没有蓝牙权限")
            return
        }
        
        // 检查蓝牙是否可用
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "蓝牙未启用")
            return
        }
        
        try {
            // 清空设备列表
            discoveredDevices.clear()
            
            // 首先添加已配对设备
            addPairedDevices()
            
            // 确保已停止之前的扫描
            if (bluetoothAdapter!!.isDiscovering) {
                bluetoothAdapter!!.cancelDiscovery()
                delay(1000) // 等待一段时间
            }
            
            // 注册广播接收器
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            
            context.registerReceiver(bluetoothReceiver, filter)
            
            // 开始扫描
            isScanning = true
            bluetoothAdapter!!.startDiscovery()
            
            // 设置超时
            scannerScope.launch {
                delay(SCAN_TIMEOUT)
                stopBluetoothScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动蓝牙设备扫描失败: ${e.message}", e)
            isScanning = false
        }
    }
    
    /**
     * 停止蓝牙设备扫描
     */
    fun stopBluetoothScan() {
        if (!isScanning) {
            return
        }
        
        try {
            // 停止蓝牙发现
            bluetoothAdapter?.cancelDiscovery()
            
            // 注销广播接收器
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "注销蓝牙广播接收器失败: ${e.message}", e)
            }
            
            // 更新状态
            isScanning = false
            
            Log.d(TAG, "蓝牙设备扫描已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止蓝牙设备扫描失败: ${e.message}", e)
        }
    }
    
    /**
     * 添加已配对设备
     */
    private fun addPairedDevices() {
        try {
            val pairedDevices = getPairedDevices()
            
            for (device in pairedDevices) {
                discoveredDevices[device.address] = device
            }
            
            // 更新扫描结果
            updateScanResults()
        } catch (e: Exception) {
            Log.e(TAG, "添加已配对设备失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取已配对设备
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
                    bluetoothAdapter?.bondedDevices ?: emptySet()
                }
            } else {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.bondedDevices ?: emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取已配对设备失败: ${e.message}", e)
            emptySet()
        }
    }
    
    /**
     * 更新扫描结果
     */
    private fun updateScanResults() {
        try {
            val devices = getDiscoveredDevices()
            deviceScanFlow.tryEmit(devices)
        } catch (e: Exception) {
            Log.e(TAG, "更新扫描结果失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取已发现的设备列表
     */
    private fun getDiscoveredDevices(): List<PrinterDevice> {
        return discoveredDevices.values.map { device ->
            val deviceName = device.name ?: "未知设备 (${device.address.takeLast(5)})"
            
            PrinterDevice(
                name = deviceName,
                address = device.address,
                type = PrinterConfig.PRINTER_TYPE_BLUETOOTH,
                status = PrinterStatus.DISCONNECTED
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
            )
        )
    }
    
    /**
     * 检查是否有蓝牙权限
     */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上版本需要BLUETOOTH_CONNECT和BLUETOOTH_SCAN权限
            val hasConnectPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasScanPermission = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasConnectPermission) {
                Log.e(TAG, "缺少BLUETOOTH_CONNECT权限")
            }
            
            if (!hasScanPermission) {
                Log.e(TAG, "缺少BLUETOOTH_SCAN权限")
            }
            
            hasConnectPermission && hasScanPermission
        } else {
            // 低版本Android上，即使没有权限也尝试继续执行
            true
        }
    }
    
    companion object {
        // 蓝牙扫描超时时间（毫秒）
        private const val SCAN_TIMEOUT = 60000L
    }
} 