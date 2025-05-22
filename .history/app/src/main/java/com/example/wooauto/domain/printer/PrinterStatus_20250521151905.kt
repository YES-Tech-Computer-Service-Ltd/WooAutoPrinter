package com.example.wooauto.domain.printer

/**
 * 打印机状态枚举
 */
enum class PrinterStatus {
    CONNECTED,    // 已连接
    DISCONNECTED, // 已断开
    CONNECTING,   // 连接中
    ERROR,        // 错误
    IDLE         // 空闲状态
} 