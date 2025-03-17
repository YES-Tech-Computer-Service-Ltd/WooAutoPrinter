package com.example.wooauto.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
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
    
    // 声音资源ID映射
    private val soundResources = mutableMapOf<String, Int>()
    
    // 已加载的声音ID映射
    private val loadedSounds = mutableMapOf<String, Int>()
    
    // 声音加载状态跟踪
    private val loadedState = mutableMapOf<Int, Boolean>()
    
    // 声音文件路径映射
    private val soundPaths = mutableMapOf<String, String>()
    
    // 资源加载器
    private val resourceLoader = ResourceLoader(context)
    
    // 默认音效ID，如果找不到指定的音效，则使用此ID
    private var defaultSoundId = -1

    // 表示是否已成功加载任何声音
    private var anySoundLoaded = false
    
    // 表示初始化是否完成
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    
    // 初始化声音资源
    init {
        CoroutineScope(Dispatchers.IO).launch {
            // 首先提取音频资源
            soundPaths.putAll(resourceLoader.extractSoundResources())
            
            // 然后初始化声音管理器
            initializeSoundResources()
            loadSettings()
            // 设置声音加载监听器
            setupSoundLoadListener()
            
            // 延迟标记为初始化完成，给足够时间让声音加载
            delay(1000) // 等待1秒，确保声音有时间加载
            _initialized.value = true
            Log.d(TAG, "SoundManager初始化完成，可以安全播放声音")
        }
    }
    
    /**
     * 设置声音加载监听器
     */
    private fun setupSoundLoadListener() {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                // 声音加载成功
                loadedState[sampleId] = true
                Log.d(TAG, "声音ID: $sampleId 加载完成")
                
                // 确认所有声音是否都已加载完成
                checkAllSoundsLoaded()
            } else {
                // 声音加载失败
                loadedState[sampleId] = false
                Log.e(TAG, "声音ID: $sampleId 加载失败，状态码: $status")
            }
        }
    }
    
    /**
     * 检查所有声音是否已加载完成
     */
    private fun checkAllSoundsLoaded() {
        // 如果已经初始化，不需要再次检查
        if (_initialized.value) return
        
        // 检查所有已注册的声音是否都加载完成
        val allLoaded = loadedSounds.values.all { soundId -> 
            soundId <= 0 || loadedState[soundId] == true 
        }
        
        if (allLoaded && loadedSounds.isNotEmpty()) {
            _initialized.value = true
            Log.d(TAG, "所有声音资源加载完成，SoundManager初始化完成")
            
            // 记录各声音类型加载状态
            loadedSounds.forEach { (type, soundId) ->
                val status = if (soundId <= 0) "资源无效" 
                             else if (loadedState[soundId] == true) "已加载" 
                             else "加载失败"
                Log.d(TAG, "声音状态 - $type: $status (ID: $soundId)")
            }
        }
    }
    
    /**
     * 初始化声音资源映射
     */
    private fun initializeSoundResources() {
        try {
            // 首先尝试使用提取的音频文件路径
            if (soundPaths.isNotEmpty()) {
                Log.d(TAG, "使用从assets提取的声音文件: ${soundPaths.size}个")
                
                // 从缓存目录加载声音
                soundPaths.forEach { (type, path) ->
                    try {
                        // 加载声音文件到SoundPool
                        val soundId = soundPool.load(path, 1)
                        loadedSounds[type] = soundId
                        loadedState[soundId] = false // 初始状态为未加载完成
                        
                        // 设置默认音效ID
                        if (type == SoundSettings.SOUND_TYPE_DEFAULT || defaultSoundId == -1) {
                            defaultSoundId = soundId
                        }
                        
                        anySoundLoaded = true
                        Log.d(TAG, "开始加载声音文件: $type (路径: $path, 声音ID: $soundId)")
                    } catch (e: Exception) {
                        Log.e(TAG, "加载声音文件失败: $type (路径: $path)", e)
                    }
                }
            } else {
                // 如果没有预提取的声音，尝试从raw资源加载
                Log.d(TAG, "没有从assets提取的声音文件，尝试从raw资源加载")
                
                // 检查资源是否存在并记录结果
                val resMap = mapOf(
                    SoundSettings.SOUND_TYPE_DEFAULT to R.raw.notification_default,
                    SoundSettings.SOUND_TYPE_BELL to R.raw.notification_bell,
                    SoundSettings.SOUND_TYPE_CASH to R.raw.notification_cash,
                    SoundSettings.SOUND_TYPE_ALERT to R.raw.notification_alert,
                    SoundSettings.SOUND_TYPE_CHIME to R.raw.notification_chime
                )
                
                // 验证资源文件是否存在
                resMap.forEach { (type, resId) ->
                    try {
                        val resourceName = context.resources.getResourceEntryName(resId)
                        soundResources[type] = resId
                        Log.d(TAG, "声音资源验证成功: $type -> $resourceName (ID: $resId)")
                    } catch (e: Exception) {
                        Log.e(TAG, "声音资源无法访问: $type (ID: $resId), 错误: ${e.message}")
                        // 设置为0表示资源不可用
                        soundResources[type] = 0
                    }
                }
                
                // 预加载所有声音
                preloadSounds()
            }
            
            Log.d(TAG, "声音资源初始化完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化声音资源失败", e)
        }
    }
    
    /**
     * 预加载所有声音
     */
    private fun preloadSounds() {
        // 记录有多少声音需要加载
        val totalSounds = soundResources.count { (_, resId) -> resId != 0 }
        var loadedCount = 0
        
        soundResources.forEach { (type, resId) ->
            if (resId != 0) {
                try {
                    val resourceName = context.resources.getResourceEntryName(resId)
                    val soundId = soundPool.load(context, resId, 1)
                    loadedSounds[type] = soundId
                    loadedState[soundId] = false // 初始状态为未加载完成
                    
                    // 设置默认音效ID
                    if (type == SoundSettings.SOUND_TYPE_DEFAULT || defaultSoundId == -1) {
                        defaultSoundId = soundId
                    }
                    
                    loadedCount++
                    anySoundLoaded = true
                    Log.d(TAG, "开始加载声音: $type (资源ID: $resId, 声音ID: $soundId) [$loadedCount/$totalSounds]")
                } catch (e: Exception) {
                    Log.e(TAG, "加载声音失败: $type (资源ID: $resId)", e)
                }
            } else {
                Log.w(TAG, "声音资源未找到: $type")
            }
        }
        
        // 如果没有加载任何声音，尝试加载系统的默认通知音效
        if (!anySoundLoaded) {
            try {
                // 使用系统铃声作为备选
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                Log.d(TAG, "没有可用的声音资源，将使用系统默认通知音效: $notification")
                
                // 如果没有任何声音，提前标记为初始化完成，使用系统声音
                if (loadedSounds.isEmpty()) {
                    _initialized.value = true
                    Log.d(TAG, "没有声音资源需要加载，使用系统默认声音，标记初始化完成")
                }
            } catch (e: Exception) {
                Log.e(TAG, "无法加载任何声音资源", e)
            }
        }
    }
    
    /**
     * 从设置加载声音配置
     */
    private fun loadSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.getSoundSettings()
                _currentVolume.value = settings.notificationVolume
                _currentSoundType.value = settings.soundType
                _soundEnabled.value = settings.soundEnabled
                
                Log.d(TAG, "已加载声音设置: 音量=${settings.notificationVolume}, 类型=${settings.soundType}, 启用=${settings.soundEnabled}")
            } catch (e: Exception) {
                Log.e(TAG, "加载声音设置失败", e)
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
                soundEnabled = _soundEnabled.value
            )
            settingsRepository.saveSoundSettings(settings)
            Log.d(TAG, "已保存声音设置")
        } catch (e: Exception) {
            Log.e(TAG, "保存声音设置失败", e)
        }
    }
    
    /**
     * 设置通知音量
     * @param volume 音量值 (0-100)
     */
    suspend fun setVolume(volume: Int) {
        val safeVolume = when {
            volume < 0 -> 0
            volume > 100 -> 100
            else -> volume
        }
        
        _currentVolume.value = safeVolume
        saveSettings()
        
        // 播放测试音效，让用户直接听到音量效果
        playSoundWithRetry(_currentSoundType.value)
    }
    
    /**
     * 设置声音类型
     * @param type 声音类型
     */
    suspend fun setSoundType(type: String) {
        if (SoundSettings.getAllSoundTypes().contains(type)) {
            _currentSoundType.value = type
            saveSettings()
            
            // 播放测试音效，让用户直接听到选择的音效
            playSoundWithRetry(type)
        }
    }
    
    /**
     * 设置是否启用声音
     * @param enabled 是否启用
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        saveSettings()
        
        // 如果启用声音，播放当前类型的测试音效
        if (enabled) {
            playSoundWithRetry(_currentSoundType.value)
        }
    }
    
    /**
     * 播放订单提示音
     */
    fun playOrderNotificationSound() {
        playSoundWithRetry(_currentSoundType.value)
    }
    
    /**
     * 播放指定类型的提示音
     * @param type 声音类型
     */
    fun playSound(type: String) {
        if (!_soundEnabled.value) {
            Log.d(TAG, "声音已禁用，不播放提示音")
            return
        }
        
        try {
            if (!anySoundLoaded) {
                Log.w(TAG, "没有可用的声音资源")
                return
            }
            
            // 尝试获取指定类型的声音ID，如果不存在则使用默认声音ID
            val soundId = loadedSounds[type] ?: loadedSounds[SoundSettings.SOUND_TYPE_DEFAULT] ?: defaultSoundId
            
            // 如果没有有效的声音ID，则直接返回
            if (soundId <= 0) {
                Log.w(TAG, "找不到有效的声音资源，无法播放")
                return
            }
            
            // 检查声音是否已加载完成
            if (loadedState[soundId] != true) {
                Log.w(TAG, "声音尚未加载完成，无法播放: $type (声音ID: $soundId)")
                return
            }
            
            // 计算音量 (将百分比转换为0.0-1.0)
            val volume = _currentVolume.value / 100f
            
            // 播放声音
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
            
            Log.d(TAG, "播放声音: 类型=$type, 音量=$volume, 声音ID=$soundId")
        } catch (e: Exception) {
            Log.e(TAG, "播放声音失败", e)
        }
    }
    
    /**
     * 检查并播放声音（带重试逻辑）
     * 用于测试和预览，会尝试等待声音加载完成
     */
    fun playSoundWithRetry(type: String, maxAttempts: Int = 5) {
        if (!_soundEnabled.value) {
            Log.d(TAG, "声音已禁用，不播放提示音")
            return
        }
        
        val soundId = loadedSounds[type] ?: loadedSounds[SoundSettings.SOUND_TYPE_DEFAULT] ?: defaultSoundId
        
        if (soundId <= 0) {
            Log.w(TAG, "找不到有效的声音资源，尝试系统声音")
            playSystemNotificationSound()
            return
        }
        
        // 如果声音已加载，直接播放
        if (_initialized.value && loadedState[soundId] == true) {
            playSound(type)
            return
        }
        
        // 声音未加载或管理器未初始化完成，使用协程尝试等待并重试
        CoroutineScope(Dispatchers.IO).launch {
            var attempts = 0
            // 等待初始化完成
            if (!_initialized.value) {
                Log.d(TAG, "等待SoundManager初始化完成...")
                var initWaitCount = 0
                while (!_initialized.value && initWaitCount < 10) {
                    delay(200)
                    initWaitCount++
                }
                if (!_initialized.value) {
                    Log.w(TAG, "SoundManager初始化超时，使用备用声音机制")
                    playSystemNotificationSound()
                    return@launch
                }
            }
            
            // 等待声音加载完成
            while (attempts < maxAttempts) {
                if (loadedState[soundId] == true) {
                    playSound(type)
                    return@launch
                }
                attempts++
                Log.d(TAG, "等待声音加载完成，尝试 $attempts/$maxAttempts: $type (声音ID: $soundId)")
                delay(500) // 等待500毫秒再次尝试
            }
            
            Log.w(TAG, "等待声音加载超时，使用备用声音机制: $type (声音ID: $soundId)")
            playSystemNotificationSound()
        }
    }
    
    /**
     * 使用系统通知声音作为备用
     */
    private fun playSystemNotificationSound() {
        try {
            // 在主线程上执行，因为MediaPlayer需要
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = RingtoneManager.getRingtone(context, notification)
                    val volume = _currentVolume.value / 100f
                    
                    // 设置音量
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.volume = volume
                    }
                    
                    ringtone.play()
                    Log.d(TAG, "使用系统默认通知声音作为备用，音量: $volume")
                } catch (e: Exception) {
                    Log.e(TAG, "播放系统通知声音失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建系统通知声音失败", e)
        }
    }
    
    /**
     * 释放资源，在应用退出时调用
     */
    fun release() {
        try {
            soundPool.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放声音资源失败", e)
        }
    }
    
    /**
     * 检查是否在使用备用声音模式（系统声音）
     */
    fun isFallbackMode(): Boolean {
        return !anySoundLoaded || loadedSounds.isEmpty() || 
               loadedSounds.values.all { soundId -> loadedState[soundId] != true }
    }
} 