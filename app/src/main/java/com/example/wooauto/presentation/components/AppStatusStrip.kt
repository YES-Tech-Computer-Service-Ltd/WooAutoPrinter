package com.example.wooauto.presentation.components

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
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
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isBluetoothConnected by remember { mutableStateOf(false) }

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
        
        fun updateStateFromNetwork(network: Network?) {
            val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isNetworkValidated = validated && internet
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateStateFromNetwork(network)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateStateFromNetwork(network)
            }

            override fun onLost(network: Network) {
                // 当默认网络丢失时，主动读取当前默认网络状态
                updateStateFromNetwork(connectivityManager.activeNetwork)
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            updateStateFromNetwork(connectivityManager.activeNetwork)
        } catch (_: Throwable) {}

        onDispose {
            try { connectivityManager.unregisterNetworkCallback(callback) } catch (_: Throwable) {}
        }
    }

    // 为了避免个别 ROM 不触发回调，增加一个定时轮询兜底
    LaunchedEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        while (true) {
            val network = connectivityManager.activeNetwork
            val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isNetworkValidated = validated && internet
            kotlinx.coroutines.delay(5_000)
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

    // 蓝牙状态监听
    DisposableEffect(Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        fun evaluateConnectionState(): Boolean {
            if (adapter == null) return false
            val profiles = listOf(
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP,
                BluetoothProfile.HEALTH,
                BluetoothProfile.GATT
            )
            return profiles.any {
                try {
                    adapter.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED
                } catch (_: Throwable) {
                    false
                }
            }
        }

        fun updateBluetoothState(extraConnectionState: Int? = null) {
            val enabled = adapter?.isEnabled == true
            isBluetoothEnabled = enabled
            isBluetoothConnected = when {
                !enabled -> false
                extraConnectionState != null -> extraConnectionState == BluetoothAdapter.STATE_CONNECTED
                else -> evaluateConnectionState()
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_OFF) {
                            isBluetoothEnabled = false
                            isBluetoothConnected = false
                        } else if (state == BluetoothAdapter.STATE_ON) {
                            updateBluetoothState()
                        }
                    }
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                        val connectionState = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_CONNECTION_STATE,
                            BluetoothAdapter.STATE_DISCONNECTED
                        )
                        updateBluetoothState(connectionState)
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> updateBluetoothState(BluetoothAdapter.STATE_CONNECTED)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> updateBluetoothState(BluetoothAdapter.STATE_DISCONNECTED)
                }
            }
        }

        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Throwable) {}

        updateBluetoothState()

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

        // 蓝牙
        val bluetoothIcon = when {
            isBluetoothEnabled && isBluetoothConnected -> Icons.Filled.BluetoothConnected
            isBluetoothEnabled -> Icons.Filled.Bluetooth
            else -> Icons.Filled.BluetoothDisabled
        }
        val bluetoothTint = if (isBluetoothEnabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        }
        Icon(
            imageVector = bluetoothIcon,
            contentDescription = null,
            tint = bluetoothTint,
            modifier = Modifier.size(18.dp)
        )

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


