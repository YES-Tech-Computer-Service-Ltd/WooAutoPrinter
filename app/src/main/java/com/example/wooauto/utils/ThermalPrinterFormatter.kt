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
        // 与 EscPosPrinter(nbCharPerLine) 保持一致：57/58mm=32列，80mm=42列
        private const val CHAR_COUNT_58MM = 32  // 58mm打印机一行约32个英文字符
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
            // 店铺名称使用专门的中文字体放大方案 - 支持中文和英文放大显示
            sb.append("[C]${formatChineseLargeFont(storeName, paperWidth)}\n")
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
            val maxCols = getCharsPerLine(paperWidth)
            val leftCols = visibleColumns(stripTags(left))
            val rightCols = visibleColumns(stripTags(right))

            // 若两端总列数超出行宽，降级为两行（允许刚好占满单行）
            if (leftCols + rightCols > maxCols) {
                return "[L]$left\n[R]$right\n"
            }

            val spacesNeeded = maxCols - leftCols - rightCols
            val spaces = " ".repeat(spacesNeeded)
            return "[L]$left$spaces$right\n"
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
            val quantityPrice = "${quantity} x $price"
            val maxChars = getCharsPerLine(paperWidth)
            
            // 使用专门的中文字体放大方案处理商品名称
            val formattedName = formatChineseLargeFont(name, paperWidth)
            
            // 如果商品名称和价格信息总长度超过行宽，分两行显示
            if (name.length + quantityPrice.length + 2 > maxChars) {
                val sb = StringBuilder()
                // 商品名称使用字体放大方案 - 支持中文和英文放大显示
                sb.append("[L]$formattedName\n")
                // 数量与单价行同样放大，保持视觉一致
                sb.append("[L]<h><w><b>  $quantityPrice</b></w></h>\n")
                return sb.toString()
            } else {
                // 使用formatLeftRightText来处理左右对齐，左右两侧都使用放大方案
                val enlargedRight = "<h><w><b>" + quantityPrice + "</b></w></h>"
                return formatLeftRightText(formattedName, enlargedRight, paperWidth)
            }
        }

        /**
         * 新版商品行格式：数量 名称 价格
         * - 优先尝试单行显示：左侧“数量 名称”，右侧“价格”
         * - 若空间不足，截断名称并添加省略号，尽量保持一行
         * - 字体：80mm 采用 <h><w><b> 放大；58mm 采用 <h><b>，尽量避免溢出
         */
        fun formatItemQtyNamePriceLine(quantity: Int, name: String, price: String, paperWidth: Int): String {
            val maxCols = getCharsPerLine(paperWidth)

            // 右侧价格列宽
            val pricePlain = price
            val rightCols = visibleColumns(pricePlain)

            // 左侧可用列：预留1列空格
            val qtyPrefix = "$quantity "
            val leftAvail = (maxCols - rightCols - 1).coerceAtLeast(1)

            // 名称按列宽截断
            val nameColsAvail = (leftAvail - visibleColumns(qtyPrefix)).coerceAtLeast(1)
            val nameTruncated = truncateToColumns(name, nameColsAvail)

            // 放大策略：避免改变列宽，仅用 <h>/<b>
            // 字号策略：80mm 使用双倍高度+加粗；58mm 使用加粗
            val qtyFormatted = when (paperWidth) {
                PrinterConfig.PAPER_WIDTH_80MM -> "<h><b>" + qtyPrefix.trimEnd() + "</b></h> "
                else -> "<b>" + qtyPrefix.trimEnd() + "</b> "
            }
            val nameFormatted = when (paperWidth) {
                PrinterConfig.PAPER_WIDTH_80MM -> "<h><b>$nameTruncated</b></h>"
                else -> "<b>$nameTruncated</b>"
            }
            val priceFormatted = when (paperWidth) {
                PrinterConfig.PAPER_WIDTH_80MM -> "<h><b>$pricePlain</b></h>"
                else -> "<b>$pricePlain</b>"
            }

            val leftFormatted = qtyFormatted + nameFormatted
            return formatLeftRightText(leftFormatted, priceFormatted, paperWidth)
        }

        /**
         * 三段式布局：| qty | name(wrap) | price |
         * - qty/price 采用固定列宽，name 在中间按列宽换行
         * - 整行使用 [L] 自行对齐，避免 <w> 等影响列宽
         */
        fun formatThreePartItemLine(quantity: Int, name: String, price: String, paperWidth: Int): String {
            val totalCols = getCharsPerLine(paperWidth)

            // 计算固定列宽：qty、price
            val qtyPlain = quantity.toString()
            val pricePlain = price

            val minQtyCols = 3 // 至少3列（含空格）
            val minPriceCols = if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM) 8 else 7

            val qtyCols = maxOf(visibleColumns(qtyPlain) + 1, minQtyCols) // +1留空格
            val priceCols = maxOf(visibleColumns(pricePlain), minPriceCols)

            // 中间可用列，预留安全右边距，确保价格稳定在第一行
            val safetyGap = if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM) 2 else 1
            val midCols = (totalCols - qtyCols - priceCols - safetyGap).coerceAtLeast(6)

            // 将名称按列宽换行
            val nameLines = wrapToColumns(name, midCols)

            // 放大策略（不改变列宽）
            val qtyCellFirst = enlargeNoWidth(qtyPlain, paperWidth)
            val priceCellFirst = enlargeNoWidth(pricePlain, paperWidth)

            // 组装多行（不对填充进行 trim，避免破坏对齐）
            val sb = StringBuilder()
            nameLines.forEachIndexed { index, seg ->
                val leftCellRaw = if (index == 0) padRightToColumns(qtyPlain, qtyCols) else " ".repeat(qtyCols)
                val midCellRaw = padRightToColumns(seg, midCols)
                val rightCellRaw = if (index == 0) padLeftToColumns(pricePlain, priceCols) else " ".repeat(priceCols)

                val leftOut = enlargeNoWidth(leftCellRaw, paperWidth)
                val midOut = enlargeNoWidth(midCellRaw, paperWidth)
                val rightOut = enlargeNoWidth(rightCellRaw, paperWidth)

                sb.append("[L]")
                sb.append(leftOut)
                sb.append(midOut)
                sb.append(rightOut)
                // 右边距安全空白，避免价格挤到下一行
                sb.append(" ".repeat(safetyGap))
                sb.append("\n")
            }

            return sb.toString()
        }

        /**
         * 三段式（价格固定在首行最右）：
         * - 首行： [qty + name_first] ... [price]
         * - 后续行： [qty 空白] + name_rest（仅中间列换行，不再绘制价格）
         */
        fun formatThreePartItemLinePinnedPrice(quantity: Int, name: String, price: String, paperWidth: Int): String {
            // 严格列宽：80mm=4|27|10(+1gap)，58mm=3|22|7
            val (qtyCols, midColsFirst, priceCols, safetyGap) = if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM) QuadCols(4, 27, 10, 1) else QuadCols(3, 22, 7, 0)

            val qtyPlain = quantity.toString()
            val pricePlain = price

            // 拆分名称：首行段 + 余下段
            val nameLines = wrapToColumns(name, midColsFirst)
            val firstSeg = nameLines.firstOrNull() ?: ""
            val restText = if (name.length > firstSeg.length) name.substring(firstSeg.length) else ""

            // 首行：左右对齐，锁定价格
            val qtyCell = padRightToColumns(qtyPlain, qtyCols)
            val nameFirstCell = padRightToColumns(firstSeg, midColsFirst)
            val leftFirst = qtyCell + nameFirstCell
            val rightFirst = pricePlain
            val firstLine = "[L]" + leftFirst + rightFirst.padStart(priceCols + safetyGap) + "\n"

            // 后续行：仅中间列换行
            val sb = StringBuilder()
            sb.append(firstLine)

            if (restText.isNotEmpty()) {
                val midColsNext = midColsFirst
                val remainLines = wrapToColumns(restText.trimStart(), midColsNext)
                remainLines.forEach { seg ->
                    val leftPad = padRightToColumns("", qtyCols)
                    val midCell = padRightToColumns(seg, midColsNext)
                    sb.append("[L]")
                    // 非厨房模板续行：使用小号（不加粗不放大）确保不会显得更大
                    sb.append(leftPad)
                    sb.append(midCell)
                    sb.append(" ".repeat(safetyGap))
                    sb.append("\n")
                }
            }

            return sb.toString()
        }

        private data class QuadCols(val a:Int,val b:Int,val c:Int,val d:Int)
        private operator fun QuadCols.component1()=a
        private operator fun QuadCols.component2()=b
        private operator fun QuadCols.component3()=c
        private operator fun QuadCols.component4()=d

        /**
         * 三段式表头：Qty | Item | Price
         */
        fun formatThreePartHeader(paperWidth: Int, qtyLabel: String = "Qty", nameLabel: String = "Item", priceLabel: String = "Price"): String {
            // 严格固定列宽：80mm=4|27|10(+1gap)，58mm=3|22|7
            val (qtyCols, midCols, priceCols, safetyGap) = if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM) QuadCols(4, 27, 10, 1) else QuadCols(3, 22, 7, 0)
            val leftCell = padRightToColumns(qtyLabel, qtyCols)
            val midCell = padRightToColumns(nameLabel, midCols)
            val rightCell = padLeftToColumns(priceLabel, priceCols)
            return "[L]" + leftCell + midCell + rightCell + " ".repeat(safetyGap) + "\n"
        }

        // 将文本按列宽换行（中文=2，英文=1），尽量按单词边界换行
        private fun wrapToColumns(text: String, maxCols: Int): List<String> {
            if (text.isEmpty()) return listOf("")
            var cols = 0
            val seg = StringBuilder()
            val out = mutableListOf<String>()
            var lastSpaceIndexInSeg = -1
            var colsAtLastSpace = 0

            text.forEach { ch ->
                val w = charColumns(ch)
                // 若本字符会溢出
                if (cols + w > maxCols) {
                    if (lastSpaceIndexInSeg >= 0) {
                        // 回退到上一个空格，优先在词边界换行
                        val line = seg.substring(0, lastSpaceIndexInSeg)
                        out.add(line)
                        // 重置seg为空格之后的余下部分
                        val remain = seg.substring(lastSpaceIndexInSeg + 1)
                        seg.clear()
                        seg.append(remain)
                        cols = visibleColumns(remain)
                    } else {
                        out.add(seg.toString())
                        seg.clear()
                        cols = 0
                    }
                    // 重置空格记录
                    lastSpaceIndexInSeg = -1
                    colsAtLastSpace = 0
                }
                seg.append(ch)
                cols += w
                if (ch == ' ') {
                    lastSpaceIndexInSeg = seg.length - 1
                    colsAtLastSpace = cols
                }
            }
            if (seg.isNotEmpty()) out.add(seg.toString())
            return out
        }

        // 右侧填充到列宽
        private fun padRightToColumns(text: String, targetCols: Int): String {
            val cols = visibleColumns(stripTags(text))
            val pad = (targetCols - cols).coerceAtLeast(0)
            return text + " ".repeat(pad)
        }

        // 左侧填充到列宽（价格右对齐）
        private fun padLeftToColumns(text: String, targetCols: Int): String {
            val cols = visibleColumns(stripTags(text))
            val pad = (targetCols - cols).coerceAtLeast(0)
            return " ".repeat(pad) + text
        }

        /**
         * 根据纸宽放大文本：
         * - 80mm 使用 <h><w><b>
         * - 58mm 使用 <h><b>
         */
        private fun enlargeForPaperWidth(text: String, paperWidth: Int): String {
            // 保留给标题等非对齐内容使用（可能包含 <w>）
            return if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM) {
                "<h><w><b>$text</b></w></h>"
            } else {
                "<h><b>$text</b></h>"
            }
        }

        // 仅用于参与单行对齐的内容，不改变列宽（不使用<w>）
        private fun enlargeNoWidth(text: String, paperWidth: Int): String {
            return if (paperWidth == PrinterConfig.PAPER_WIDTH_80MM) {
                "<h><b>$text</b></h>"
            } else {
                "<b>$text</b>"
            }
        }

        // 去除标签
        private fun stripTags(text: String): String = text.replace(Regex("<[^>]*>"), "")

        // 计算可见列宽：中文/全角=2，其他=1
        private fun visibleColumns(text: String): Int {
            var cols = 0
            text.forEach { ch -> cols += charColumns(ch) }
            return cols
        }

        private fun charColumns(ch: Char): Int {
            return if (
                ch.code in 0x4E00..0x9FFF || // CJK统一汉字
                ch.code in 0x3400..0x4DBF || // CJK扩展A
                ch.code in 0x3000..0x303F || // CJK符号和标点
                ch.code in 0xFF00..0xFFEF || // 全角ASCII与标点
                ch.code in 0xFE30..0xFE4F || // CJK兼容形式
                ch == '￥'
            ) 2 else 1
        }

        // 按列宽截断，超出以"..."结尾
        private fun truncateToColumns(text: String, maxCols: Int): String {
            if (maxCols <= 0) return ""
            var cols = 0
            val sb = StringBuilder()
            for (ch in text) {
                val w = charColumns(ch)
                if (cols + w > maxCols) break
                sb.append(ch)
                cols += w
            }
            if (visibleColumns(text) > maxCols) {
                val ell = "..."
                val ellCols = visibleColumns(ell)
                var keepCols = (maxCols - ellCols).coerceAtLeast(0)
                val base = StringBuilder()
                cols = 0
                for (ch in sb.toString()) {
                    val w = charColumns(ch)
                    if (cols + w > keepCols) break
                    base.append(ch)
                    cols += w
                }
                return base.toString() + ell
            }
            return sb.toString()
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
        
        /**
         * 为中文内容提供备用字体放大方案
         * 当硬件字体放大不支持中文时，使用字符间距等方式增强视觉效果
         * @param text 文本内容
         * @param paperWidth 打印纸宽度
         * @return 格式化后的文本
         */
        fun formatChineseLargeFont(text: String, paperWidth: Int): String {
            // 检测是否包含中文字符
            val hasChineseCharacters = text.any { char ->
                char.code in 0x4E00..0x9FFF || // CJK统一汉字
                char.code in 0x3400..0x4DBF || // CJK扩展A
                char.code in 0x3000..0x303F || // CJK符号和标点
                char.code in 0xFF00..0xFFEF    // 全角ASCII、全角标点符号
            }
            
            return if (hasChineseCharacters) {
                // 对于中文内容，使用字符间距来增强视觉效果
                val sb = StringBuilder()
                
                // 在中文字符之间添加空格，增加视觉间距
                var result = ""
                for (i in text.indices) {
                    result += text[i]
                    // 如果当前字符是中文，并且下一个字符也存在且是中文，添加半角空格
                    if (i < text.length - 1 && 
                        text[i].code in 0x4E00..0x9FFF && 
                        text[i + 1].code in 0x4E00..0x9FFF) {
                        result += " "
                    }
                }
                
                // 使用加粗和双倍标签组合
                "<h><w><b>$result</b></w></h>"
            } else {
                // 英文内容直接使用标准放大标签
                "<h><w><b>$text</b></w></h>"
            }
        }
    }
} 