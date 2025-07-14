package com.example.wooauto.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.SoundPool
// 添加突破音量限制的新导入
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.DynamicsProcessing
import android.media.AudioTrack
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 声音管理器 - 极限音量增强版
 * 负责播放应用中的提示音、控制音量等
 * 使用多种技术突破系统音量限制
 */
@Singleton
class SoundManager @Inject constructor(
    private val context: Context,
    private val settingsRepository: DomainSettingRepository
) {
    companion object {
        private const val TAG = "SoundManager"
        // 音频突破增强常量
        private const val MAX_CONCURRENT_SOUNDS = 6  // 最大同时播放音频数量（音频叠加）
        private const val AUDIO_BOOST_MULTIPLIER = 3.0f  // 音频数据预处理放大倍数
        private const val MIN_PLAY_INTERVAL = 500L // 最小播放间隔，防止过度频繁播放
        private const val BATCH_NOTIFICATION_DELAY = 1500L // 批量通知延迟
    }
    
    // 声音池，用于播放短音效
    private val soundPool: SoundPool by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE) // 使用铃声用途获得更高优先级
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // 强制音频可听性
                .build()
            SoundPool.Builder()
                .setMaxStreams(MAX_CONCURRENT_SOUNDS) // 支持多音频同时播放
                .setAudioAttributes(audioAttributes)
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(MAX_CONCURRENT_SOUNDS, AudioManager.STREAM_NOTIFICATION, 0)
        }
    }
    
    // 音频管理器
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    // 振动器管理器 - 用于在嘈杂环境下提供触觉反馈
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
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
    
    // 音频增强处理 - 用于提高音频响度和清晰度
    private var useAudioEnhancement = true  // 是否启用音频增强
    
    // 重复播放配置 - 用于嘈杂环境下确保提示音被听到
    private var repeatPlayCount = 1  // 重复播放次数 (1=不重复, 2=重复1次, 3=重复2次)
    private var repeatPlayInterval = 1000L  // 重复播放间隔 (毫秒)
    private var currentRepeatCount = 0  // 当前重复计数器
    private var currentPlayingSoundType = ""  // 当前播放的声音类型
    
    // 振动配置 - 用于在嘈杂环境下提供额外的触觉提醒
    private var enableVibration = true  // 是否启用振动
    private var vibrationIntensity = 2  // 振动强度 (1=轻, 2=中, 3=强)
    
    // 音频突破限制的增强组件
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var audioTrack: AudioTrack? = null
    private val concurrentPlayers = mutableListOf<MediaPlayer>()
    
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
     * @param volume 音量值 (0-1000) - 极大扩展音量范围以适应任何嘈杂环境
     */
    suspend fun setVolume(volume: Int) {
        val safeVolume = when {
            volume < 0 -> 0
            volume > 1000 -> 1000  // 扩展音量上限到1000%
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

        // 根据音量级别自动配置重复播放 - 1000%音量范围适配
        repeatPlayCount = when {
            _currentVolume.value >= 750 -> 2  // 很响/极响级别：重复1次 (总共播放2次)
            _currentVolume.value >= 500 -> 2  // 响亮级别：重复1次 (总共播放2次)
            else -> 1  // 中等及以下：不重复 (总共播放1次)
        }
        currentRepeatCount = 0  // 重置重复计数器

        Log.d(TAG, "[音效播放] 开始播放声音 - 类型: $type, 音量级别: ${_currentVolume.value}%, 重复次数: $repeatPlayCount")

        // 记录当前播放的声音类型
        currentPlayingSoundType = type

        // 如果已经有声音在播放，先停止
        stopCurrentSound()
        
        // 立即执行振动提醒 - 与声音同时进行
        performVibration()
        
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
     * 播放系统声音 - 极限音量增强版
     * @param ringtoneType 铃声类型
     */
    private fun playSystemSound(ringtoneType: Int) {
        try {
            Log.d(TAG, "[极限音效] 开始播放极限增强系统声音 - 类型: $ringtoneType, 音量: ${_currentVolume.value}%")
            
            // 停止之前的声音
            stopCurrentSound()
            
            // 如果音量超过100%，临时提升系统音量
            boostSystemVolume()
            
            val notificationUri = RingtoneManager.getDefaultUri(ringtoneType)
            Log.d(TAG, "[极限音效] 获取到系统声音URI: $notificationUri")
            
            // 根据音量级别选择播放策略
            when {
                _currentVolume.value >= 750 -> {
                    // 极响/很响级别：使用多音频叠加技术
                    playWithMultipleAudioLayers(notificationUri)
                }
                _currentVolume.value >= 500 -> {
                    // 响亮级别：使用音频增强技术
                    playWithAudioEnhancement(notificationUri)
                }
                _currentVolume.value >= 250 -> {
                    // 中等级别：使用标准增强
                    playWithStandardEnhancement(notificationUri)
                }
                else -> {
                    // 低音量：使用标准播放
                    playStandardSound(notificationUri)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[极限音效] 播放极限增强声音失败", e)
            // 回退到标准播放
            playFallbackSound()
        }
    }
    
    /**
     * 多音频叠加播放 - 突破单音频音量限制
     * 通过同时播放多个相同音频实现音量叠加效果
     */
    private fun playWithMultipleAudioLayers(uri: Uri) {
        try {
            Log.d(TAG, "[多音频叠加] 开始多音频叠加播放，目标音量: ${_currentVolume.value}%")
            
            // 计算需要的音频层数
            val layerCount = when {
                _currentVolume.value >= 1000 -> 6  // 极响：6层叠加
                _currentVolume.value >= 750 -> 4   // 很响：4层叠加
                else -> 3                          // 默认：3层叠加
            }
            
            // 清理之前的播放器
            stopAllConcurrentPlayers()
            
            // 创建多个 MediaPlayer 同时播放
            for (i in 0 until layerCount) {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    
                    setDataSource(context, uri)
                    
                    // 轻微的延迟启动，创造更厚重的音效
                    val delayMs = i * 50L
                    
                    setOnPreparedListener { mediaPlayer ->
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(delayMs)
                            
                            try {
                                // 检查MediaPlayer状态，避免在错误状态下设置音量
                                if (mediaPlayer.isPlaying || !concurrentPlayers.contains(this@apply)) {
                                    Log.w(TAG, "[多音频叠加] MediaPlayer状态异常，跳过第${i + 1}层音频")
                                    return@launch
                                }
                                
                                // 每层音频使用最大音量，通过叠加达到突破效果
                                mediaPlayer.setVolume(1.0f, 1.0f)
                                
                                // 添加音频增强效果
                                setupAudioEffects(mediaPlayer.audioSessionId)
                                
                                mediaPlayer.start()
                                Log.d(TAG, "[多音频叠加] 启动第${i + 1}层音频，延迟: ${delayMs}ms")
                            } catch (e: IllegalStateException) {
                                Log.e(TAG, "[多音频叠加] MediaPlayer状态错误，第${i + 1}层音频启动失败: ${e.message}")
                            } catch (e: Exception) {
                                Log.e(TAG, "[多音频叠加] 第${i + 1}层音频启动出错: ${e.message}")
                            }
                        }
                    }
                    
                    setOnCompletionListener {
                        concurrentPlayers.remove(this)
                        this.release()
                    }
                    
                    prepareAsync()
                }
                
                concurrentPlayers.add(player)
            }
            
            testSoundPlaying = true
            
            // 自动停止和重复播放处理
            CoroutineScope(Dispatchers.Main).launch {
                delay(3500) // 稍微延长播放时间
                stopAllConcurrentPlayers()
                handleRepeatPlay()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[多音频叠加] 多音频叠加播放失败", e)
            playStandardSound(uri)
        }
    }
    
    /**
     * 音频增强播放 - 使用音频处理器增强音量
     */
    private fun playWithAudioEnhancement(uri: Uri) {
        try {
            Log.d(TAG, "[音频增强] 开始音频增强播放，目标音量: ${_currentVolume.value}%")
            
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                setDataSource(context, uri)
                setVolume(1.0f, 1.0f)
                
                setOnPreparedListener { mediaPlayer ->
                    // 设置高级音频增强效果
                    setupAdvancedAudioEffects(mediaPlayer.audioSessionId)
                    mediaPlayer.start()
                    Log.d(TAG, "[音频增强] 音频增强播放启动")
                }
                
                setOnCompletionListener {
                    this.release()
                    testSoundPlaying = false
                }
                
                prepareAsync()
            }
            
            ringtonePlayer = null // 不使用 ringtonePlayer，改用 MediaPlayer
            testSoundPlaying = true
            
            // 自动停止和重复播放处理
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                player.stop()
                player.release()
                handleRepeatPlay()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[音频增强] 音频增强播放失败", e)
            playStandardSound(uri)
        }
    }
    
    /**
     * 标准增强播放
     */
    private fun playWithStandardEnhancement(uri: Uri) {
        try {
            ringtonePlayer = RingtoneManager.getRingtone(context, uri)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val volumeGain = (_currentVolume.value / 100f).coerceAtMost(2.5f)
                    ringtonePlayer?.volume = volumeGain
                    Log.d(TAG, "[标准增强] 设置音量增益: $volumeGain")
                } catch (e: Exception) {
                    Log.w(TAG, "[标准增强] 设置音量失败: ${e.message}")
                }
            }
            
            ringtonePlayer?.play()
            testSoundPlaying = true
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                stopCurrentSound()
                handleRepeatPlay()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[标准增强] 标准增强播放失败", e)
            playStandardSound(uri)
        }
    }
    
    /**
     * 标准播放
     */
    private fun playStandardSound(uri: Uri) {
        try {
            ringtonePlayer = RingtoneManager.getRingtone(context, uri)
            ringtonePlayer?.play()
            testSoundPlaying = true
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                stopCurrentSound()
                handleRepeatPlay()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[标准播放] 标准播放失败", e)
            playFallbackSound()
        }
    }
    
    /**
     * 设置音频增强效果
     */
    private fun setupAudioEffects(audioSessionId: Int) {
        try {
            // 清理之前的效果器
            loudnessEnhancer?.release()
            
            // 音量增强器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    // 设置极限增益（以毫贝为单位）
                    val gainMb = when {
                        _currentVolume.value >= 1000 -> 3000  // 极响：30dB增益
                        _currentVolume.value >= 750 -> 2500   // 很响：25dB增益
                        _currentVolume.value >= 500 -> 2000   // 响亮：20dB增益
                        else -> 1500                          // 中等：15dB增益
                    }
                    
                    setTargetGain(gainMb)
                    setEnabled(true)
                    Log.d(TAG, "[音频效果] LoudnessEnhancer 设置增益: ${gainMb}mB")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[音频效果] 设置音频效果失败", e)
        }
    }
    
    /**
     * 设置高级音频增强效果
     */
    private fun setupAdvancedAudioEffects(audioSessionId: Int) {
        try {
            setupAudioEffects(audioSessionId)
            
            // 动态范围处理器（Android 9+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    dynamicsProcessing?.release()
                    
                    val config = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        1, // 声道数
                        true, // 预均衡器启用
                        1, // 预均衡器频段数
                        true, // 多频带压缩器启用
                        1, // 多频带压缩器频段数
                        true, // 后均衡器启用
                        1, // 后均衡器频段数
                        true  // 限幅器启用
                    ).build()
                    
                    dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config).apply {
                        setEnabled(true)
                        Log.d(TAG, "[高级音频效果] DynamicsProcessing 已启用")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "[高级音频效果] DynamicsProcessing 设置失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[高级音频效果] 设置高级音频效果失败", e)
        }
    }
    
    /**
     * 停止所有并发播放器
     */
    private fun stopAllConcurrentPlayers() {
        Log.d(TAG, "[音频管理] 正在停止 ${concurrentPlayers.size} 个并发播放器")
        
        // 创建副本避免并发修改
        val playersToStop = concurrentPlayers.toList()
        concurrentPlayers.clear()
        
        playersToStop.forEach { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                    Log.d(TAG, "[音频管理] 停止播放器")
                }
                player.release()
                Log.d(TAG, "[音频管理] 释放播放器")
            } catch (e: Exception) {
                Log.w(TAG, "[音频管理] 停止并发播放器失败: ${e.message}")
            }
        }
    }
    
    /**
     * 播放回退声音
     */
    private fun playFallbackSound() {
        try {
            val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtonePlayer = RingtoneManager.getRingtone(context, fallbackUri)
            ringtonePlayer?.play()
            Log.d(TAG, "[回退播放] 使用备用系统通知声音")
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                stopCurrentSound()
                handleRepeatPlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[回退播放] 播放备用声音也失败", e)
        }
    }
    
    /**
     * 播放指定URI的系统声音 - 极限音量增强版
     */
    private fun playSpecificSound(uri: Uri) {
        try {
            Log.d(TAG, "[特定音效] 开始播放极限增强特定声音: $uri, 音量: ${_currentVolume.value}%")
            
            // 停止之前的声音
            stopCurrentSound()
            
            // 如果音量超过100%，临时提升系统音量
            boostSystemVolume()
            
            // 根据音量级别选择播放策略（与 playSystemSound 一致）
            when {
                _currentVolume.value >= 750 -> {
                    // 极响/很响级别：使用多音频叠加技术
                    playWithMultipleAudioLayers(uri)
                }
                _currentVolume.value >= 500 -> {
                    // 响亮级别：使用音频增强技术
                    playWithAudioEnhancement(uri)
                }
                _currentVolume.value >= 250 -> {
                    // 中等级别：使用标准增强
                    playWithStandardEnhancement(uri)
                }
                else -> {
                    // 低音量：使用标准播放
                    playStandardSound(uri)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[特定音效] 播放极限增强特定声音失败", e)
            // 回退到标准播放
            playFallbackSound()
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
            
            // 如果音量超过100%，临时提升系统音量
            boostSystemVolume()
            
            // 停止可能正在播放的MediaPlayer
            mediaPlayer?.release()
            
            // 创建新的MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                
                // 音频增强配置 - 优化通知音频播放
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)  // 使用铃声用法以获得更高音量
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)  // 声音化内容类型
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)  // 强制可听性
                        .build()
                )
                
                // 支持超过100%的音量增强 - 支持1000%极限音量范围
                val volumeGain = when {
                    _currentVolume.value <= 100 -> _currentVolume.value / 100f
                    else -> {
                        val baseGain = _currentVolume.value / 100f
                        val enhancedGain = if (useAudioEnhancement) {
                            // 音频增强模式：支持1000%极限音量
                            when {
                                _currentVolume.value >= 1000 -> (baseGain * 1.0f).coerceAtMost(10.0f) // 极响：最大10倍增益
                                _currentVolume.value >= 750 -> (baseGain * 0.9f).coerceAtMost(8.0f)  // 很响：最大8倍增益
                                _currentVolume.value >= 500 -> (baseGain * 0.8f).coerceAtMost(5.0f)  // 响亮：最大5倍增益
                                _currentVolume.value >= 250 -> (baseGain * 0.7f).coerceAtMost(3.0f)  // 中等：最大3倍增益
                                else -> (baseGain * 0.6f).coerceAtMost(2.0f)                        // 轻：最大2倍增益
                            }
                        } else {
                            // 非增强模式也支持极高音量
                            when {
                                _currentVolume.value >= 1000 -> baseGain.coerceAtMost(8.0f)  // 极响：最大8倍增益
                                _currentVolume.value >= 750 -> baseGain.coerceAtMost(6.0f)   // 很响：最大6倍增益  
                                _currentVolume.value >= 500 -> baseGain.coerceAtMost(4.0f)   // 响亮：最大4倍增益
                                _currentVolume.value >= 250 -> baseGain.coerceAtMost(2.5f)   // 中等：最大2.5倍增益
                                else -> baseGain.coerceAtMost(2.0f)                          // 轻：最大2倍增益
                            }
                        }
                        enhancedGain
                    }
                }
                setVolume(volumeGain, volumeGain)
                Log.d(TAG, "[音频增强] 设置音量增益: $volumeGain (原始: ${_currentVolume.value}%, 增强: $useAudioEnhancement)")
                
                prepare()
                start()
                
                // 播放完成后释放资源
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    restoreSystemVolume()  // 恢复系统音量
                    Log.d(TAG, "[自定义音效] 自定义声音播放完成，已释放资源")
                }
            }
            
            testSoundPlaying = true
            Log.d(TAG, "[自定义音效] 自定义声音文件播放开始: $filePath")
            
            // 添加自动停止计时器，防止声音一直循环播放，并处理重复播放
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000) // 3秒后自动停止 (缩短等待时间以便重复播放)
                mediaPlayer?.release()
                mediaPlayer = null
                testSoundPlaying = false
                Log.d(TAG, "[自定义音效] 自定义声音自动停止")
                
                // 处理重复播放逻辑
                handleRepeatPlay()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[自定义音效] 播放自定义声音文件失败: ${e.message}", e)
            // 播放备用声音
            playSystemSound(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
    
    /**
     * 停止当前正在播放的声音 - 极限音量增强版
     */
    private fun stopCurrentSound() {
        try {
            // 停止传统 ringtone 播放器
            ringtonePlayer?.stop()
            ringtonePlayer = null
            
            // 停止 MediaPlayer
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            // 停止所有并发音频播放器
            stopAllConcurrentPlayers()
            
            // 清理音频增强效果
            cleanupAudioEffects()
            
            testSoundPlaying = false
            
            // 恢复系统音量
            restoreSystemVolume()
            
            Log.d(TAG, "[音效控制] 所有声音已停止，音频效果已清理")
        } catch (e: Exception) {
            Log.e(TAG, "[音效控制] 停止声音失败", e)
        }
    }
    
    /**
     * 清理音频增强效果
     */
    private fun cleanupAudioEffects() {
        try {
            loudnessEnhancer?.let {
                it.setEnabled(false)
                it.release()
                loudnessEnhancer = null
                Log.d(TAG, "[音频效果] LoudnessEnhancer 已清理")
            }
            
            dynamicsProcessing?.let {
                it.setEnabled(false)
                it.release()
                dynamicsProcessing = null
                Log.d(TAG, "[音频效果] DynamicsProcessing 已清理")
            }
            
            audioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                    it.release()
                }
                audioTrack = null
                Log.d(TAG, "[音频效果] AudioTrack 已清理")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "[音频效果] 清理音频效果时出错", e)
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
            
            // 如果应用音量设置超过100%，则提升系统音量 - 支持1000%极限范围
            if (_currentVolume.value > 100) {
                // 计算基础提升比例 - 支持1000%音量级别
                val boostRatio = when {
                    _currentVolume.value >= 1000 -> 1.0f  // 极响级别：最大提升
                    _currentVolume.value >= 750 -> 0.95f  // 很响级别：95%提升
                    _currentVolume.value >= 500 -> 0.85f  // 响亮级别：85%提升
                    _currentVolume.value >= 250 -> 0.7f   // 中等级别：70%提升
                    _currentVolume.value >= 100 -> 0.5f   // 轻级别：50%提升
                    else -> (_currentVolume.value - 100) / 100f  // 其他：渐进提升
                }
                
                // 音频增强模式下使用更积极的提升策略
                val enhancedBoostRatio = if (useAudioEnhancement) {
                    (boostRatio * 1.1f).coerceAtMost(1.0f)  // 增强模式下额外提升10%
                } else {
                    boostRatio
                }
                
                val targetVolume = (originalSystemVolume + (maxVolume - originalSystemVolume) * enhancedBoostRatio).toInt()
                val boostedVolume = targetVolume.coerceAtMost(maxVolume)
                
                // 设置提升后的音量
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, boostedVolume, 0)
                isVolumeBoostActive = true
                
                Log.d(TAG, "[系统音量增强] 原音量: $originalSystemVolume, 提升至: $boostedVolume (应用音量级别: ${_currentVolume.value}%, 提升比例: $enhancedBoostRatio, 增强: $useAudioEnhancement)")
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
     * 处理重复播放逻辑
     */
    private fun handleRepeatPlay() {
        currentRepeatCount++
        
        if (currentRepeatCount < repeatPlayCount) {
            // 还需要重复播放
            Log.d(TAG, "[重复播放] 准备第${currentRepeatCount + 1}次播放 (共${repeatPlayCount}次)")
            
            CoroutineScope(Dispatchers.Main).launch {
                delay(repeatPlayInterval)  // 等待间隔时间
                
                // 播放下一次
                Log.d(TAG, "[重复播放] 开始第${currentRepeatCount + 1}次播放")
                playSound(currentPlayingSoundType)
            }
        } else {
            // 播放完成，恢复系统音量
            Log.d(TAG, "[重复播放] 所有重复播放完成 (${repeatPlayCount}次)")
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)  // 延迟1秒后恢复音量，确保声音播放完毕
                restoreSystemVolume()
            }
        }
    }
    
    /**
     * 执行振动提醒 - 配合音频提示使用
     */
    private fun performVibration() {
        if (!enableVibration) {
            Log.d(TAG, "[振动提醒] 振动已禁用，跳过振动")
            return
        }
        
        try {
            // 检查设备是否支持振动
            if (!vibrator.hasVibrator()) {
                Log.d(TAG, "[振动提醒] 设备不支持振动功能")
                return
            }
            
            // 根据音量级别自动调整振动模式 - 适配1000%音量范围
            val vibrationPattern = when {
                _currentVolume.value >= 1000 -> {
                    // 极响级别：超级强烈振动 - 最强振动组合
                    longArrayOf(0, 1000, 300, 800, 300, 600, 300, 800, 300, 1000)
                }
                _currentVolume.value >= 750 -> {
                    // 很响级别：强烈振动模式 - 超长振动组合
                    longArrayOf(0, 800, 300, 600, 200, 400, 200, 600)
                }
                _currentVolume.value >= 500 -> {
                    // 响亮级别：较强振动模式 - 长振动组合  
                    longArrayOf(0, 600, 250, 500, 200, 400)
                }
                _currentVolume.value >= 250 -> {
                    // 中等级别：中强振动模式 - 中长振动
                    longArrayOf(0, 400, 200, 400)
                }
                _currentVolume.value >= 100 -> {
                    // 轻级别：标准振动模式 - 标准振动
                    longArrayOf(0, 300, 150, 300)
                }
                _currentVolume.value >= 50 -> {
                    // 很轻级别：轻微振动模式 - 短振动
                    longArrayOf(0, 200)
                }
                else -> {
                    // 静音级别：不振动
                    longArrayOf(0)
                }
            }
            
            // 根据强度设置振动幅度
            val amplitude = when (vibrationIntensity) {
                1 -> 80   // 轻
                2 -> 150  // 中等
                3 -> 255  // 强 (最大值)
                else -> 150
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用VibrationEffect
                // 创建与振动模式长度匹配的振幅数组
                val amplitudes = IntArray(vibrationPattern.size) { index ->
                    if (index % 2 == 0) 0 else amplitude  // 偶数索引为暂停(0)，奇数索引为振动(amplitude)
                }
                val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, amplitudes, -1)
                vibrator.vibrate(vibrationEffect)
                Log.d(TAG, "[振动提醒] 执行振动 - 模式: ${vibrationPattern.contentToString()}, 强度: $amplitude")
            } else {
                // Android 8.0以下使用传统方法
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
                Log.d(TAG, "[振动提醒] 执行传统振动 - 模式: ${vibrationPattern.contentToString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[振动提醒] 执行振动失败", e)
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