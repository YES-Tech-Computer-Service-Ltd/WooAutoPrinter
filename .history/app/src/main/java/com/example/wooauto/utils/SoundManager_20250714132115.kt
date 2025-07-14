package com.example.wooauto.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.wooauto.R
import com.example.wooauto.domain.models.SoundSettings
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 声音管理器
 * 负责播放应用中的提示音、控制音量等
 */
@Singleton
class SoundManager @Inject constructor(
    private val context: Context,
    private val settingsRepository: DomainSettingRepository
) {
    companion object {
        private const val TAG = "SoundManager"
    }
    
    // 声音池，用于播放短音效
    private val soundPool: SoundPool by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(4, AudioManager.STREAM_NOTIFICATION, 0)
        }
    }
    
    // 音频管理器
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    // 当前音量和声音类型状态
    private val _currentVolume = MutableStateFlow(70)
    val currentVolume: StateFlow<Int> = _currentVolume.asStateFlow()
    
    private val _currentSoundType = MutableStateFlow(SoundSettings.SOUND_TYPE_DEFAULT)
    val currentSoundType: StateFlow<String> = _currentSoundType.asStateFlow()
    
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()
    
    // 自定义声音URI
    private val _customSoundUri = MutableStateFlow("")
    val customSoundUri: StateFlow<String> = _customSoundUri.asStateFlow()
    
    // 声音资源ID映射
    private val soundResources = mutableMapOf<String, Int>()
    
    // 已加载的声音ID映射
    private val loadedSounds = mutableMapOf<String, Int>()
    
    // 系统通知音效
    private var systemNotificationUri: Uri? = null
    private var useSystemSound = false
    
    // 默认音效ID，如果找不到指定的音效，则使用此ID
    private var defaultSoundId = -1

    // 表示是否已成功加载任何声音
    private var anySoundLoaded = false
    
    // 防止短时间内重复播放声音
    private var lastPlayTime = 0L
    private val MIN_PLAY_INTERVAL = 1000L // 最小播放间隔，单位毫秒
    
    // 批量通知处理
    private var pendingNotifications = 0
    private val notificationLock = Any()
    private val BATCH_NOTIFICATION_DELAY = 500L // 批量通知延迟，单位毫秒
    
    // 防止测试声音连续播放
    private var ringtonePlayer: android.media.Ringtone? = null
    private var testSoundPlaying = false
    
    // 播放自定义声音文件
    private var mediaPlayer: MediaPlayer? = null
    
    // 系统音量管理 - 用于在播放提示音时临时提升音量
    private var originalSystemVolume = -1  // 保存原始音量
    private var isVolumeBoostActive = false  // 是否启用了音量增强
    
    // 初始化声音资源
    init {
        initializeSoundResources()
        loadSettings()
    }
    
    /**
     * 初始化声音资源映射
     */
    private fun initializeSoundResources() {
        try {
            // 我们使用系统声音，不需要加载自定义资源
            Log.d(TAG, "使用系统提供的声音资源")
            
            // 尝试预先获取各种系统声音URI备用
            try {
                systemNotificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                Log.d(TAG, "成功获取系统默认通知音效URI")
            } catch (e: Exception) {
                Log.e(TAG, "获取系统通知音效URI失败", e)
            }
            
            anySoundLoaded = true
            Log.d(TAG, "声音资源初始化完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化声音资源失败", e)
        }
    }
    
    /**
     * 预加载所有声音 - 不再需要，因为我们现在使用系统声音
     */
    private fun preloadSounds() {
        // 不再需要预加载，因为使用系统声音
        anySoundLoaded = true
        Log.d(TAG, "系统声音不需要预加载")
    }
    
    /**
     * 从设置加载声音配置
     */
    private fun loadSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "[声音设置加载] 开始从存储加载声音配置...")
                val settings = settingsRepository.getSoundSettings()
                _currentVolume.value = settings.notificationVolume
                _currentSoundType.value = settings.soundType
                _soundEnabled.value = settings.soundEnabled
                _customSoundUri.value = settings.customSoundUri
                
                Log.d(TAG, "[声音设置加载] 已加载声音设置: 音量=${settings.notificationVolume}, 类型=${settings.soundType}, 启用=${settings.soundEnabled}, 自定义声音=${settings.customSoundUri}")
            } catch (e: Exception) {
                Log.e(TAG, "[声音设置加载] 加载声音设置失败", e)
            }
        }
    }
    
    /**
     * 保存声音设置
     */
    private suspend fun saveSettings() {
        try {
            val settings = SoundSettings(
                notificationVolume = _currentVolume.value,
                soundType = _currentSoundType.value,
                soundEnabled = _soundEnabled.value,
                customSoundUri = _customSoundUri.value
            )
            settingsRepository.saveSoundSettings(settings)
            Log.d(TAG, "已保存声音设置")
        } catch (e: Exception) {
            Log.e(TAG, "保存声音设置失败", e)
        }
    }
    
    /**
     * 设置通知音量
     * @param volume 音量值 (0-150) - 扩展音量范围以适应嘈杂环境
     */
    suspend fun setVolume(volume: Int) {
        val safeVolume = when {
            volume < 0 -> 0
            volume > 150 -> 150  // 扩展音量上限到150%
            else -> volume
        }
        
        _currentVolume.value = safeVolume
        saveSettings()
        
        // 播放测试音效，让用户直接听到音量效果
        Log.d(TAG, "[音效播放] 原因: 设置音量测试 - 新音量: $safeVolume, 声音类型: ${_currentSoundType.value}")
        playSound(_currentSoundType.value)
    }
    
    /**
     * 设置声音类型
     * @param type 声音类型
     */
    suspend fun setSoundType(type: String) {
        if (SoundSettings.getAllSoundTypes().contains(type)) {
            // 先停止当前可能正在播放的声音
            stopCurrentSound()
            
            _currentSoundType.value = type
            saveSettings()
            
            // 播放测试音效，让用户直接听到选择的音效
            Log.d(TAG, "[音效播放] 原因: 设置声音类型测试 - 新类型: $type")
            playSound(type)
        }
    }
    
    /**
     * 设置是否启用声音
     * @param enabled 是否启用
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        saveSettings()
        
        // 如果启用声音，播放一个测试音效
        if (enabled) {
            Log.d(TAG, "[音效播放] 原因: 启用声音设置测试 - 声音类型: ${_currentSoundType.value}")
            playSound(_currentSoundType.value)
        } else {
            Log.d(TAG, "[音效设置] 声音已禁用，不播放测试音效")
        }
    }
    
    /**
     * 设置自定义声音URI
     * @param uri 自定义声音URI
     */
    suspend fun setCustomSoundUri(uri: String) {
        _customSoundUri.value = uri
        saveSettings()
        
        // 如果当前声音类型是自定义，那么播放测试音效
        if (_currentSoundType.value == SoundSettings.SOUND_TYPE_CUSTOM) {
            playSound(_currentSoundType.value)
        }
    }
    
    /**
     * 播放订单提示音
     */
    fun playOrderNotificationSound() {
        synchronized(notificationLock) {
            val currentTime = System.currentTimeMillis()
            
            // 检查是否在短时间内连续播放
            if (currentTime - lastPlayTime < MIN_PLAY_INTERVAL) {
                // 仅增加待处理通知计数，不立即播放
                pendingNotifications++
                Log.d(TAG, "[音效播放] 检测到短时间内连续通知，延迟播放，当前待处理通知: $pendingNotifications")
                
                // 如果是第一个待处理通知，启动延迟处理
                if (pendingNotifications == 1) {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(BATCH_NOTIFICATION_DELAY)
                        processPendingNotifications()
                    }
                }
                return
            }
            
            // 正常播放订单通知声音
            lastPlayTime = currentTime
            pendingNotifications = 0
            
            Log.d(TAG, "[音效播放] 原因: 订单通知 - 声音类型: ${_currentSoundType.value}")
            // 直接使用playSound方法确保声音类型一致性
            playSound(_currentSoundType.value)
            Log.d(TAG, "播放订单通知声音: 类型=${_currentSoundType.value}")
        }
    }
    
    /**
     * 处理待处理的批量通知
     */
    private fun processPendingNotifications() {
        synchronized(notificationLock) {
            if (pendingNotifications > 0) {
                Log.d(TAG, "[音效播放] 原因: 批量通知处理 - 处理 $pendingNotifications 个通知，声音类型: ${_currentSoundType.value}")
                // 无论多少个通知，只播放一次声音
                playSound(_currentSoundType.value)
                pendingNotifications = 0
                lastPlayTime = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * 播放指定类型的提示音 - 确保不同类型使用不同的系统声音
     * @param type 声音类型
     */
    fun playSound(type: String) {
        if (!_soundEnabled.value) {
            Log.d(TAG, "[音效播放] 声音已禁用，不播放提示音")
            return
        }

        Log.d(TAG, "[音效播放] 开始播放声音 - 类型: $type, 音量: ${_currentVolume.value}%")

        // 如果已经有声音在播放，先停止
        stopCurrentSound()
        
        try {
            // 根据声音类型使用不同的系统声音ID或URI
            when(type) {
                SoundSettings.SOUND_TYPE_ALARM -> {
                    Log.d(TAG, "[系统音效] 播放系统闹钟声音")
                    // 使用系统闹钟声音
                    playSystemSound(RingtoneManager.TYPE_ALARM)
                }
                
                SoundSettings.SOUND_TYPE_RINGTONE -> {
                    Log.d(TAG, "[系统音效] 播放系统铃声")
                    // 使用系统铃声
                    playSystemSound(RingtoneManager.TYPE_RINGTONE)
                }
                
                SoundSettings.SOUND_TYPE_DEFAULT -> {
                    Log.d(TAG, "[系统音效] 播放默认通知声音")
                    // 默认通知声音
                    playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                }
                
                SoundSettings.SOUND_TYPE_EVENT -> {
                    Log.d(TAG, "[系统音效] 播放事件声音")
                    // 尝试使用系统事件声音，安卓原生没有这个类型，我们使用特定URI
                    try {
                        val uri = Settings.System.DEFAULT_NOTIFICATION_URI
                        playSpecificSound(uri)
                    } catch (e: Exception) {
                        // 使用备用系统声音
                        playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                    }
                }
                
                SoundSettings.SOUND_TYPE_EMAIL -> {
                    Log.d(TAG, "[系统音效] 播放邮件声音")
                    // 尝试使用邮件声音，安卓原生没有这个类型，我们使用特定URI
                    try {
                        // 在不同Android版本上尝试不同的声音
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Android 8以上使用第二个可用的通知声音
                            val availableSounds = RingtoneManager(context).cursor
                            if (availableSounds.moveToPosition(1)) { // 尝试获取第二个声音
                                val uri = Uri.parse(availableSounds.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" +
                                        availableSounds.getString(RingtoneManager.ID_COLUMN_INDEX))
                                playSpecificSound(uri)
                                availableSounds.close()
                                return
                            }
                            availableSounds.close()
                        }
                        // 备用：使用系统第三个铃声
                        val manager = RingtoneManager(context)
                        manager.setType(RingtoneManager.TYPE_NOTIFICATION)
                        val cursor = manager.cursor
                        
                        // 尝试获取第三个铃声
                        if(cursor.count > 2 && cursor.moveToPosition(2)) {
                            val position = cursor.position
                            val uri = manager.getRingtoneUri(position)
                            playSpecificSound(uri)
                            cursor.close()
                            return
                        }
                        cursor.close()
                        
                        // 如果没有找到额外的铃声，使用默认通知声音
                        playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                    } catch (e: Exception) {
                        Log.e(TAG, "[系统音效] 播放邮件声音失败: ${e.message}")
                        // 备用声音
                        playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                    }
                }
                
                SoundSettings.SOUND_TYPE_CUSTOM -> {
                    Log.d(TAG, "[自定义音效] 播放自定义声音")
                    // 播放自定义音频文件
                    if (_customSoundUri.value.isNotEmpty()) {
                        try {
                            // 直接使用保存的文件路径
                            val filePath = _customSoundUri.value
                            playCustomSound(filePath)
                            Log.d(TAG, "[自定义音效] 播放自定义声音: $filePath")
                        } catch (e: Exception) {
                            Log.e(TAG, "[自定义音效] 播放自定义声音失败: ${e.message}", e)
                            // 失败时使用默认声音
                            playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                        }
                    } else {
                        Log.d(TAG, "[自定义音效] 自定义声音URI为空，使用默认声音")
                        playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                    }
                }
                
                else -> {
                    Log.d(TAG, "[系统音效] 未知类型，使用默认通知声音")
                    // 未知类型使用默认通知声音
                    playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[音效播放] 播放声音失败: ${e.message}", e)
            try {
                // 兜底使用默认通知声音
                playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
            } catch (e: Exception) {
                Log.e(TAG, "[音效播放] 播放备用声音也失败: ${e.message}", e)
            }
        }
    }
    
    /**
     * 播放系统声音
     * @param ringtoneType 铃声类型
     */
    private fun playSystemSound(ringtoneType: Int) {
        try {
            Log.d(TAG, "[系统音效] 开始播放系统声音 - 类型: $ringtoneType, 音量: ${_currentVolume.value}%")
            
            // 停止之前的声音
            stopCurrentSound()
            
            // 如果音量超过100%，临时提升系统音量
            boostSystemVolume()
            
            val notificationUri = RingtoneManager.getDefaultUri(ringtoneType)
            Log.d(TAG, "[系统音效] 获取到系统声音URI: $notificationUri")
            
            ringtonePlayer = RingtoneManager.getRingtone(context, notificationUri)
            
            // 尝试设置音量 (部分设备可能不支持)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // 支持超过100%的音量增强 - 最大1.5倍增益
                    val volumeGain = when {
                        _currentVolume.value <= 100 -> _currentVolume.value / 100f
                        else -> (_currentVolume.value / 100f).coerceAtMost(1.5f)
                    }
                    ringtonePlayer?.volume = volumeGain
                    Log.d(TAG, "[系统音效] 成功设置系统声音音量: $volumeGain (原始值: ${_currentVolume.value}%)")
                } catch (e: Exception) {
                    Log.w(TAG, "[系统音效] 设置系统声音音量失败: ${e.message}")
                }
            }
            
            ringtonePlayer?.play()
            testSoundPlaying = true
            Log.d(TAG, "[系统音效] 系统声音播放开始: 类型=$ringtoneType")
            
            // 添加自动停止计时器，防止声音一直循环播放
            CoroutineScope(Dispatchers.Main).launch {
                delay(5000) // 5秒后自动停止
                stopCurrentSound()
                restoreSystemVolume()  // 恢复系统音量
            }
        } catch (e: Exception) {
            Log.e(TAG, "[系统音效] 播放系统声音失败", e)
            // 回退到最基本的系统通知声音
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtonePlayer = RingtoneManager.getRingtone(context, fallbackUri)
                ringtonePlayer?.play()
                Log.d(TAG, "[系统音效] 使用备用系统通知声音")
                
                // 同样添加自动停止
                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000) // 5秒后自动停止
                    stopCurrentSound()
                    restoreSystemVolume()  // 恢复系统音量
                }
            } catch (e: Exception) {
                Log.e(TAG, "[系统音效] 播放备用系统声音也失败", e)
            }
        }
    }
    
    /**
     * 播放指定URI的系统声音
     */
    private fun playSpecificSound(uri: Uri) {
        try {
            Log.d(TAG, "[特定音效] 开始播放特定URI声音: $uri, 音量: ${_currentVolume.value}%")
            
            // 停止之前的声音
            stopCurrentSound()
            
            // 如果音量超过100%，临时提升系统音量
            boostSystemVolume()
            
            ringtonePlayer = RingtoneManager.getRingtone(context, uri)
            
            // 尝试设置音量
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // 支持超过100%的音量增强 - 最大1.5倍增益
                    val volumeGain = when {
                        _currentVolume.value <= 100 -> _currentVolume.value / 100f
                        else -> (_currentVolume.value / 100f).coerceAtMost(1.5f)
                    }
                    ringtonePlayer?.volume = volumeGain
                    Log.d(TAG, "[特定音效] 成功设置特定声音音量: $volumeGain (原始值: ${_currentVolume.value}%)")
                } catch (e: Exception) {
                    Log.w(TAG, "[特定音效] 设置声音音量失败: ${e.message}")
                }
            }
            
            ringtonePlayer?.play()
            testSoundPlaying = true
            Log.d(TAG, "[特定音效] 特定URI声音播放开始: $uri")
            
            // 添加自动停止计时器，防止声音一直循环播放
            CoroutineScope(Dispatchers.Main).launch {
                delay(5000) // 5秒后自动停止
                stopCurrentSound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[特定音效] 播放特定URI声音失败: ${e.message}", e)
            // 播放备用声音
            playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
    
    /**
     * 播放自定义声音文件
     */
    private fun playCustomSound(filePath: String) {
        try {
            Log.d(TAG, "[自定义音效] 开始播放自定义声音文件: $filePath, 音量: ${_currentVolume.value}%")
            
            // 先停止当前声音
            stopCurrentSound()
            
            // 停止可能正在播放的MediaPlayer
            mediaPlayer?.release()
            
            // 创建新的MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                // 支持超过100%的音量增强 - 最大1.5倍增益
                val volumeGain = when {
                    _currentVolume.value <= 100 -> _currentVolume.value / 100f
                    else -> (_currentVolume.value / 100f).coerceAtMost(1.5f)
                }
                setVolume(volumeGain, volumeGain)
                prepare()
                start()
                
                // 播放完成后释放资源
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    Log.d(TAG, "[自定义音效] 自定义声音播放完成，已释放资源")
                }
            }
            
            testSoundPlaying = true
            Log.d(TAG, "[自定义音效] 自定义声音文件播放开始: $filePath")
            
            // 添加自动停止计时器，防止声音一直循环播放
            CoroutineScope(Dispatchers.Main).launch {
                delay(5000) // 5秒后自动停止
                mediaPlayer?.release()
                mediaPlayer = null
                testSoundPlaying = false
                Log.d(TAG, "[自定义音效] 自定义声音自动停止")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[自定义音效] 播放自定义声音文件失败: ${e.message}", e)
            // 播放备用声音
            playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
    
    /**
     * 停止当前正在播放的声音
     */
    private fun stopCurrentSound() {
        try {
            ringtonePlayer?.stop()
            ringtonePlayer = null
            
            // 也停止MediaPlayer
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            testSoundPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "停止声音失败", e)
        }
    }
    
    /**
     * 临时提升系统音量 - 在播放提示音时使用
     */
    private fun boostSystemVolume() {
        try {
            if (isVolumeBoostActive) return  // 避免重复提升
            
            // 获取当前音量
            originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            
            // 如果应用音量设置超过100%，则提升系统音量
            if (_currentVolume.value > 100) {
                val boostRatio = (_currentVolume.value - 100) / 100f  // 计算提升比例
                val targetVolume = (originalSystemVolume + (maxVolume - originalSystemVolume) * boostRatio).toInt()
                val boostedVolume = targetVolume.coerceAtMost(maxVolume)
                
                // 设置提升后的音量
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, boostedVolume, 0)
                isVolumeBoostActive = true
                
                Log.d(TAG, "[系统音量增强] 原音量: $originalSystemVolume, 提升至: $boostedVolume (应用音量: ${_currentVolume.value}%)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[系统音量增强] 提升系统音量失败", e)
        }
    }
    
    /**
     * 恢复原始系统音量
     */
    private fun restoreSystemVolume() {
        try {
            if (!isVolumeBoostActive || originalSystemVolume == -1) return
            
            // 恢复原始音量
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalSystemVolume, 0)
            isVolumeBoostActive = false
            
            Log.d(TAG, "[系统音量增强] 已恢复原始音量: $originalSystemVolume")
        } catch (e: Exception) {
            Log.e(TAG, "[系统音量增强] 恢复系统音量失败", e)
        }
    }
    
    /**
     * 释放资源，在应用退出时调用
     */
    fun release() {
        try {
            stopCurrentSound()
            restoreSystemVolume()  // 确保恢复系统音量
            soundPool.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放声音资源失败", e)
        }
    }
    
    /**
     * 停止所有正在播放的声音
     * 对外公开的方法，用于UI层调用
     */
    fun stopAllSounds() {
        stopCurrentSound()
        Log.d(TAG, "已停止所有正在播放的声音")
    }
} 