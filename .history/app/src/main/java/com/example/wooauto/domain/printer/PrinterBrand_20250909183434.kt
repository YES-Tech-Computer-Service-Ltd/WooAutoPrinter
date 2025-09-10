package com.example.wooauto.domain.printer

/**
 * 打印机品牌信息
 * 定义了支持的打印机品牌和对应的命令语言
 */
enum class PrinterBrand(
    val displayName: String,
    val commandLanguage: String
) {
    UNKNOWN(
        "unknown",
        "ESC/POS"
    );
} 