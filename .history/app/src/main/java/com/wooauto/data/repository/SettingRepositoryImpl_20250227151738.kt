package com.wooauto.data.repository

import com.wooauto.data.local.dao.SettingDao
import com.wooauto.data.local.entities.SettingEntity
import com.wooauto.domain.models.Setting
import com.wooauto.domain.repositories.SettingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置仓库实现类
 * 实现了领域层定义的SettingRepository接口
 */
@Singleton
class SettingRepositoryImpl @Inject constructor(
    private val settingDao: SettingDao
) : SettingRepository {

    /**
     * 获取所有设置
     * @return 设置列表流
     */
    override fun getAllSettings(): Flow<List<Setting>> {
        return settingDao.getAllSettings().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 根据键获取设置
     * @param key 设置键
     * @return 设置值
     */
    override suspend fun getSettingByKey(key: String): Setting? {
        return settingDao.getSettingByKey(key)?.toDomainModel()
    }

    /**
     * 保存设置
     * @param key 设置键
     * @param value 设置值
     * @param type 设置类型
     */
    override suspend fun saveSetting(key: String, value: String, type: String) {
        val setting = SettingEntity(
            key = key,
            value = value,
            type = type,
            description = null
        )
        settingDao.insertOrUpdateSetting(setting)
    }

    /**
     * 删除设置
     * @param key 设置键
     */
    override suspend fun deleteSetting(key: String) {
        settingDao.getSettingByKey(key)?.let {
            settingDao.deleteSetting(it)
        }
    }
} 