package com.example.wooauto.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.wooauto.BuildConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.utils.UiLog
import com.example.wooauto.utils.GlobalErrorManager
import com.example.wooauto.utils.ErrorSource
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
	private val globalErrorManager: GlobalErrorManager,
) {
	companion object {
		private const val TAG = "SystemPollingManager"
		private const val NETWORK_HEARTBEAT_INTERVAL_MS = 30_000L
		private const val WATCHDOG_INTERVAL_MS = 30_000L
		private const val POLLING_HEALTH_CHECK_INTERVAL_MS = 30_000L
		private const val PRINTER_HEALTH_INTERVAL_MS = 5_000L
	}

	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	private var networkHeartbeatJob: Job? = null
	private var watchdogJob: Job? = null
	private var pollingHealthMonitorJob: Job? = null
	private var printerHealthJob: Job? = null
	private var bluetoothStateReceiver: BroadcastReceiver? = null

	// PollingHealthMonitor 配置与状态
	data class PollingHealthMonitorConfig(
		val isPollingActive: () -> Boolean,
		val lastPollingActivityProvider: () -> Long,
		val timeoutThresholdMs: Long,
		val onPollingTimeout: suspend (staleDurationMs: Long) -> Unit,
		val onPollingRecovered: suspend (staleDurationMs: Long) -> Unit,
	)

	private var pollingHealthMonitorConfig: PollingHealthMonitorConfig? = null
	private val pollingHealthMonitorMutex = Mutex()
	private var lastPollingHealthAlertAt = 0L
	private var isPollingCurrentlyStuck = false

	fun startAll(defaultPrinterProvider: suspend () -> PrinterConfig?) {
		startNetworkHeartbeat()
		startWatchdog()
		startPrinterHealth(defaultPrinterProvider)
		startBluetoothMonitor()
	}

	fun stopAll() {
		networkHeartbeatJob?.cancel(); networkHeartbeatJob = null
		watchdogJob?.cancel(); watchdogJob = null
		pollingHealthMonitorJob?.cancel(); pollingHealthMonitorJob = null
		printerHealthJob?.cancel(); printerHealthJob = null
		stopBluetoothMonitor()
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
		UiLog.d(TAG, "系统轮询/看门狗：跳过启动（请使用 startPollingHealthMonitor）")
	}

	// 对外：启动/停止 轮询健康监测（PollingHealthMonitor）
	fun startPollingHealthMonitor(config: PollingHealthMonitorConfig) {
		pollingHealthMonitorConfig = config
		if (pollingHealthMonitorJob?.isActive == true) return
		UiLog.d(TAG, "系统轮询/轮询健康监测：启动")
		pollingHealthMonitorJob = scope.launch {
			while (isActive) {
				try {
					performPollingHealthCheck()
				} catch (e: Exception) {
					Log.e(TAG, "系统轮询/轮询健康监测：检查异常: ${e.message}", e)
				} finally {
					delay(POLLING_HEALTH_CHECK_INTERVAL_MS)
				}
			}
		}
	}

	fun stopPollingHealthMonitor() {
		pollingHealthMonitorJob?.cancel(); pollingHealthMonitorJob = null
		pollingHealthMonitorConfig = null
		lastPollingHealthAlertAt = 0L
		isPollingCurrentlyStuck = false
	}

	private suspend fun performPollingHealthCheck() {
		val cfg = pollingHealthMonitorConfig ?: return
		pollingHealthMonitorMutex.withLock {
			if (!cfg.isPollingActive()) {
				if (isPollingCurrentlyStuck) {
					try { cfg.onPollingRecovered(0) } catch (e: Exception) {
						Log.e(TAG, "系统轮询/轮询健康监测：未激活时恢复回调异常: ${e.message}", e)
					}
				}
				isPollingCurrentlyStuck = false
				return
			}

			val now = System.currentTimeMillis()
			val lastActivityRaw = try { cfg.lastPollingActivityProvider() } catch (e: Exception) {
				Log.e(TAG, "系统轮询/轮询健康监测：获取轮询活动时间失败: ${e.message}", e)
				now
			}
			val lastActivity = if (lastActivityRaw <= 0) now else lastActivityRaw
			var idleDuration = now - lastActivity
			if (idleDuration < 0) idleDuration = 0
			val timeoutThreshold = cfg.timeoutThresholdMs.coerceAtLeast(POLLING_HEALTH_CHECK_INTERVAL_MS)

			if (idleDuration >= timeoutThreshold) {
				if (!isPollingCurrentlyStuck || now - lastPollingHealthAlertAt >= 120_000L) {
					isPollingCurrentlyStuck = true
					lastPollingHealthAlertAt = now
					Log.w(TAG, "系统轮询/轮询健康监测：检测到轮询任务 ${idleDuration}ms 未活跃，触发恢复")
					try { cfg.onPollingTimeout(idleDuration) } catch (e: Exception) {
						Log.e(TAG, "系统轮询/轮询健康监测：超时回调异常: ${e.message}", e)
					}
				} else if (BuildConfig.DEBUG) {
					Log.d(TAG, "系统轮询/轮询健康监测：仍处于超时状态，已于 ${lastPollingHealthAlertAt} 通知")
				}
				return@withLock
			}

			if (isPollingCurrentlyStuck) {
				isPollingCurrentlyStuck = false
				Log.i(TAG, "系统轮询/轮询健康监测：轮询恢复正常，最近活动 ${idleDuration}ms 前")
				try { cfg.onPollingRecovered(idleDuration) } catch (e: Exception) {
					Log.e(TAG, "系统轮询/轮询健康监测：恢复回调异常: ${e.message}", e)
				}
				return@withLock
			}

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "系统轮询/轮询健康监测：正常，最近活动 ${idleDuration}ms 前")
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

	private fun startBluetoothMonitor() {
		if (bluetoothStateReceiver != null) return
		val initialEnabled = try {
			BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
		} catch (_: Exception) { false }
		handleBluetoothState(initialEnabled)

		val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
		val receiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
				val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
				when (state) {
					BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON -> handleBluetoothState(true)
					BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> handleBluetoothState(false)
				}
			}
		}

		try {
			context.registerReceiver(receiver, filter)
			bluetoothStateReceiver = receiver
			UiLog.d(TAG, "系统轮询/蓝牙：已启动蓝牙状态监听")
		} catch (e: Exception) {
			Log.e(TAG, "系统轮询/蓝牙：注册状态监听失败: ${e.message}", e)
		}
	}

	private fun stopBluetoothMonitor() {
		try {
			bluetoothStateReceiver?.let { context.unregisterReceiver(it) }
		} catch (e: Exception) {
			Log.w(TAG, "系统轮询/蓝牙：注销状态监听失败: ${e.message}")
		} finally {
			bluetoothStateReceiver = null
		}
	}

	private fun handleBluetoothState(enabled: Boolean) {
		if (enabled) {
			globalErrorManager.resolveError(ErrorSource.BLUETOOTH)
		} else {
			globalErrorManager.reportError(
				source = ErrorSource.BLUETOOTH,
				title = "蓝牙已关闭",
				message = "检测到系统蓝牙已关闭，打印功能将不可用。请开启蓝牙。",
				debugInfo = "SystemPollingManager detected Bluetooth adapter OFF.",
				onSettingsAction = {
					try {
						val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
						intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
						context.startActivity(intent)
					} catch (e: Exception) {
						Log.e(TAG, "系统轮询/蓝牙：无法打开蓝牙设置: ${e.message}")
					}
				}
			)
		}
	}
}
