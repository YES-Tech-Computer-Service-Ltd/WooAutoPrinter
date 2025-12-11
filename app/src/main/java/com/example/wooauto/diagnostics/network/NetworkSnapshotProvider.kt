package com.example.wooauto.diagnostics.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 采集“当前网络快照”，用于定位 DNS/IPv/验证网络/传输类型等问题。
 * 注意：只读系统信息，不涉及任何权限敏感数据（如 WiFi SSID）。
 */
@Singleton
class NetworkSnapshotProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun snapshotText(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) return "ConnectivityManager unavailable"

            val net = cm.activeNetwork
            val caps = runCatching { cm.getNetworkCapabilities(net) }.getOrNull()
            val lp = runCatching { cm.getLinkProperties(net) }.getOrNull()

            buildString {
                appendLine("== Network Snapshot ==")
                appendLine("timeMs=${System.currentTimeMillis()}")
                appendLine("sdk=${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("activeNetwork=${net != null}")

                if (caps != null) appendNetworkCapabilities(caps) else appendLine("capabilities=null")
                if (lp != null) appendLinkProperties(lp) else appendLine("linkProperties=null")
            }.trimEnd()
        } catch (e: Exception) {
            "NetworkSnapshot error: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun StringBuilder.appendNetworkCapabilities(caps: NetworkCapabilities) {
        val transports = mutableListOf<String>().apply {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
        }

        appendLine("transports=${transports.joinToString(",").ifBlank { "NONE" }}")
        appendLine("hasInternet=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
        appendLine("validated=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
        appendLine("notMetered=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)}")
        appendLine("captivePortal=${caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)}")
    }

    private fun StringBuilder.appendLinkProperties(lp: LinkProperties) {
        appendLine("iface=${lp.interfaceName ?: "null"}")

        val dns = lp.dnsServers.mapNotNull { it.hostAddress }.distinct()
        appendLine("dnsServers=${dns.joinToString(",").ifBlank { "[]" }}")

        val addrs = lp.linkAddresses.mapNotNull { it.address?.hostAddress?.let { addr -> "$addr/${it.prefixLength}" } }
        val hasIpv4 = addrs.any { !it.contains(":") }
        val hasIpv6 = addrs.any { it.contains(":") }
        appendLine("linkAddresses=${addrs.joinToString(",").ifBlank { "[]" }}")
        appendLine("hasIpv4=$hasIpv4 hasIpv6=$hasIpv6")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appendLine("privateDnsActive=${lp.isPrivateDnsActive}")
            appendLine("privateDnsServerName=${lp.privateDnsServerName ?: "null"}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appendLine("mtu=${lp.mtu}")
        }
    }
}


