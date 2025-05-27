package com.example.wooauto.presentation.screens.templatePreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.domain.repositories.DomainTemplateConfigRepository
import com.example.wooauto.domain.templates.TemplateType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 模板配置ViewModel
 * 管理模板配置的状态和业务逻辑
 */
@HiltViewModel
class TemplateConfigViewModel @Inject constructor(
    private val templateConfigRepository: DomainTemplateConfigRepository
) : ViewModel() {
    
    // 所有模板配置列表
    private val _allConfigs = MutableStateFlow<List<TemplateConfig>>(emptyList())
    val allConfigs: StateFlow<List<TemplateConfig>> = _allConfigs.asStateFlow()
    
    // 当前正在编辑的模板配置
    private val _currentConfig = MutableStateFlow<TemplateConfig?>(null)
    val currentConfig: StateFlow<TemplateConfig?> = _currentConfig.asStateFlow()
    
    // 是否正在保存
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    // 是否正在加载
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 成功保存的消息
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    init {
        // 初始化默认配置
        viewModelScope.launch {
            try {
                templateConfigRepository.initializeDefaultConfigs()
                loadAllConfigs()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to initialize configuration: ${e.message}"
            }
        }
    }
    
    /**
     * 加载所有模板配置
     */
    fun loadAllConfigs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                templateConfigRepository.getAllConfigs()
                    .catch { e ->
                        _errorMessage.value = "Failed to load configuration: ${e.message}"
                    }
                    .collect { configs ->
                        _allConfigs.value = configs
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load configuration: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 根据模板ID加载配置
     * @param templateId 模板ID
     * @param templateType 模板类型（用于创建默认配置）
     * @param customTemplateName 自定义模板名称（用于新建模板）
     */
    fun loadConfigById(templateId: String, templateType: TemplateType, customTemplateName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = if (templateId.startsWith("custom_") && customTemplateName != null) {
                    // 创建新的自定义模板，所有选项默认为false
                    createCustomTemplate(templateId, customTemplateName, templateType)
                } else {
                    templateConfigRepository.getOrCreateConfig(templateId, templateType)
                }
                _currentConfig.value = config
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "加载配置失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 创建自定义模板，所有选项默认为false
     */
    private fun createCustomTemplate(templateId: String, templateName: String, templateType: TemplateType): TemplateConfig {
        return TemplateConfig(
            templateId = templateId,
            templateType = templateType,
            templateName = templateName,
            // 所有显示选项都设置为false
            showStoreInfo = false,
            showStoreName = false,
            showStoreAddress = false,
            showStorePhone = false,
            showOrderInfo = false,
            showOrderNumber = false,
            showOrderDate = false,
            showCustomerInfo = false,
            showCustomerName = false,
            showCustomerPhone = false,
            showDeliveryInfo = false,
            showOrderContent = false,
            showItemDetails = false,
            showItemPrices = false,
            showOrderNotes = false,
            showTotals = false,
            showPaymentInfo = false,
            showFooter = false,
            footerText = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新当前配置
     * @param updatedConfig 更新后的配置
     */
    fun updateCurrentConfig(updatedConfig: TemplateConfig) {
        _currentConfig.value = updatedConfig
    }
    
    /**
     * 保存当前配置
     */
    fun saveCurrentConfig() {
        val config = _currentConfig.value ?: return
        
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.saveConfig(config)
                _successMessage.value = "配置已保存"
                _isSaving.value = false
            } catch (e: Exception) {
                _errorMessage.value = "保存配置失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * 保存指定配置
     * @param config 要保存的配置
     */
    fun saveConfig(config: TemplateConfig) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.saveConfig(config)
                _successMessage.value = "配置已保存"
                _isSaving.value = false
                
                // 如果是当前正在编辑的配置，更新状态
                if (_currentConfig.value?.templateId == config.templateId) {
                    _currentConfig.value = config
                }
            } catch (e: Exception) {
                _errorMessage.value = "保存配置失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * 重置配置为默认值
     * @param templateId 模板ID
     * @param templateType 模板类型
     */
    fun resetToDefault(templateId: String, templateType: TemplateType) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.resetToDefault(templateId, templateType)
                // 重新加载配置
                loadConfigById(templateId, templateType)
                _successMessage.value = "已重置为默认配置"
                _isSaving.value = false
            } catch (e: Exception) {
                _errorMessage.value = "重置配置失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * 删除配置
     * @param templateId 模板ID
     */
    fun deleteConfig(templateId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.deleteConfig(templateId)
                _successMessage.value = "配置已删除"
                _isSaving.value = false
                
                // 如果删除的是当前配置，清空当前配置
                if (_currentConfig.value?.templateId == templateId) {
                    _currentConfig.value = null
                }
                
                // 重新加载所有配置
                loadAllConfigs()
            } catch (e: Exception) {
                _errorMessage.value = "删除配置失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * 复制配置
     * @param sourceTemplateId 源模板ID
     * @param newTemplateId 新模板ID
     * @param newTemplateName 新模板名称
     */
    fun copyConfig(sourceTemplateId: String, newTemplateId: String, newTemplateName: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val newConfig = templateConfigRepository.copyConfig(sourceTemplateId, newTemplateId, newTemplateName)
                if (newConfig != null) {
                    _currentConfig.value = newConfig
                    _successMessage.value = "配置已复制"
                    loadAllConfigs() // 重新加载所有配置
                } else {
                    _errorMessage.value = "复制配置失败：源配置不存在"
                }
                _isSaving.value = false
            } catch (e: Exception) {
                _errorMessage.value = "复制配置失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * 清除成功消息
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    /**
     * 根据模板类型获取配置列表
     * @param templateType 模板类型
     */
    fun getConfigsByType(templateType: TemplateType) {
        viewModelScope.launch {
            try {
                templateConfigRepository.getConfigsByType(templateType)
                    .catch { e ->
                        _errorMessage.value = "加载配置失败: ${e.message}"
                    }
                    .collect { configs ->
                        // 可以根据需要处理特定类型的配置
                    }
            } catch (e: Exception) {
                _errorMessage.value = "加载配置失败: ${e.message}"
            }
        }
    }
} 