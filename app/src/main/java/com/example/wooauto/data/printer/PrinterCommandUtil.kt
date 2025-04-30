package com.example.wooauto.data.printer

import com.example.wooauto.domain.printer.PrinterBrand

/**
 * 打印机命令工具类
 * 封装了不同品牌打印机的ESC/POS命令
 */
object PrinterCommandUtil {
    
    /**
     * 初始化打印机命令 - ESC @
     */
    val INITIALIZE = byteArrayOf(0x1B, 0x40)
    
    /**
     * 清除缓冲区命令 - CAN
     */
    val CLEAR_BUFFER = byteArrayOf(0x18)
    
    /**
     * 换行命令 - LF
     */
    val LINE_FEED = byteArrayOf(0x0A)
    
    /**
     * 回车命令 - CR
     */
    val CARRIAGE_RETURN = byteArrayOf(0x0D)
    
    /**
     * 走纸命令 - ESC d n
     * @param lines 走纸行数
     * @return 走纸命令字节数组
     */
    fun getPaperFeedCommand(lines: Int): ByteArray {
        return byteArrayOf(0x1B, 0x64, lines.toByte())
    }
    
    /**
     * 获取切纸命令
     * @param brand 打印机品牌
     * @param partial 是否部分切纸，默认true
     * @return 切纸命令字节数组
     */
    fun getCutCommand(brand: PrinterBrand, partial: Boolean = true): ByteArray {
        return when (brand) {
            PrinterBrand.STAR -> {
                // STAR打印机特有切纸命令
                byteArrayOf(0x1B, 0x64, 0x02)
            }
            PrinterBrand.EPSON, PrinterBrand.BIXOLON, PrinterBrand.SPRT, PrinterBrand.CITIZEN -> {
                // ESC/POS标准切纸命令
                if (partial) {
                    byteArrayOf(0x1D, 0x56, 0x01)  // GS V 1 - 部分切纸
                } else {
                    byteArrayOf(0x1D, 0x56, 0x00)  // GS V 0 - 全切
                }
            }
            else -> {
                // 默认使用ESC/POS标准切纸命令
                if (partial) {
                    byteArrayOf(0x1D, 0x56, 0x01)  // GS V 1 - 部分切纸
                } else {
                    byteArrayOf(0x1D, 0x56, 0x00)  // GS V 0 - 全切
                }
            }
        }
    }
    
    /**
     * 获取走纸并切纸的组合命令
     * @param brand 打印机品牌
     * @param feedLines 走纸行数
     * @param partial 是否部分切纸，默认true
     * @return 走纸并切纸的组合命令字节数组
     */
    fun getFeedAndCutCommand(brand: PrinterBrand, feedLines: Int = 4, partial: Boolean = true): ByteArray {
        val feedCommand = getPaperFeedCommand(feedLines)
        val cutCommand = getCutCommand(brand, partial)
        
        // 组合命令
        return feedCommand + cutCommand
    }
    
    /**
     * 获取字体样式命令
     * @param bold 是否加粗
     * @param doubleHeight 是否双倍高度
     * @param doubleWidth 是否双倍宽度
     * @param underline 是否下划线
     * @return 字体样式命令字节数组
     */
    fun getFontStyleCommand(
        bold: Boolean = false,
        doubleHeight: Boolean = false,
        doubleWidth: Boolean = false,
        underline: Boolean = false
    ): ByteArray {
        val result = mutableListOf<Byte>()
        
        // 加粗命令 - ESC E n
        if (bold) {
            result.addAll(byteArrayOf(0x1B, 0x45, 0x01).toList())
        }
        
        // 下划线命令 - ESC - n
        if (underline) {
            result.addAll(byteArrayOf(0x1B, 0x2D, 0x01).toList())
        }
        
        // 字体大小命令 - ESC ! n
        var fontSizeParam: Byte = 0
        if (doubleWidth) fontSizeParam = (fontSizeParam.toInt() or 0x20).toByte()
        if (doubleHeight) fontSizeParam = (fontSizeParam.toInt() or 0x10).toByte()
        
        if (doubleWidth || doubleHeight) {
            result.addAll(byteArrayOf(0x1B, 0x21, fontSizeParam).toList())
        }
        
        return result.toByteArray()
    }
    
    /**
     * 获取对齐方式命令
     * @param alignment 对齐方式：0-左对齐，1-居中，2-右对齐
     * @return 对齐方式命令字节数组
     */
    fun getAlignmentCommand(alignment: Int): ByteArray {
        val param = when (alignment) {
            1 -> 0x01.toByte()  // 居中
            2 -> 0x02.toByte()  // 右对齐
            else -> 0x00.toByte()  // 左对齐
        }
        
        // ESC a n
        return byteArrayOf(0x1B, 0x61, param)
    }
    
    /**
     * 获取心跳命令 - 用于保持打印机连接
     * @param brand 打印机品牌
     * @return 心跳命令字节数组
     */
    fun getHeartbeatCommand(brand: PrinterBrand): ByteArray {
        // 目前所有打印机都使用初始化命令作为心跳，未来可以根据品牌定制
        return INITIALIZE
    }
    
    /**
     * 获取打印并走纸命令
     * 有些打印机需要这个命令来触发缓冲区的打印
     * @return 打印并走纸命令字节数组
     */
    fun getPrintFeedCommand(): ByteArray {
        // 依次发送回车、换行、走纸命令
        return byteArrayOf(0x0D, 0x0A, 0x1B, 0x64, 0x01)
    }
} 