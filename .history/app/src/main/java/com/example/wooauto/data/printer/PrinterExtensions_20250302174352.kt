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
        this.disconnectPrinter()
    } catch (e: Exception) {
        Log.e("PrinterExtensions", "断开打印机连接失败: ${e.message}", e)
    }
} 