package com.example.wooauto.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.wooauto.data.local.WordPressUpdaterConfig
import com.example.wooauto.updater.model.AppVersion
import com.example.wooauto.updater.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WordPress更新器实现
 * 基于WordPress网站API进行应用更新
 */
@Singleton
class WordPressUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: WordPressUpdaterConfig
) : UpdaterInterface {
    
    companion object {
        private const val TAG = "WordPressUpdater"
        private const val PREFS_NAME = "wordpress_updater"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_CHECK_INTERVAL = "check_interval_hours"
        private const val KEY_LAST_CHECK = "last_check_time"
    }
    
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 检查更新
     */
    override suspend fun checkForUpdates(): Flow<UpdateInfo> = flow {
        try {
            val currentVersion = getCurrentVersion()
            val (baseUrl, token) = config.getCurrentConfig()
            val response = makeApiRequest("$baseUrl/check?token=$token")
            
            if (response != null) {
                val jsonResponse = JSONObject(response)
                val success = jsonResponse.optBoolean("success", false)
                
                if (success) {
                    val serverVersion = jsonResponse.optString("current_version", "")
                    val hasUpdate = jsonResponse.optBoolean("has_update", false)
                    val downloadUrl = jsonResponse.optString("download_url", "")
                    val fileSize = jsonResponse.optLong("file_size", 0L)
                    
                    if (hasUpdate && serverVersion.isNotEmpty()) {
                        val latestVersion = parseVersionString(serverVersion)
                        val updateInfo = UpdateInfo(
                            latestVersion = latestVersion,
                            currentVersion = currentVersion,
                            hasUpdate = currentVersion.isOlderThan(latestVersion),
                            changelog = "新版本 $serverVersion 可用",
                            downloadUrl = downloadUrl,
                            fileSize = if (fileSize > 0) fileSize / 1024 else null // 转换为KB
                        )
                        emit(updateInfo)
                    } else {
                        emit(UpdateInfo.noUpdateAvailable(currentVersion))
                    }
                } else {
                    emit(UpdateInfo.noUpdateAvailable(currentVersion))
                }
            } else {
                emit(UpdateInfo.noUpdateAvailable(currentVersion))
            }
            
            // 更新最后检查时间
            preferences.edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
                
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            emit(UpdateInfo.noUpdateAvailable(getCurrentVersion()))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 获取当前版本
     */
    override fun getCurrentVersion(): AppVersion {
        return AppVersion.fromBuildConfig()
    }
    
    /**
     * 获取最新版本
     */
    override suspend fun getLatestVersion(): AppVersion? {
        return try {
            val (baseUrl, token) = config.getCurrentConfig()
            val response = makeApiRequest("$baseUrl/check?token=$token")
            if (response != null) {
                val jsonResponse = JSONObject(response)
                val success = jsonResponse.optBoolean("success", false)
                if (success) {
                    val serverVersion = jsonResponse.optString("current_version", "")
                    if (serverVersion.isNotEmpty()) {
                        parseVersionString(serverVersion)
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "获取最新版本失败", e)
            null
        }
    }
    
    /**
     * 下载并安装更新
     */
    override suspend fun downloadAndInstall(updateInfo: UpdateInfo): Flow<Int> = flow {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "wooauto-update-${updateInfo.latestVersion.toVersionString()}.apk"
            
            val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
                .setTitle("WooAuto 更新")
                .setDescription("正在下载版本 ${updateInfo.latestVersion.toVersionString()}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            // 根据Android版本选择不同的下载目录
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用应用专属目录
                val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                request.setDestinationUri(Uri.fromFile(downloadsDir))
            } else {
                // Android 9 及以下使用公共下载目录
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            
            val downloadId = downloadManager.enqueue(request)
            
            // 监控下载进度
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    
                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            
                            if (total > 0) {
                                val progress = (downloaded * 100 / total).toInt()
                                emit(progress)
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            emit(100)
                            downloading = false
                            
                            // 安装APK
                            val downloadUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                // Android 10+ 需要使用FileProvider
                                val localFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
                            } else {
                                downloadManager.getUriForDownloadedFile(downloadId)
                            }
                            installApk(downloadUri)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Log.e(TAG, "下载失败，原因代码: $reason")
                        }
                    }
                }
                cursor.close()
                
                if (downloading) {
                    kotlinx.coroutines.delay(1000) // 每秒检查一次进度
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载安装失败", e)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 是否有更新
     */
    override suspend fun hasUpdate(): Boolean {
        val latestVersion = getLatestVersion()
        return latestVersion?.let { 
            getCurrentVersion().isOlderThan(it)
        } ?: false
    }
    
    /**
     * 获取更新日志
     */
    override suspend fun getChangelog(version: String?): String {
        // WordPress API暂不支持获取详细更新日志，返回基本信息
        val latestVersion = getLatestVersion()
        return if (latestVersion != null) {
            "新版本 ${latestVersion.toVersionString()} 已发布！\n\n请查看应用内更新说明了解详细改进内容。"
        } else {
            "暂无更新日志信息"
        }
    }
    
    /**
     * 设置自动检查更新
     */
    override fun setAutoCheckEnabled(enabled: Boolean, intervalHours: Int) {
        preferences.edit()
            .putBoolean(KEY_AUTO_CHECK, enabled)
            .putInt(KEY_CHECK_INTERVAL, intervalHours)
            .apply()
        
        Log.i(TAG, "自动检查更新: $enabled, 间隔: ${intervalHours}小时")
    }
    
    /**
     * 发起API请求
     */
    private suspend fun makeApiRequest(urlString: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "WooAuto-Android")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                response
            } else {
                Log.e(TAG, "API请求失败，响应码: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "API请求异常", e)
            null
        }
    }
    
    /**
     * 解析版本字符串
     */
    private fun parseVersionString(versionString: String): AppVersion {
        val currentVersion = getCurrentVersion()
        
        try {
            // 解析如 "1.2.3" 或 "1.2.3-beta" 格式的版本号
            val parts = versionString.split("-")
            val versionParts = parts[0].split(".")
            val isBeta = parts.size > 1 && parts[1] == "beta"
            
            val major = versionParts.getOrNull(0)?.toIntOrNull() ?: currentVersion.major
            val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: currentVersion.minor
            val patch = versionParts.getOrNull(2)?.toIntOrNull() ?: currentVersion.patch
            
            return AppVersion(
                major = major,
                minor = minor,
                patch = patch,
                build = currentVersion.build + 1, // 假设服务器版本的构建号比当前版本高
                versionCode = currentVersion.versionCode + 1,
                versionName = versionString,
                isBeta = isBeta
            )
        } catch (e: Exception) {
            Log.e(TAG, "版本字符串解析失败: $versionString", e)
            return currentVersion
        }
    }
    
    /**
     * 安装APK
     */
    private fun installApk(apkUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "启动APK安装失败", e)
        }
    }
    
    /**
     * 获取自动检查设置
     */
    fun isAutoCheckEnabled(): Boolean {
        return preferences.getBoolean(KEY_AUTO_CHECK, true)
    }
    
    /**
     * 获取检查间隔
     */
    fun getCheckIntervalHours(): Int {
        return preferences.getInt(KEY_CHECK_INTERVAL, 24)
    }
    
    /**
     * 获取最后检查时间
     */
    fun getLastCheckTime(): Long {
        return preferences.getLong(KEY_LAST_CHECK, 0L)
    }
} 