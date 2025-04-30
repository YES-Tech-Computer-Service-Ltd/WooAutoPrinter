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
        // 显示当前连接请求的详细信息
        val logPrefix = "【Star连接】"
        Log.d(TAG, "$logPrefix ------开始连接Star打印机------")
        Log.d(TAG, "$logPrefix 名称: ${config.name}")
        Log.d(TAG, "$logPrefix 地址: ${config.address}")
        Log.d(TAG, "$logPrefix 类型: ${config.type}")
        Log.d(TAG, "$logPrefix 品牌: ${config.brand.displayName}")
        Log.d(TAG, "$logPrefix 宽度: ${config.paperWidth}mm")

        return try {
            // 检测TSP100系列
            isTSP100Series = TSP100_MODELS.any { model -> 
                config.name.lowercase().contains(model) 
            }
            
            if (isTSP100Series) {
                Log.d(TAG, "$logPrefix 检测到TSP100系列打印机，启用特殊处理")
            }
            
            lastConnectedModel = config.name
            
            // 根据打印机类型选择接口类型
            val interfaceType = when (config.type) {
                PrinterConfig.PRINTER_TYPE_BLUETOOTH -> InterfaceType.Bluetooth
                PrinterConfig.PRINTER_TYPE_WIFI -> InterfaceType.Lan
                PrinterConfig.PRINTER_TYPE_USB -> InterfaceType.Usb
                else -> {
                    Log.e(TAG, "$logPrefix 不支持的打印机类型: ${config.type}")
                    return false
                }
            }
            
            // 创建连接设置，为TSP100系列添加特殊设置
            val settings = StarConnectionSettings(interfaceType, config.address)
            Log.d(TAG, "$logPrefix 接口类型: $interfaceType, 使用设置: $settings")
            
            // 创建打印机实例
            Log.d(TAG, "$logPrefix 创建StarPrinter实例")
            val printer = StarPrinter(settings, context)
            
            // 连接打印机
            withContext(Dispatchers.IO) {
                Log.d(TAG, "$logPrefix 开始连接 - 调用openAsync()...")
                try {
                    printer.openAsync().await()
                    Log.d(TAG, "$logPrefix 打印机连接已成功打开")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 打开连接时出错: ${e.message}", e)
                    throw e
                }
                
                // 获取并记录打印机完整状态
                try {
                    val status = printer.getStatusAsync().await()
                    Log.d(TAG, "$logPrefix 初始状态: $status")
                    Log.d(TAG, "$logPrefix 状态详情 - " + 
                         "有错误: ${status?.hasError}, " +
                         "纸盖状态: ${status?.coverOpen}")
                } catch (e: Exception) {
                    Log.w(TAG, "$logPrefix 无法获取打印机状态: ${e.message}", e)
                }
                
                // 发送初始化命令
                try {
                    val initCommand = if (isTSP100Series) {
                        Log.d(TAG, "$logPrefix 使用TSP100系列特殊初始化命令: CMD_RESET")
                        CMD_RESET  // Star专用重置命令
                    } else {
                        Log.d(TAG, "$logPrefix 使用标准初始化命令: CMD_INIT")
                        CMD_INIT   // 标准ESC @ 命令
                    }
                    
                    // 记录发送的初始化命令的十六进制表示
                    val hexInit = initCommand.toByteArray().joinToString("") { 
                        String.format("%02X", it.toInt() and 0xFF) 
                    }
                    Log.d(TAG, "$logPrefix 发送初始化命令 (HEX): $hexInit")
                    
                    printer.printAsync(initCommand).await()
                    Log.d(TAG, "$logPrefix 初始化命令已发送")
                    
                    // 让初始化命令生效
                    delay(300L)
                    Log.d(TAG, "$logPrefix 等待初始化命令处理完成")
                } catch (e: Exception) {
                    Log.w(TAG, "$logPrefix 发送初始化命令失败: ${e.message}", e)
                    // 忽略错误继续
                }
                
                // 连接后立即测试打印机通信
                try {
                    Log.d(TAG, "$logPrefix 发送测试通信命令")
                    // 发送简单的状态请求命令
                    printer.getStatusAsync().await()
                    Log.d(TAG, "$logPrefix 测试通信成功")
                } catch (e: Exception) {
                    Log.w(TAG, "$logPrefix 测试通信失败: ${e.message}", e)
                }
            }
            
            currentPrinter = printer
            Log.d(TAG, "$logPrefix ------Star打印机连接成功------")
            
            // 连接成功后尝试获取打印机信息
            try {
                withContext(Dispatchers.IO) {
                    val info = printer.information
                    Log.d(TAG, "$logPrefix 打印机信息: 型号=${info?.model ?: "未知"}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "$logPrefix 获取打印机信息失败: ${e.message}", e)
            }
            
            true
        } catch (e: StarIO10Exception) {
            Log.e(TAG, "$logPrefix Star打印机连接失败: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常类型: StarIO10Exception, 代码: ${e.errorCode}, 详情: ${e.localizedMessage}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix Star打印机连接异常: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常类型: ${e.javaClass.simpleName}, 详情: ${e.stackTraceToString()}")
            false
        }
    }
    
    /**
     * 断开Star打印机连接
     */
    suspend fun disconnect() {
        val logPrefix = "【Star断开】"
        Log.d(TAG, "$logPrefix 开始断开Star打印机连接...")
        
        try {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "$logPrefix 调用closeAsync()...")
                    currentPrinter?.closeAsync()?.await()
                    Log.d(TAG, "$logPrefix closeAsync()调用成功")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 断开连接时出错: ${e.message}", e)
                    throw e
                }
            }
            currentPrinter = null
            Log.d(TAG, "$logPrefix Star打印机断开连接完成")
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 断开Star打印机连接失败: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常类型: ${e.javaClass.simpleName}, 详情: ${e.stackTraceToString()}")
        }
    }
    
    /**
     * 获取Star打印机状态
     * @return 打印机状态
     */
    suspend fun getStatus(): StarPrinterStatus? {
        val logPrefix = "【Star状态】"
        Log.d(TAG, "$logPrefix 开始获取Star打印机状态...")
        
        return try {
            withContext(Dispatchers.IO) {
                try {
                    val status = currentPrinter?.getStatusAsync()?.await()
                    Log.d(TAG, "$logPrefix 当前Star打印机状态: $status")
                    
                    if (status != null) {
                        Log.d(TAG, "$logPrefix 状态详情 - " + 
                              "有错误: ${status.hasError}, " +
                              "纸盖状态: ${status.coverOpen}")
                    } else {
                        Log.w(TAG, "$logPrefix 获取到空状态")
                    }
                    
                    status
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 获取状态时出错: ${e.message}", e)
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 获取Star打印机状态失败: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常类型: ${e.javaClass.simpleName}")
            null
        }
    }
    
    /**
     * 打印文本内容
     * @param content 要打印的内容
     * @return 打印是否成功
     */
    suspend fun printText(content: String): Boolean {
        val logPrefix = "【Star打印】"
        
        if (currentPrinter == null) {
            Log.e(TAG, "$logPrefix 打印机未连接")
            return false
        }
        
        Log.d(TAG, "$logPrefix ------开始打印文本------")
        
        try {
            // 记录打印内容的前100个字符和长度(用于调试)
            val contentPreview = if (content.length > 100) content.substring(0, 100) + "..." else content
            Log.d(TAG, "$logPrefix 打印内容预览: $contentPreview")
            Log.d(TAG, "$logPrefix 打印内容总长度: ${content.length}字符")
            
            // 尝试获取并记录打印机信息
            try {
                val info = currentPrinter?.information
                Log.d(TAG, "$logPrefix 打印机信息: 型号=${info?.model ?: "未知"}, " +
                      "是否TSP100系列=$isTSP100Series")
            } catch (e: Exception) {
                Log.d(TAG, "$logPrefix 无法获取打印机信息: ${e.message}")
            }
            
            // 为TSP100系列添加特殊处理
            val printContent = if (isTSP100Series) {
                // 添加TSP100专用前缀和后缀
                Log.d(TAG, "$logPrefix 应用TSP100系列特殊处理 - 添加初始化和切纸命令")
                CMD_INIT + content + "\n\n\n" + CMD_CUT
            } else {
                content
            }
            
            // 记录完整处理后的内容长度
            Log.d(TAG, "$logPrefix 处理后内容长度: ${printContent.length}字符")
            
            // 记录内容的十六进制前50个字节(用于调试)
            val hexPreview = printContent.toByteArray().take(50).joinToString("") { 
                String.format("%02X ", it.toInt() and 0xFF) 
            }
            Log.d(TAG, "$logPrefix 打印内容前50字节(HEX): $hexPreview")
            
            // 使用printAsync方法直接打印字符串内容
            withContext(Dispatchers.IO) {
                Log.d(TAG, "$logPrefix 开始发送打印数据...")
                
                try {
                    // 打印前获取状态
                    val beforeStatus = currentPrinter?.getStatusAsync()?.await()
                    Log.d(TAG, "$logPrefix 打印前状态: $beforeStatus")
                    
                    // 开始打印
                    Log.d(TAG, "$logPrefix 调用printAsync()...")
                    currentPrinter?.printAsync(printContent)?.await()
                    Log.d(TAG, "$logPrefix printAsync()调用完成")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送打印数据时出错: ${e.message}", e)
                    throw e
                }
                
                // 对TSP100系列额外发送刷新缓冲区命令
                if (isTSP100Series) {
                    try {
                        Log.d(TAG, "$logPrefix 等待200ms...")
                        delay(200L)
                        Log.d(TAG, "$logPrefix 发送TSP100系列缓冲区刷新命令")
                        val flushCmd = "\u0018\u000A\u000D" // CAN + LF + CR
                        Log.d(TAG, "$logPrefix 刷新命令(HEX): ${flushCmd.toByteArray().joinToString("") { 
                                String.format("%02X ", it.toInt() and 0xFF) }}")
                        currentPrinter?.printAsync(flushCmd)?.await()
                        Log.d(TAG, "$logPrefix 缓冲区刷新命令已发送")
                    } catch (e: Exception) {
                        Log.e(TAG, "$logPrefix 发送刷新命令时出错: ${e.message}", e)
                        // 继续执行，不因刷新命令失败而中断整个打印过程
                    }
                }
            }
            
            // 等待打印完成
            val waitTime = if (isTSP100Series) 800L else 500L
            Log.d(TAG, "$logPrefix 等待打印完成，延时: ${waitTime}ms")
            delay(waitTime)
            Log.d(TAG, "$logPrefix 等待完成")
            
            // 尝试获取打印后的状态
            try {
                val status = getStatus()
                Log.d(TAG, "$logPrefix 打印后状态: $status")
                
                // 检查打印机是否报错
                if (status?.hasError == true) {
                    Log.e(TAG, "$logPrefix 打印后状态显示有错误: ${status}, 错误代码: ${status?.hasError}")
                    return false
                }
            } catch (e: Exception) {
                Log.d(TAG, "$logPrefix 无法获取打印后状态: ${e.message}")
            }
            
            Log.d(TAG, "$logPrefix ------Star打印机打印成功------")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix Star打印机打印失败: ${e.message}", e)
            // 记录详细错误堆栈信息
            Log.e(TAG, "$logPrefix 异常详情: ${e.stackTraceToString()}")
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
        val logPrefix = "【Star转换】"
        Log.d(TAG, "$logPrefix 开始转换ESC/POS格式到Star格式")
        
        // 在转换前记录原始内容前100个字符
        val contentPreview = if (content.length > 100) content.substring(0, 100) + "..." else content
        Log.d(TAG, "$logPrefix 原始内容预览: $contentPreview")
        Log.d(TAG, "$logPrefix 原始内容长度: ${content.length}字符")
        
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
            Log.d(TAG, "$logPrefix 应用TSP100系列特殊处理")
            starContent = starContent.replace("\u001d!", "\u001b!") // 字体大小命令转换
            // 添加额外的TSP100特殊替换
            starContent = starContent.replace("\u001dV", "\u001bV") // 切纸命令转换
        }
        
        // 添加处理后的内容
        converted.append(starContent)
        
        // 在末尾添加换行和切纸命令
        converted.append("\n\n\n\n")
        converted.append("\u001bd\u0003")  // ESC d 3 - 走纸3行
        converted.append(CMD_CUT)  // GS V 1 - 部分切纸
        
        // 记录转换后内容长度
        Log.d(TAG, "$logPrefix 转换后内容长度: ${converted.length}字节")
        
        // 记录转换后内容十六进制前50个字节
        val hexPreview = converted.toString().toByteArray().take(50).joinToString("") { 
            String.format("%02X ", it.toInt() and 0xFF) 
        }
        Log.d(TAG, "$logPrefix 转换后内容前50字节(HEX): $hexPreview")
        
        // 返回转换后的内容
        return converted.toString()
    }
    
    /**
     * 发送测试打印内容
     * @param config 打印机配置
     * @return 测试是否成功
     */
    suspend fun printTest(config: PrinterConfig): Boolean {
        val logPrefix = "【Star测试】"
        Log.d(TAG, "$logPrefix 开始执行Star打印机测试")
        
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
        testContent.append("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n")
        
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
        
        Log.d(TAG, "$logPrefix 测试打印内容已生成，长度: ${testContent.length}字节")
        
        return printText(testContent.toString())
    }
    
    /**
     * 使用纯ASCII文本进行简单测试打印
     * 当其他方法都不工作时尝试此方法
     */
    suspend fun printSimpleText(text: String): Boolean {
        val logPrefix = "【Star简单打印】"
        Log.d(TAG, "$logPrefix 尝试简单文本打印")
        
        if (currentPrinter == null) {
            Log.e(TAG, "$logPrefix 打印机未连接")
            return false
        }
        
        try {
            // 构建一个非常简单的ASCII文本
            val simpleText = "*** SIMPLE TEST ***\n\n" + 
                             text + "\n\n" +
                             "****************\n\n\n\n"
            
            Log.d(TAG, "$logPrefix 简单文本长度: ${simpleText.length}字符")
            Log.d(TAG, "$logPrefix 发送简单文本: $simpleText")
            
            // 直接打印纯文本
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "$logPrefix 调用printAsync()发送简单文本...")
                    currentPrinter?.printAsync(simpleText)?.await()
                    Log.d(TAG, "$logPrefix 简单文本发送完成")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送简单文本时出错: ${e.message}", e)
                    throw e
                }
                
                // TSP100系列额外处理
                if (isTSP100Series) {
                    try {
                        Log.d(TAG, "$logPrefix 等待200ms后发送TSP100额外命令...")
                        delay(200L)
                        // 发送走纸和切纸命令
                        Log.d(TAG, "$logPrefix 发送走纸和切纸命令")
                        currentPrinter?.printAsync("\n\n\n" + CMD_CUT)?.await()
                        Log.d(TAG, "$logPrefix 走纸和切纸命令已发送")
                    } catch (e: Exception) {
                        Log.e(TAG, "$logPrefix 发送走纸和切纸命令时出错: ${e.message}", e)
                        // 继续执行，不因额外命令失败而中断整个打印过程
                    }
                }
            }
            
            Log.d(TAG, "$logPrefix 简单文本打印完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 简单文本打印失败: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常详情: ${e.stackTraceToString()}")
            return false
        }
    }
    
    /**
     * 直接发送StarLINE指令（适用于TSP100系列）
     * TSP100系列使用StarLINE命令集而非传统的Star命令
     */
    suspend fun printTSP100DirectCommands(): Boolean {
        val logPrefix = "【TSP100直接命令】"
        
        if (currentPrinter == null || !isTSP100Series) {
            Log.e(TAG, "$logPrefix 打印机未连接或非TSP100系列")
            return false
        }
        
        try {
            Log.d(TAG, "$logPrefix 开始发送TSP100直接命令测试")
            
            // TSP100系列特殊初始化和测试序列
            val specialTSP100Commands = """
                ${CMD_RESET}
                \u001b@
                \u001ba1
                *** TSP100 Direct Test ***
                
                Testing direct commands
                for TSP100III printer
                
                Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}
                
                \u001ba0
                Model: ${lastConnectedModel}
                RESULT: OK
                
                
                
                ${CMD_CUT}
            """.trimIndent()
            
            // 记录命令十六进制
            val hexCommands = specialTSP100Commands.toByteArray().take(50).joinToString("") { 
                String.format("%02X ", it.toInt() and 0xFF) 
            }
            Log.d(TAG, "$logPrefix 命令前50字节(HEX): $hexCommands")
            
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "$logPrefix 调用printAsync()发送TSP100直接命令...")
                    currentPrinter?.printAsync(specialTSP100Commands)?.await()
                    Log.d(TAG, "$logPrefix TSP100直接命令已发送")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送TSP100直接命令时出错: ${e.message}", e)
                    throw e
                }
                
                Log.d(TAG, "$logPrefix 等待500ms...")
                delay(500L)
                Log.d(TAG, "$logPrefix 等待完成")
                
                // 额外发送刷新缓冲区和状态检查
                try {
                    Log.d(TAG, "$logPrefix 发送额外刷新命令")
                    currentPrinter?.printAsync("\u0018\n\n\n")?.await()
                    delay(200L)
                    
                    // 检查状态
                    val status = currentPrinter?.getStatusAsync()?.await()
                    Log.d(TAG, "$logPrefix 命令发送后状态: $status")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 额外处理时出错: ${e.message}", e)
                    // 忽略错误继续
                }
            }
            
            Log.d(TAG, "$logPrefix TSP100直接命令发送完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix TSP100直接命令失败: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常详情: ${e.stackTraceToString()}")
            return false
        }
    }
    
    /**
     * TSP100专用清理和测试
     * 用于解决TSP100III可能卡死的问题
     */
    suspend fun tsp100Reset(): Boolean {
        val logPrefix = "【TSP100重置】"
        
        if (currentPrinter == null) {
            Log.e(TAG, "$logPrefix 打印机未连接")
            return false
        }
        
        try {
            Log.d(TAG, "$logPrefix 开始执行TSP100重置流程")
            
            withContext(Dispatchers.IO) {
                // 1. 检查状态
                try {
                    val status = currentPrinter?.getStatusAsync()?.await()
                    Log.d(TAG, "$logPrefix 重置前状态: $status")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 获取状态时出错: ${e.message}", e)
                    // 继续尝试重置
                }
                
                // 2. 发送清除缓冲区命令
                try {
                    Log.d(TAG, "$logPrefix 发送清除缓冲区命令")
                    currentPrinter?.printAsync("\u0018")?.await() // CAN
                    delay(100L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 清除缓冲区时出错: ${e.message}", e)
                    // 继续尝试其他命令
                }
                
                // 3. 发送重置命令序列
                try {
                    Log.d(TAG, "$logPrefix 发送重置命令序列")
                    // Star专用重置序列
                    currentPrinter?.printAsync("\u001bEJL\n\u001b@\n\u001bRS\n")?.await()
                    delay(300L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送重置命令时出错: ${e.message}", e)
                    // 继续尝试简单命令
                }
                
                // 4. 发送简单初始化
                try {
                    Log.d(TAG, "$logPrefix 发送简单初始化命令")
                    currentPrinter?.printAsync("\u001b@\n")?.await() // ESC @
                    delay(200L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送初始化命令时出错: ${e.message}", e)
                    // 继续执行
                }
                
                // 5. 发送测试文本
                try {
                    Log.d(TAG, "$logPrefix 发送测试文本")
                    val testText = "\u001b@\n\u001ba1\nTSP100 RESET TEST\n\n\n\n" + CMD_CUT
                    currentPrinter?.printAsync(testText)?.await()
                    delay(500L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送测试文本时出错: ${e.message}", e)
                    return@withContext false
                }
                
                // 6. 再次检查状态
                try {
                    val status = currentPrinter?.getStatusAsync()?.await()
                    Log.d(TAG, "$logPrefix 重置后状态: $status")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 获取重置后状态时出错: ${e.message}", e)
                    // 忽略状态检查错误
                }
            }
            
            Log.d(TAG, "$logPrefix TSP100重置流程完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix TSP100重置失败: ${e.message}", e)
            Log.e(TAG, "$logPrefix 异常详情: ${e.stackTraceToString()}")
            return false
        }
    }
} 