package com.example.wooauto.data.printer.detect

import com.example.wooauto.domain.printer.PrinterVendor

/**
 * 供应商识别器：基于设备名称的轻量识别。
 * 不依赖任何外部 SDK，便于在扫描或连接阶段快速判别。
 */
object VendorDetector {
    /**
     * 根据设备名称判定是否为 Star 打印机
     */
    fun isStarByName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        // 常见 Star 打印机命名特征（可按需扩展/调整）
        val patterns = listOf(
            "star micronics",
            "star",
            "tsp",         // TSP系列
            "mc-",         // mC-Print系列
            "mc-print",
            "sm-s",        // 便携系列
            "sm-t",
            "mpop"         // mPOP
        )
        return patterns.any { lower.contains(it) }
    }

    /**
     * 基于名称的简单供应商识别
     */
    fun detectByName(name: String?): PrinterVendor {
        return if (isStarByName(name)) PrinterVendor.STAR else PrinterVendor.GENERIC
    }
}


