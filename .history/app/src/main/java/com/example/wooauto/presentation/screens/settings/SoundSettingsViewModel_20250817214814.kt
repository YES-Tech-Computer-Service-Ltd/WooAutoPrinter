package com.example.wooauto.presentation.screens.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.R
import com.example.wooauto.domain.models.SoundSettings
import com.example.wooauto.domain.repositories.DomainSettingRepository
import com.example.wooauto.utils.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 声音设置ViewModel
 * 管理声音设置界面的状态和逻辑
 */
@HiltViewModel
class SoundSettingsViewModel @Inject constructor(
    private val settingsRepository: DomainSettingRepository,
    private val soundManager: SoundManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SoundSettingsViewModel"
    }

    // 通知音量
    private val _notificationVolume = MutableStateFlow(50)   // 默认轻级别
    val notificationVolume: StateFlow<Int> = _notificationVolume.asStateFlow()

    // 提示音类型
    private val _soundType = MutableStateFlow(SoundSettings.SOUND_TYPE_DEFAULT)
    val soundType: StateFlow<String> = _soundType.asStateFlow()

    // 声音启用状态
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()
    
    // 自定义声音URI
    private val _customSoundUri = MutableStateFlow("")
    val customSoundUri: StateFlow<String> = _customSoundUri.asStateFlow()
    
    // 接单持续提示 - 独立开关（不改SoundSettings数据类）
    private val _keepRingingUntilAccept = MutableStateFlow(false)
    val keepRingingUntilAccept: StateFlow<Boolean> = _keepRingingUntilAccept.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载声音设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSoundSettings()
                _notificationVolume.value = settings.notificationVolume
                _soundType.value = settings.soundType
                _soundEnabled.value = settings.soundEnabled
                _customSoundUri.value = settings.customSoundUri
                _keepRingingUntilAccept.value = settingsRepository.getKeepRingingUntilAccept()
                
                Log.d(TAG, "成功加载声音设置: 音量=${settings.notificationVolume}, 类型=${settings.soundType}, 启用=${settings.soundEnabled}, 自定义声音=${settings.customSoundUri}")
            } catch (e: Exception) {
                Log.e(TAG, "加载声音设置失败", e)
            }
        }
    }

    /**
     * 保存声音设置
     */
    suspend fun saveSettings() {
        try {
            val settings = SoundSettings(
                notificationVolume = _notificationVolume.value,
                soundType = _soundType.value,
                soundEnabled = _soundEnabled.value,
                customSoundUri = _customSoundUri.value
            )
            
            settingsRepository.saveSoundSettings(settings)
            settingsRepository.setKeepRingingUntilAccept(_keepRingingUntilAccept.value)
            Log.d(TAG, "成功保存声音设置")
            
            // 应用到SoundManager
            if (_soundEnabled.value) {
                soundManager.setVolume(_notificationVolume.value)
                soundManager.setSoundType(_soundType.value)
                soundManager.setSoundEnabled(true)
                // 如果是自定义类型，设置自定义URI
                if (_soundType.value == SoundSettings.SOUND_TYPE_CUSTOM) {
                    soundManager.setCustomSoundUri(_customSoundUri.value)
                }
                // 同步应用“接单持续提示”
                soundManager.setKeepRingingUntilAccept(_keepRingingUntilAccept.value)
            } else {
                soundManager.setSoundEnabled(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存声音设置失败", e)
        }
    }

    /**
     * 设置音量
     */
    suspend fun setVolume(volume: Int) {
        _notificationVolume.value = volume
        soundManager.setVolume(volume)
    }

    /**
     * 设置声音类型
     */
    suspend fun setSoundType(type: String) {
        _soundType.value = type
        soundManager.setSoundType(type)
        
        // 如果选择自定义声音但URI为空，需要提示用户选择音频文件
        if (type == SoundSettings.SOUND_TYPE_CUSTOM && _customSoundUri.value.isEmpty()) {
            Log.d(TAG, "选择了自定义声音类型，但未设置音频文件")
        }
    }

    /**
     * 设置声音启用状态
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        soundManager.setSoundEnabled(enabled)
    }
    
    /** 设置接单持续提示 */
    suspend fun setKeepRingingUntilAccept(enabled: Boolean) {
        _keepRingingUntilAccept.value = enabled
        soundManager.setKeepRingingUntilAccept(enabled)
        try {
            settingsRepository.setKeepRingingUntilAccept(enabled)
            Log.d(TAG, "已立即持久化 keepRingingUntilAccept=$enabled")
        } catch (e: Exception) {
            Log.e(TAG, "持久化 keepRingingUntilAccept 失败", e)
        }
    }
    
    /**
     * 设置自定义声音URI
     */
    suspend fun setCustomSoundUri(uri: String) {
        _customSoundUri.value = uri
        
        // 如果当前选择的是自定义声音类型，则应用新的URI
        if (_soundType.value == SoundSettings.SOUND_TYPE_CUSTOM) {
            soundManager.setCustomSoundUri(uri)
        }
    }

    /**
     * 播放测试音效
     */
    fun playTestSound() {
        if (_soundEnabled.value) {
            Log.d(TAG, "[ViewModel测试音效] 用户手动播放测试音效 - 声音类型: ${_soundType.value}, 音量: ${_notificationVolume.value}")
            soundManager.playSound(_soundType.value)
        } else {
            Log.d(TAG, "[ViewModel测试音效] 声音已禁用，不播放测试音效")
        }
    }
    
    /**
     * 停止所有正在播放的声音
     * 在关闭对话框时调用
     */
    fun stopSound() {
        soundManager.stopAllSounds()
    }
    
    /**
     * A  获取声音类型的显示名称
     * @param soundType 声音类型
     * @return 对应的显示名称
     */
    fun getSoundTypeDisplayName(soundType: String): String {
        return when (soundType) {
            SoundSettings.SOUND_TYPE_DEFAULT -> context.getString(R.string.sound_type_default)
            SoundSettings.SOUND_TYPE_ALARM -> context.getString(R.string.sound_type_alarm)
            SoundSettings.SOUND_TYPE_RINGTONE -> context.getString(R.string.sound_type_ringtone)
            SoundSettings.SOUND_TYPE_EVENT -> context.getString(R.string.sound_type_event)
            SoundSettings.SOUND_TYPE_EMAIL -> context.getString(R.string.sound_type_email)
            SoundSettings.SOUND_TYPE_CUSTOM -> context.getString(R.string.sound_type_custom)
            else -> context.getString(R.string.sound_type_default)
        }
    }
} 