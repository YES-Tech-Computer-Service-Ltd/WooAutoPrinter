package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.domain.models.PrinterConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.Charset

/**
 * 热敏打印机格式化工具
 * 用于处理各种宽度打印机的文本格式转换
 * 支持中文GB18030编码
 */
class ThermalPrinterFormatter {
    companion object {
        private const val TAG = "ThermalPrinterFormatter"
        
        // 打印机字符数 - 不同纸张宽度的打印机能打印的字符数
        // 中文字符占用双倍宽度，所以需要调整
        private const val CHAR_COUNT_58MM = 28  // 58mm打印机一行约28个英文字符(有效打印区域约50mm)
        private const val CHAR_COUNT_80MM = 42  // 80mm打印机一行约42个英文字符(有效打印区域约72mm)
        
        // 中文字符宽度系数
        private const val CHINESE_CHAR_WIDTH = 2.0
        private const val ENGLISH_CHAR_WIDTH = 1.0
        
        // GB18030字符集
        private val GB18030_CHARSET = Charset.forName("GB18030")
        
        /**
         * 获取打印机每行能打印的字符数
         * @param paperWidth 打印纸宽度，单位mm
         * @return 每行字符数
         */
        fun getCharsPerLine(paperWidth: Int): Int {
            return when (paperWidth) {
                PrinterConfig.PAPER_WIDTH_80MM -> CHAR_COUNT_80MM
                PrinterConfig.PAPER_WIDTH_57MM -> CHAR_COUNT_58MM
                else -> CHAR_COUNT_58MM // 默认使用58mm
            }
        }
        
        /**
         * 计算字符串的实际显示宽度（考虑中文字符）
         * @param text 要计算的文本
         * @return 实际显示宽度（以英文字符为单位）
         */
        fun calculateTextWidth(text: String): Double {
            var width = 0.0
            for (char in text) {
                width += if (isChineseChar(char)) {
                    CHINESE_CHAR_WIDTH
                } else {
                    ENGLISH_CHAR_WIDTH
                }
            }
            return width
        }
        
        /**
         * 判断字符是否为中文字符
         * @param char 要判断的字符
         * @return 是否为中文字符
         */
        fun isChineseChar(char: Char): Boolean {
            return char.code in 0x4E00..0x9FFF || // 基本汉字
                   char.code in 0x3400..0x4DBF || // 扩展A区
                   char.code in 0x20000..0x2A6DF || // 扩展B区
                   char.code in 0x2A700..0x2B73F || // 扩展C区
                   char.code in 0x2B740..0x2B81F || // 扩展D区
                   char.code in 0x2B820..0x2CEAF || // 扩展E区
                   char.code in 0xF900..0xFAFF || // 兼容汉字
                   char.code in 0x2F800..0x2FA1F // 兼容扩展
        }
        
        /**
         * 将文本转换为GB18030编码的字节数组
         * @param text 要转换的文本
         * @return GB18030编码的字节数组
         */
        fun toGB18030Bytes(text: String): ByteArray {
            return try {
                text.toByteArray(GB18030_CHARSET)
            } catch (e: Exception) {
                Log.e(TAG, "转换GB18030编码失败: ${e.message}")
                // 如果GB18030失败，回退到UTF-8
                text.toByteArray(Charsets.UTF_8)
            }
        }
        
        /**
         * 智能截断文本，确保不超过打印机宽度
         * @param text 原始文本
         * @param maxWidth 最大宽度（以英文字符为单位）
         * @return 截断后的文本
         */
        fun truncateText(text: String, maxWidth: Int): String {
            if (calculateTextWidth(text) <= maxWidth) {
                return text
            }
            
            var truncated = ""
            for (char in text) {
                val newWidth = calculateTextWidth(truncated + char)
                if (newWidth <= maxWidth) {
                    truncated += char
                } else {
                    break
                }
            }
            return truncated
        }
        
        /**
         * 智能换行文本，考虑中文字符宽度
         * @param text 原始文本
         * @param maxWidth 最大宽度（以英文字符为单位）
         * @return 换行后的文本列表
         */
        fun wrapText(text: String, maxWidth: Int): List<String> {
            val lines = mutableListOf<String>()
            val words = text.split(" ")
            var currentLine = ""
            
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (calculateTextWidth(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                    }
                    // 如果单个词就超过宽度，需要强制换行
                    if (calculateTextWidth(word) > maxWidth) {
                        var remainingWord = word
                        while (remainingWord.isNotEmpty()) {
                            val truncated = truncateText(remainingWord, maxWidth)
                            lines.add(truncated)
                            remainingWord = remainingWord.substring(truncated.length)
                        }
                        currentLine = ""
                    } else {
                        currentLine = word
                    }
                }
            }
            
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
            
            return lines
        }

        /**
         * 格式化标题行
         * @param title 标题文本
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatTitle(title: String, paperWidth: Int): String {
            return "[C]<b>$title</b>\n"
        }
        
        /**
         * 格式化店铺名称（大号字体，加粗，居中）
         * @param storeName 店铺名称
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatStoreName(storeName: String, paperWidth: Int): String {
            // 使用加粗和上下装饰线来突出显示店铺名称
            val maxWidth = getCharsPerLine(paperWidth)
            val topBottomLine = "=".repeat(maxWidth)
            val sb = StringBuilder()
            
            // 上装饰线
            sb.append("[C]$topBottomLine\n")
            // 店铺名称（加粗，居中）
            sb.append("[C]<b>$storeName</b>\n")
            // 下装饰线
            sb.append("[C]$topBottomLine\n")
            
            return sb.toString()
        }
        
        /**
         * 格式化店铺地址（中等字体，居中）
         * @param storeAddress 店铺地址
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatStoreAddress(storeAddress: String, paperWidth: Int): String {
            return "[C]$storeAddress\n"
        }
        
        /**
         * 格式化店铺电话（中等字体，居中，带前缀）
         * @param storePhone 店铺电话
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatStorePhone(storePhone: String, paperWidth: Int): String {
            return "[C]Tel: $storePhone\n"
        }
        
        /**
         * 格式化分隔线
         * @param paperWidth 打印纸宽度
         * @return 格式化后的分隔线
         */
        fun formatDivider(paperWidth: Int): String {
            val chars = getCharsPerLine(paperWidth)
            val divider = "-".repeat(chars)
            return "[C]$divider\n"
        }
        
        /**
         * 格式化标签值对
         * @param label 标签
         * @param value 值
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatLabelValue(label: String, value: String, paperWidth: Int): String {
            return "[L]<b>$label:</b> $value\n"
        }
        
        /**
         * 格式化居中文本
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatCenteredText(text: String, paperWidth: Int): String {
            return "[C]$text\n"
        }
        
        /**
         * 格式化左对齐文本
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatLeftText(text: String, paperWidth: Int): String {
            return "[L]$text\n"
        }
        
        /**
         * 格式化右对齐文本
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatRightText(text: String, paperWidth: Int): String {
            return "[R]$text\n"
        }
        
        /**
         * 格式化左右对齐的行（用于价格等）
         * @param left 左侧文本
         * @param right 右侧文本
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatLeftRightText(left: String, right: String, paperWidth: Int): String {
            return "[L]$left[R]$right\n"
        }
        
        /**
         * 格式化加粗文本
         * @param text 文本内容
         * @return 格式化后的文本
         */
        fun formatBold(text: String): String {
            return "<b>$text</b>"
        }
        
        /**
         * 格式化缩进文本（用于子项）
         * @param text 文本内容
         * @param indent 缩进级别
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatIndentedText(text: String, indent: Int = 1, paperWidth: Int): String {
            val indentText = "  ".repeat(indent) // 每级缩进2个空格
            return "[L]$indentText$text\n"
        }
        
        /**
         * 格式化日期时间
         * @param date 日期对象
         * @param format 日期格式，默认为 yyyy-MM-dd HH:mm:ss
         * @return 格式化后的日期字符串
         */
        fun formatDateTime(date: Date, format: String = "yyyy-MM-dd HH:mm:ss"): String {
            val dateFormat = SimpleDateFormat(format, Locale.getDefault())
            return dateFormat.format(date)
        }
        
        /**
         * 格式化多行文本，确保每行不超过打印机宽度（支持中文）
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @param alignment 对齐方式：'L'=左对齐，'C'=居中，'R'=右对齐
         * @return 格式化后的文本
         */
        fun formatMultilineText(text: String, paperWidth: Int, alignment: Char = 'L'): String {
            val maxWidth = getCharsPerLine(paperWidth)
            val sb = StringBuilder()
            
            // 按换行符分割
            val lines = text.split("\n")
            
            for (line in lines) {
                // 使用智能换行处理中文
                val wrappedLines = wrapText(line, maxWidth)
                for (wrappedLine in wrappedLines) {
                    sb.append("[$alignment]$wrappedLine\n")
                }
            }
            
            return sb.toString()
        }
        
        /**
         * 添加空行
         * @param count 空行数量
         * @return 格式化后的文本
         */
        fun addEmptyLines(count: Int): String {
            return "\n".repeat(count)
        }
        
        /**
         * 格式化页脚信息
         * @param text 页脚文本
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatFooter(text: String, paperWidth: Int): String {
            val sb = StringBuilder()
            sb.append(formatDivider(paperWidth))
            
            // 使用多行文本格式化方法，并设置为居中对齐
            // 这样可以处理自动换行和手动换行的情况
            sb.append(formatMultilineText(text, paperWidth, 'C'))
            
            sb.append(addEmptyLines(3)) // 添加空行便于撕纸
            return sb.toString()
        }
        
        /**
         * 格式化商品行（带价格）- 支持中文
         * @param name 商品名称
         * @param quantity 数量
         * @param price 单价
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatItemPriceLine(name: String, quantity: Int, price: String, paperWidth: Int): String {
            val maxWidth = getCharsPerLine(paperWidth)
            val nameWidth = calculateTextWidth(name)
            
            // 对于较窄的打印机，可能需要将商品名称和价格分为两行
            if (paperWidth == PrinterConfig.PAPER_WIDTH_57MM && nameWidth > 14) {
                val sb = StringBuilder()
                sb.append("[L]$name\n")
                sb.append("[L]  ${quantity} x $price\n")
                return sb.toString()
            } else if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM && nameWidth > 26) {
                // 80mm打印机但有效打印宽度控制在72mm
                val sb = StringBuilder()
                sb.append("[L]$name\n")
                sb.append("[L]  ${quantity} x $price\n")
                return sb.toString()
            } else {
                return "[L]$name[R]${quantity} x $price\n"
            }
        }
        
        /**
         * 转换ESC/POS命令
         * 将我们的简单格式转换为打印机命令
         * 注意：这个方法只是演示，实际打印由打印库处理
         * @param formattedText 已格式化的文本
         * @return ESC/POS命令字符串
         */
        fun convertToEscPos(formattedText: String): String {
            Log.d(TAG, "转换为ESC/POS命令: $formattedText")
            // 这里只是演示，实际应用中会使用专门的打印库处理具体的ESC/POS命令
            return formattedText
        }
    }
} 