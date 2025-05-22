package com.example.wooauto.domain.printer

/**
 * 打印机状态枚举
 */
enum class PrinterStatus {
    CONNECTED,    // 已连接
    DISCONNECTED, // 已断开
    CONNECTING,   // 连接中
    ERROR,        // 错误
    IDLE,         // 空闲状态
    PAPER_OUT,    // 缺纸
    OVERHEATED,   // 过热
    LOW_BATTERY   // 电量低
} 