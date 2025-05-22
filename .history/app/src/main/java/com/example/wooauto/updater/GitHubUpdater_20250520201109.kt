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
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

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
        private const val GITHUB_USER = "YES-Tech-Computer-Service-Ltd"
        private const val GITHUB_REPO = "WooAutoPrinter" 
        
        // 修改为使用REST API URL
        private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_USER/$GITHUB_REPO/releases"
        private const val RELEASES_URL = "https://github.com/$GITHUB_USER/$GITHUB_REPO/releases"
        private const val DIRECT_DOWNLOAD_URL_PATTERN = "https://github.com/$GITHUB_USER/$GITHUB_REPO/releases/download/%s/app-release.apk"
        
        // GitHub认证相关常量 - 可以从BuildConfig中获取或硬编码（不推荐）
        // 这里使用基本认证（用户名:密码或令牌）
        private const val GITHUB_AUTH_TOKEN = BuildConfig.GITHUB_AUTH_TOKEN // 在build.gradle.kts中定义

        private const val DEFAULT_CHECK_INTERVAL_HOURS = 24
        
        // 下载相关常量
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_FOLDER = "WooAutoPrinter"
        private const val DOWNLOAD_FILE_NAME = "WooAutoPrinter-update.apk"
        private const val DOWNLOAD_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"
        
        // 重试相关常量
        private const val MAX_RETRY_COUNT = 3
    }

    // 当前版本信息
    private val currentVersion = AppVersion.fromBuildConfig()
    
    // 最新版本信息缓存
    private var cachedLatestVersion: AppVersion? = null
    private var lastCheckTime: Long = 0
    
    // 下载相关变量
    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null
    private var downloadRetryCount = 0
    private var _updateState = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    
    // 自动检查设置
    private var autoCheckEnabled = true
    private var checkIntervalHours = DEFAULT_CHECK_INTERVAL_HOURS

    // 更新状态封闭类
    sealed class UpdateStatus {
        object Idle : UpdateStatus()
        data class Downloading(val progress: Int) : UpdateStatus()
        data class Downloaded(val file: File) : UpdateStatus()
        data class Error(val message: String) : UpdateStatus()
    }

    /**
     * 检查更新 - 使用GitHub API直接获取
     * 支持公共和私有仓库
     */
    override suspend fun checkForUpdates(): Flow<UpdateInfo> = callbackFlow {
        Log.d(TAG, "开始检查更新，当前版本: ${currentVersion.toFullVersionString()}")
        
        withContext(Dispatchers.IO) {
            try {
                // 创建HTTP客户端
                val client = OkHttpClient.Builder().build()
                
                // 创建API请求
                val requestBuilder = Request.Builder()
                    .url(GITHUB_API_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                
                // 如果有访问令牌，添加认证头
                if (GITHUB_AUTH_TOKEN.isNotEmpty()) {
                    requestBuilder.header("Authorization", "Basic ${GITHUB_AUTH_TOKEN}")
                }
                
                val request = requestBuilder.build()
                
                // 执行请求
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "GitHub API 请求失败: ${response.code}")
                        if (response.code == 401 || response.code == 403) {
                            Log.e(TAG, "认证失败，请检查访问令牌")
                        } else if (response.code == 404) {
                            Log.e(TAG, "找不到仓库或发布信息，请检查仓库名称")
                        }
                        
                        trySend(UpdateInfo.noUpdateAvailable(currentVersion))
                        return@use
                    }
                    
                    // 解析响应
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        Log.e(TAG, "GitHub API 返回空响应")
                        trySend(UpdateInfo.noUpdateAvailable(currentVersion))
                        return@use
                    }
                    
                    try {
                        // 解析JSON数组，获取最新版本
                        val releasesArray = JSONArray(responseBody)
                        if (releasesArray.length() == 0) {
                            Log.d(TAG, "GitHub上没有发布版本")
                            trySend(UpdateInfo.noUpdateAvailable(currentVersion))
                            return@use
                        }
                        
                        // 获取最新发布（数组中的第一个元素）
                        val latestRelease = releasesArray.getJSONObject(0)
                        val tagName = latestRelease.getString("tag_name")
                        val releaseName = latestRelease.optString("name", tagName)
                        val publishedAt = latestRelease.getString("published_at")
                        val body = latestRelease.optString("body", "")
                        
                        Log.d(TAG, "最新版本: $tagName, 发布于: $publishedAt")
                        
                        // 解析版本
                        val latestVersion = parseVersionFromString(tagName)
                        cachedLatestVersion = latestVersion
                        lastCheckTime = System.currentTimeMillis()
                        
                        // 检查是否有新版本
                        val isUpdateAvailable = currentVersion.isOlderThan(latestVersion)
                        Log.d(TAG, "检查更新结果: 是否有更新=${isUpdateAvailable}, 当前版本=${currentVersion.toVersionString()}, 最新版本=${latestVersion.toVersionString()}")
                        
                        if (isUpdateAvailable) {
                            // 获取下载URL
                            val downloadUrl = getDirectDownloadUrl(latestVersion.toVersionString())
                            
                            // 创建更新信息
                            val updateInfo = UpdateInfo(
                                latestVersion = latestVersion,
                                currentVersion = currentVersion,
                                hasUpdate = true,
                                changelog = body,
                                releaseDate = parseIsoDate(publishedAt),
                                downloadUrl = downloadUrl,
                                isForceUpdate = false
                            )
                            
                            trySend(updateInfo)
                        } else {
                            trySend(UpdateInfo.noUpdateAvailable(currentVersion))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析GitHub API响应失败", e)
                        trySend(UpdateInfo.noUpdateAvailable(currentVersion))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新时出错", e)
                trySend(UpdateInfo.noUpdateAvailable(currentVersion))
            }
        }
        
        awaitClose { 
            // 清理资源
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 解析ISO 8601日期格式
     */
    private fun parseIsoDate(isoDateString: String): Date {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(isoDateString) ?: Date()
        } catch (e: Exception) {
            Log.e(TAG, "解析日期出错: $isoDateString", e)
            Date()
        }
    }

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
            // 重置重试计数
            downloadRetryCount = 0
            
            // 发送初始进度
            trySend(0)
            _updateState.value = UpdateStatus.Downloading(0)
            
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
                                    // 下载完成，验证APK文件
                                    trySend(100)
                                    _updateState.value = UpdateStatus.Downloaded(destinationFile)
                                    
                                    if (verifyApkFile(destinationFile)) {
                                        // 文件验证成功，安装APK
                                        installApk(destinationFile)
                                    } else {
                                        // 文件验证失败，尝试重试
                                        Log.e(TAG, "APK文件验证失败，尝试重试下载")
                                        retryDownloadIfNeeded(updateInfo)
                                        trySend(-1) // 发送错误代码
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    Log.e(TAG, "下载失败，原因: $reason")
                                    retryDownloadIfNeeded(updateInfo)
                                    trySend(-1) // 发送错误代码
                                }
                            }
                        }
                        cursor.close()
                        
                        // 注销广播接收器
                        try {
                            context.unregisterReceiver(this)
                            downloadReceiver = null
                        } catch (e: Exception) {
                            Log.e(TAG, "注销广播接收器失败", e)
                        }
                    }
                }
            }
            
            // 注册广播接收器
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Context.RECEIVER_NOT_EXPORTED
                } else {
                    0
                }
            )
            
            // 保存接收器引用以便后续清理
            downloadReceiver = receiver
            
            // 启动进度监控协程
            withContext(Dispatchers.IO) {
                monitorDownloadProgress(downloadManager, downloadId) { progress ->
                    trySend(progress)
                    _updateState.value = UpdateStatus.Downloading(progress)
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
            _updateState.value = UpdateStatus.Error("下载过程中出错: ${e.message}")
            trySend(-1) // 发送错误代码
            close(e)
        }
    }

    /**
     * 重试下载
     */
    private fun retryDownloadIfNeeded(updateInfo: UpdateInfo) {
        if (downloadRetryCount < MAX_RETRY_COUNT) {
            downloadRetryCount++
            Log.d(TAG, "正在重试下载，第${downloadRetryCount}次")
            _updateState.value = UpdateStatus.Idle // 重置状态
            // 注意：这里不能直接调用downloadAndInstall()，因为它是suspend函数
            // 而是应该通过外部调用者处理重试
        } else {
            Log.e(TAG, "下载重试达到最大次数: $MAX_RETRY_COUNT")
            _updateState.value = UpdateStatus.Error("下载失败，请手动下载")
        }
    }

    /**
     * 验证APK文件
     * 检查文件是否为有效的ZIP格式（APK本质上是ZIP文件）
     */
    private fun verifyApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 100) {
            Log.e(TAG, "APK文件不存在或大小异常: ${file.length()} bytes")
            return false
        }
        
        return try {
            // 检查文件是否为有效的ZIP/APK
            ZipFile(file).use { zipFile ->
                // 检查是否包含必要的APK文件
                val hasManifest = zipFile.getEntry("AndroidManifest.xml") != null
                val hasClasses = zipFile.getEntry("classes.dex") != null
                
                if (!hasManifest || !hasClasses) {
                    Log.e(TAG, "APK文件缺少关键组件: hasManifest=$hasManifest, hasClasses=$hasClasses")
                }
                
                hasManifest && hasClasses
            }
        } catch (e: ZipException) {
            Log.e(TAG, "APK文件不是有效的ZIP格式: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "APK文件验证失败: ${e.message}")
            false
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
            _updateState.value = UpdateStatus.Error("安装失败: ${e.message}")
        }
    }

    /**
     * 获取直接下载URL
     * 确保使用的是直接二进制文件下载链接而非GitHub页面链接
     */
    private fun getDirectDownloadUrl(version: String): String {
        val normalizedVersion = if (version.startsWith("v")) version else "v$version"
        Log.d(TAG, "生成下载URL, 原始版本: $version, 标准化版本: $normalizedVersion")
        return String.format(DIRECT_DOWNLOAD_URL_PATTERN, normalizedVersion)
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
                val doc = Jsoup.connect("$RELEASES_URL/tag/v$targetVersion").get()
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
            // 打印详细的版本解析日志
            Log.d(TAG, "开始解析版本字符串: '$versionString'")
            
            // 解析版本字符串，例如："1.0.0" 或 "1.0.0-beta" 或 "v1.0.0"
            // 移除前缀'v'（如果有）
            val cleanVersionString = versionString.trim().removePrefix("v")
            Log.d(TAG, "清理后的版本字符串: '$cleanVersionString'")
            
            val isBeta = cleanVersionString.contains("-beta", ignoreCase = true)
            val cleanVersion = cleanVersionString.replace("-beta", "").trim()
            val parts = cleanVersion.split(".")
            
            Log.d(TAG, "版本部分: ${parts.joinToString(", ")}")
            
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            
            Log.d(TAG, "解析结果: major=$major, minor=$minor, patch=$patch, isBeta=$isBeta")
            
            return AppVersion(
                major = major,
                minor = minor,
                patch = patch,
                build = 0, // GitHub版本无法获取build号
                versionCode = 0, // GitHub版本无法获取versionCode
                versionName = cleanVersion,
                isBeta = isBeta
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析版本字符串失败: '$versionString'", e)
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
                // 添加更详细的错误日志
                when (error) {
                    AppUpdaterError.NETWORK_NOT_AVAILABLE -> 
                        Log.e(TAG, "网络不可用，无法检查更新")
                    AppUpdaterError.GITHUB_USER_REPO_INVALID -> 
                        Log.e(TAG, "GitHub用户或仓库无效: 用户=$GITHUB_USER, 仓库=$GITHUB_REPO")
                    AppUpdaterError.GITHUB_RATE_LIMIT_REACHED -> 
                        Log.e(TAG, "GitHub API请求达到限制")
                    else -> 
                        Log.e(TAG, "未知错误: $error")
                }
                
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