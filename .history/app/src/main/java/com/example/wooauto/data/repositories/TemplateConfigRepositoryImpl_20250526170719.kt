package com.example.wooauto.data.repositories

import com.example.wooauto.data.local.dao.TemplateConfigDao
import com.example.wooauto.data.local.entities.TemplateConfigEntity
import com.example.wooauto.domain.models.TemplateConfig
import com.example.wooauto.domain.repositories.DomainTemplateConfigRepository
import com.example.wooauto.domain.templates.TemplateType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模板配置仓库实现
 * 负责模板配置的数据存储和业务逻辑
 */
@Singleton
class TemplateConfigRepositoryImpl @Inject constructor(
    private val templateConfigDao: TemplateConfigDao
) : DomainTemplateConfigRepository {
    
    override fun getAllConfigs(): Flow<List<TemplateConfig>> {
        return templateConfigDao.getAllConfigs().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun getConfigById(templateId: String): Flow<TemplateConfig?> {
        return templateConfigDao.observeConfigById(templateId).map { entity ->
            entity?.toDomainModel()
        }
    }
    
    override fun getConfigsByType(templateType: TemplateType): Flow<List<TemplateConfig>> {
        return templateConfigDao.getConfigsByType(templateType.name).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun saveConfig(config: TemplateConfig) {
        val entity = TemplateConfigEntity.fromDomainModel(config.copyWithUpdatedTimestamp())
        templateConfigDao.insertOrUpdateConfig(entity)
    }
    
    override suspend fun saveConfigs(configs: List<TemplateConfig>) {
        val entities = configs.map { config ->
            TemplateConfigEntity.fromDomainModel(config.copyWithUpdatedTimestamp())
        }
        templateConfigDao.insertOrUpdateConfigs(entities)
    }
    
    override suspend fun deleteConfig(templateId: String) {
        templateConfigDao.deleteConfigById(templateId)
    }
    
    override suspend fun existsById(templateId: String): Boolean {
        return templateConfigDao.existsById(templateId)
    }
    
    override suspend fun initializeDefaultConfigs() {
        // 检查是否已有配置
        val configCount = templateConfigDao.getConfigCount()
        if (configCount == 0) {
            // 创建并保存默认配置
            val defaultConfigs = TemplateConfig.PRESET_TEMPLATES.map { templateId ->
                val templateType = when (templateId) {
                    "full_details" -> TemplateType.FULL_DETAILS
                    "delivery" -> TemplateType.DELIVERY
                    "kitchen" -> TemplateType.KITCHEN
                    else -> TemplateType.FULL_DETAILS
                }
                TemplateConfig.createDefaultConfig(templateType, templateId)
            }
            saveConfigs(defaultConfigs)
        }
    }
    
    override suspend fun getOrCreateConfig(templateId: String, templateType: TemplateType): TemplateConfig {
        // 先尝试从数据库获取
        val existingConfig = templateConfigDao.getConfigById(templateId)
        return if (existingConfig != null) {
            existingConfig.toDomainModel()
        } else {
            // 不存在则创建默认配置并保存
            val newConfig = TemplateConfig.createDefaultConfig(templateType, templateId)
            saveConfig(newConfig)
            newConfig
        }
    }
    
    override suspend fun resetToDefault(templateId: String, templateType: TemplateType) {
        val defaultConfig = TemplateConfig.createDefaultConfig(templateType, templateId)
        saveConfig(defaultConfig)
    }
    
    override suspend fun copyConfig(
        sourceTemplateId: String,
        newTemplateId: String,
        newTemplateName: String
    ): TemplateConfig? {
        val sourceConfig = templateConfigDao.getConfigById(sourceTemplateId)
        return if (sourceConfig != null) {
            val newConfig = sourceConfig.toDomainModel().copy(
                templateId = newTemplateId,
                templateName = newTemplateName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            saveConfig(newConfig)
            newConfig
        } else {
            null
        }
    }
} 