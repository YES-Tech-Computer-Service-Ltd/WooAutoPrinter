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
import com.starmicronics.stario10.starprnt.StarPrintBuilder
import com.starmicronics.stario10.starprnt.PrinterBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            // 创建打印内容
            val builder = StarPrintBuilder()
            
            // 添加文本内容
            builder.actionPrintText(content)
            
            // 添加切纸命令
            builder.actionCut(StarPrintBuilder.CutType.Partial)
            
            // 发送打印数据
            val commands = builder.getCommands()
            
            withContext(Dispatchers.IO) {
                currentPrinter?.printAsync(commands)?.await()
            }
            
            Log.d(TAG, "Star打印机打印成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Star打印机打印失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 转换ESC/POS格式文本到StarPRNT命令
     * @param content ESC/POS格式的文本
     * @return 转换后可用于Star打印机的文本
     */
    fun convertEscPosToStarPrnt(content: String): String {
        // 这里进行基本的转换，实际情况可能需要更复杂的转换逻辑
        var starContent = content
        
        // 替换ESC/POS的格式化标签为Star支持的格式
        // 替换文本对齐方式
        starContent = starContent.replace("[L]", "")  // 左对齐，Star默认就是左对齐
        starContent = starContent.replace("[C]", "{center}")  // 居中
        starContent = starContent.replace("[R]", "{right}")  // 右对齐
        
        // 替换字体样式
        starContent = starContent.replace("<b>", "{bold}")
        starContent = starContent.replace("</b>", "{/bold}")
        
        // 替换文本大小
        starContent = starContent.replace("<w>", "{width2}")
        starContent = starContent.replace("</w>", "{/width2}")
        starContent = starContent.replace("<h>", "{height2}")
        starContent = starContent.replace("</h>", "{/height2}")
        
        // 添加结束标记
        starContent += "\n{cut}"
        
        return starContent
    }
    
    /**
     * 发送测试打印内容
     * @param config 打印机配置
     * @return 测试是否成功
     */
    suspend fun printTest(config: PrinterConfig): Boolean {
        val testContent = """
            {center}{bold}Star打印测试{/bold}{/center}
            {center}----------------{/center}
            品牌: ${config.brand.displayName}
            地址: ${config.address}
            名称: ${config.name}
            {center}----------------{/center}
            {center}测试成功{/center}
            
            
            {cut}
        """.trimIndent()
        
        return printText(testContent)
    }
} 