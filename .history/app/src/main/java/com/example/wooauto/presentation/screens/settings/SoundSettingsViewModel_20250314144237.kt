package com.example.wooauto.presentation.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                
                Log.d(TAG, "成功加载声音设置: 音量=${settings.notificationVolume}, 类型=${settings.soundType}, 启用=${settings.soundEnabled}")
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
                soundEnabled = _soundEnabled.value
            )
            
            settingsRepository.saveSoundSettings(settings)
            Log.d(TAG, "成功保存声音设置")
            
            // 应用到SoundManager
            if (_soundEnabled.value) {
                soundManager.setVolume(_notificationVolume.value)
                soundManager.setSoundType(_soundType.value)
                soundManager.setSoundEnabled(true)
            } else {
                soundManager.setSoundEnabled(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存声音设置失败", e)
        }
    }

    /**
     * 设置通知音量
     */
    suspend fun setVolume(volume: Int) {
        try {
            val safeVolume = when {
                volume < 0 -> 0
                volume > 100 -> 100
                else -> volume
            }
            
            _notificationVolume.value = safeVolume
            
            // 实时应用到SoundManager并播放测试音效
            if (_soundEnabled.value) {
                soundManager.setVolume(safeVolume)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败", e)
        }
    }

    /**
     * 设置提示音类型
     */
    suspend fun setSoundType(type: String) {
        try {
            if (SoundSettings.getAllSoundTypes().contains(type)) {
                _soundType.value = type
                
                // 实时应用到SoundManager并播放测试音效
                if (_soundEnabled.value) {
                    soundManager.setSoundType(type)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置提示音类型失败", e)
        }
    }

    /**
     * 设置声音启用状态
     */
    suspend fun setSoundEnabled(enabled: Boolean) {
        try {
            _soundEnabled.value = enabled
            
            // 实时应用到SoundManager
            soundManager.setSoundEnabled(enabled)
            
            // 如果启用声音，播放当前类型的测试音效
            if (enabled) {
                soundManager.playSound(_soundType.value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置声音启用状态失败", e)
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
} 