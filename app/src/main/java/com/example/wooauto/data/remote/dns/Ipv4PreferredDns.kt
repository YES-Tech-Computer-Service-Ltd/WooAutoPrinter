package com.example.wooauto.data.remote.dns

import android.util.Log
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 自定义 DNS：优先返回 IPv4 地址。
 *
 * - 如果域名具备 IPv4 解析结果，则只返回 IPv4，直接避免走 IPv6。
 * - 如果没有 IPv4 解析结果，则回退到系统解析列表（通常就是 IPv6），
 *   确保不会因为完全过滤而导致域名不可用。
 * - 之后要重新启用 IPv6，只需改成返回 IPv4 + IPv6 的排序列表即可。
 */
object Ipv4PreferredDns : Dns {

    private const val TAG = "Ipv4PreferredDns"

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            val all = Dns.SYSTEM.lookup(hostname)
            if (all.isEmpty()) return all

            val ipv4List = all.filterIsInstance<Inet4Address>()
            if (ipv4List.isNotEmpty()) {
                // 使用 IPv4，日志方便排查 IPv6 问题
                Log.d(TAG, "命中IPv4优先策略，hostname=$hostname, count=${ipv4List.size}")
                return ipv4List
            }

            // 没有 IPv4 时不再过滤，维持默认行为，避免彻底断网
            Log.w(TAG, "未找到IPv4记录，hostname=$hostname，回退至系统解析结果")
            return all
        } catch (e: UnknownHostException) {
            // 保持原始异常
            throw e
        } catch (t: Throwable) {
            // 非预期错误时，回退系统实现，降低影响面
            Log.e(TAG, "DNS解析异常，fallback至系统行为: ${t.message}")
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}

