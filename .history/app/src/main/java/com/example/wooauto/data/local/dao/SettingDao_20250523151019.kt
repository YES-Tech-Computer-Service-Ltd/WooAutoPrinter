package com.example.wooauto.data.local.dao

import androidx.room.*
import com.example.wooauto.data.local.entities.SettingEntity
import kotlinx.coroutines.flow.Flow

/**
 * 设置数据访问对象
 * 定义了对设置表的所有数据库操作
 */
@Dao
interface SettingDao {
    /**
     * 插入或更新设置
     * @param setting 要插入或更新的设置实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSetting(setting: SettingEntity)

    /**
     * 获取所有设置
     * @return 设置列表流
     */
    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingEntity>>

    /**
     * 根据键获取设置
     * @param key 设置键
     * @return 设置实体
     */
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSettingByKey(key: String): SettingEntity?

    /**
     * 观察指定键的设置变化
     * @param key 设置键
     * @return 设置实体流
     */
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun observeSettingByKey(key: String): Flow<SettingEntity?>

    /**
     * 删除设置
     * @param setting 要删除的设置实体
     */
    @Delete
    suspend fun deleteSetting(setting: SettingEntity)

    /**
     * 更新设置
     * @param setting 要更新的设置实体
     */
    @Update
    suspend fun updateSetting(setting: SettingEntity)
} 