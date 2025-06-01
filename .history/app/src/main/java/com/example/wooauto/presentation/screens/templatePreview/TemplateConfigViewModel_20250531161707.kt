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
 * Template Configuration ViewModel
 * Manages template configuration state and business logic
 */
@HiltViewModel
class TemplateConfigViewModel @Inject constructor(
    private val templateConfigRepository: DomainTemplateConfigRepository
) : ViewModel() {
    
    // All template configurations list
    private val _allConfigs = MutableStateFlow<List<TemplateConfig>>(emptyList())
    val allConfigs: StateFlow<List<TemplateConfig>> = _allConfigs.asStateFlow()
    
    // Currently editing template configuration
    private val _currentConfig = MutableStateFlow<TemplateConfig?>(null)
    val currentConfig: StateFlow<TemplateConfig?> = _currentConfig.asStateFlow()
    
    // Is saving
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    // Is loading
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Success message
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    init {
        // Initialize default configurations
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
     * Load all template configurations
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
     * Load configuration by template ID
     * @param templateId Template ID
     * @param templateType Template type (for creating default configuration)
     * @param customTemplateName Custom template name (for new templates)
     */
    fun loadConfigById(templateId: String, templateType: TemplateType, customTemplateName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = if (templateId.startsWith("custom_") && customTemplateName != null) {
                    // Create new custom template with all options defaulting to false
                    createCustomTemplate(templateId, customTemplateName, templateType)
                } else {
                    templateConfigRepository.getOrCreateConfig(templateId, templateType)
                }
                _currentConfig.value = config
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load configuration: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Create custom template with all options defaulting to false
     */
    private fun createCustomTemplate(templateId: String, templateName: String, templateType: TemplateType): TemplateConfig {
        return TemplateConfig(
            templateId = templateId,
            templateType = templateType,
            templateName = templateName,
            // All display options set to false
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
     * Update current configuration
     * @param updatedConfig Updated configuration
     */
    fun updateCurrentConfig(updatedConfig: TemplateConfig) {
        _currentConfig.value = updatedConfig
    }
    
    /**
     * Save current configuration
     */
    fun saveCurrentConfig() {
        val config = _currentConfig.value ?: return
        
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.saveConfig(config)
                _successMessage.value = "Configuration saved"
                _isSaving.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save configuration: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Save specified configuration
     * @param config Configuration to save
     */
    fun saveConfig(config: TemplateConfig) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.saveConfig(config)
                _successMessage.value = "Configuration saved"
                _isSaving.value = false
                
                // If this is the currently editing configuration, update the state
                if (_currentConfig.value?.templateId == config.templateId) {
                    _currentConfig.value = config
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save configuration: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Reset configuration to default values
     * @param templateId Template ID
     * @param templateType Template type
     */
    fun resetToDefault(templateId: String, templateType: TemplateType) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.resetToDefault(templateId, templateType)
                // Reload configuration
                loadConfigById(templateId, templateType)
                _successMessage.value = "Reset to default configuration"
                _isSaving.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reset configuration: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Delete configuration
     * @param templateId Template ID
     */
    fun deleteConfig(templateId: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.deleteConfig(templateId)
                _successMessage.value = "Configuration deleted"
                _isSaving.value = false
                
                // If deleting the current configuration, clear current configuration
                if (_currentConfig.value?.templateId == templateId) {
                    _currentConfig.value = null
                }
                
                // Reload all configurations
                loadAllConfigs()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete configuration: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Delete custom template
     * @param templateId Custom template ID (must start with "custom_")
     */
    fun deleteCustomTemplate(templateId: String) {
        if (!templateId.startsWith("custom_")) {
            _errorMessage.value = "只能删除自定义模板"
            return
        }
        
        viewModelScope.launch {
            _isSaving.value = true
            try {
                templateConfigRepository.deleteConfig(templateId)
                _successMessage.value = "自定义模板已删除"
                _isSaving.value = false
                
                // If deleting the current configuration, clear current configuration
                if (_currentConfig.value?.templateId == templateId) {
                    _currentConfig.value = null
                }
                
                // Reload all configurations
                loadAllConfigs()
            } catch (e: Exception) {
                _errorMessage.value = "删除自定义模板失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Reset all templates to default settings
     * This will delete all custom templates and reset default templates
     */
    fun resetAllTemplates() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Delete all custom templates
                val customTemplates = _allConfigs.value.filter { it.templateId.startsWith("custom_") }
                for (template in customTemplates) {
                    templateConfigRepository.deleteConfig(template.templateId)
                }
                
                // Reset default templates to their original settings
                templateConfigRepository.resetToDefault("full_details", TemplateType.FULL_DETAILS)
                templateConfigRepository.resetToDefault("delivery", TemplateType.DELIVERY)
                templateConfigRepository.resetToDefault("kitchen", TemplateType.KITCHEN)
                
                _successMessage.value = "所有模板已重置为默认设置"
                _isSaving.value = false
                
                // Clear current configuration if it was a custom template
                val currentConfig = _currentConfig.value
                if (currentConfig?.templateId?.startsWith("custom_") == true) {
                    _currentConfig.value = null
                }
                
                // Reload all configurations
                loadAllConfigs()
            } catch (e: Exception) {
                _errorMessage.value = "重置模板失败: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Copy configuration
     * @param sourceTemplateId Source template ID
     * @param newTemplateId New template ID
     * @param newTemplateName New template name
     */
    fun copyConfig(sourceTemplateId: String, newTemplateId: String, newTemplateName: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val newConfig = templateConfigRepository.copyConfig(sourceTemplateId, newTemplateId, newTemplateName)
                if (newConfig != null) {
                    _currentConfig.value = newConfig
                    _successMessage.value = "Configuration copied"
                    loadAllConfigs() // Reload all configurations
                } else {
                    _errorMessage.value = "Failed to copy configuration: source configuration not found"
                }
                _isSaving.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Failed to copy configuration: ${e.message}"
                _isSaving.value = false
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
    
    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }
    
    /**
     * Get configurations by template type
     * @param templateType Template type
     */
    fun getConfigsByType(templateType: TemplateType) {
        viewModelScope.launch {
            try {
                templateConfigRepository.getConfigsByType(templateType)
                    .catch { e ->
                        _errorMessage.value = "Failed to load configuration: ${e.message}"
                    }
                    .collect { configs ->
                        // Can handle type-specific configurations as needed
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load configuration: ${e.message}"
            }
        }
    }
} 