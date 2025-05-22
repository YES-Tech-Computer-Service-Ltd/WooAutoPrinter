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
    private val soundManager: SoundManager
) : ViewModel() {

    companion object {
        private const val TAG = "SoundSettingsViewModel"
    }

    // 通知音量
    private val _notificationVolume = MutableStateFlow(70)
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
            soundManager.playSound(_soundType.value)
        }
    }
    
    /**
     * 获取声音类型的显示名称
     * @param soundType 声音类型
     * @return 对应的显示名称
     */
    fun getSoundTypeDisplayName(soundType: String): String {
        return when (soundType) {
            SoundSettings.SOUND_TYPE_DEFAULT -> "系统默认通知音"
            SoundSettings.SOUND_TYPE_ALARM -> "系统闹钟声音"
            SoundSettings.SOUND_TYPE_RINGTONE -> "系统电话铃声"
            SoundSettings.SOUND_TYPE_EVENT -> "系统事件提示音"
            SoundSettings.SOUND_TYPE_EMAIL -> "系统邮件提示音"
            SoundSettings.SOUND_TYPE_CUSTOM -> "自定义音频"
            else -> "系统默认通知音"
        }
    }
} 