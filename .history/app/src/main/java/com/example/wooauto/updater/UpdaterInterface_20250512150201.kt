package com.example.wooauto.updater

import com.example.wooauto.updater.model.AppVersion
import com.example.wooauto.updater.model.UpdateInfo
import kotlinx.coroutines.flow.Flow

/**
 * 应用更新服务接口
 * 定义了应用更新所需的基本功能
 */
interface UpdaterInterface {
    /**
     * 检查更新
     * @return 更新信息流
     */
    suspend fun checkForUpdates(): Flow<UpdateInfo>
    
    /**
     * 获取当前应用版本信息
     * @return 当前版本信息
     */
    fun getCurrentVersion(): AppVersion
    
    /**
     * 获取最新版本信息
     * @return 最新版本信息（如果有的话）
     */
    suspend fun getLatestVersion(): AppVersion?
    
    /**
     * 下载并安装更新
     * @param updateInfo 更新信息
     * @return 下载进度流，范围0-100
     */
    suspend fun downloadAndInstall(updateInfo: UpdateInfo): Flow<Int>
    
    /**
     * 是否有更新可用
     * @return 是否有新版本
     */
    suspend fun hasUpdate(): Boolean
    
    /**
     * 获取更新日志
     * @param version 指定版本，为空则获取最新版本
     * @return 更新日志内容
     */
    suspend fun getChangelog(version: String? = null): String
    
    /**
     * 设置自动检查更新
     * @param enabled 是否启用
     * @param intervalHours 检查间隔(小时)
     */
    fun setAutoCheckEnabled(enabled: Boolean, intervalHours: Int = 24)
} 