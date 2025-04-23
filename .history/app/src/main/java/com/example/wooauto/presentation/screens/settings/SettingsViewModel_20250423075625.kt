package com.example.wooauto.presentation.screens.settings

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // 自动化任务状态
    private val _automaticOrderProcessing = MutableStateFlow(false)
    val automaticOrderProcessing: StateFlow<Boolean> = _automaticOrderProcessing.asStateFlow()
    
    private val _automaticPrinting = MutableStateFlow(false)
    val automaticPrinting: StateFlow<Boolean> = _automaticPrinting.asStateFlow()
    
    private val _inventoryAlerts = MutableStateFlow(false)
    val inventoryAlerts: StateFlow<Boolean> = _inventoryAlerts.asStateFlow()
    
    private val _dailyBackup = MutableStateFlow(false)
    val dailyBackup: StateFlow<Boolean> = _dailyBackup.asStateFlow()
    
    private val _defaultTemplateType = MutableStateFlow(TemplateType.FULL_DETAILS)
    val defaultTemplateType: StateFlow<TemplateType> = _defaultTemplateType.asStateFlow()

    // 添加状态消息流
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        Log.d("SettingsViewModel", "初始化ViewModel")
        loadSettings()
        loadPrinterConfigs()
        loadStoreInfo()
        loadAutomationSettings()
        
        // 监听打印机扫描结果
        viewModelScope.launch {
            if (printerManager is BluetoothPrinterManager) {
                printerManager.getScanResultFlow().collect { printers ->
                    _availablePrinters.value = printers
                    Log.d("SettingsViewModel", "更新蓝牙设备列表，设备数量: ${printers.size}")
                    
                    if (_isScanning.value && printers.isNotEmpty()) {
                        // 延迟停止扫描状态，给用户时间看到结果
                        delay(2000)
                        _isScanning.value = false
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val config = settingsRepository.getWooCommerceConfig()
                _siteUrl.value = config.siteUrl
                _consumerKey.value = config.consumerKey
                _consumerSecret.value = config.consumerSecret
                _pollingInterval.value = config.pollingInterval
                _useWooCommerceFood.value = config.useWooCommerceFood
                
                Log.d("SettingsViewModel", "成功加载设置: $config")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载设置失败", e)
            }
        }
    }

    fun updateSiteUrl(url: String) {
        _siteUrl.value = url
        saveSettings()
    }

    fun updateConsumerKey(key: String) {
        _consumerKey.value = key
        saveSettings()
    }

    fun updateConsumerSecret(secret: String) {
        _consumerSecret.value = secret
        saveSettings()
    }

    fun updatePollingInterval(interval: Int) {
        val oldInterval = _pollingInterval.value
        _pollingInterval.value = interval
        saveSettings()
        
        // 如果轮询间隔发生变化，通知服务重启轮询
        if (oldInterval != interval) {
            Log.d(TAG, "轮询间隔已更改: ${oldInterval}秒 -> ${interval}秒，将通知服务重启轮询")
            notifyServiceToRestartPolling()
        }
    }

    fun updateUseWooCommerceFood(use: Boolean) {
        _useWooCommerceFood.value = use
        saveSettings()
    }

    private fun saveSettings() {
        viewModelScope.launch {
            try {
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value,
                    useWooCommerceFood = _useWooCommerceFood.value
                )
                settingsRepository.saveWooCommerceConfig(config)
                Log.d("SettingsViewModel", "成功保存设置: $config")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存设置失败", e)
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            try {
                _isTestingConnection.value = true
                Log.d("SettingsViewModel", "开始测试API连接")
                
                // 创建临时配置
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value,
                    useWooCommerceFood = _useWooCommerceFood.value
                )
                
                // 检查配置是否有效
                if (!config.isValid()) {
                    Log.e("SettingsViewModel", "配置无效: siteUrl=${config.siteUrl}, consumerKey长度=${config.consumerKey.length}, consumerSecret长度=${config.consumerSecret.length}")
                    _connectionTestResult.value = ConnectionTestResult.Error("配置无效，请确保填写了所有必要的字段")
                    return@launch
                }
                
                Log.d("SettingsViewModel", "使用配置测试连接: $config")
                
                // 立即保存配置
                saveSettings(immediate = true)
                
                // 尝试获取产品数据以测试连接
                val testResult = productRepository.testConnection(config)
                
                if (testResult) {
                    _connectionTestResult.value = ConnectionTestResult.Success
                    Log.d("SettingsViewModel", "API连接测试成功")
                } else {
                    _connectionTestResult.value = ConnectionTestResult.Error("无法连接到API，请检查配置")
                    Log.e("SettingsViewModel", "API连接测试失败")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "测试连接时出错", e)
                var errorMessage = "连接错误: ${e.message}"
                
                // 处理特定错误
                if (e.message?.contains("401") == true) {
                    errorMessage = "认证失败: 请检查您的消费者密钥(Consumer Key)和密钥(Secret)"
                } else if (e.message?.contains("404") == true) {
                    errorMessage = "找不到API: 请检查您的站点URL是否正确，并确保WooCommerce REST API已启用"
                } else if (e.message?.contains("UnknownHostException") == true || e.message?.contains("No address associated with hostname") == true) {
                    errorMessage = "无法连接到服务器: 请检查站点URL和网络连接"
                }
                
                _connectionTestResult.value = ConnectionTestResult.Error(errorMessage)
            } finally {
                _isTestingConnection.value = false
            }
        }
    }
    
    private fun saveSettings(immediate: Boolean = false) {
        viewModelScope.launch {
            try {
                // 验证消费者密钥格式
                if (_consumerKey.value.contains("http")) {
                    Log.e("SettingsViewModel", "Consumer Key不应该包含URL: ${_consumerKey.value}")
                    _connectionTestResult.value = ConnectionTestResult.Error("消费者密钥(Consumer Key)格式错误，不应包含URL")
                    return@launch
                }
                
                // 验证消费者密钥密文格式
                if (_consumerSecret.value.contains("http")) {
                    Log.e("SettingsViewModel", "Consumer Secret不应该包含URL: ${_consumerSecret.value}")
                    _connectionTestResult.value = ConnectionTestResult.Error("消费者密钥密文(Consumer Secret)格式错误，不应包含URL")
                    return@launch
                }
                
                val config = WooCommerceConfig(
                    siteUrl = _siteUrl.value,
                    consumerKey = _consumerKey.value,
                    consumerSecret = _consumerSecret.value,
                    pollingInterval = _pollingInterval.value,
                    useWooCommerceFood = _useWooCommerceFood.value
                )
                settingsRepository.saveWooCommerceConfig(config)
                Log.d("SettingsViewModel", "成功保存设置: $config")
                
                if (immediate) {
                    // 清除缓存，强制重新加载
                    productRepository.clearCache()
                    orderRepository.clearCache()
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存设置失败", e)
                _connectionTestResult.value = ConnectionTestResult.Error("保存设置失败: ${e.message}")
            }
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    fun setAppLanguage(locale: Locale) {
        viewModelScope.launch {
            try {
                Log.d("SettingsViewModel", "语言切换开始: ${locale.language}")
                
                // 1. 使用 LocaleManager 设置和保存语言
                LocaleManager.setAndSaveLocale(context, locale)
                
                // 2. 更新当前语言状态
                _currentLocale.value = locale
                
                // 3. 额外尝试一次强制刷新 UI，确保语言变化生效
                kotlinx.coroutines.delay(100)  // 短暂延迟以确保状态更新
                LocaleManager.forceRefreshUI()
                
                Log.d("SettingsViewModel", "语言切换完成，UI 将立即更新")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "语言切换失败", e)
            }
        }
    }

    // 打印机相关方法
    fun loadPrinterConfigs() {
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
    
    /**
     * 扫描蓝牙打印机
     * 改进扫描逻辑，支持Android 7设备
     */
    fun scanPrinters() {
        _isScanning.value = true
        _connectionErrorMessage.value = null
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "蓝牙设备扫描已启动")
                
                // 获取打印机管理器
                val printerManager = getPrinterManager()
                if (printerManager == null) {
                    _isScanning.value = false
                    _connectionErrorMessage.value = "打印机管理器初始化失败"
                    return@launch
                }
                
                // 打印诊断日志
                if (printerManager is BluetoothPrinterManager) {
                    printerManager.logBluetoothDiagnostics()
                }
                
                // 开始扫描
                val devices = printerManager.scanPrinters(PrinterConfig.PRINTER_TYPE_BLUETOOTH)
                
                // 更新UI
                withContext(Dispatchers.Main) {
                    _availablePrinters.value = devices
                    
                    // 订阅蓝牙扫描结果流
                    if (printerManager is BluetoothPrinterManager) {
                        try {
                            printerManager.getScanResultFlow().collect { updatedDevices ->
                                Log.d(TAG, "蓝牙设备列表更新: ${updatedDevices.size}个设备")
                                _availablePrinters.value = updatedDevices
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "蓝牙设备流收集异常: ${e.message}")
                        }
                    }
                }
                
                // 扫描完成后一段时间自动设置扫描状态为false
                delay(30000) // 30秒后
                _isScanning.value = false
            } catch (e: Exception) {
                Log.e(TAG, "扫描蓝牙设备出错: ${e.message}")
                withContext(Dispatchers.Main) {
                    _connectionErrorMessage.value = "扫描蓝牙设备时出错: ${e.message ?: "未知错误"}"
                    _isScanning.value = false
                }
            }
        }
    }
    
    /**
     * 连接打印机 - 优化安卓7支持
     */
    fun connectPrinter(device: PrinterDevice) {
        viewModelScope.launch {
            _isConnecting.value = true
            _connectionErrorMessage.value = null
            
            try {
                Log.d("SettingsViewModel", "开始连接打印机: ${device.name} (${device.address})")
                
                // 构建打印机配置
                val existingConfig = _printerConfigs.value.find { it.address == device.address }
                
                val config = existingConfig ?: PrinterConfig(
                    id = UUID.randomUUID().toString(),
                    name = device.name,
                    address = device.address,
                    type = device.type,
                    paperWidth = PrinterConfig.PAPER_WIDTH_57MM, // 默认57mm
                    isDefault = _printerConfigs.value.isEmpty(), // 如果没有打印机，则设为默认
                    isAutoPrint = false,
                    printCopies = 1
                )
                
                // 更新状态
                _printerStatus.value = PrinterStatus.CONNECTING
                
                // 开始连接
                val connected = printerManager.connect(config)
                
                if (connected) {
                    _printerStatus.value = PrinterStatus.CONNECTED
                    _currentPrinterConfig.value = config
                    Log.d("SettingsViewModel", "成功连接打印机: ${config.name}")
                    
                    // 如果是新配置，保存到配置列表
                    if (existingConfig == null) {
                        savePrinterConfig(config)
                    }
                } else {
                    _printerStatus.value = PrinterStatus.ERROR
                    _connectionErrorMessage.value = "连接失败，请确保打印机已开启并在范围内"
                    Log.e("SettingsViewModel", "连接打印机失败: ${config.name}")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "连接打印机异常", e)
                _printerStatus.value = PrinterStatus.ERROR
                _connectionErrorMessage.value = "连接异常: ${e.message ?: "未知错误"}"
            } finally {
                _isConnecting.value = false
            }
        }
    }
    
    /**
     * 根据已有配置连接打印机
     */
    suspend fun connectPrinter(config: PrinterConfig): Boolean {
        try {
            _isConnecting.value = true
            _connectionErrorMessage.value = null
            
            val connected = printerManager.connect(config)
            if (connected) {
                _printerStatus.value = PrinterStatus.CONNECTED
                Log.d("SettingsViewModel", "成功连接打印机: ${config.name}")
            } else {
                _printerStatus.value = PrinterStatus.ERROR
                _connectionErrorMessage.value = "连接失败，请确保打印机已开启并在范围内"
                Log.e("SettingsViewModel", "连接打印机失败: ${config.name}")
            }
            return connected
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "连接打印机异常", e)
            _printerStatus.value = PrinterStatus.ERROR
            _connectionErrorMessage.value = "连接异常: ${e.message ?: "未知错误"}"
            return false
        } finally {
            _isConnecting.value = false
        }
    }
    
    /**
     * 清除连接错误信息
     */
    fun clearConnectionError() {
        _connectionErrorMessage.value = null
    }
    
    fun disconnectPrinter(config: PrinterConfig) {
        viewModelScope.launch {
            try {
                printerManager.disconnect(config)
                _printerStatus.value = PrinterStatus.DISCONNECTED
                Log.d("SettingsViewModel", "已断开打印机连接: ${config.name}")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "断开打印机连接失败", e)
            }
        }
    }
    
    fun savePrinterConfig(config: PrinterConfig) {
        viewModelScope.launch {
            try {
                // 如果设置为默认打印机，则将其他打印机设置为非默认
                if (config.isDefault) {
                    printerConfigs.value.forEach { printerConfig ->
                        if (printerConfig.id != config.id && printerConfig.isDefault) {
                            val updatedConfig = printerConfig.copy(isDefault = false)
                            settingsRepository.savePrinterConfig(updatedConfig)
                        }
                    }
                }
                
                // 保存当前配置
                settingsRepository.savePrinterConfig(config)
                
                // 如果设置了当前配置
                if (_currentPrinterConfig.value?.id == config.id) {
                    _currentPrinterConfig.value = config
                }
                
                // 重新加载列表
                loadPrinterConfigs()
                
                // 如果配置为默认，连接到该打印机
                if (config.isDefault) {
                    // 修正调用，直接传递config对象而不是id
                    viewModelScope.launch {
                        printerManager.connect(config)
                    }
                }
                
                Log.d(TAG, "成功保存打印机配置: $config")
            } catch (e: Exception) {
                Log.e(TAG, "保存打印机配置失败", e)
            }
        }
    }
    
    /**
     * 更新打印机autoCut属性（自动切纸）
     * 在保存前临时保存autoCut属性值
     */
    fun updateAutoCut(autoCut: Boolean) {
        val currentConfig = _currentPrinterConfig.value
        if (currentConfig != null) {
            _currentPrinterConfig.value = currentConfig.copy(autoCut = autoCut)
            Log.d(TAG, "更新打印机自动切纸设置: ${currentConfig.name}, autoCut=$autoCut")
        }
    }

    /**
     * 获取打印机配置
     * @param printerId 打印机ID
     * @return 打印机配置，如果未找到则返回null
     */
    fun getPrinterConfig(printerId: String): PrinterConfig? {
        return printerConfigs.value.find { it.id == printerId }?.let {
            // 确保当前编辑的配置是最新的
            _currentPrinterConfig.value = it
            it
        }
    }
    
    fun deletePrinterConfig(printerId: String) {
        viewModelScope.launch {
            try {
                settingsRepository.deletePrinterConfig(printerId)
                Log.d("SettingsViewModel", "删除打印机配置: $printerId")
                
                // 刷新打印机列表
                loadPrinterConfigs()
                
                // 如果删除的是当前打印机，清除当前打印机
                if (_currentPrinterConfig.value?.id == printerId) {
                    _currentPrinterConfig.value = null
                    _printerStatus.value = PrinterStatus.DISCONNECTED
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "删除打印机配置失败", e)
            }
        }
    }
    
    /**
     * 执行测试打印
     * 将打印流程拆分为多个步骤，每个步骤都有明确的职责
     * @param config 打印机配置
     * @return 打印结果，成功返回true
     */
    suspend fun testPrint(config: PrinterConfig): Boolean {
        // 1. 准备阶段 - 设置UI状态
        prepareForPrinting()
        
        // 2. 连接打印机
        if (!await(::connectToPrinter, config)) {
            return false
        }
        
        // 3. 执行打印并处理结果
        return executePrintingAndHandleResult(config)
    }
    
    /**
     * 准备打印
     * 设置打印相关UI状态
     */
    private fun prepareForPrinting() {
        _isPrinting.value = true
        _connectionErrorMessage.value = null
        Log.d(TAG, "准备打印，状态已重置")
    }
    
    /**
     * 连接到打印机
     * @param config 打印机配置
     * @return 连接结果，连接成功返回true
     */
    private suspend fun connectToPrinter(config: PrinterConfig): Boolean {
        try {
            Log.d(TAG, "检查打印机连接状态")
            
            // 如果打印机已连接，则直接返回成功
            if (_printerStatus.value == PrinterStatus.CONNECTED) {
                Log.d(TAG, "打印机已连接")
                return true
            }
            
            // 尝试连接打印机
            Log.d(TAG, "打印机未连接，尝试连接: ${config.name}")
            val connected = printerManager.connect(config)
            
            if (!connected) {
                Log.e(TAG, "打印机连接失败")
                _connectionErrorMessage.value = "打印机连接失败，请检查打印机状态后再试"
                _isPrinting.value = false
                return false
            }
            
            Log.d(TAG, "打印机连接成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "连接打印机异常", e)
            _connectionErrorMessage.value = "连接异常: ${e.message ?: "未知错误"}"
            _isPrinting.value = false
            return false
        }
    }
    
    /**
     * 执行打印并处理结果
     * @param config 打印机配置
     * @return 打印结果，成功返回true
     */
    private suspend fun executePrintingAndHandleResult(config: PrinterConfig): Boolean {
        return try {
            Log.d(TAG, "执行测试打印内容生成")
            
            // 执行测试打印
            val success = withContext(Dispatchers.IO) {
                printerManager.printTest(config)
            }
            
            // 处理打印结果
            if (success) {
                Log.d(TAG, "测试打印成功完成")
                _connectionErrorMessage.value = null
                true
            } else {
                Log.e(TAG, "测试打印返回失败结果")
                _connectionErrorMessage.value = "测试打印失败，请检查打印机状态和连接"
                false
            }
        } catch (e: Exception) {
            handlePrintingException(e)
            false
        } finally {
            _isPrinting.value = false
        }
    }
    
    /**
     * 处理打印过程中的异常
     * @param e 捕获的异常
     */
    private suspend fun handlePrintingException(e: Exception) {
        val errorMsg = when {
            e is StringIndexOutOfBoundsException -> "打印内容格式错误，可能与打印机不兼容"
            e.message?.contains("connection") == true -> "打印机连接中断"
            e.message?.contains("timeout") == true -> "打印机响应超时"
            else -> "打印出错: ${e.message ?: "未知错误"}"
        }
        
        Log.e(TAG, "测试打印过程中异常: $errorMsg", e)
        withContext(Dispatchers.Main) {
            _connectionErrorMessage.value = errorMsg
        }
    }
    
    /**
     * 通用的异步操作辅助函数，用于简化异常处理
     * @param operation 要执行的操作
     * @param param 操作参数
     * @return 操作结果
     */
    private suspend fun <T, R> await(operation: suspend (T) -> R, param: T): R {
        return try {
            operation(param)
        } catch (e: Exception) {
            Log.e(TAG, "操作执行异常: ${e.message}", e)
            throw e
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
                // 从设置仓库加载自动化设置
                _automaticOrderProcessing.value = settingsRepository.getAutomaticOrderProcessingEnabled() ?: true  // 默认开启
                _automaticPrinting.value = settingsRepository.getAutomaticPrintingEnabled() ?: false  // 默认关闭
                _inventoryAlerts.value = true  // 默认开启库存提醒
                _dailyBackup.value = false  // 默认关闭定时备份
                
                // 加载默认打印模板
                val defaultTemplate = settingsRepository.getDefaultTemplateType() ?: TemplateType.FULL_DETAILS
                _defaultTemplateType.value = defaultTemplate
                
                Log.d("SettingsViewModel", "加载自动化设置完成: 自动接单=${_automaticOrderProcessing.value}, " +
                        "自动打印=${_automaticPrinting.value}, 默认模板=${_defaultTemplateType.value}")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "加载自动化设置失败", e)
            }
        }
    }
    
    /**
     * 保存自动化任务设置
     */
    fun saveAutomationSettings(
        automaticOrderProcessing: Boolean,
        automaticPrinting: Boolean,
        inventoryAlerts: Boolean,
        dailyBackup: Boolean,
        defaultTemplateType: TemplateType
    ) {
        viewModelScope.launch {
            try {
                Log.d("SettingsViewModel", "保存自动化设置: 自动接单=$automaticOrderProcessing, 自动打印=$automaticPrinting, " +
                        "库存提醒=$inventoryAlerts, 定时备份=$dailyBackup, 默认模板=$defaultTemplateType")
                
                // 更新本地状态
                _automaticOrderProcessing.value = automaticOrderProcessing
                _automaticPrinting.value = automaticPrinting
                _inventoryAlerts.value = inventoryAlerts
                _dailyBackup.value = dailyBackup
                
                // 保存到设置仓库
                settingsRepository.setAutomaticOrderProcessingEnabled(automaticOrderProcessing)
                settingsRepository.setAutomaticPrintingEnabled(automaticPrinting)
                
                // 保存自动打印模板
                if (_defaultTemplateType.value != defaultTemplateType) {
                    _defaultTemplateType.value = defaultTemplateType
                    settingsRepository.saveDefaultTemplateType(defaultTemplateType)
                    Log.d("SettingsViewModel", "更新默认打印模板: $defaultTemplateType")
                }
                
                // 更新打印机配置
                updatePrinterAutoSettings(automaticPrinting)
                
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存自动化设置失败", e)
            }
        }
    }
    
    /**
     * 更新打印机的自动打印设置
     */
    private fun updatePrinterAutoSettings(enableAutoPrint: Boolean) {
        viewModelScope.launch {
            try {
                // 获取默认打印机配置
                val defaultPrinter = settingsRepository.getDefaultPrinterConfig()
                if (defaultPrinter != null) {
                    // 如果配置的自动打印状态与传入值不同，则更新
                    if (defaultPrinter.isAutoPrint != enableAutoPrint) {
                        val updatedConfig = defaultPrinter.copy(isAutoPrint = enableAutoPrint)
                        settingsRepository.savePrinterConfig(updatedConfig)
                        Log.d("SettingsViewModel", "更新打印机自动打印设置: $enableAutoPrint")
                    }
                } else {
                    Log.w("SettingsViewModel", "未找到默认打印机，无法更新自动打印设置")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "更新打印机自动打印设置失败", e)
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
                settingsRepository.saveDefaultTemplateType(templateType)
                Log.d(TAG, "保存默认模板类型: $templateType")
            } catch (e: Exception) {
                Log.e(TAG, "保存默认模板类型失败: ${e.message}")
            }
        }
    }
    
    // 获取默认模板类型
    suspend fun getDefaultTemplateType(): TemplateType? {
        return try {
            settingsRepository.getDefaultTemplateType()
        } catch (e: Exception) {
            Log.e(TAG, "获取默认模板类型失败: ${e.message}")
            TemplateType.FULL_DETAILS // 出错时返回默认值
        }
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
            val automaticPrinting = settingsRepository.getAutomaticPrintingEnabled() ?: false
            
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
     * 更新状态消息
     * 用于显示打印机操作的状态信息
     */
    private fun updateStatusMessage(message: String) {
        _statusMessage.value = message
        Log.d(TAG, "状态消息更新: $message")
        
        // 3秒后清除消息
        viewModelScope.launch {
            delay(3000)
            if (_statusMessage.value == message) {
                _statusMessage.value = null
            }
        }
    }
} 