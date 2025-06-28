package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.domain.models.PrinterConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 热敏打印机格式化工具
 * 用于处理各种宽度打印机的文本格式转换
 * 支持中文字符编码
 */
class ThermalPrinterFormatter {
    companion object {
        private const val TAG = "ThermalPrinterFormatter"
        
        // 打印机字符数 - 不同纸张宽度的打印机能打印的字符数
        private const val CHAR_COUNT_58MM = 28  // 58mm打印机一行约28个英文字符(有效打印区域约50mm)
        private const val CHAR_COUNT_80MM = 42  // 80mm打印机一行约42个英文字符(有效打印区域约72mm)
        
        /**
         * 检测文本是否包含中文字符
         * @param text 要检测的文本
         * @return 是否包含中文字符
         */
        private fun containsChineseCharacters(text: String): Boolean {
            return text.any { char ->
                char.code in 0x4E00..0x9FFF || // 基本汉字
                char.code in 0x3400..0x4DBF || // 扩展A区
                char.code in 0x20000..0x2A6DF || // 扩展B区
                char.code in 0x2A700..0x2B73F || // 扩展C区
                char.code in 0x2B740..0x2B81F || // 扩展D区
                char.code in 0x2B820..0x2CEAF || // 扩展E区
                char.code in 0xF900..0xFAFF || // 兼容汉字
                char.code in 0x2F800..0x2FA1F // 兼容扩展
            }
        }
        
        /**
         * 将文本转换为适合打印机的编码
         * 如果包含中文字符，使用GBK编码；否则使用UTF-8
         * @param text 要转换的文本
         * @return 转换后的字节数组
         */
        fun convertTextToPrinterEncoding(text: String): ByteArray {
            return try {
                if (containsChineseCharacters(text)) {
                    // 包含中文字符，使用GBK编码
                    Log.d(TAG, "检测到中文字符，使用GBK编码: $text")
                    text.toByteArray(Charset.forName("GBK"))
                } else {
                    // 不包含中文字符，使用UTF-8编码
                    text.toByteArray(StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.e(TAG, "字符编码转换失败，使用UTF-8作为后备: ${e.message}")
                text.toByteArray(StandardCharsets.UTF_8)
            }
        }
        
        /**
         * 获取打印机每行能打印的字符数
         * 考虑中文字符的宽度（中文字符通常占用2个英文字符的宽度）
         * @param paperWidth 打印纸宽度，单位mm
         * @param text 要打印的文本（用于检测是否包含中文）
         * @return 每行字符数
         */
        fun getCharsPerLine(paperWidth: Int, text: String = ""): Int {
            val baseChars = when (paperWidth) {
                PrinterConfig.PAPER_WIDTH_80MM -> CHAR_COUNT_80MM
                PrinterConfig.PAPER_WIDTH_57MM -> CHAR_COUNT_58MM
                else -> CHAR_COUNT_58MM // 默认使用58mm
            }
            
            // 如果文本包含中文字符，调整字符数
            if (containsChineseCharacters(text)) {
                // 中文字符占用更多空间，减少每行字符数
                return (baseChars * 0.7).toInt() // 减少30%的字符数
            }
            
            return baseChars
        }
        
        /**
         * 智能文本换行，考虑中文字符宽度
         * @param text 要换行的文本
         * @param paperWidth 打印纸宽度
         * @param alignment 对齐方式
         * @return 格式化后的文本
         */
        fun formatMultilineTextWithChineseSupport(text: String, paperWidth: Int, alignment: Char = 'L'): String {
            val sb = StringBuilder()
            
            // 按换行符分割
            val lines = text.split("\n")
            
            for (line in lines) {
                if (containsChineseCharacters(line)) {
                    // 包含中文字符，使用更保守的字符数
                    val maxChars = getCharsPerLine(paperWidth, line)
                    var remainingText = line
                    
                    while (remainingText.isNotEmpty()) {
                        // 计算当前行能容纳的字符数
                        var currentLineChars = 0
                        var currentLine = ""
                        
                        for (char in remainingText) {
                            val charWidth = if (containsChineseCharacters(char.toString())) 2 else 1
                            if (currentLineChars + charWidth <= maxChars) {
                                currentLine += char
                                currentLineChars += charWidth
                            } else {
                                break
                            }
                        }
                        
                        if (currentLine.isNotEmpty()) {
                            sb.append("[$alignment]$currentLine\n")
                            remainingText = remainingText.substring(currentLine.length)
                        } else {
                            // 防止无限循环
                            sb.append("[$alignment]${remainingText.take(1)}\n")
                            remainingText = remainingText.substring(1)
                        }
                    }
                } else {
                    // 不包含中文字符，使用原有逻辑
                    val maxChars = getCharsPerLine(paperWidth, line)
                    if (line.length > maxChars) {
                        var remainingText = line
                        while (remainingText.isNotEmpty()) {
                            val chunk = remainingText.take(maxChars)
                            sb.append("[$alignment]$chunk\n")
                            remainingText = remainingText.drop(maxChars)
                        }
                    } else {
                        sb.append("[$alignment]$line\n")
                    }
                }
            }
            
            return sb.toString()
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
            val chars = getCharsPerLine(paperWidth, storeName)
            val topBottomLine = "=".repeat(chars)
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
         * 格式化多行文本，确保每行不超过打印机宽度
         * 使用支持中文的版本
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @param alignment 对齐方式：'L'=左对齐，'C'=居中，'R'=右对齐
         * @return 格式化后的文本
         */
        fun formatMultilineText(text: String, paperWidth: Int, alignment: Char = 'L'): String {
            return formatMultilineTextWithChineseSupport(text, paperWidth, alignment)
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
         * 格式化商品行（带价格）
         * @param name 商品名称
         * @param quantity 数量
         * @param price 单价
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatItemPriceLine(name: String, quantity: Int, price: String, paperWidth: Int): String {
            // 对于较窄的打印机，可能需要将商品名称和价格分为两行
            if (paperWidth == PrinterConfig.PAPER_WIDTH_57MM && name.length > 14) {
                val sb = StringBuilder()
                sb.append("[L]$name\n")
                sb.append("[L]  ${quantity} x $price\n")
                return sb.toString()
            } else if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM && name.length > 26) {
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