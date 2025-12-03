package com.example.wooauto.domain.printer

/**
 * 连通性检测的统一结果结构
 */
data class PrinterConnectionCheckResult(
    val state: PrinterConnectionState,
    val summary: String,
    val detail: String? = null,
    val commandUsed: String? = null,
    val rawResponseHex: String? = null,
    val rawResponseDec: String? = null
) {
    val isSuccessful: Boolean
        get() = state == PrinterConnectionState.ONLINE || state == PrinterConnectionState.WARNING
}

/**
 * 连通性检测结果分类
 */
enum class PrinterConnectionState {
    ONLINE,
    WARNING,
    OFFLINE,
    ERROR
}

