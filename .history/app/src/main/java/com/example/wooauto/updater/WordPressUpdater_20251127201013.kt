package com.example.wooauto.updater

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
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
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import androidx.core.net.toUri

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
        private const val DIAG = "WordPressUpdaterDiag"
        private const val PREFS_NAME = "wordpress_updater"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_CHECK_INTERVAL = "check_interval_hours"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val UPDATE_HOST = "yestech.ca"
    }
    
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 仅用于 yestech.ca：使用信任所有证书的 SSLSocketFactory (简化版)
    // 之前的自定义 PKIX 验证在某些 Android 环境下导致 Chain validation failed
    private val trustAllSslSocketFactory: SSLSocketFactory get() {
        Log.d(TAG, "Initializing TrustAll SSLSocketFactory for $UPDATE_HOST")
        return buildTrustAllSocketFactory()
    }

    // 对应的 TrustManager
    private val trustAllTrustManager: X509TrustManager get() {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                // 信任所有，不抛出异常
                Log.d(TAG, "[TLS] Trusting server certificate for update check")
            }
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    }
    
    /**
     * 检查是否有存储权限
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 不需要存储权限，使用应用专属目录
            true
        } else {
            // Android 9 及以下需要存储权限
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 获取下载目录和文件名
     */
    private fun getDownloadLocation(fileName: String): Pair<File, Boolean> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用应用专属外部存储
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val file = File(downloadDir, fileName)
            Pair(file, true) // true表示使用FileProvider
        } else if (hasStoragePermission()) {
            // Android 9及以下，有存储权限时使用公共目录
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val file = File(downloadDir, fileName)
            Pair(file, false) // false表示使用DownloadManager的Uri
        } else {
            // Android 9及以下，没有存储权限时使用应用内部存储
            Log.w(TAG, "没有存储权限，使用应用内部存储目录")
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val file = File(downloadDir, fileName)
            Pair(file, true) // true表示使用FileProvider
        }
    }
    
    /**
     * 清理旧的APK文件
     */
    private fun cleanupOldApkFiles() {
        try {
            val downloadDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || !hasStoragePermission()) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            
            downloadDir?.listFiles { file ->
                file.name.startsWith("wooauto-update-") && file.name.endsWith(".apk")
            }?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "已删除旧的APK文件: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理旧APK文件失败", e)
        }
    }
    
    /**
     * 检查更新
     */
    override suspend fun checkForUpdates(): Flow<UpdateInfo> = flow {
        try {
            val currentVersion = getCurrentVersion()
            Log.d(TAG, "开始检查更新 - 当前版本: ${currentVersion.toVersionString()} (versionCode: ${currentVersion.versionCode})")
            Log.d(DIAG, "[Check] LocalVersion: major=${currentVersion.major}, minor=${currentVersion.minor}, patch=${currentVersion.patch}, build=${currentVersion.build}, code=${currentVersion.versionCode}, name=${currentVersion.versionName}, isBeta=${currentVersion.isBeta}")
            
            val (baseUrl, token) = config.getCurrentConfig()
            // 在API请求中传递当前版本，让服务器能够正确比较
            val apiUrl = "$baseUrl/check?token=$token&client_version=${currentVersion.toVersionString()}"
            Log.d(TAG, "[Updater] 开始检查: url=$apiUrl, current=${currentVersion.toFullVersionString()}")
            Log.d(DIAG, "[Check] UsingConfig: baseUrl=$baseUrl, tokenMasked=${maskToken(token)}")
            Log.d(DIAG, "[Check] FullRequestUrl(maskedToken)=${apiUrl.replace(token, maskToken(token))}")
            val response = makeApiRequest(apiUrl)
            
            if (response != null) {
                Log.d(TAG, "[Updater] API响应长度: ${response.length}")
                Log.d(DIAG, "[Check] RawResponsePreview=${preview(response)}")
                val jsonResponse = JSONObject(response)
                val success = jsonResponse.optBoolean("success", false)
                
                if (success) {
                    val serverVersion = jsonResponse.optString("current_version", "")
                    val downloadUrl = jsonResponse.optString("download_url", "")
                    val fileSize = jsonResponse.optLong("file_size", 0L)
                    val serverHasUpdate = jsonResponse.optBoolean("has_update", false)
                    
                    Log.d(TAG, "[Updater] 服务器返回: version=$serverVersion, has_update=$serverHasUpdate, size=$fileSize")
                    Log.d(DIAG, "[Check] ParsedJSON: success=$success, current_version=$serverVersion, has_update=$serverHasUpdate, file_size=$fileSize, download_url_present=${downloadUrl.isNotEmpty()}")
                    
                    if (serverVersion.isNotEmpty()) {
                        val latestVersion = parseVersionString(serverVersion)
                        Log.d(TAG, "[Updater] 解析服务器版本: ${latestVersion.toFullVersionString()}")
                        
                        // 详细的版本比较日志
                        val isCurrentOlder = currentVersion.isOlderThan(latestVersion)
                        Log.d(TAG, "[Updater] 比较: current=${currentVersion.toFullVersionString()}, server=${latestVersion.toFullVersionString()}, isCurrentOlder=$isCurrentOlder, serverHasUpdate=$serverHasUpdate")
                        Log.d(DIAG, "[Check] CompareDetail: current={maj=${currentVersion.major},min=${currentVersion.minor},pat=${currentVersion.patch},b=${currentVersion.build},beta=${currentVersion.isBeta}} vs server={maj=${latestVersion.major},min=${latestVersion.minor},pat=${latestVersion.patch},b=${latestVersion.build},beta=${latestVersion.isBeta}} => older=$isCurrentOlder")
                        
                        // 更稳健的判定：服务器声明有更新时优先提示；否则用本地兜底比较
                        val needsUpdate = serverHasUpdate || isCurrentOlder
                        
                        val updateInfo = UpdateInfo(
                            latestVersion = latestVersion,
                            currentVersion = currentVersion,
                            hasUpdate = needsUpdate,
                            changelog = if (needsUpdate) "新版本 $serverVersion 可用" else "当前已是最新版本",
                            downloadUrl = downloadUrl,
                            fileSize = if (fileSize > 0) fileSize / 1024 else null // 转换为KB
                        )
                        
                        Log.d(TAG, "[Updater] 判定结果: needsUpdate=$needsUpdate")
                        Log.d(DIAG, "[Check] Decision: serverHasUpdate=$serverHasUpdate, isCurrentOlder=$isCurrentOlder, final=$needsUpdate")
                        emit(updateInfo)
                    } else {
                        Log.d(TAG, "服务器返回的版本号为空，认为无更新")
                        Log.w(DIAG, "[Check] Empty serverVersion in success response, emitting noUpdate")
                        emit(UpdateInfo.noUpdateAvailable(currentVersion))
                    }
                } else {
                    Log.d(TAG, "服务器API返回失败，认为无更新")
                    Log.w(DIAG, "[Check] success=false, response=${preview(response)}")
                    emit(UpdateInfo.noUpdateAvailable(currentVersion))
                }
            } else {
                Log.d(TAG, "无法连接到更新服务器，认为无更新")
                Log.w(DIAG, "[Check] Response is null (network/timeout/non-200). See network logs below.")
                emit(UpdateInfo.noUpdateAvailable(currentVersion, isNetworkError = true))
            }
            
            // 更新最后检查时间
            preferences.edit {
                putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            Log.e(DIAG, "[Check] Exception during checkForUpdates: ${e.message}", e)
            emit(UpdateInfo.noUpdateAvailable(getCurrentVersion(), isNetworkError = true))
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
            val url = "$baseUrl/check?token=$token"
            Log.d(DIAG, "[Latest] RequestUrl(maskedToken)=${url.replace(token, maskToken(token))}")
            val response = makeApiRequest(url)
            if (response != null) {
                val jsonResponse = JSONObject(response)
                val success = jsonResponse.optBoolean("success", false)
                if (success) {
                    val serverVersion = jsonResponse.optString("current_version", "")
                    Log.d(DIAG, "[Latest] success=true, current_version=$serverVersion, raw=${preview(response)}")
                    if (serverVersion.isNotEmpty()) {
                        parseVersionString(serverVersion)
                    } else null
                } else {
                    Log.w(DIAG, "[Latest] success=false, raw=${preview(response)}")
                    null
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "获取最新版本失败", e)
            Log.e(DIAG, "[Latest] Exception: ${e.message}", e)
            null
        }
    }
    
    /**
     * 下载并安装更新
     */
    override suspend fun downloadAndInstall(updateInfo: UpdateInfo): Flow<Int> = flow {
        try {
            // 先清理旧的APK文件
            cleanupOldApkFiles()
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "wooauto-update-${updateInfo.latestVersion.toVersionString()}.apk"
            
            // 根据Android版本和权限状态选择合适的下载目录
            val (downloadLocation, useFileProvider) = getDownloadLocation(fileName)
            
            // 如果文件已存在，先删除
            if (downloadLocation.exists()) {
                if (downloadLocation.delete()) {
                    Log.d(TAG, "已删除已存在的文件: ${downloadLocation.absolutePath}")
                } else {
                    Log.w(TAG, "无法删除已存在的文件: ${downloadLocation.absolutePath}")
                }
            }
            
            val request = DownloadManager.Request(updateInfo.downloadUrl.toUri())
                .setTitle("WooAuto 更新")
                .setDescription("正在下载版本 ${updateInfo.latestVersion.toVersionString()}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationUri(Uri.fromFile(downloadLocation))
                
            Log.d(TAG, "下载位置: ${downloadLocation.absolutePath}")
            Log.d(TAG, "下载URL: ${updateInfo.downloadUrl}")
            
            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "开始下载，下载ID: $downloadId")
            
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
                            
                            // 检查文件是否真的存在
                            if (!downloadLocation.exists()) {
                                Log.e(TAG, "下载显示成功但文件不存在: ${downloadLocation.absolutePath}")
                                return@flow
                            }
                            
                            // 安装APK
                            val downloadUri = if (useFileProvider) {
                                // 使用FileProvider方式
                                Log.d(TAG, "下载完成，文件路径: ${downloadLocation.absolutePath}, 大小: ${downloadLocation.length()}")
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", downloadLocation)
                            } else {
                                // 使用DownloadManager方式
                                downloadManager.getUriForDownloadedFile(downloadId)
                            }
                            Log.d(TAG, "准备安装APK: $downloadUri")
                            installApk(downloadUri)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            val errorMessage = getDownloadErrorMessage(reason)
                            Log.e(TAG, "下载失败，原因代码: $reason - $errorMessage")
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Log.w(TAG, "下载暂停，原因代码: $reason")
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
     * 获取下载错误信息
     */
    private fun getDownloadErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载 (文件系统错误或存储问题)"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "未找到存储设备"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
            DownloadManager.ERROR_FILE_ERROR -> "文件系统错误"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "未处理的HTTP代码"
            DownloadManager.ERROR_UNKNOWN -> "未知错误"
            else -> "错误代码: $reason"
        }
    }
    
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
        // 简单退避重试：最多4次，1s/2s/3s退避；并提升读超时，减少弱网下误判
        val maxRetries = 4
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxRetries) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                // 若是 https 且目标为 yestech.ca，则应用自定义 TLS（信任所有）
                if (connection is HttpsURLConnection && url.host.equals(UPDATE_HOST, ignoreCase = true)) {
                    try {
                        connection.sslSocketFactory = trustAllSslSocketFactory
                        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                        Log.d(DIAG, "[TLS] Applied TrustAll SSL to $UPDATE_HOST")
                    } catch (e: Exception) {
                        Log.w(DIAG, "[TLS] Failed to apply custom SSL, fallback to default: ${e.message}", e)
                    }
                }
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000 // 10s 更快地失败以便重试
                connection.readTimeout = 35000 // 35s 给服务器更多响应时间
                connection.setRequestProperty("User-Agent", "WooAuto-Android")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Connection", "close")
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    Log.d(TAG, "[Updater] 请求成功: code=$responseCode, len=${response.length}")
                    Log.d(DIAG, "[Net] OK url=${preview(urlString)}, bodyPreview=${preview(response)}")
                    return@withContext response
                } else {
                    val errorPreview = try {
                        val es = connection.errorStream
                        if (es != null) {
                            val er = BufferedReader(InputStreamReader(es))
                            val text = er.readText()
                            er.close()
                            preview(text)
                        } else "<no-error-body>"
                    } catch (_: Exception) { "<error-reading-error-body>" }
                    Log.w(TAG, "[Updater] 非200响应: code=$responseCode")
                    Log.w(DIAG, "[Net] Non200 url=${preview(urlString)}, code=$responseCode, errorPreview=$errorPreview")
                    return@withContext null
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "[Updater] 请求异常(第${attempt + 1}次): ${e.message}")
                Log.w(DIAG, "[Net] Exception attempt=${attempt + 1} url=${preview(urlString)} msg=${e.message}", e)
                if (attempt < maxRetries - 1) {
                    // 线性退避 1s/2s/3s，并引入少量抖动
                    val base = 1000L * (attempt + 1)
                    val jitter = (200L..600L).random()
                    try { kotlinx.coroutines.delay(base + jitter) } catch (_: Exception) {}
                }
            } finally {
                attempt++
            }
        }
        Log.e(TAG, "[Updater] 多次重试失败: ${lastError?.message}", lastError)
        Log.e(DIAG, "[Net] Exhausted retries for url=${preview(urlString)} last=${lastError?.message}", lastError)
        null
    }

    // ===== 自定义 TLS（信任所有） =====
    private fun buildTrustAllSocketFactory(): SSLSocketFactory {
        val trustManagers = arrayOf<TrustManager>(trustAllTrustManager)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, SecureRandom())
        return sslContext.socketFactory
    }
    
    /* 移除复杂的 RevocationDisabledTrustManager 以修复 Chain validation failed */
    
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
            
            // 如果版本号完全相同，使用当前版本的build和versionCode
            // 否则，假设服务器版本的build和versionCode更高
            val (build, versionCode) = if (major == currentVersion.major && 
                                          minor == currentVersion.minor && 
                                          patch == currentVersion.patch) {
                // 版本号相同，保持当前的build和versionCode，避免错误的更新提示
                Pair(currentVersion.build, currentVersion.versionCode)
            } else {
                // 版本号不同，假设是更新的版本
                Pair(currentVersion.build + 1, currentVersion.versionCode + 1)
            }
            
            Log.d(DIAG, "[Parse] input=$versionString -> major=$major, minor=$minor, patch=$patch, isBeta=$isBeta, build=$build, code=$versionCode")
            return AppVersion(
                major = major,
                minor = minor,
                patch = patch,
                build = build,
                versionCode = versionCode,
                versionName = versionString,
                isBeta = isBeta
            )
        } catch (e: Exception) {
            Log.e(TAG, "版本字符串解析失败: $versionString", e)
            Log.e(DIAG, "[Parse] Failed: $versionString -> ${e.message}", e)
            return currentVersion
        }
    }
    
    /**
     * 安装APK
     */
    private fun installApk(apkUri: Uri) {
        try {
            Log.d(TAG, "开始安装APK: $apkUri")
            
            // 对于Android 9及以下，使用简化的安装方式
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                installApkLegacy(apkUri)
                return
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            
            // 检查是否有应用可以处理这个Intent
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (activities.isNotEmpty()) {
                Log.d(TAG, "找到 ${activities.size} 个应用可以处理APK安装")
                context.startActivity(intent)
                Log.d(TAG, "APK安装Intent已启动")
            } else {
                Log.e(TAG, "没有找到可以处理APK安装的应用")
                installApkLegacy(apkUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动APK安装失败", e)
            installApkLegacy(apkUri)
        }
    }
    
    /**
     * 传统方式安装APK (用于Android 9及以下或备用方案)
     */
    private fun installApkLegacy(apkUri: Uri) {
        try {
            Log.d(TAG, "使用传统方式安装APK")
            
            // 最简单的安装方式
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(intent)
            Log.d(TAG, "传统安装方式Intent已启动")
            
        } catch (e: Exception) {
            Log.e(TAG, "传统安装方式失败", e)
            
            // 最后的备用方案：尝试打开文件管理器
            try {
                Log.d(TAG, "尝试最后备用方案：打开文件管理器")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "*/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "所有安装方案都失败", e2)
            }
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

    /**
     * 掩码token，避免日志暴露完整敏感信息
     */
    private fun maskToken(token: String): String {
        if (token.isEmpty()) return "<empty>"
        if (token.length <= 8) return "****${token.last()}"
        val head = token.substring(0, 4)
        val tail = token.substring(token.length - 4)
        return "$head****$tail"
    }

    /**
     * 输出预览（前后截断），避免在日志中打印过长内容
     */
    private fun preview(text: String?, limit: Int = 300): String {
        if (text == null) return "<null>"
        val trimmed = text.replace("\n", "\\n")
        return if (trimmed.length <= limit) trimmed else trimmed.substring(0, limit) + "…(${trimmed.length})"
    }
} 