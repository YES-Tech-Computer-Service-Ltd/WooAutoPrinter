package com.example.wooauto.diagnostics.network

/**
 * 诊断后的网络问题类型（用于展示与去重）
 */
enum class NetworkIssueType {
    NO_NETWORK,
    DNS,
    IPV,
    TIMEOUT,
    CONNECTION,
    TLS,
    HTTP_AUTH,
    HTTP_CLIENT,
    HTTP_SERVER,
    HTTP_RATE_LIMIT,
    HTTP_OTHER,
    IO,
    UNKNOWN
}

/**
 * 单条“去重后”的网络问题日志。
 * - 同 fingerprint 的问题不会新增，只会累加 count 与更新 lastSeen。
 * - 为了能看到“具体层面的问题”，保留最近若干次样本（recentSamples）。
 */
data class NetworkErrorLogEntry(
    val fingerprint: String,
    val issueType: NetworkIssueType,
    val title: String,
    val summary: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val count: Int,
    val lastDetails: String,
    val lastNetworkSnapshot: String?,
    val recentSamples: List<String> = emptyList()
)

/**
 * 记录一次网络异常（输入事件，未去重）。
 */
data class NetworkErrorEvent(
    val fingerprint: String,
    val now: Long,
    val endpoint: String?,
    val siteHost: String?,
    val siteUrl: String?,
    val httpCode: Int?,
    val throwableClass: String,
    val rootCauseClass: String,
    val message: String?,
    val dnsTrace: String?,
    val issueType: NetworkIssueType,
    val title: String,
    val summary: String,
    val details: String,
    val networkSnapshot: String?,
)


