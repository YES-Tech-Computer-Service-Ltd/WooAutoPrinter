package com.example.wooauto.utils

import android.content.Intent

/**
 * 错误源类型
 */
enum class ErrorSource {
    NETWORK,          // 网络连接问题
    BLUETOOTH,        // 蓝牙适配器问题（关闭/不可用）
    PRINTER_CONN,     // 打印机连接问题
    API,              // 通用 API 请求失败
    API_ORDER,        // 订单 API 请求失败
    API_PRODUCT,      // 产品 API 请求失败
    LICENSE,          // 授权验证失败
    SYSTEM,           // 系统级错误
    OTHER             // 其他
}

/**
 * 应用错误实体
 */
data class AppError(
    val id: String = java.util.UUID.randomUUID().toString(),
    val source: ErrorSource,
    val title: String,
    val message: String,
    val debugInfo: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val onSettingsAction: (() -> Unit)? = null
)

