package com.example.wooauto.data.printer

import android.util.Log
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import java.nio.charset.Charset

/**
 * GB18030编码打印机工具类
 * 专门处理GB18030编码的打印功能，解决中文显示问题
 */
class GB18030Printer(private val connection: BluetoothConnection) {
    
    companion object {
        private const val TAG = "GB18030Printer"
        
        // GB18030编码
        private val GB18030_CHARSET = Charset.forName("GB18030")
        
        // ESC/POS命令常量
        private val ESC = 0x1B
        private val GS = 0x1D
        private val LF = 0x0A
        private val CR = 0x0D
        private val FF = 0x0C
        
        // 初始化打印机命令
        private val INIT_COMMAND = byteArrayOf(ESC.toByte(), 0x40.toByte())
        
        // 设置GB18030编码命令 (ESC t 15)
        private val SET_GB18030_ENCODING = byteArrayOf(ESC.toByte(), 0x74.toByte(), 0x0F.toByte())
        
        // 字体样式命令
        private val BOLD_ON = byteArrayOf(ESC.toByte(), 0x45.toByte(), 0x01.toByte())
        private val BOLD_OFF = byteArrayOf(ESC.toByte(), 0x45.toByte(), 0x00.toByte())
        private val UNDERLINE_ON = byteArrayOf(ESC.toByte(), 0x2D.toByte(), 0x01.toByte())
        private val UNDERLINE_OFF = byteArrayOf(ESC.toByte(), 0x2D.toByte(), 0x00.toByte())
        
        // 字体大小命令
        private val DOUBLE_WIDTH = byteArrayOf(ESC.toByte(), 0x21.toByte(), 0x20.toByte())
        private val DOUBLE_HEIGHT = byteArrayOf(ESC.toByte(), 0x21.toByte(), 0x10.toByte())
        private val DOUBLE_SIZE = byteArrayOf(ESC.toByte(), 0x21.toByte(), 0x30.toByte())
        private val NORMAL_SIZE = byteArrayOf(ESC.toByte(), 0x21.toByte(), 0x00.toByte())
        
        // 对齐命令
        private val ALIGN_LEFT = byteArrayOf(ESC.toByte(), 0x61.toByte(), 0x00.toByte())
        private val ALIGN_CENTER = byteArrayOf(ESC.toByte(), 0x61.toByte(), 0x01.toByte())
        private val ALIGN_RIGHT = byteArrayOf(ESC.toByte(), 0x61.toByte(), 0x02.toByte())
        
        // 切纸命令
        private val CUT_PAPER = byteArrayOf(GS.toByte(), 0x56.toByte(), 0x01.toByte())
        
        // 走纸命令
        private val FEED_LINE = byteArrayOf(LF.toByte())
        private val FEED_PAPER = byteArrayOf(ESC.toByte(), 0x64.toByte(), 0x05.toByte())
    }
    
    /**
     * 初始化打印机并设置GB18030编码
     */
    fun initialize(): Boolean {
        return try {
            Log.d(TAG, "初始化打印机并设置GB18030编码")
            
            // 初始化打印机
            connection.write(INIT_COMMAND)
            Thread.sleep(100)
            
            // 设置GB18030编码
            connection.write(SET_GB18030_ENCODING)
            Thread.sleep(100)
            
            Log.d(TAG, "打印机初始化完成，已设置GB18030编码")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打印机初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 打印纯文本（GB18030编码）
     * @param text 要打印的文本
     */
    fun printText(text: String) {
        try {
            val bytes = text.toByteArray(GB18030_CHARSET)
            connection.write(bytes)
            Log.d(TAG, "打印文本: $text")
        } catch (e: Exception) {
            Log.e(TAG, "打印文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印文本并换行
     * @param text 要打印的文本
     */
    fun printLine(text: String) {
        printText(text)
        feedLine()
    }
    
    /**
     * 打印居中对齐的文本
     * @param text 要打印的文本
     */
    fun printCenter(text: String) {
        try {
            connection.write(ALIGN_CENTER)
            printText(text)
            connection.write(ALIGN_LEFT) // 恢复左对齐
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印居中文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印右对齐的文本
     * @param text 要打印的文本
     */
    fun printRight(text: String) {
        try {
            connection.write(ALIGN_RIGHT)
            printText(text)
            connection.write(ALIGN_LEFT) // 恢复左对齐
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印右对齐文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印加粗文本
     * @param text 要打印的文本
     */
    fun printBold(text: String) {
        try {
            connection.write(BOLD_ON)
            printText(text)
            connection.write(BOLD_OFF)
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印加粗文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印加粗居中的文本
     * @param text 要打印的文本
     */
    fun printBoldCenter(text: String) {
        try {
            connection.write(BOLD_ON)
            connection.write(ALIGN_CENTER)
            printText(text)
            connection.write(BOLD_OFF)
            connection.write(ALIGN_LEFT)
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印加粗居中文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印下划线文本
     * @param text 要打印的文本
     */
    fun printUnderline(text: String) {
        try {
            connection.write(UNDERLINE_ON)
            printText(text)
            connection.write(UNDERLINE_OFF)
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印下划线文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印双倍大小文本
     * @param text 要打印的文本
     */
    fun printDoubleSize(text: String) {
        try {
            connection.write(DOUBLE_SIZE)
            printText(text)
            connection.write(NORMAL_SIZE)
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印双倍大小文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印双倍大小居中的文本
     * @param text 要打印的文本
     */
    fun printDoubleSizeCenter(text: String) {
        try {
            connection.write(DOUBLE_SIZE)
            connection.write(ALIGN_CENTER)
            printText(text)
            connection.write(NORMAL_SIZE)
            connection.write(ALIGN_LEFT)
            feedLine()
        } catch (e: Exception) {
            Log.e(TAG, "打印双倍大小居中文本失败: ${e.message}")
        }
    }
    
    /**
     * 打印分隔线
     * @param char 分隔符字符，默认为"-"
     * @param length 分隔线长度，默认为32
     */
    fun printSeparator(char: String = "-", length: Int = 32) {
        val separator = char.repeat(length)
        printLine(separator)
    }
    
    /**
     * 打印空行
     * @param lines 空行数量，默认为1
     */
    fun printEmptyLines(lines: Int = 1) {
        repeat(lines) {
            feedLine()
        }
    }
    
    /**
     * 走纸
     * @param lines 走纸行数，默认为1
     */
    fun feedLine(lines: Int = 1) {
        try {
            repeat(lines) {
                connection.write(FEED_LINE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "走纸失败: ${e.message}")
        }
    }
    
    /**
     * 走纸（指定行数）
     * @param lines 走纸行数
     */
    fun feedPaper(lines: Int) {
        try {
            val command = byteArrayOf(ESC.toByte(), 0x64.toByte(), lines.toByte())
            connection.write(command)
        } catch (e: Exception) {
            Log.e(TAG, "走纸失败: ${e.message}")
        }
    }
    
    /**
     * 切纸
     */
    fun cutPaper() {
        try {
            connection.write(CUT_PAPER)
            Log.d(TAG, "执行切纸")
        } catch (e: Exception) {
            Log.e(TAG, "切纸失败: ${e.message}")
        }
    }
    
    /**
     * 打印格式化文本（支持HTML标签和简单标签）
     * @param formattedText 格式化文本，支持以下标签：
     *   HTML标签: <b>加粗</b>, <u>下划线</u>, <i>斜体</i>
     *   简单标签: [C] - 居中, [R] - 右对齐, [L] - 左对齐（默认）
     *   [B] - 加粗, [U] - 下划线, [D] - 双倍大小, [S] - 分隔线
     */
    fun printFormattedText(formattedText: String) {
        try {
            val lines = formattedText.split("\n")
            
            for (line in lines) {
                if (line.isBlank()) {
                    feedLine()
                    continue
                }
                
                var text = line.trim()
                var isBold = false
                var isUnderline = false
                var isDoubleSize = false
                var alignment = ALIGN_LEFT
                
                // 处理HTML标签
                text = processHtmlTags(text)
                
                // 处理对齐标签
                when {
                    text.startsWith("[C]") -> {
                        alignment = ALIGN_CENTER
                        text = text.substring(3)
                    }
                    text.startsWith("[R]") -> {
                        alignment = ALIGN_RIGHT
                        text = text.substring(3)
                    }
                    text.startsWith("[L]") -> {
                        alignment = ALIGN_LEFT
                        text = text.substring(3)
                    }
                }
                
                // 处理样式标签
                when {
                    text.startsWith("[B]") -> {
                        isBold = true
                        text = text.substring(3)
                    }
                    text.startsWith("[U]") -> {
                        isUnderline = true
                        text = text.substring(3)
                    }
                    text.startsWith("[D]") -> {
                        isDoubleSize = true
                        text = text.substring(3)
                    }
                }
                
                // 处理分隔线
                if (text.startsWith("[S]")) {
                    val separator = text.substring(3).ifEmpty { "-" }
                    printSeparator(separator)
                    continue
                }
                
                // 应用样式
                if (isDoubleSize) connection.write(DOUBLE_SIZE)
                if (isBold) connection.write(BOLD_ON)
                if (isUnderline) connection.write(UNDERLINE_ON)
                connection.write(alignment)
                
                // 打印文本
                if (text.isNotBlank()) {
                    printText(text)
                }
                
                // 恢复样式
                if (isUnderline) connection.write(UNDERLINE_OFF)
                if (isBold) connection.write(BOLD_OFF)
                if (isDoubleSize) connection.write(NORMAL_SIZE)
                connection.write(ALIGN_LEFT)
                
                feedLine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打印格式化文本失败: ${e.message}")
        }
    }
    
    /**
     * 处理HTML标签，将HTML标签转换为ESC/POS命令
     * @param text 包含HTML标签的文本
     * @return 处理后的文本
     */
    private fun processHtmlTags(text: String): String {
        var processedText = text
        
        // 处理加粗标签 <b>...</b>
        while (processedText.contains("<b>") && processedText.contains("</b>")) {
            val startIndex = processedText.indexOf("<b>")
            val endIndex = processedText.indexOf("</b>")
            
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val beforeTag = processedText.substring(0, startIndex)
                val boldText = processedText.substring(startIndex + 3, endIndex)
                val afterTag = processedText.substring(endIndex + 4)
                
                // 发送加粗开始命令
                connection.write(BOLD_ON)
                // 打印加粗前的文本
                if (beforeTag.isNotBlank()) {
                    printText(beforeTag)
                }
                // 打印加粗文本
                printText(boldText)
                // 发送加粗结束命令
                connection.write(BOLD_OFF)
                // 打印加粗后的文本
                if (afterTag.isNotBlank()) {
                    printText(afterTag)
                }
                
                // 更新处理后的文本
                processedText = beforeTag + boldText + afterTag
            } else {
                break
            }
        }
        
        // 处理下划线标签 <u>...</u>
        while (processedText.contains("<u>") && processedText.contains("</u>")) {
            val startIndex = processedText.indexOf("<u>")
            val endIndex = processedText.indexOf("</u>")
            
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val beforeTag = processedText.substring(0, startIndex)
                val underlineText = processedText.substring(startIndex + 3, endIndex)
                val afterTag = processedText.substring(endIndex + 4)
                
                // 发送下划线开始命令
                connection.write(UNDERLINE_ON)
                // 打印下划线前的文本
                if (beforeTag.isNotBlank()) {
                    printText(beforeTag)
                }
                // 打印下划线文本
                printText(underlineText)
                // 发送下划线结束命令
                connection.write(UNDERLINE_OFF)
                // 打印下划线后的文本
                if (afterTag.isNotBlank()) {
                    printText(afterTag)
                }
                
                // 更新处理后的文本
                processedText = beforeTag + underlineText + afterTag
            } else {
                break
            }
        }
        
        return processedText
    }
    
    /**
     * 打印测试页面
     */
    fun printTestPage() {
        try {
            Log.d(TAG, "开始打印测试页面")
            
            initialize()
            
            printDoubleSizeCenter("GB18030打印测试")
            printEmptyLines(2)
            
            printBoldCenter("中文测试")
            printLine("你好世界 - Hello World")
            printLine("测试成功！- Test Success!")
            printLine("中文商品名称测试")
            printLine("客户姓名：张三")
            printLine("配送地址：北京市朝阳区")
            printLine("联系电话：138-1234-5678")
            printLine("订单备注：请尽快配送")
            printLine("支付方式：微信支付")
            
            printSeparator()
            
            printLine("数字测试：1234567890")
            printLine("符号测试：!@#$%^&*()")
            printLine("混合测试：中文English123")
            
            printSeparator()
            printEmptyLines(3)
            
            cutPaper()
            
            Log.d(TAG, "测试页面打印完成")
        } catch (e: Exception) {
            Log.e(TAG, "打印测试页面失败: ${e.message}")
        }
    }
    
    /**
     * 关闭连接
     */
    fun close() {
        try {
            connection.disconnect()
            Log.d(TAG, "打印机连接已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭打印机连接失败: ${e.message}")
        }
    }
} 