package com.example.wooauto.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.example.wooauto.BuildConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.utils.UiLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System Polling Manager
 * - Centralizes all non-order-related periodic checks (network heartbeat, polling health monitor, printer health checks)
 * - Does not directly modify business state; updates status only via domain components such as PrinterManager
 */
@Singleton
class SystemPollingManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val printerManager: PrinterManager,
) {
	companion object {
		private const val TAG = "SystemPollingManager"
		private const val NETWORK_HEARTBEAT_INTERVAL_MS = 30_000L
		private const val POLLING_HEALTH_CHECK_INTERVAL_MS = 30_000L
		private const val PRINTER_HEALTH_INTERVAL_MS = 5_000L
		private const val MAX_NETWORK_RETRY_COUNT = 3
		private const val POLLING_HEALTH_ALERT_COOLDOWN_MS = 2 * 60_000L
	}

	data class NetworkHeartbeatConfig(
		val isPollingActive: () -> Boolean,
		val latestPolledDateProvider: () -> Date?,
		val onNetworkRestored: suspend (Date?) -> Unit,
		val resetWifiLocks: suspend () -> Unit,
		val showNetworkIssueNotification: suspend () -> Unit,
	)

	data class PollingHealthMonitorConfig(
		val isPollingActive: () -> Boolean,
		val lastPollingActivityProvider: () -> Long,
		val timeoutThresholdMs: Long,
		val onPollingTimeout: suspend (staleDurationMs: Long) -> Unit,
		val onPollingRecovered: suspend (staleDurationMs: Long) -> Unit,
	)

	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	private var networkHeartbeatJob: Job? = null
	private var pollingHealthMonitorJob: Job? = null
	private var printerHealthJob: Job? = null

	private var networkHeartbeatConfig: NetworkHeartbeatConfig? = null
	private val networkHeartbeatMutex = Mutex()
	private var networkRetryCount = 0
	private var lastNetworkCheckTime = 0L
	private var pollingHealthMonitorConfig: PollingHealthMonitorConfig? = null
	private val pollingHealthMonitorMutex = Mutex()
	private var lastPollingHealthAlertAt = 0L
	private var isPollingCurrentlyStuck = false

	fun startAll(
		defaultPrinterProvider: suspend () -> PrinterConfig?,
		networkHeartbeatConfig: NetworkHeartbeatConfig,
		pollingHealthMonitorConfig: PollingHealthMonitorConfig,
	) {
		this.networkHeartbeatConfig = networkHeartbeatConfig
		this.pollingHealthMonitorConfig = pollingHealthMonitorConfig
		networkRetryCount = 0
		startNetworkHeartbeat()
		startPollingHealthMonitor()
		startPrinterHealth(defaultPrinterProvider)
	}

	fun stopAll() {
		networkHeartbeatJob?.cancel(); networkHeartbeatJob = null
		networkHeartbeatConfig = null
		networkRetryCount = 0
		lastNetworkCheckTime = 0L
		pollingHealthMonitorJob?.cancel(); pollingHealthMonitorJob = null
		pollingHealthMonitorConfig = null
		lastPollingHealthAlertAt = 0L
		isPollingCurrentlyStuck = false
		printerHealthJob?.cancel(); printerHealthJob = null
	}

	private fun startNetworkHeartbeat() {
		if (networkHeartbeatJob?.isActive == true) return
		if (networkHeartbeatConfig == null) {
			Log.w(TAG, "系统轮询/网络：未提供网络心跳配置，跳过启动")
			return
		}
		UiLog.d(TAG, "系统轮询/网络：启动网络心跳")
		networkHeartbeatJob = scope.launch {
			while (isActive) {
				try {
					val config = networkHeartbeatConfig
					if (config != null) {
						performNetworkHeartbeatCycle(config, reason = "PERIODIC")
					} else if (BuildConfig.DEBUG) {
						Log.d(TAG, "系统轮询/网络：配置缺失，跳过本次心跳")
					}
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/网络：心跳异常: ${e.message}", e)
				} finally {
					delay(NETWORK_HEARTBEAT_INTERVAL_MS)
				}
			}
		}
	}

	fun requestImmediateNetworkCheck(reason: String? = null) {
		val config = networkHeartbeatConfig ?: return
		scope.launch {
			try {
				performNetworkHeartbeatCycle(config, reason = reason ?: "MANUAL")
			} catch (e: Exception) {
				Log.e(TAG, "系统轮询/网络：立即心跳检查异常: ${e.message}", e)
			}
		}
	}

	fun checkNetworkConnectivity(): Boolean = checkNetworkConnectivityInternal()

	private suspend fun performNetworkHeartbeatCycle(
		config: NetworkHeartbeatConfig,
		reason: String,
	) {
		networkHeartbeatMutex.withLock {
			if (!config.isPollingActive()) {
				if (BuildConfig.DEBUG) {
					Log.d(TAG, "系统轮询/网络：轮询未激活，跳过心跳 (触发: $reason)")
				}
				return
			}

			lastNetworkCheckTime = System.currentTimeMillis()
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "系统轮询/网络：执行网络心跳 (触发: $reason)")
			}

			val isConnected = checkNetworkConnectivityInternal()
			if (!isConnected) {
				Log.w(TAG, "系统轮询/网络：检测到网络断开 (触发: $reason)，尝试恢复")
				handleNetworkDisconnection(config)
			} else {
				if (BuildConfig.DEBUG) {
					Log.d(TAG, "系统轮询/网络：网络连接正常")
				}
				networkRetryCount = 0
				val canReachInternet = testInternetConnectivity()
				if (!canReachInternet) {
					Log.w(TAG, "系统轮询/网络：虽然WiFi已连接，但无法访问互联网")
				}
			}
		}
	}

	private suspend fun handleNetworkDisconnection(config: NetworkHeartbeatConfig) {
		networkRetryCount++
		Log.w(TAG, "系统轮询/网络：处理网络断开，重试次数: $networkRetryCount/$MAX_NETWORK_RETRY_COUNT")

		if (networkRetryCount <= MAX_NETWORK_RETRY_COUNT) {
			try {
				config.resetWifiLocks()
			} catch (e: Exception) {
				Log.e(TAG, "系统轮询/网络：重置网络保持锁失败: ${e.message}", e)
			}

			val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
			val wifiState = wifiManager?.wifiState
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "系统轮询/网络：当前WiFi状态: $wifiState")
			}

			if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
				Log.w(TAG, "系统轮询/网络：WiFi已禁用，尝试启用")
				try {
					@Suppress("DEPRECATION")
					wifiManager?.isWifiEnabled = true
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/网络：无法启用WiFi (需要用户权限?): ${e.message}")
				}
			}

			delay(5_000)

			val isReconnected = checkNetworkConnectivityInternal()
			if (isReconnected) {
				Log.i(TAG, "系统轮询/网络：网络连接已恢复")
				networkRetryCount = 0

				if (config.isPollingActive()) {
					delay(1_000)
					try {
						config.onNetworkRestored(config.latestPolledDateProvider())
					} catch (e: Exception) {
						Log.e(TAG, "系统轮询/网络：网络恢复后回调失败: ${e.message}", e)
					}
				} else if (BuildConfig.DEBUG) {
					Log.d(TAG, "系统轮询/网络：网络恢复，但轮询未激活，跳过回调")
				}
			} else {
				Log.w(TAG, "系统轮询/网络：网络连接仍未恢复，将继续监控")
			}
		} else {
			Log.e(TAG, "系统轮询/网络：网络重连失败，已达到最大重试次数")
			try {
				config.showNetworkIssueNotification()
			} catch (e: Exception) {
				Log.e(TAG, "系统轮询/网络：显示网络问题通知失败: ${e.message}", e)
			}
		}
	}

	private fun checkNetworkConnectivityInternal(): Boolean {
		return try {
			val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			val network = connectivityManager.activeNetwork ?: return false
			val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
			capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
				capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
				capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
		} catch (e: Exception) {
			Log.e(TAG, "系统轮询/网络：检查网络连接状态失败: ${e.message}", e)
			false
		}
	}

	private suspend fun testInternetConnectivity(): Boolean {
		return try {
			withTimeoutOrNull(5_000) {
				val url = java.net.URL("https://www.google.com")
				val connection = url.openConnection()
				connection.connectTimeout = 3_000
				connection.readTimeout = 3_000
				connection.connect()
				true
			} ?: false
		} catch (e: Exception) {
			Log.w(TAG, "系统轮询/网络：互联网连接测试失败: ${e.message}")
			false
		}
	}

	private fun startPollingHealthMonitor() {
		if (pollingHealthMonitorJob?.isActive == true) return
		if (pollingHealthMonitorConfig == null) {
			Log.w(TAG, "系统轮询/健康监测：未提供配置，跳过启动")
			return
		}
		UiLog.d(TAG, "系统轮询/健康监测：启动")
		pollingHealthMonitorJob = scope.launch {
			while (isActive) {
				try {
					val config = pollingHealthMonitorConfig
					if (config != null) {
						performPollingHealthCheck(config)
					} else {
						if (BuildConfig.DEBUG) {
							Log.d(TAG, "系统轮询/健康监测：配置缺失，跳过本次检查")
						}
					}
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/健康监测：检查异常: ${e.message}", e)
				} finally {
					delay(POLLING_HEALTH_CHECK_INTERVAL_MS)
				}
			}
		}
	}

	private suspend fun performPollingHealthCheck(config: PollingHealthMonitorConfig) {
		pollingHealthMonitorMutex.withLock {
			if (!config.isPollingActive()) {
				if (isPollingCurrentlyStuck) {
					try {
						config.onPollingRecovered(0)
					} catch (e: Exception) {
						Log.e(TAG, "系统轮询/健康监测：轮询未激活时恢复回调异常: ${e.message}", e)
					}
				}
				isPollingCurrentlyStuck = false
				return
			}

			val now = System.currentTimeMillis()
			val lastActivityRaw = try {
				config.lastPollingActivityProvider()
			} catch (e: Exception) {
				Log.e(TAG, "系统轮询/健康监测：获取轮询活动时间失败: ${e.message}", e)
				now
			}
			val lastActivity = if (lastActivityRaw <= 0) now else lastActivityRaw
			var idleDuration = now - lastActivity
			if (idleDuration < 0) idleDuration = 0
			val timeoutThreshold = config.timeoutThresholdMs.coerceAtLeast(POLLING_HEALTH_CHECK_INTERVAL_MS)

			if (idleDuration >= timeoutThreshold) {
				if (!isPollingCurrentlyStuck || now - lastPollingHealthAlertAt >= POLLING_HEALTH_ALERT_COOLDOWN_MS) {
					isPollingCurrentlyStuck = true
					lastPollingHealthAlertAt = now
					Log.w(TAG, "系统轮询/健康监测：检测到轮询任务 ${idleDuration}ms 未活跃，触发恢复流程")
					try {
						config.onPollingTimeout(idleDuration)
					} catch (e: Exception) {
						Log.e(TAG, "系统轮询/健康监测：处理轮询超时回调失败: ${e.message}", e)
					}
				} else if (BuildConfig.DEBUG) {
					Log.d(TAG, "系统轮询/健康监测：轮询仍处于超时状态，已于 ${lastPollingHealthAlertAt} 通知")
				}
				return@withLock
			}

			if (isPollingCurrentlyStuck) {
				isPollingCurrentlyStuck = false
				Log.i(TAG, "系统轮询/健康监测：轮询恢复正常，最近活动 ${idleDuration}ms 前")
				try {
					config.onPollingRecovered(idleDuration)
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/健康监测：恢复回调执行异常: ${e.message}", e)
				}
				return@withLock
			}

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "系统轮询/健康监测：轮询正常，最近活动 ${idleDuration}ms 前")
			}
		}
	}

	private fun startPrinterHealth(defaultPrinterProvider: suspend () -> PrinterConfig?) {
		if (printerHealthJob?.isActive == true) return
		UiLog.d(TAG, "系统轮询/打印机：启动")
		printerHealthJob = scope.launch {
			while (isActive) {
				try {
					val config = try { defaultPrinterProvider() } catch (_: Exception) { null }
					if (config != null) {
						// 仅触发检查与恢复，具体状态更新在 PrinterManager 内部完成
						try {
							val status = printerManager.getPrinterStatus(config)
							if (status != PrinterStatus.CONNECTED) {
								printerManager.connect(config)
							} else {
								// 轻量测试，失败时由 manager 标记并自行重连
								printerManager.testConnection(config)
							}
						} catch (e: Exception) {
							Log.e(TAG, "系统轮询/打印机：检查异常: ${e.message}", e)
						}
					}
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/打印机：循环异常: ${e.message}", e)
				} finally {
					delay(PRINTER_HEALTH_INTERVAL_MS)
				}
			}
		}
	}
}
