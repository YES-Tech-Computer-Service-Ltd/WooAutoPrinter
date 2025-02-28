package com.example.wooauto.data.printer

import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter

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