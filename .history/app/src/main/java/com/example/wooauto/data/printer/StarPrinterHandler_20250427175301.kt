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
        
        // TSP100系列打印机型号标识
        private val TSP100_MODELS = listOf(
            "tsp100", "tsp143", "tsp100iii", "tsp143iii", "tsp143iv"
        )
        
        // 特殊命令集
        private const val CMD_INIT = "\u001b@"     // ESC @ - 初始化打印机
        private const val CMD_LF = "\u000A"        // 换行
        private const val CMD_CR = "\u000D"        // 回车
        private const val CMD_CUT = "\u001dV\u0001" // GS V 1 - 切纸
        private const val CMD_RESET = "\u001bEJL \u001b@" // Star专用重置命令
    }

    private var currentPrinter: StarPrinter? = null
    private var isTSP100Series = false
    private var lastConnectedModel = ""

    /**
     * 连接到Star打印机
     * @param config 打印机配置
     * @return 连接是否成功
     */
    suspend fun connect(config: PrinterConfig): Boolean {
        return try {
            Log.d(TAG, "尝试连接Star打印机: ${config.name} (${config.address})")
            
            // 检测TSP100系列
            isTSP100Series = TSP100_MODELS.any { model -> 
                config.name.lowercase().contains(model) 
            }
            
            if (isTSP100Series) {
                Log.d(TAG, "检测到TSP100系列打印机，启用特殊处理")
            }
            
            lastConnectedModel = config.name
            
            // 根据打印机类型选择接口类型
            val interfaceType = when (config.type) {
                PrinterConfig.PRINTER_TYPE_BLUETOOTH -> InterfaceType.Bluetooth
                PrinterConfig.PRINTER_TYPE_WIFI -> InterfaceType.Lan
                PrinterConfig.PRINTER_TYPE_USB -> InterfaceType.Usb
                else -> return false
            }
            
            // 创建连接设置，为TSP100系列添加特殊设置
            val settings = StarConnectionSettings(interfaceType, config.address)
            
            // 创建打印机实例
            val printer = StarPrinter(settings, context)
            
            // 连接打印机
            withContext(Dispatchers.IO) {
                Log.d(TAG, "正在打开Star打印机连接...")
                printer.openAsync().await()
                Log.d(TAG, "Star打印机连接已打开")
                
                // 检查连接状态
                val status = printer.getStatusAsync().await()
                Log.d(TAG, "Star打印机初始状态: $status")
                
                // 发送初始化命令
                try {
                    val initCommand = if (isTSP100Series) CMD_RESET else CMD_INIT
                    Log.d(TAG, "发送Star打印机初始化命令")
                    printer.printAsync(initCommand).await()
                    delay(200)
                } catch (e: Exception) {
                    Log.w(TAG, "发送初始化命令失败: ${e.message}")
                    // 忽略错误继续
                }
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
                Log.d(TAG, "准备断开Star打印机连接...")
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
                val status = currentPrinter?.getStatusAsync()?.await()
                Log.d(TAG, "当前Star打印机状态: $status")
                status
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
            // 记录打印内容的前100个字符(用于调试)
            val contentPreview = if (content.length > 100) content.substring(0, 100) + "..." else content
            Log.d(TAG, "Star打印机开始打印内容: $contentPreview")
            
            // 尝试获取并记录打印机信息
            try {
                val info = currentPrinter?.information
                Log.d(TAG, "打印机信息: 型号=${info?.model ?: "未知"}, 是否TSP100系列=$isTSP100Series")
            } catch (e: Exception) {
                Log.d(TAG, "无法获取打印机信息: ${e.message}")
            }
            
            // 为TSP100系列添加特殊处理
            val printContent = if (isTSP100Series) {
                // 添加TSP100专用前缀和后缀
                CMD_INIT + content + "\n\n\n" + CMD_CUT
            } else {
                content
            }
            
            // 使用printAsync方法直接打印字符串内容
            withContext(Dispatchers.IO) {
                Log.d(TAG, "发送打印数据...")
                currentPrinter?.printAsync(printContent)?.await()
                Log.d(TAG, "printAsync方法调用完成")
                
                // 对TSP100系列额外发送刷新缓冲区命令
                if (isTSP100Series) {
                    delay(200)
                    Log.d(TAG, "发送TSP100系列缓冲区刷新命令")
                    currentPrinter?.printAsync("\u0018\u000A\u000D")?.await() // CAN + LF + CR
                }
            }
            
            // 等待打印完成
            val waitTime = if (isTSP100Series) 800 else 500
            delay(waitTime)
            
            // 尝试获取打印后的状态
            try {
                val status = getStatus()
                Log.d(TAG, "打印后状态: $status")
                
                // 检查打印机是否报错
                if (status?.hasError == true) {
                    Log.e(TAG, "打印后状态显示有错误: ${status}")
                    return false
                }
            } catch (e: Exception) {
                Log.d(TAG, "无法获取打印后状态: ${e.message}")
            }
            
            Log.d(TAG, "Star打印机打印成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Star打印机打印失败: ${e.message}", e)
            // 记录详细错误堆栈信息
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 转换ESC/POS格式文本到Star命令
     * @param content ESC/POS格式的文本
     * @return 转换后可用于Star打印机的文本
     */
    fun convertEscPosToStarPrnt(content: String): String {
        Log.d(TAG, "开始转换ESC/POS格式到Star格式")
        
        // 在转换前记录原始内容前100个字符
        val contentPreview = if (content.length > 100) content.substring(0, 100) + "..." else content
        Log.d(TAG, "原始内容: $contentPreview")
        
        // 创建StringBuilder以构建转换后的内容
        val converted = StringBuilder()
        
        // 添加初始化命令
        converted.append(CMD_INIT)  // ESC @ 初始化打印机
        
        // 基本替换
        var starContent = content
        
        // 替换文本对齐方式
        starContent = starContent.replace("[L]", "\u001ba0")  // ESC a 0 - 左对齐
        starContent = starContent.replace("[C]", "\u001ba1")  // ESC a 1 - 居中
        starContent = starContent.replace("[R]", "\u001ba2")  // ESC a 2 - 右对齐
        
        // 替换文本样式
        starContent = starContent.replace("<b>", "\u001bE1")  // ESC E 1 - 加粗开启
        starContent = starContent.replace("</b>", "\u001bE0")  // ESC E 0 - 加粗关闭
        
        // TSP100系列特殊处理
        if (isTSP100Series) {
            // TSP100可能需要特殊标记或头部命令
            Log.d(TAG, "应用TSP100系列特殊处理")
            starContent = starContent.replace("\u001d!", "\u001b!") // 字体大小命令转换
        }
        
        // 添加处理后的内容
        converted.append(starContent)
        
        // 在末尾添加换行和切纸命令
        converted.append("\n\n\n\n")
        converted.append("\u001bd\u0003")  // ESC d 3 - 走纸3行
        converted.append(CMD_CUT)  // GS V 1 - 部分切纸
        
        // 记录转换后内容长度
        Log.d(TAG, "转换后内容长度: ${converted.length}字节")
        
        // 返回转换后的内容
        return converted.toString()
    }
    
    /**
     * 发送测试打印内容
     * @param config 打印机配置
     * @return 测试是否成功
     */
    suspend fun printTest(config: PrinterConfig): Boolean {
        Log.d(TAG, "开始执行Star打印机测试")
        
        // 使用Star打印机原生命令格式，不依赖ESC/POS转换
        val testContent = StringBuilder()
        
        // 初始化打印机
        testContent.append(CMD_INIT)  // ESC @
        
        // 居中对齐
        testContent.append("\u001ba1")  // ESC a 1
        
        // 加粗
        testContent.append("\u001bE1")  // ESC E 1
        
        // 文本内容
        testContent.append("=== Star打印测试 ===\n")
        
        // 取消加粗
        testContent.append("\u001bE0")  // ESC E 0
        
        // 添加分隔线
        testContent.append("----------------\n")
        
        // 左对齐
        testContent.append("\u001ba0")  // ESC a 0
        
        // 添加打印机信息
        testContent.append("品牌: ${config.brand.displayName}\n")
        testContent.append("地址: ${config.address}\n")
        testContent.append("名称: ${config.name}\n")
        testContent.append("型号: ${lastConnectedModel}\n")
        testContent.append("TSP100: ${if(isTSP100Series) "是" else "否"}\n")
        
        // 居中对齐
        testContent.append("\u001ba1")  // ESC a 1
        
        // 添加分隔线
        testContent.append("----------------\n")
        
        // 加粗
        testContent.append("\u001bE1")  // ESC E 1
        
        // 测试成功消息
        testContent.append("测试成功\n")
        
        // 取消加粗
        testContent.append("\u001bE0")  // ESC E 0
        
        // 走纸和切纸
        testContent.append("\n\n\n\n")
        testContent.append("\u001bd\u0003")  // ESC d 3 - 走纸3行
        testContent.append(CMD_CUT)  // GS V 1 - 部分切纸
        
        Log.d(TAG, "测试打印内容已生成，长度: ${testContent.length}字节")
        
        return printText(testContent.toString())
    }
    
    /**
     * 使用纯ASCII文本进行简单测试打印
     * 当其他方法都不工作时尝试此方法
     */
    suspend fun printSimpleText(text: String): Boolean {
        Log.d(TAG, "尝试简单文本打印")
        
        if (currentPrinter == null) {
            Log.e(TAG, "打印机未连接")
            return false
        }
        
        try {
            // 构建一个非常简单的ASCII文本
            val simpleText = "*** SIMPLE TEST ***\n\n" + 
                             text + "\n\n" +
                             "****************\n\n\n\n"
            
            Log.d(TAG, "发送简单文本: $simpleText")
            
            // 直接打印纯文本
            withContext(Dispatchers.IO) {
                currentPrinter?.printAsync(simpleText)?.await()
                
                // TSP100系列额外处理
                if (isTSP100Series) {
                    delay(200)
                    // 发送走纸和切纸命令
                    currentPrinter?.printAsync("\n\n\n" + CMD_CUT)?.await()
                }
            }
            
            Log.d(TAG, "简单文本打印完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "简单文本打印失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 直接发送StarLINE指令（适用于TSP100系列）
     * TSP100系列使用StarLINE命令集而非传统的Star命令
     */
    suspend fun printTSP100DirectCommands(): Boolean {
        if (currentPrinter == null || !isTSP100Series) {
            Log.e(TAG, "打印机未连接或非TSP100系列")
            return false
        }
        
        try {
            Log.d(TAG, "发送TSP100直接命令")
            
            // TSP100系列特殊初始化和测试序列
            val specialTSP100Commands = """
                ${CMD_RESET}
                \u001b@
                \u001ba1
                *** TSP100 Direct Test ***
                
                Testing direct commands
                for TSP100III printer
                
                \u001ba0
                RESULT: OK
                
                
                
                ${CMD_CUT}
            """.trimIndent()
            
            withContext(Dispatchers.IO) {
                currentPrinter?.printAsync(specialTSP100Commands)?.await()
                delay(500)
            }
            
            Log.d(TAG, "TSP100直接命令发送完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "TSP100直接命令失败: ${e.message}", e)
            return false
        }
    }
} 