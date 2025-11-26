package com.example.wooauto.domain.repositories

import com.example.wooauto.data.remote.WooCommerceConfig
import com.example.wooauto.domain.models.PrinterConfig
import com.example.wooauto.domain.models.SoundSettings
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
    
    // 自动打印设置
    private val _automaticPrintingEnabled = MutableStateFlow<Boolean?>(false)

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
    
    // 临时手动打印标志
    private var temporaryManualPrintFlag = false
    
    // 模板和打印设置
    private var defaultPrintTemplate: TemplateType = TemplateType.FULL_DETAILS
    private var customTemplateId: String? = null
    private var defaultAutoPrintTemplateId: String? = null
    private var autoPrintEnabled = false
    
    // 声音设置
    private val _notificationVolume = MutableStateFlow(70)
    private val _soundType = MutableStateFlow("default")
    private val _soundEnabled = MutableStateFlow(true)
    private val _customSoundUri = MutableStateFlow("")
    
    // 自动更新设置
    private val _autoUpdate = MutableStateFlow(true)
    private var _lastUpdateCheckTime: Long = 0

    // 自定义模板相关设置
    private val _defaultAutoPrintTemplateId = MutableStateFlow<String?>(null)
    private val _temporaryManualPrintFlag = MutableStateFlow(false)
    
    // 屏幕常亮设置
    private val _keepScreenOn = MutableStateFlow(false)
    
    // 应用内亮度设置（null 表示跟随系统）
    private val _appBrightnessPercent = MutableStateFlow<Int?>(null)

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
    
    // 自动处理订单设置
    override suspend fun getAutomaticOrderProcessingEnabled(): Boolean? {
        return _automaticOrderProcessingEnabled.value
    }
    
    override suspend fun setAutomaticOrderProcessingEnabled(enabled: Boolean) {
        _automaticOrderProcessingEnabled.value = enabled
    }
    
    // 自动打印设置
    override suspend fun getAutomaticPrintingEnabled(): Boolean? {
        return _automaticPrintingEnabled.value
    }
    
    override suspend fun setAutomaticPrintingEnabled(enabled: Boolean) {
        _automaticPrintingEnabled.value = enabled
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
    override suspend fun getDefaultPrintTemplate(): TemplateType {
        return defaultPrintTemplate
    }

    override suspend fun setDefaultPrintTemplate(templateType: TemplateType) {
        defaultPrintTemplate = templateType
    }

    // 模板设置实现
    override suspend fun getDefaultTemplateType(): TemplateType = defaultTemplateType ?: TemplateType.FULL_DETAILS
    
    override suspend fun saveDefaultTemplateType(templateType: TemplateType) {
        defaultTemplateType = templateType
    }

    override suspend fun getCurrentCustomTemplateId(): String? {
        return customTemplateId
    }

    override suspend fun saveCustomTemplateId(templateId: String) {
        customTemplateId = templateId
    }

    override suspend fun getDefaultAutoPrintTemplateId(): String? {
        return defaultAutoPrintTemplateId
    }

    override suspend fun saveDefaultAutoPrintTemplateId(templateId: String) {
        defaultAutoPrintTemplateId = templateId
    }

    override suspend fun setTemporaryManualPrintFlag(isManualPrint: Boolean) {
        temporaryManualPrintFlag = isManualPrint
    }

    // 声音设置实现
    override suspend fun getSoundSettings(): SoundSettings {
        return SoundSettings(
            notificationVolume = _notificationVolume.value,
            soundType = _soundType.value,
            soundEnabled = _soundEnabled.value
        )
    }
    
    override suspend fun saveSoundSettings(settings: SoundSettings) {
        _notificationVolume.value = settings.notificationVolume
        _soundType.value = settings.soundType
        _soundEnabled.value = settings.soundEnabled
    }
    
    override suspend fun getNotificationVolume(): Int {
        return _notificationVolume.value
    }
    
    override suspend fun setNotificationVolume(volume: Int) {
        _notificationVolume.value = volume
    }
    
    override suspend fun getSoundType(): String {
        return _soundType.value
    }
    
    override suspend fun setSoundType(type: String) {
        _soundType.value = type
    }
    
    override suspend fun getSoundEnabled(): Boolean {
        return _soundEnabled.value
    }
    
    override suspend fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
    }
    
    override suspend fun getCustomSoundUri(): String {
        return _customSoundUri.value
    }
    
    override suspend fun setCustomSoundUri(uri: String) {
        _customSoundUri.value = uri
    }

    override suspend fun getKeepRingingUntilAccept(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun setKeepRingingUntilAccept(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    // 自动更新相关方法实现
    override suspend fun getAutoUpdate(): Boolean {
        return _autoUpdate.value
    }
    
    override suspend fun setAutoUpdate(enabled: Boolean) {
        _autoUpdate.value = enabled
    }
    
    override fun getAutoUpdateFlow(): Flow<Boolean> {
        return _autoUpdate.asStateFlow()
    }
    
    override suspend fun getLastUpdateCheckTime(): Long {
        return _lastUpdateCheckTime
    }
    
    override suspend fun setLastUpdateCheckTime(timestamp: Long) {
        _lastUpdateCheckTime = timestamp
    }

    // 临时手动打印标志
    override suspend fun getAndClearTemporaryManualPrintFlag(): Boolean {
        val result = temporaryManualPrintFlag
        temporaryManualPrintFlag = false
        return result
    }

    override suspend fun getAutoPrintEnabled(): Boolean {
        return autoPrintEnabled
    }

    override suspend fun setAutoPrintEnabled(enabled: Boolean) {
        autoPrintEnabled = enabled
    }

    // 屏幕常亮设置
    override fun getKeepScreenOn(): Flow<Boolean> {
        return _keepScreenOn.asStateFlow()
    }
    
    override suspend fun setKeepScreenOn(keepOn: Boolean) {
        _keepScreenOn.value = keepOn
    }
    
    // 应用内亮度实现
    override fun getAppBrightnessFlow(): Flow<Int?> = _appBrightnessPercent.asStateFlow()
    
    override suspend fun setAppBrightness(percent: Int) {
        _appBrightnessPercent.value = percent.coerceIn(5, 100)
    }
    
    override suspend fun clearAppBrightness() {
        _appBrightnessPercent.value = null
    }
    
    // 模板打印份数设置
    private var templatePrintCopies: Map<String, Int> = emptyMap()
    
    override suspend fun getTemplatePrintCopies(): Map<String, Int> {
        return templatePrintCopies
    }
    
    override suspend fun saveTemplatePrintCopies(printCopies: Map<String, Int>) {
        templatePrintCopies = printCopies
    }

    // 打印机唤醒（最小走纸）设置
    private var keepAliveFeedEnabled = false
    private var keepAliveFeedIntervalHours = 24
    private var lastKeepAliveFeedTime = 0L

    override suspend fun getKeepAliveFeedEnabled(): Boolean {
        return keepAliveFeedEnabled
    }

    override suspend fun setKeepAliveFeedEnabled(enabled: Boolean) {
        keepAliveFeedEnabled = enabled
    }

    override suspend fun getKeepAliveFeedIntervalHours(): Int {
        return keepAliveFeedIntervalHours
    }

    override suspend fun setKeepAliveFeedIntervalHours(hours: Int) {
        keepAliveFeedIntervalHours = hours
    }

    override suspend fun getLastKeepAliveFeedTime(): Long {
        return lastKeepAliveFeedTime
    }

    override suspend fun setLastKeepAliveFeedTime(timestamp: Long) {
        lastKeepAliveFeedTime = timestamp
    }
} 