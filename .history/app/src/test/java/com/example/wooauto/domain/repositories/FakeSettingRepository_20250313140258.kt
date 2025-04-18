package com.example.wooauto.domain.repositories

import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.templates.TemplateType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用于测试的设置仓库假实现
 */
class FakeSettingRepository : DomainSettingRepository {
    // API设置
    private val _apiUrl = MutableStateFlow("https://default.api.com")
    private val _consumerKey = MutableStateFlow("")
    private val _consumerSecret = MutableStateFlow("")

    // 通知设置
    private val _notificationEnabled = MutableStateFlow(false)
    
    // 自动处理订单设置
    private val _automaticOrderProcessingEnabled = MutableStateFlow<Boolean?>(false)

    // 打印机设置
    private val _printerType = MutableStateFlow("")
    private val _printerConnection = MutableStateFlow(false)

    // 语言和货币设置
    private val _language = MutableStateFlow("en")
    private val _currency = MutableStateFlow("USD")
    private val _currencySymbol = MutableStateFlow("$")

    // 商店信息
    private val _storeName = MutableStateFlow("")
    private val _storeAddress = MutableStateFlow("")
    private val _storePhone = MutableStateFlow("")

    // 打印机配置列表
    private val _printerConfigs = MutableStateFlow<List<PrinterConfig>>(emptyList())
    
    // 默认模板类型
    private var defaultTemplateType: TemplateType? = null

    override suspend fun getWooCommerceConfig(): WooCommerceConfig {
        return WooCommerceConfig(_apiUrl.value, _consumerKey.value, _consumerSecret.value)
    }

    override suspend fun saveWooCommerceConfig(config: WooCommerceConfig) {
        _apiUrl.value = config.siteUrl
        _consumerKey.value = config.consumerKey
        _consumerSecret.value = config.consumerSecret
    }

    override suspend fun clearWooCommerceConfig() {
        _apiUrl.value = ""
        _consumerKey.value = ""
        _consumerSecret.value = ""
    }
    
    // 自动处理订单设置
    override suspend fun getAutomaticOrderProcessingEnabled(): Boolean? {
        return _automaticOrderProcessingEnabled.value
    }
    
    override suspend fun setAutomaticOrderProcessingEnabled(enabled: Boolean) {
        _automaticOrderProcessingEnabled.value = enabled
    }

    // API设置实现
    override fun getApiUrlFlow(): Flow<String> = _apiUrl.asStateFlow()
    override suspend fun setApiUrl(url: String) {
        _apiUrl.value = url
    }

    override fun getConsumerKeyFlow(): Flow<String> = _consumerKey.asStateFlow()
    override suspend fun setConsumerKey(consumerKey: String) {
        _consumerKey.value = consumerKey
    }

    override fun getConsumerSecretFlow(): Flow<String> = _consumerSecret.asStateFlow()
    override suspend fun setConsumerSecret(consumerSecret: String) {
        _consumerSecret.value = consumerSecret
    }

    // 通知设置实现
    override fun getNotificationEnabledFlow(): Flow<Boolean> = _notificationEnabled.asStateFlow()
    override suspend fun setNotificationEnabled(enabled: Boolean) {
        _notificationEnabled.value = enabled
    }

    // 打印机设置实现
    override fun getPrinterTypeFlow(): Flow<String> = _printerType.asStateFlow()
    override suspend fun setPrinterType(type: String) {
        _printerType.value = type
    }

    override fun getPrinterConnectionFlow(): Flow<Boolean> = _printerConnection.asStateFlow()
    override suspend fun setPrinterConnection(isConnected: Boolean) {
        _printerConnection.value = isConnected
    }

    // 语言设置实现
    override fun getLanguageFlow(): Flow<String> = _language.asStateFlow()
    override suspend fun setLanguage(language: String) {
        _language.value = language
    }

    // 货币设置实现
    override fun getCurrencyFlow(): Flow<String> = _currency.asStateFlow()
    override suspend fun setCurrency(currency: String) {
        _currency.value = currency
    }

    // 打印机配置实现
    override suspend fun getAllPrinterConfigs(): List<PrinterConfig> = _printerConfigs.value
    
    override suspend fun getPrinterConfig(printerId: String): PrinterConfig? {
        return _printerConfigs.value.find { it.id == printerId }
    }
    
    override suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        return _printerConfigs.value.find { it.isDefault }
    }
    
    override suspend fun savePrinterConfig(config: PrinterConfig) {
        val currentConfigs = _printerConfigs.value.toMutableList()
        val index = currentConfigs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            currentConfigs[index] = config
        } else {
            currentConfigs.add(config)
        }
        _printerConfigs.value = currentConfigs
    }
    
    override suspend fun deletePrinterConfig(printerId: String) {
        _printerConfigs.value = _printerConfigs.value.filter { it.id != printerId }
    }
    
    override fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?> {
        val result = MutableStateFlow<PrinterConfig?>(null)
        _printerConfigs.value.find { it.id == printerId }?.let {
            result.value = it
        }
        return result
    }
    
    override fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> = _printerConfigs.asStateFlow()

    // 商店信息实现
    override suspend fun getStoreName(): String? = _storeName.value.takeIf { it.isNotEmpty() }
    
    override suspend fun setStoreName(name: String) {
        _storeName.value = name
    }
    
    override suspend fun getStoreAddress(): String? = _storeAddress.value.takeIf { it.isNotEmpty() }
    
    override suspend fun setStoreAddress(address: String) {
        _storeAddress.value = address
    }
    
    override suspend fun getStorePhone(): String? = _storePhone.value.takeIf { it.isNotEmpty() }
    
    override suspend fun setStorePhone(phone: String) {
        _storePhone.value = phone
    }
    
    override suspend fun getCurrencySymbol(): String? = _currencySymbol.value.takeIf { it.isNotEmpty() }
    
    override suspend fun setCurrencySymbol(symbol: String) {
        _currencySymbol.value = symbol
    }
    
    override fun getStoreNameFlow(): Flow<String> = _storeName.asStateFlow()
    
    override fun getStoreAddressFlow(): Flow<String> = _storeAddress.asStateFlow()
    
    override fun getStorePhoneFlow(): Flow<String> = _storePhone.asStateFlow()
    
    override fun getCurrencySymbolFlow(): Flow<String> = _currencySymbol.asStateFlow()

    // 模板设置实现
    override suspend fun getDefaultTemplateType(): TemplateType? = defaultTemplateType
    
    override suspend fun saveDefaultTemplateType(templateType: TemplateType) {
        defaultTemplateType = templateType
    }
} 