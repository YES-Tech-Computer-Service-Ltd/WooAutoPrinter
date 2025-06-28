package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 热敏打印机格式化工具
 * 用于处理各种宽度打印机的文本格式转换
 */
class ThermalPrinterFormatter {
    companion object {
        private const val TAG = "ThermalPrinterFormatter"
        
        // 打印机字符数 - 不同纸张宽度的打印机能打印的字符数
        private const val CHAR_COUNT_58MM = 28  // 58mm打印机一行约28个英文字符(有效打印区域约50mm)
        private const val CHAR_COUNT_80MM = 42  // 80mm打印机一行约42个英文字符(有效打印区域约72mm)
        
        // GB8030打印机字符数 - 针对中文优化的字符数
        private const val CHAR_COUNT_GB8030_58MM = 24  // GB8030 58mm打印机，考虑中文字符宽度
        private const val CHAR_COUNT_GB8030_80MM = 36  // GB8030 80mm打印机，考虑中文字符宽度
        
        /**
         * 获取打印机每行能打印的字符数
         * @param paperWidth 打印纸宽度，单位mm
         * @param brand 打印机品牌
         * @return 每行字符数
         */
        fun getCharsPerLine(paperWidth: Int, brand: PrinterBrand = PrinterBrand.UNKNOWN): Int {
            return when {
                brand == PrinterBrand.GB8030 -> {
                    when (paperWidth) {
                        PrinterConfig.PAPER_WIDTH_80MM -> CHAR_COUNT_GB8030_80MM
                        PrinterConfig.PAPER_WIDTH_57MM -> CHAR_COUNT_GB8030_58MM
                        else -> CHAR_COUNT_GB8030_58MM
                    }
                }
                paperWidth == PrinterConfig.PAPER_WIDTH_80MM -> CHAR_COUNT_80MM
                paperWidth == PrinterConfig.PAPER_WIDTH_57MM -> CHAR_COUNT_58MM
                else -> CHAR_COUNT_58MM // 默认使用58mm
            }
        }
        
        /**
         * 检测文本是否包含中文字符
         * @param text 要检测的文本
         * @return 是否包含中文字符
         */
        fun containsChineseCharacters(text: String): Boolean {
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
         * 计算文本的实际显示长度（中文字符按2个字符计算）
         * @param text 要计算的文本
         * @return 实际显示长度
         */
        fun getTextDisplayLength(text: String): Int {
            var length = 0
            for (char in text) {
                if (containsChineseCharacters(char.toString())) {
                    length += 2 // 中文字符按2个字符计算
                } else {
                    length += 1 // 英文字符按1个字符计算
                }
            }
            return length
        }
        
        /**
         * 将文本转换为打印机编码格式
         * 针对GB8030打印机优化中文字符处理
         * @param text 原始文本
         * @param brand 打印机品牌
         * @return 转换后的文本
         */
        fun convertTextToPrinterEncoding(text: String, brand: PrinterBrand = PrinterBrand.UNKNOWN): String {
            if (brand == PrinterBrand.GB8030) {
                // GB8030打印机特殊处理
                return convertTextForGB8030(text)
            }
            
            // 其他打印机使用默认处理
            return text
        }
        
        /**
         * 针对GB8030打印机的中文文本转换
         * @param text 原始文本
         * @return 转换后的文本
         */
        private fun convertTextForGB8030(text: String): String {
            if (!containsChineseCharacters(text)) {
                return text // 不包含中文，直接返回
            }
            
            // GB8030打印机支持GBK编码，但需要确保文本格式正确
            // 这里主要处理一些特殊字符和格式问题
            var processedText = text
            
            // 替换一些可能导致问题的字符
            processedText = processedText.replace("…", "...") // 省略号替换
            processedText = processedText.replace("—", "-")   // 长破折号替换
            processedText = processedText.replace(""", "\"") // 中文引号替换
            processedText = processedText.replace(""", "\"") // 中文引号替换
            processedText = processedText.replace("'", "'") // 中文单引号替换
            processedText = processedText.replace("'", "'") // 中文单引号替换
            
            return processedText
        }
        
        /**
         * 智能文本换行，考虑中文字符宽度
         * @param text 原始文本
         * @param maxLength 最大长度
         * @param brand 打印机品牌
         * @return 换行后的文本
         */
        fun smartTextWrap(text: String, maxLength: Int, brand: PrinterBrand = PrinterBrand.UNKNOWN): String {
            if (text.length <= maxLength) {
                return text
            }
            
            val lines = mutableListOf<String>()
            var currentLine = ""
            
            for (char in text) {
                val testLine = currentLine + char
                val displayLength = getTextDisplayLength(testLine)
                
                if (displayLength <= maxLength) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                        currentLine = char.toString()
                    } else {
                        // 单个字符就超长，强制添加
                        lines.add(char.toString())
                    }
                }
            }
            
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }
            
            return lines.joinToString("\n")
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
            val chars = getCharsPerLine(paperWidth)
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
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @param alignment 对齐方式：'L'=左对齐，'C'=居中，'R'=右对齐
         * @return 格式化后的文本
         */
        fun formatMultilineText(text: String, paperWidth: Int, alignment: Char = 'L'): String {
            val maxChars = getCharsPerLine(paperWidth)
            val sb = StringBuilder()
            
            // 按换行符分割
            val lines = text.split("\n")
            
            for (line in lines) {
                // 如果单行长度超过打印机宽度，需要拆分
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