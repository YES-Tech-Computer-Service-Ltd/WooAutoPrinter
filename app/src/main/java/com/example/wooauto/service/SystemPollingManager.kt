package com.example.wooauto.service

import android.content.Context
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
	}

	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	private var networkHeartbeatJob: Job? = null
	private var watchdogJob: Job? = null
	private var printerHealthJob: Job? = null

	fun startAll(defaultPrinterProvider: suspend () -> PrinterConfig?) {
		startNetworkHeartbeat()
		startWatchdog()
		startPrinterHealth(defaultPrinterProvider)
	}

	fun stopAll() {
		networkHeartbeatJob?.cancel(); networkHeartbeatJob = null
		watchdogJob?.cancel(); watchdogJob = null
		printerHealthJob?.cancel(); printerHealthJob = null
	}

	private fun startNetworkHeartbeat() {
		if (networkHeartbeatJob?.isActive == true) return
		UiLog.d(TAG, "系统轮询/网络：启动网络心跳")
		networkHeartbeatJob = scope.launch {
			while (isActive) {
				try {
					UiLog.d(TAG, "系统轮询/网络：执行网络心跳")
					// 此处保留空实现：原网络心跳逻辑仍在 BackgroundPollingService 中
					// 迁移时由服务改为委托本管理器或直接在此实现
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/网络：心跳异常: ${e.message}", e)
				} finally {
					delay(NETWORK_HEARTBEAT_INTERVAL_MS)
				}
			}
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
