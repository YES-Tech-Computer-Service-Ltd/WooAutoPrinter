package com.example.wooauto.updater

import android.content.Context
import android.util.Log
import com.example.wooauto.BuildConfig
import com.example.wooauto.updater.model.AppVersion
import com.example.wooauto.updater.model.UpdateInfo
import com.github.javiersantos.appupdater.AppUpdaterUtils
import com.github.javiersantos.appupdater.enums.AppUpdaterError
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.github.javiersantos.appupdater.objects.Update
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * GitHub更新器
 * 基于GitHub Releases实现应用更新检查和下载
 */
@Singleton
class GitHubUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) : UpdaterInterface {

    companion object {
        private const val TAG = "GitHubUpdater"
        private const val GITHUB_USER = "yourusername" // 替换为您的GitHub用户名
        private const val GITHUB_REPO = "WooAutoPrinter" // 替换为您的仓库名
        private const val RELEASES_URL = "https://github.com/$GITHUB_USER/$GITHUB_REPO/releases"
        private const val DEFAULT_CHECK_INTERVAL_HOURS = 24
    }

    // 当前版本信息
    private val currentVersion = AppVersion.fromBuildConfig()
    
    // 最新版本信息缓存
    private var cachedLatestVersion: AppVersion? = null
    private var lastCheckTime: Long = 0
    
    // 自动检查设置
    private var autoCheckEnabled = true
    private var checkIntervalHours = DEFAULT_CHECK_INTERVAL_HOURS

    /**
     * 检查更新
     */
    override suspend fun checkForUpdates(): Flow<UpdateInfo> = callbackFlow {
        val appUpdaterUtils = AppUpdaterUtils(context)
            .setGitHubUserAndRepo(GITHUB_USER, GITHUB_REPO)
            .setUpdateFrom(UpdateFrom.GITHUB)
            
        Log.d(TAG, "开始检查更新，当前版本: ${currentVersion.toFullVersionString()}")
        
        appUpdaterUtils.withListener(object : AppUpdaterUtils.UpdateListener {
            override fun onSuccess(update: Update, isUpdateAvailable: Boolean) {
                Log.d(TAG, "检查更新成功，是否有更新: $isUpdateAvailable, 最新版本: ${update.latestVersion}")
                
                if (isUpdateAvailable) {
                    val latestVersion = parseVersionFromString(update.latestVersion)
                    cachedLatestVersion = latestVersion
                    lastCheckTime = System.currentTimeMillis()
                    
                    // 获取更新日志
                    viewModelScope.launch {
                        val changelog = getChangelog(update.latestVersion)
                        val updateInfo = UpdateInfo(
                            latestVersion = latestVersion,
                            currentVersion = currentVersion,
                            hasUpdate = true,
                            changelog = changelog,
                            releaseDate = update.releaseDate,
                            downloadUrl = update.urlToDownload.toString(),
                            isForceUpdate = false
                        )
                        
                        trySend(updateInfo)
                    }
                } else {
                    // 没有更新
                    trySend(UpdateInfo.noUpdateAvailable(currentVersion))
                }
            }

            override fun onFailed(error: AppUpdaterError) {
                Log.e(TAG, "检查更新失败: $error")
                trySend(UpdateInfo.noUpdateAvailable(currentVersion))
            }
        })
        
        // 执行更新检查
        appUpdaterUtils.start()
        
        awaitClose { 
            // 清理资源
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 获取当前应用版本
     */
    override fun getCurrentVersion(): AppVersion {
        return currentVersion
    }

    /**
     * 获取最新版本
     */
    override suspend fun getLatestVersion(): AppVersion? {
        // 如果有缓存且未过期，直接返回缓存
        val cacheValidTime = TimeUnit.HOURS.toMillis(1) // 缓存有效期1小时
        if (cachedLatestVersion != null && 
            (System.currentTimeMillis() - lastCheckTime) < cacheValidTime) {
            return cachedLatestVersion
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val update = checkForUpdateSuspend()
                if (update != null) {
                    val latestVersion = parseVersionFromString(update.latestVersion)
                    cachedLatestVersion = latestVersion
                    lastCheckTime = System.currentTimeMillis()
                    latestVersion
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取最新版本失败", e)
                null
            }
        }
    }

    /**
     * 下载并安装更新
     */
    override suspend fun downloadAndInstall(updateInfo: UpdateInfo): Flow<Int> {
        // 使用流来报告下载进度
        val progressFlow = MutableStateFlow(0)
        
        withContext(Dispatchers.IO) {
            try {
                // 这里可以使用系统的下载管理器或自定义下载实现
                // 简化实现，仅作示例
                progressFlow.value = 10
                // ... 实际下载代码
                progressFlow.value = 100
                
                // 下载完成后安装APK
                // ... 安装代码
            } catch (e: Exception) {
                Log.e(TAG, "下载或安装更新失败", e)
            }
        }
        
        return progressFlow
    }

    /**
     * 是否有更新可用
     */
    override suspend fun hasUpdate(): Boolean {
        val latestVersion = getLatestVersion() ?: return false
        return currentVersion.isOlderThan(latestVersion)
    }

    /**
     * 获取更新日志
     */
    override suspend fun getChangelog(version: String?): String {
        return withContext(Dispatchers.IO) {
            try {
                val targetVersion = version ?: cachedLatestVersion?.toVersionString() ?: return@withContext ""
                
                // 使用Jsoup解析GitHub release页面获取更新日志
                val doc = Jsoup.connect("$RELEASES_URL/tag/$targetVersion").get()
                val releaseNotes = doc.select(".markdown-body").text()
                releaseNotes.ifEmpty { "无更新日志" }
            } catch (e: Exception) {
                Log.e(TAG, "获取更新日志失败", e)
                "无法获取更新日志"
            }
        }
    }

    /**
     * 设置自动检查更新
     */
    override fun setAutoCheckEnabled(enabled: Boolean, intervalHours: Int) {
        this.autoCheckEnabled = enabled
        this.checkIntervalHours = intervalHours
    }

    /**
     * 将版本字符串解析为AppVersion对象
     */
    private fun parseVersionFromString(versionString: String): AppVersion {
        try {
            // 解析版本字符串，例如："1.0.0" 或 "1.0.0-beta"
            val isBeta = versionString.contains("-beta", ignoreCase = true)
            val cleanVersion = versionString.replace("-beta", "").trim()
            val parts = cleanVersion.split(".")
            
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            
            return AppVersion(
                major = major,
                minor = minor,
                patch = patch,
                build = 0, // GitHub版本无法获取build号
                versionCode = 0, // GitHub版本无法获取versionCode
                versionName = versionString,
                isBeta = isBeta
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析版本字符串失败: $versionString", e)
            return AppVersion(0, 0, 0, 0, 0, versionString, false)
        }
    }

    /**
     * 挂起函数版本的更新检查
     */
    private suspend fun checkForUpdateSuspend(): Update? = suspendCancellableCoroutine { continuation ->
        val appUpdaterUtils = AppUpdaterUtils(context)
            .setGitHubUserAndRepo(GITHUB_USER, GITHUB_REPO)
            .setUpdateFrom(UpdateFrom.GITHUB)
        
        appUpdaterUtils.withListener(object : AppUpdaterUtils.UpdateListener {
            override fun onSuccess(update: Update, isUpdateAvailable: Boolean) {
                if (isUpdateAvailable) {
                    continuation.resume(update)
                } else {
                    continuation.resume(null)
                }
            }

            override fun onFailed(error: AppUpdaterError) {
                Log.e(TAG, "检查更新失败: $error")
                continuation.resume(null)
            }
        })
        
        appUpdaterUtils.start()
        
        continuation.invokeOnCancellation {
            // 取消时的清理
        }
    }
} 