package com.wooauto.domain.repositories

import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class FakeSettingRepository :
    DomainSettingRepository {
    // API设置
    private val _apiUrl = MutableStateFlow("https://default.api.com")
    private val _consumerKey = MutableStateFlow("")
    private val _consumerSecret = MutableStateFlow("")

    // 通知设置
    private val _notificationEnabled = MutableStateFlow(false)

    // 打印机设置
    private val _printerType = MutableStateFlow("")
    private val _printerConnection = MutableStateFlow(false)

    // 语言和货币设置
    private val _language = MutableStateFlow("en")
    private val _currency = MutableStateFlow("USD")
    
    // 商店信息
    private val _storeName = MutableStateFlow("")
    private val _storeAddress = MutableStateFlow("")
    private val _storePhone = MutableStateFlow("")
    private val _currencySymbol = MutableStateFlow("$")
    
    // 打印机配置
    private val _printerConfigs = MutableStateFlow<List<PrinterConfig>>(emptyList())

    // WooCommerce配置
    override suspend fun getWooCommerceConfig(): WooCommerceConfig {
        return WooCommerceConfig(
            siteUrl = _apiUrl.value,
            consumerKey = _consumerKey.value,
            consumerSecret = _consumerSecret.value,
            pollingInterval = 30,
            useWooCommerceFood = false
        )
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

    // API设置实现
    override fun getApiUrlFlow(): Flow<String> = _apiUrl.asStateFlow()
    override suspend fun setApiUrl(url: String) {
        _apiUrl.value = url
    }

    override fun getConsumerKeyFlow(): Flow<String> = _consumerKey.asStateFlow()
    override suspend fun setConsumerKey(key: String) {
        _consumerKey.value = key
    }

    override fun getConsumerSecretFlow(): Flow<String> = _consumerSecret.asStateFlow()
    override suspend fun setConsumerSecret(secret: String) {
        _consumerSecret.value = secret
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
    override suspend fun setLanguage(lang: String) {
        _language.value = lang
    }

    // 货币设置实现
    override fun getCurrencyFlow(): Flow<String> = _currency.asStateFlow()
    override suspend fun setCurrency(curr: String) {
        _currency.value = curr
    }
    
    // 打印机配置实现
    override suspend fun getAllPrinterConfigs(): List<PrinterConfig> {
        return _printerConfigs.value
    }
    
    override suspend fun getPrinterConfig(printerId: String): PrinterConfig? {
        return _printerConfigs.value.find { it.id == printerId }
    }
    
    override suspend fun getDefaultPrinterConfig(): PrinterConfig? {
        return _printerConfigs.value.find { it.isDefault }
    }
    
    override suspend fun savePrinterConfig(config: PrinterConfig) {
        val currentList = _printerConfigs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == config.id }
        
        if (index >= 0) {
            currentList[index] = config
        } else {
            currentList.add(config)
        }
        
        _printerConfigs.value = currentList
    }
    
    override suspend fun deletePrinterConfig(printerId: String) {
        _printerConfigs.value = _printerConfigs.value.filter { it.id != printerId }
    }
    
    override fun getPrinterConfigFlow(printerId: String): Flow<PrinterConfig?> {
        return flow { emit(_printerConfigs.value.find { it.id == printerId }) }
    }
    
    override fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> {
        return _printerConfigs.asStateFlow()
    }
    
    // 商店信息实现
    override suspend fun getStoreName(): String? {
        return _storeName.value.ifEmpty { null }
    }
    
    override suspend fun setStoreName(name: String) {
        _storeName.value = name
    }
    
    override suspend fun getStoreAddress(): String? {
        return _storeAddress.value.ifEmpty { null }
    }
    
    override suspend fun setStoreAddress(address: String) {
        _storeAddress.value = address
    }
    
    override suspend fun getStorePhone(): String? {
        return _storePhone.value.ifEmpty { null }
    }
    
    override suspend fun setStorePhone(phone: String) {
        _storePhone.value = phone
    }
    
    override suspend fun getCurrencySymbol(): String? {
        return _currencySymbol.value.ifEmpty { null }
    }
    
    override suspend fun setCurrencySymbol(symbol: String) {
        _currencySymbol.value = symbol
    }
    
    override fun getStoreNameFlow(): Flow<String> {
        return _storeName.asStateFlow()
    }
    
    override fun getStoreAddressFlow(): Flow<String> {
        return _storeAddress.asStateFlow()
    }
    
    override fun getStorePhoneFlow(): Flow<String> {
        return _storePhone.asStateFlow()
    }
    
    override fun getCurrencySymbolFlow(): Flow<String> {
        return _currencySymbol.asStateFlow()
    }
} 