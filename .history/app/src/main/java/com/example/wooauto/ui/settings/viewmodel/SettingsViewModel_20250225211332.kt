package com.example.wooauto.ui.screens.settings.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wooauto.MainActivity
import com.example.wooauto.utils.LanguageHelper
import com.example.wooauto.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val _shouldRestartApp = MutableStateFlow(false)
    val shouldRestartApp: StateFlow<Boolean> = _shouldRestartApp

    // Language preference
    val language: StateFlow<String> = preferencesManager.language
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PreferencesManager.DEFAULT_LANGUAGE
        )

    /**
     * Update app language
     */
    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            try {
                // 保存新的语言设置
                preferencesManager.setLanguage(languageCode)

                // 应用语言更改
                LanguageHelper.setLocale(getApplication(), languageCode)

                // 标记需要重启应用
                _shouldRestartApp.value = true
                
                Log.d("SettingsViewModel", "语言更新成功：$languageCode")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "语言更新失败", e)
            }
        }
    }

    /**
     * 重启应用以应用新的语言设置
     */
    fun restartApp(activity: Activity) {
        try {
            // 创建启动主活动的Intent
            val intent = Intent(activity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            
            // 启动新的Activity
            activity.startActivity(intent)
            
            // 结束当前Activity
            activity.finish()
            
            // 重置重启标志
            _shouldRestartApp.value = false
            
            Log.d("SettingsViewModel", "应用重启成功")
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "应用重启失败", e)
        }
    }
}