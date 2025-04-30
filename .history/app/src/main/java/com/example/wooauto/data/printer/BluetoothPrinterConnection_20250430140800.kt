package com.example.wooauto.data.printer

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterConnection
import com.example.wooauto.domain.printer.PrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 蓝牙打印机连接实现
 */
class BluetoothPrinterConnection(
    private val device: BluetoothDevice,
    private val config: PrinterConfig
) : PrinterConnection {
    
    private val TAG = "BluetoothPrinterConn"
    private var bluetoothConnection: BluetoothConnection? = null
    private var printer: EscPosPrinter? = null
    private val isConnected = AtomicBoolean(false)
    
    // 打印机DPI，默认为203dpi
    private val printerDpi = 203
    
    // 打印机字符数，根据纸宽确定
    private val nbCharPerLine = when (config.paperWidth) {
        PrinterConfig.PAPER_WIDTH_57MM -> 32
        PrinterConfig.PAPER_WIDTH_80MM -> 42
        else -> 32
    }
    
    /**
     * 连接打印机
     */
    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get() && bluetoothConnection?.isConnected == true) {
                return@withContext true
            }
            
            // 创建蓝牙连接
            bluetoothConnection = BluetoothConnection(device)
            
            // 建立连接
            val connection = bluetoothConnection ?: return@withContext false
            val connected = connection.connect()
            
            if (connected == false) {
                Log.e(TAG, "无法建立蓝牙连接: ${device.address}")
                isConnected.set(false)
                return@withContext false
            }
            
            // 创建打印机实例
            try {
                printer = EscPosPrinter(
                    connection,
                    printerDpi,
                    config.paperWidth.toFloat(),
                    nbCharPerLine
                )
                
                // 初始化打印机
                initialize()
                
                // 更新连接状态
                isConnected.set(true)
                Log.d(TAG, "打印机连接成功: ${config.name}")
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "创建打印机实例失败: ${e.message}", e)
                connection.disconnect()
                isConnected.set(false)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接打印机异常: ${e.message}", e)
            isConnected.set(false)
            return@withContext false
        }
    }
    
    /**
     * 断开打印机连接
     */
    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            // 使用扩展方法断开打印机连接
            printer?.disconnectPrinter()
            bluetoothConnection?.disconnect()
            
            // 清空引用
            printer = null
            bluetoothConnection = null
            
            // 更新状态
            isConnected.set(false)
            Log.d(TAG, "打印机已断开连接: ${config.name}")
        } catch (e: Exception) {
            Log.e(TAG, "断开打印机连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 检查连接状态
     */
    override fun isConnected(): Boolean {
        return isConnected.get() && bluetoothConnection?.isConnected == true
    }
    
    /**
     * 发送数据到打印机
     */
    override suspend fun write(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected()) {
                Log.e(TAG, "打印机未连接，无法发送数据")
                return@withContext false
            }
            
            bluetoothConnection?.write(data)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "发送数据到打印机失败: ${e.message}", e)
            // 连接可能已断开，更新状态
            if (e is EscPosConnectionException || e.message?.contains("Broken pipe") == true) {
                isConnected.set(false)
            }
            return@withContext false
        }
    }
    
    /**
     * 读取打印机状态
     */
    override suspend fun readStatus(): PrinterStatus = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext PrinterStatus.DISCONNECTED
        }
        
        try {
            // 发送查询状态命令
            val statusCommand = byteArrayOf(0x10, 0x04, 0x01)  // DLE EOT n
            bluetoothConnection?.write(statusCommand)
            
            // 由于蓝牙打印机通常不返回状态数据，我们只能根据连接状态判断
            return@withContext PrinterStatus.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "读取打印机状态失败: ${e.message}", e)
            
            // 根据异常类型判断状态
            return@withContext when {
                e is EscPosConnectionException || e.message?.contains("Broken pipe") == true -> {
                    isConnected.set(false)
                    PrinterStatus.DISCONNECTED
                }
                else -> PrinterStatus.ERROR
            }
        }
    }
    
    /**
     * 打印内容
     */
    override suspend fun print(content: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.e(TAG, "打印机未连接，无法打印内容")
            return@withContext false
        }
        
        try {
            // 确保内容有效，避免EscPosPrinter空内容异常
            val validContent = validatePrintContent(content)
            
            // 使用EscPosPrinter打印
            printer?.printFormattedText(validContent)
            
            // 发送打印触发命令
            val printTrigger = PrinterCommandUtil.getPrintFeedCommand()
            write(printTrigger)
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "打印内容失败: ${e.message}", e)
            
            // 连接可能已断开，更新状态
            if (e is EscPosConnectionException || e.message?.contains("Broken pipe") == true) {
                isConnected.set(false)
            }
            
            return@withContext false
        }
    }
    
    /**
     * 执行切纸
     */
    override suspend fun cutPaper(partial: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.e(TAG, "打印机未连接，无法执行切纸")
            return@withContext false
        }
        
        try {
            // 先走纸
            feedPaper(4)
            
            // 获取切纸命令
            val cutCommand = PrinterCommandUtil.getCutCommand(config.brand, partial)
            val success = write(cutCommand)
            
            // 发送换行和回车确保命令执行
            write(byteArrayOf(0x0A, 0x0D))
            
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "执行切纸失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 走纸
     */
    override suspend fun feedPaper(lines: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            Log.e(TAG, "打印机未连接，无法走纸")
            return@withContext false
        }
        
        try {
            // 获取走纸命令
            val feedCommand = PrinterCommandUtil.getPaperFeedCommand(lines)
            return@withContext write(feedCommand)
        } catch (e: Exception) {
            Log.e(TAG, "执行走纸失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 初始化打印机
     */
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected.get()) {
            Log.e(TAG, "打印机未连接，无法初始化")
            return@withContext false
        }
        
        try {
            // 清除缓冲区
            write(PrinterCommandUtil.CLEAR_BUFFER)
            Thread.sleep(50)
            
            // 初始化打印机
            val success = write(PrinterCommandUtil.INITIALIZE)
            Thread.sleep(100)
            
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "初始化打印机失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 发送心跳命令
     */
    override suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext false
        }
        
        try {
            // 发送初始化命令作为心跳
            val heartbeatCommand = PrinterCommandUtil.getHeartbeatCommand(config.brand)
            return@withContext write(heartbeatCommand)
        } catch (e: Exception) {
            Log.e(TAG, "发送心跳命令失败: ${e.message}", e)
            
            // 连接可能已断开，更新状态
            if (e is EscPosConnectionException || e.message?.contains("Broken pipe") == true) {
                isConnected.set(false)
            }
            
            return@withContext false
        }
    }
    
    /**
     * 获取打印机配置
     */
    override fun getConfig(): PrinterConfig {
        return config
    }
    
    /**
     * 测试打印机连接
     */
    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            return@withContext false
        }
        
        try {
            // 发送初始化命令测试连接
            return@withContext write(PrinterCommandUtil.INITIALIZE)
        } catch (e: Exception) {
            Log.e(TAG, "测试打印机连接失败: ${e.message}", e)
            
            // 连接可能已断开，更新状态
            if (e is EscPosConnectionException || e.message?.contains("Broken pipe") == true) {
                isConnected.set(false)
            }
            
            return@withContext false
        }
    }
    
    /**
     * 验证并修复打印内容
     */
    private fun validatePrintContent(content: String): String {
        if (content.isBlank()) {
            return "[L]测试打印内容"
        }
        
        val lines = content.split("\n")
        val fixedLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 空行转换为带空格的左对齐行
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
        
        return fixedLines.joinToString("\n")
    }
} 