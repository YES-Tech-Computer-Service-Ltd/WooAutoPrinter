package com.example.wooauto.data.printer

import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterBrand
import java.io.ByteArrayOutputStream

/**
 * 打印机扩展方法
 */

/**
 * 断开打印机连接
 */
fun EscPosPrinter.disconnectPrinter() {
    try {
        // 关闭打印机连接
        try {
            // 尝试调用EscPosPrinter的close方法
            val closeMethod = this.javaClass.getMethod("close")
            closeMethod.invoke(this)
        } catch (e: NoSuchMethodException) {
            // 如果没有close方法，尝试断开连接
            val connection = this.javaClass.getDeclaredField("printerConnection")
            connection.isAccessible = true
            val printerConnection = connection.get(this)
            val disconnectMethod = printerConnection.javaClass.getMethod("disconnect")
            disconnectMethod.invoke(printerConnection)
        }
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "断开打印机连接失败: ${e.message}", e)
    }
}

/**
 * 发送ESC/POS初始化命令
 */
fun EscPosPrinter.initializePrinter() {
    try {
        // 获取打印机连接
        val connection = this.javaClass.getDeclaredField("printerConnection")
        connection.isAccessible = true
        val printerConnection = connection.get(this)
        
        // 初始化打印机 ESC @
        val initCommand = byteArrayOf(0x1B, 0x40)
        val writeMethod = printerConnection.javaClass.getMethod("write", ByteArray::class.java)
        writeMethod.invoke(printerConnection, initCommand)
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "初始化打印机失败: ${e.message}", e)
    }
}

/**
 * 清除打印缓冲区
 */
fun EscPosPrinter.clearBuffer() {
    try {
        // 获取打印机连接
        val connection = this.javaClass.getDeclaredField("printerConnection")
        connection.isAccessible = true
        val printerConnection = connection.get(this)
        
        // 发送清除缓冲区命令 CAN (0x18)
        val clearCommand = byteArrayOf(0x18)
        val writeMethod = printerConnection.javaClass.getMethod("write", ByteArray::class.java)
        writeMethod.invoke(printerConnection, clearCommand)
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "清除打印缓冲区失败: ${e.message}", e)
    }
}

/**
 * 发送走纸命令
 * @param lines 走纸行数
 */
fun EscPosPrinter.feedPaper(lines: Int = 3) {
    try {
        // 获取打印机连接
        val connection = this.javaClass.getDeclaredField("printerConnection")
        connection.isAccessible = true
        val printerConnection = connection.get(this)
        
        // 发送走纸命令 ESC d n
        val feedCommand = byteArrayOf(0x1B, 0x64, lines.toByte())
        val writeMethod = printerConnection.javaClass.getMethod("write", ByteArray::class.java)
        writeMethod.invoke(printerConnection, feedCommand)
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "走纸命令失败: ${e.message}", e)
    }
}

/**
 * 执行打印机切纸
 * @param config 打印机配置
 * @param partial 是否部分切纸，默认true
 */
fun EscPosPrinter.cutPaper(config: PrinterConfig, partial: Boolean = true) {
    try {
        // 获取打印机连接
        val connection = this.javaClass.getDeclaredField("printerConnection")
        connection.isAccessible = true
        val printerConnection = connection.get(this)
        val writeMethod = printerConnection.javaClass.getMethod("write", ByteArray::class.java)
        
        // 首先走纸以确保内容完全打印出来
        writeMethod.invoke(printerConnection, byteArrayOf(0x1B, 0x64, 4)) // 走纸4行
        Thread.sleep(100)
        
        // 发送切纸命令
        val cutCommand = when {
            // Star打印机使用特殊的切纸命令
            config.brand == PrinterBrand.STAR -> byteArrayOf(0x1B, 0x64, 0x02)
            
            // 普通ESC/POS打印机的切纸命令
            partial -> byteArrayOf(0x1D, 0x56, 0x01)  // 部分切纸
            else -> byteArrayOf(0x1D, 0x56, 0x00)     // 全切
        }
        
        writeMethod.invoke(printerConnection, cutCommand)
        
        // 再次走纸确保切纸完成
        Thread.sleep(100)
        writeMethod.invoke(printerConnection, byteArrayOf(0x0A, 0x0D)) // 换行回车
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "切纸失败: ${e.message}", e)
    }
}

/**
 * 发送打印机心跳命令，保持连接活跃
 */
fun EscPosPrinter.sendHeartbeat() {
    try {
        // 获取打印机连接
        val connection = this.javaClass.getDeclaredField("printerConnection")
        connection.isAccessible = true
        val printerConnection = connection.get(this)
        
        // 发送无影响的初始化命令作为心跳
        val heartbeatCommand = byteArrayOf(0x1B, 0x40)  // ESC @
        val writeMethod = printerConnection.javaClass.getMethod("write", ByteArray::class.java)
        writeMethod.invoke(printerConnection, heartbeatCommand)
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "发送心跳命令失败: ${e.message}", e)
    }
} 