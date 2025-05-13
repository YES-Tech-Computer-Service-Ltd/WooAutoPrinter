package com.example.wooauto.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
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
        private const val GITHUB_USER = "wuchenhao0810"
        private const val GITHUB_REPO = "WooAutoPrinter" 
        private const val RELEASES_URL = "https://github.com/$GITHUB_USER/$GITHUB_REPO/releases"
        private const val DEFAULT_CHECK_INTERVAL_HOURS = 24
        
        // 下载相关常量
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_FOLDER = "WooAutoPrinter"
        private const val DOWNLOAD_FILE_NAME = "WooAutoPrinter-update.apk"
        private const val DOWNLOAD_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"
    }

    // 当前版本信息
    private val currentVersion = AppVersion.fromBuildConfig()
    
    // 最新版本信息缓存
    private var cachedLatestVersion: AppVersion? = null
    private var lastCheckTime: Long = 0
    
    // 下载相关变量
    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null
    
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
                    
                    // 获取更新日志并发送结果
                    try {
                        val updateInfo = UpdateInfo(
                            latestVersion = latestVersion,
                            currentVersion = currentVersion,
                            hasUpdate = true,
                            changelog = "加载中...", // 先提供一个占位符
                            releaseDate = getCurrentDate(), // 使用当前日期作为发布日期
                            downloadUrl = update.urlToDownload.toString(),
                            isForceUpdate = false
                        )
                        
                        trySend(updateInfo)
                    } catch (e: Exception) {
                        Log.e(TAG, "发送更新信息失败", e)
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
     * 使用Android DownloadManager下载APK，然后触发安装
     */
    override suspend fun downloadAndInstall(updateInfo: UpdateInfo): Flow<Int> = callbackFlow {
        try {
            // 发送初始进度
            trySend(0)
            
            // 准备下载
            val uri = Uri.parse(updateInfo.downloadUrl)
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_FOLDER)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val destinationFile = File(downloadDir, DOWNLOAD_FILE_NAME)
            
            // 如果目标文件已存在，删除它
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            
            // 创建下载请求
            val request = DownloadManager.Request(uri).apply {
                setTitle("WooAutoPrinter 更新")
                setDescription("下载版本 ${updateInfo.latestVersion.toVersionString()}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType(APK_MIME_TYPE)
            }
            
            // 获取下载管理器并开始下载
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            Log.d(TAG, "APK下载已开始，下载ID: $downloadId")
            
            // 注册广播接收器接收下载完成通知
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        // 检查下载状态
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    // 下载完成，安装APK
                                    trySend(100)
                                    installApk(destinationFile)
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    Log.e(TAG, "下载失败，原因: $reason")
                                    trySend(-1) // 发送错误代码
                                }
                            }
                        }
                        cursor.close()
                        
                        // 注销广播接收器
                        context.unregisterReceiver(this)
                        downloadReceiver = null
                    }
                }
            }
            
            // 注册广播接收器
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
            
            // 保存接收器引用以便后续清理
            downloadReceiver = receiver
            
            // 启动进度监控协程
            withContext(Dispatchers.IO) {
                monitorDownloadProgress(downloadManager, downloadId) { progress ->
                    trySend(progress)
                }
            }
            
            // 在通道关闭时清理资源
            awaitClose {
                downloadReceiver?.let {
                    try {
                        context.unregisterReceiver(it)
                        downloadReceiver = null
                    } catch (e: Exception) {
                        Log.e(TAG, "注销下载广播接收器时出错", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载过程中出错", e)
            trySend(-1) // 发送错误代码
            close(e)
        }
    }

    /**
     * 监控下载进度
     */
    private suspend fun monitorDownloadProgress(
        downloadManager: DownloadManager,
        downloadId: Long,
        onProgress: (Int) -> Unit
    ) {
        var isDownloading = true
        var lastProgress = 0
        
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                    DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                        isDownloading = false
                    }
                }
            } else {
                isDownloading = false
            }
            
            cursor.close()
            delay(500) // 延迟500毫秒再次检查
        }
    }

    /**
     * 安装APK
     */
    private fun installApk(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            // 适配Android 7.0及以上版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 使用FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    DOWNLOAD_AUTHORITY,
                    file
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setDataAndType(uri, APK_MIME_TYPE)
            } else {
                // Android 7.0以下直接使用文件URI
                intent.setDataAndType(Uri.fromFile(file), APK_MIME_TYPE)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "APK安装请求已发送")
        } catch (e: Exception) {
            Log.e(TAG, "启动APK安装失败", e)
        }
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

    /**
     * 获取当前日期
     */
    private fun getCurrentDate(): Date {
        return Date()
    }
} 