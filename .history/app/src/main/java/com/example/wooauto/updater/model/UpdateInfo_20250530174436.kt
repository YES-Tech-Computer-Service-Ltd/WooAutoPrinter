package com.example.wooauto.updater.model

import java.util.Date

/**
 * 更新信息模型类
 * 用于存储应用更新相关的信息
 */
data class UpdateInfo(
    /**
     * 最新版本信息
     */
    val latestVersion: AppVersion,
    
    /**
     * 当前版本信息
     */
    val currentVersion: AppVersion,
    
    /**
     * 是否有更新可用
     */
    val hasUpdate: Boolean,
    
    /**
     * 更新日志
     */
    val changelog: String,
    
    /**
     * 更新发布日期
     */
    val releaseDate: Date? = null,
    
    /**
     * 下载URL
     */
    val downloadUrl: String,
    
    /**
     * APK文件大小（KB）
     */
    val fileSize: Long? = null,
    
    /**
     * 是否为强制更新
     */
    val isForceUpdate: Boolean = false
) {
    /**
     * 判断是否需要更新
     * 直接返回hasUpdate字段的值，因为这个值已经在WordPressUpdater中正确计算过了
     */
    fun needsUpdate(): Boolean {
        return hasUpdate
    }
    
    /**
     * 获取版本变化描述
     */
    fun getVersionChangeDescription(): String {
        val current = currentVersion.toVersionString()
        val latest = latestVersion.toVersionString()
        return "新版本：$latest (当前版本：$current)"
    }
    
    companion object {
        /**
         * 创建一个表示没有更新的UpdateInfo对象
         */
        fun noUpdateAvailable(currentVersion: AppVersion): UpdateInfo {
            return UpdateInfo(
                latestVersion = currentVersion,
                currentVersion = currentVersion,
                hasUpdate = false,
                changelog = "",
                downloadUrl = "",
                isForceUpdate = false
            )
        }
    }
} 