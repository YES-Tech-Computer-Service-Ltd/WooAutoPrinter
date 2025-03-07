import com.example.wooauto.domain.models.StoreInfo
import com.example.wooauto.domain.models.ThemePreferences
import com.example.wooauto.domain.models.WooCommerceConfig
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.domain.templates.TemplateType

class SettingRepository(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) : DomainSettingRepository {

    private val PREF_DEFAULT_PRINTER_ID = "default_printer_id"
    private val PREF_WOOFOOD_ENABLED = "woofood_enabled"
    private val PREF_WOOCOMMERCE_ENABLED = "woocommerce_enabled"
    private val PREF_DEFAULT_TEMPLATE_TYPE = "default_template_type"

    override suspend fun getAllPrinterConfigsFlow(): Flow<List<PrinterConfig>> = dataStore.data
        .map { preferences ->
            val configsJson = preferences[stringPreferencesKey(PREF_PRINTER_CONFIGS)] ?: "[]"
            val type = object : TypeToken<List<PrinterConfig>>() {}.type
            gson.fromJson(configsJson, type)
        }
        
    override suspend fun getDefaultTemplateType(): TemplateType? = withContext(Dispatchers.IO) {
        try {
            val preferences = dataStore.data.first()
            val templateTypeValue = preferences[stringPreferencesKey(PREF_DEFAULT_TEMPLATE_TYPE)]
            if (templateTypeValue != null) {
                return@withContext TemplateType.valueOf(templateTypeValue)
            }
            return@withContext TemplateType.FULL_DETAILS  // 默认返回完整详情模板
        } catch (e: Exception) {
            Log.e(TAG, "获取默认模板类型失败", e)
            return@withContext TemplateType.FULL_DETAILS  // 出错时返回默认模板
        }
    }
    
    override suspend fun saveDefaultTemplateType(templateType: TemplateType) = withContext(Dispatchers.IO) {
        try {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(PREF_DEFAULT_TEMPLATE_TYPE)] = templateType.name
            }
            Log.d(TAG, "保存默认模板类型: $templateType")
        } catch (e: Exception) {
            Log.e(TAG, "保存默认模板类型失败", e)
        }
    }
} 