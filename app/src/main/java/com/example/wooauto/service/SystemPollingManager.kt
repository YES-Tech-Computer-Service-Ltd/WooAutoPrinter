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
 * - Centralizes all non-order-related periodic checks (network heartbeat, watchdog, printer health checks)
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
		private const val WATCHDOG_INTERVAL_MS = 30_000L
		private const val PRINTER_HEALTH_INTERVAL_MS = 5_000L
		private const val MAX_NETWORK_RETRY_COUNT = 3
	}

	data class NetworkHeartbeatConfig(
		val isPollingActive: () -> Boolean,
		val latestPolledDateProvider: () -> Date?,
		val onNetworkRestored: suspend (Date?) -> Unit,
		val resetWifiLocks: suspend () -> Unit,
		val showNetworkIssueNotification: suspend () -> Unit,
	)

	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	private var networkHeartbeatJob: Job? = null
	private var watchdogJob: Job? = null
	private var printerHealthJob: Job? = null

	private var networkHeartbeatConfig: NetworkHeartbeatConfig? = null
	private val networkHeartbeatMutex = Mutex()
	private var networkRetryCount = 0
	private var lastNetworkCheckTime = 0L

	fun startAll(
		defaultPrinterProvider: suspend () -> PrinterConfig?,
		networkHeartbeatConfig: NetworkHeartbeatConfig,
	) {
		this.networkHeartbeatConfig = networkHeartbeatConfig
		networkRetryCount = 0
		startNetworkHeartbeat()
		startWatchdog()
		startPrinterHealth(defaultPrinterProvider)
	}

	fun stopAll() {
		networkHeartbeatJob?.cancel(); networkHeartbeatJob = null
		networkHeartbeatConfig = null
		networkRetryCount = 0
		lastNetworkCheckTime = 0L
		watchdogJob?.cancel(); watchdogJob = null
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

	private fun startWatchdog() {
		if (watchdogJob?.isActive == true) return
		UiLog.d(TAG, "系统轮询/看门狗：启动")
		watchdogJob = scope.launch {
			while (isActive) {
				try {
					UiLog.d(TAG, "系统轮询/看门狗：检查轮询健康")
					// 此处保留空实现：原看门狗逻辑仍在 BackgroundPollingService 中
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/看门狗：异常: ${e.message}", e)
				} finally {
					delay(WATCHDOG_INTERVAL_MS)
				}
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
