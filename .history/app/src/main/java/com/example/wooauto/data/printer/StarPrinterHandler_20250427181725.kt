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
    private var lastPrinterState: StarPrinterStatus? = null

    /**
     * 连接到Star打印机
     * @param config 打印机配置
     * @return 连接是否成功
     */
    suspend fun connect(config: PrinterConfig): Boolean {
        return try {
            Log.d(TAG, "【DEBUG】尝试连接Star打印机: ${config.name} (${config.address})")
            Log.d(TAG, "【DEBUG】打印机配置: 名称=${config.name}, 地址=${config.address}, 品牌=${config.brand.displayName}, 类型=${config.type}")
            
            // 检测TSP100系列
            isTSP100Series = TSP100_MODELS.any { model -> 
                config.name.lowercase().contains(model) 
            }
            
            if (isTSP100Series) {
                Log.d(TAG, "【DEBUG】检测到TSP100系列打印机，启用特殊处理")
            }
            
            lastConnectedModel = config.name
            
            // 根据打印机类型选择接口类型
            val interfaceType = when (config.type) {
                PrinterConfig.PRINTER_TYPE_BLUETOOTH -> InterfaceType.Bluetooth
                PrinterConfig.PRINTER_TYPE_WIFI -> InterfaceType.Lan
                PrinterConfig.PRINTER_TYPE_USB -> InterfaceType.Usb
                else -> {
                    Log.e(TAG, "【ERROR】不支持的打印机类型: ${config.type}")
                    return false
                }
            }
            
            // 创建连接设置，为TSP100系列添加特殊设置
            val settings = StarConnectionSettings(interfaceType, config.address)
            Log.d(TAG, "【DEBUG】连接设置: 接口=${interfaceType.name}, 地址=${config.address}")
            
            // 创建打印机实例
            Log.d(TAG, "【DEBUG】创建StarPrinter实例...")
            val printer = StarPrinter(settings, context)
            
            // 连接打印机
            withContext(Dispatchers.IO) {
                Log.d(TAG, "【DEBUG】正在打开Star打印机连接...")
                try {
                    printer.openAsync().await()
                    Log.d(TAG, "【DEBUG】Star打印机连接已打开")
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】打开打印机连接失败: ${e.message}")
                    throw e
                }
                
                // 检查连接状态
                try {
                    val status = printer.getStatusAsync().await()
                    lastPrinterState = status
                    Log.d(TAG, "【DEBUG】Star打印机初始状态: $status")
                    Log.d(TAG, "【DEBUG】打印机状态详情: 在线=${status?.isOnline}, " +
                          "错误=${status?.hasError}, " +
                          "纸尽=${status?.hasPaperEmpty}, " +
                          "纸将尽=${status?.hasPaperNearEmpty}, " +
                          "盖子打开=${status?.hasCoverOpen}")
                    
                    if (status?.hasError == true) {
                        Log.e(TAG, "【ERROR】打印机状态异常，无法继续")
                        if (status.hasPaperEmpty) {
                            Log.e(TAG, "【ERROR】打印机缺纸，请检查纸张")
                        }
                        if (status.hasCoverOpen) {
                            Log.e(TAG, "【ERROR】打印机盖子打开，请关闭")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】获取打印机状态失败: ${e.message}")
                    // 继续尝试连接
                }
                
                // 发送初始化命令
                try {
                    val initCommand = if (isTSP100Series) CMD_RESET else CMD_INIT
                    Log.d(TAG, "【DEBUG】发送Star打印机初始化命令: ${if (isTSP100Series) "TSP100专用命令" else "标准命令"}")
                    printer.printAsync(initCommand).await()
                    Log.d(TAG, "【DEBUG】初始化命令发送成功")
                    delay(200L)
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】发送初始化命令失败: ${e.message}")
                    // 忽略错误继续
                }
            }
            
            currentPrinter = printer
            Log.d(TAG, "【SUCCESS】Star打印机连接成功")
            true
        } catch (e: StarIO10Exception) {
            Log.e(TAG, "【ERROR】Star打印机连接失败: ${e.message}", e)
            Log.e(TAG, "【ERROR】Star SDK错误详情: ${e.javaClass.simpleName}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】Star打印机连接异常: ${e.message}", e)
            Log.e(TAG, "【ERROR】异常类型: ${e.javaClass.simpleName}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 断开Star打印机连接
     */
    suspend fun disconnect() {
        try {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "【DEBUG】准备断开Star打印机连接...")
                currentPrinter?.closeAsync()?.await()
            }
            currentPrinter = null
            lastPrinterState = null
            Log.d(TAG, "【INFO】Star打印机断开连接")
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】断开Star打印机连接失败: ${e.message}", e)
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
                lastPrinterState = status
                Log.d(TAG, "【DEBUG】当前Star打印机状态: $status")
                if (status != null) {
                    Log.d(TAG, "【DEBUG】详细状态: 在线=${status.isOnline}, " +
                          "错误=${status.hasError}, " +
                          "纸尽=${status.hasPaperEmpty}, " +
                          "纸将尽=${status.hasPaperNearEmpty}, " +
                          "盖子打开=${status.hasCoverOpen}")
                }
                status
            }
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】获取Star打印机状态失败: ${e.message}", e)
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
            Log.e(TAG, "【ERROR】打印机未连接")
            return false
        }
        
        try {
            // 记录打印内容的前100个字符(用于调试)
            val contentPreview = if (content.length > 100) content.substring(0, 100) + "..." else content
            Log.d(TAG, "【DEBUG】Star打印机开始打印内容，长度: ${content.length}字节")
            Log.d(TAG, "【DEBUG】内容预览: $contentPreview")
            
            // 尝试获取并记录打印机信息
            try {
                val info = currentPrinter?.information
                Log.d(TAG, "【DEBUG】打印机信息: 型号=${info?.model ?: "未知"}, 是否TSP100系列=$isTSP100Series")
                Log.d(TAG, "【DEBUG】打印机固件: ${info?.firmwareVersion ?: "未知"}")
            } catch (e: Exception) {
                Log.e(TAG, "【ERROR】无法获取打印机信息: ${e.message}")
            }
            
            // 打印前检查打印机状态
            try {
                val beforeStatus = getStatus()
                if (beforeStatus?.hasError == true) {
                    Log.e(TAG, "【ERROR】打印机状态异常，无法打印")
                    if (beforeStatus.hasPaperEmpty) {
                        Log.e(TAG, "【ERROR】打印机缺纸，请检查纸张")
                        return false
                    }
                    if (beforeStatus.hasCoverOpen) {
                        Log.e(TAG, "【ERROR】打印机盖子打开，请关闭")
                        return false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "【ERROR】打印前状态检查失败: ${e.message}")
                // 继续尝试打印
            }
            
            // 为TSP100系列添加特殊处理
            val printContent = if (isTSP100Series) {
                // 添加TSP100专用前缀和后缀
                Log.d(TAG, "【DEBUG】添加TSP100专用命令前缀和后缀")
                CMD_INIT + content + "\n\n\n" + CMD_CUT
            } else {
                content
            }
            
            // 使用printAsync方法直接打印字符串内容
            withContext(Dispatchers.IO) {
                Log.d(TAG, "【DEBUG】正在发送打印数据...")
                try {
                    currentPrinter?.printAsync(printContent)?.await()
                    Log.d(TAG, "【DEBUG】printAsync方法调用完成")
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】发送打印数据失败: ${e.message}")
                    throw e
                }
                
                // 对TSP100系列额外发送刷新缓冲区命令
                if (isTSP100Series) {
                    Log.d(TAG, "【DEBUG】延迟200ms后发送TSP100刷新命令")
                    delay(200L)
                    Log.d(TAG, "【DEBUG】发送TSP100系列缓冲区刷新命令")
                    try {
                        currentPrinter?.printAsync("\u0018\u000A\u000D")?.await() // CAN + LF + CR
                        Log.d(TAG, "【DEBUG】刷新命令发送成功")
                    } catch (e: Exception) {
                        Log.e(TAG, "【ERROR】发送刷新命令失败: ${e.message}")
                        // 继续执行后续步骤
                    }
                }
            }
            
            // 等待打印完成
            val waitTime = if (isTSP100Series) 800L else 500L
            Log.d(TAG, "【DEBUG】等待${waitTime}ms让打印机完成打印")
            delay(waitTime)
            
            // 尝试获取打印后的状态
            try {
                val status = getStatus()
                Log.d(TAG, "【DEBUG】打印后状态: $status")
                
                // 检查打印机是否报错
                if (status?.hasError == true) {
                    Log.e(TAG, "【ERROR】打印后状态显示有错误")
                    if (status.hasPaperEmpty) {
                        Log.e(TAG, "【ERROR】打印机缺纸，打印未完成")
                    }
                    if (status.hasCoverOpen) {
                        Log.e(TAG, "【ERROR】打印机盖子打开，打印中断")
                    }
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "【ERROR】无法获取打印后状态: ${e.message}")
                // 继续尝试
            }
            
            // 再次发送一个切纸命令，确保打印结束
            try {
                withContext(Dispatchers.IO) {
                    if (isTSP100Series) {
                        Log.d(TAG, "【DEBUG】发送TSP100最终切纸命令")
                        currentPrinter?.printAsync("\n" + CMD_CUT)?.await()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "【ERROR】发送最终切纸命令失败: ${e.message}")
                // 不影响打印结果
            }
            
            Log.d(TAG, "【SUCCESS】Star打印机打印成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】Star打印机打印失败: ${e.message}", e)
            Log.e(TAG, "【ERROR】异常类型: ${e.javaClass.simpleName}")
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
        Log.d(TAG, "【DEBUG】开始转换ESC/POS格式到Star格式")
        
        // 在转换前记录原始内容前100个字符
        val contentPreview = if (content.length > 100) content.substring(0, 100) + "..." else content
        Log.d(TAG, "【DEBUG】原始内容预览: $contentPreview")
        
        // 创建StringBuilder以构建转换后的内容
        val converted = StringBuilder()
        
        // 添加初始化命令
        Log.d(TAG, "【DEBUG】添加初始化命令")
        converted.append(CMD_INIT)  // ESC @ 初始化打印机
        
        // 基本替换
        var starContent = content
        
        // 替换文本对齐方式
        Log.d(TAG, "【DEBUG】替换文本对齐标记")
        starContent = starContent.replace("[L]", "\u001ba0")  // ESC a 0 - 左对齐
        starContent = starContent.replace("[C]", "\u001ba1")  // ESC a 1 - 居中
        starContent = starContent.replace("[R]", "\u001ba2")  // ESC a 2 - 右对齐
        
        // 替换文本样式
        Log.d(TAG, "【DEBUG】替换文本样式标记")
        starContent = starContent.replace("<b>", "\u001bE1")  // ESC E 1 - 加粗开启
        starContent = starContent.replace("</b>", "\u001bE0")  // ESC E 0 - 加粗关闭
        
        // TSP100系列特殊处理
        if (isTSP100Series) {
            // TSP100可能需要特殊标记或头部命令
            Log.d(TAG, "【DEBUG】应用TSP100系列特殊处理")
            starContent = starContent.replace("\u001d!", "\u001b!") // 字体大小命令转换
        }
        
        // 添加处理后的内容
        converted.append(starContent)
        
        // 在末尾添加换行和切纸命令
        Log.d(TAG, "【DEBUG】添加结尾命令(换行和切纸)")
        converted.append("\n\n\n\n")
        converted.append("\u001bd\u0003")  // ESC d 3 - 走纸3行
        converted.append(CMD_CUT)  // GS V 1 - 部分切纸
        
        // 记录转换后内容长度
        Log.d(TAG, "【DEBUG】转换后内容长度: ${converted.length}字节")
        
        // 返回转换后的内容
        return converted.toString()
    }
    
    /**
     * 发送测试打印内容
     * @param config 打印机配置
     * @return 测试是否成功
     */
    suspend fun printTest(config: PrinterConfig): Boolean {
        Log.d(TAG, "【INFO】开始执行Star打印机测试")
        
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
        
        // 添加状态信息
        val status = lastPrinterState
        testContent.append("打印机状态: ${if(status?.isOnline == true) "在线" else "离线"}\n")
        if (status != null) {
            if (status.hasError) testContent.append("错误状态: 是\n")
            if (status.hasPaperEmpty) testContent.append("缺纸: 是\n")
            if (status.hasCoverOpen) testContent.append("盖子打开: 是\n")
        }
        
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
        
        Log.d(TAG, "【DEBUG】测试打印内容已生成，长度: ${testContent.length}字节")
        
        return printText(testContent.toString())
    }
    
    /**
     * 使用纯ASCII文本进行简单测试打印
     * 当其他方法都不工作时尝试此方法
     */
    suspend fun printSimpleText(text: String): Boolean {
        Log.d(TAG, "【INFO】尝试简单文本打印")
        
        if (currentPrinter == null) {
            Log.e(TAG, "【ERROR】打印机未连接")
            return false
        }
        
        try {
            // 构建一个非常简单的ASCII文本
            val simpleText = "*** SIMPLE TEST ***\n\n" + 
                             text + "\n\n" +
                             "****************\n\n\n\n"
            
            Log.d(TAG, "【DEBUG】发送简单文本，长度: ${simpleText.length}字节")
            
            // 直接打印纯文本
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "【DEBUG】调用printAsync发送简单文本")
                    currentPrinter?.printAsync(simpleText)?.await()
                    Log.d(TAG, "【DEBUG】简单文本发送成功")
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】发送简单文本失败: ${e.message}")
                    throw e
                }
                
                // TSP100系列额外处理
                if (isTSP100Series) {
                    Log.d(TAG, "【DEBUG】等待200ms后发送TSP100额外命令")
                    delay(200L)
                    // 发送走纸和切纸命令
                    try {
                        Log.d(TAG, "【DEBUG】发送TSP100走纸和切纸命令")
                        currentPrinter?.printAsync("\n\n\n" + CMD_CUT)?.await()
                        Log.d(TAG, "【DEBUG】TSP100额外命令发送成功")
                    } catch (e: Exception) {
                        Log.e(TAG, "【ERROR】发送TSP100额外命令失败: ${e.message}")
                        // 继续执行
                    }
                }
            }
            
            Log.d(TAG, "【SUCCESS】简单文本打印完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】简单文本打印失败: ${e.message}", e)
            Log.e(TAG, "【ERROR】异常类型: ${e.javaClass.simpleName}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 直接发送StarLINE指令（适用于TSP100系列）
     * TSP100系列使用StarLINE命令集而非传统的Star命令
     */
    suspend fun printTSP100DirectCommands(): Boolean {
        if (currentPrinter == null || !isTSP100Series) {
            Log.e(TAG, "【ERROR】打印机未连接或非TSP100系列")
            return false
        }
        
        try {
            Log.d(TAG, "【INFO】发送TSP100直接命令")
            
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
            
            Log.d(TAG, "【DEBUG】TSP100命令序列长度: ${specialTSP100Commands.length}字节")
            
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "【DEBUG】发送TSP100特殊命令序列...")
                    currentPrinter?.printAsync(specialTSP100Commands)?.await()
                    Log.d(TAG, "【DEBUG】TSP100特殊命令发送成功")
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】发送TSP100特殊命令失败: ${e.message}")
                    throw e
                }
                Log.d(TAG, "【DEBUG】等待500ms")
                delay(500L)
            }
            
            Log.d(TAG, "【SUCCESS】TSP100直接命令发送完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】TSP100直接命令失败: ${e.message}", e)
            Log.e(TAG, "【ERROR】异常类型: ${e.javaClass.simpleName}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 发送直接的切纸命令，单独测试切纸功能
     */
    suspend fun testPaperCut(): Boolean {
        if (currentPrinter == null) {
            Log.e(TAG, "【ERROR】打印机未连接")
            return false
        }
        
        try {
            Log.d(TAG, "【INFO】测试切纸命令")
            
            // 发送走纸和切纸命令
            val cutCommand = "\n\n\n\n" + 
                            "\u001bd\u0003" +  // ESC d 3 - 走纸3行
                            CMD_CUT            // GS V 1 - 切纸
            
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "【DEBUG】发送走纸和切纸命令...")
                    currentPrinter?.printAsync(cutCommand)?.await()
                    Log.d(TAG, "【DEBUG】切纸命令发送成功")
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】发送切纸命令失败: ${e.message}")
                    throw e
                }
                delay(300L)
            }
            
            Log.d(TAG, "【SUCCESS】切纸命令测试完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】切纸命令测试失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 强制刷新打印缓冲区，确保所有命令被执行
     */
    suspend fun flushBuffer(): Boolean {
        if (currentPrinter == null) {
            Log.e(TAG, "【ERROR】打印机未连接")
            return false
        }
        
        try {
            Log.d(TAG, "【INFO】刷新打印缓冲区")
            
            withContext(Dispatchers.IO) {
                try {
                    // 初始化打印机
                    Log.d(TAG, "【DEBUG】发送初始化命令...")
                    currentPrinter?.printAsync(CMD_INIT)?.await()
                    
                    // 发送清除缓冲区命令
                    Log.d(TAG, "【DEBUG】发送清除缓冲区命令...")
                    currentPrinter?.printAsync("\u0018")?.await() // CAN
                    
                    // 发送刷新触发命令
                    Log.d(TAG, "【DEBUG】发送刷新触发命令...")
                    currentPrinter?.printAsync("\u000A\u000D\u000A")?.await() // LF CR LF
                    
                    Log.d(TAG, "【DEBUG】刷新命令发送成功")
                } catch (e: Exception) {
                    Log.e(TAG, "【ERROR】发送刷新命令失败: ${e.message}")
                    throw e
                }
                delay(200L)
            }
            
            Log.d(TAG, "【SUCCESS】刷新缓冲区完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "【ERROR】刷新缓冲区失败: ${e.message}", e)
            return false
        }
    }
} 