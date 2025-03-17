package com.example.wooauto.utils

import android.content.Context
import android.content.res.Resources
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 资源加载助手类
 * 用于处理声音资源的提取和预加载
 */
class ResourceLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "ResourceLoader"
    }
    
    /**
     * 检查并提取原始声音资源到缓存目录
     * 这样SoundPool可以更容易地加载它们
     */
    suspend fun extractSoundResources(): Map<String, String> = withContext(Dispatchers.IO) {
        val resourceMap = mutableMapOf<String, String>()
        
        try {
            val soundFiles = arrayOf(
                "notification_default.mp3",
                "notification_bell.mp3",
                "notification_cash.mp3",
                "notification_alert.mp3",
                "notification_chime.mp3"
            )
            
            // 创建应用的声音缓存目录
            val cacheDir = File(context.cacheDir, "sounds")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // 清理旧文件
            cacheDir.listFiles()?.forEach { it.delete() }
            
            // 尝试从assets目录复制声音文件
            val assetManager = context.assets
            soundFiles.forEach { filename ->
                try {
                    // 创建目标文件
                    val outputFile = File(cacheDir, filename)
                    // 从assets读取文件并写入缓存目录
                    assetManager.open("sounds/$filename").use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // 添加到资源映射
                    val soundName = filename.substringBefore(".mp3")
                                            .removePrefix("notification_")
                    resourceMap[soundName] = outputFile.absolutePath
                    Log.d(TAG, "声音资源提取成功: $soundName -> ${outputFile.absolutePath}")
                    
                    // 验证文件是否可播放
                    validateSoundFile(outputFile.absolutePath)
                } catch (e: IOException) {
                    // 如果assets目录中没有文件，使用系统默认铃声
                    Log.e(TAG, "声音资源提取失败: $filename, ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "声音资源提取过程中出错", e)
        }
        
        return@withContext resourceMap
    }
    
    /**
     * 验证声音文件是否可以播放
     */
    private fun validateSoundFile(filePath: String): Boolean {
        val player = MediaPlayer()
        return try {
            player.setDataSource(filePath)
            player.prepare()
            Log.d(TAG, "声音文件验证成功: $filePath, 时长: ${player.duration}ms")
            player.release()
            true
        } catch (e: Exception) {
            Log.e(TAG, "声音文件验证失败: $filePath, ${e.message}")
            player.release()
            false
        }
    }
    
    /**
     * 获取资源ID
     */
    fun getResourceId(name: String, type: String): Int {
        val packageName = context.packageName
        val resources = context.resources
        return resources.getIdentifier(name, type, packageName)
    }
} 