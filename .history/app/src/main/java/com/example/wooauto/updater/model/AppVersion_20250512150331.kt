package com.example.wooauto.updater.model

import com.example.wooauto.BuildConfig

/**
 * 应用版本模型类
 * 用于表示应用的版本信息
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val build: Int,
    val versionCode: Int,
    val versionName: String,
    val isBeta: Boolean
) {
    companion object {
        /**
         * 从BuildConfig获取当前应用版本
         */
        fun fromBuildConfig(): AppVersion {
            return AppVersion(
                major = BuildConfig.VERSION_MAJOR,
                minor = BuildConfig.VERSION_MINOR,
                patch = BuildConfig.VERSION_PATCH,
                build = BuildConfig.VERSION_BUILD,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                isBeta = BuildConfig.IS_BETA
            )
        }
    }
    
    /**
     * 比较版本新旧，如果当前版本较旧则返回true
     * @param other 要比较的版本
     * @return 如果当前版本比other旧则返回true
     */
    fun isOlderThan(other: AppVersion): Boolean {
        // 先比较主要版本号
        if (major < other.major) return true
        if (major > other.major) return false
        
        // 主要版本号相同，比较次要版本号
        if (minor < other.minor) return true
        if (minor > other.minor) return false
        
        // 次要版本号相同，比较补丁版本号
        if (patch < other.patch) return true
        if (patch > other.patch) return false
        
        // 版本号相同，beta版比非beta版旧
        if (isBeta && !other.isBeta) return true
        
        // 都是beta版本或者都不是beta版本，比较构建号
        return build < other.build
    }
    
    /**
     * 版本号字符串, 如 "1.0.0" 或 "1.0.0-beta"
     */
    fun toVersionString(): String {
        return "$major.$minor.$patch${if (isBeta) "-beta" else ""}"
    }
    
    /**
     * 版本号完整字符串, 包含构建号, 如 "1.0.0 (10)" 或 "1.0.0-beta (10)"
     */
    fun toFullVersionString(): String {
        return "${toVersionString()} (Build $build)"
    }
} 