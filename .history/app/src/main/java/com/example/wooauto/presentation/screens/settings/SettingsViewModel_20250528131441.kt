package com.example.wooauto.presentation.screens.settings

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.R // Import R for string resources
import com.example.wooauto.data.printer.BluetoothPrinterManager
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.utils.LocaleHelper
import com.example.wooauto.utils.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.Activity
import android.content.Intent
import com.example.wooauto.domain.templates.TemplateType
import com.example.wooauto.service.BackgroundPollingService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.wooauto.updater.UpdaterInterface
import com.example.wooauto.updater.model.UpdateInfo
import com.example.wooauto.licensing.LicenseManager
import com.example.wooauto.licensing.LicenseInfo
import com.example.wooauto.licensing.LicenseStatus
import com.example.wooauto.licensing.TrialTokenManager
import com.example.wooauto.licensing.LicenseDataStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.withTimeoutOrNull
import com.example.wooauto.licensing.EligibilityInfo
import com.example.wooauto.licensing.EligibilityStatus
import com.example.wooauto.licensing.EligibilitySource

/**
 * 定义模板条目的数据类
 */
data class TemplateItem(val id: String, val name: String)

/**
 * 系统设置ViewModel
 * 用于管理设置界面的状态和操作
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsRepository: DomainSettingRepository,
    private val productRepository: DomainProductRepository,
    private val orderRepository: DomainOrderRepository,
    private val printerManager: PrinterManager,
    private val updater: UpdaterInterface,
    val licenseManager: LicenseManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    // API 配置相关状态
    private val _siteUrl = MutableStateFlow("")
    val siteUrl: StateFlow<String> = _siteUrl.asStateFlow()

    private val _consumerKey = MutableStateFlow("")
    val consumerKey: StateFlow<String> = _consumerKey.asStateFlow()

    private val _consumerSecret = MutableStateFlow("")
    val consumerSecret: StateFlow<String> = _consumerSecret.asStateFlow()

    private val _pollingInterval = MutableStateFlow(30) // 默认30秒
    val pollingInterval: StateFlow<Int> = _pollingInterval.asStateFlow()

    private val _useWooCommerceFood = MutableStateFlow(false)
    val useWooCommerceFood: StateFlow<Boolean> = _useWooCommerceFood.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    // 打印机相关状态
    private val _printerConfigs = MutableStateFlow<List<PrinterConfig>>(emptyList())
    val printerConfigs: StateFlow<List<PrinterConfig>> = _printerConfigs.asStateFlow()
    
    private val _currentPrinterConfig = MutableStateFlow<PrinterConfig?>(null)
    val currentPrinterConfig: StateFlow<PrinterConfig?> = _currentPrinterConfig.asStateFlow()
    
    private val _printerStatus = MutableStateFlow<PrinterStatus>(PrinterStatus.DISCONNECTED)
    val printerStatus: StateFlow<PrinterStatus> = _printerStatus.asStateFlow()
    
    private val _availablePrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val availablePrinters: StateFlow<List<PrinterDevice>> = _availablePrinters.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()
    
    private val _isPrinting = MutableStateFlow(false)
    val isPrinting: StateFlow<Boolean> = _isPrinting.asStateFlow()

    // 语言设置相关状态
    private val _currentLocale = MutableStateFlow(LocaleHelper.getSelectedLocale(context))
    val currentLocale: StateFlow<Locale> = _currentLocale.asStateFlow()
    
    // 商店信息相关状态
    private val _storeName = MutableStateFlow("")
    val storeName: StateFlow<String> = _storeName.asStateFlow()
    
    private val _storeAddress = MutableStateFlow("")
    val storeAddress: StateFlow<String> = _storeAddress.asStateFlow()
    
    private val _storePhone = MutableStateFlow("")
    val storePhone: StateFlow<String> = _storePhone.asStateFlow()
    
    private val _currencySymbol = MutableStateFlow("C$")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    // 添加连接错误信息状态
    private val _connectionErrorMessage = MutableStateFlow<String?>(null)
    val connectionErrorMessage: StateFlow<String?> = _connectionErrorMessage.asStateFlow()

    // 自动化任务状态 (只保留自动打印相关的)
    // private val _automaticOrderProcessing = MutableStateFlow(false)
    // val automaticOrderProcessing: StateFlow<Boolean> = _automaticOrderProcessing.asStateFlow()
    
    private val _automaticPrinting = MutableStateFlow(false)
    val automaticPrinting: StateFlow<Boolean> = _automaticPrinting.asStateFlow()
    
    // private val _inventoryAlerts = MutableStateFlow(false)
    // val inventoryAlerts: StateFlow<Boolean> = _inventoryAlerts.asStateFlow()
    // 
    // private val _dailyBackup = MutableStateFlow(false)
    // val dailyBackup: StateFlow<Boolean> = _dailyBackup.asStateFlow()
    
    private val _defaultTemplateType = MutableStateFlow(TemplateType.FULL_DETAILS)
    val defaultTemplateType: StateFlow<TemplateType> = _defaultTemplateType.asStateFlow()

    // 新增：模板列表状态
    private val _templates = MutableStateFlow<List<TemplateItem>>(emptyList())
    val templates: StateFlow<List<TemplateItem>> = _templates.asStateFlow()

    // 添加状态消息流
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // 添加版本相关状态
    private val _currentAppVersion = MutableStateFlow(updater.getCurrentVersion().toFullVersionString())
    val currentAppVersion: StateFlow<String> = _currentAppVersion.asStateFlow()
    
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()
    
    private val _hasUpdate = MutableStateFlow(false)
    val hasUpdate: StateFlow<Boolean> = _hasUpdate.asStateFlow()

    // 添加下载相关状态
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    // 添加声音设置相关状态
    private val _soundVolume = MutableStateFlow(70)
    val soundVolume: StateFlow<Int> = _soundVolume.asStateFlow()
    
    private val _soundType = MutableStateFlow("")
    val soundType: StateFlow<String> = _soundType.asStateFlow()
    
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    // 许可证相关状态
    private val _licenseStatusText = MutableStateFlow("")
    val licenseStatusText: StateFlow<String> = _licenseStatusText.asStateFlow()

    private val _trialDaysRemaining = MutableStateFlow<Int?>(null)
    val trialDaysRemaining: StateFlow<Int?> = _trialDaysRemaining.asStateFlow()

    init {
        // 加载设置数据
        loadSettings()
        loadPrinterConfigs()
        loadAutomationSettings()
        loadSoundSettings()
        loadLicenseInfo()
        
        // 监听资格状态变化
        licenseManager.eligibilityInfo.observeForever { eligibilityInfo ->
            eligibilityInfo?.let {
                updateLicenseTextBasedOnEligibility(it)
            }
        }
    }

    private fun updatePrinterDeviceList(printers: List<PrinterDevice>) {
        _availablePrinters.value = printers
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val config = settingsRepository.getWooCommerceConfig()
                _siteUrl.value = config.siteUrl ?: ""
                _consumerKey.value = config.consumerKey ?: ""
                _consumerSecret.value = config.consumerSecret ?: ""
                _pollingInterval.value = config.pollingInterval ?: 30
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载设置失败", e)
            }
        }
    }

    // 监听轮询间隔变化，当发生变化时通知服务重启轮询
    private fun observePollingIntervalChanges() {
        pollingInterval.observeForever { interval ->
            if (isFirstLoad) {
                isFirstLoad = false
                return@observeForever
            }
            
            val oldInterval = previousPollingInterval
            if (oldInterval != null && oldInterval != interval) {
                // 轮询间隔发生了变化，通知服务重启
                notifyServiceToRestartPolling()
            }
            previousPollingInterval = interval
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            try {
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value
                )
                settingsRepository.saveWooCommerceConfig(config)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存设置失败", e)
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = ConnectionTestResult.Testing
            
            try {
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value
                )
                
                if (config.siteUrl.isNullOrBlank() || config.consumerKey.isNullOrBlank() || config.consumerSecret.isNullOrBlank()) {
                    Log.e("SettingsViewModel", "配置无效: siteUrl=${config.siteUrl}, consumerKey长度=${config.consumerKey?.length}, consumerSecret长度=${config.consumerSecret?.length}")
                    _connectionTestResult.value = ConnectionTestResult.Error("Please fill in all fields")
                    return@launch
                }
                
                val wooCommerceApi = WooCommerceApi(config)
                
                try {
                    val orders = wooCommerceApi.getOrders(limit = 1)
                    _connectionTestResult.value = ConnectionTestResult.Success("Connection successful! Found ${orders.size} orders.")
                } catch (e: Exception) {
                    _connectionTestResult.value = ConnectionTestResult.Error("Connection failed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "测试连接时出错", e)
                _connectionTestResult.value = ConnectionTestResult.Error("Error: ${e.message}")
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun updateSiteUrl(url: String) {
        val cleanUrl = url.trim()
        _siteUrl.value = cleanUrl
        
        // 检查 Consumer Key 和 Consumer Secret 是否包含了 URL
        _consumerKey.value?.let { key ->
            if (key.contains("http")) {
                Log.e("SettingsViewModel", "Consumer Key不应该包含URL: ${_consumerKey.value}")
            }
        }
        _consumerSecret.value?.let { secret ->
            if (secret.contains("http")) {
                Log.e("SettingsViewModel", "Consumer Secret不应该包含URL: ${_consumerSecret.value}")
            }
        }
    }

    fun saveSettingsAndClose(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value
                )
                settingsRepository.saveWooCommerceConfig(config)
                
                // 确保设置已保存后再关闭
                onComplete()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存设置失败", e)
                // 即使保存失败也要关闭，避免用户卡住
                onComplete()
            }
        }
    }

    fun switchLanguage(locale: Locale) {
        viewModelScope.launch {
            try {
                LocaleManager.setAndSaveLocale(context, locale)
                
                // 更新本地状态以反映UI变化
                _currentLocale.value = locale
                
                // 立即更新应用的语言配置
                LocaleManager.forceRefreshUI()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "语言切换失败", e)
            }
        }
    }

    private fun loadPrinterConfigs() {
        viewModelScope.launch {
            try {
                _printerConfigs.value = settingsRepository.getAllPrinterConfigs()
                Log.d("SettingsViewModel", "加载了${_printerConfigs.value.size}个打印机配置")
                
                // 加载默认打印机
                val defaultPrinter = settingsRepository.getDefaultPrinterConfig()
                if (defaultPrinter != null) {
                    _currentPrinterConfig.value = defaultPrinter
                    // 获取打印机状态
                    _printerStatus.value = printerManager.getPrinterStatus(defaultPrinter)
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载打印机配置失败", e)
            }
        }
    }

    private fun startBluetoothDeviceScanning() {
        viewModelScope.launch {
            try {
                printerManager.scanPrinters(PrinterConfig.PRINTER_TYPE_BLUETOOTH)
                    .onStart { _isScanning.value = true }
                    .onCompletion { _isScanning.value = false }
                    .collect { updatedDevices ->
                        updatePrinterDeviceList(updatedDevices)
                    }
            } catch (e: Exception) {
                _isScanning.value = false
                Log.e(TAG, "扫描蓝牙设备出错: ${e.message}")
            }
        }
    }

    // 商店信息相关方法
    fun loadStoreInfo() {
        viewModelScope.launch {
            try {
                _storeName.value = settingsRepository.getStoreNameFlow().first()
                _storeAddress.value = settingsRepository.getStoreAddressFlow().first()
                _storePhone.value = settingsRepository.getStorePhoneFlow().first()
                _currencySymbol.value = settingsRepository.getCurrencySymbolFlow().first()
                
                Log.d("SettingsViewModel", "加载商店信息成功")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载商店信息失败", e)
            }
        }
    }
    
    fun updateStoreName(name: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setStoreName(name)
                _storeName.value = name
                Log.d("SettingsViewModel", "更新商店名称: $name")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "更新商店名称失败", e)
            }
        }
    }
    
    fun updateStoreAddress(address: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setStoreAddress(address)
                _storeAddress.value = address
                Log.d("SettingsViewModel", "更新商店地址: $address")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "更新商店地址失败", e)
            }
        }
    }
    
    fun updateStorePhone(phone: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setStorePhone(phone)
                _storePhone.value = phone
                Log.d("SettingsViewModel", "更新商店电话: $phone")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "更新商店电话失败", e)
            }
        }
    }
    
    fun updateCurrencySymbol(symbol: String) {
        viewModelScope.launch {
            try {
                settingsRepository.setCurrencySymbol(symbol)
                _currencySymbol.value = symbol
                Log.d("SettingsViewModel", "更新货币符号: $symbol")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "更新货币符号失败", e)
            }
        }
    }

    // 连接测试结果密封类
    sealed class ConnectionTestResult {
        object Success : ConnectionTestResult()
        data class Error(val message: String) : ConnectionTestResult()
        object Testing : ConnectionTestResult()
    }

    /**
     * 停止蓝牙扫描
     */
    fun stopScanning() {
        _isScanning.value = false
        Log.d("SettingsViewModel", "手动停止蓝牙扫描")
    }

    /**
     * 获取打印机管理器实例
     */
    fun getPrinterManager(): PrinterManager {
        return printerManager
    }

    /**
     * 加载自动化任务设置
     */
    private fun loadAutomationSettings() {
        viewModelScope.launch {
            try {
                // _automaticOrderProcessing.value = settingsRepository.getAutomaticOrderProcessing()
                _automaticPrinting.value = settingsRepository.getAutoPrintEnabled()
                _defaultTemplateType.value = settingsRepository.getDefaultPrintTemplate()
                // _inventoryAlerts.value = settingsRepository.getInventoryAlerts()
                // _dailyBackup.value = settingsRepository.getDailyBackup()
                Log.d(TAG, "成功加载自动化设置: autoPrint=${_automaticPrinting.value}, defaultTemplate=${_defaultTemplateType.value}")
            } catch (e: Exception) {
                Log.e(TAG, "加载自动化设置失败", e)
            }
        }
    }
    
    /**
     * 保存自动化任务设置
     */
    fun saveAutomationSettings() {
        viewModelScope.launch {
            try {
                settingsRepository.setAutoPrintEnabled(_automaticPrinting.value)
                settingsRepository.setDefaultPrintTemplate(_defaultTemplateType.value)
                // settingsRepository.saveAutomaticOrderProcessing(_automaticOrderProcessing.value)
                // settingsRepository.saveInventoryAlerts(_inventoryAlerts.value)
                // settingsRepository.saveDailyBackup(_dailyBackup.value)
                Log.d(TAG, "成功保存自动化设置: autoPrint=${_automaticPrinting.value}, defaultTemplate=${_defaultTemplateType.value}")
            } catch (e: Exception) {
                Log.e(TAG, "保存自动化设置失败", e)
            }
        }
    }
    
    /**
     * 更新自动打印设置
     */
    fun updateAutomaticPrinting(enabled: Boolean) {
        _automaticPrinting.value = enabled
        saveAutomationSettings()
    }

    /**
     * 更新默认打印模板
     */
    fun updateDefaultTemplateType(templateType: TemplateType) {
        _defaultTemplateType.value = templateType
        saveAutomationSettings() // 模板更改也保存整个自动化设置
    }
    
    /**
     * 更新默认自动打印模板ID（支持自定义模板）
     * @param templateId 模板ID
     * @param templateType 模板类型（为了向后兼容）
     */
    fun updateDefaultAutoPrintTemplate(templateId: String, templateType: TemplateType) {
        _defaultTemplateType.value = templateType
        viewModelScope.launch {
            try {
                // 保存模板ID（新方式）
                settingsRepository.saveDefaultAutoPrintTemplateId(templateId)
                // 保存模板类型（向后兼容）
                settingsRepository.saveDefaultTemplateType(templateType)
                Log.d(TAG, "保存默认自动打印模板: ID=$templateId, Type=$templateType")
            } catch (e: Exception) {
                Log.e(TAG, "保存默认自动打印模板失败: ${e.message}")
            }
        }
    }

    /**
     * 通知服务重启轮询
     * 用于配置变更后刷新轮询任务
     */
    fun notifyServiceToRestartPolling() {
        try {
            val intent = Intent(context, BackgroundPollingService::class.java).apply {
                putExtra(BackgroundPollingService.EXTRA_RESTART_POLLING, true)
            }
            context.startService(intent)
            Log.d(TAG, "已发送重启轮询请求给服务")
        } catch (e: Exception) {
            Log.e(TAG, "通知服务重启轮询失败: ${e.message}", e)
        }
    }

    // 保存默认模板类型
    fun saveDefaultTemplateType(templateType: TemplateType) {
        viewModelScope.launch {
            try {
                settingsRepository.setDefaultPrintTemplate(templateType)
                Log.d(TAG, "保存默认模板类型: $templateType")
            } catch (e: Exception) {
                Log.e(TAG, "保存默认模板类型失败: ${e.message}")
            }
        }
    }
    
    // 获取默认模板类型
    fun getDefaultTemplateType(): TemplateType {
        return _defaultTemplateType.value
    }

    /**
     * 自动化设置数据类
     */
    data class AutomationSettings(
        val automaticOrderProcessing: Boolean = true,
        val automaticPrinting: Boolean = false,
        val inventoryAlerts: Boolean = true,
        val dailyBackup: Boolean = false
    )
    
    /**
     * 获取所有自动化设置
     * 从仓库加载并返回所有自动化设置
     */
    suspend fun getAutomationSettings(): AutomationSettings {
        return try {
            val automaticOrderProcessing = settingsRepository.getAutomaticOrderProcessingEnabled() ?: true
            val automaticPrinting = settingsRepository.getAutoPrintEnabled()
            
            // 这里可以添加其他设置的加载
            AutomationSettings(
                automaticOrderProcessing = automaticOrderProcessing,
                automaticPrinting = automaticPrinting
                // 其他设置也可以类似加载
            )
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "加载自动化设置失败", e)
            // 返回默认设置
            AutomationSettings()
        }
    }

    /**
     * 启动二维码扫描器
     */
    fun startQrCodeScanner() {
        // TODO: 实现启动二维码扫描器的逻辑
        // 这里可以通过ActivityResultLauncher启动ZXing扫描活动
        // 由于需要Activity上下文和结果处理，建议通过事件通知UI层处理
        // 发送事件到UI层
        _scanQrCodeEvent.tryEmit(Unit)
    }
    
    // 二维码扫描事件
    private val _scanQrCodeEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scanQrCodeEvent = _scanQrCodeEvent.asSharedFlow()
    
    /**
     * 处理二维码扫描结果
     */
    fun handleQrCodeResult(result: String) {
        try {
            Log.d(TAG, "处理二维码扫描结果: ${result.take(30)}...")
            
            // 检查是否是wooauto://开头的特定格式
            if (result.startsWith("wooauto://")) {
                // 提取JSON部分 (wooauto://之后的内容)
                val jsonStr = result.substring("wooauto://".length)
                
                // 使用Gson解析JSON
                val gson = com.google.gson.Gson()
                try {
                    val qrData = gson.fromJson(jsonStr, WooCommerceQrData::class.java)
                    
                    // 更新各字段值
                    _siteUrl.value = qrData.url ?: ""
                    _consumerKey.value = qrData.key ?: ""
                    _consumerSecret.value = qrData.secret ?: ""
                    
                    // 保存设置
                    saveSettings()
                    
                    Log.d(TAG, "二维码数据解析成功: URL=${qrData.url}, Key=${qrData.key?.take(10)}..., Secret=${qrData.secret?.take(10)}...")
                } catch (e: Exception) {
                    Log.e(TAG, "解析二维码JSON数据失败: ${e.message}")
                    // 如果JSON解析失败，则直接使用整个结果作为URL
                    _siteUrl.value = result
                }
            } else {
                // 不是特定格式，直接作为URL使用
                _siteUrl.value = result.trim()
                Log.d(TAG, "使用普通URL格式: ${result.take(30)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理二维码扫描结果出错", e)
        }
    }

    /**
     * 二维码数据类
     */
    data class WooCommerceQrData(
        val url: String? = null,
        val key: String? = null,
        val secret: String? = null
    )

    fun handleQrCodeScan() {
        viewModelScope.launch {
            _scanQrCodeEvent.emit(Unit)
        }
    }

    /**
     * 更新状态消息
     * 用于显示打印机操作的状态信息
     */
    private fun updateStatusMessage(message: String) {
        _connectionErrorMessage.value = message
        Log.d(TAG, "状态消息更新: $message")
        
        // 3秒后清除消息
        viewModelScope.launch {
            delay(3000)
            if (_connectionErrorMessage.value == message) {
                _connectionErrorMessage.value = null
            }
        }
    }

    /**
     * 执行打印机测试
     */
    fun testPrinter() {
        val config = _currentPrinterConfig.value
        if (config == null) {
            updateStatusMessage("当前没有选择的打印机")
            return
        }

        viewModelScope.launch {
            updateStatusMessage("开始测试打印...")
            val success = printerManager.printTest(config)
            updateStatusMessage(if (success) "打印测试成功" else "打印测试失败，请检查打印机连接")
        }
    }

    /**
     * 测试打印机切纸功能
     * 直接发送多种切纸命令，尝试解决切纸问题
     */
    fun testPrinterCut() {
        val config = _currentPrinterConfig.value
        if (config == null) {
            updateStatusMessage("当前没有选择的打印机")
            return
        }

        viewModelScope.launch {
            updateStatusMessage("开始测试切纸功能...")
            
            // 确保打印机启用了自动切纸
            val configWithCut = config.copy(autoCut = true)
            _currentPrinterConfig.value = configWithCut
            
            // 对于80mm打印机，执行特殊的切纸命令
            if (config.paperWidth >= 80) {
                updateStatusMessage("检测到80mm打印机，尝试特殊切纸命令...")
            }
            
            try {
                // 使用BluetoothPrinterManager中的testDirectCut方法
                val manager = printerManager as? BluetoothPrinterManager
                val result = manager?.testDirectCut(configWithCut) ?: false
                
                updateStatusMessage(if (result) "切纸测试命令已发送" else "切纸测试失败，请检查打印机连接")
            } catch (e: Exception) {
                Log.e(TAG, "测试切纸功能失败: ${e.message}", e)
                updateStatusMessage("切纸测试异常: ${e.message}")
            }
        }
    }

    /**
     * 检查应用更新
     */
    fun checkAppUpdate() {
        viewModelScope.launch {
            try {
                _isCheckingUpdate.value = true
                
                // 使用更新器检查更新
                updater.checkForUpdates().collect { updateInfo ->
                    _updateInfo.value = updateInfo
                    _hasUpdate.value = updateInfo.needsUpdate()
                    _isCheckingUpdate.value = false
                    
                    if (updateInfo.needsUpdate()) {
                        Log.d(TAG, "发现新版本: ${updateInfo.latestVersion.toVersionString()}, 当前版本: ${updateInfo.currentVersion.toVersionString()}")
                    } else {
                        Log.d(TAG, "当前已是最新版本: ${updateInfo.currentVersion.toVersionString()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新失败", e)
                _isCheckingUpdate.value = false
            }
        }
    }
    
    /**
     * 获取关于信息文本，包括版本和更新状态
     */
    fun getAboutInfoText(): String {
        // 如果正在检查更新，显示正在获取版本信息
        if (_isCheckingUpdate.value) {
            return context.getString(R.string.fetching_version_info)
        }
        
        val currentVersion = updater.getCurrentVersion().toVersionString()
        val updateInfo = _updateInfo.value
        
        // 如果updateInfo为空，也显示正在获取版本信息
        if (updateInfo == null) {
            return context.getString(R.string.fetching_version_info)
        }
        
        return if (updateInfo.needsUpdate()) {
            val latestVersion = updateInfo.latestVersion.toVersionString()
            context.getString(R.string.about_version_info, currentVersion,
                context.getString(R.string.version_needs_update, latestVersion))
        } else {
            context.getString(R.string.about_version_info, currentVersion,
                context.getString(R.string.version_is_latest))
        }
    }
    
    /**
     * 设置自动更新
     */
    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            try {
                // 设置更新器的自动检查
                updater.setAutoCheckEnabled(enabled)
                
                // 可以保存到设置
                settingsRepository.setAutoUpdate(enabled)
                
                Log.d(TAG, "自动更新设置为: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "设置自动更新失败", e)
            }
        }
    }

    /**
     * 下载更新
     */
    fun downloadUpdate() {
        if (_updateInfo.value == null || !_hasUpdate.value) {
            Log.d(TAG, "无更新可下载")
            return
        }
        
        viewModelScope.launch {
            try {
                _isDownloading.value = true
                _downloadProgress.value = 0
                
                updater.downloadAndInstall(_updateInfo.value!!).collect { progress ->
                    if (progress < 0) {
                        // 下载出错
                        _statusMessage.value = "下载更新失败，请重试"
                        _isDownloading.value = false
                    } else {
                        _downloadProgress.value = progress
                        if (progress >= 100) {
                            _isDownloading.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载更新出错", e)
                _statusMessage.value = "下载更新出错: ${e.message}"
                _isDownloading.value = false
            }
        }
    }

    // 新增：加载模板列表的方法
    private fun loadTemplates() {
        // 目前模板是固定的，基于TemplateType枚举
        // 将来可以从Repository或其他数据源加载
        _templates.value = TemplateType.values().map {
            TemplateItem(
                id = it.name, // 使用枚举名称作为ID
                name = getTemplateDisplayName(it) // 获取本地化的显示名称
            )
        }
    }

    // 新增：获取模板本地化显示名称的方法
    private fun getTemplateDisplayName(templateType: TemplateType): String {
        return when (templateType) {
            TemplateType.FULL_DETAILS -> context.getString(R.string.template_full_details)
            TemplateType.DELIVERY -> context.getString(R.string.template_delivery)
            TemplateType.KITCHEN -> context.getString(R.string.template_kitchen)
            // else -> templateType.name // 如果没有定义特定字符串，则返回枚举名作为备用
        }
    }

    fun setAutomaticPrinting(enabled: Boolean) {
        _automaticPrinting.value = enabled
        viewModelScope.launch {
            settingsRepository.setAutoPrintEnabled(enabled)
        }
    }
    
    fun setDefaultPrintTemplate(templateType: TemplateType) {
        _defaultTemplateType.value = templateType
        viewModelScope.launch {
            settingsRepository.setDefaultPrintTemplate(templateType)
        }
    }

    // 加载声音设置
    private fun loadSoundSettings() {
        viewModelScope.launch {
            try {
                val soundSettings = settingsRepository.getSoundSettings()
                _soundVolume.value = soundSettings.notificationVolume
                _soundType.value = soundSettings.soundType
                _soundEnabled.value = soundSettings.soundEnabled
                Log.d("SettingsViewModel", "加载声音设置成功: 音量=${soundSettings.notificationVolume}, 类型=${soundSettings.soundType}, 启用=${soundSettings.soundEnabled}")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载声音设置失败", e)
            }
        }
    }
    
    // 获取声音类型的显示名称
    fun getSoundTypeDisplayName(soundType: String): String {
        return context.getString(
            when (soundType) {
                "default" -> R.string.sound_type_default
                "system_alarm" -> R.string.sound_type_alarm
                "system_ringtone" -> R.string.sound_type_ringtone
                "system_event" -> R.string.sound_type_event
                "system_email" -> R.string.sound_type_email
                "custom" -> R.string.sound_type_custom
                else -> R.string.sound_type_default
            }
        )
    }
    
    /**
     * 刷新声音设置
     * 用于在声音设置对话框关闭后重新加载最新设置
     */
    fun refreshSoundSettings() {
        viewModelScope.launch {
            try {
                val soundSettings = settingsRepository.getSoundSettings()
                _soundVolume.value = soundSettings.notificationVolume
                _soundType.value = soundSettings.soundType
                _soundEnabled.value = soundSettings.soundEnabled
                Log.d(TAG, "刷新声音设置成功: 音量=${soundSettings.notificationVolume}, 类型=${soundSettings.soundType}, 启用=${soundSettings.soundEnabled}")
            } catch (e: Exception) {
                Log.e(TAG, "刷新声音设置失败", e)
            }
        }
    }

    private fun loadLicenseInfo() {
        viewModelScope.launch {
            try {
                // 监听许可证信息变化
                licenseManager.licenseInfo.observeForever { info ->
                    updateLicenseStatusText(info)
                }
                
                // 监听统一资格状态变化 - 新增
                licenseManager.eligibilityInfo.observeForever { eligibilityInfo ->
                    updateLicenseStatusTextFromEligibility(eligibilityInfo)
                }
                
                // 立即更新一次许可证状态文本
                updateLicenseStatusText(licenseManager.licenseInfo.value)
                
                Log.d(TAG, "许可证信息加载完成: 状态=${licenseManager.licenseInfo.value?.status}")
            } catch (e: Exception) {
                Log.e(TAG, "加载许可证信息失败", e)
            }
        }
    }
    
    /**
     * 根据许可证状态更新显示文本
     */
    private fun updateLicenseStatusText(info: LicenseInfo?) {
        viewModelScope.launch {
            try {
                if (info == null) {
                    _licenseStatusText.value = context.getString(R.string.license_status_unverified)
                    return@launch
                }
                
                // 如果是试用期状态，同时更新试用期剩余天数
                if (info.status == LicenseStatus.TRIAL) {
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                    val appId = context.packageName
                    _trialDaysRemaining.value = TrialTokenManager.getRemainingDays(context, deviceId, appId)
                }
                
                _licenseStatusText.value = when (info.status) {
                    LicenseStatus.VALID -> {
                        // 格式化有效期信息
                        val endDate = LicenseDataStore.calculateEndDate(info.activationDate, info.validity)
                        context.getString(R.string.license_status_valid, endDate)
                    }
                    LicenseStatus.TRIAL -> {
                        val days = _trialDaysRemaining.value ?: 0
                        context.getString(R.string.license_status_trial, days)
                    }
                    LicenseStatus.INVALID, LicenseStatus.TIMEOUT -> {
                        context.getString(R.string.license_status_expired)
                    }
                    else -> {
                        context.getString(R.string.license_status_unverified)
                    }
                }
                
                Log.d(TAG, "许可证状态文本已更新: ${_licenseStatusText.value}, 试用期剩余: ${_trialDaysRemaining.value}")
            } catch (e: Exception) {
                Log.e(TAG, "更新许可证状态文本失败", e)
                _licenseStatusText.value = context.getString(R.string.license_status_unverified)
            }
        }
    }
    
    /**
     * 基于统一资格状态更新显示文本 - 新增
     * 优先使用资格状态的displayMessage
     */
    private fun updateLicenseStatusTextFromEligibility(eligibilityInfo: EligibilityInfo?) {
        viewModelScope.launch {
            try {
                if (eligibilityInfo == null) {
                    _licenseStatusText.value = context.getString(R.string.license_status_unverified)
                    return@launch
                }
                
                // 如果资格基于试用期，更新试用期剩余天数
                if (eligibilityInfo.source == EligibilitySource.TRIAL) {
                    _trialDaysRemaining.value = eligibilityInfo.trialDaysRemaining
                }
                
                // 使用资格状态的显示消息，如果为空则使用默认逻辑
                _licenseStatusText.value = if (eligibilityInfo.displayMessage.isNotEmpty()) {
                    eligibilityInfo.displayMessage
                } else {
                    // 备用显示逻辑
                    when (eligibilityInfo.status) {
                        EligibilityStatus.ELIGIBLE -> {
                            if (eligibilityInfo.isLicensed) {
                                context.getString(R.string.license_status_valid, eligibilityInfo.licenseEndDate)
                            } else {
                                context.getString(R.string.license_status_trial, eligibilityInfo.trialDaysRemaining)
                            }
                        }
                        EligibilityStatus.INELIGIBLE -> {
                            context.getString(R.string.license_status_expired)
                        }
                        EligibilityStatus.CHECKING -> {
                            context.getString(R.string.license_status_verifying)
                        }
                        else -> {
                            context.getString(R.string.license_status_unverified)
                        }
                    }
                }
                
                Log.d(TAG, "基于资格状态更新许可证文本: ${_licenseStatusText.value}, 资格状态: ${eligibilityInfo.status}")
            } catch (e: Exception) {
                Log.e(TAG, "基于资格状态更新文本失败", e)
                _licenseStatusText.value = context.getString(R.string.license_status_unverified)
            }
        }
    }

    /**
     * 获取许可证状态文本（供UI使用）
     */
    fun getLicenseStatusText(): String {
        return _licenseStatusText.value
    }

    /**
     * 重新验证许可证状态
     * 使用统一的验证方法确保状态一致性
     */
    fun revalidateLicenseStatus() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "开始重新验证许可证状态")
                val isValid = licenseManager.forceRevalidateAndSync(context)
                Log.d(TAG, "许可证重新验证完成: $isValid")
            } catch (e: Exception) {
                Log.e(TAG, "重新验证许可证状态失败", e)
            }
        }
    }
} 
} 