package com.wooauto.data.repository

import com.wooauto.data.local.dao.SettingDao
import com.wooauto.data.local.entities.SettingEntity
import com.wooauto.domain.models.Setting
import com.wooauto.domain.repositories.SettingRepository_domain
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
) : SettingRepository_domain {

    /**
     * 获取所有设置的Flow
     */
    override fun getAllSettingsFlow(): Flow<List<Setting>> {
        return settingDao.getAllSettings().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * 根据key获取设置的Flow
     */
    override fun getSettingByKeyFlow(key: String): Flow<Setting?> {
        return settingDao.getSettingByKey(key).map { entity ->
            entity?.toDomainModel()
        }
    }

    /**
     * 保存设置
     */
    override suspend fun saveSetting(setting: Setting): Result<Setting> {
        return try {
            val entity = setting.toEntity()
            settingDao.insertSetting(entity)
            Result.success(entity.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除设置
     */
    override suspend fun deleteSetting(key: String): Result<Unit> {
        return try {
            settingDao.deleteSetting(key)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 