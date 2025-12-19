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
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.example.wooauto.R
import com.example.wooauto.WooAutoApplication
import com.example.wooauto.data.remote.ApiError
import com.example.wooauto.data.remote.exfood.ExFoodStoreStatusApi
import com.example.wooauto.di.StoreStatusEntryPoint
import com.example.wooauto.domain.repositories.DomainSettingRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppStatusStrip(
    modifier: Modifier = Modifier,
    hasEligibility: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var timeText by remember { mutableStateOf(formatTime(System.currentTimeMillis())) }
    var isWifiConnected by remember { mutableStateOf(false) }
    var isNetworkValidated by remember { mutableStateOf(false) }
    var batteryPercent by remember { mutableStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var isBluetoothEnabled by remember { mutableStateOf(false) }
    var isBluetoothConnected by remember { mutableStateOf(false) }

    // ===== Store Status (ExFood) =====
    val app = remember(context) { context.applicationContext as WooAutoApplication }
    val wooConfig = app.wooCommerceConfig
    val siteUrl by wooConfig.siteUrl.collectAsState(initial = "")
    val useWooCommerceFood by wooConfig.useWooCommerceFood.collectAsState(initial = false)
    val isConfigured by wooConfig.isConfiguredFlow.collectAsState(initial = false)

    val showStoreStatusEntry = hasEligibility && useWooCommerceFood && isConfigured

    // Default is "Open" (营业中). We only refine after a successful GET.
    var storeStatus by remember(siteUrl) { mutableStateOf(StoreStatusUi.OPEN_SCHEDULED) }
    var didInitialStoreStatusFetch by remember(siteUrl) { mutableStateOf(false) }
    var storeStatusBusy by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<StoreStatusAction?>(null) }

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf("") }
    var errorUserMessage by remember { mutableStateOf("") }
    var errorDebugMessage by remember { mutableStateOf("") }

    val okHttpClient = remember {
        EntryPointAccessors.fromApplication(app, StoreStatusEntryPoint::class.java).okHttpClient()
    }
    val gson = remember {
        EntryPointAccessors.fromApplication(app, StoreStatusEntryPoint::class.java).gson()
    }
    val settingsRepository = remember {
        EntryPointAccessors.fromApplication(app, StoreStatusEntryPoint::class.java).settingsRepository()
    }

    // First show: try GET once to align status. Failure -> keep default "营业中" and don't alert.
    LaunchedEffect(showStoreStatusEntry, siteUrl) {
        if (showStoreStatusEntry && !didInitialStoreStatusFetch) {
            didInitialStoreStatusFetch = true
            try {
                val resp = ExFoodStoreStatusApi.getStoreStatus(okHttpClient, gson, siteUrl)
                val raw = resp.status ?: resp.rawValue
                storeStatus = StoreStatusUi.fromApi(raw)
                syncPreferredOpenStatusIfOpen(settingsRepository, raw)
            } catch (_: Throwable) {
                // Keep default "营业中" and stay silent for initial fetch.
            }
        }
    }

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
        // 店铺营业状态（仅在：已授权 + 已启用插件 + 已配置 API 时显示）
        if (showStoreStatusEntry) {
            val pillText = when (storeStatus) {
                StoreStatusUi.OPEN_SCHEDULED -> stringResource(R.string.store_status_open)
                StoreStatusUi.OPEN_ALWAYS -> stringResource(R.string.store_status_open_all_day)
                StoreStatusUi.CLOSED -> stringResource(R.string.store_status_closed)
            }

            StoreStatusPill(
                text = pillText,
                busy = storeStatusBusy,
                onClick = {
                    if (storeStatusBusy) return@StoreStatusPill
                    storeStatusBusy = true
                    coroutineScope.launch {
                        try {
                            // Always GET first. If GET fails -> do not allow switching.
                            val resp = ExFoodStoreStatusApi.getStoreStatus(okHttpClient, gson, siteUrl)
                            val raw = resp.status ?: resp.rawValue
                            storeStatus = StoreStatusUi.fromApi(raw)
                            syncPreferredOpenStatusIfOpen(settingsRepository, raw)

                            pendingAction = when (storeStatus) {
                                StoreStatusUi.CLOSED -> resolveOpenAction(
                                    settingsRepository = settingsRepository,
                                    remoteRawStatus = raw
                                )
                                StoreStatusUi.OPEN_SCHEDULED,
                                StoreStatusUi.OPEN_ALWAYS -> StoreStatusAction.SWITCH_TO_CLOSED
                            }
                        } catch (t: Throwable) {
                            // 保持当前状态不变，提示联系技术人员，并在详情中展示报错
                            errorTitle = context.getString(R.string.store_status_error_title)
                            errorUserMessage = context.getString(R.string.store_status_error_get_user_message)
                            errorDebugMessage = buildStoreStatusDebug(
                                action = "GET store-status (click)",
                                siteUrl = siteUrl,
                                isNetworkValidated = isNetworkValidated,
                                isWifiConnected = isWifiConnected,
                                isBluetoothEnabled = isBluetoothEnabled,
                                isBluetoothConnected = isBluetoothConnected,
                                throwable = t
                            )
                            showErrorDialog = true
                        } finally {
                            storeStatusBusy = false
                        }
                    }
                }
            )
        }

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

    // 确认切换对话框（GET 成功后才会出现）
    pendingAction?.let { action ->
        val titleRes = when (action) {
            StoreStatusAction.SWITCH_TO_CLOSED -> R.string.store_status_confirm_close_title
            StoreStatusAction.SWITCH_TO_OPEN_SCHEDULED,
            StoreStatusAction.SWITCH_TO_OPEN_ALWAYS -> R.string.store_status_confirm_open_title
        }
        val messageRes = when (action) {
            StoreStatusAction.SWITCH_TO_CLOSED -> R.string.store_status_confirm_close_message
            StoreStatusAction.SWITCH_TO_OPEN_SCHEDULED -> R.string.store_status_confirm_open_message
            StoreStatusAction.SWITCH_TO_OPEN_ALWAYS -> R.string.store_status_confirm_open_all_day_message
        }

        AlertDialog(
            onDismissRequest = { if (!storeStatusBusy) pendingAction = null },
            title = { Text(text = stringResource(titleRes)) },
            text = { Text(text = stringResource(messageRes)) },
            confirmButton = {
                TextButton(
                    enabled = !storeStatusBusy,
                    onClick = {
                        if (storeStatusBusy) return@TextButton
                        storeStatusBusy = true
                        coroutineScope.launch {
                            try {
                                val target = when (action) {
                                    StoreStatusAction.SWITCH_TO_CLOSED -> ExFoodStoreStatusApi.STATUS_CLOSED
                                    StoreStatusAction.SWITCH_TO_OPEN_SCHEDULED -> ExFoodStoreStatusApi.STATUS_ENABLE
                                    StoreStatusAction.SWITCH_TO_OPEN_ALWAYS -> ExFoodStoreStatusApi.STATUS_DISABLE
                                }

                                val resp = ExFoodStoreStatusApi.updateStoreStatus(okHttpClient, gson, siteUrl, target)
                                val raw = resp.newStatus ?: resp.internalValue ?: target
                                storeStatus = StoreStatusUi.fromApi(raw)
                                syncPreferredOpenStatusIfOpen(settingsRepository, raw)
                                pendingAction = null
                            } catch (t: Throwable) {
                                // 保持当前状态不变，提示联系技术人员，并在详情中展示报错
                                errorTitle = context.getString(R.string.store_status_error_title)
                                errorUserMessage = context.getString(R.string.store_status_error_post_user_message)
                                errorDebugMessage = buildStoreStatusDebug(
                                    action = "POST store-status",
                                    siteUrl = siteUrl,
                                    isNetworkValidated = isNetworkValidated,
                                    isWifiConnected = isWifiConnected,
                                    isBluetoothEnabled = isBluetoothEnabled,
                                    isBluetoothConnected = isBluetoothConnected,
                                    throwable = t
                                )
                                showErrorDialog = true
                                pendingAction = null
                            } finally {
                                storeStatusBusy = false
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(enabled = !storeStatusBusy, onClick = { pendingAction = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // 错误详情对话框（点击 GET 失败 / POST 失败才弹）
    if (showErrorDialog) {
        ErrorDetailsDialog(
            title = errorTitle,
            userMessage = errorUserMessage,
            debugMessage = errorDebugMessage,
            showSettingsButton = false,
            showAckButton = true,
            ackButtonText = stringResource(R.string.ok),
            onAckClick = { showErrorDialog = false },
            onDismissRequest = {
                // 强制用户点击按钮确认
            }
        )
    }
}

private fun formatTime(millis: Long): String {
    val is24 = try { DateFormat.is24HourFormat(null) } catch (_: Throwable) { true }
    val pattern = if (is24) "HH:mm" else "h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

private enum class StoreStatusUi {
    OPEN_SCHEDULED,
    OPEN_ALWAYS,
    CLOSED;

    companion object {
        fun fromApi(raw: String?): StoreStatusUi {
            val v = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return when (v) {
                ExFoodStoreStatusApi.STATUS_DISABLE -> OPEN_ALWAYS
                ExFoodStoreStatusApi.STATUS_CLOSED -> CLOSED
                ExFoodStoreStatusApi.STATUS_ENABLE -> OPEN_SCHEDULED
                else -> OPEN_SCHEDULED
            }
        }
    }
}

private enum class StoreStatusAction {
    SWITCH_TO_CLOSED,
    SWITCH_TO_OPEN_SCHEDULED,
    SWITCH_TO_OPEN_ALWAYS
}

private fun normalizeExFoodStatus(raw: String?): String? {
    val v = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return v.takeIf { it.isNotBlank() }
}

private suspend fun syncPreferredOpenStatusIfOpen(
    settingsRepository: DomainSettingRepository,
    remoteRawStatus: String?
) {
    val v = normalizeExFoodStatus(remoteRawStatus) ?: return
    if (v != ExFoodStoreStatusApi.STATUS_ENABLE && v != ExFoodStoreStatusApi.STATUS_DISABLE) return
    try {
        settingsRepository.setPreferredOpenStatus(v)
    } catch (_: Throwable) {
        // Ignore local persistence failure; should not block UI.
    }
}

private suspend fun resolveOpenAction(
    settingsRepository: DomainSettingRepository,
    remoteRawStatus: String?
): StoreStatusAction {
    val mode = try {
        settingsRepository.getStoreOpenRestoreModeFlow().first()
    } catch (_: Throwable) {
        DomainSettingRepository.STORE_OPEN_RESTORE_MODE_AUTO
    }

    val normalizedMode = normalizeExFoodStatus(mode) ?: DomainSettingRepository.STORE_OPEN_RESTORE_MODE_AUTO

    val targetStatus: String = when (normalizedMode) {
        DomainSettingRepository.STORE_OPEN_RESTORE_MODE_ENABLE -> ExFoodStoreStatusApi.STATUS_ENABLE
        DomainSettingRepository.STORE_OPEN_RESTORE_MODE_DISABLE -> ExFoodStoreStatusApi.STATUS_DISABLE
        else -> {
            val preferred = try {
                settingsRepository.getPreferredOpenStatusFlow().first()
            } catch (_: Throwable) {
                null
            }
            val normalizedPreferred = normalizeExFoodStatus(preferred)
            when (normalizedPreferred) {
                ExFoodStoreStatusApi.STATUS_ENABLE,
                ExFoodStoreStatusApi.STATUS_DISABLE -> normalizedPreferred
                else -> {
                    val normalizedRemote = normalizeExFoodStatus(remoteRawStatus)
                    when (normalizedRemote) {
                        ExFoodStoreStatusApi.STATUS_ENABLE,
                        ExFoodStoreStatusApi.STATUS_DISABLE -> normalizedRemote
                        else -> ExFoodStoreStatusApi.STATUS_ENABLE // initial closed/empty -> enable
                    }
                }
            }
        }
    }

    return if (targetStatus == ExFoodStoreStatusApi.STATUS_DISABLE) {
        StoreStatusAction.SWITCH_TO_OPEN_ALWAYS
    } else {
        StoreStatusAction.SWITCH_TO_OPEN_SCHEDULED
    }
}

@Composable
private fun StoreStatusPill(
    text: String,
    busy: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            }
        }
    }
}

private fun buildStoreStatusDebug(
    action: String,
    siteUrl: String,
    isNetworkValidated: Boolean,
    isWifiConnected: Boolean,
    isBluetoothEnabled: Boolean,
    isBluetoothConnected: Boolean,
    throwable: Throwable
): String {
    val url = ExFoodStoreStatusApi.buildStoreStatusUrl(siteUrl)
    val httpCode = (throwable as? ApiError)?.code
    return buildString {
        appendLine("== Store Status Failure ==")
        appendLine("timeMs=${System.currentTimeMillis()}")
        appendLine("action=$action")
        appendLine("endpoint=$url")
        if (httpCode != null) appendLine("httpCode=$httpCode")
        appendLine("wifiTransport=$isWifiConnected")
        appendLine("networkValidated=$isNetworkValidated")
        appendLine("bluetoothEnabled=$isBluetoothEnabled")
        appendLine("bluetoothConnected=$isBluetoothConnected")
        appendLine("errorType=${throwable.javaClass.name}")
        appendLine("message=${throwable.message}")
        appendLine()
        appendLine("stacktrace=")
        appendLine(try { throwable.stackTraceToString() } catch (_: Throwable) { Log.getStackTraceString(throwable) })
    }
}


