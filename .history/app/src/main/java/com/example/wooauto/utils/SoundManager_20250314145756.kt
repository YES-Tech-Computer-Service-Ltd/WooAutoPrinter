package com.example.wooauto.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.util.Log
import com.example.wooauto.R
import com.example.wooauto.domain.models.SoundSettings
import com.example.wooauto.domain.repositories.DomainSettingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    
    // 初始化声音资源
    init {
        initializeSoundResources()
        loadSettings()
    }
    
    /**
     * 初始化声音资源映射
     */
    private fun initializeSoundResources() {
        soundResources[SoundSettings.SOUND_TYPE_DEFAULT] = R.raw.notification_default
        soundResources[SoundSettings.SOUND_TYPE_BELL] = R.raw.notification_bell
        soundResources[SoundSettings.SOUND_TYPE_CASH] = R.raw.notification_cash
        soundResources[SoundSettings.SOUND_TYPE_ALERT] = R.raw.notification_alert
        soundResources[SoundSettings.SOUND_TYPE_CHIME] = R.raw.notification_chime
        
        // 预加载所有声音
        preloadSounds()
    }
    
    /**
     * 预加载所有声音
     */
    private fun preloadSounds() {
        soundResources.forEach { (type, resId) ->
            try {
                val soundId = soundPool.load(context, resId, 1)
                loadedSounds[type] = soundId
            } catch (e: Exception) {
                Log.e(TAG, "加载声音失败: $type", e)
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
        playSound(_currentSoundType.value)
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
            playSound(_currentSoundType.value)
        }
    }
    
    /**
     * 播放订单提示音
     */
    fun playOrderNotificationSound() {
        playSound(_currentSoundType.value)
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
            val soundId = loadedSounds[type] ?: loadedSounds[SoundSettings.SOUND_TYPE_DEFAULT] ?: return
            
            // 计算音量 (将百分比转换为0.0-1.0)
            val volume = _currentVolume.value / 100f
            
            // 播放声音
            soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
            
            Log.d(TAG, "播放声音: 类型=$type, 音量=$volume")
        } catch (e: Exception) {
            Log.e(TAG, "播放声音失败", e)
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
} 