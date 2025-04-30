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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        
        // Star原生命令集
        private const val STAR_CMD_INIT = "\u001b\u0040"              // ESC @ - 初始化打印机
        private const val STAR_CMD_RESET = "\u001b\u0040"            // ESC @ - 软件重置
        private const val STAR_CMD_ENCODING = "\u001b\u001c\u0070"   // ESC FS p - 设置代码页
        private const val STAR_CMD_ALIGN_LEFT = "\u001b\u0061\u0000" // ESC a 0 - 左对齐
        private const val STAR_CMD_ALIGN_CENTER = "\u001b\u0061\u0001" // ESC a 1 - 居中对齐
        private const val STAR_CMD_ALIGN_RIGHT = "\u001b\u0061\u0002"  // ESC a 2 - 右对齐
        private const val STAR_CMD_BOLD_ON = "\u001b\u0045"          // ESC E - 加粗开启
        private const val STAR_CMD_BOLD_OFF = "\u001b\u0046"         // ESC F - 加粗关闭
        private const val STAR_CMD_DOUBLE_WIDTH = "\u001b\u0057\u0001" // ESC W 1 - 双倍宽度
        private const val STAR_CMD_NORMAL_WIDTH = "\u001b\u0057\u0000" // ESC W 0 - 正常宽度
        private const val STAR_CMD_FEED_LINE = "\u000A"              // LF - 换行
        private const val STAR_CMD_CUT = "\u001b\u0064\u0003"       // ESC d 3 - 切纸

        // 特殊命令集
        private const val CMD_INIT = "\u001b@"     // ESC @ - 初始化打印机
        private const val CMD_LF = "\u000A"        // 换行
        private const val CMD_CR = "\u000D"        // 回车
        private const val CMD_CUT = "\u001dV\u0001" // GS V 1 - 切纸
        
        // TSP100专用命令
        private const val CMD_TSP100_RESET = "\u001b\u0040" +           // ESC @ - 软件重置
                                           "\u001b\u001d\u0074\u0001" + // GS t 1 - 设置代码页(PC437)
                                           "\u001b\u0052\u0000" +       // ESC R 0 - 设置国际字符集
                                           "\u001b\u0074\u0001" +       // ESC t 1 - 设置字符代码表
                                           "\u001b\u0021\u0000" +       // ESC ! 0 - 设置字符模式
                                           "\u001b\u0033\u0028" +       // ESC 3 40 - 设置行间距
                                           "\u001b\u0032"               // ESC 2 - 设置默认行间距

        private const val CMD_TSP100_INIT = "\u001b\u0040" +           // ESC @ - 初始化
                                          "\u001b\u0061\u0000" +       // ESC a 0 - 左对齐
                                          "\u001b\u0021\u0000" +       // ESC ! 0 - 标准字符
                                          "\u001b\u0074\u0001"         // ESC t 1 - 代码页PC437

        private const val CMD_TSP100_ENCODING = "\u001b\u0074\u0001"   // ESC t 1 - 代码页PC437
        
        private const val CMD_TSP100_CLEAR = "\u0018" +                // CAN - 清除缓冲区
                                           "\u001b\u0040"              // ESC @ - 初始化

        private const val CMD_TSP100_FEED = "\u001b\u004a\u0040" +     // ESC J 64 - 打印并进纸n点
                                          "\u001b\u0064\u0005"         // ESC d 5 - 打印并进纸n行

        private const val CMD_TSP100_CUT = "\u001b\u0064\u0003" +      // ESC d 3 - 进纸3行
                                         "\u001d\u0056\u0042\u0000"    // GS V B 0 - 切纸（带预留）

        // Star TSP100III专用命令集
        private const val TSP100_INIT = "\u001b\u0040" +           // ESC @ - 初始化
                                      "\u001b\u001d\u0074\u0001" + // ESC GS t 1 - 设置代码页
                                      "\u001b\u0052\u0000" +       // ESC R 0 - 设置国际字符集
                                      "\u001b\u0074\u0001"         // ESC t 1 - 设置字符代码表

        private const val TSP100_RASTER_START = "\u001b\u0061\u0000" +  // ESC a 0 - 左对齐
                                              "\u001b\u0021\u0000"      // ESC ! 0 - 标准字符

        private const val TSP100_LINE_SPACE = "\u001b\u0033\u0028" +   // ESC 3 40 - 设置行间距
                                            "\u001b\u0032"             // ESC 2 - 设置默认行间距

        private const val TSP100_CLEAR = "\u0018"                      // CAN - 清除缓冲区

        private const val TSP100_CUT = "\u001b\u0064\u0003" +         // ESC d 3 - 进纸3行
                                     "\u001d\u0056\u0042\u0000"       // GS V B 0 - 切纸
    }

    private var currentPrinter: StarPrinter? = null
    private var isTSP100Series = false
    private var lastConnectedModel = ""
    private var recoveryJob: Job? = null
    private val recoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                        Log.d(TAG, "$logPrefix 使用TSP100系列特殊初始化命令: CMD_TSP100_RESET")
                        CMD_TSP100_RESET  // TSP100专用重置命令
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
     * 使用Star原生命令打印文本
     */
    suspend fun printWithStarCommands(content: String): Boolean {
        try {
            Log.d(TAG, "使用Star原生命令打印")
            
            // 检查打印机连接
            if (currentPrinter == null) {
                Log.e(TAG, "打印机未连接")
                return false
            }
            
            // 1. 初始化打印机
            val initCommands = StringBuilder()
            initCommands.append(STAR_CMD_INIT)        // 初始化
            initCommands.append(STAR_CMD_ENCODING)    // 设置编码
            initCommands.append(STAR_CMD_ALIGN_LEFT)  // 默认左对齐
            
            // 2. 处理格式化标签
            val formattedContent = content.replace("[L]", STAR_CMD_ALIGN_LEFT)
                                .replace("[C]", STAR_CMD_ALIGN_CENTER)
                                .replace("[R]", STAR_CMD_ALIGN_RIGHT)
                                .replace("<b>", STAR_CMD_BOLD_ON)
                                .replace("</b>", STAR_CMD_BOLD_OFF)
            
            // 3. 添加结束命令
            val endCommands = StringBuilder()
            endCommands.append("\n\n\n")  // 走纸
            endCommands.append(STAR_CMD_CUT)  // 切纸
            
            // 4. 组合完整打印内容
            val finalContent = initCommands.toString() + formattedContent + endCommands.toString()
            
            // 5. 直接使用打印机实例打印
            currentPrinter?.printAsync(finalContent)?.await()
            delay(100)
            
            Log.d(TAG, "Star原生命令打印完成")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Star原生命令打印失败: ${e.message}")
            return false
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { String.format("%02X ", it.toInt() and 0xFF) }
    }

    /**
     * 使用Star TSP100III专用命令打印
     */
    suspend fun printWithTSP100Commands(content: String): Boolean {
        val logPrefix = "【TSP100打印】"
        try {
            if (currentPrinter == null) {
                Log.e(TAG, "$logPrefix 打印机未连接")
                return false
            }

            // 记录打印内容
            Log.d(TAG, "$logPrefix 原始打印内容: $content")
            Log.d(TAG, "$logPrefix 打印内容长度: ${content.length}")

            // 1. 清除缓冲区
            Log.d(TAG, "$logPrefix 清除缓冲区")
            val clearCommand = TSP100_CLEAR.toByteArray()
            Log.d(TAG, "$logPrefix 清除缓冲区命令(HEX): ${clearCommand.toHexString()}")
            currentPrinter?.printAsync(TSP100_CLEAR)?.await()
            delay(200)

            // 2. 初始化打印机
            Log.d(TAG, "$logPrefix 初始化打印机")
            val initCommand = TSP100_INIT.toByteArray()
            Log.d(TAG, "$logPrefix 初始化命令(HEX): ${initCommand.toHexString()}")
            currentPrinter?.printAsync(TSP100_INIT)?.await()
            delay(200)

            // 3. 设置光栅打印模式
            Log.d(TAG, "$logPrefix 设置打印模式")
            val rasterCommand = TSP100_RASTER_START.toByteArray()
            Log.d(TAG, "$logPrefix 光栅模式命令(HEX): ${rasterCommand.toHexString()}")
            currentPrinter?.printAsync(TSP100_RASTER_START)?.await()
            delay(100)

            // 4. 设置行间距
            Log.d(TAG, "$logPrefix 设置行间距")
            val lineSpaceCommand = TSP100_LINE_SPACE.toByteArray()
            Log.d(TAG, "$logPrefix 行间距命令(HEX): ${lineSpaceCommand.toHexString()}")
            currentPrinter?.printAsync(TSP100_LINE_SPACE)?.await()
            delay(100)

            // 5. 处理格式化标签
            val formattedContent = content.replace("[L]", "\u001b\u0061\u0000")  // 左对齐
                                        .replace("[C]", "\u001b\u0061\u0001")  // 居中对齐
                                        .replace("[R]", "\u001b\u0061\u0002")  // 右对齐
                                        .replace("<b>", "\u001b\u0045\u0001")  // 加粗开启
                                        .replace("</b>", "\u001b\u0045\u0000") // 加粗关闭

            Log.d(TAG, "$logPrefix 格式化后内容: $formattedContent")
            val contentBytes = formattedContent.toByteArray()
            Log.d(TAG, "$logPrefix 格式化内容前50字节(HEX): ${contentBytes.take(50).toByteArray().toHexString()}")

            // 6. 打印内容
            Log.d(TAG, "$logPrefix 打印内容")
            currentPrinter?.printAsync(formattedContent)?.await()
            
            // 检查打印状态
            val midStatus = currentPrinter?.getStatusAsync()?.await()
            Log.d(TAG, "$logPrefix 打印内容后状态: $midStatus")
            delay(500)

            // 7. 走纸和切纸
            Log.d(TAG, "$logPrefix 走纸和切纸")
            val cutCommand = ("\n\n\n" + TSP100_CUT).toByteArray()
            Log.d(TAG, "$logPrefix 切纸命令(HEX): ${cutCommand.toHexString()}")
            currentPrinter?.printAsync("\n\n\n" + TSP100_CUT)?.await()
            delay(200)

            // 8. 检查打印状态
            val status = currentPrinter?.getStatusAsync()?.await()
            Log.d(TAG, "$logPrefix 打印完成，状态: $status")

            // 9. 尝试获取打印机信息
            try {
                val info = currentPrinter?.information
                Log.d(TAG, "$logPrefix 打印机信息: 型号=${info?.model ?: "未知"}")
                Log.d(TAG, "$logPrefix 打印机固件版本: ${info?.firmwareVersion ?: "未知"}")
            } catch (e: Exception) {
                Log.w(TAG, "$logPrefix 获取打印机信息失败: ${e.message}")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 打印失败: ${e.message}")
            Log.e(TAG, "$logPrefix 异常堆栈: ${e.stackTraceToString()}")
            return false
        }
    }

    /**
     * 打印测试页
     */
    suspend fun printTestPage(): Boolean {
        val logPrefix = "【TSP100测试】"
        try {
            if (currentPrinter == null) {
                Log.e(TAG, "$logPrefix 打印机未连接")
                return false
            }

            // 构建测试内容
            val testContent = """
                [C]*** TSP100III 测试页 ***
                [C]--------------------
                [L]打印机型号: ${currentPrinter?.information?.model ?: "未知"}
                [L]固件版本: ${currentPrinter?.information?.firmwareVersion ?: "未知"}
                [L]时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}
                [C]--------------------
                [L]测试项目:
                [L]1. 左对齐
                [C]2. 居中对齐
                [R]3. 右对齐
                [L]4. <b>加粗文本</b>
                [L]5. 普通文本
                [C]--------------------
                [C]测试完成
            """.trimIndent()

            return printWithTSP100Commands(testContent)
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 打印测试页失败: ${e.message}")
            return false
        }
    }

    /**
     * 打印文本内容
     */
    suspend fun printText(content: String): Boolean {
        val logPrefix = "【Star打印】"
        Log.d(TAG, "$logPrefix 开始打印，内容长度: ${content.length}")
        
        try {
            // 检查打印机连接
            if (currentPrinter == null) {
                Log.e(TAG, "$logPrefix 打印机未连接")
                return false
            }

            // 检查是否是TSP100系列
            if (isTSP100Series) {
                Log.d(TAG, "$logPrefix 使用TSP100专用命令打印")
                return printWithTSP100Commands(content)
            }

            // 非TSP100系列使用标准Star打印流程
            Log.d(TAG, "$logPrefix 使用标准Star打印流程")
            
            // 1. 初始化打印机
            currentPrinter?.printAsync(CMD_INIT)?.await()
            delay(100)
            
            // 2. 打印内容
            currentPrinter?.printAsync(content)?.await()
            delay(500)
            
            // 3. 结束处理
            currentPrinter?.printAsync("\n\n\n" + CMD_CUT)?.await()
            delay(200)
            
            Log.d(TAG, "$logPrefix 打印完成")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 打印失败: ${e.message}")
            return false
        }
    }
    
    /**
     * 转换ESC/POS格式文本到Star命令
     * @param content ESC/POS格式的文本
     * @return 转换后可用于Star打印机的文本
     */
    fun convertEscPosToStarPrnt(content: String): String {
        Log.d(TAG, "StarPrinterHandler.convertEscPosToStarPrnt() 原始内容长度: ${content.length}")
        val converted = try {
            // 这里添加ESC/POS到StarPRNT的具体转换逻辑
            // 如果没有特殊转换需求，可以直接原样返回
            content
        } catch (e: Exception) {
            Log.e(TAG, "StarPrinterHandler转换内容异常: ${e.message}", e)
            content  // 转换失败时返回原内容
        }
        Log.d(TAG, "StarPrinterHandler.convertEscPosToStarPrnt() 转换后内容长度: ${converted.length}")
        return converted
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
                ${CMD_TSP100_RESET}
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
                    Log.e(TAG, "$logPrefix 获取状态时出错: ${e.message}")
                }
                
                // 2. 发送清除缓冲区命令
                try {
                    Log.d(TAG, "$logPrefix 发送清除缓冲区命令")
                    currentPrinter?.printAsync(CMD_TSP100_CLEAR)?.await() // CAN + 初始化
                    delay(500L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 清除缓冲区时出错: ${e.message}")
                }
                
                // 3. 发送重置命令序列
                try {
                    Log.d(TAG, "$logPrefix 发送重置命令序列")
                    // 完整的TSP100III重置序列
                    currentPrinter?.printAsync(CMD_TSP100_RESET)?.await()
                    delay(1000L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送重置命令时出错: ${e.message}")
                }
                
                // 4. 发送简单初始化
                try {
                    Log.d(TAG, "$logPrefix 发送简单初始化命令")
                    // 初始化并设置编码
                    currentPrinter?.printAsync(CMD_TSP100_INIT)?.await()
                    delay(500L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送初始化命令时出错: ${e.message}")
                }
                
                // 5. 发送测试文本
                try {
                    Log.d(TAG, "$logPrefix 发送测试文本")
                    val testText = StringBuilder()
                        .append(CMD_TSP100_INIT)                    // 初始化
                        .append("\u001b\u0021\u0000")              // 重置字体样式
                        .append("\u001b\u0033\u0028")              // 设置行间距
                        .append("\u001b\u0061\u0001")              // 居中对齐
                        .append("=== TSP100 RESET TEST ===\n")     // 测试文本
                        .append("\u001b\u0061\u0000")              // 左对齐
                        .append("Status: OK\n")                     // 状态信息
                        .append("Time: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}\n") // 时间
                        .append("\n")                              // 额外换行
                        .append(CMD_TSP100_FEED)                   // 走纸
                        .append(CMD_TSP100_CUT)                    // 切纸
                        .toString()
                    
                    currentPrinter?.printAsync(testText)?.await()
                    delay(1000L)
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 发送测试文本时出错: ${e.message}")
                    return@withContext false
                }
                
                // 6. 再次检查状态
                try {
                    val status = currentPrinter?.getStatusAsync()?.await()
                    Log.d(TAG, "$logPrefix 重置后状态: $status")
                } catch (e: Exception) {
                    Log.e(TAG, "$logPrefix 获取重置后状态时出错: ${e.message}")
                }
            }
            
            Log.d(TAG, "$logPrefix TSP100重置流程完成")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix TSP100重置失败: ${e.message}")
            Log.e(TAG, "$logPrefix 异常详情: ${e.stackTraceToString()}")
            return false
        }
    }

    /**
     * TSP100系列专用打印方法
     */
    private suspend fun printTSP100(content: String): Boolean {
        val logPrefix = "【TSP100打印】"
        Log.d(TAG, "$logPrefix 开始TSP100专用打印流程")
        
        try {
            if (currentPrinter == null) {
                Log.e(TAG, "$logPrefix 打印机未连接")
                return false
            }

            // 1. 检查初始状态
            val initialStatus = currentPrinter?.getStatusAsync()?.await()
            Log.d(TAG, "$logPrefix 初始状态: $initialStatus")
            
            // 2. 清除缓冲区并等待
            currentPrinter?.printAsync(CMD_TSP100_CLEAR)?.await()
            delay(500)

            // 3. 发送完整的初始化序列并等待
            currentPrinter?.printAsync(CMD_TSP100_RESET)?.await()
            delay(1000)

            // 4. 设置基本参数
            val setupCommands = StringBuilder()
                .append(CMD_TSP100_INIT)        // 初始化并左对齐
                .append(CMD_TSP100_ENCODING)    // 设置字符集
                .append("\u001b\u0021\u0000")   // 重置字体样式
                .append("\u001b\u0033\u0028")   // 设置行间距
                .toString()
            
            currentPrinter?.printAsync(setupCommands)?.await()
            delay(500)

            // 5. 检查状态
            val midStatus = currentPrinter?.getStatusAsync()?.await()
            Log.d(TAG, "$logPrefix 设置后状态: $midStatus")

            // 6. 处理并打印内容
            val processedContent = content
                .replace("[L]", "\u001b\u0061\u0000")  // 左对齐
                .replace("[C]", "\u001b\u0061\u0001")  // 居中对齐
                .replace("[R]", "\u001b\u0061\u0002")  // 右对齐
                .replace("<b>", "\u001b\u0045\u0001")  // 加粗开启
                .replace("</b>", "\u001b\u0045\u0000") // 加粗关闭

            // 7. 发送打印内容
            currentPrinter?.printAsync(processedContent)?.await()
            delay(1000)
            
            // 8. 结束处理
            val endingCommands = StringBuilder()
                .append("\n\n")                // 多个换行
                .append(CMD_TSP100_FEED)       // 走纸
                .append(CMD_TSP100_CUT)        // 切纸命令
                .toString()
            
            currentPrinter?.printAsync(endingCommands)?.await()
            delay(500)
            
            // 9. 最终状态检查
            val finalStatus = currentPrinter?.getStatusAsync()?.await()
            Log.d(TAG, "$logPrefix 打印完成后状态: $finalStatus")
            
            Log.d(TAG, "$logPrefix 打印流程完成")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "$logPrefix 打印失败: ${e.message}")
            Log.e(TAG, "$logPrefix 异常详情: ${e.stackTraceToString()}")
            return false
        }
    }

    suspend fun checkStatus(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val printer = currentPrinter ?: return@withContext false
                val status = printer.getStatusAsync().await()
                
                when {
                    status.hasError -> {
                        Log.e(TAG, "Star打印机出错")
                        startRecoveryProcess()
                        false
                    }
                    status.coverOpen -> {
                        Log.e(TAG, "Star打印机盖子打开")
                        false
                    }
                    status.paperEmpty -> {
                        Log.e(TAG, "Star打印机缺纸")
                        false
                    }
                    else -> true
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查Star打印机状态失败: ${e.message}")
                false
            }
        }
    }

    private fun startRecoveryProcess() {
        recoveryJob?.cancel()
        recoveryJob = recoveryScope.launch {
            try {
                Log.d(TAG, "开始Star打印机恢复流程")
                currentPrinter?.let { printer ->
                    // 1. 重置打印机 - 使用初始化命令代替reset
                    printer.printAsync(CMD_INIT).await()
                    delay(1000)
                    
                    // 2. 重新初始化 - 使用TSP100专用命令或标准命令
                    val initCommand = if (isTSP100Series) {
                        CMD_TSP100_RESET
                    } else {
                        CMD_INIT
                    }
                    printer.printAsync(initCommand).await()
                    delay(500)
                    
                    // 3. 检查状态
                    if (checkStatus()) {
                        Log.d(TAG, "Star打印机恢复成功")
                    } else {
                        Log.e(TAG, "Star打印机恢复失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Star打印机恢复过程出错: ${e.message}")
            }
        }
    }
} 