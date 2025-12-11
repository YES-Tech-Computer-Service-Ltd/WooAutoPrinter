package com.example.wooauto.diagnostics.network

import com.example.wooauto.utils.UiLog
import okhttp3.Dns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络错误日志记录器：
 * - 外部调用为非 suspend（避免侵入现有网络代码）
 * - 内部使用 IO 协程写入 DataStore
 */
@Singleton
class NetworkErrorLogger @Inject constructor(
    private val store: NetworkErrorLogStore,
    private val snapshotProvider: NetworkSnapshotProvider
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun logApiFailure(
        endpoint: String?,
        siteUrl: String?,
        error: Throwable
    ) {
        scope.launch {
            try {
                val baseSnapshot = snapshotProvider.snapshotText()
                val enhancedSnapshot = enhanceSnapshotWithDnsCheck(baseSnapshot, siteUrl, error)
                val event = NetworkDiagnostics.buildEvent(
                    endpoint = endpoint,
                    siteUrl = siteUrl,
                    error = error,
                    networkSnapshot = enhancedSnapshot
                ) ?: return@launch

                store.record(event)
            } catch (e: Exception) {
                UiLog.e("NetworkErrorLogger", "记录网络报错日志失败: ${e.message}", e)
            }
        }
    }

    private fun enhanceSnapshotWithDnsCheck(baseSnapshot: String, siteUrl: String?, error: Throwable): String {
        // 只对“非 HTTP 业务错误（ApiError）”补充 DNS 快速检查，尽量避免无意义的额外开销。
        if (error is com.example.wooauto.data.remote.ApiError) return baseSnapshot

        val host = NetworkDiagnostics.parseHost(siteUrl) ?: return baseSnapshot
        val start = System.currentTimeMillis()
        val line = try {
            val addrs = Dns.SYSTEM.lookup(host)
            val ms = System.currentTimeMillis() - start
            "host=$host ms=$ms result=$addrs"
        } catch (e: Exception) {
            val ms = System.currentTimeMillis() - start
            "host=$host ms=$ms error=${e.javaClass.simpleName}: ${e.message}"
        }

        return buildString {
            appendLine(baseSnapshot.trimEnd())
            appendLine()
            appendLine("== Quick DNS Check (SYSTEM) ==")
            appendLine(line)
        }.trimEnd()
    }
}


