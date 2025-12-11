package com.example.wooauto.diagnostics.network

import com.example.wooauto.data.remote.ApiError
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.cancellation.CancellationException

object NetworkDiagnostics {

    fun buildEvent(
        endpoint: String?,
        siteUrl: String?,
        error: Throwable,
        networkSnapshot: String?
    ): NetworkErrorEvent? {
        if (error is CancellationException) return null

        val now = System.currentTimeMillis()
        val host = parseHost(siteUrl)
        val httpCode = (error as? ApiError)?.code

        val root = rootCause(error)
        val throwableClass = error.javaClass.name
        val rootClass = root.javaClass.name

        val dnsTrace = extractDnsTrace(error)
        val sanitizedMsg = sanitizeMessage(root.message ?: error.message)

        val issueType = classify(error = error, root = root, httpCode = httpCode, dnsTrace = dnsTrace, message = sanitizedMsg)
        val fingerprint = fingerprint(
            issueType = issueType,
            host = host,
            httpCode = httpCode,
            rootCauseClass = rootClass,
            extraKey = fingerprintExtraKey(issueType, dnsTrace, sanitizedMsg)
        )

        val title = buildTitle(issueType, httpCode)
        val summary = buildSummary(issueType, httpCode, host, sanitizedMsg)
        val details = buildDetails(
            now = now,
            endpoint = endpoint,
            siteUrl = siteUrl,
            siteHost = host,
            issueType = issueType,
            httpCode = httpCode,
            throwableClass = throwableClass,
            rootCauseClass = rootClass,
            message = sanitizedMsg,
            dnsTrace = dnsTrace,
            networkSnapshot = networkSnapshot
        )

        return NetworkErrorEvent(
            fingerprint = fingerprint,
            now = now,
            endpoint = endpoint,
            siteHost = host,
            siteUrl = siteUrl,
            httpCode = httpCode,
            throwableClass = throwableClass,
            rootCauseClass = rootClass,
            message = sanitizedMsg,
            dnsTrace = dnsTrace,
            issueType = issueType,
            title = title,
            summary = summary,
            details = details,
            networkSnapshot = networkSnapshot
        )
    }

    fun parseHost(siteUrl: String?): String? {
        val raw = siteUrl?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw.toHttpUrlOrNull()?.host
    }

    fun rootCause(t: Throwable): Throwable {
        var cur: Throwable = t
        val seen = HashSet<Throwable>(8)
        while (true) {
            val next = cur.cause ?: break
            if (!seen.add(next)) break
            cur = next
        }
        return cur
    }

    fun extractDnsTrace(t: Throwable): String? {
        val seen = HashSet<Throwable>(16)

        fun scan(x: Throwable?): String? {
            if (x == null) return null
            if (!seen.add(x)) return null

            x.suppressed?.forEach { s ->
                if (s is DnsTraceThrowable) return s.trace
            }

            return scan(x.cause)
        }

        return scan(t)
    }

    private fun classify(
        error: Throwable,
        root: Throwable,
        httpCode: Int?,
        dnsTrace: String?,
        message: String?
    ): NetworkIssueType {
        if (error is ApiError && httpCode != null) {
            return when {
                httpCode == 401 || httpCode == 403 -> NetworkIssueType.HTTP_AUTH
                httpCode == 429 -> NetworkIssueType.HTTP_RATE_LIMIT
                httpCode in 400..499 -> NetworkIssueType.HTTP_CLIENT
                httpCode in 500..599 -> NetworkIssueType.HTTP_SERVER
                else -> NetworkIssueType.HTTP_OTHER
            }
        }

        // 先识别“明确无网”的提示（NetworkStatusInterceptor / 自定义文案）
        if (message?.contains("网络未连接") == true || message?.contains("no network", ignoreCase = true) == true) {
            return NetworkIssueType.NO_NETWORK
        }

        // DNS/IPv
        if (root is UnknownHostException) return NetworkIssueType.DNS
        if (dnsTrace?.contains("没有找到 IPv4") == true) return NetworkIssueType.IPV

        // Timeout
        if (root is SocketTimeoutException) return NetworkIssueType.TIMEOUT

        // TLS / SSL
        if (root is SSLHandshakeException || root is SSLPeerUnverifiedException || root is SSLException) return NetworkIssueType.TLS

        // Connection
        if (root is ConnectException || root is NoRouteToHostException) return NetworkIssueType.CONNECTION
        if (root is SocketException) return NetworkIssueType.CONNECTION

        // IO-ish
        if (root is EOFException) return NetworkIssueType.IO
        if (root is IOException) return NetworkIssueType.IO

        return NetworkIssueType.UNKNOWN
    }

    private fun fingerprint(
        issueType: NetworkIssueType,
        host: String?,
        httpCode: Int?,
        rootCauseClass: String,
        extraKey: String?
    ): String {
        val hostPart = host ?: "unknown-host"
        val codePart = httpCode?.toString() ?: "-"
        val extra = extraKey ?: "-"
        return "${issueType.name}|$hostPart|$codePart|$rootCauseClass|$extra"
    }

    private fun fingerprintExtraKey(issueType: NetworkIssueType, dnsTrace: String?, message: String?): String? {
        return when (issueType) {
            NetworkIssueType.DNS, NetworkIssueType.IPV -> {
                when {
                    dnsTrace?.contains("[DoH-Google] 解析失败") == true -> "doh_fail"
                    dnsTrace?.contains("降级使用系统 DNS") == true -> "fallback_system_dns"
                    else -> null
                }
            }
            NetworkIssueType.TLS -> {
                when {
                    message?.contains("handshake", ignoreCase = true) == true -> "handshake"
                    message?.contains("certificate", ignoreCase = true) == true -> "cert"
                    else -> null
                }
            }
            NetworkIssueType.TIMEOUT -> {
                when {
                    message?.contains("connect", ignoreCase = true) == true -> "connect_timeout"
                    message?.contains("read", ignoreCase = true) == true -> "read_timeout"
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun buildTitle(issueType: NetworkIssueType, httpCode: Int?): String {
        return when (issueType) {
            NetworkIssueType.NO_NETWORK -> "网络未连接"
            NetworkIssueType.DNS -> "DNS 域名解析失败"
            NetworkIssueType.IPV -> "IP/IPv6 兼容问题"
            NetworkIssueType.TIMEOUT -> "网络超时"
            NetworkIssueType.CONNECTION -> "网络连接失败"
            NetworkIssueType.TLS -> "HTTPS/证书握手失败"
            NetworkIssueType.HTTP_AUTH -> "接口鉴权失败 (${httpCode ?: "401/403"})"
            NetworkIssueType.HTTP_RATE_LIMIT -> "接口限流 (429)"
            NetworkIssueType.HTTP_CLIENT -> "客户端请求错误 (${httpCode ?: "4xx"})"
            NetworkIssueType.HTTP_SERVER -> "服务器错误 (${httpCode ?: "5xx"})"
            NetworkIssueType.HTTP_OTHER -> "HTTP 错误 (${httpCode ?: "unknown"})"
            NetworkIssueType.IO -> "网络 IO 错误"
            NetworkIssueType.UNKNOWN -> "未知网络错误"
        }
    }

    private fun buildSummary(issueType: NetworkIssueType, httpCode: Int?, host: String?, message: String?): String {
        val h = host ?: "unknown-host"
        return when (issueType) {
            NetworkIssueType.DNS -> "无法解析域名：$h（DNS 失败）。"
            NetworkIssueType.IPV -> "可能存在 IPv4/IPv6 兼容问题：$h。"
            NetworkIssueType.TIMEOUT -> "请求超时：$h（可能网络差或服务器慢）。"
            NetworkIssueType.TLS -> "安全连接失败：$h（证书/时间/代理）。"
            NetworkIssueType.CONNECTION -> "连接建立失败：$h（被拒绝/路由不可达/被重置）。"
            NetworkIssueType.NO_NETWORK -> "设备当前无可用网络连接。"
            NetworkIssueType.HTTP_SERVER -> "服务器返回 ${httpCode ?: "5xx"}：$h（服务端异常或网关问题）。"
            NetworkIssueType.HTTP_CLIENT -> "服务器返回 ${httpCode ?: "4xx"}：$h（请求/配置可能有误）。"
            NetworkIssueType.HTTP_AUTH -> "鉴权失败 ${httpCode ?: ""}：请检查 API Key 权限。"
            NetworkIssueType.HTTP_RATE_LIMIT -> "请求被限流：请降低轮询频率或稍后重试。"
            else -> message?.let { "错误：${it.take(80)}" } ?: "网络异常：$h"
        }
    }

    private fun buildDetails(
        now: Long,
        endpoint: String?,
        siteUrl: String?,
        siteHost: String?,
        issueType: NetworkIssueType,
        httpCode: Int?,
        throwableClass: String,
        rootCauseClass: String,
        message: String?,
        dnsTrace: String?,
        networkSnapshot: String?
    ): String {
        return buildString {
            appendLine("== Network Error Diagnosis ==")
            appendLine("timeMs=$now")
            appendLine("issueType=${issueType.name}")
            if (httpCode != null) appendLine("httpCode=$httpCode")
            appendLine("endpoint=${endpoint ?: "-"}")
            appendLine("siteHost=${siteHost ?: "-"}")
            appendLine("siteUrl=${siteUrl ?: "-"}")
            appendLine("throwable=$throwableClass")
            appendLine("rootCause=$rootCauseClass")
            appendLine("message=${message ?: "-"}")

            if (!dnsTrace.isNullOrBlank()) {
                appendLine()
                appendLine("== DNS Trace ==")
                appendLine(dnsTrace.trim())
            }

            if (!networkSnapshot.isNullOrBlank()) {
                appendLine()
                appendLine(networkSnapshot.trim())
            }

            appendLine()
            appendLine("== Suggestions ==")
            suggestions(issueType, httpCode, dnsTrace).forEach { s -> appendLine("- $s") }
        }.trimEnd()
    }

    private fun suggestions(issueType: NetworkIssueType, httpCode: Int?, dnsTrace: String?): List<String> {
        return when (issueType) {
            NetworkIssueType.NO_NETWORK -> listOf(
                "检查是否已连接 Wi‑Fi/移动数据，或是否处于飞行模式",
                "如果连着 Wi‑Fi 但无法上网，可能是需要登录的“认证网络”(captive portal)"
            )
            NetworkIssueType.DNS -> listOf(
                "检查站点域名是否正确、是否能在其他设备解析",
                "检查路由器/运营商 DNS 是否异常（可尝试切换网络）",
                "若系统启用了“私有 DNS”，可尝试改为自动/关闭后再试",
                if (dnsTrace?.contains("[DoH-Google] 解析失败") == true) "日志显示 DoH(Google) 解析失败：在某些网络环境可能被拦截/不稳定" else "如持续失败，优先关注本地 DNS/私有 DNS/路由器 DNS"
            )
            NetworkIssueType.IPV -> listOf(
                "日志显示没有 IPv4 解析结果：可能是 IPv6-only 或 IPv6 质量差导致连接异常",
                "可尝试切换网络或调整路由器的 IPv6 设置（仅作为排查手段）"
            )
            NetworkIssueType.TIMEOUT -> listOf(
                "可能是网络质量差/丢包/信号弱，或服务器响应慢",
                "可尝试切换网络；若频繁发生，请检查服务器负载与响应时间"
            )
            NetworkIssueType.CONNECTION -> listOf(
                "可能是服务器端口不可达/被防火墙拦截/连接被重置",
                "如果“断网重连”能恢复，常见原因是路由器/NAT/运营商链路抖动导致连接假死"
            )
            NetworkIssueType.TLS -> listOf(
                "检查设备系统时间是否正确（时间错误会导致证书校验失败）",
                "如果在公司/门店网络有代理或中间人设备，可能导致证书校验失败",
                "若服务器证书链配置有误，需要在服务端修复"
            )
            NetworkIssueType.HTTP_AUTH -> listOf(
                "检查 consumer_key / consumer_secret 是否正确、权限是否足够（读订单/写状态等）"
            )
            NetworkIssueType.HTTP_RATE_LIMIT -> listOf(
                "服务器触发限流：建议降低轮询频率或增加退避重试"
            )
            NetworkIssueType.HTTP_SERVER -> listOf(
                "这是服务端/网关错误：建议查看 WooCommerce/WordPress 日志、反代/防火墙日志",
                "如果偶发，可稍后重试；若持续出现，通常需要服务端修复"
            )
            NetworkIssueType.HTTP_CLIENT -> listOf(
                "检查站点 URL、API 路径是否正确，或参数是否符合要求",
                "若为 404，通常是 URL/路径配置错误；若为 400，通常是请求参数/格式问题"
            )
            NetworkIssueType.HTTP_OTHER -> listOf("检查服务器返回的状态码与错误信息")
            NetworkIssueType.IO -> listOf("可能是底层 IO/连接中断：建议结合网络快照与 DNS 信息排查")
            NetworkIssueType.UNKNOWN -> listOf("建议先切换网络测试；若可复现，请导出日志用于进一步定位")
        }
    }

    private fun sanitizeMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        var s = raw
        // 脱敏常见 query 参数
        s = s.replace(Regex("(consumer_key=)[^&\\s]+"), "$1***")
        s = s.replace(Regex("(consumer_secret=)[^&\\s]+"), "$1***")
        // 过长截断（避免日志过大/隐私风险）
        if (s.length > 800) s = s.take(800) + "…(truncated)"
        return s
    }
}


