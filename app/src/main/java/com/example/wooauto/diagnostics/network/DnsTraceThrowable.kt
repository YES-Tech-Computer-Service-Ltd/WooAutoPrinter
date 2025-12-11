package com.example.wooauto.diagnostics.network

/**
 * 仅用于把“DNS 解析过程”挂在异常的 suppressed 里，方便上层统一记录日志。
 * - 不改变原始异常类型（避免影响现有 UI/重试逻辑）
 * - fillInStackTrace 置空，避免额外开销
 */
class DnsTraceThrowable(
    val trace: String
) : Throwable("DNS_TRACE") {
    override fun fillInStackTrace(): Throwable = this
}


