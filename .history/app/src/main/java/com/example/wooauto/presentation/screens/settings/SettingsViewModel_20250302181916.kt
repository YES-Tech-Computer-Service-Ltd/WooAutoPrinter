package com.example.wooauto.presentation.screens.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.printer.PrinterDevice
import com.example.wooauto.domain.printer.PrinterManager
import com.example.wooauto.domain.printer.PrinterStatus
import com.example.wooauto.domain.repositories.DomainOrderRepository
import com.example.wooauto.domain.repositories.DomainProductRepository
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.utils.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * 系统设置ViewModel
 * 用于管理设置界面的状态和操作
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: DomainSettingRepository,
    private val productRepository: DomainProductRepository,
    private val orderRepository: DomainOrderRepository,
    private val printerManager: PrinterManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

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
    
    private val _currencySymbol = MutableStateFlow("¥")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    init {
        Log.d("SettingsViewModel", "初始化ViewModel")
        loadSettings()
        loadPrinterConfigs()
        loadStoreInfo()
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
        _pollingInterval.value = interval
        saveSettings()
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
            _currentLocale.value = locale
            // 使用LocaleHelper来设置应用程序的语言
            LocaleHelper.setLocale(locale)
            // 保存应用语言设置
            try {
                // 使用 Android 的 SharedPreferences 存储语言设置
                context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putString("app_locale", locale.toLanguageTag())
                    .apply()
                Log.d("SettingsViewModel", "应用语言已更改为: ${locale.language}")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存语言设置失败: ${e.message}")
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
    
    fun scanPrinters() {
        viewModelScope.launch {
            try {
                _isScanning.value = true
                _availablePrinters.value = printerManager.scanPrinters(PrinterConfig.PRINTER_TYPE_BLUETOOTH)
                Log.d("SettingsViewModel", "扫描到${_availablePrinters.value.size}个打印机")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "扫描打印机失败", e)
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    suspend fun connectPrinter(config: PrinterConfig): Boolean {
        try {
            _isConnecting.value = true
            val connected = printerManager.connect(config)
            if (connected) {
                _printerStatus.value = PrinterStatus.CONNECTED
                Log.d("SettingsViewModel", "成功连接打印机: ${config.name}")
            } else {
                _printerStatus.value = PrinterStatus.ERROR
                Log.e("SettingsViewModel", "连接打印机失败: ${config.name}")
            }
            return connected
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "连接打印机异常", e)
            _printerStatus.value = PrinterStatus.ERROR
            return false
        } finally {
            _isConnecting.value = false
        }
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
                settingsRepository.savePrinterConfig(config)
                Log.d("SettingsViewModel", "保存打印机配置: ${config.name}")
                
                // 刷新打印机列表
                loadPrinterConfigs()
                
                // 如果是默认打印机，更新当前打印机
                if (config.isDefault) {
                    _currentPrinterConfig.value = config
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "保存打印机配置失败", e)
            }
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
    
    // 获取特定ID的打印机配置
    fun getPrinterConfig(printerId: String): PrinterConfig? {
        return _printerConfigs.value.find { it.id == printerId }
    }
    
    fun testPrint(config: PrinterConfig) {
        viewModelScope.launch {
            try {
                _isPrinting.value = true
                val success = printerManager.printTest(config)
                if (success) {
                    Log.d("SettingsViewModel", "测试打印成功")
                } else {
                    Log.e("SettingsViewModel", "测试打印失败")
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "测试打印异常", e)
            } finally {
                _isPrinting.value = false
            }
        }
    }
    
    // 商店信息相关方法
    private fun loadStoreInfo() {
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
} 