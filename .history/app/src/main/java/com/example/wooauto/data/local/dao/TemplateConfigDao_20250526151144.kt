package com.example.wooauto.data.local.dao

import androidx.room.*
import com.example.wooauto.data.local.entities.TemplateConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * 模板配置数据访问对象
 * 定义了对模板配置表的所有数据库操作
 */
@Dao
interface TemplateConfigDao {
    
    /**
     * 插入或更新模板配置
     * @param config 要插入或更新的模板配置实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: TemplateConfigEntity)
    
    /**
     * 批量插入或更新模板配置
     * @param configs 要插入或更新的模板配置实体列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfigs(configs: List<TemplateConfigEntity>)
    
    /**
     * 获取所有模板配置
     * @return 模板配置列表流
     */
    @Query("SELECT * FROM template_configs ORDER BY createdAt ASC")
    fun getAllConfigs(): Flow<List<TemplateConfigEntity>>
    
    /**
     * 根据模板ID获取配置
     * @param templateId 模板ID
     * @return 模板配置实体
     */
    @Query("SELECT * FROM template_configs WHERE templateId = :templateId")
    suspend fun getConfigById(templateId: String): TemplateConfigEntity?
    
    /**
     * 观察指定模板ID的配置变化
     * @param templateId 模板ID
     * @return 模板配置实体流
     */
    @Query("SELECT * FROM template_configs WHERE templateId = :templateId")
    fun observeConfigById(templateId: String): Flow<TemplateConfigEntity?>
    
    /**
     * 根据模板类型获取配置列表
     * @param templateType 模板类型
     * @return 模板配置列表流
     */
    @Query("SELECT * FROM template_configs WHERE templateType = :templateType ORDER BY createdAt ASC")
    fun getConfigsByType(templateType: String): Flow<List<TemplateConfigEntity>>
    
    /**
     * 删除模板配置
     * @param config 要删除的模板配置实体
     */
    @Delete
    suspend fun deleteConfig(config: TemplateConfigEntity)
    
    /**
     * 根据模板ID删除配置
     * @param templateId 模板ID
     */
    @Query("DELETE FROM template_configs WHERE templateId = :templateId")
    suspend fun deleteConfigById(templateId: String)
    
    /**
     * 更新模板配置
     * @param config 要更新的模板配置实体
     */
    @Update
    suspend fun updateConfig(config: TemplateConfigEntity)
    
    /**
     * 检查模板ID是否存在
     * @param templateId 模板ID
     * @return 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM template_configs WHERE templateId = :templateId")
    suspend fun existsById(templateId: String): Boolean
    
    /**
     * 获取配置总数
     * @return 配置总数
     */
    @Query("SELECT COUNT(*) FROM template_configs")
    suspend fun getConfigCount(): Int
    
    /**
     * 清空所有模板配置
     */
    @Query("DELETE FROM template_configs")
    suspend fun deleteAllConfigs()
} 