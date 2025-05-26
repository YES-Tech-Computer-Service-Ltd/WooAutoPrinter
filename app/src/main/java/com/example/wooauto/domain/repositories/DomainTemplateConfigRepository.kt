package com.example.wooauto.domain.repositories

import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.domain.templates.TemplateType
import kotlinx.coroutines.flow.Flow

/**
 * 模板配置仓库接口
 * 定义了与模板配置相关的数据操作
 */
interface DomainTemplateConfigRepository {
    
    /**
     * 获取所有模板配置
     * @return 模板配置列表流
     */
    fun getAllConfigs(): Flow<List<TemplateConfig>>
    
    /**
     * 根据模板ID获取配置
     * @param templateId 模板ID
     * @return 模板配置流
     */
    fun getConfigById(templateId: String): Flow<TemplateConfig?>
    
    /**
     * 根据模板类型获取配置列表
     * @param templateType 模板类型
     * @return 模板配置列表流
     */
    fun getConfigsByType(templateType: TemplateType): Flow<List<TemplateConfig>>
    
    /**
     * 保存模板配置
     * @param config 要保存的模板配置
     */
    suspend fun saveConfig(config: TemplateConfig)
    
    /**
     * 批量保存模板配置
     * @param configs 要保存的模板配置列表
     */
    suspend fun saveConfigs(configs: List<TemplateConfig>)
    
    /**
     * 删除模板配置
     * @param templateId 要删除的模板ID
     */
    suspend fun deleteConfig(templateId: String)
    
    /**
     * 检查模板ID是否存在
     * @param templateId 模板ID
     * @return 是否存在
     */
    suspend fun existsById(templateId: String): Boolean
    
    /**
     * 初始化默认模板配置
     * 如果数据库中没有配置，则创建默认的预设模板
     */
    suspend fun initializeDefaultConfigs()
    
    /**
     * 获取或创建模板配置
     * 如果指定ID的配置不存在，则根据模板类型创建默认配置
     * @param templateId 模板ID
     * @param templateType 模板类型（用于创建默认配置）
     * @return 模板配置
     */
    suspend fun getOrCreateConfig(templateId: String, templateType: TemplateType): TemplateConfig
    
    /**
     * 重置模板配置为默认值
     * @param templateId 模板ID
     * @param templateType 模板类型
     */
    suspend fun resetToDefault(templateId: String, templateType: TemplateType)
    
    /**
     * 复制模板配置
     * @param sourceTemplateId 源模板ID
     * @param newTemplateId 新模板ID
     * @param newTemplateName 新模板名称
     * @return 新的模板配置
     */
    suspend fun copyConfig(sourceTemplateId: String, newTemplateId: String, newTemplateName: String): TemplateConfig?
} 