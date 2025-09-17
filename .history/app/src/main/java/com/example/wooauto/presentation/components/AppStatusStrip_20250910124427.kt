package com.example.wooauto.presentation.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppStatusStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var timeText by remember { mutableStateOf(formatTime(System.currentTimeMillis())) }
    var isWifiConnected by remember { mutableStateOf(false) }
    var isNetworkValidated by remember { mutableStateOf(false) }
    var batteryPercent by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    // 时间每30秒刷新
    LaunchedEffect(Unit) {
        while (true) {
            timeText = formatTime(System.currentTimeMillis())
            kotlinx.coroutines.delay(30_000)
        }
    }

    // 网络状态监听（仅在网络具备有效的互联网能力且通过验证时视为已连接）
    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                isWifiConnected = validated && (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                isNetworkValidated = validated && internet
            }
            override fun onLost(network: Network) {
                isNetworkValidated = false
                isWifiConnected = false
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: Throwable) {}

        onDispose {
            try { connectivityManager.unregisterNetworkCallback(callback) } catch (_: Throwable) {}
        }
    }

    // 电池状态监听
    DisposableEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0 && scale > 0) {
                    batteryPercent = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                }
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Throwable) {}
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
        }
    }

    Row(
        modifier = modifier.padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 时间
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Medium
        )

        // 网络
        if (isNetworkValidated) {
            Icon(
                imageVector = if (isWifiConnected) Icons.Filled.Wifi else Icons.Filled.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        // 电池
        Icon(
            imageVector = if (isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "$batteryPercent%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private fun formatTime(millis: Long): String {
    val is24 = try { DateFormat.is24HourFormat(null) } catch (_: Throwable) { true }
    val pattern = if (is24) "HH:mm" else "h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}


