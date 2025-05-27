package com.example.wooauto.utils

import android.util.Log
import com.example.wooauto.domain.models.PrinterConfig
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
         * 格式化标题行
         * @param title 标题文本
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatTitle(title: String, paperWidth: Int): String {
            return "[C]<b>$title</b>\n"
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
            
            // 处理多行文本，确保每行都居中显示
            val lines = text.split("\n")
            for (line in lines) {
                if (line.trim().isNotEmpty()) {
                    sb.append(formatCenteredText(line.trim(), paperWidth))
                } else {
                    // 保持空行
                    sb.append("\n")
                }
            }
            
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