package com.example.wooauto.data.printer

import android.content.Context
import android.util.Log
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterBrand
import com.starmicronics.stario10.StarIO10Exception
import com.starmicronics.stario10.StarPrinter
import com.starmicronics.stario10.StarConnectionSettings
import com.starmicronics.stario10.InterfaceType
import com.starmicronics.stario10.StarPrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Star打印机处理类
 * 处理Star打印机的特定实现
 */
class StarPrinterHandler(private val context: Context) {
    companion object {
        private const val TAG = "StarPrinterHandler"
    }

    private var currentPrinter: StarPrinter? = null

    /**
     * 连接到Star打印机
     * @param config 打印机配置
     * @return 连接是否成功
     */
    suspend fun connect(config: PrinterConfig): Boolean {
        return try {
            Log.d(TAG, "尝试连接Star打印机: ${config.name} (${config.address})")
            
            // 根据打印机类型选择接口类型
            val interfaceType = when (config.type) {
                PrinterConfig.PRINTER_TYPE_BLUETOOTH -> InterfaceType.Bluetooth
                PrinterConfig.PRINTER_TYPE_WIFI -> InterfaceType.Lan
                PrinterConfig.PRINTER_TYPE_USB -> InterfaceType.Usb
                else -> return false
            }
            
            // 创建连接设置
            val settings = StarConnectionSettings(interfaceType, config.address)
            val printer = StarPrinter(settings, context)
            
            // 连接打印机
            withContext(Dispatchers.IO) {
                printer.openAsync().await()
            }
            
            currentPrinter = printer
            Log.d(TAG, "Star打印机连接成功")
            true
        } catch (e: StarIO10Exception) {
            Log.e(TAG, "Star打印机连接失败: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Star打印机连接异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 断开Star打印机连接
     */
    suspend fun disconnect() {
        try {
            withContext(Dispatchers.IO) {
                currentPrinter?.closeAsync()?.await()
            }
            currentPrinter = null
            Log.d(TAG, "Star打印机断开连接")
        } catch (e: Exception) {
            Log.e(TAG, "断开Star打印机连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取Star打印机状态
     * @return 打印机状态
     */
    suspend fun getStatus(): StarPrinterStatus? {
        return try {
            withContext(Dispatchers.IO) {
                currentPrinter?.getStatusAsync()?.await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取Star打印机状态失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 打印文本内容
     * @param content 要打印的内容
     * @return 打印是否成功
     */
    suspend fun printText(content: String): Boolean {
        if (currentPrinter == null) {
            Log.e(TAG, "打印机未连接")
            return false
        }
        
        try {
            // 使用printAsync方法直接打印字符串内容
            withContext(Dispatchers.IO) {
                currentPrinter?.printAsync(content)?.await()
            }
            
            // 等待打印完成
            delay(500)
            
            Log.d(TAG, "Star打印机打印成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Star打印机打印失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 转换ESC/POS格式文本到Star命令
     * @param content ESC/POS格式的文本
     * @return 转换后可用于Star打印机的文本
     */
    fun convertEscPosToStarPrnt(content: String): String {
        // 这里进行基本的转换，实际情况可能需要更复杂的转换逻辑
        var starContent = content
        
        // 替换ESC/POS的格式化标签为Star支持的格式
        // 对于Star打印机，大多数命令可以直接使用ESC/POS命令，因为Star支持ESC/POS兼容模式
        
        // 添加额外的换行确保打印后有足够的空白边距
        starContent += "\n\n\n\n"
        
        return starContent
    }
    
    /**
     * 发送测试打印内容
     * @param config 打印机配置
     * @return 测试是否成功
     */
    suspend fun printTest(config: PrinterConfig): Boolean {
        val testContent = """
            
            === Star打印测试 ===
            ----------------
            品牌: ${config.brand.displayName}
            地址: ${config.address}
            名称: ${config.name}
            ----------------
            测试成功
            
            
            
            
            
        """.trimIndent()
        
        return printText(testContent)
    }
} 